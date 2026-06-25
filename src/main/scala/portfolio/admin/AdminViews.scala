package portfolio.admin

import scalatags.Text.all.*

object AdminViews:

  private val headBlock = head(
    meta(charset := "utf-8"),
    meta(name    := "viewport", content := "width=device-width, initial-scale=1"),
    tag("title")("Admin — Portfolio"),
    link(rel := "preconnect", href := "https://fonts.googleapis.com"),
    link(
      rel := "stylesheet",
      href := "https://fonts.googleapis.com/css2?family=Space+Mono:ital,wght@0,400;0,700;1,400&family=Syne:wght@400;600;700;800&display=swap"
    ),
    link(rel := "stylesheet", href := "/static/css/main.css"),
    link(rel := "stylesheet", href := "/static/css/admin.css")
  )

  val loginPage: String =
    "<!DOCTYPE html>" + html(lang := "en")(
      headBlock,
      body(cls := "admin-body")(
        div(cls := "admin-container")(
          div(cls := "admin-card")(
            h1(cls := "admin-title")("[ Admin ]"),
            p(cls := "admin-subtitle")("Enter your email to receive the code."),
            div(id := "step-request")(
              div(cls := "form-group")(
                label(`for` := "email")("Email"),
                input(
                  `type`   := "email",
                  id       := "email",
                  value    := AdminConfig.adminEmail,
                  readonly := true,
                  cls      := "admin-input"
                )
              ),
              button(cls := "btn btn-primary admin-btn", onclick := "requestOtp()")(
                "Request OTP Code"
              )
            ),
            div(id := "step-verify", display.none)(
              div(cls := "otp-sent-badge")(
                "✓ Codice inviato a ",
                AdminConfig.adminEmail
              ),
              div(cls := "otp-field")(
                label(cls := "otp-label", `for` := "otp-1")(
                  s"Inserisci il codice a ${AdminConfig.otpLength} cifre"
                ),
                div(cls := "otp-code-group")(
                  input(
                    `type`            := "text",
                    id                := "otp-1",
                    maxlength         := "1",
                    cls               := "otp-digit",
                    attr("inputmode") := "numeric",
                    autocomplete      := "off"
                  ),
                  input(
                    `type`            := "text",
                    id                := "otp-2",
                    maxlength         := "1",
                    cls               := "otp-digit",
                    attr("inputmode") := "numeric",
                    autocomplete      := "off"
                  ),
                  input(
                    `type`            := "text",
                    id                := "otp-3",
                    maxlength         := "1",
                    cls               := "otp-digit",
                    attr("inputmode") := "numeric",
                    autocomplete      := "off"
                  ),
                  input(
                    `type`            := "text",
                    id                := "otp-4",
                    maxlength         := "1",
                    cls               := "otp-digit",
                    attr("inputmode") := "numeric",
                    autocomplete      := "off"
                  ),
                  input(
                    `type`            := "text",
                    id                := "otp-5",
                    maxlength         := "1",
                    cls               := "otp-digit",
                    attr("inputmode") := "numeric",
                    autocomplete      := "off"
                  ),
                  input(
                    `type`            := "text",
                    id                := "otp-6",
                    maxlength         := "1",
                    cls               := "otp-digit",
                    attr("inputmode") := "numeric",
                    autocomplete      := "off"
                  )
                )
              ),
              button(cls := "otp-back", onclick := "backToRequest()")(
                "← Torna indietro"
              )
            ),
            div(id := "message", cls := "admin-message")
          )
        ),
        script(src := "/static/js/admin-login.js")
      )
    ).render

  def dashboardPage(isWritable: Boolean, isGitHubMode: Boolean): String =
    val badge = if isGitHubMode then "admin-badge-info" else "admin-badge-warn"
    val text  = if isGitHubMode then "GitHub attivo" else "Sola lettura"
    "<!DOCTYPE html>" + html(lang := "en")(
      headBlock,
      body(cls := "admin-body")(
        div(cls := "admin-dashboard")(
          div(cls := "admin-header")(
            h1(cls := "admin-title")("[ Admin Dashboard ]"),
            div(cls := "admin-header-actions")(
              span(cls := s"admin-badge $badge")(text),
              button(cls := "btn btn-ghost", onclick := "logout()")("Logout")
            )
          ),
          div(cls := "admin-layout")(
            div(cls := "admin-sidebar")(
              h3(cls := "sidebar-title")("Sections"),
              div(id := "file-list", cls := "file-list")
            ),
            div(cls := "admin-editor")(
              div(id := "editor-placeholder", cls := "editor-placeholder")("Select a file"),
              div(id := "editor-container", display.none)(
                div(cls := "editor-header")(
                  h2(id := "editor-filename", cls := "editor-filename")(""),
                  div(cls := "editor-actions")(
                    span(id := "save-status", cls := "save-status")(""),
                    button(cls := "btn btn-primary", onclick := "saveFile()")("Save")
                  )
                ),
                div(cls := "editor-toolbar")(
                  button(
                    cls     := "toolbar-btn",
                    onclick := "insertMarkdown('**', '**')",
                    title   := "Bold"
                  )("B"),
                  button(
                    cls     := "toolbar-btn",
                    onclick := "insertMarkdown('*', '')",
                    title   := "Italic"
                  )("I"),
                  button(
                    cls     := "toolbar-btn",
                    onclick := "insertMarkdown('# ', '')",
                    title   := "Heading"
                  )("H"),
                  button(
                    cls     := "toolbar-btn",
                    onclick := "insertMarkdown('[', '](url)')",
                    title   := "Link"
                  )("🔗"),
                  button(
                    cls     := "toolbar-btn",
                    onclick := "insertMarkdown('`', '`')",
                    title   := "Code"
                  )("<>"),
                  button(
                    cls     := "toolbar-btn",
                    onclick := "insertMarkdown('- ', '')",
                    title   := "List"
                  )("≡"),
                  span(cls := "toolbar-separator"),
                  button(
                    cls     := "toolbar-btn preview-toggle",
                    id      := "preview-btn",
                    onclick := "togglePreview()",
                    title   := "Preview"
                  )("👁 Preview")
                ),
                div(cls := "editor-main")(
                  textarea(id := "editor-content", cls := "editor-textarea", spellcheck := "false"),
                  div(id      := "editor-preview", cls := "editor-preview", display.none)
                )
              )
            )
          )
        ),
        script(src := "/static/js/admin-dashboard.js")
      )
    ).render
