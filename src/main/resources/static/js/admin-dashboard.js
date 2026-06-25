let currentFile = null;

async function loadFiles() {
  try {
    const r = await fetch('/admin/api/files', { credentials: 'include' });
    if (r.status === 401) {
      window.location.href = '/admin';
      return;
    }
    const d = await r.json();
    renderFileList(d.files);
  } catch (e) {
    console.error(e);
  }
}

function renderFileList(files) {
  const c = document.getElementById('file-list');
  c.innerHTML = '';
  const s = {};
  files.forEach(f => {
    if (!s[f.section]) s[f.section] = [];
    s[f.section].push(f);
  });
  Object.entries(s).forEach(([sec, fs]) => {
    const l = document.createElement('div');
    l.className = 'file-section-label';
    l.textContent = '// ' + sec;
    c.appendChild(l);
    fs.forEach(f => {
      const b = document.createElement('button');
      b.className = 'file-item';
      b.textContent = f.displayName || f.relativePath;
      b.dataset.path = f.relativePath;
      b.onclick = () => openFile(f.relativePath, b);
      c.appendChild(b);
    });
  });
}

async function openFile(p, b) {
  document.querySelectorAll('.file-item').forEach(el => el.classList.remove('active'));
  if (b) b.classList.add('active');
  try {
    const r = await fetch('/admin/api/files/get?filename=' + encodeURIComponent(p), { credentials: 'include' });
    if (r.status === 401) {
      window.location.href = '/admin';
      return;
    }
    const d = await r.json();
    currentFile = p;
    document.getElementById('editor-filename').textContent = p;
    document.getElementById('editor-content').value = d.content;
    document.getElementById('editor-placeholder').style.display = 'none';
    document.getElementById('editor-container').style.display = 'flex';
    document.getElementById('editor-container').style.flexDirection = 'column';
    document.getElementById('editor-container').style.flex = '1';
    document.getElementById('save-status').textContent = '';
    updatePreview();
  } catch (e) {
    console.error(e);
  }
}

async function saveFile() {
  if (!currentFile) return;
  const s = document.getElementById('save-status');
  s.textContent = 'Salvataggio...';
  s.className = 'save-status';
  try {
    const c = document.getElementById('editor-content').value;
    const r = await fetch('/admin/api/files', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ path: currentFile, content: c }),
      credentials: 'include'
    });
    if (r.status === 401) {
      window.location.href = '/admin';
      return;
    }
    const d = await r.json();
    if (d.success) {
      s.innerHTML = `OK: ${d.message}<br><small><a href="${d.commitUrl}" target="_blank" class="commit-link">View commit</a> - ${d.rebuildNote}</small>`;
      s.className = 'save-status success';
    } else {
      s.textContent = 'Error: ' + d.error;
      s.className = 'save-status error';
    }
  } catch (e) {
    s.textContent = 'Error: ' + e.message;
    s.className = 'save-status error';
  }
  setTimeout(() => {
    if (!s.textContent.includes('View commit')) s.textContent = '';
  }, 10000);
}

function insertMarkdown(before, after) {
  const ta = document.getElementById('editor-content');
  const start = ta.selectionStart;
  const end = ta.selectionEnd;
  const text = ta.value;
  const selected = text.substring(start, end);
  ta.value = text.substring(0, start) + before + selected + after + text.substring(end);
  ta.focus();
  ta.selectionStart = start + before.length;
  ta.selectionEnd = start + before.length + selected.length;
  updatePreview();
}

function togglePreview() {
  const main = document.querySelector('.editor-main');
  const btn = document.getElementById('preview-btn');
  main.classList.toggle('split-view');
  btn.classList.toggle('preview-active');
  if (main.classList.contains('split-view')) {
    btn.innerHTML = '✕ Close';
  } else {
    btn.innerHTML = '👁 Preview';
  }
  updatePreview();
}

function updatePreview() {
  const preview = document.getElementById('editor-preview');
  const main = document.querySelector('.editor-main');
  if (!preview || !main.classList.contains('split-view')) return;
  const md = document.getElementById('editor-content').value;
  preview.innerHTML = parseMarkdown(md);
}

document.getElementById('editor-content')?.addEventListener('input', updatePreview);

function parseMarkdown(md) {
  md = md.replace(/```(\w*)\n([\s\S]*?)```/g, '<pre><code class="language-$1">$2</code></pre>');
  md = md.replace(/^#### (.+)$/gm, '<h4>$1</h4>');
  md = md.replace(/^### (.+)$/gm, '<h3>$1</h3>');
  md = md.replace(/^## (.+)$/gm, '<h2>$1</h2>');
  md = md.replace(/^# (.+)$/gm, '<h1>$1</h1>');
  md = md.replace(/\*\*\*(.+?)\*\*\*/g, '<strong><em>$1</em></strong>');
  md = md.replace(/\*\*(.+?)\*\*/g, '<strong>$1</strong>');
  md = md.replace(/\*(.+?)\*/g, '<em>$1</em>');
  md = md.replace(/!\[([^\]]*)\]\(([^)]+)\)/g, '<img src="$2" alt="$1">');
  md = md.replace(/\[([^\]]+)\]\(([^)]+)\)/g, '<a href="$2" target="_blank">$1</a>');
  md = md.replace(/`([^`]+)`/g, '<code>$1</code>');
  md = md.replace(/^---$/gm, '<hr>');
  md = md.replace(/^> (.+)$/gm, '<blockquote>$1</blockquote>');
  md = md.replace(/^(\d+)\. (.+)$/gm, '<li>$2</li>');
  md = md.replace(/^- (.+)$/gm, '<li>$1</li>');
  md = md.replace(/\n\n/g, '</p><p>');
  md = md.replace(/\n/g, '<br>');
  return '<p>' + md + '</p>';
}

async function logout() {
  await fetch('/admin/api/logout', { method: 'POST', credentials: 'include' });
  window.location.href = '/admin';
}

document.addEventListener('keydown', e => {
  if ((e.ctrlKey || e.metaKey) && e.key === 's') {
    e.preventDefault();
    saveFile();
  }
});

// Binding dei controlli via addEventListener (nessun onclick inline → CSP stretta).
function bindControls() {
  document.getElementById('btn-logout')?.addEventListener('click', logout);
  document.getElementById('btn-save')?.addEventListener('click', saveFile);
  document.getElementById('preview-btn')?.addEventListener('click', togglePreview);
  document.querySelectorAll('.toolbar-btn[data-md-before]').forEach(btn => {
    btn.addEventListener('click', () => {
      insertMarkdown(btn.dataset.mdBefore || '', btn.dataset.mdAfter || '');
    });
  });
}

bindControls();
loadFiles();