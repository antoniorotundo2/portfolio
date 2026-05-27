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
    "zio" ->
      """<svg xmlns="http://www.w3.org/2000/svg"
          width="42"
          height="18"
          viewBox="0 0 2048 834"
          fill="currentColor"
          aria-hidden="true">

          <path d="M1591,490 L1559,490 L1540,527 L1526,545 L1501,569 L1463,592 L1448,598 L1420,604 L1383,604 L1358,601 L1331,593 L1311,583 L1285,566 L1270,551 L1256,532 L1244,510 L1241,500 L1240,502 L1248,521 L1265,548 L1288,572 L1310,588 L1332,600 L1361,611 L1394,617 L1425,617 L1446,614 L1474,606 L1500,594 L1530,574 L1550,556 L1565,538 L1585,506 Z"/>

          <path d="M1164,501 L1259,490 L1398,478 L1463,475 L1579,475 L1580,473 L1592,471 L1656,467 L1657,465 L1726,467 L1731,464 L1750,462 L1839,464 L1898,470 L1914,469 L1890,465 L1779,456 L1625,448 L1506,445 L1327,445 L1168,452 Z"/>

          <path d="M1251,273 L1252,277 L1275,271 L1280,275 L1267,287 L1269,289 L1265,295 L1269,294 L1271,298 L1266,304 L1263,314 L1268,315 L1257,331 L1272,324 L1317,288 L1346,273 L1372,265 L1392,262 L1416,262 L1435,265 L1436,262 L1440,262 L1471,276 L1475,275 L1515,305 L1548,344 L1560,376 L1563,378 L1569,406 L1570,427 L1607,426 L1607,393 L1602,361 L1591,328 L1576,299 L1559,275 L1530,246 L1505,228 L1478,214 L1443,202 L1412,197 L1389,196 L1346,202 L1320,211 L1296,225 L1274,243 L1281,243 L1266,254 Z"/>

          <path d="M1090,170 L1080,178 L1072,194 L1077,286 L1078,289 L1081,288 L1083,291 L1082,312 L1085,314 L1083,335 L1087,360 L1092,372 L1092,389 L1095,393 L1095,404 L1101,428 L1115,520 L1117,571 L1121,586 L1122,614 L1124,618 L1121,664 L1140,501 L1149,377 L1160,284 L1161,237 L1153,208 L1147,214 L1146,197 L1142,191 L1136,191 L1124,201 L1117,202 L1114,201 L1115,192 L1113,192 L1112,204 L1111,188 L1107,192 L1103,191 L1099,182 L1096,180 L1092,182 Z"/>
        </svg>""",
    "email" ->
      """<svg xmlns="http://www.w3.org/2000/svg" width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
        <rect width="20" height="16" x="2" y="4" rx="2"/><path d="m22 7-8.97 5.7a1.94 1.94 0 0 1-2.06 0L2 7"/>
      </svg>""",
    "website" ->
      """<svg xmlns="http://www.w3.org/2000/svg" width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
        <circle cx="12" cy="12" r="10"/><path d="M12 2a14.5 14.5 0 0 1 0 20 14.5 14.5 0 0 1 0-20"/><path d="M2 12h20"/>
      </svg>""",
  )

  def apply(name: String): scalatags.Text.all.Modifier =
    raw(svgs.getOrElse(name.toLowerCase.trim,
      """<svg xmlns="http://www.w3.org/2000/svg" width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
        <path d="M10 13a5 5 0 0 0 7.54.54l3-3a5 5 0 0 0-7.07-7.07l-1.72 1.71"/><path d="M14 11a5 5 0 0 0-7.54-.54l-3 3a5 5 0 0 0 7.07 7.07l1.71-1.71"/>
      </svg>"""
    ))

// ── Design tokens (injected via CSS variables defined in stylesheet) ──────────

object Layout:

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
        span(cls := "footer-built")(
          "built with Scala + ",
          Icons("zio"),
        ),
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
            a(
              href := s.url,
              cls := "social-link",
              target := "_blank",
              rel := "noopener",
              attr("aria-label") := s.label
            )(
              Icons(s.icon),
              span(cls := "social-label")(s.label),
            )
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