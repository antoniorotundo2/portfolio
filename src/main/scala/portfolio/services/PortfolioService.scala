package portfolio.services

import portfolio.models.*
import zio.*

// ── Algebra ───────────────────────────────────────────────────────────────────

trait PortfolioService:
  def getProfile: UIO[Profile]
  def getProjects: UIO[List[Project]]
  def getProject(id: String): UIO[Option[Project]]
  def getBlogPosts: UIO[List[BlogPost]]
  def getBlogPost(slug: String): UIO[Option[BlogPost]]

// ── Live implementation (in-memory) ──────────────────────────────────────────

object PortfolioServiceLive:

  val layer: ULayer[PortfolioService] = ZLayer.succeed(new PortfolioService:

    private val profile = Profile(
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

    private val projects = List(
      Project(
        id              = "zio-saga",
        title           = "ZIO Saga",
        description     = "Distributed saga pattern implementation for ZIO",
        longDescription = "A library implementing the Saga pattern for managing distributed transactions in ZIO applications. Provides compensating transactions, parallel execution, and full observability.",
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
        longDescription = "An event-driven processing pipeline capable of ingesting 500k events/sec. Built on ZIO Streams and Kafka, with exactly-once semantics and backpressure management.",
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
        longDescription = "A micro-framework built on ZIO HTTP that derives OpenAPI specs at compile time via Scala 3 macros. Eliminates runtime errors in route handlers.",
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
        longDescription = "A production-grade K8s operator that automates deployment, scaling and health management of Scala-based microservices. Written in Scala with fabric8 client.",
        tags            = List("Scala", "Kubernetes", "DevOps", "Operators"),
        githubUrl       = Some("https://github.com/example/k8s-operator"),
        liveUrl         = None,
        status          = ProjectStatus.Archived,
        year            = 2023,
      ),
    )

    private val blogPosts = List(
      BlogPost(
        id             = "1",
        slug           = "zio-2-fibers-explained",
        title          = "ZIO 2 Fibers: Concurrency Without the Pain",
        excerpt        = "A deep dive into ZIO fibers, structured concurrency, and why they make concurrent code actually maintainable.",
        content        = """
          <h2>What are Fibers?</h2>
          <p>Fibers are ZIO's lightweight concurrency primitive. Unlike threads, fibers are virtual — you can spawn millions of them without exhausting OS resources.</p>
          <h2>Structured Concurrency</h2>
          <p>ZIO enforces structured concurrency: child fibers cannot outlive their parent scope. This eliminates entire classes of resource leak bugs.</p>
          <pre><code>for {
  fiber1 &lt;- ZIO.sleep(1.second).fork
  fiber2 &lt;- ZIO.sleep(2.seconds).fork
  _      &lt;- fiber1.join
  _      &lt;- fiber2.join
} yield ()</code></pre>
          <p>The ZIO runtime handles scheduling, interruption, and cleanup automatically.</p>
        """.strip,
        tags           = List("Scala", "ZIO", "Concurrency", "Fibers"),
        publishedAt    = "2024-11-15",
        readingMinutes = 8,
      ),
      BlogPost(
        id             = "2",
        slug           = "scala3-macros-intro",
        title          = "Scala 3 Macros: Generate Code at Compile Time",
        excerpt        = "How to use Scala 3's new macro system to eliminate boilerplate and catch errors before your program ever runs.",
        content        = """
          <h2>Why Macros?</h2>
          <p>Macros let you run Scala code at compile time — generating boilerplate, validating constraints, and deriving type class instances automatically.</p>
          <h2>Inline and Quotes</h2>
          <p>Scala 3 introduces a hygienic macro system based on <code>inline</code> defs and quoted expressions (<code>'{ ... }</code>). It's type-safe all the way down.</p>
          <pre><code>inline def assertPositive(inline n: Int): Int =
  inline if n > 0 then n
  else error("n must be positive")</code></pre>
          <p>The compiler rejects invalid calls with a precise error message — zero runtime cost.</p>
        """.strip,
        tags           = List("Scala 3", "Macros", "Metaprogramming"),
        publishedAt    = "2024-10-02",
        readingMinutes = 12,
      ),
      BlogPost(
        id             = "3",
        slug           = "kafka-zio-streams",
        title          = "Building a Kafka Consumer with ZIO Streams",
        excerpt        = "Step-by-step guide to building a resilient, backpressure-aware Kafka consumer using ZIO Streams and zio-kafka.",
        content        = """
          <h2>Setting Up zio-kafka</h2>
          <p>zio-kafka provides a purely functional Kafka client built on ZIO Streams. It handles consumer groups, offset commits and error recovery for you.</p>
          <h2>The Consumer Stream</h2>
          <pre><code>val stream = Consumer
  .plainStream(Subscription.topics("events"), Serde.string, Serde.string)
  .mapZIO(record => processRecord(record.value))
  .tap(_.offset.commit)</code></pre>
          <p>Backpressure is handled automatically — the stream only pulls as fast as you can process.</p>
        """.strip,
        tags           = List("Scala", "Kafka", "ZIO Streams", "Event-Driven"),
        publishedAt    = "2024-09-18",
        readingMinutes = 10,
      ),
    )

    def getProfile: UIO[Profile]                       = ZIO.succeed(profile)
    def getProjects: UIO[List[Project]]                = ZIO.succeed(projects)
    def getProject(id: String): UIO[Option[Project]]   = ZIO.succeed(projects.find(_.id == id))
    def getBlogPosts: UIO[List[BlogPost]]              = ZIO.succeed(blogPosts)
    def getBlogPost(slug: String): UIO[Option[BlogPost]] =
      ZIO.succeed(blogPosts.find(_.slug == slug))
  )
