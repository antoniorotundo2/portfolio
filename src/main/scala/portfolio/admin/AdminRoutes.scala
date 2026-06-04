package portfolio.admin

import portfolio.services.PortfolioService
import zio.*
import zio.http.*
import zio.json.*

// --- Modelli di dati ---
case class OtpRequest(otp: String)
case class SaveFileRequest(path: String, content: String)
case class FileInfo(path: String, displayName: String, section: String)
case class FilesListResponse(files: List[FileInfo], writable: Boolean, isGitHubMode: Boolean)
case class FileContentResponse(path: String, content: String)
case class SaveResponse(success: Boolean, message: String, commitUrl: String, rebuildNote: String)

// --- Codec JSON ---
object OtpRequest:
  given JsonDecoder[OtpRequest] = DeriveJsonDecoder.gen[OtpRequest]

object SaveFileRequest:
  given JsonDecoder[SaveFileRequest] = DeriveJsonDecoder.gen[SaveFileRequest]

object FileInfo:
  given JsonEncoder[FileInfo] = DeriveJsonEncoder.gen[FileInfo]

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

  private def checkAuth(adminSvc: AdminService, req: Request): UIO[Boolean] =
    adminSvc.isAuthenticated(extractToken(req).getOrElse(""))

  private def decodeSaveRequest(body: String): Either[String, SaveFileRequest] =
    summon[JsonDecoder[SaveFileRequest]].decodeJson(body)

  private def decodeOtpRequest(body: String): Either[String, OtpRequest] =
    summon[JsonDecoder[OtpRequest]].decodeJson(body)

  private case class Dependencies(
    adminSvc: AdminService,
    contentSvc: ContentService,
    portfolio: PortfolioService
  )

  val routes: ZIO[AdminService & ContentService & PortfolioService & Client, Nothing, Routes[Any, Nothing]] =
    for
      deps <- ZIO.service[AdminService]
        .zip(ZIO.service[ContentService])
        .zip(ZIO.service[PortfolioService])
        .map { case ((a, c), p) => Dependencies(a, c, p) }
    yield Routes(

      // GET /admin - pagina di login
      Method.GET / "admin" ->
        Handler.fromFunction { (_: Request) =>
          Response(
            status = Status.Ok,
            headers = Headers(Header.ContentType(MediaType.text.html)),
            body = Body.fromString(AdminViews.loginPage)
          )
        },

      // GET /admin/dashboard
      Method.GET / "admin" / "dashboard" ->
        Handler.fromFunctionZIO { (req: Request) =>
          extractToken(req) match {
            case None =>
              ZIO.succeed(Response.redirect(URL.root / "admin"))
            case Some(token) =>
              deps.adminSvc.isAuthenticated(token).flatMap {
                case false => ZIO.succeed(Response.redirect(URL.root / "admin"))
                case true =>
                  deps.contentSvc.isWritable.map { writable =>
                    Response(
                      status = Status.Ok,
                      headers = Headers(Header.ContentType(MediaType.text.html)),
                      body = Body.fromString(AdminViews.dashboardPage(writable, isGitHubMode = true))
                    )
                  }
              }
          }
        },

      // POST /admin/api/request-otp
      Method.POST / "admin" / "api" / "request-otp" ->
        Handler.fromFunctionZIO { (_: Request) =>
          deps.adminSvc.requestOtp.map {
            case Some(_) => Response.json("""{"message":"OTP sent"}""")
            case None    => Response.json("""{"error":"OTP generation error"}""").status(Status.InternalServerError)
          }
        },

      // POST /admin/api/verify-otp
      Method.POST / "admin" / "api" / "verify-otp" ->
        Handler.fromFunctionZIO { (req: Request) =>
          req.body.asString.flatMap { rawBody =>
            decodeOtpRequest(rawBody) match {
              case Left(_) =>
                ZIO.succeed(
                  Response.json("""{"error":"Missing 'otp' field"}""").status(Status.BadRequest)
                )
              case Right(otpReq) =>
                deps.adminSvc.verifyOtp(otpReq.otp).flatMap {
                  case None =>
                    ZIO.succeed(
                      Response.json("""{"error":"Invalid code"}""").status(Status.Unauthorized)
                    )
                  case Some(token) =>
                    val cookie = Cookie.Response(
                      name = "admin_session",
                      content = token,
                      maxAge = Some(java.time.Duration.ofHours(AdminConfig.sessionExpiryHours)),
                      isHttpOnly = true,
                      sameSite = Some(Cookie.SameSite.Strict),
                      path = Some(Path.root / "admin")
                    )
                    ZIO.succeed(Response.json("""{"success":true}""").addCookie(cookie))
                }
            }
          }.orDie
        },

      // POST /admin/api/logout
      Method.POST / "admin" / "api" / "logout" ->
        Handler.fromFunctionZIO { (req: Request) =>
          extractToken(req) match {
            case None =>
              ZIO.succeed(Response.redirect(URL.root / "admin"))
            case Some(token) =>
              deps.adminSvc.logout(token).as(
                Response.json("""{"success":true}""").addCookie(
                  Cookie.Response(
                    name = "admin_session",
                    content = "",
                    maxAge = Some(java.time.Duration.ZERO),
                    path = Some(Path.root / "admin")
                  )
                )
              )
          }
        },

      // POST /admin/api/files (salvataggio file)
      Method.POST / "admin" / "api" / "files" ->
        Handler.fromFunctionZIO { (req: Request) =>
          checkAuth(deps.adminSvc, req).flatMap {
            case false => ZIO.succeed(notAuthenticated)
            case true =>
              req.body.asString.flatMap { body =>
                decodeSaveRequest(body) match {
                  case Left(parseErr) =>
                    ZIO.succeed(
                      Response.json(s"""{"error":"Invalid JSON: $parseErr"}""").status(Status.BadRequest)
                    )
                  case Right(saveReq) =>
                    deps.contentSvc.writeFile(saveReq.path, saveReq.content).flatMap { commit =>
                      deps.portfolio.reload.catchAll(_ => ZIO.unit) *>
                        ZIO.succeed(jsonResponse(SaveResponse(
                          success = true,
                          message = "File saved to GitHub!",
                          commitUrl = commit.html_url,
                          rebuildNote = "The site will update automatically on Render in ~1-2 minutes."
                        )))
                    }.catchAll { err =>
                      ZIO.succeed(
                        Response.json(s"""{"error":"Save error: ${err.getMessage}"}""")
                          .status(Status.InternalServerError)
                      )
                    }
                }
              }.orDie
          }
        },

      // GET /admin/api/files/:section/:filename
      Method.GET / "admin" / "api" / "files" / string("section") / string("filename") ->
        Handler.fromFunctionZIO { (section: String, filename: String, req: Request) =>
          checkAuth(deps.adminSvc, req).flatMap {
            case false => ZIO.succeed(notAuthenticated)
            case true =>
              deps.contentSvc.readFile(s"$section/$filename").flatMap { content =>
                ZIO.succeed(jsonResponse(FileContentResponse(s"$section/$filename", content)))
              }.catchAll { _ =>
                ZIO.succeed(
                  Response.json(s"""{"error":"Not found"}""").status(Status.NotFound)
                )
              }
          }
        }
    )