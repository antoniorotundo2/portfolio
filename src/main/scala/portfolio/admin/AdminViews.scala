package portfolio.admin

import scalatags.Text.all.*

object AdminViews:

  private val headBlock = head(
    meta(charset := "utf-8"),
    meta(name := "viewport", content := "width=device-width, initial-scale=1"),
    tag("title")("Admin — Portfolio"),
    link(rel := "preconnect", href := "https://fonts.googleapis.com"),
    link(rel := "stylesheet", href := "https://fonts.googleapis.com/css2?family=Space+Mono:ital,wght@0,400;0,700;1,400&family=Syne:wght@400;600;700;800&display=swap"),
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
            p(cls := "admin-subtitle")("Inserisci la tua email per ricevere il codice di accesso."),
            div(id := "step-request")(
              div(cls := "form-group")(
                label(`for` := "email")("Email"),
                input(`type` := "email", id := "email", value := AdminConfig.adminEmail, readonly := true, cls := "admin-input")
              ),
              button(cls := "btn btn-primary admin-btn", onclick := "requestOtp()")("Richiedi Codice OTP")
            ),
            div(id := "step-verify", display.none)(
              p(cls := "admin-hint")(s"Codice inviato a <strong>${AdminConfig.adminEmail}</strong>. Controlla la tua casella."),
              div(cls := "form-group")(
                label(`for` := "otp")("Codice a 6 cifre"),
                input(`type` := "text", id := "otp", maxlength := "6", placeholder := "000000", cls := "admin-input admin-otp-input", autocomplete := "one-time-code")
              ),
              button(cls := "btn btn-primary admin-btn", onclick := "verifyOtp()")("Accedi"),
              button(cls := "btn btn-ghost admin-btn", onclick := "backToRequest()")("← Indietro")
            ),
            div(id := "message", cls := "admin-message")
          )
        ),
        script(raw(loginJs))
      )
    ).render

  def dashboardPage(isWritable: Boolean, isGitHubMode: Boolean): String =
    val statusBadge = if isGitHubMode then "admin-badge-info" else "admin-badge-warn"
    val statusText = if isGitHubMode then "🟢 GitHub Commit attivo" else "🟡 Modalità sola lettura"
    "<!DOCTYPE html>" + html(lang := "en")(
      headBlock,
      body(cls := "admin-body")(
        div(cls := "admin-dashboard")(
          div(cls := "admin-header")(
            h1(cls := "admin-title")("[ Admin Dashboard ]"),
            div(cls := "admin-header-actions")(
              span(cls := s"admin-badge $statusBadge")(statusText),
              button(cls := "btn btn-ghost", onclick := "logout()")("Logout")
            )
          ),
          div(cls := "admin-layout")(
            div(cls := "admin-sidebar")(
              h3(cls := "sidebar-title")("Sezioni"),
              div(id := "file-list", cls := "file-list")
            ),
            div(cls := "admin-editor")(
              div(id := "editor-placeholder", cls := "editor-placeholder")("Seleziona un file dalla barra laterale per modificarlo."),
              div(id := "editor-container", display.none)(
                div(cls := "editor-header")(
                  h2(id := "editor-filename", cls := "editor-filename")(""),
                  div(cls := "editor-actions")(
                    span(id := "save-status", cls := "save-status")(""),
                    button(cls := "btn btn-primary", onclick := "saveFile()")("💾 Salva")
                  )
                ),
                textarea(id := "editor-content", cls := "editor-textarea", spellcheck := "false")
              )
            )
          )
        ),
        script(raw(dashboardJs))
      )
    ).render

  private val loginJs = """
    const messageEl = document.getElementById('message');
    function showMessage(text, type) { messageEl.textContent = text; messageEl.className = 'admin-message ' + type; }
    async function requestOtp() {
      showMessage('Invio in corso...', 'success');
      try {
        const res = await fetch('/admin/api/request-otp', { method: 'POST' });
        const data = await res.json();
        if (res.ok) { showMessage('✅ ' + data.message, 'success'); document.getElementById('step-request').style.display = 'none'; document.getElementById('step-verify').style.display = 'block'; document.getElementById('otp').focus(); }
        else { showMessage('❌ ' + data.error, 'error'); }
      } catch (err) { showMessage('❌ Errore di rete: ' + err.message, 'error'); }
    }
    async function verifyOtp() {
      const code = document.getElementById('otp').value.trim();
      if (code.length !== 6) { showMessage('❌ Inserisci un codice a 6 cifre', 'error'); return; }
      showMessage('Verifica in corso...', 'success');
      try {
        const res = await fetch('/admin/api/verify-otp', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ otp: code }) });
        if (res.ok) { window.location.href = '/admin/dashboard'; } else { const data = await res.json(); showMessage('❌ ' + data.error, 'error'); }
      } catch (err) { showMessage('❌ Errore di rete: ' + err.message, 'error'); }
    }
    function backToRequest() { document.getElementById('step-request').style.display = 'block'; document.getElementById('step-verify').style.display = 'none'; messageEl.className = 'admin-message'; }
    document.getElementById('otp')?.addEventListener('keydown', (e) => { if (e.key === 'Enter') verifyOtp(); });
  """

  private val dashboardJs = """
    let currentFile = null;
    async function loadFiles() {
      try {
        const res = await fetch('/admin/api/files');
        if (res.status === 401) { window.location.href = '/admin'; return; }
        const data = await res.json();
        renderFileList(data.files);
      } catch (err) { console.error('Errore caricamento file:', err); }
    }
    function renderFileList(files) {
      const container = document.getElementById('file-list'); container.innerHTML = '';
      const sections = {}; files.forEach(f => { if (!sections[f.section]) sections[f.section] = []; sections[f.section].push(f); });
      Object.entries(sections).forEach(([section, sectionFiles]) => {
        const label = document.createElement('div'); label.className = 'file-section-label'; label.textContent = '// ' + section; container.appendChild(label);
        sectionFiles.forEach(f => {
          const btn = document.createElement('button'); btn.className = 'file-item'; btn.textContent = f.displayName || f.path; btn.dataset.path = f.path; btn.onclick = () => openFile(f.path, btn); container.appendChild(btn);
        });
      });
    }
    async function openFile(path, btnEl) {
      document.querySelectorAll('.file-item').forEach(el => el.classList.remove('active'));
      if (btnEl) btnEl.classList.add('active');
      try {
        const res = await fetch('/admin/api/files/' + path);
        if (res.status === 401) { window.location.href = '/admin'; return; }
        const data = await res.json();
        currentFile = path; document.getElementById('editor-filename').textContent = path; document.getElementById('editor-content').value = data.content;
        document.getElementById('editor-placeholder').style.display = 'none'; document.getElementById('editor-container').style.display = 'flex';
        document.getElementById('editor-container').style.flexDirection = 'column'; document.getElementById('editor-container').style.flex = '1';
        document.getElementById('save-status').textContent = '';
      } catch (err) { console.error('Errore apertura file:', err); }
    }
    async function saveFile() {
      if (!currentFile) return;
      const statusEl = document.getElementById('save-status'); statusEl.textContent = '🔄 Commit su GitHub...'; statusEl.className = 'save-status';
      try {
        const content = document.getElementById('editor-content').value;
        const res = await fetch('/admin/api/files', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ path: currentFile, content }) });
        if (res.status === 401) { window.location.href = '/admin'; return; }
        const data = await res.json();
        if (data.success) {
          statusEl.innerHTML = `✅ ${data.message}<br><small><a href="${data.commitUrl}" target="_blank" style="color:#00ff88">Vedi commit</a> • ${data.rebuildNote}</small>`;
          statusEl.className = 'save-status success';
        } else { statusEl.textContent = '❌ ' + data.error; statusEl.className = 'save-status error'; }
      } catch (err) { statusEl.textContent = '❌ Errore: ' + err.message; statusEl.className = 'save-status error'; }
      setTimeout(() => { if (!statusEl.textContent.includes('Vedi commit')) statusEl.textContent = ''; }, 10000);
    }
    async function logout() { await fetch('/admin/api/logout', { method: 'POST' }); window.location.href = '/admin'; }
    document.addEventListener('keydown', (e) => { if ((e.ctrlKey || e.metaKey) && e.key === 's') { e.preventDefault(); saveFile(); } });
    loadFiles();
  """