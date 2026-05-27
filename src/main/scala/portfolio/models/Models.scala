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
  content: String,         // Markdown / raw HTML
  tags: List[String],
  publishedAt: String,     // ISO date string
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
