package portfolio.admin

import portfolio.services.PortfolioService
import zio.*
import zio.http.*
import zio.json.*

case class OtpRequest(otp: String)
object OtpRequest:
  given JsonDecoder[OtpRequest] = DeriveJsonDecoder.gen[OtpRequest]

case class SaveFileRequest(path: String, content: String)
object SaveFileRequest:
  given JsonDecoder[SaveFileRequest] = DeriveJsonDecoder.gen[SaveFileRequest]

case class FileInfo(path: String, displayName: String, section: String)
object FileInfo:
  given JsonEncoder[FileInfo] = DeriveJsonEncoder.gen[FileInfo]

case class FilesListResponse(files: List[FileInfo], writable: Boolean, isGitHubMode: Boolean)
object FilesListResponse:
  given JsonEncoder[FilesListResponse] = DeriveJsonEncoder.gen[FilesListResponse]

case class FileContentResponse(path: String, content: String)
object FileContentResponse:
  given JsonEncoder[FileContentResponse] = DeriveJsonEncoder.gen[FileContentResponse]

case class SaveResponse(success: Boolean, message: String, commitUrl: String, rebuildNote: String)
object SaveResponse:
  given JsonEncoder[SaveResponse] = DeriveJsonEncoder.gen[SaveResponse]

