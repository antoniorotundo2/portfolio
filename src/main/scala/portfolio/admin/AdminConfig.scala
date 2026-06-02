// src/main/scala/portfolio/admin/AdminConfig.scala
package portfolio.admin

/** Configurazione dell'area amministratore.
  *
  * ⚠️  Tutti i segreti DEVONO essere passati come environment variables.
  */
object AdminConfig:

  // ── Email amministratore (pubblica) ─────────────────────────────────────
  val adminEmail: String = 
    sys.env.getOrElse("ADMIN_EMAIL", "antonio@rotundo.dev")

  // ── GitHub API (SEGRETI — obbligatori in production) ────────────────────
  val githubToken: String =
    sys.env.getOrElse("GITHUB_TOKEN", 
      throw new RuntimeException("GITHUB_TOKEN non impostato. Vedi: https://github.com/settings/tokens")
    )

  val githubOwner: String = sys.env.getOrElse("GITHUB_OWNER", "antoniorotundo2")
  val githubRepo: String = sys.env.getOrElse("GITHUB_REPO", "portfolio")
  val githubBranch: String = sys.env.getOrElse("GITHUB_BRANCH", "main")
  val contentBasePath: String = sys.env.getOrElse("CONTENT_BASE_PATH", "src/main/resources")

  // ── Commit metadata per GitHub ─────────────────────────────────────────
  val commitAuthorName: String = "Portfolio Admin"
  val commitAuthorEmail: String = adminEmail
  val commitMessagePrefix: String = "🤖 Admin update:"

  // ── SMTP per invio OTP (SEGRETI) ───────────────────────────────────────
  val smtpHost: String = sys.env.getOrElse("SMTP_HOST", "smtp.gmail.com")
  val smtpPort: Int = sys.env.getOrElse("SMTP_PORT", "587").toInt
  val smtpUser: String = sys.env.getOrElse("SMTP_USER", adminEmail)
  val smtpPassword: String =
    sys.env.getOrElse("SMTP_PASSWORD",
      throw new RuntimeException("SMTP_PASSWORD non impostato")
    )
  val smtpFrom: String = sys.env.getOrElse("SMTP_FROM", s"Portfolio Admin <$smtpUser>")

  // ── OTP e sessioni ─────────────────────────────────────────────────────
  val otpLength: Int = 6
  val otpExpiryMinutes: Int = 5
  val sessionExpiryHours: Int = 8