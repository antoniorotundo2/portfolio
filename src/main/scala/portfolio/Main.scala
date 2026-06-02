package portfolio

import portfolio.routes.AppRoutes
import portfolio.services.PortfolioServiceLive
import zio.*
import zio.http.*
import zio.logging.*

object Main extends ZIOAppDefault:

  override val bootstrap: ZLayer[ZIOAppArgs, Any, Any] =
    Runtime.removeDefaultLoggers >>> consoleLogger()

  override def run: ZIO[ZIOAppArgs & Scope, Any, Any] =
    ZIO.logInfo("🚀 Portfolio starting on http://localhost:8080") *>
    Server
      .serve(AppRoutes.routes)
      .provide(
        Server.defaultWithPort(8080),
        PortfolioServiceLive.layer,
        Client.live, // 👈 OBBLIGATORIO per GitHubService
        AdminServiceLive.layer,
        GitHubServiceLive.layer,
        ContentServiceLive.layer
      )
