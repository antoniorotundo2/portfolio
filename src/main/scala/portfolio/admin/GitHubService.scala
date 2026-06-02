package portfolio.admin

import zio.*
import zio.http.*
import zio.json.*
import java.time.Instant
import java.util.Base64

case class GitHubFileResponse(sha: String, content: Option[String], encoding: Option[String], download_url: Option[String])
object GitHubFileResponse:
  given JsonDecoder[GitHubFileResponse] = DeriveJsonDecoder.gen[GitHubFileResponse]

case class GitHubAuthor(name: String, email: String, date: String = Instant.now().toString)
object GitHubAuthor:
  given JsonEncoder[GitHubAuthor] = DeriveJsonEncoder.gen[GitHubAuthor]

case class GitHubCreateUpdateRequest(message: String, content: String, sha: Option[String], branch: String, author: Option[GitHubAuthor] = None, committer: Option[GitHubAuthor] = None)
object GitHubCreateUpdateRequest:
  given JsonEncoder[GitHubCreateUpdateRequest] = DeriveJsonEncoder.gen[GitHubCreateUpdateRequest]

case class GitHubCommitResult(sha: String, html_url: String)
object GitHubCommitResult:
  given JsonDecoder[GitHubCommitResult] = DeriveJsonDecoder.gen[GitHubCommitResult]

case class GitHubCommitResponse(commit: GitHubCommitResult, html_url: String)
object GitHubCommitResponse:
  given JsonDecoder[GitHubCommitResponse] = DeriveJsonDecoder.gen[GitHubCommitResponse]

trait GitHubService:
  def getFileContent(path: String): ZIO[Client, Throwable, String]
  def updateFile(path: String, content: String, commitMessage: String): ZIO[Client, Throwable, GitHubCommitResult]

object GitHubServiceLive:
  val layer: ZLayer[Any, Nothing, GitHubService] = ZLayer.succeed(Live())

  private final class Live extends GitHubService:
    private val baseUrl = "https://api.github.com"
    private val apiHeaders = Headers(
      Header.ContentType(MediaType.application.json),
      Header.Custom("Authorization", s"token ${AdminConfig.githubToken}"),
      Header.Custom("Accept", "application/vnd.github.v3+json"),
      Header.Custom("User-Agent", "Portfolio-Admin/1.0")
    )

    private def apiPath(path: String): String =
      s"/repos/${AdminConfig.githubOwner}/${AdminConfig.githubRepo}$path"

    private def encodeBase64(s: String): String =
      Base64.getEncoder.encodeToString(s.getBytes("UTF-8"))

    private def decodeBase64(s: String): String =
      new String(Base64.getDecoder.decode(s), "UTF-8")

    private def safeRequest(req: Request): ZIO[Client, Throwable, Response] =
      ZIO.scoped(ZIO.serviceWithZIO[Client](_.request(req)))

    def getFileContent(path: String): ZIO[Client, Throwable, String] =
      val fullPath = s"${AdminConfig.contentBasePath}/$path"
      val url = s"$baseUrl${apiPath(s"/contents/$fullPath")}?ref=${AdminConfig.githubBranch}"
      for
        response <- safeRequest(Request.get(url).addHeaders(apiHeaders))
        _ <- ZIO.unless(response.status.isSuccess)(ZIO.fail(new RuntimeException(s"GitHub API error: ${response.status}")))
        body <- response.body.asString
        fileResp <- ZIO.fromEither(body.fromJson[GitHubFileResponse](using GitHubFileResponse.given))
        content <- fileResp.content match
          case Some(b64) => ZIO.succeed(decodeBase64(b64.replace("\n", "")))
          case None => fileResp.download_url match
            case Some(dlUrl) => safeRequest(Request.get(dlUrl)).flatMap(_.body.asString)
            case None => ZIO.fail(new RuntimeException("No content available"))
      yield content

    def updateFile(path: String, content: String, commitMessage: String): ZIO[Client, Throwable, GitHubCommitResult] =
      val fullPath = s"${AdminConfig.contentBasePath}/$path"
      val url = s"$baseUrl${apiPath(s"/contents/$fullPath")}"

      val getSha = safeRequest(Request.get(s"$url?ref=${AdminConfig.githubBranch}").addHeaders(apiHeaders)).flatMap { resp =>
        if resp.status == Status.NotFound then ZIO.succeed(None)
        else if resp.status.isSuccess then
          resp.body.asString.flatMap { body =>
            body.fromJson[GitHubFileResponse](using GitHubFileResponse.given) match
              case Right(f) => ZIO.succeed(Some(f.sha))
              case Left(errMsg) => ZIO.fail(new RuntimeException(s"Decode error: $errMsg"))
          }
        else ZIO.fail(new RuntimeException(s"GitHub API error: ${resp.status}"))
      }.catchAll(_ => ZIO.succeed(None))

      for
        existingSha <- getSha
        request: GitHubCreateUpdateRequest = GitHubCreateUpdateRequest(
          message = s"${AdminConfig.commitMessagePrefix} $commitMessage",
          content = encodeBase64(content),
          sha = existingSha,
          branch = AdminConfig.githubBranch,
          author = Some(GitHubAuthor(AdminConfig.commitAuthorName, AdminConfig.commitAuthorEmail)),
          committer = Some(GitHubAuthor(AdminConfig.commitAuthorName, AdminConfig.commitAuthorEmail))
        )
        requestJson = GitHubCreateUpdateRequest.given.toJson(request)
        response <- safeRequest(Request.post(url, Body.fromString(requestJson)).addHeaders(apiHeaders))
        _ <- ZIO.unless(response.status.isSuccess)(
          response.body.asString.flatMap(b => ZIO.fail(new RuntimeException(s"Commit failed [${response.status}]: $b")))
        )
        resultBody <- response.body.asString
        commitResp <- ZIO.fromEither(resultBody.fromJson[GitHubCommitResponse](using GitHubCommitResponse.given))
      yield commitResp.commit