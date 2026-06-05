package portfolio.admin

import zio.*
import zio.http.*
import zio.json.*

trait ContentService:
  def listFiles: Task[List[ContentFile]]
  def readFile(relativePath: String): ZIO[Client, Throwable, String]
  def writeFile(relativePath: String, content: String): ZIO[Client, Throwable, GitHubCommitResult]
  def isWritable: UIO[Boolean]

case class ContentFile(relativePath: String, displayName: String, section: String)

object ContentFile:
  given JsonEncoder[ContentFile] = DeriveJsonEncoder.gen[ContentFile]

object ContentServiceLive:
  val layer: ZLayer[GitHubService, Nothing, ContentService] = ZLayer.fromFunction(Live.apply)

  private final class Live(gh: GitHubService) extends ContentService:
    private val knownFiles: List[ContentFile] = List(
      ContentFile("home/home.md", "Home Page", "home"),
      ContentFile("home/profile.md", "Profilo", "home"),
      ContentFile("layout/layout.md", "Layout", "layout"),
      ContentFile("projects/projects.md", "Progetti", "projects"),
      ContentFile("blog/blog.md", "Blog", "blog"),
      ContentFile("notfound/notfound.md", "404", "notfound")
    )

    def isWritable: UIO[Boolean] = ZIO.succeed(AdminConfig.githubToken.nonEmpty)
    def listFiles: Task[List[ContentFile]] = ZIO.succeed(knownFiles)
    def readFile(p: String): ZIO[Client, Throwable, String] = gh.getFileContent(p)
    def writeFile(p: String, c: String): ZIO[Client, Throwable, GitHubCommitResult] = gh.updateFile(p, c, s"Update $p")