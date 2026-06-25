package portfolio.admin

import zio.*
import zio.test.*

object ContentServiceSpec extends ZIOSpecDefault:

  // GitHubService finto: non tocca la rete, restituisce valori prevedibili.
  private val fakeGitHub: GitHubService = new GitHubService:
    def getFileContent(path: String): Task[String] =
      ZIO.succeed(s"content:$path")
    def updateFile(path: String, content: String, commitMessage: String): Task[GitHubCommitResult] =
      ZIO.succeed(GitHubCommitResult("sha123", "https://example/commit"))

  private val layer = ZLayer.succeed(fakeGitHub) >>> ContentServiceLive.layer

  def spec = suite("ContentService (whitelist path)")(
    test("legge un path in whitelist") {
      for content <- ZIO.serviceWithZIO[ContentService](_.readFile("blog/blog.md"))
      yield assertTrue(content == "content:blog/blog.md")
    },
    test("rifiuta la lettura di un path fuori whitelist (path traversal)") {
      for exit <- ZIO.serviceWithZIO[ContentService](_.readFile("../../../build.sbt")).exit
      yield assertTrue(exit.isFailure)
    },
    test("scrive un path in whitelist") {
      for r <- ZIO.serviceWithZIO[ContentService](_.writeFile("home/home.md", "x"))
      yield assertTrue(r.sha == "sha123")
    },
    test("rifiuta la scrittura di un path arbitrario") {
      for exit <- ZIO.serviceWithZIO[ContentService](_.writeFile(".github/workflows/x.yml", "evil")).exit
      yield assertTrue(exit.isFailure)
    },
  ).provideShared(layer)
