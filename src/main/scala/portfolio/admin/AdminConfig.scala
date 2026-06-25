package portfolio.admin

object AdminConfig:
  val adminEmail: String = sys.env.getOrElse("ADMIN_EMAIL", "admin@example.com")

  val githubToken: String =
    sys.env.getOrElse("GITHUB_TOKEN", throw new RuntimeException("GITHUB_TOKEN non impostato"))
  val githubOwner: String     = sys.env.getOrElse("GITHUB_OWNER", "your-username")
  val githubRepo: String      = sys.env.getOrElse("GITHUB_REPO", "portfolio")
  val githubBranch: String    = sys.env.getOrElse("GITHUB_BRANCH", "main")
  val contentBasePath: String = sys.env.getOrElse("CONTENT_BASE_PATH", "src/main/resources")

  val commitAuthorName: String    = "Portfolio Admin"
  val commitAuthorEmail: String   = adminEmail
  val commitMessagePrefix: String = "Admin update:"

  // SMTP - Resend
  val smtpHost: String = sys.env.getOrElse("SMTP_HOST", "smtp.resend.com")
  val smtpPort: Int    = sys.env.getOrElse("SMTP_PORT", "587").toInt
  val smtpUser: String = sys.env.getOrElse("SMTP_USER", "resend")
  val smtpPassword: String =
    sys.env.getOrElse("SMTP_PASSWORD", throw new RuntimeException("SMTP_PASSWORD non impostato"))
  val smtpFrom: String = sys.env.getOrElse("SMTP_FROM", "onboarding@resend.dev")

  val otpLength: Int          = 6
  val otpExpiryMinutes: Int   = 5
  val otpMaxAttempts: Int     = 5
  val sessionExpiryHours: Int = 8

  /** Il cookie di sessione viaggia solo su HTTPS, salvo override esplicito (dev). */
  val cookieSecure: Boolean = sys.env.getOrElse("COOKIE_SECURE", "true").toBoolean
