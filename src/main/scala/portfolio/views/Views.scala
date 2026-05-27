package portfolio.views

import scalatags.Text.all.*
import scalatags.Text.tags2.{nav, main, section, article, time}
import scalatags.Text.TypedTag
import portfolio.models.*

// ── Design tokens (injected via CSS variables defined in stylesheet) ──────────

object Layout:

  // Shared <head> block
  private def headBlock(pageTitle: String) =
    head(
      meta(charset := "utf-8"),
      meta(name := "viewport", content := "width=device-width, initial-scale=1"),
      tag("title")(pageTitle),
      link(rel := "preconnect", href := "https://fonts.googleapis.com"),
      link(
        rel  := "stylesheet",
        href := "https://fonts.googleapis.com/css2?family=Space+Mono:ital,wght@0,400;0,700;1,400&family=Syne:wght@400;600;700;800&display=swap",
      ),
      link(rel := "stylesheet", href := "/static/css/main.css"),
    )

  // Navigation bar
  private def navbar(currentPath: String) =
    nav(cls := "navbar")(
      a(href := "/", cls := "nav-logo")(
        span(cls := "logo-bracket")("["),
        "AR",
        span(cls := "logo-bracket")("]"),
      ),
      div(cls := "nav-links")(
        a(href := "/",         cls := s"nav-link ${if currentPath == "/"         then "active" else ""}")("home"),
        a(href := "/projects", cls := s"nav-link ${if currentPath == "/projects" then "active" else ""}")("projects"),
        a(href := "/blog",     cls := s"nav-link ${if currentPath == "/blog"     then "active" else ""}")("blog"),
        a(href := "/#contact", cls := "nav-link nav-cta")("contact"),
      ),
    )

  private def footer =
    tag("footer")(cls := "site-footer")(
      div(cls := "footer-inner")(
        span(cls := "footer-copy")("© 2025 Antonio Rotundo"),
        span(cls := "footer-sep")("//"),
        span(cls := "footer-built")("built with Scala + ZIO"),
      ),
    )

  def page(pageTitle: String, currentPath: String, content: Modifier*): String =
    "<!DOCTYPE html>" + html(lang := "en")(
      headBlock(pageTitle),
      body(
        navbar(currentPath),
        main(cls := "site-main")(content*),
        footer,
        script(src := "/static/js/main.js"),
      ),
    ).render

// ── Home page ─────────────────────────────────────────────────────────────────

object HomeView:

  def render(profile: Profile, featuredProjects: List[Project], latestPosts: List[BlogPost]): String =
    Layout.page("Antonio Rotundo — Software Engineer", "/",
      // Hero
      section(cls := "hero")(
        div(cls := "hero-label")("> whoami"),
        h1(cls := "hero-name")(profile.name),
        p(cls  := "hero-role")(
          span(cls := "role-prefix")("_ "),
          profile.role,
        ),
        p(cls := "hero-location")(profile.location),
        p(cls := "hero-bio")(profile.bio),
        div(cls := "hero-actions")(
          a(href := "/projects", cls := "btn btn-primary")("view projects"),
          a(href := "/blog",     cls := "btn btn-ghost")("read blog"),
        ),
        div(cls := "hero-stats")(
          div(cls := "stat")(span(cls := "stat-num")(featuredProjects.length.toString), span(cls := "stat-label")("projects")),
          div(cls := "stat")(span(cls := "stat-num")(latestPosts.length.toString), span(cls := "stat-label")("articles")),
          div(cls := "stat")(span(cls := "stat-num")("5+"), span(cls := "stat-label")("years")),
        ),
      ),

      // Skills
      section(cls := "skills-section")(
        div(cls := "section-header")(
          span(cls := "section-tag")("// tech_stack"),
        ),
        div(cls := "skills-grid")(
          profile.skills.map(skill => span(cls := "skill-tag")(skill))*
        ),
      ),

      // Featured projects
      section(cls := "featured-section")(
        div(cls := "section-header")(
          span(cls := "section-tag")("// featured_projects"),
          a(href := "/projects", cls := "see-all")("see all →"),
        ),
        div(cls := "projects-grid")(
          featuredProjects.take(3).map(proj => ProjectCard.mini(proj))*
        ),
      ),

      // Latest posts
      section(cls := "posts-section")(
        div(cls := "section-header")(
          span(cls := "section-tag")("// latest_posts"),
          a(href := "/blog", cls := "see-all")("see all →"),
        ),
        div(cls := "posts-list")(
          latestPosts.take(3).map(post => BlogCard.mini(post))*
        ),
      ),

      // Contact
      section(cls := "contact-section", id := "contact")(
        span(cls := "section-tag")("// get_in_touch"),
        h2(cls := "contact-title")("Let's build something"),
        p(cls  := "contact-sub")("Open to interesting projects and conversations."),
        a(href := s"mailto:${profile.email}", cls := "btn btn-primary btn-lg")(
          profile.email,
        ),
        div(cls := "social-links")(
          profile.socials.map(s =>
            a(href := s.url, cls := "social-link", target := "_blank", rel := "noopener")(s.label)
          )*
        ),
      ),
    )

