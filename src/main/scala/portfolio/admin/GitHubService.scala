// src/main/scala/portfolio/admin/GitHubService.scala
package portfolio.admin

import zio.*
import zio.http.*
import zio.json.*
import java.time.Instant
import java.util.Base64

// ── DTOs con given espliciti ─────────────────────────────────────────────

case class GitHubFileResponse(
  sha: String,
  content: Option[String],
  encoding: Option[String],
  download_url: Option[String]
)
object GitHubFileResponse:
  given JsonDecoder[GitHubFileResponse] = DeriveJsonDecoder.gen[GitHubFileResponse]

case class GitHubAuthor(
  name: String,
  email: String,
  date: String = Instant.now().toString
)
object GitHubAuthor:
  given JsonEncoder[GitHubAuthor] = DeriveJsonEncoder.gen[GitHubAuthor]

case class GitHubCreateUpdateRequest(
  message: String,
  content: String,
  sha: Option[String],
  branch: String,
  author: Option[GitHubAuthor] = None,
  committer: Option[GitHubAuthor] = None
)
object GitHubCreateUpdateRequest:
  given JsonEncoder[GitHubCreateUpdateRequest] = DeriveJsonEncoder.gen[GitHubCreateUpdateRequest]

case class GitHubCommitResult(
  sha: String,
  html_url: String
)
object GitHubCommitResult:
  given JsonDecoder[GitHubCommitResult] = DeriveJsonDecoder.gen[GitHubCommitResult]

case class GitHubCommitResponse(
  commit: GitHubCommitResult,
  html_url: String
)
object GitHubCommitResponse:
  given JsonDecoder[GitHubCommitResponse] = DeriveJsonDecoder.gen[GitHubCommitResponse]

// ── Algebra ──────────────────────────────────────────────────────────────

trait GitHubService:
  def getFileContent(path: String): Task[String]
  def updateFile(path: String, content: String, commitMessage: String): Task[GitHubCommitResult]

// ── Implementazione ──────────────────────────────────────────────────────

object GitHubServiceLive:
  val layer: ZLayer[Any, Nothing, GitHubService] = ZLayer.succeed(Live())

  private final class Live extends GitHubService:
    private val baseUrl = "https://api.github.com"
    private val headers = Headers(
      Header.ContentType(MediaType.application.json),
      Header.Authorization.Credential("token", AdminConfig.githubToken),
      Header.Custom("Accept", "application/vnd.github.v3+json"),
      Header.Custom("User-Agent", "Portfolio-Admin/1.0")
    )

    private def apiPath(path: String): String = s"/repos/${AdminConfig.githubOwner}/${AdminConfig.githubRepo}$path"
    private def encodeBase64(s: String): String = Base64.getEncoder.encodeToString(s.getBytes(java.nio.charset.StandardCharsets.UTF_8))
    private def decodeBase64(s: String): String = new String(Base64.getDecoder.decode(s), java.nio.charset.StandardCharsets.UTF_8)

    def getFileContent(path: String): Task[String] =
      val fullPath = s"${AdminConfig.contentBasePath}/$path"
      val url = baseUrl + apiPath(s"/contents/$fullPath") + s"?ref=${AdminConfig.githubBranch}"
      for
        response <- Client.request(Request.get(url).addHeaders(headers))
        _ <- ZIO.unless(response.status.isSuccess)(ZIO.fail(new RuntimeException(s"GitHub API error: ${response.status}")))
        body <- response.body.asString
        fileResp <- ZIO.fromEither(body.fromJson[GitHubFileResponse])
        content <- fileResp.content match
          case Some(b64) => ZIO.succeed(decodeBase64(b64.replace("\n", "")))
          case None => fileResp.download_url match
            case Some(dlUrl) => Client.request(Request.get(dlUrl)).flatMap(_.body.asString)
            case None => ZIO.fail(new RuntimeException("Nessun contenuto disponibile"))
      yield content

    def updateFile(path: String, content: String, commitMessage: String): Task[GitHubCommitResult] =
      val fullPath = s"${AdminConfig.contentBasePath}/$path"
      val url = baseUrl + apiPath(s"/contents/$fullPath")
      
      // 👇 Step 1: Ottieni SHA esistente (fuori dal for per chiarezza)
      val getSha = Client.request(Request.get(url + s"?ref=${AdminConfig.githubBranch}").addHeaders(headers)).flatMap { resp =>
        if resp.status == Status.NotFound then ZIO.succeed(None)
        else if resp.status.isSuccess then
          resp.body.asString.flatMap { body =>
            ZIO.fromEither(body.fromJson[GitHubFileResponse]) match
              case Right(f) => ZIO.succeed(Some(f.sha))
              case Left(err) => ZIO.fail(new RuntimeException(s"Decode error: $err"))
          }
        else ZIO.fail(new RuntimeException(s"GitHub API error: ${resp.status}"))
      }.catchAll(_ => ZIO.succeed(None))

      for
        existingSha <- getSha
        
        // 👇 Step 2: Crea la request con tipo esplicito (aiuta il compilatore)
        request: GitHubCreateUpdateRequest = GitHubCreateUpdateRequest(
          message = s"${AdminConfig.commitMessagePrefix} $commitMessage",
          content = encodeBase64(content),
          sha = existingSha,
          branch = AdminConfig.githubBranch,
          author = Some(GitHubAuthor(AdminConfig.commitAuthorName, AdminConfig.commitAuthorEmail)),
          committer = Some(GitHubAuthor(AdminConfig.commitAuthorName, AdminConfig.commitAuthorEmail))
        )
        
        // 👇 Step 3: Serializza CON METODO ESPLICITO (evita problemi di implicit)
        requestJson = GitHubCreateUpdateRequest.given.toJson(request)
        
        // 👇 Step 4: Invia la richiesta HTTP
        response <- Client.request(Request.post(url, Body.fromString(requestJson)).addHeaders(headers))
        _ <- ZIO.unless(response.status.isSuccess)(
          response.body.asString.flatMap(b => ZIO.fail(new RuntimeException(s"GitHub commit failed [${response.status}]: $b")))
        )
        resultBody <- response.body.asString
        commitResp <- ZIO.fromEither(resultBody.fromJson[GitHubCommitResponse])
      yield commitResp.commit