package portfolio.models

import zio.json.*

// ── Project ──────────────────────────────────────────────────────────────────

enum ProjectStatus:
  case Active, Completed, Archived

object ProjectStatus:
  def fromString(s: String): Option[ProjectStatus] =
    s.toLowerCase match
      case "active"    => Some(Active)
      case "completed" => Some(Completed)
      case "archived"  => Some(Archived)
      case _           => None

  given JsonEncoder[ProjectStatus] =
    JsonEncoder[String].contramap(_.toString.toLowerCase)

  given JsonDecoder[ProjectStatus] =
    JsonDecoder[String].mapOrFail(s =>
      fromString(s).toRight(s"Invalid ProjectStatus: $s")
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
  content: String,         // HTML renderizzato
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