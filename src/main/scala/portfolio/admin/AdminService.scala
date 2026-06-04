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
      ZIO.attemptBlocking {
        val json = s"""{"from":"${AdminConfig.smtpFrom}","to":"$email","subject":"Admin Code — Portfolio","text":"Your code is: $otp\\nExpires in ${AdminConfig.otpExpiryMinutes} minutes."}"""
        val url = java.net.URI("https://api.resend.com/emails").toURL
        val conn = url.openConnection().asInstanceOf[java.net.HttpURLConnection]
        conn.setRequestMethod("POST")
        conn.setRequestProperty("Authorization", s"Bearer ${AdminConfig.smtpPassword}")
        conn.setRequestProperty("Content-Type", "application/json")
        conn.setDoOutput(true)
        conn.setConnectTimeout(15000)
        conn.setReadTimeout(15000)
        conn.getOutputStream.write(json.getBytes("UTF-8"))
        val responseCode = conn.getResponseCode
        val stream = if (responseCode >= 200 && responseCode < 300) conn.getInputStream else conn.getErrorStream
        val responseBody = scala.io.Source.fromInputStream(stream).mkString
        stream.close()
        if (responseCode != 200) throw new RuntimeException(s"Resend error: $responseCode - $responseBody")
      }
        .timeoutFail(new RuntimeException("Resend timeout"))(20.seconds)
        .catchAll(err => ZIO.logWarning(s"Email failed: ${err.getMessage}. OTP: $otp"))

    def requestOtp: Task[Option[String]] =
      val email = AdminConfig.adminEmail
      val otp = generateOtp()
      val entry = OtpEntry(otp, Instant.now().plusSeconds(AdminConfig.otpExpiryMinutes * 60L))
      for
        _ <- otpStore.update(_.updated(email, entry))
        _ <- sendOtpEmail(email, otp)
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