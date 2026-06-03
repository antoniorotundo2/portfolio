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

  private def handleSaveFile(
    contentSvc: ContentService,
    portfolio: PortfolioService,
    body: String
  ): Task[Response] =
    body.fromJson[SaveFileRequest](using SaveFileRequest.given) match
      case Left(err) =>
        ZIO.succeed(Response.json(s"""{"error":"Invalid JSON: $err"}""").status(Status.BadRequest))
      case Right(parsed) =>
        contentSvc.writeFile(parsed.path, parsed.content).flatMap { commit =>
          portfolio.reload.catchAll(_ => ZIO.unit) *>
          ZIO.succeed(jsonResponse(SaveResponse(
            success     = true,
            message     = "File saved to GitHub!",
            commitUrl   = commit.html_url,
            rebuildNote = "The site will update automatically on Render in ~1-2 minutes."
          ))(using SaveResponse.given))
        }.catchAll { err =>
          ZIO.succeed(Response.json(s"""{"error":"Save error: ${err.getMessage}"}""").status(Status.InternalServerError))
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
        extractToken(req) match
          case None =>
            ZIO.succeed(Response.redirect(URL.root / "admin"))
          case Some(token) =>
            adminSvc.isAuthenticated(token).flatMap {
              case false =>
                ZIO.succeed(Response.redirect(URL.root / "admin"))
              case true =>
                contentSvc.isWritable.flatMap { writable =>
                  ZIO.succeed(Response(
                    status  = Status.Ok,
                    headers = Headers(Header.ContentType(MediaType.text.html)),
                    body    = Body.fromString(AdminViews.dashboardPage(writable, isGitHubMode = true))
                  ))
                }.orDie
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
          rawBody.fromJson[OtpRequest](using OtpRequest.given) match
            case Left(_) =>
              ZIO.succeed(Response.json("""{"error":"Missing 'otp' field"}""").status(Status.BadRequest))
            case Right(parsed) =>
              adminSvc.verifyOtp(parsed.otp).flatMap {
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
        }.orDie
      },

      Method.POST / "admin" / "api" / "logout" -> Handler.fromFunctionZIO[Request] { req =>
        extractToken(req) match
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
      },

      Method.GET / "admin" / "api" / "files" -> Handler.fromFunctionZIO[Request] { req =>
        (for
          _        <- adminSvc.isAuthenticated(extractToken(req).getOrElse("")).flatMap {
                        case false => ZIO.fail(Response(status = Status.Unauthorized, body = Body.fromString("""{"error":"Not authenticated"}""")))
                        case true  => ZIO.unit
                      }
          files    <- contentSvc.listFiles
          writable <- contentSvc.isWritable
        yield jsonResponse(FilesListResponse(
          files.map(f => FileInfo(f.relativePath, f.displayName, f.section)),
          writable,
          isGitHubMode = true
        ))(using FilesListResponse.given))
        .catchAll(resp => ZIO.succeed(resp))
      },

      Method.GET / "admin" / "api" / "files" / string("section") / string("filename") ->
        Handler.fromFunctionZIO[(String, String, Request)] { (section, filename, req) =>
          (for
            _ <- adminSvc.isAuthenticated(extractToken(req).getOrElse("")).flatMap {
                   case false => ZIO.fail(Response(status = Status.Unauthorized, body = Body.fromString("""{"error":"Not authenticated"}""")))
                   case true  => ZIO.unit
                 }
            content <- contentSvc.readFile(s"$section/$filename")
          yield jsonResponse(FileContentResponse(s"$section/$filename", content))(using FileContentResponse.given))
          .catchAll { _ =>
            ZIO.succeed(Response.json(s"""{"error":"Not found"}""").status(Status.NotFound))
          }
        },

      Method.POST / "admin" / "api" / "files" -> Handler.fromFunctionZIO[Request] { req =>
        (for
          _ <- adminSvc.isAuthenticated(extractToken(req).getOrElse("")).flatMap {
                 case false => ZIO.fail(Response(status = Status.Unauthorized, body = Body.fromString("""{"error":"Not authenticated"}""")))
                 case true  => ZIO.unit
               }
          body     <- req.body.asString
          response <- handleSaveFile(contentSvc, portfolio, body)
        yield response)
        .catchAll(resp => ZIO.succeed(resp))
      }
    )
