package portfolio

import portfolio.routes.AppRoutes
import portfolio.admin.{AdminRoutes, AdminServiceLive, GitHubServiceLive, ContentServiceLive}
import portfolio.services.{PortfolioServiceLive, PortfolioService}
import zio.*
import zio.http.*
import zio.logging.*

object Main extends ZIOAppDefault:
  override val bootstrap: ZLayer[ZIOAppArgs, Any, Any] =
    Runtime.removeDefaultLoggers >>> consoleLogger()

  override def run: ZIO[ZIOAppArgs & Scope, Any, Any] =
    ZIO.logInfo("Portfolio starting on http://localhost:8080") *>
    (for {
      adminRoutes <- AdminRoutes.routes
      allRoutes = adminRoutes ++ AppRoutes.routes
      _ <- Server.serve(allRoutes)
    } yield ()).provide(
      Server.defaultWithPort(8080),
      PortfolioServiceLive.layer,
      ZClient.default,
      AdminServiceLive.layer,
      GitHubServiceLive.layer,
      ContentServiceLive.layer
    )