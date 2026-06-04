package portfolio.admin

import zio.*
import zio.http.*
import zio.json.*
import java.security.SecureRandom
import java.time.Instant

trait AdminService:
  def requestOtp: Task[Option[String]]
  def verifyOtp(code: String): Task[Option[String]]
  def isAuthenticated(token: String): UIO[Boolean]
  def logout(token: String): UIO[Unit]

object AdminServiceLive:
  val layer: ZLayer[Client, Nothing, AdminService] =
    ZLayer.fromZIO {
      for
        otpStore     <- Ref.make(Map.empty[String, OtpEntry])
        sessionStore <- Ref.make(Map.empty[String, AdminSession])
        client       <- ZIO.service[Client]
      yield Live(otpStore, sessionStore, client)
    }

  case class OtpEntry(code: String, expiresAt: Instant)
  case class AdminSession(expiresAt: Instant)

  private case class ResendRequest(from: String, to: String, subject: String, text: String)
  private object ResendRequest:
    given JsonEncoder[ResendRequest] = DeriveJsonEncoder.gen[ResendRequest]

  private final class Live(
    otpStore: Ref[Map[String, OtpEntry]],
    sessionStore: Ref[Map[String, AdminSession]],
    client: Client
  ) extends AdminService:

    private val random = new SecureRandom()

    private def generateOtp(): String =
      val bound = math.pow(10, AdminConfig.otpLength).toInt
      (random.nextInt(bound) + bound).toString.take(AdminConfig.otpLength)

    private def generateToken(): String =
      val bytes = new Array[Byte](32)
      random.nextBytes(bytes)
      bytes.map("%02x".format(_)).mkString

    private def sendOtpEmail(email: String, otp: String): Task[Unit] =
      val requestBody = ResendRequest(
        from = AdminConfig.smtpFrom,
        to = email,
        subject = "Admin Code — Portfolio",
        text = s"Your code is: $otp\nExpires in ${AdminConfig.otpExpiryMinutes} minutes."
      )

      ZIO.scoped {
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
            else ZIO.fail(new RuntimeException(s"Resend API error: ${response.status}"))
          }
      }
        .timeoutFail(new RuntimeException("Resend timeout"))(10.seconds)
        .catchAll { err =>
          ZIO.logWarning(s"Email failed: ${err.getMessage}. OTP: $otp")
        }

    def requestOtp: Task[Option[String]] =
      val email = AdminConfig.adminEmail
      val otp = generateOtp()
      val entry = OtpEntry(otp, Instant.now().plusSeconds(AdminConfig.otpExpiryMinutes * 60L))
      for
        _ <- otpStore.update(_.updated(email, entry))
        _ <- sendOtpEmail(email, otp).fork
        _ <- ZIO.logInfo(s"OTP generated for $email: $otp")
      yield Some(otp)

    def verifyOtp(code: String): Task[Option[String]] =
      val email = AdminConfig.adminEmail
      for
        store <- otpStore.get
        result <- store.get(email) match
          case None => ZIO.logWarning("No OTP requested").as(None)
          case Some(entry) if Instant.now().isAfter(entry.expiresAt) =>
            otpStore.update(_ - email) *> ZIO.logWarning("OTP expired").as(None)
          case Some(entry) if entry.code != code =>
            ZIO.logWarning("Invalid OTP").as(None)
          case Some(_) =>
            val token = generateToken()
            val session = AdminSession(Instant.now().plusSeconds(AdminConfig.sessionExpiryHours * 3600L))
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
          case None => ZIO.succeed(false)
      yield valid

    def logout(token: String): UIO[Unit] = sessionStore.update(_ - token)