package portfolio.services

import portfolio.models.*
import zio.*

import scala.jdk.CollectionConverters.*
import java.nio.file.{FileSystem, FileSystems, Files, Path, Paths}
import java.util.Collections.emptyMap

// ── Algebra ───────────────────────────────────────────────────────────────────

trait PortfolioService:
  def getLayout: UIO[LayoutConfig]
  def getHomeConfig: UIO[HomeConfig]
  def getProjectsConfig: UIO[ProjectsConfig]
  def getBlogConfig: UIO[BlogConfig]
  def getNotFoundConfig: UIO[NotFoundConfig]
  def getProfile: UIO[Profile]
  def getProjects: UIO[List[Project]]
  def getProject(id: String): UIO[Option[Project]]
  def getBlogPosts: UIO[List[BlogPost]]
  def getBlogPost(slug: String): UIO[Option[BlogPost]]
  
// ── Admin: ricarica tutti i contenuti da disco ──────────────────────────
  def reload: Task[Unit]

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
          val md   = withoutFirstDelim.substring(endIndex + 4).strip()
          (yaml, md)
        else
          ("", s)
      case s => ("", s)

    val document = parser.parse(markdownContent)
    val html     = renderer.render(document)

    val fm = if yamlBlock.nonEmpty then
      val yaml   = Yaml()
      val loaded = yaml.load[Any](yamlBlock)
      val data: JMap[String, Any] =
        if loaded.isInstanceOf[java.util.Map[?, ?]] then
          loaded.asInstanceOf[JMap[String, Any]]
        else
          java.util.Collections.emptyMap[String, Any]()
      convertYamlMap(data)
    else Map.empty[String, List[String]]

    (fm, html)

  private def convertYamlMap(data: JMap[String, Any]): Map[String, List[String]] =
    data.asScala.toMap.map { (key, value) =>
      val list = Option(value) match
        case None    => List.empty[String]
        case Some(v) =>
          v match
            case s: String            => List(s)
            case l: java.util.List[?] => l.asScala.toList.map(_.toString)
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
      if url == null then throw new RuntimeException(s"$dirName/ directory not found in classpath")

      val uri   = url.toURI
      var jarFs: Option[FileSystem] = None

      try
        val dir: Path = uri.getScheme match
          case "file" => Paths.get(uri)
          case "jar"  =>
            val fs = FileSystems.newFileSystem(uri, emptyMap[String, Any]())
            jarFs = Some(fs)
            fs.getPath(s"/$dirName")
          case other  =>
            throw new RuntimeException(s"Unsupported URI scheme: $other")

        Files.list(dir).iterator().asScala
          .filter(_.toString.endsWith(".md"))
          .map { path =>
            val slug = path.getFileName.toString.stripSuffix(".md")
            val raw  = Files.readString(path)
            slug -> raw
          }
          .toList
      finally
        jarFs.foreach(_.close())
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
    ZIO.attemptBlocking {
      val stream = getClass.getResourceAsStream("/home/profile.md")
      if stream == null then
        throw new RuntimeException("home/profile.md not found in classpath")
      new String(stream.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8)
    }.flatMap { raw =>
      ZIO.fromOption(parseProfile(raw))
        .orElseFail(new RuntimeException(
          "Failed to parse home/profile.md: check required frontmatter fields (name, role, bio, location, email)"
        ))
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
        case _                           => None
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

// ── Home loader ───────────────────────────────────────────────────────────────

object HomeLoader:

  def load: Task[HomeConfig] =
    ZIO.attemptBlocking {
      val stream = getClass.getResourceAsStream("/home/home.md")
      if stream == null then
        throw new RuntimeException("home/home.md not found in classpath")
      new String(stream.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8)
    }.flatMap { raw =>
      ZIO.fromOption(parseHome(raw))
        .orElseFail(new RuntimeException(
          "Failed to parse home/home.md: check required frontmatter fields"
        ))
    }

  private def parseHome(raw: String): Option[HomeConfig] =
    val (fm, _) = MarkdownParser.parse(raw)
    for
      pageTitle    <- MarkdownParser.frontString(fm, "pageTitle")
      heroLabel    <- MarkdownParser.frontString(fm, "heroLabel")
      rolePrefix   <- MarkdownParser.frontString(fm, "rolePrefix")
      ctaPrimary   <- MarkdownParser.frontString(fm, "heroCtaPrimary")
      ctaSecondary <- MarkdownParser.frontString(fm, "heroCtaSecondary")
      statYearsVal <- MarkdownParser.frontString(fm, "statYearsValue")
      statYearsLbl <- MarkdownParser.frontString(fm, "statYearsLabel")
      secSkills    <- MarkdownParser.frontString(fm, "sectionSkills")
      secProjects  <- MarkdownParser.frontString(fm, "sectionProjects")
      secPosts     <- MarkdownParser.frontString(fm, "sectionPosts")
      secContact   <- MarkdownParser.frontString(fm, "sectionContact")
      seeAll       <- MarkdownParser.frontString(fm, "seeAllLabel")
      contactTitle <- MarkdownParser.frontString(fm, "contactTitle")
      contactSub   <- MarkdownParser.frontString(fm, "contactSubtitle")
    yield HomeConfig(
      pageTitle             = pageTitle,
      heroLabel             = heroLabel,
      rolePrefix            = rolePrefix,
      heroCtaPrimary        = ctaPrimary,
      heroCtaSecondary      = ctaSecondary,
      statYearsValue        = statYearsVal,
      statYearsLabel        = statYearsLbl,
      sectionSkills         = secSkills,
      sectionProjects       = secProjects,
      sectionPosts          = secPosts,
      sectionContact        = secContact,
      seeAllLabel           = seeAll,
      featuredProjectsCount = MarkdownParser.frontInt(fm, "featuredProjectsCount", 3),
      latestPostsCount      = MarkdownParser.frontInt(fm, "latestPostsCount", 3),
      contactTitle          = contactTitle,
      contactSubtitle       = contactSub,
      githubLinkLabel       = MarkdownParser.frontString(fm, "githubLinkLabel").getOrElse("github ↗"),
      liveLinkLabel         = MarkdownParser.frontString(fm, "liveLinkLabel").getOrElse("live ↗"),
      readSuffix            = MarkdownParser.frontString(fm, "readSuffix").getOrElse("min"),
    )

// ── Layout loader ─────────────────────────────────────────────────────────────

object LayoutLoader:

  def load: Task[LayoutConfig] =
    ZIO.attemptBlocking {
      val stream = getClass.getResourceAsStream("/layout/layout.md")
      if stream == null then
        throw new RuntimeException("layout/layout.md not found in classpath")
      new String(stream.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8)
    }.flatMap { raw =>
      ZIO.fromOption(parseLayout(raw))
        .orElseFail(new RuntimeException(
          "Failed to parse layout/layout.md: check required frontmatter fields (logoText, footerCopy, footerBuiltWith)"
        ))
    }

  private def parseLayout(raw: String): Option[LayoutConfig] =
    val (fm, _) = MarkdownParser.parse(raw)
    for
      logoText      <- MarkdownParser.frontString(fm, "logoText")
      footerCopy    <- MarkdownParser.frontString(fm, "footerCopy")
      footerBuiltWith <- MarkdownParser.frontString(fm, "footerBuiltWith")
    yield LayoutConfig(
      logoText        = logoText,
      navLinks        = parseNavLinks(fm),
      footerCopy      = footerCopy,
      footerBuiltWith = footerBuiltWith,
    )

  private def parseNavLinks(fm: Map[String, List[String]]): List[NavLink] =
    MarkdownParser.frontList(fm, "navLinks").flatMap { entry =>
      entry.split("\\|").map(_.trim).toList match
        case label :: path :: rest =>
          val isCta = rest.headOption.exists(_.toLowerCase == "cta")
          Some(NavLink(label, path, isCta))
        case _ => None
    }

// ── Projects loader ───────────────────────────────────────────────────────────

object ProjectsLoader:

  def load: Task[ProjectsConfig] =
    ZIO.attemptBlocking {
      val stream = getClass.getResourceAsStream("/projects/projects.md")
      if stream == null then throw new RuntimeException("projects/projects.md not found in classpath")
      new String(stream.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8)
    }.flatMap { raw =>
      ZIO.fromOption(parse(raw))
        .orElseFail(new RuntimeException("Failed to parse projects/projects.md"))
    }

  private def parse(raw: String): Option[ProjectsConfig] =
    val (fm, _) = MarkdownParser.parse(raw)
    for
      pageTitle       <- MarkdownParser.frontString(fm, "pageTitle")
      sectionTag      <- MarkdownParser.frontString(fm, "sectionTag")
      heading         <- MarkdownParser.frontString(fm, "heading")
      subtitle        <- MarkdownParser.frontString(fm, "subtitle")
      githubLinkLabel <- MarkdownParser.frontString(fm, "githubLinkLabel")
      liveLinkLabel   <- MarkdownParser.frontString(fm, "liveLinkLabel")
    yield ProjectsConfig(
      pageTitle,
      sectionTag,
      heading,
      subtitle,
      githubLinkLabel,
      liveLinkLabel,
      readSuffix = MarkdownParser.frontString(fm, "readSuffix").getOrElse("min")
    )

// ── Blog loader ───────────────────────────────────────────────────────────────

object BlogLoader:

  def load: Task[BlogConfig] =
    ZIO.attemptBlocking {
      val stream = getClass.getResourceAsStream("/blog/blog.md")
      if stream == null then throw new RuntimeException("blog/blog.md not found in classpath")
      new String(stream.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8)
    }.flatMap { raw =>
      ZIO.fromOption(parse(raw))
        .orElseFail(new RuntimeException("Failed to parse blog/blog.md"))
    }

  private def parse(raw: String): Option[BlogConfig] =
    val (fm, _) = MarkdownParser.parse(raw)
    for
      pageTitle       <- MarkdownParser.frontString(fm, "pageTitle")
      pageTitleSuffix <- MarkdownParser.frontString(fm, "pageTitleSuffix")
      sectionTag      <- MarkdownParser.frontString(fm, "sectionTag")
      heading         <- MarkdownParser.frontString(fm, "heading")
      subtitle        <- MarkdownParser.frontString(fm, "subtitle")
      readSuffix      <- MarkdownParser.frontString(fm, "readSuffix")
      readSuffixFull  <- MarkdownParser.frontString(fm, "readSuffixFull")
      backLabel       <- MarkdownParser.frontString(fm, "backLabel")
    yield BlogConfig(pageTitle, pageTitleSuffix, sectionTag, heading, subtitle, readSuffix, readSuffixFull, backLabel)

// ── NotFound loader ───────────────────────────────────────────────────────────

object NotFoundLoader:

  def load: Task[NotFoundConfig] =
    ZIO.attemptBlocking {
      val stream = getClass.getResourceAsStream("/notfound/notfound.md")
      if stream == null then throw new RuntimeException("notfound/notfound.md not found in classpath")
      new String(stream.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8)
    }.flatMap { raw =>
      ZIO.fromOption(parse(raw))
        .orElseFail(new RuntimeException("Failed to parse notfound/notfound.md"))
    }

  private def parse(raw: String): Option[NotFoundConfig] =
    val (fm, _) = MarkdownParser.parse(raw)
    for
      pageTitle     <- MarkdownParser.frontString(fm, "pageTitle")
      errorCode     <- MarkdownParser.frontString(fm, "errorCode")
      errorTitle    <- MarkdownParser.frontString(fm, "errorTitle")
      errorSubtitle <- MarkdownParser.frontString(fm, "errorSubtitle")
      goHomeLabel   <- MarkdownParser.frontString(fm, "goHomeLabel")
    yield NotFoundConfig(pageTitle, errorCode, errorTitle, errorSubtitle, goHomeLabel)

// ── Live implementation ───────────────────────────────────────────────────────

object PortfolioServiceLive:

  /** Crea il layer. Se CONTENT_DIR è impostato, usa quella cartella; altrimenti il classpath. */
  val layer: TaskLayer[PortfolioService] =
    ZLayer.fromZIO(
      for
        data <- loadAllData
        ref  <- Ref.make(data)
      yield ReloadableLive(ref)
    )

  /** Carica tutti i dati da disco/classpath. */
  private def loadAllData: Task[LoadedData] =
    for
      layout      <- LayoutLoader.load
      home        <- HomeLoader.load
      projects_c  <- ProjectsLoader.load
      blog_c      <- BlogLoader.load
      notfound_c  <- NotFoundLoader.load
      posts       <- PostLoader.loadAll
      projects    <- ProjectLoader.loadAll
      profile     <- ProfileLoader.load
    yield LoadedData(layout, home, projects_c, blog_c, notfound_c, profile, projects, posts)

  case class LoadedData(
    layout: LayoutConfig,
    home: HomeConfig,
    projectsConfig: ProjectsConfig,
    blogConfig: BlogConfig,
    notFoundConfig: NotFoundConfig,
    profile: Profile,
    projects: List[Project],
    posts: List[BlogPost],
  )

  /** Implementazione che supporta il reload dei dati. */
  private final class ReloadableLive(ref: Ref[LoadedData]) extends PortfolioService:

    def getLayout: UIO[LayoutConfig]            = ref.get.map(_.layout)
    def getHomeConfig: UIO[HomeConfig]          = ref.get.map(_.home)
    def getProjectsConfig: UIO[ProjectsConfig]  = ref.get.map(_.projectsConfig)
    def getBlogConfig: UIO[BlogConfig]          = ref.get.map(_.blogConfig)
    def getNotFoundConfig: UIO[NotFoundConfig]  = ref.get.map(_.notFoundConfig)
    def getProfile: UIO[Profile]                = ref.get.map(_.profile)
    def getProjects: UIO[List[Project]]         = ref.get.map(_.projects)
    def getProject(id: String): UIO[Option[Project]] = ref.get.map(_.projects.find(_.id == id))
    def getBlogPosts: UIO[List[BlogPost]]       = ref.get.map(_.posts)
    def getBlogPost(slug: String): UIO[Option[BlogPost]] = ref.get.map(_.posts.find(_.slug == slug))

    def reload: Task[Unit] =
      for
        newData <- loadAllData
        _       <- ref.set(newData)
        _       <- ZIO.logInfo("Contenuti ricaricati da disco")
      yield ()