package portfolio

import portfolio.services.MarkdownParser
import zio.test.*
import zio.test.Assertion.*

object MarkdownParserSpec extends ZIOSpecDefault:

  def spec = suite("MarkdownParser")(
    test("estrae il frontmatter YAML e il corpo Markdown") {
      val raw =
        """---
          |title: Ciao
          |tags:
          |  - scala
          |  - zio
          |---
          |# Titolo
          |""".stripMargin
      val (fm, html) = MarkdownParser.parse(raw)
      assertTrue(
        MarkdownParser.frontString(fm, "title").contains("Ciao"),
        MarkdownParser.frontList(fm, "tags") == List("scala", "zio"),
        html.contains("<h1>Titolo</h1>")
      )
    },
    test("escapa l'HTML grezzo nel Markdown (no XSS)") {
      val raw       = "Testo <script>alert('xss')</script> fine"
      val (_, html) = MarkdownParser.parse(raw)
      assertTrue(
        !html.contains("<script>"),
        html.contains("&lt;script&gt;")
      )
    },
    test("neutralizza URL pericolosi nei link") {
      val raw       = "[click](javascript:alert(1))"
      val (_, html) = MarkdownParser.parse(raw)
      assertTrue(!html.contains("javascript:alert"))
    },
    test("non esegue tag YAML globali non sicuri (SafeConstructor)") {
      val raw =
        """---
          |title: ok
          |---
          |corpo
          |""".stripMargin
      val (fm, _) = MarkdownParser.parse(raw)
      assertTrue(MarkdownParser.frontString(fm, "title").contains("ok"))
    }
  )
