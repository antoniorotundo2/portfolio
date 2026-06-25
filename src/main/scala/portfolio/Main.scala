package portfolio

import portfolio.routes.AppRoutes
import portfolio.admin.{
  AdminRoutes,
  AdminService,
  AdminServiceLive,
  GitHubServiceLive,
  ContentServiceLive
}
import portfolio.services.PortfolioServiceLive
import zio.*
import zio.http.*
import zio.logging.*

object Main extends ZIOAppDefault:
  override val bootstrap: ZLayer[ZIOAppArgs, Any, Any] =
    Runtime.removeDefaultLoggers >>> consoleLogger()

  // Rimuove periodicamente OTP e sessioni scaduti dagli store in-memory.
  private val sessionSweeper =
    ZIO.serviceWithZIO[AdminService](_.sweepExpired)
      .repeat(Schedule.spaced(15.minutes))
      .unit

  // Header di sicurezza applicati a tutte le risposte.
  // NOTA: la CSP usa 'unsafe-inline' perché le viste admin hanno handler onclick e stili inline;
  // per una CSP stretta andrebbero spostati su addEventListener / file CSS esterni.
  private val securityHeaders: Middleware[Any] =
    Middleware.addHeaders(
      Headers(
        Header.Custom("X-Content-Type-Options", "nosniff"),
        Header.Custom("X-Frame-Options", "DENY"),
        Header.Custom("Referrer-Policy", "strict-origin-when-cross-origin"),
        Header.Custom("Strict-Transport-Security", "max-age=31536000; includeSubDomains"),
        Header.Custom(
          "Content-Security-Policy",
          "default-src 'self'; img-src 'self' data:; " +
            "style-src 'self' 'unsafe-inline' https://fonts.googleapis.com; " +
            "font-src 'self' https://fonts.gstatic.com; " +
            "script-src 'self' 'unsafe-inline'; connect-src 'self'; " +
            "frame-ancestors 'none'; base-uri 'self'; form-action 'self'"
        )
      )
    )

  // Abilita la compressione gzip/deflate delle risposte (HTML, CSS, JS).
  private val serverLayer =
    Server.defaultWith(
      _.copy(
        address = java.net.InetSocketAddress(8080),
        responseCompression = Some(Server.Config.ResponseCompressionConfig.default)
      )
    )

  override def run: ZIO[ZIOAppArgs & Scope, Any, Any] =
    ZIO.logInfo("Portfolio starting on http://localhost:8080") *>
      (for {
        _ <- sessionSweeper.forkDaemon
        allRoutes =
          (AdminRoutes.routes ++ AppRoutes.routes) @@ securityHeaders @@ Middleware.requestLogging()
        _ <- Server.serve(allRoutes)
      } yield ()).provide(
        serverLayer,
        PortfolioServiceLive.layer,
        ZClient.default,
        AdminServiceLive.layer,
        GitHubServiceLive.layer,
        ContentServiceLive.layer
      )