object AdminRoutes:

  private def extractToken(req: Request): Option[String] =
    req.header(Header.Cookie).flatMap(_.value.toCookieMap.get("admin_session"))

  private def jsonResponse[A](value: A)(using encoder: JsonEncoder[A]): Response =
    Response.json(encoder.toJson(value))

  private val notAuthenticated: UIO[Response] =
    ZIO.succeed(Response(status = Status.Unauthorized, body = Body.fromString("""{"error":"Not authenticated"}""")))

  private def withAuth(adminSvc: AdminService, req: Request)(action: UIO[Response]): UIO[Response] =
    adminSvc.isAuthenticated(extractToken(req).getOrElse("")).flatMap {
      case false => notAuthenticated
      case true  => action
    }

  private def decodeSaveRequest(body: String): Either[String, SaveFileRequest] =
    summon[JsonDecoder[SaveFileRequest]].decodeJson(body)

  private def decodeOtpRequest(body: String): Either[String, OtpRequest] =
    summon[JsonDecoder[OtpRequest]].decodeJson(body)

  private def handleSaveFile(
    contentSvc: ContentService,
    portfolio: PortfolioService,
    body: String
  ): Task[Response] =
    decodeSaveRequest(body) match {
      case Left(parseErr) =>
        ZIO.succeed(Response.json(s"""{"error":"Invalid JSON: $parseErr"}""").status(Status.BadRequest))
      case Right(saveReq) =>
        contentSvc.writeFile(saveReq.path, saveReq.content).flatMap { commit =>
          portfolio.reload.catchAll(_ => ZIO.unit) *>
          ZIO.succeed(jsonResponse(SaveResponse(
            success     = true,
            message     = "File saved to GitHub!",
            commitUrl   = commit.html_url,
            rebuildNote = "The site will update automatically on Render in ~1-2 minutes."
          )))
        }.catchAll { saveErr =>
          ZIO.succeed(Response.json(s"""{"error":"Save error: ${saveErr.getMessage}"}""").status(Status.InternalServerError))
        }
    }

  val routes: ZIO[AdminService & ContentService & PortfolioService & Client, Nothing, Routes[Any, Nothing]] =
    for
      adminSvc   <- ZIO.service[AdminService]
      contentSvc <- ZIO.service[ContentService]
      portfolio  <- ZIO.service[PortfolioService]
    yield Routes(

      Method.GET / "admin" -> Handler.fromFunctionZIO[Request] { _ =>
        ZIO.succeed(Response(
          status  = Status.Ok,
          headers = Headers(Header.ContentType(MediaType.text.html)),
          body    = Body.fromString(AdminViews.loginPage)
        ))
      },

      Method.GET / "admin" / "dashboard" -> Handler.fromFunctionZIO[Request] { req =>
        extractToken(req) match {
          case None =>
            ZIO.succeed(Response.redirect(URL.root / "admin"))
          case Some(token) =>
            adminSvc.isAuthenticated(token).flatMap {
              case false => ZIO.succeed(Response.redirect(URL.root / "admin"))
              case true  => contentSvc.isWritable.map { writable =>
                Response(
                  status  = Status.Ok,
                  headers = Headers(Header.ContentType(MediaType.text.html)),
                  body    = Body.fromString(AdminViews.dashboardPage(writable, isGitHubMode = true))
                )
              }
            }
        }
      },

      Method.POST / "admin" / "api" / "request-otp" -> Handler.fromFunctionZIO[Request] { _ =>
        adminSvc.requestOtp.flatMap {
          case Some(_) => ZIO.succeed(Response.json("""{"message":"OTP sent"}"""))
          case None    => ZIO.succeed(Response.json("""{"error":"OTP generation error"}""").status(Status.InternalServerError))
        }.orDie
      },

      Method.POST / "admin" / "api" / "verify-otp" -> Handler.fromFunctionZIO[Request] { req =>
        req.body.asString.flatMap { rawBody =>
          decodeOtpRequest(rawBody) match {
            case Left(_) =>
              ZIO.succeed(Response.json("""{"error":"Missing 'otp' field"}""").status(Status.BadRequest))
            case Right(otpReq) =>
              adminSvc.verifyOtp(otpReq.otp).flatMap {
                case None =>
                  ZIO.succeed(Response.json("""{"error":"Invalid code"}""").status(Status.Unauthorized))
                case Some(token) =>
                  val cookie = Cookie.Response(
                    name       = "admin_session",
                    content    = token,
                    maxAge     = Some(java.time.Duration.ofHours(AdminConfig.sessionExpiryHours)),
                    isHttpOnly = true,
                    sameSite   = Some(Cookie.SameSite.Strict),
                    path       = Some(Path.root / "admin")
                  )
                  ZIO.succeed(Response.json("""{"success":true}""").addCookie(cookie))
              }
          }
        }.orDie
      },

      Method.POST / "admin" / "api" / "logout" -> Handler.fromFunctionZIO[Request] { req =>
        extractToken(req) match {
          case None =>
            ZIO.succeed(Response.redirect(URL.root / "admin"))
          case Some(token) =>
            adminSvc.logout(token).as(
              Response.json("""{"success":true}""").addCookie(
                Cookie.Response(
                  name    = "admin_session",
                  content = "",
                  maxAge  = Some(java.time.Duration.ZERO),
                  path    = Some(Path.root / "admin")
                )
              )
            )
        }
      },

      Method.GET / "admin" / "api" / "files" -> Handler.fromFunctionZIO[Request] { req =>
        withAuth(adminSvc, req) {
          contentSvc.listFiles.flatMap { files =>
            contentSvc.isWritable.map { writable =>
              jsonResponse(FilesListResponse(
                files.map(f => FileInfo(f.relativePath, f.displayName, f.section)),
                writable,
                isGitHubMode = true
              ))
            }
          }.orDie
        }
      },

      Method.GET / "admin" / "api" / "files" / string("section") / string("filename") ->
        Handler.fromFunctionZIO[(String, String, Request)] { (section, filename, req) =>
          withAuth(adminSvc, req) {
            contentSvc.readFile(s"$section/$filename").flatMap { content =>
              ZIO.succeed(jsonResponse(FileContentResponse(s"$section/$filename", content)))
            }.catchAll { _ =>
              ZIO.succeed(Response.json(s"""{"error":"Not found"}""").status(Status.NotFound))
            }
          }
        },

      Method.POST / "admin" / "api" / "files" -> Handler.fromFunctionZIO[Request] { req =>
        withAuth(adminSvc, req) {
          req.body.asString.flatMap { body =>
            handleSaveFile(contentSvc, portfolio, body)
          }.orDie
        }
      }
    )
