package portfolio

import portfolio.routes.Routes
import portfolio.services.PortfolioServiceLive
import zio.*
import zio.http.*
import zio.logging.*
import zio.logging.slf4j.bridge.Slf4jBridge

object Main extends ZIOAppDefault:

  override val bootstrap: ZLayer[ZIOAppArgs, Any, Any] =
    Runtime.removeDefaultLoggers >>> consoleLogger()

  override def run: ZIO[ZIOAppArgs & ZIOAppArgs & Scope, Any, Any] =
    ZIO.logInfo("🚀 Portfolio starting on http://localhost:8080") *>
    Server
      .serve(Routes.routes)
      .provide(
        Server.defaultWithPort(8080),
        PortfolioServiceLive.layer,
      )
