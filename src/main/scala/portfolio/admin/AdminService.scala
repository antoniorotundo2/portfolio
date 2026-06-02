package portfolio.admin

import zio.*
import java.security.SecureRandom
import java.time.Instant

// ── Algebra ──────────────────────────────────────────────────────────────────

trait AdminService:
  /** Richiede un OTP per l'email admin. Restituisce l'OTP generato (solo per dev mode). */
  def requestOtp: Task[Option[String]]

  /** Verifica un OTP. Se valido, crea una sessione e restituisce il token. */
  def verifyOtp(code: String): Task[Option[String]]

  /** Verifica se un token di sessione è valido. */
  def isAuthenticated(token: String): UIO[Boolean]

  /** Invalida una sessione (logout). */
  def logout(token: String): UIO[Unit]

// ── Implementazione ──────────────────────────────────────────────────────────

object AdminServiceLive:

  // Stato in memoria: OTP pendenti e sessioni attive
  case class OtpEntry(code: String, expiresAt: Instant)
  case class Session(expiresAt: Instant)

  val layer: ZLayer[Any, Nothing, AdminService] =
    ZLayer.fromZIO(
      for
        otpStore    <- Ref.make(Map.empty[String, OtpEntry])   // email -> OTP
        sessionStore <- Ref.make(Map.empty[String, Session])    // token -> Session
      yield Live(otpStore, sessionStore)
    )

  private final class Live(
    otpStore: Ref[Map[String, OtpEntry]],
    sessionStore: Ref[Map[String, Session]],
  ) extends AdminService:

    private val random = new SecureRandom()

    // ── OTP Generation ────────────────────────────────────────────────────
    private def generateOtp(): String =
      val bound = math.pow(10, AdminConfig.otpLength).toInt
      val code  = random.nextInt(bound)
      code.toString.padLeft(AdminConfig.otpLength, '0')

    private def generateToken(): String =
      val bytes = new Array[Byte](32)
      random.nextBytes(bytes)
      bytes.map("%02x".format(_)).mkString

    // ── Send email (best-effort, fallback to console) ─────────────────────
    private def sendOtpEmail(email: String, otp: String): Task[Unit] =
      ZIO.attemptBlocking {
        import java.util.Properties
        import jakarta.mail.*
        import jakarta.mail.internet.*

        val props = new Properties()
        props.put("mail.smtp.auth", "true")
        props.put("mail.smtp.starttls.enable", "true")
        props.put("mail.smtp.host", AdminConfig.smtpHost)
        props.put("mail.smtp.port", AdminConfig.smtpPort.toString)

        val session = Session.getInstance(props, new Authenticator:
          override def getPasswordAuthentication: PasswordAuthentication =
            new PasswordAuthentication(AdminConfig.smtpUser, AdminConfig.smtpPassword)
        )

        val message = new MimeMessage(session)
        message.setFrom(new InternetAddress(AdminConfig.smtpFrom))
        message.setRecipients(Message.RecipientType.TO, InternetAddress.parse(email).asInstanceOf[Array[Address]])
        message.setSubject("🔐 Codice di accesso Admin — Portfolio")
        message.setText(
          s"""Il tuo codice monouso è: $otp
             |
             |Scade tra ${AdminConfig.otpExpiryMinutes} minuti.
             |Se non hai richiesto questo codice, ignora questa email.
             |""".stripMargin,
          "UTF-8",
        )
        Transport.send(message)
      }.tapError { err =>
        // Fallback: stampa in console se l'invio fallisce
        ZIO.logWarning(s"⚠️  Impossibile inviare email OTP: ${err.getMessage}. Codice: $otp")
      }.ignore

    // ── Request OTP ───────────────────────────────────────────────────────
    def requestOtp: Task[Option[String]] =
      val email = AdminConfig.adminEmail
      val otp   = generateOtp()
      val entry = OtpEntry(otp, Instant.now().plusSeconds(AdminConfig.otpExpiryMinutes * 60L))

      for
        _       <- otpStore.update(_.updated(email, entry))
        _       <- sendOtpEmail(email, otp)
        _       <- ZIO.logInfo(s"🔐 OTP generato per $email (scade tra ${AdminConfig.otpExpiryMinutes} min)")
      yield Some(otp) // Restituito solo per dev mode, in produzione togliere

    // ── Verify OTP ────────────────────────────────────────────────────────
    def verifyOtp(code: String): Task[Option[String]] =
      val email = AdminConfig.adminEmail
      for
        store   <- otpStore.get
        result  <- store.get(email) match
          case None =>
            ZIO.logWarning("Nessun OTP richiesto o già scaduto").as(None)
          case Some(entry) if Instant.now().isAfter(entry.expiresAt) =>
            otpStore.update(_ - email) *>
            ZIO.logWarning("OTP scaduto").as(None)
          case Some(entry) if entry.code != code =>
            ZIO.logWarning("Codice OTP non valido").as(None)
          case Some(_) =>
            // OTP valido: crea sessione
            val token   = generateToken()
            val session = Session(Instant.now().plusSeconds(AdminConfig.sessionExpiryHours * 3600L))
            for
              _ <- otpStore.update(_ - email)
              _ <- sessionStore.update(_.updated(token, session))
              _ <- ZIO.logInfo(s"✅ Login admin riuscito per $email")
            yield Some(token)
      yield result

    // ── Auth check ────────────────────────────────────────────────────────
    def isAuthenticated(token: String): UIO[Boolean] =
      for
        store   <- sessionStore.get
        isValid <- store.get(token) match
          case Some(session) if Instant.now().isBefore(session.expiresAt) =>
            ZIO.succeed(true)
          case Some(_) =>
            // Sessione scaduta, rimuovi
            sessionStore.update(_ - token).as(false)
          case None =>
            ZIO.succeed(false)
      yield isValid

    // ── Logout ────────────────────────────────────────────────────────────
    def logout(token: String): UIO[Unit] =
      sessionStore.update(_ - token)