package portfolio.services

import portfolio.models.*
import zio.*

import scala.jdk.CollectionConverters.*
import java.nio.file.{FileSystem, FileSystems, Files, Path, Paths}
import java.util.Collections.emptyMap

// ── Algebra ───────────────────────────────────────────────────────────────────

trait PortfolioService:
  def getProfile: UIO[Profile]
  def getProjects: UIO[List[Project]]
  def getProject(id: String): UIO[Option[Project]]
  def getBlogPosts: UIO[List[BlogPost]]
  def getBlogPost(slug: String): UIO[Option[BlogPost]]

// ── Markdown parser ───────────────────────────────────────────────────────────

object MarkdownParser:

  import org.commonmark.parser.Parser
  import org.commonmark.renderer.html.HtmlRenderer
  import org.commonmark.ext.gfm.tables.TablesExtension
  import org.commonmark.ext.gfm.strikethrough.StrikethroughExtension
  import org.yaml.snakeyaml.Yaml

  import java.util.{List as JList, Map as JMap}

  private val extensions = java.util.Arrays.asList(
    TablesExtension.create(),
    StrikethroughExtension.create(),
  )

  private val parser = Parser.builder()
    .extensions(extensions)
    .build()

  private val renderer = HtmlRenderer.builder()
    .extensions(extensions)
    .build()
    
  def parse(raw: String): (Map[String, List[String]], String) =
    val (yamlBlock, markdownContent) = raw.stripLeading() match
      case s if s.startsWith("---") =>
        val withoutFirstDelim = s.stripPrefix("---")
        val endIndex = withoutFirstDelim.indexOf("\n---")
        if endIndex >= 0 then
          val yaml = withoutFirstDelim.substring(0, endIndex).strip()
          val md = withoutFirstDelim.substring(endIndex + 4).strip()
          (yaml, md)
        else
          ("", s)
      case s => ("", s)

    val document = parser.parse(markdownContent)
    val html = renderer.render(document)

    val fm = if yamlBlock.nonEmpty then
      val yaml = Yaml()
      val data = yaml.load(yamlBlock) match
        case map: JMap[?, ?] => map.asInstanceOf[JMap[String, Any]]
        case _ => java.util.Collections.emptyMap[String, Any]()
      convertYamlMap(data)
    else Map.empty[String, List[String]]

    (fm, html)

  private def convertYamlMap(data: JMap[String, Any]): Map[String, List[String]] =
    println(s"[DEBUG V3] convertYamlMap chiamata con chiavi: ${data.keySet().asScala.mkString(", ")}")  // 👈 Log forzato  
    data.asScala.toMap.map { (key, value) =>
      val list = Option(value) match
        case None => List.empty[String]
        case Some(v) =>
          v match
            case s: String            => List(s)
            case l: java.util.List[_] => l.asScala.toList.map(_.toString)
            case other                => List(other.toString)
      key -> list
    }

  def frontString(fm: Map[String, List[String]], key: String): Option[String] =
    fm.get(key).flatMap(_.headOption)

  def frontList(fm: Map[String, List[String]], key: String): List[String] =
    fm.get(key).getOrElse(Nil)

  def frontInt(fm: Map[String, List[String]], key: String, default: Int): Int =
    frontString(fm, key).flatMap(_.toIntOption).getOrElse(default)

// ── Generic resource loader ───────────────────────────────────────────────────

object ResourceLoader:

  def loadDirectory(dirName: String): Task[List[(String, String)]] =
    ZIO.attemptBlocking {
      val url = getClass.getResource(s"/$dirName")
      if (url == null) throw new RuntimeException(s"$dirName/ directory not found in classpath")

      val uri = url.toURI
      var jarFs: Option[FileSystem] = None

      try {
        val dir: Path = uri.getScheme match {
          case "file" => Paths.get(uri)
          case "jar" =>
            val env = emptyMap[String, Any]()
            val fs  = FileSystems.newFileSystem(uri, env)
            jarFs = Some(fs)
            fs.getPath(s"/$dirName")
          case other =>
            throw new RuntimeException(s"Unsupported URI scheme: $other")
        }

        Files.list(dir).iterator().asScala
          .filter(_.toString.endsWith(".md"))
          .map { path =>
            val slug = path.getFileName.toString.stripSuffix(".md")
            val raw  = Files.readString(path)
            slug -> raw
          }
          .toList
      } finally {
        jarFs.foreach(_.close())
      }
    }

// ── Post loader ───────────────────────────────────────────────────────────────

