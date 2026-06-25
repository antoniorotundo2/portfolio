package portfolio.routes

import portfolio.models.*
import portfolio.services.PortfolioService
import portfolio.views.*
import zio.*
import zio.http.*

object AppRoutes:

  // Nomi file statici consentiti: blocca path traversal (es. "../") nel segmento.
  private val safeFileName = "^[A-Za-z0-9._-]+$".r

  /** Asset statico in memoria con ETag precalcolato. */
  private final case class CachedAsset(bytes: Array[Byte], etag: String)

  // Cache in-memory degli asset: evita di rileggere il classpath ad ogni richiesta.
  private val staticCache = scala.collection.concurrent.TrieMap.empty[String, Option[CachedAsset]]

  private def loadAsset(resourcePath: String): Option[CachedAsset] =
    staticCache.getOrElseUpdate(
      resourcePath, {
        val stream = getClass.getResourceAsStream(resourcePath)
        if stream == null then None
        else
          val bytes = stream.readAllBytes()
          val crc   = new java.util.zip.CRC32()
          crc.update(bytes)
          Some(CachedAsset(bytes, "\"" + java.lang.Long.toHexString(crc.getValue) + "\""))
      }
    )

  private def serveStatic(
      dir: String,
      file: String,
      contentType: Header.ContentType,
      req: Request
  ): Response =
    if !safeFileName.matches(file) then Response.notFound
    else
      loadAsset(s"/static/$dir/$file") match
        case None => Response.notFound
        case Some(asset) =>
          val cacheHeaders = Headers(
            contentType,
            Header.Custom("ETag", asset.etag),
            Header.Custom("Cache-Control", "public, max-age=3600, must-revalidate")
          )
          // Revalidazione: se l'ETag del client combacia, rispondiamo 304 senza corpo.
          val ifNoneMatch = req.headers.get("If-None-Match")
          if ifNoneMatch.contains(asset.etag) then
            Response(status = Status.NotModified, headers = cacheHeaders)
          else
            Response(status = Status.Ok, headers = cacheHeaders, body = Body.fromArray(asset.bytes))

  private def okHtml(content: String): Response =
    Response(
      status = Status.Ok,
      headers = Headers(Header.ContentType(MediaType.text.html)),
      body = Body.fromString(content)
    )

  private def notFoundHtml(
      layout: portfolio.models.LayoutConfig,
      nf: portfolio.models.NotFoundConfig
  ): Response =
    Response(
      status = Status.NotFound,
      headers = Headers(Header.ContentType(MediaType.text.html)),
      body = Body.fromString(NotFoundView.render(layout, nf))
    )

  val routes: Routes[PortfolioService, Nothing] =
    Routes(
      // ── Static files ────────────────────────────────────────────────────────
      // ── Health check (leggero, per Render) ───────────────────────────────────
      Method.GET / "healthz" ->
        handler { (_: Request) =>
          Response(status = Status.Ok, body = Body.fromString("ok"))
        },

      // ── Static files ────────────────────────────────────────────────────────
      Method.GET / "static" / "css" / string("file") ->
        handler { (file: String, req: Request) =>
          ZIO
            .attemptBlocking(serveStatic("css", file, Header.ContentType(MediaType.text.css), req))
            .orDie
        },
      Method.GET / "static" / "js" / string("file") ->
        handler { (file: String, req: Request) =>
          ZIO.attemptBlocking(
            serveStatic("js", file, Header.ContentType(MediaType.application.`javascript`), req)
          ).orDie
        },

      // ── Home ────────────────────────────────────────────────────────────────
      Method.GET / "" ->
        handler { (_: Request) =>
          for {
            svc      <- ZIO.service[PortfolioService]
            layout   <- svc.getLayout
            home     <- svc.getHomeConfig
            profile  <- svc.getProfile
            projects <- svc.getProjects
            posts    <- svc.getBlogPosts
          } yield okHtml(HomeView.render(layout, home, profile, projects, posts))
        },

      // ── Projects ────────────────────────────────────────────────────────────
      Method.GET / "projects" ->
        handler { (_: Request) =>
          for {
            svc      <- ZIO.service[PortfolioService]
            layout   <- svc.getLayout
            cfg      <- svc.getProjectsConfig
            projects <- svc.getProjects
          } yield okHtml(ProjectsView.render(layout, cfg, projects))
        },

      // ── Blog list ────────────────────────────────────────────────────────────
      Method.GET / "blog" ->
        handler { (_: Request) =>
          for {
            svc    <- ZIO.service[PortfolioService]
            layout <- svc.getLayout
            cfg    <- svc.getBlogConfig
            posts  <- svc.getBlogPosts
          } yield okHtml(BlogView.render(layout, cfg, posts))
        },

      // ── Blog post ────────────────────────────────────────────────────────────
      Method.GET / "blog" / string("slug") ->
        handler { (slug: String, _: Request) =>
          for {
            svc    <- ZIO.service[PortfolioService]
            layout <- svc.getLayout
            cfg    <- svc.getBlogConfig
            nf     <- svc.getNotFoundConfig
            post   <- svc.getBlogPost(slug)
          } yield post.fold(notFoundHtml(layout, nf))(p =>
            okHtml(BlogPostView.render(layout, cfg, p))
          )
        },

      // ── JSON API ─────────────────────────────────────────────────────────────
      Method.GET / "api" / "projects" ->
        handler { (_: Request) =>
          import zio.json.*
          for {
            svc      <- ZIO.service[PortfolioService]
            projects <- svc.getProjects
          } yield Response.json(projects.toJson)
        },
      Method.GET / "api" / "posts" ->
        handler { (_: Request) =>
          import zio.json.*
          for {
            svc   <- ZIO.service[PortfolioService]
            posts <- svc.getBlogPosts
          } yield Response.json(posts.toJson)
        },

      // ── 404 Catch-all per qualsiasi path non matchato (DEVE essere l'ultima) ─
      Method.ANY / trailing ->
        handler { (_: Path, _: Request) =>
          ZIO.serviceWithZIO[PortfolioService] { svc =>
            svc.getLayout.zip(svc.getNotFoundConfig).map { case (layout, nf) =>
              notFoundHtml(layout, nf)
            }
          }
        }
    )
