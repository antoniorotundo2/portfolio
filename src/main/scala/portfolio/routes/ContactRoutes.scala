package portfolio.routes

import portfolio.services.ContactService
import zio.*
import zio.http.*
import zio.json.*

case class ContactRequest(
    name: String,
    email: String,
    message: String,
    consent: Boolean,
    website: String // honeypot: deve arrivare vuoto, i bot lo compilano
)
object ContactRequest:
  given JsonDecoder[ContactRequest] = DeriveJsonDecoder.gen[ContactRequest]

object ContactRoutes:

  private val emailRegex = "^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$".r

  private def clientKey(req: Request): String =
    req.headers.get("X-Forwarded-For").map(_.split(",").head.trim).getOrElse("unknown")

  private def errorResponse(status: Status, msg: String): Response =
    Response.json(s"""{"error":"$msg"}""").status(status)

  /** Validazione lato server: mai fidarsi solo dei controlli HTML del client. */
  private def validate(c: ContactRequest): Either[String, Unit] =
    if c.website.nonEmpty then
      Left("Invalid request") // honeypot compilato → probabile bot, messaggio generico
    else if c.name.trim.isEmpty || c.name.length > 100 then Left("Please provide a valid name")
    else if !emailRegex.matches(c.email.trim) then Left("Please provide a valid email address")
    else if c.message.trim.isEmpty || c.message.length > 5000 then
      Left("Message must be between 1 and 5000 characters")
    else if !c.consent then Left("You must consent to data processing to send a message")
    else Right(())

  val routes: Routes[ContactService, Nothing] =
    Routes(
      Method.POST / "api" / "contact" ->
        Handler.fromFunctionZIO { (req: Request) =>
          ZIO.serviceWithZIO[ContactService] { svc =>
            req.body.asString
              .flatMap { raw =>
                raw.fromJson[ContactRequest] match
                  case Left(_) =>
                    ZIO.succeed(errorResponse(Status.BadRequest, "Invalid request body"))
                  case Right(c) =>
                    validate(c) match
                      case Left(err) => ZIO.succeed(errorResponse(Status.BadRequest, err))
                      case Right(()) =>
                        svc.tryAcquire(clientKey(req)).flatMap {
                          case false =>
                            ZIO.succeed(
                              errorResponse(
                                Status.TooManyRequests,
                                "Too many requests, please wait a moment"
                              )
                            )
                          case true =>
                            svc
                              .send(c.name.trim, c.email.trim, c.message.trim)
                              .as(Response.json("""{"success":true}"""))
                              .catchAllCause { cause =>
                                ZIO.logErrorCause("Contact form send failed", cause) *>
                                  ZIO.succeed(
                                    errorResponse(
                                      Status.InternalServerError,
                                      "Could not send message, please try again later"
                                    )
                                  )
                              }
                        }
              }
              .catchAllCause { cause =>
                ZIO.logErrorCause("Contact form request failed", cause) *>
                  ZIO.succeed(errorResponse(Status.BadRequest, "Invalid request"))
              }
          }
        }
    )
