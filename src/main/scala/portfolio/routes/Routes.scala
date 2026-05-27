package portfolio.routes

import portfolio.models.*
import portfolio.services.PortfolioService
import portfolio.views.*
import zio.*
import zio.http.*

object AppRoutes:

  private def htmlResponse(content: String): Response =
    Response(
      status  = Status.Ok,
      headers = Headers(Header.ContentType(MediaType.text.html)),
      body    = Body.fromString(content),
    )

  private def notFoundResponse: Response =
    Response(
      status  = Status.NotFound,
      headers = Headers(Header.ContentType(MediaType.text.html)),
      body    = Body.fromString(NotFoundView.render),
    )

  val routes: zio.http.Routes[PortfolioService, Nothing] =
    zio.http.Routes(

      // ── Static files ────────────────────────────────────────────────────────
      Method.GET / "static" / "css" / string("file") ->
        handler { (file: String, _: Request) =>
          ZIO.attemptBlocking {
            val stream = getClass.getResourceAsStream(s"/static/css/$file")
            if stream == null then Response.notFound
            else
              Response(
                status  = Status.Ok,
                headers = Headers(Header.ContentType(MediaType.text.css)),
                body    = Body.fromArray(stream.readAllBytes()),
              )
          }.orDie
        },

      Method.GET / "static" / "js" / string("file") ->
        handler { (file: String, _: Request) =>
          ZIO.attemptBlocking {
            val stream = getClass.getResourceAsStream(s"/static/js/$file")
            if stream == null then Response.notFound
            else
              Response(
                status  = Status.Ok,
                headers = Headers(Header.ContentType(MediaType.application.`javascript`)),
                body    = Body.fromArray(stream.readAllBytes()),
              )
          }.orDie
        },

      // ── Home ────────────────────────────────────────────────────────────────
      Method.GET / "" ->
        handler { (_: Request) =>
          for {
            svc      <- ZIO.service[PortfolioService]
            profile  <- svc.getProfile
            projects <- svc.getProjects
            posts    <- svc.getBlogPosts
          } yield htmlResponse(HomeView.render(profile, projects, posts))
        },

      // ── Projects ────────────────────────────────────────────────────────────
      Method.GET / "projects" ->
        handler { (_: Request) =>
          for {
            svc      <- ZIO.service[PortfolioService]
            projects <- svc.getProjects
          } yield htmlResponse(ProjectsView.render(projects))
        },

      // ── Blog list ────────────────────────────────────────────────────────────
      Method.GET / "blog" ->
        handler { (_: Request) =>
          for {
            svc   <- ZIO.service[PortfolioService]
            posts <- svc.getBlogPosts
          } yield htmlResponse(BlogView.render(posts))
        },

      // ── Blog post ────────────────────────────────────────────────────────────
      Method.GET / "blog" / string("slug") ->
        handler { (slug: String, _: Request) =>
          for {
            svc  <- ZIO.service[PortfolioService]
            post <- svc.getBlogPost(slug)
          } yield post.fold(notFoundResponse)(p => htmlResponse(BlogPostView.render(p)))
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
    )
