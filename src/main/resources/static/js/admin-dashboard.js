let currentFile = null;

async function loadFiles() {
  try {
    const r = await fetch('/admin/api/files');
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
      b.textContent = f.displayName || f.path;
      b.dataset.path = f.path;
      b.onclick = () => openFile(f.path, b);
      c.appendChild(b);
    });
  });
}

async function openFile(p, b) {
  document.querySelectorAll('.file-item').forEach(el => el.classList.remove('active'));
  if (b) b.classList.add('active');
  try {
    const r = await fetch('/admin/api/files/' + p);
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
  } catch (e) {
    console.error(e);
  }
}

async function saveFile() {
  if (!currentFile) return;
  const s = document.getElementById('save-status');
  s.textContent = 'Committing...';
  s.className = 'save-status';
  try {
    const c = document.getElementById('editor-content').value;
    const r = await fetch('/admin/api/files', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ path: currentFile, content: c })
    });
    if (r.status === 401) {
      window.location.href = '/admin';
      return;
    }
    const d = await r.json();
    if (d.success) {
      s.innerHTML = `OK: ${d.message}<br><small><a href="${d.commitUrl}" target="_blank" style="color:#00ff88">View commit</a> - ${d.rebuildNote}</small>`;
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

async function logout() {
  await fetch('/admin/api/logout', { method: 'POST' });
  window.location.href = '/admin';
}

document.addEventListener('keydown', e => {
  if ((e.ctrlKey || e.metaKey) && e.key === 's') {
    e.preventDefault();
    saveFile();
  }
});

loadFiles();