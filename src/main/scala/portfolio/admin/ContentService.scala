package portfolio.admin

import zio.*
import java.nio.file.{Files, Path, Paths, DirectoryStream}
import scala.jdk.CollectionConverters.*

// ── Algebra ──────────────────────────────────────────────────────────────────

trait ContentService:
  /** Lista tutti i file .md disponibili, raggruppati per sezione. */
  def listFiles: Task[List[ContentFile]]

  /** Legge il contenuto raw di un file .md. */
  def readFile(relativePath: String): Task[String]

  /** Salva il contenuto di un file .md. */
  def writeFile(relativePath: String, content: String): Task[Unit]

  /** Indica se la cartella contenuti è scrivibile. */
  def isWritable: UIO[Boolean]

/** Rappresenta un file .md nel sistema. */
case class ContentFile(
  relativePath: String,  // es. "blog/zio-2-fibers-explained.md"
  displayName: String,   // es. "ZIO 2 Fibers" (dal frontmatter, se possibile)
  section: String,       // es. "blog", "home", "projects"
)

// ── Implementazione ──────────────────────────────────────────────────────────

object ContentServiceLive:

  val layer: ZLayer[Any, Nothing, ContentService] =
    ZLayer.succeed(Live())

  private final class Live extends ContentService:

    /** Risolve il path base per i contenuti.
      * Se CONTENT_DIR è impostato, usa quella cartella (scrivibile).
      * Altrimenti, usa le risorse classpath (sola lettura).
      */
    private def resolvePath(relativePath: String): Task[Path] =
      ZIO.attempt {
        AdminConfig.contentDir match
          case Some(dir) =>
            Paths.get(dir, relativePath)
          case None =>
            // Fallback: classpath (non scrivibile!)
            val url = getClass.getResource(s"/$relativePath")
            if url == null then throw new RuntimeException(s"File non trovato: $relativePath")
            Paths.get(url.toURI)
      }

    def isWritable: UIO[Boolean] =
      ZIO.succeed(AdminConfig.contentDir.isDefined)

    def listFiles: Task[List[ContentFile]] =
      AdminConfig.contentDir match
        case Some(dir) =>
          // Leggi dalla cartella esterna
          ZIO.attemptBlocking {
            val base = Paths.get(dir)
            val sections = List("home", "layout", "blog", "projects", "notfound")
            sections.flatMap { section =>
              val sectionDir = base.resolve(section)
              if Files.isDirectory(sectionDir) then
                Files.list(sectionDir).iterator().asScala
                  .filter(p => p.toString.endsWith(".md"))
                  .map { p =>
                    val rel = base.relativize(p).toString.replace("\\", "/")
                    ContentFile(rel, p.getFileName.toString.stripSuffix(".md"), section)
                  }
                  .toList
              else Nil
            }
          }
        case None =>
          // Leggi dal classpath (solo le directory note)
          ZIO.attemptBlocking {
            val sections = List("home", "layout", "blog", "projects", "notfound")
            sections.flatMap { section =>
              val url = getClass.getResource(s"/$section")
              if url == null then Nil
              else
                // Nota: nel classpath non possiamo fare list facilmente in tutti i casi
                // Per semplicità, restituiamo i file noti
                val knownFiles = section match
                  case "home"     => List("home.md", "profile.md")
                  case "layout"   => List("layout.md")
                  case "notfound" => List("notfound.md")
                  case "blog"     => List("blog.md") // I singoli post andrebbero scoperti diversamente
                  case "projects" => List("projects.md")
                  case _          => Nil
                knownFiles.map { f =>
                  ContentFile(s"$section/$f", f.stripSuffix(".md"), section)
                }
            }
          }

    def readFile(relativePath: String): Task[String] =
      AdminConfig.contentDir match
        case Some(dir) =>
          ZIO.attemptBlocking {
            val path = Paths.get(dir, relativePath)
            if !Files.exists(path) then throw new RuntimeException(s"File non trovato: $relativePath")
            Files.readString(path)
          }
        case None =>
          // Leggi dal classpath
          ZIO.attemptBlocking {
            val stream = getClass.getResourceAsStream(s"/$relativePath")
            if stream == null then throw new RuntimeException(s"File non trovato: $relativePath")
            new String(stream.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8)
          }

    def writeFile(relativePath: String, content: String): Task[Unit] =
      AdminConfig.contentDir match
        case Some(dir) =>
          ZIO.attemptBlocking {
            val path = Paths.get(dir, relativePath)
            // Crea le directory padre se non esistono
            Files.createDirectories(path.getParent)
            Files.writeString(path, content, java.nio.charset.StandardCharsets.UTF_8)
          }
        case None =>
          ZIO.fail(new RuntimeException(
            "Impossibile salvare: CONTENT_DIR non impostato. " +
            "Imposta la variabile d'ambiente CONTENT_DIR per abilitare la scrittura."
          ))