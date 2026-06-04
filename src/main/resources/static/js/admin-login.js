const messageEl = document.getElementById('message');

function showMessage(t, y) {
  messageEl.textContent = t;
  messageEl.className = 'admin-message ' + y;
}

async function requestOtp() {
  showMessage('Sending...', 'success');
  try {
    const r = await fetch('/admin/api/request-otp', { method: 'POST' });
    if (!r.ok) {
      const err = await r.text();
      showMessage('Error: ' + err, 'error');
      return;
    }
    try {
      const d = await r.json();
      showMessage('OK: ' + (d.message || 'Code sent!'), 'success');
    } catch (e) {
      showMessage('OK: Code sent!', 'success');
    }
    document.getElementById('step-request').style.display = 'none';
    document.getElementById('step-verify').style.display = 'block';
    document.getElementById('otp').focus();
  } catch (e) {
    showMessage('Error: ' + e.message, 'error');
  }
}

async function verifyOtp() {
  const c = document.getElementById('otp').value.trim();
  if (c.length !== 6) {
    showMessage('6-digit code required', 'error');
    return;
  }
  showMessage('Verifying...', 'success');
  try {
    const r = await fetch('/admin/api/verify-otp', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ otp: c })
    });
    if (r.ok) {
      window.location.href = '/admin/dashboard';
    } else {
      const err = await r.text();
      showMessage('Error: ' + err, 'error');
    }
  } catch (e) {
    showMessage('Error: ' + e.message, 'error');
  }
}

function backToRequest() {
  document.getElementById('step-request').style.display = 'block';
  document.getElementById('step-verify').style.display = 'none';
  messageEl.className = 'admin-message';
}

document.getElementById('otp')?.addEventListener('keydown', e => {
  if (e.key === 'Enter') verifyOtp();
});