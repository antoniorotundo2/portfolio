package portfolio.models

import zio.json.*

// ── Project ──────────────────────────────────────────────────────────────────

enum ProjectStatus:
  case Active, Archived, WIP

object ProjectStatus:
  given JsonCodec[ProjectStatus] = JsonCodec.string.transform(
    s => ProjectStatus.valueOf(s),
    _.toString,
  )

  def fromString(s: String): Option[ProjectStatus] =
    s.trim.toLowerCase match
      case "active"   => Some(Active)
      case "archived" => Some(Archived)
      case "wip"      => Some(WIP)
      case _          => None

case class Project(
  id: String,
  title: String,
  description: String,
  longDescription: String,
  tags: List[String],
  githubUrl: Option[String],
  liveUrl: Option[String],
  status: ProjectStatus,
  year: Int,
) derives JsonCodec

// ── Blog ─────────────────────────────────────────────────────────────────────

case class BlogPost(
  id: String,
  slug: String,
  title: String,
  excerpt: String,
  content: String,
  tags: List[String],
  publishedAt: String,
  readingMinutes: Int,
) derives JsonCodec

// ── Profile ──────────────────────────────────────────────────────────────────

case class SocialLink(label: String, url: String, icon: String) derives JsonCodec

case class Profile(
  name: String,
  role: String,
  bio: String,
  location: String,
  email: String,
  skills: List[String],
  socials: List[SocialLink],
) derives JsonCodec

// ── Layout ───────────────────────────────────────────────────────────────────

case class NavLink(label: String, path: String, isCta: Boolean) derives JsonCodec

case class LayoutConfig(
  logoText: String,
  navLinks: List[NavLink],
  footerCopy: String,
  footerBuiltWith: String,
) derives JsonCodec

// ── Home ─────────────────────────────────────────────────────────────────────

case class HomeConfig(
  pageTitle: String,
  // Hero
  heroLabel: String,
  rolePrefix: String,
  heroCtaPrimary: String,
  heroCtaSecondary: String,
  statYearsValue: String,
  statYearsLabel: String,
  // Sections
  sectionSkills: String,
  sectionProjects: String,
  sectionPosts: String,
  sectionContact: String,
  seeAllLabel: String,
  featuredProjectsCount: Int,
  latestPostsCount: Int,
  githubLinkLabel: String,
  liveLinkLabel: String,
  // Contact
  contactTitle: String,
  contactSubtitle: String,
) derives JsonCodec

// ── Projects page ─────────────────────────────────────────────────────────────

case class ProjectsConfig(
  pageTitle: String,
  sectionTag: String,
  heading: String,
  subtitle: String,
  githubLinkLabel: String,
  liveLinkLabel: String,
  readSuffix: String,
) derives JsonCodec

// ── Blog page ─────────────────────────────────────────────────────────────────

case class BlogConfig(
  pageTitle: String,
  pageTitleSuffix: String,
  sectionTag: String,
  heading: String,
  subtitle: String,
  readSuffix: String,
  readSuffixFull: String,
  backLabel: String,
) derives JsonCodec

// ── 404 page ──────────────────────────────────────────────────────────────────

case class NotFoundConfig(
  pageTitle: String,
  errorCode: String,
  errorTitle: String,
  errorSubtitle: String,
  goHomeLabel: String,
) derives JsonCodec
