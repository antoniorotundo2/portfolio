```scala
package portfolio.admin

import portfolio.services.PortfolioService
import zio.*
import zio.http.*
import zio.json.*

// --- Modelli di dati ---
case class OtpRequest(otp: String)
case class SaveFileRequest(path: String, content: String)
case class FileContentResponse(path: String, content: String)
case class SaveResponse(success: Boolean, message: String, commitUrl: String, rebuildNote: String)
case class FilesListResponse(files: List[ContentFile], writable: Boolean, isGitHubMode: Boolean)

// --- Codec JSON ---
object OtpRequest:
  given JsonDecoder[OtpRequest] = DeriveJsonDecoder.gen[OtpRequest]

object SaveFileRequest:
  given JsonDecoder[SaveFileRequest] = DeriveJsonDecoder.gen[SaveFileRequest]

object FilesListResponse:
  given JsonEncoder[FilesListResponse] = DeriveJsonEncoder.gen[FilesListResponse]

object FileContentResponse:
  given JsonEncoder[FileContentResponse] = DeriveJsonEncoder.gen[FileContentResponse]

object SaveResponse:
  given JsonEncoder[SaveResponse] = DeriveJsonEncoder.gen[SaveResponse]

// --- Routes ---
object AdminRoutes:

  private def extractToken(req: Request): Option[String] =
    req.cookies.find(_.name == "admin_session").map(_.content)

  private def jsonResponse[A](value: A)(using encoder: JsonEncoder[A]): Response =
    Response.json(encoder.encodeJson(value, None).toString)

  private val notAuthenticated: Response =
    Response(status = Status.Unauthorized, body = Body.fromString("""{"error":"Not authenticated"}"""))

  private def checkAuth(adminSvc: AdminService, req: Request): UIO[Boolean] =
    adminSvc.isAuthenticated(extractToken(req).getOrElse(""))

  private def decodeSaveRequest(body: String): Either[String, SaveFileRequest] =
    summon[JsonDecoder[SaveFileRequest]].decodeJson(body)

  private def decodeOtpRequest(body: String): Either[String, OtpRequest] =
    summon[JsonDecoder[OtpRequest]].decodeJson(body)

  private def toUIO(task: Task[Response]): UIO[Response] =
    task.catchAll(err => ZIO.succeed(Response.internalServerError(err.getMessage)))

  val routes: ZIO[AdminService & ContentService & PortfolioService & Client, Nothing, Routes[Any, Nothing]] =
    for
      adminSvc   <- ZIO.service[AdminService]
      contentSvc <- ZIO.service[ContentService]
      portfolio  <- ZIO.service[PortfolioService]
      client     <- ZIO.service[Client]
      adminLayer   = ZLayer.succeed(adminSvc)
      contentLayer = ZLayer.succeed(contentSvc)
      portLayer    = ZLayer.succeed(portfolio)
      clientLayer  = ZLayer.succeed(client)
    yield Routes(

      Method.GET / "admin" ->
        Handler.fromFunction { (_: Request) =>
          Response(status = Status.Ok, headers = Headers(Header.ContentType(MediaType.text.html)),
            body = Body.fromString(AdminViews.loginPage))
        },

      Method.GET / "admin" / "dashboard" ->
        Handler.fromFunctionZIO { (req: Request) =>
          toUIO {
            (for {
              as <- ZIO.service[AdminService]
              cs <- ZIO.service[ContentService]
              result <- extractToken(req) match {
                case None => ZIO.succeed(Response.redirect(URL.root / "admin"))
                case Some(token) =>
                  as.isAuthenticated(token).flatMap {
                    case false => ZIO.succeed(Response.redirect(URL.root / "admin"))
                    case true =>
                      cs.isWritable.map { writable =>
                        Response(status = Status.Ok, headers = Headers(Header.ContentType(MediaType.text.html)),
                          body = Body.fromString(AdminViews.dashboardPage(writable, isGitHubMode = true)))
                      }
                  }
              }
            } yield result).provide(adminLayer ++ contentLayer)
          }
        },

      Method.POST / "admin" / "api" / "request-otp" ->
        Handler.fromFunctionZIO { (_: Request) =>
          toUIO {
            ZIO.serviceWithZIO[AdminService](_.requestOtp)
              .map {
                case Some(_) => Response.json("""{"message":"OTP sent"}""")
                case None    => Response.json("""{"error":"OTP generation error"}""").status(Status.InternalServerError)
              }
              .provide(adminLayer)
          }
        },

      Method.POST / "admin" / "api" / "verify-otp" ->
        Handler.fromFunctionZIO { (req: Request) =>
          toUIO {
            (for {
              as <- ZIO.service[AdminService]
              result <- req.body.asString.flatMap { rawBody =>
                decodeOtpRequest(rawBody) match {
                  case Left(_) => ZIO.succeed(Response.json("""{"error":"Missing 'otp' field"}""").status(Status.BadRequest))
                  case Right(otpReq) =>
                    as.verifyOtp(otpReq.otp).flatMap {
                      case None => ZIO.succeed(Response.json("""{"error":"Invalid code"}""").status(Status.Unauthorized))
                      case Some(token) =>
                        val cookie = Cookie.Response(name = "admin_session", content = token,
                          maxAge = Some(java.time.Duration.ofHours(AdminConfig.sessionExpiryHours)),
                          isHttpOnly = true, sameSite = Some(Cookie.SameSite.Strict), path = Some(Path.root / "admin"))
                        ZIO.succeed(Response.json("""{"success":true}""").addCookie(cookie))
                    }
                }
              }
            } yield result).provide(adminLayer)
          }
        },

      Method.POST / "admin" / "api" / "logout" ->
        Handler.fromFunctionZIO { (req: Request) =>
          toUIO {
            ZIO.serviceWithZIO[AdminService] { as =>
              extractToken(req) match {
                case None => ZIO.succeed(Response.redirect(URL.root / "admin"))
                case Some(token) =>
                  as.logout(token).as(Response.json("""{"success":true}""").addCookie(
                    Cookie.Response(name = "admin_session", content = "",
                      maxAge = Some(java.time.Duration.ZERO), path = Some(Path.root / "admin"))))
              }
            }.provide(adminLayer)
          }
        },

      // GET /admin/api/files - lista file
      Method.GET / "admin" / "api" / "files" ->
        Handler.fromFunctionZIO { (req: Request) =>
          toUIO {
            (for {
              as <- ZIO.service[AdminService]
              cs <- ZIO.service[ContentService]
              result <- checkAuth(as, req).flatMap {
                case false => ZIO.succeed(notAuthenticated)
                case true =>
                  cs.listFiles.map { files =>
                    jsonResponse(FilesListResponse(files, writable = true, isGitHubMode = true))
                  }.catchAll(_ => ZIO.succeed(Response.json("""{"error":"Cannot list files"}""").status(Status.InternalServerError)))
              }
            } yield result).provide(adminLayer ++ contentLayer ++ clientLayer)
          }
        },

      // POST /admin/api/files - salva file
      Method.POST / "admin" / "api" / "files" ->
        Handler.fromFunctionZIO { (req: Request) =>
          toUIO {
            (for {
              as <- ZIO.service[AdminService]
              cs <- ZIO.service[ContentService]
              ps <- ZIO.service[PortfolioService]
              result <- checkAuth(as, req).flatMap {
                case false => ZIO.succeed(notAuthenticated)
                case true =>
                  req.body.asString.flatMap { body =>
                    ZIO.logInfo(s"Body ricevuto: ${body.take(200)}") *>
                    decodeSaveRequest(body) match {
                      case Left(err) =>
                        ZIO.logError(s"Parse error: $err") *>
                        ZIO.succeed(Response.json(s"""{"error":"Invalid JSON: $err"}""").status(Status.BadRequest))
                      case Right(sr) =>
                        ZIO.logInfo(s"Salvo file: ${sr.path}") *>
                        cs.writeFile(sr.path, sr.content).flatMap { commit =>
                          ps.reload.catchAll(_ => ZIO.unit) *>
                            ZIO.succeed(jsonResponse(SaveResponse(
                              success = true,
                              message = "File saved to GitHub!",
                              commitUrl = commit.html_url,
                              rebuildNote = "The site will update automatically on Render in ~1-2 minutes."
                            )))
                        }.catchAll(e => ZIO.succeed(
                          Response.json(s"""{"error":"Save error: ${e.getMessage}"}""").status(Status.InternalServerError)
                        ))
                    }
                  }
              }
            } yield result).provide(adminLayer ++ contentLayer ++ portLayer ++ clientLayer)
          }
        },

      Method.GET / "admin" / "api" / "files" / string("section") / string("filename") ->
        Handler.fromFunctionZIO { (req: Request) =>
          toUIO {
            (for {
              as <- ZIO.service[AdminService]
              cs <- ZIO.service[ContentService]
              pathParts = req.path.encode.split("/").drop(4)
              section   = if pathParts.length >= 1 then pathParts(0) else ""
              filename  = if pathParts.length >= 2 then pathParts(1) else ""
              result <- checkAuth(as, req).flatMap {
                case false => ZIO.succeed(notAuthenticated)
                case true =>
                  cs.readFile(s"$section/$filename").flatMap(content =>
                    ZIO.succeed(jsonResponse(FileContentResponse(s"$section/$filename", content)))
                  ).catchAll(_ => ZIO.succeed(Response.json(s"""{"error":"Not found"}""").status(Status.NotFound)))
              }
            } yield result).provide(adminLayer ++ contentLayer ++ clientLayer)
          }
        }
    )
```