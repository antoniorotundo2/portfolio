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
    Response(
      status = Status.Unauthorized,
      body = Body.fromString("""{"error":"Not authenticated"}""")
    )

  private val sessionCookiePath = Path.root / "admin"

  private def checkAuth(adminSvc: AdminService, req: Request): UIO[Boolean] =
    adminSvc.isAuthenticated(extractToken(req).getOrElse(""))

  private def decodeSaveRequest(body: String): Either[String, SaveFileRequest] =
    summon[JsonDecoder[SaveFileRequest]].decodeJson(body)

  private def decodeOtpRequest(body: String): Either[String, OtpRequest] =
    summon[JsonDecoder[OtpRequest]].decodeJson(body)

  /** Logga l'errore reale ma restituisce al client un messaggio generico (no info disclosure). */
  private def toUIO[R](effect: ZIO[R, Throwable, Response]): URIO[R, Response] =
    effect.catchAllCause { cause =>
      ZIO.logErrorCause("Admin request failed", cause) *>
        ZIO.succeed(
          Response.json("""{"error":"Internal error"}""").status(Status.InternalServerError)
        )
    }

  val routes: Routes[AdminService & ContentService & PortfolioService, Nothing] =
    Routes(
      Method.GET / "admin" ->
        Handler.fromFunction { (_: Request) =>
          Response(
            status = Status.Ok,
            headers = Headers(Header.ContentType(MediaType.text.html)),
            body = Body.fromString(AdminViews.loginPage)
          )
        },
      Method.GET / "admin" / "dashboard" ->
        Handler.fromFunctionZIO { (req: Request) =>
          toUIO {
            for {
              as <- ZIO.service[AdminService]
              cs <- ZIO.service[ContentService]
              result <- extractToken(req) match {
                case None => ZIO.succeed(Response.redirect(URL.root / "admin"))
                case Some(token) =>
                  as.isAuthenticated(token).flatMap {
                    case false => ZIO.succeed(Response.redirect(URL.root / "admin"))
                    case true =>
                      cs.isWritable.map { writable =>
                        Response(
                          status = Status.Ok,
                          headers = Headers(Header.ContentType(MediaType.text.html)),
                          body =
                            Body.fromString(AdminViews.dashboardPage(writable, isGitHubMode = true))
                        )
                      }
                  }
              }
            } yield result
          }
        },
      Method.POST / "admin" / "api" / "request-otp" ->
        Handler.fromFunctionZIO { (_: Request) =>
          toUIO {
            ZIO.serviceWithZIO[AdminService](_.requestOtp)
              .as(Response.json("""{"message":"OTP sent"}"""))
          }
        },
      Method.POST / "admin" / "api" / "verify-otp" ->
        Handler.fromFunctionZIO { (req: Request) =>
          toUIO {
            for {
              as <- ZIO.service[AdminService]
              result <- req.body.asString.flatMap { rawBody =>
                decodeOtpRequest(rawBody) match {
                  case Left(_) => ZIO.succeed(
                      Response.json("""{"error":"Missing 'otp' field"}""").status(Status.BadRequest)
                    )
                  case Right(otpReq) =>
                    as.verifyOtp(otpReq.otp).flatMap {
                      case None => ZIO.succeed(
                          Response.json("""{"error":"Invalid code"}""").status(Status.Unauthorized)
                        )
                      case Some(token) =>
                        val cookie = Cookie.Response(
                          name = "admin_session",
                          content = token,
                          maxAge = Some(java.time.Duration.ofHours(AdminConfig.sessionExpiryHours)),
                          isHttpOnly = true,
                          isSecure = AdminConfig.cookieSecure,
                          sameSite = Some(Cookie.SameSite.Strict),
                          path = Some(sessionCookiePath)
                        )
                        ZIO.succeed(Response.json("""{"success":true}""").addCookie(cookie))
                    }
                }
              }
            } yield result
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
                    Cookie.Response(
                      name = "admin_session",
                      content = "",
                      maxAge = Some(java.time.Duration.ZERO),
                      isHttpOnly = true,
                      isSecure = AdminConfig.cookieSecure,
                      sameSite = Some(Cookie.SameSite.Strict),
                      path = Some(sessionCookiePath)
                    )
                  ))
              }
            }
          }
        },

      // GET /admin/api/files - lista file
      Method.GET / "admin" / "api" / "files" ->
        Handler.fromFunctionZIO { (req: Request) =>
          toUIO {
            for {
              as <- ZIO.service[AdminService]
              cs <- ZIO.service[ContentService]
              result <- checkAuth(as, req).flatMap {
                case false => ZIO.succeed(notAuthenticated)
                case true =>
                  cs.listFiles.map { files =>
                    jsonResponse(FilesListResponse(files, writable = true, isGitHubMode = true))
                  }
              }
            } yield result
          }
        },

      // GET /admin/api/files/get?filename=... - leggi file
      Method.GET / "admin" / "api" / "files" / "get" ->
        Handler.fromFunctionZIO { (req: Request) =>
          toUIO {
            for {
              as <- ZIO.service[AdminService]
              cs <- ZIO.service[ContentService]
              filename = req.url.queryParams.getAll("filename").headOption.getOrElse("")
              result <- checkAuth(as, req).flatMap {
                case false => ZIO.succeed(notAuthenticated)
                case true =>
                  ZIO.logInfo(s"Lettura file: $filename") *>
                    cs.readFile(filename)
                      .map(content => jsonResponse(FileContentResponse(filename, content)))
                      .catchAll(_ =>
                        ZIO.succeed(
                          Response.json("""{"error":"Not found"}""").status(Status.NotFound)
                        )
                      )
              }
            } yield result
          }
        },

      // POST /admin/api/files - salva file
      Method.POST / "admin" / "api" / "files" ->
        Handler.fromFunctionZIO { (req: Request) =>
          toUIO {
            for {
              as <- ZIO.service[AdminService]
              cs <- ZIO.service[ContentService]
              ps <- ZIO.service[PortfolioService]
              result <- checkAuth(as, req).flatMap {
                case false => ZIO.succeed(notAuthenticated)
                case true =>
                  req.body.asString.flatMap { body =>
                    decodeSaveRequest(body) match {
                      case Left(_) =>
                        ZIO.logWarning("Save request: invalid JSON body") *>
                          ZIO.succeed(Response.json("""{"error":"Invalid request body"}""").status(
                            Status.BadRequest
                          ))
                      case Right(saveReq) =>
                        ZIO.logInfo(s"Salvo file: ${saveReq.path}") *>
                          cs.writeFile(saveReq.path, saveReq.content).flatMap { commit =>
                            ps.reload.catchAll(_ => ZIO.unit) *>
                              ZIO.succeed(jsonResponse(SaveResponse(
                                success = true,
                                message = "File saved to GitHub!",
                                commitUrl = commit.html_url,
                                rebuildNote =
                                  "The site will update automatically on Render in ~1-2 minutes."
                              )))
                          }
                    }
                  }
              }
            } yield result
          }
        }
    )
