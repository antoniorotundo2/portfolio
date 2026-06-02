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

  private def requireAuth(adminSvc: AdminService)(handler: Request => Task[Response]): Request => Task[Response] = req =>
    extractToken(req) match
      case None => ZIO.succeed(Response.status(Status.Unauthorized).body(Body.fromString("""{"error":"Non autenticato"}""")))
      case Some(token) => adminSvc.isAuthenticated(token).flatMap {
        case true  => handler(req)
        case false => ZIO.succeed(Response.status(Status.Unauthorized).body(Body.fromString("""{"error":"Sessione scaduta"}""")))
      }

  private def jsonResponse[A](value: A)(using encoder: JsonEncoder[A]): Response =
    Response.json(encoder.toJson(value))

  val routes: ZIO[AdminService & ContentService & PortfolioService & Client, Nothing, Routes[Any, Nothing]] =
    for
      adminSvc   <- ZIO.service[AdminService]
      contentSvc <- ZIO.service[ContentService]
      portfolio  <- ZIO.service[PortfolioService]
    yield Routes(
      Method.GET / "admin" -> handler { (_: Request) =>
        ZIO.succeed(Response(status = Status.Ok, headers = Headers(Header.ContentType(MediaType.text.html)), body = Body.fromString(AdminViews.loginPage)))
      },
      Method.GET / "admin" / "dashboard" -> handler { (req: Request) =>
        extractToken(req) match
          case Some(token) => adminSvc.isAuthenticated(token).flatMap {
            case true => contentSvc.isWritable.flatMap { writable =>
              ZIO.succeed(Response(status = Status.Ok, headers = Headers(Header.ContentType(MediaType.text.html)), body = Body.fromString(AdminViews.dashboardPage(writable, isGitHubMode = true))))
            }
            case false => ZIO.succeed(Response.redirect(URL.root / "admin"))
          }
          case None => ZIO.succeed(Response.redirect(URL.root / "admin"))
      },
      Method.POST / "admin" / "api" / "request-otp" -> handler { (_: Request) =>
        adminSvc.requestOtp.flatMap {
          case Some(_) => ZIO.succeed(Response.json("""{"message":"OTP inviato"}"""))
          case None => ZIO.succeed(Response.json("""{"error":"Errore generazione OTP"}""").status(Status.InternalServerError))
        }
      },
      Method.POST / "admin" / "api" / "verify-otp" -> handler { (req: Request) =>
        req.body.asString.flatMap { body =>
          val otp = body.fromJson[OtpRequest](using OtpRequest.given).toOption.flatMap(_.otp)
          otp match
            case None => ZIO.succeed(Response.json("""{"error":"Campo 'otp' mancante"}""").status(Status.BadRequest))
            case Some(code) => adminSvc.verifyOtp(code).flatMap {
              case Some(token) =>
                val cookie = Cookie.Response(name = "admin_session", content = token, maxAge = Some(java.time.Duration.ofHours(AdminConfig.sessionExpiryHours)), isHttpOnly = true, secure = true, sameSite = Some(Cookie.SameSite.Strict), path = Some(Path.root / "admin"))
                ZIO.succeed(Response.json("""{"success":true}""").addCookie(cookie))
              case None => ZIO.succeed(Response.json("""{"error":"Codice non valido"}""").status(Status.Unauthorized))
            }
        }
      },
      Method.POST / "admin" / "api" / "logout" -> handler { (req: Request) =>
        extractToken(req) match
          case Some(token) => adminSvc.logout(token) *> ZIO.succeed(Response.json("""{"success":true}""").addCookie(Cookie.Response(name = "admin_session", content = "", maxAge = Some(java.time.Duration.ZERO), path = Some(Path.root / "admin"))))
          case None => ZIO.succeed(Response.redirect(URL.root / "admin"))
      },
      Method.GET / "admin" / "api" / "files" -> handler { (req: Request) =>
        requireAuth(adminSvc) { _ =>
          contentSvc.listFiles.flatMap { files =>
            contentSvc.isWritable.map { writable =>
              val resp = FilesListResponse(files.map(f => FileInfo(f.relativePath, f.displayName, f.section)), writable, isGitHubMode = true)
              jsonResponse(resp)(using FilesListResponse.given)
            }
          }
        }(req)
      },
      Method.GET / "admin" / "api" / "files" / string("section") / string("filename") -> handler { (section: String, filename: String, req: Request) =>
        requireAuth(adminSvc) { _ =>
          contentSvc.readFile(s"$section/$filename").flatMap { content =>
            val resp = FileContentResponse(s"$section/$filename", content)
            ZIO.succeed(jsonResponse(resp)(using FileContentResponse.given))
          }.catchAll { err => ZIO.succeed(Response.json(s"""{"error":"${err.getMessage}"}""").status(Status.NotFound)) }
        }(req)
      },
      Method.POST / "admin" / "api" / "files" -> handler { (req: Request) =>
        requireAuth(adminSvc) { _ =>
          req.body.asString.flatMap { body =>
            body.fromJson[SaveFileRequest](using SaveFileRequest.given) match
              case Left(err) => ZIO.succeed(Response.json(s"""{"error":"JSON non valido: $err"}""").status(Status.BadRequest))
              case Right(saveReq) => contentSvc.writeFile(saveReq.path, saveReq.content).flatMap { commit =>
                portfolio.reload.catchAll(_ => ZIO.unit) *>
                {
                  val resp = SaveResponse(success = true, message = "File salvato su GitHub!", commitUrl = commit.html_url, rebuildNote = "Il sito si aggiornerà automaticamente su Render tra ~1-2 minuti.")
                  ZIO.succeed(jsonResponse(resp)(using SaveResponse.given))
                }
              }.catchAll { err => ZIO.succeed(Response.json(s"""{"error":"Errore salvataggio: ${err.getMessage}"}""").status(Status.InternalServerError)) }
          }
        }(req)
      }
    )