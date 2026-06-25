package portfolio.views

import scalatags.Text.all.*
import scalatags.Text.tags2.{nav, main, section, article, time}
import scalatags.Text.TypedTag
import portfolio.models.*

// ── Icons ─────────────────────────────────────────────────────────────────────

object Icons:

  private val svgs: Map[String, String] = Map(
    "github" ->
      """<svg xmlns="http://www.w3.org/2000/svg" width="18" height="18" viewBox="0 0 24 24" fill="currentColor" aria-hidden="true">
        <path d="M12 0C5.37 0 0 5.37 0 12c0 5.31 3.435 9.795 8.205 11.385.6.105.825-.255.825-.57 0-.285-.015-1.23-.015-2.235-3.015.555-3.795-.735-4.035-1.41-.135-.345-.72-1.41-1.23-1.695-.42-.225-1.02-.78-.015-.795.945-.015 1.62.87 1.845 1.23 1.08 1.815 2.805 1.305 3.495.99.105-.78.42-1.305.765-1.605-2.67-.3-5.46-1.335-5.46-5.925 0-1.305.465-2.385 1.23-3.225-.12-.3-.54-1.53.12-3.18 0 0 1.005-.315 3.3 1.23.96-.27 1.98-.405 3-.405s2.04.135 3 .405c2.295-1.56 3.3-1.23 3.3-1.23.66 1.65.24 2.88.12 3.18.765.84 1.23 1.905 1.23 3.225 0 4.605-2.805 5.625-5.475 5.925.435.375.81 1.095.81 2.22 0 1.605-.015 2.895-.015 3.3 0 .315.225.69.825.57A12.02 12.02 0 0 0 24 12c0-6.63-5.37-12-12-12z"/>
      </svg>""",
    "linkedin" ->
      """<svg xmlns="http://www.w3.org/2000/svg" width="18" height="18" viewBox="0 0 24 24" fill="currentColor" aria-hidden="true">
        <path d="M20.447 20.452h-3.554v-5.569c0-1.328-.027-3.037-1.852-3.037-1.853 0-2.136 1.445-2.136 2.939v5.667H9.351V9h3.414v1.561h.046c.477-.9 1.637-1.85 3.37-1.85 3.601 0 4.267 2.37 4.267 5.455v6.286zM5.337 7.433a2.062 2.062 0 0 1-2.063-2.065 2.064 2.064 0 1 1 2.063 2.065zm1.782 13.019H3.555V9h3.564v11.452zM22.225 0H1.771C.792 0 0 .774 0 1.729v20.542C0 23.227.792 24 1.771 24h20.451C23.2 24 24 23.227 24 22.271V1.729C24 .774 23.2 0 22.222 0h.003z"/>
      </svg>""",
    "twitter" ->
      """<svg xmlns="http://www.w3.org/2000/svg" width="18" height="18" viewBox="0 0 24 24" fill="currentColor" aria-hidden="true">
        <path d="M18.244 2.25h3.308l-7.227 8.26 8.502 11.24H16.17l-4.714-6.231-5.401 6.231H2.746l7.73-8.835L1.254 2.25H8.08l4.259 5.63L18.244 2.25zm-1.161 17.52h1.833L7.084 4.126H5.117z"/>
      </svg>""",
    "email" ->
      """<svg xmlns="http://www.w3.org/2000/svg" width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
        <rect width="20" height="16" x="2" y="4" rx="2"/><path d="m22 7-8.97 5.7a1.94 1.94 0 0 1-2.06 0L2 7"/>
      </svg>""",
    "website" ->
      """<svg xmlns="http://www.w3.org/2000/svg" width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
        <circle cx="12" cy="12" r="10"/><path d="M12 2a14.5 14.5 0 0 1 0 20 14.5 14.5 0 0 1 0-20"/><path d="M2 12h20"/>
      </svg>"""
  )

  // Returns the SVG markup for the given icon key, falling back to a generic link icon
  def apply(name: String): scalatags.Text.all.Modifier =
    raw(svgs.getOrElse(
      name.toLowerCase.trim,
      """<svg xmlns="http://www.w3.org/2000/svg" width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
        <path d="M10 13a5 5 0 0 0 7.54.54l3-3a5 5 0 0 0-7.07-7.07l-1.72 1.71"/><path d="M14 11a5 5 0 0 0-7.54-.54l-3 3a5 5 0 0 0 7.07 7.07l1.71-1.71"/>
      </svg>"""
    ))

// ── Design tokens (injected via CSS variables defined in stylesheet) ──────────

