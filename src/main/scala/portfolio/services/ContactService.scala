package portfolio.services

import portfolio.admin.AdminConfig
import zio.*
import zio.http.*
import zio.json.*
import java.time.Instant

trait ContactService:
  def send(name: String, email: String, message: String): Task[Unit]

  /** true se il client può inviare ora (e la richiesta viene registrata), false se in cooldown. */
  def tryAcquire(clientKey: String): UIO[Boolean]

object ContactServiceLive:

  // Intervallo minimo tra due invii dallo stesso client: rate-limit anti-spam / quota Resend.
  private val cooldownSeconds = 30L

  val layer: ZLayer[Client, Nothing, ContactService] =
    ZLayer.fromZIO {
      for
        lastSent <- Ref.make(Map.empty[String, Instant])
        client   <- ZIO.service[Client]
      yield Live(lastSent, client)
    }

  private case class ResendRequest(
      from: String,
      to: String,
      subject: String,
      text: String,
      reply_to: String
  )
  private object ResendRequest:
    given JsonEncoder[ResendRequest] = DeriveJsonEncoder.gen[ResendRequest]

  private final class Live(lastSent: Ref[Map[String, Instant]], client: Client)
      extends ContactService:

    def tryAcquire(clientKey: String): UIO[Boolean] =
      val now = Instant.now()
      lastSent.modify { m =>
        m.get(clientKey) match
          case Some(t) if now.isBefore(t.plusSeconds(cooldownSeconds)) => (false, m)
          case _ => (true, m.updated(clientKey, now))
      }

    def send(name: String, email: String, message: String): Task[Unit] =
      val body = ResendRequest(
        from = AdminConfig.smtpFrom,
        to = AdminConfig.adminEmail,
        subject = s"Portfolio contact: $name",
        text = s"From: $name <$email>\n\n$message",
        reply_to = email
      )
      ZIO
        .scoped {
          client
            .batched(
              Request(
                method = Method.POST,
                url = URL.decode("https://api.resend.com/emails").toOption.get,
                headers = Headers(
                  Header.Custom("Authorization", s"Bearer ${AdminConfig.smtpPassword}"),
                  Header.ContentType(MediaType.application.json)
                ),
                body = Body.fromString(body.toJson)
              )
            )
            .flatMap { response =>
              if response.status.isSuccess then ZIO.unit
              else
                response.body.asString.flatMap(b =>
                  ZIO.fail(new RuntimeException(s"Resend error: ${response.status} - $b"))
                )
            }
        }
        .timeoutFail(new RuntimeException("Resend timeout"))(20.seconds)
