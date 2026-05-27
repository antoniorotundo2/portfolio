// ── Generic resource loader ───────────────────────────────────────────────────

object ResourceLoader:

  // Carica tutti i file .md da una directory nel classpath
  def loadDirectory(dirName: String): Task[List[(String, String)]] =
    ZIO.attemptBlocking {
      val url = getClass.getResource(s"/$dirName")
      if (url == null) throw new RuntimeException(s"$dirName/ directory not found in classpath")

      val uri = url.toURI
      var jarFs: Option[FileSystem] = None

      try {
        val dir: Path = uri.getScheme match {
          case "file" => Paths.get(uri)
          case "jar" =>
            val env = emptyMap[String, Any]()
            val fs  = FileSystems.newFileSystem(uri, env)
            jarFs = Some(fs)
            fs.getPath(s"/$dirName")
          case other =>
            throw new RuntimeException(s"Unsupported URI scheme: $other")
        }

        Files.list(dir).iterator().asScala
          .filter(_.toString.endsWith(".md"))
          .map { path =>
            val slug = path.getFileName.toString.stripSuffix(".md")
            val raw  = Files.readString(path)
            slug -> raw
          }
          .toList
      } finally {
        jarFs.foreach(_.close())
      }
    }

// ── Profile loader ────────────────────────────────────────────────────────────

object ProfileLoader:

  def load: Task[Profile] =
    ResourceLoader.loadDirectory("profile").flatMap { files =>
      files.headOption match
        case Some((_, raw)) => ZIO.fromOption(parseProfile(raw))
          .orElseFail(new RuntimeException("Failed to parse profile"))
        case None => ZIO.fail(new RuntimeException("profile.md not found"))
    }

  private def parseProfile(raw: String): Option[Profile] =
    val (fm, _) = MarkdownParser.parse(raw)
    for
      name     <- MarkdownParser.frontString(fm, "name")
      role     <- MarkdownParser.frontString(fm, "role")
      bio      <- MarkdownParser.frontString(fm, "bio")
      location <- MarkdownParser.frontString(fm, "location")
      email    <- MarkdownParser.frontString(fm, "email")
    yield Profile(
      name     = name,
      role     = role,
      bio      = bio,
      location = location,
      email    = email,
      skills   = MarkdownParser.frontList(fm, "skills"),
      socials  = parseSocials(fm),
    )

  private def parseSocials(fm: Map[String, List[String]]): List[SocialLink] =
    MarkdownParser.frontList(fm, "socials").flatMap { entry =>
      entry.split("\\|").toList match
        case label :: url :: icon :: Nil => Some(SocialLink(label, url, icon))
        case _ => None
    }

// ── Project loader ────────────────────────────────────────────────────────────

object ProjectLoader:

  def loadAll: Task[List[Project]] =
    ResourceLoader.loadDirectory("projects").map { files =>
      files.flatMap { (_, raw) => parseProject(raw) }
        .sortBy(_.year).reverse
    }

  private def parseProject(raw: String): Option[Project] =
    val (fm, html) = MarkdownParser.parse(raw)
    for
      id          <- MarkdownParser.frontString(fm, "id")
      title       <- MarkdownParser.frontString(fm, "title")
      description <- MarkdownParser.frontString(fm, "description")
    yield Project(
      id              = id,
      title           = title,
      description     = description,
      longDescription = html.strip(),
      tags            = MarkdownParser.frontList(fm, "tags"),
      githubUrl       = MarkdownParser.frontString(fm, "githubUrl"),
      liveUrl         = MarkdownParser.frontString(fm, "liveUrl"),
      status          = MarkdownParser.frontString(fm, "status")
                          .flatMap(ProjectStatus.fromString)
                          .getOrElse(ProjectStatus.Active),
      year            = MarkdownParser.frontInt(fm, "year", 2024),
    )