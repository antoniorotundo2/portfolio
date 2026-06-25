package portfolio.admin

import zio.*
import zio.http.*
import zio.json.*
import java.security.SecureRandom
import java.time.Instant

trait AdminService:
  def requestOtp: Task[Unit]
  def verifyOtp(code: String): Task[Option[String]]
  def isAuthenticated(token: String): UIO[Boolean]
  def logout(token: String): UIO[Unit]
  def sweepExpired: UIO[Unit]

object AdminServiceLive:

  /** Invio dell'OTP via email. Iniettabile per rendere il servizio testabile senza rete. */
  type EmailSender = (String, String) => Task[Unit]

  val layer: ZLayer[Client, Nothing, AdminService] =
    ZLayer.fromZIO {
      for
        otpStore     <- Ref.make(Map.empty[String, OtpEntry])
        sessionStore <- Ref.make(Map.empty[String, AdminSession])
        client       <- ZIO.service[Client]
      yield Live(otpStore, sessionStore, resendSender(client))
    }

  /** Factory senza dipendenza dal Client: usata nei test con un sender finto. */
  def make(sender: EmailSender): UIO[AdminService] =
    for
      otpStore     <- Ref.make(Map.empty[String, OtpEntry])
      sessionStore <- Ref.make(Map.empty[String, AdminSession])
    yield Live(otpStore, sessionStore, sender)

  case class OtpEntry(code: String, expiresAt: Instant, issuedAt: Instant, attempts: Int)
  case class AdminSession(expiresAt: Instant)

  private case class ResendRequest(from: String, to: String, subject: String, text: String)
  private object ResendRequest:
    given JsonEncoder[ResendRequest] = DeriveJsonEncoder.gen[ResendRequest]

  /** Implementazione reale del sender, basata su Resend. */
  private def resendSender(client: Client): EmailSender = (email, otp) =>
    val requestBody = ResendRequest(
      from = AdminConfig.smtpFrom,
      to = email,
      subject = "Admin Code — Portfolio",
      text = s"Your code is: $otp\nExpires in ${AdminConfig.otpExpiryMinutes} minutes."
    )

    ZIO
      .scoped {
        client
          .batched(
            Request(
              method = Method.POST,
              url = URL.decode("https://api.resend.com/emails").toOption.get,
              headers = Headers(
                Header.Custom("Authorization", s"Bearer ${AdminConfig.smtpPassword}"),
                Header.ContentType(MediaType.application.json)
              ),
              body = Body.fromString(requestBody.toJson)
            )
          )
          .flatMap { response =>
            if response.status.isSuccess then ZIO.unit
            else
              response.body.asString.flatMap(body =>
                ZIO.fail(new RuntimeException(s"Resend error: ${response.status} - $body"))
              )
          }
      }
      .timeoutFail(new RuntimeException("Resend timeout"))(30.seconds)
      // L'OTP non viene loggato: in caso di errore segnaliamo solo il fallimento.
      .catchAll(err => ZIO.logWarning(s"Email failed: ${err.getMessage}"))

  private final class Live(
      otpStore: Ref[Map[String, OtpEntry]],
      sessionStore: Ref[Map[String, AdminSession]],
      sendEmail: EmailSender
  ) extends AdminService:

    private val random = new SecureRandom()

    private def generateOtp(): String =
      val bound = math.pow(10, AdminConfig.otpLength).toInt
      String.format(s"%0${AdminConfig.otpLength}d", random.nextInt(bound))

    /** Confronto a tempo costante per evitare timing attack sull'OTP. */
    private def constantTimeEquals(a: String, b: String): Boolean =
      java.security.MessageDigest.isEqual(
        a.getBytes(java.nio.charset.StandardCharsets.UTF_8),
        b.getBytes(java.nio.charset.StandardCharsets.UTF_8)
      )

    private def generateToken(): String =
      val bytes = new Array[Byte](32)
      random.nextBytes(bytes)
      bytes.map("%02x".format(_)).mkString

    def requestOtp: Task[Unit] =
      val email = AdminConfig.adminEmail
      val now   = Instant.now()
      otpStore.get.flatMap { store =>
        store.get(email) match
          // Cooldown: se un OTP valido è stato emesso da poco, non ne generiamo/inviamo un altro
          // (rate-limit anti spam email / quota Resend). L'OTP esistente resta valido.
          case Some(entry)
              if now.isBefore(entry.expiresAt) &&
                now.isBefore(entry.issuedAt.plusSeconds(AdminConfig.otpResendCooldownSeconds)) =>
            ZIO.logWarning(s"OTP request throttled for $email")
          case _ =>
            val otp = generateOtp()
            val entry = OtpEntry(
              code = otp,
              expiresAt = now.plusSeconds(AdminConfig.otpExpiryMinutes * 60L),
              issuedAt = now,
              attempts = 0
            )
            otpStore.update(_.updated(email, entry)) *>
              sendEmail(email, otp) *>
              ZIO.logInfo(s"OTP generated for $email")
      }

    def verifyOtp(code: String): Task[Option[String]] =
      val email = AdminConfig.adminEmail
      for
        store <- otpStore.get
        result <- store.get(email) match
          case None => ZIO.logWarning("No OTP requested").as(None)
          case Some(entry) if Instant.now().isAfter(entry.expiresAt) =>
            otpStore.update(_ - email) *> ZIO.logWarning("OTP expired").as(None)
          case Some(entry) if entry.attempts >= AdminConfig.otpMaxAttempts =>
            otpStore.update(_ - email) *> ZIO.logWarning("OTP max attempts exceeded").as(None)
          case Some(entry) if !constantTimeEquals(entry.code, code) =>
            // incrementa il contatore tentativi e rifiuta
            otpStore.update(_.updatedWith(email)(_.map(e => e.copy(attempts = e.attempts + 1)))) *>
              ZIO.logWarning("Invalid OTP").as(None)
          case Some(_) =>
            val token = generateToken()
            val session =
              AdminSession(Instant.now().plusSeconds(AdminConfig.sessionExpiryHours * 3600L))
            for
              _ <- otpStore.update(_ - email)
              _ <- sessionStore.update(_.updated(token, session))
            yield Some(token)
      yield result

    def isAuthenticated(token: String): UIO[Boolean] =
      for
        store <- sessionStore.get
        valid <- store.get(token) match
          case Some(sess) if Instant.now().isBefore(sess.expiresAt) => ZIO.succeed(true)
          case Some(_) => sessionStore.update(_ - token).as(false)
          case None    => ZIO.succeed(false)
      yield valid

    def logout(token: String): UIO[Unit] = sessionStore.update(_ - token)

    /** Rimuove sessioni e OTP scaduti: evita la crescita illimitata degli store in-memory. */
    def sweepExpired: UIO[Unit] =
      val now = Instant.now()
      sessionStore.update(_.filter((_, s) => now.isBefore(s.expiresAt))) *>
        otpStore.update(_.filter((_, e) => now.isBefore(e.expiresAt)))
