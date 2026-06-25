package portfolio.admin

import portfolio.services.ContentValidator
import zio.*
import zio.http.*
import zio.json.*

trait ContentService:
  def listFiles: Task[List[ContentFile]]
  def readFile(relativePath: String): Task[String]
  def writeFile(relativePath: String, content: String): Task[GitHubCommitResult]
  def isWritable: UIO[Boolean]

case class ContentFile(relativePath: String, displayName: String, section: String)

object ContentFile:
  given JsonEncoder[ContentFile] = DeriveJsonEncoder.gen[ContentFile]

object ContentServiceLive:
  val layer: ZLayer[GitHubService, Nothing, ContentService] = ZLayer.fromFunction(Live.apply)

  private val knownFiles: List[ContentFile] = List(
    ContentFile("home/home.md", "Home Page", "home"),
    ContentFile("home/profile.md", "Profilo", "home"),
    ContentFile("layout/layout.md", "Layout", "layout"),
    ContentFile("projects/projects.md", "Progetti", "projects"),
    ContentFile("blog/blog.md", "Blog", "blog"),
    ContentFile("notfound/notfound.md", "404", "notfound")
  )
  private val allowedPaths: Set[String] = knownFiles.map(_.relativePath).toSet

  private final class Live(gh: GitHubService) extends ContentService:

    /** Autorizza solo i path nella whitelist: blocca path traversal e scrittura arbitraria sul
      * repo.
      */
    private def validated(p: String): Task[String] =
      if allowedPaths.contains(p) then ZIO.succeed(p)
      else ZIO.fail(new RuntimeException(s"Path non consentito: $p"))

    def isWritable: UIO[Boolean]           = ZIO.succeed(AdminConfig.githubToken.nonEmpty)
    def listFiles: Task[List[ContentFile]] = ZIO.succeed(knownFiles)
    def readFile(p: String): Task[String]  = validated(p).flatMap(gh.getFileContent)

    def writeFile(p: String, c: String): Task[GitHubCommitResult] =
      for
        vp <- validated(p)
        // Valida il contenuto prima del commit: un file malformato impedirebbe il boot al redeploy.
        _      <- ZIO.fromEither(ContentValidator.validate(vp, c)).mapError(new RuntimeException(_))
        commit <- gh.updateFile(vp, c, s"Update $vp")
      yield commit
