package portfolio.services

import portfolio.models.*
import com.vladsch.flexmark.html.HtmlRenderer
import com.vladsch.flexmark.parser.Parser
import com.vladsch.flexmark.ext.tables.TablesExtension
import com.vladsch.flexmark.ext.gfm.strikethrough.StrikethroughExtension
import com.vladsch.flexmark.ext.yaml.front.matter.{AbstractYamlFrontMatterVisitor, YamlFrontMatterExtension}
import com.vladsch.flexmark.util.data.MutableDataSet
import zio.*

import java.util
import scala.jdk.CollectionConverters.*
//
import java.nio.file.{FileSystem, FileSystems, Files, Path, Paths}
import java.io.IOException
import java.util.Collections

// ── Algebra ───────────────────────────────────────────────────────────────────

trait PortfolioService:
  def getProfile: UIO[Profile]
  def getProjects: UIO[List[Project]]
  def getProject(id: String): UIO[Option[Project]]
  def getBlogPosts: UIO[List[BlogPost]]
  def getBlogPost(slug: String): UIO[Option[BlogPost]]

// ── Markdown parser ───────────────────────────────────────────────────────────

object MarkdownParser:

  private val options =
    val opts = MutableDataSet()
    opts.set(
      Parser.EXTENSIONS,
      util.Arrays.asList(
        TablesExtension.create(),
        StrikethroughExtension.create(),
        YamlFrontMatterExtension.create(),
      ),
    )
    opts

  private val parser   = Parser.builder(options).build()
  private val renderer = HtmlRenderer.builder(options).build()

  /** Parse a raw .md string into (frontmatter map, rendered HTML body) */
  def parse(raw: String): (Map[String, List[String]], String) =
    val document = parser.parse(raw)

    // Extract YAML front matter
    val visitor = new AbstractYamlFrontMatterVisitor()
    visitor.visit(document)
    val fm = visitor.getData.asScala.toMap.map { (k, v) => k -> v.asScala.toList }

    // Render body (front matter block is stripped from output by the extension)
    val html = renderer.render(document)
    (fm, html)

  def frontString(fm: Map[String, List[String]], key: String): Option[String] =
    fm.get(key).flatMap(_.headOption).map(_.stripPrefix("\"").stripSuffix("\""))

  def frontList(fm: Map[String, List[String]], key: String): List[String] =
    fm.get(key).getOrElse(Nil).map(_.stripPrefix("\"").stripSuffix("\""))

  def frontInt(fm: Map[String, List[String]], key: String, default: Int): Int =
    frontString(fm, key).flatMap(_.toIntOption).getOrElse(default)

// ── Post loader ───────────────────────────────────────────────────────────────

object PostLoader:

  // Load all posts from classpath resources/posts/*.md
  def loadAll: Task[List[BlogPost]] = ZIO.attemptBlocking {

    val url = getClass.getResource("/posts")
    if url == null then throw new RuntimeException("posts/ directory not found in classpath")

    val uri = url.toURI

    // Obtain a Path to the "posts" directory, whether in a JAR or on the filesystem
    val dir: Path = uri.getScheme match {
      case "file" => Paths.get(uri)
      case "jar"  =>
        val env = Collections.emptyMap[String, Any]()
        val fs  = FileSystems.newFileSystem(uri, env)
        try {
          fs.getPath("/posts")
        } finally {
          fs.close()
        }
      case other =>
        throw new RuntimeException(s"Unsupported URI scheme: $other")
    }

    // List .md files
    val files = Files.list(dir).iterator().asScala
      .filter(_.toString.endsWith(".md"))
      .toList

    // Parse each file and collect the results
    files.flatMap { path =>
      val slug = path.getFileName.toString.stripSuffix(".md")
      val raw  = Files.readString(path)
      parsePost(slug, raw)
    }.sortBy(_.publishedAt).reverse
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

// ── Live implementation ───────────────────────────────────────────────────────

object PortfolioServiceLive:

  val layer: TaskLayer[PortfolioService] =
    ZLayer.fromZIO(
      PostLoader.loadAll.map(posts => Live(posts))
    )

  private val staticProfile = Profile(
    name     = "Alex Ferretti",
    role     = "Software Engineer",
    bio      = "I build robust, scalable systems with a passion for functional programming, type safety, and elegant architecture. Specialised in Scala, ZIO, and distributed systems.",
    location = "Milano, IT",
    email    = "alex@ferretti.dev",
    skills   = List(
      "Scala", "ZIO", "Akka", "Kafka", "PostgreSQL",
      "Kubernetes", "Rust", "TypeScript", "Redis", "gRPC",
    ),
    socials  = List(
      SocialLink("GitHub",   "https://github.com",   "github"),
      SocialLink("LinkedIn", "https://linkedin.com", "linkedin"),
      SocialLink("Twitter",  "https://twitter.com",  "twitter"),
    ),
  )

  private val staticProjects = List(
    Project(
      id              = "zio-saga",
      title           = "ZIO Saga",
      description     = "Distributed saga pattern implementation for ZIO",
      longDescription = "A library implementing the Saga pattern for managing distributed transactions in ZIO applications.",
      tags            = List("Scala", "ZIO", "Distributed Systems", "Open Source"),
      githubUrl       = Some("https://github.com/example/zio-saga"),
      liveUrl         = None,
      status          = ProjectStatus.Active,
      year            = 2024,
    ),
    Project(
      id              = "stream-processor",
      title           = "Real-time Stream Processor",
      description     = "High-throughput event processing pipeline with Kafka + ZIO Streams",
      longDescription = "An event-driven processing pipeline capable of ingesting 500k events/sec.",
      tags            = List("Scala", "Kafka", "ZIO Streams", "PostgreSQL"),
      githubUrl       = Some("https://github.com/example/stream-processor"),
      liveUrl         = None,
      status          = ProjectStatus.Active,
      year            = 2024,
    ),
    Project(
      id              = "type-safe-api",
      title           = "Type-Safe REST API Framework",
      description     = "Compile-time validated HTTP routes with full schema derivation",
      longDescription = "A micro-framework built on ZIO HTTP that derives OpenAPI specs at compile time via Scala 3 macros.",
      tags            = List("Scala 3", "ZIO HTTP", "Macros", "OpenAPI"),
      githubUrl       = Some("https://github.com/example/type-safe-api"),
      liveUrl         = Some("https://example.dev/type-safe-api"),
      status          = ProjectStatus.Active,
      year            = 2023,
    ),
    Project(
      id              = "k8s-operator",
      title           = "Kubernetes Operator in Scala",
      description     = "Custom Kubernetes operator for managing stateful Scala microservices",
      longDescription = "A production-grade K8s operator that automates deployment, scaling and health management.",
      tags            = List("Scala", "Kubernetes", "DevOps", "Operators"),
      githubUrl       = Some("https://github.com/example/k8s-operator"),
      liveUrl         = None,
      status          = ProjectStatus.Archived,
      year            = 2023,
    ),
  )

  private final class Live(posts: List[BlogPost]) extends PortfolioService:
    def getProfile: UIO[Profile]                         = ZIO.succeed(staticProfile)
    def getProjects: UIO[List[Project]]                  = ZIO.succeed(staticProjects)
    def getProject(id: String): UIO[Option[Project]]     = ZIO.succeed(staticProjects.find(_.id == id))
    def getBlogPosts: UIO[List[BlogPost]]                = ZIO.succeed(posts)
    def getBlogPost(slug: String): UIO[Option[BlogPost]] = ZIO.succeed(posts.find(_.slug == slug))
