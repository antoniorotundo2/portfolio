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

  private def requireAuth(adminSvc: AdminService)(h: Request => Task[Response]): Request => UIO[Response] = req =>
    extractToken(req) match
      case None =>
        ZIO.succeed(Response.status(Status.Unauthorized).body(Body.fromString("""{"error":"Not authenticated"}""")))
      case Some(token) =>
        adminSvc.isAuthenticated(token).flatMap {
          case true  => h(req)
          case false => ZIO.succeed(Response.status(Status.Unauthorized).body(Body.fromString("""{"error":"Session expired"}""")))
        }.orDie

  private def jsonResponse[A](value: A)(using encoder: JsonEncoder[A]): Response =
    Response.json(encoder.toJson(value))

  private def handleSaveFile(
    adminSvc: AdminService,
    contentSvc: ContentService,
    portfolio: PortfolioService,
    body: String
  ): Task[Response] =
    body.fromJson[SaveFileRequest](using SaveFileRequest.given) match
      case Left(err) =>
        ZIO.succeed(Response.json(s"""{"error":"Invalid JSON: $err"}""").status(Status.BadRequest))
      case Right(saveReq) =>
        contentSvc.writeFile(saveReq.path, saveReq.content).flatMap { commit =>
          portfolio.reload.catchAll(_ => ZIO.unit) *>
          ZIO.succeed {
            val resp = SaveResponse(success = true, message = "File saved to GitHub!", commitUrl = commit.html_url, rebuildNote = "The site will update automatically on Render in ~1-2 minutes.")
            jsonResponse(resp)(using SaveResponse.given)
          }
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
        ZIO.succeed(Response(status = Status.Ok, headers = Headers(Header.ContentType(MediaType.text.html)), body = Body.fromString(AdminViews.loginPage)))
      },
      Method.GET / "admin" / "dashboard" -> Handler.fromFunctionZIO[Request] { req =>
        extractToken(req) match
          case Some(token) =>
            adminSvc.isAuthenticated(token).flatMap {
              case true =>
                contentSvc.isWritable.map { writable =>
                  Response(status = Status.Ok, headers = Headers(Header.ContentType(MediaType.text.html)), body = Body.fromString(AdminViews.dashboardPage(writable, isGitHubMode = true)))
                }.orDie
              case false =>
                ZIO.succeed(Response.redirect(URL.root / "admin"))
            }
          case None =>
            ZIO.succeed(Response.redirect(URL.root / "admin"))
      },
      Method.POST / "admin" / "api" / "request-otp" -> Handler.fromFunctionZIO[Request] { _ =>
        adminSvc.requestOtp.map {
          case Some(_) => Response.json("""{"message":"OTP sent"}""")
          case None    => Response.json("""{"error":"OTP generation error"}""").status(Status.InternalServerError)
        }.orDie
      },
      Method.POST / "admin" / "api" / "verify-otp" -> Handler.fromFunctionZIO[Request] { req =>
        req.body.asString.flatMap { body =>
          body.fromJson[OtpRequest](using OtpRequest.given).toOption.flatMap(_.otp) match
            case None =>
              ZIO.succeed(Response.json("""{"error":"Missing 'otp' field"}""").status(Status.BadRequest))
            case Some(otpCode) =>
              adminSvc.verifyOtp(otpCode).map {
                case Some(token) =>
                  val cookie = Cookie.Response(
                    name       = "admin_session",
                    content    = token,
                    maxAge     = Some(java.time.Duration.ofHours(AdminConfig.sessionExpiryHours)),
                    isHttpOnly = true,
                    secure     = true,
                    sameSite   = Some(Cookie.SameSite.Strict),
                    path       = Some(Path.root / "admin")
                  )
                  Response.json("""{"success":true}""").addCookie(cookie)
                case None =>
                  Response.json("""{"error":"Invalid code"}""").status(Status.Unauthorized)
              }
        }.orDie
      },
      Method.POST / "admin" / "api" / "logout" -> Handler.fromFunctionZIO[Request] { req =>
        extractToken(req) match
          case Some(token) =>
            adminSvc.logout(token).as(
              Response.json("""{"success":true}""").addCookie(
                Cookie.Response(name = "admin_session", content = "", maxAge = Some(java.time.Duration.ZERO), path = Some(Path.root / "admin"))
              )
            )
          case None =>
            ZIO.succeed(Response.redirect(URL.root / "admin"))
      },
      Method.GET / "admin" / "api" / "files" -> Handler.fromFunctionZIO[Request] { req =>
        requireAuth(adminSvc) { _ =>
          contentSvc.listFiles.flatMap { files =>
            contentSvc.isWritable.map { writable =>
              val resp = FilesListResponse(files.map(f => FileInfo(f.relativePath, f.displayName, f.section)), writable, isGitHubMode = true)
              jsonResponse(resp)(using FilesListResponse.given)
            }
          }
        }(req)
      },
      Method.GET / "admin" / "api" / "files" / string("section") / string("filename") -> Handler.fromFunctionZIO[(String, String, Request)] { (section, filename, req) =>
        requireAuth(adminSvc) { _ =>
          contentSvc.readFile(s"$section/$filename").flatMap { content =>
            ZIO.succeed(jsonResponse(FileContentResponse(s"$section/$filename", content))(using FileContentResponse.given))
          }.catchAll { err =>
            ZIO.succeed(Response.json(s"""{"error":"${err.getMessage}"}""").status(Status.NotFound))
          }
        }(req)
      },
      Method.POST / "admin" / "api" / "files" -> Handler.fromFunctionZIO[Request] { req =>
        requireAuth(adminSvc) { _ =>
          req.body.asString.flatMap { body =>
            handleSaveFile(adminSvc, contentSvc, portfolio, body)
          }
        }(req)
      }
    )