object Layout:

  // Shared <head> block: include meta description + Open Graph/Twitter per SEO e condivisioni.
  private def headBlock(pageTitle: String, description: String) =
    head(
      meta(charset := "utf-8"),
      meta(name    := "viewport", content := "width=device-width, initial-scale=1"),
      tag("title")(pageTitle),
      meta(name             := "description", content    := description),
      meta(attr("property") := "og:title", content       := pageTitle),
      meta(attr("property") := "og:description", content := description),
      meta(attr("property") := "og:type", content        := "website"),
      meta(name             := "twitter:card", content   := "summary"),
      link(rel              := "preconnect", href        := "https://fonts.googleapis.com"),
      link(rel := "preconnect", href := "https://fonts.gstatic.com", attr("crossorigin") := ""),
      link(
        rel := "stylesheet",
        href := "https://fonts.googleapis.com/css2?family=Space+Mono:ital,wght@0,400;0,700;1,400&family=Syne:wght@400;600;700;800&display=swap"
      ),
      link(rel := "stylesheet", href := "/static/css/main.css")
    )

  // Navigation bar driven by LayoutConfig
  private def navbar(layout: LayoutConfig, currentPath: String) =
    nav(cls := "navbar")(
      a(href := "/", cls := "nav-logo")(
        span(cls := "logo-bracket")("["),
        layout.logoText,
        span(cls := "logo-bracket")("]")
      ),
      div(cls := "nav-links")(
        layout.navLinks.map { link =>
          val activeClass =
            if link.path == currentPath || (link.path != "/" && currentPath.startsWith(link.path))
            then " active"
            else ""
          val ctaClass = if link.isCta then " nav-cta" else ""
          a(href := link.path, cls := s"nav-link$activeClass$ctaClass")(link.label)
        }*
      )
    )

  private def footer(layout: LayoutConfig) =
    tag("footer")(cls := "site-footer")(
      div(cls := "footer-inner")(
        span(cls := "footer-copy")(layout.footerCopy),
        span(cls := "footer-sep")("//"),
        span(cls := "footer-built")(layout.footerBuiltWith)
      )
    )

  def page(
      layout: LayoutConfig,
      pageTitle: String,
      description: String,
      currentPath: String,
      content: Modifier*
  ): String =
    "<!DOCTYPE html>" + html(lang := "en")(
      headBlock(pageTitle, description),
      body(
        navbar(layout, currentPath),
        main(cls := "site-main")(content*),
        footer(layout),
        script(src := "/static/js/main.js")
      )
    ).render

// ── Home page ─────────────────────────────────────────────────────────────────

object HomeView:

  def render(
      layout: LayoutConfig,
      home: HomeConfig,
      profile: Profile,
      featuredProjects: List[Project],
      latestPosts: List[BlogPost]
  ): String =
    Layout.page(
      layout,
      home.pageTitle,
      profile.bio,
      "/",
      // Hero
      section(cls := "hero")(
        div(cls := "hero-label")(home.heroLabel),
        h1(cls := "hero-name")(profile.name),
        p(cls := "hero-role")(
          span(cls := "role-prefix")(home.rolePrefix),
          profile.role
        ),
        p(cls := "hero-bio")(profile.bio),
        div(cls := "hero-actions")(
          a(href := "/projects", cls := "btn btn-primary")(home.heroCtaPrimary),
          a(href := "/blog", cls := "btn btn-ghost")(home.heroCtaSecondary)
        ),
        div(cls := "hero-stats")(
          div(cls := "stat")(
            span(cls := "stat-num")(featuredProjects.length.toString),
            span(cls := "stat-label")("projects")
          ),
          div(cls := "stat")(
            span(cls := "stat-num")(latestPosts.length.toString),
            span(cls := "stat-label")("articles")
          ),
          div(cls := "stat")(
            span(cls := "stat-num")(home.statYearsValue),
            span(cls := "stat-label")(home.statYearsLabel)
          )
        )
      ),

      // Skills
      section(cls := "skills-section")(
        div(cls := "section-header")(
          span(cls := "section-tag")(home.sectionSkills)
        ),
        div(cls := "skills-grid")(
          profile.skills.map(skill => span(cls := "skill-tag")(skill))*
        )
      ),

      // Featured projects
      section(cls := "featured-section")(
        div(cls := "section-header")(
          span(cls := "section-tag")(home.sectionProjects),
          a(href := "/projects", cls := "see-all")(home.seeAllLabel)
        ),
        div(cls := "projects-grid")(
          featuredProjects.take(home.featuredProjectsCount).map(proj =>
            ProjectCard.mini(proj, home.githubLinkLabel, home.liveLinkLabel)
          )*
        )
      ),

      // Latest posts
      section(cls := "posts-section")(
        div(cls := "section-header")(
          span(cls := "section-tag")(home.sectionPosts),
          a(href := "/blog", cls := "see-all")(home.seeAllLabel)
        ),
        div(cls := "posts-list")(
          latestPosts.take(home.latestPostsCount).map(post => BlogCard.mini(post, home.readSuffix))*
        )
      ),

      // Contact
      section(cls := "contact-section", id := "contact")(
        span(cls := "section-tag")(home.sectionContact),
        h2(cls := "contact-title")(home.contactTitle),
        p(cls := "contact-sub")(home.contactSubtitle),
        a(href := s"mailto:${profile.email}", cls := "btn btn-primary btn-lg")(
          profile.email
        ),
        div(cls := "social-links")(
          profile.socials.map(s =>
            a(
              href               := s.url,
              cls                := "social-link",
              target             := "_blank",
              rel                := "noopener",
              attr("aria-label") := s.label
            )(
              Icons(s.icon),
              span(cls := "social-label")(s.label)
            )
          )*
        )
      )
    )