// ── Projects ──────────────────────────────────────────────────────────────────

object ProjectCard:
  def mini(proj: Project) =
    val links = proj.githubUrl.map(u => a(href := u, cls := "card-link", target := "_blank")("github ↗")).toSeq ++
                proj.liveUrl.map(u  => a(href := u, cls := "card-link card-link-live", target := "_blank")("live ↗")).toSeq
    article(cls := "project-card")(
      div(cls := "card-top")(
        span(cls := s"status-dot status-${proj.status.toString.toLowerCase}"),
        span(cls := "card-year")(proj.year.toString),
      ),
      h3(cls := "card-title")(proj.title),
      p(cls  := "card-desc")(proj.description),
      div(cls := "card-tags")(proj.tags.map(t => span(cls := "tag")(t))*),
      div(cls := "card-links")(links*),
    )

object ProjectsView:
  def render(projects: List[Project]): String =
    Layout.page("Projects — Antonio Rotundo", "/projects",
      section(cls := "page-hero")(
        span(cls := "section-tag")("// projects"),
        h1(cls := "page-title")("Things I've built"),
        p(cls  := "page-subtitle")("Open source tools, experiments, and production systems."),
      ),
      section(cls := "projects-full")(
        div(cls := "projects-grid projects-grid-full")(
          projects.map(proj => ProjectCard.mini(proj))*
        ),
      ),
    )

// ── Blog ─────────────────────────────────────────────────────────────────────

object BlogCard:
  def mini(post: BlogPost) =
    article(cls := "post-row")(
      div(cls := "post-meta")(
        time(cls := "post-date")(post.publishedAt),
        span(cls := "post-read")(s"${post.readingMinutes} min"),
      ),
      div(cls := "post-body")(
        a(href := s"/blog/${post.slug}", cls := "post-title")(post.title),
        p(cls  := "post-excerpt")(post.excerpt),
        div(cls := "post-tags")(post.tags.map(t => span(cls := "tag")(t))*),
      ),
    )

object BlogView:
  def render(posts: List[BlogPost]): String =
    Layout.page("Blog — Antonio Rotundo", "/blog",
      section(cls := "page-hero")(
        span(cls := "section-tag")("// blog"),
        h1(cls := "page-title")("Writing"),
        p(cls  := "page-subtitle")("Notes on Scala, ZIO, distributed systems, and software craft."),
      ),
      section(cls := "blog-list")(
        posts.map(post => BlogCard.mini(post))*
      ),
    )

object BlogPostView:
  def render(post: BlogPost): String =
    Layout.page(s"${post.title} — Antonio Rotundo", "/blog",
      article(cls := "post-full")(
        header(cls := "post-header")(
          div(cls := "post-meta")(
            time(cls := "post-date")(post.publishedAt),
            span(cls := "post-read")(s"${post.readingMinutes} min read"),
          ),
          h1(cls := "post-heading")(post.title),
          p(cls  := "post-lead")(post.excerpt),
          div(cls := "post-tags")(post.tags.map(t => span(cls := "tag")(t))*),
        ),
        div(cls := "post-content")(raw(post.content)),
        tag("footer")(cls := "post-footer")(
          a(href := "/blog", cls := "back-link")("← back to blog"),
        ),
      ),
    )

// ── 404 ───────────────────────────────────────────────────────────────────────

object NotFoundView:
  def render: String =
    Layout.page("404 — Not Found", "",
      section(cls := "not-found")(
        span(cls := "nf-code")("404"),
        h1(cls := "nf-title")("Page not found"),
        p(cls  := "nf-sub")("The route you requested doesn't exist."),
        a(href := "/", cls := "btn btn-primary")("go home"),
      ),
    )
