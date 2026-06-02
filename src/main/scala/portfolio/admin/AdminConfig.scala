package portfolio.admin

import zio.*

/** Configurazione admin — email e credenziali hardcodate.
  *
  * ⚠️  In produzione, spostare in variabili d'ambiente!
  */
object AdminConfig:

  // ── Email amministratore (hardcodata) ─────────────────────────────────────
  val adminEmail: String = "antonio@rotundo.dev"

  // ── SMTP per invio OTP (configura con le tue credenziali) ────────────────
  val smtpHost: String     = "smtp.gmail.com"
  val smtpPort: Int        = 587
  val smtpUser: String     = "antonio@rotundo.dev"
  val smtpPassword: String = "LA_TUA_APP_PASSWORD_GMAIL"
  val smtpFrom: String     = s"Portfolio Admin <${smtpUser}>"

  // ── OTP settings ──────────────────────────────────────────────────────────
  val otpLength: Int          = 6
  val otpExpiryMinutes: Int   = 5
  val sessionExpiryHours: Int = 8

  // ── Content directory ─────────────────────────────────────────────────────
  // Se impostata, legge/scrive i .md da questa cartella esterna.
  // Se vuota, usa il classpath (sola lettura).
  // In sviluppo: CONTENT_DIR=src/main/resources
  val contentDir: Option[String] =
    sys.env.get("CONTENT_DIR").filter(_.nonEmpty)