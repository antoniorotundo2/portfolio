package portfolio.admin

import zio.*
import java.security.SecureRandom
import java.time.Instant

trait AdminService:
  def requestOtp: Task[Option[String]]
  def verifyOtp(code: String): Task[Option[String]]
  def isAuthenticated(token: String): UIO[Boolean]
  def logout(token: String): UIO[Unit]

object AdminServiceLive:
  val layer: ZLayer[Any, Nothing, AdminService] =
    ZLayer.fromZIO {
      for
        otpStore     <- Ref.make(Map.empty[String, OtpEntry])
        sessionStore <- Ref.make(Map.empty[String, AdminSession])  // Renamed to avoid conflicts
      yield Live(otpStore, sessionStore)
    }

  case class OtpEntry(code: String, expiresAt: Instant)
  case class AdminSession(expiresAt: Instant)  // Unique name, no conflict with jakarta.mail.Session

  private final class Live(
    otpStore: Ref[Map[String, OtpEntry]],
    sessionStore: Ref[Map[String, AdminSession]]
  ) extends AdminService:

    private val random = new SecureRandom()

    private def generateOtp(): String =
      val bound = math.pow(10, AdminConfig.otpLength).toInt
      random.nextInt(bound).toString.padTo(AdminConfig.otpLength, '0')

    private def generateToken(): String =
      val bytes = new Array[Byte](32)
      random.nextBytes(bytes)
      bytes.map("%02x".format(_)).mkString

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

        // Use fully qualified name to avoid ambiguity with AdminSession
        val mailSession = jakarta.mail.Session.getInstance(props, new Authenticator:
          override def getPasswordAuthentication =
            new PasswordAuthentication(AdminConfig.smtpUser, AdminConfig.smtpPassword)
        )

        val message = new MimeMessage(mailSession)
        message.setFrom(new InternetAddress(AdminConfig.smtpFrom))
        message.setRecipients(Message.RecipientType.TO, Array[Address](new InternetAddress(email)))
        message.setSubject("Admin Code — Portfolio")
        message.setText(s"Your code is: $otp\nExpires in ${AdminConfig.otpExpiryMinutes} minutes.", "UTF-8")
        Transport.send(message)
      }.catchAll(err => ZIO.logWarning(s"Email failed: ${err.getMessage}. OTP: $otp")).ignore

    def requestOtp: Task[Option[String]] =
      val email = AdminConfig.adminEmail
      val otp = generateOtp()
      val entry = OtpEntry(otp, Instant.now().plusSeconds(AdminConfig.otpExpiryMinutes * 60L))
      for
        _ <- otpStore.update(_.updated(email, entry))
        _ <- sendOtpEmail(email, otp)
        _ <- ZIO.logInfo(s"OTP generated for $email")
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
