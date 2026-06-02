// src/main/scala/portfolio/Main.scala
package portfolio

import portfolio.routes.AppRoutes
import portfolio.services.PortfolioServiceLive
import portfolio.admin.{AdminServiceLive, GitHubServiceLive, ContentServiceLive}
import zio.*
import zio.http.*
import zio.logging.*

object Main extends ZIOAppDefault:

  override val bootstrap: ZLayer[ZIOAppArgs, Any, Any] =
    Runtime.removeDefaultLoggers >>> consoleLogger()

  override def run: ZIO[ZIOAppArgs & Scope, Any, Any] =
    ZIO.logInfo("🚀 Portfolio starting on http://localhost:8080") *>
    ZIO.serviceWithZIO[PortfolioService & AdminService & ContentService & Client] { _ =>
      AppRoutes.routes
    }.flatten.flatMap { routes =>
      Server.serve(routes)
    }.provide(
      Server.defaultWithPort(8080),
      PortfolioServiceLive.layer,
      Client.live,
      AdminServiceLive.layer,
      GitHubServiceLive.layer,
      ContentServiceLive.layer
    )