object PostLoader:

  def loadAll: Task[List[BlogPost]] =
    ResourceLoader.loadDirectory("blog").map { files =>
      files.flatMap { (slug, raw) => parsePost(slug, raw) }
        .sortBy(_.publishedAt).reverse
    }

  private def parsePost(slug: String, raw: String): Option[BlogPost] =
    val (fm, html) = MarkdownParser.parse(raw)
    for
      title   <- MarkdownParser.frontString(fm, "title")
      excerpt <- MarkdownParser.frontString(fm, "excerpt")
      date    <- MarkdownParser.frontString(fm, "publishedAt")
    yield BlogPost(
      id             = slug,
      slug           = slug,
      title          = title,
      excerpt        = excerpt,
      content        = html,
      tags           = MarkdownParser.frontList(fm, "tags"),
      publishedAt    = date,
      readingMinutes = MarkdownParser.frontInt(fm, "readingMinutes", 5),
    )

// ── Profile loader ────────────────────────────────────────────────────────────

object ProfileLoader:

  def load: Task[Profile] =
    ResourceLoader.loadDirectory("home").flatMap { files =>
      files.headOption match
        case Some((_, raw)) => ZIO.fromOption(parseProfile(raw))
          .orElseFail(new RuntimeException("Failed to parse profile"))
        case None => ZIO.fail(new RuntimeException("profile.md not found"))
    }

  private def parseProfile(raw: String): Option[Profile] =
    val (fm, _) = MarkdownParser.parse(raw)
    for
      name     <- MarkdownParser.frontString(fm, "name")
      role     <- MarkdownParser.frontString(fm, "role")
      bio      <- MarkdownParser.frontString(fm, "bio")
      location <- MarkdownParser.frontString(fm, "location")
      email    <- MarkdownParser.frontString(fm, "email")
    yield Profile(
      name     = name,
      role     = role,
      bio      = bio,
      location = location,
      email    = email,
      skills   = MarkdownParser.frontList(fm, "skills"),
      socials  = parseSocials(fm),
    )

  private def parseSocials(fm: Map[String, List[String]]): List[SocialLink] =
    MarkdownParser.frontList(fm, "socials").flatMap { entry =>
      entry.split("\\|").toList match
        case label :: url :: icon :: Nil => Some(SocialLink(label.trim, url.trim, icon.trim))
        case _ => None
    }

// ── Project loader ────────────────────────────────────────────────────────────

object ProjectLoader:

  def loadAll: Task[List[Project]] =
    ResourceLoader.loadDirectory("projects").map { files =>
      files.flatMap { (_, raw) => parseProject(raw) }
        .sortBy(_.year).reverse
    }

  private def parseProject(raw: String): Option[Project] =
    val (fm, html) = MarkdownParser.parse(raw)
    for
      id          <- MarkdownParser.frontString(fm, "id")
      title       <- MarkdownParser.frontString(fm, "title")
      description <- MarkdownParser.frontString(fm, "description")
    yield Project(
      id              = id,
      title           = title,
      description     = description,
      longDescription = html.strip(),
      tags            = MarkdownParser.frontList(fm, "tags"),
      githubUrl       = MarkdownParser.frontString(fm, "githubUrl"),
      liveUrl         = MarkdownParser.frontString(fm, "liveUrl"),
      status          = MarkdownParser.frontString(fm, "status")
                          .flatMap(ProjectStatus.fromString)
                          .getOrElse(ProjectStatus.Active),
      year            = MarkdownParser.frontInt(fm, "year", 2024),
    )

// ── Live implementation ───────────────────────────────────────────────────────

object PortfolioServiceLive:

  val layer: TaskLayer[PortfolioService] =
    ZLayer.fromZIO(
      for
        posts    <- PostLoader.loadAll
        projects <- ProjectLoader.loadAll
        profile  <- ProfileLoader.load
      yield Live(profile, projects, posts)
    )

  private final class Live(
    profile: Profile,
    projects: List[Project],
    posts: List[BlogPost]
  ) extends PortfolioService:
    def getProfile: UIO[Profile]                         = ZIO.succeed(profile)
    def getProjects: UIO[List[Project]]                  = ZIO.succeed(projects)
    def getProject(id: String): UIO[Option[Project]]     = ZIO.succeed(projects.find(_.id == id))
    def getBlogPosts: UIO[List[BlogPost]]                = ZIO.succeed(posts)
    def getBlogPost(slug: String): UIO[Option[BlogPost]] = ZIO.succeed(posts.find(_.slug == slug))