// ── Projects ──────────────────────────────────────────────────────────────────

object ProjectCard:
  def mini(proj: Project, githubLabel: String, liveLabel: String) =
    val links = proj.githubUrl.map(u =>
      a(href := u, cls := "card-link", target := "_blank")(githubLabel)
    ).toSeq ++
      proj.liveUrl.map(u =>
        a(href := u, cls := "card-link card-link-live", target := "_blank")(liveLabel)
      ).toSeq
    article(cls := "project-card")(
      div(cls := "card-top")(
        span(cls := s"status-dot status-${proj.status.toString.toLowerCase}"),
        span(cls := "card-year")(proj.year.toString)
      ),
      h3(cls := "card-title")(proj.title),
      p(cls := "card-desc")(proj.description),
      div(cls := "card-tags")(proj.tags.map(t => span(cls := "tag")(t))*),
      div(cls := "card-links")(links*)
    )

object ProjectsView:
  def render(layout: LayoutConfig, cfg: ProjectsConfig, projects: List[Project]): String =
    Layout.page(
      layout,
      cfg.pageTitle,
      cfg.subtitle,
      "/projects",
      section(cls := "page-hero")(
        span(cls := "section-tag")(cfg.sectionTag),
        h1(cls := "page-title")(cfg.heading),
        p(cls := "page-subtitle")(cfg.subtitle)
      ),
      section(cls := "projects-full")(
        div(cls := "projects-grid projects-grid-full")(
          projects.map(proj => ProjectCard.mini(proj, cfg.githubLinkLabel, cfg.liveLinkLabel))*
        )
      )
    )

// ── Blog ─────────────────────────────────────────────────────────────────────

object BlogCard:
  def mini(post: BlogPost, readSuffix: String) =
    article(cls := "post-row")(
      div(cls := "post-meta")(
        time(cls := "post-date")(post.publishedAt),
        span(cls := "post-read")(s"${post.readingMinutes} ${readSuffix}")
      ),
      div(cls := "post-body")(
        a(href := s"/blog/${post.slug}", cls := "post-title")(post.title),
        p(cls := "post-excerpt")(post.excerpt),
        div(cls := "post-tags")(post.tags.map(t => span(cls := "tag")(t))*)
      )
    )

object BlogView:
  def render(layout: LayoutConfig, cfg: BlogConfig, posts: List[BlogPost]): String =
    Layout.page(
      layout,
      cfg.pageTitle,
      cfg.subtitle,
      "/blog",
      section(cls := "page-hero")(
        span(cls := "section-tag")(cfg.sectionTag),
        h1(cls := "page-title")(cfg.heading),
        p(cls := "page-subtitle")(cfg.subtitle)
      ),
      section(cls := "blog-list")(
        posts.map(post => BlogCard.mini(post, cfg.readSuffix))*
      )
    )

object BlogPostView:
  def render(layout: LayoutConfig, cfg: BlogConfig, post: BlogPost): String =
    Layout.page(
      layout,
      s"${post.title}${cfg.pageTitleSuffix}",
      post.excerpt,
      "/blog",
      article(cls := "post-full")(
        header(cls := "post-header")(
          div(cls := "post-meta")(
            time(cls := "post-date")(post.publishedAt),
            span(cls := "post-read")(s"${post.readingMinutes} ${cfg.readSuffixFull}")
          ),
          h1(cls := "post-heading")(post.title),
          p(cls := "post-lead")(post.excerpt),
          div(cls := "post-tags")(post.tags.map(t => span(cls := "tag")(t))*)
        ),
        div(cls := "post-content")(raw(post.content)),
        tag("footer")(cls := "post-footer")(
          a(href := "/blog", cls := "back-link")(cfg.backLabel)
        )
      )
    )

// ── 404 ───────────────────────────────────────────────────────────────────────

object NotFoundView:
  def render(layout: LayoutConfig, cfg: NotFoundConfig): String =
    Layout.page(
      layout,
      cfg.pageTitle,
      cfg.errorSubtitle,
      "",
      section(cls := "not-found")(
        span(cls := "nf-code")(cfg.errorCode),
        h1(cls := "nf-title")(cfg.errorTitle),
        p(cls := "nf-sub")(cfg.errorSubtitle),
        a(href := "/", cls := "btn btn-primary")(cfg.goHomeLabel)
      )
    )
