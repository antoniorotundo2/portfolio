const messageEl = document.getElementById('message');

function showMessage(t, y) {
  messageEl.textContent = t;
  messageEl.className = 'otp-message ' + y;
}

// ── Step 1: Richiedi OTP ──────────────────────────────────
async function requestOtp() {
  showMessage('Invio in corso...', 'success');
  try {
    const r = await fetch('/admin/api/request-otp', { method: 'POST' });
    if (!r.ok) {
      const err = await r.text();
      showMessage('Errore: ' + err, 'error');
      return;
    }
    try {
      const d = await r.json();
      showMessage(d.message || 'Codice inviato!', 'success');
    } catch (e) {
      showMessage('Codice inviato! Controlla la tua email.', 'success');
    }
    document.getElementById('step-request').style.display = 'none';
    document.getElementById('step-verify').style.display = 'block';
    document.getElementById('otp-1').focus();
  } catch (e) {
    showMessage('Errore di rete: ' + e.message, 'error');
  }
}

// ── Step 2: Gestione input OTP ────────────────────────────
function setupOtpInputs() {
  const digits = document.querySelectorAll('.otp-digit');
  
  digits.forEach((digit, index) => {
    // Permetti solo numeri
    digit.addEventListener('input', (e) => {
      const val = e.target.value.replace(/[^0-9]/g, '');
      e.target.value = val.slice(0, 1);
      
      if (val) {
        digit.classList.add('filled');
        // Passa al prossimo input
        if (index < digits.length - 1) {
          digits[index + 1].focus();
        }
      } else {
        digit.classList.remove('filled');
      }
      
      // Auto-submit quando tutte le cifre sono inserite
      if (getOtpCode().length === digits.length) {
        verifyOtp();
      }
    });
    
    // Gestisci backspace
    digit.addEventListener('keydown', (e) => {
      if (e.key === 'Backspace' && !digit.value && index > 0) {
        digits[index - 1].focus();
      }
      if (e.key === 'ArrowLeft' && index > 0) {
        digits[index - 1].focus();
      }
      if (e.key === 'ArrowRight' && index < digits.length - 1) {
        digits[index + 1].focus();
      }
    });
    
    // Gestisci incolla
    digit.addEventListener('paste', (e) => {
      e.preventDefault();
      const pasted = (e.clipboardData || window.clipboardData).getData('text');
      const cleaned = pasted.replace(/[^0-9]/g, '').slice(0, digits.length);
      
      if (cleaned) {
        cleaned.split('').forEach((char, i) => {
          if (digits[i]) {
            digits[i].value = char;
            digits[i].classList.add('filled');
          }
        });
        
        // Focus sull'ultimo input riempito o sul primo vuoto
        const lastFilled = Math.min(cleaned.length, digits.length - 1);
        digits[lastFilled].focus();
        
        // Auto-submit se tutte le cifre sono state incollate
        if (cleaned.length === digits.length) {
          verifyOtp();
        }
      }
    });
  });
}

function getOtpCode() {
  const digits = document.querySelectorAll('.otp-digit');
  return Array.from(digits).map(d => d.value).join('');
}

function clearOtpInputs() {
  document.querySelectorAll('.otp-digit').forEach(d => {
    d.value = '';
    d.classList.remove('filled', 'error');
  });
}

async function verifyOtp() {
  const code = getOtpCode();
  if (code.length !== 6) return; // Non ancora completo
  
  showMessage('Verifica in corso...', 'success');
  try {
    const r = await fetch('/admin/api/verify-otp', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ otp: code })
    });
    if (r.ok) {
      window.location.href = '/admin/dashboard';
    } else {
      showMessage('Codice non valido o scaduto', 'error');
      // Evidenzia gli input in rosso
      document.querySelectorAll('.otp-digit').forEach(d => d.classList.add('error'));
      // Pulisci e rifocalizza dopo 1 secondo
      setTimeout(() => {
        clearOtpInputs();
        document.getElementById('otp-1').focus();
      }, 1000);
    }
  } catch (e) {
    showMessage('Errore di rete: ' + e.message, 'error');
  }
}

function backToRequest() {
  document.getElementById('step-request').style.display = 'block';
  document.getElementById('step-verify').style.display = 'none';
  messageEl.className = 'otp-message';
  clearOtpInputs();
}

// Inizializza al caricamento
document.addEventListener('DOMContentLoaded', () => {
  setupOtpInputs();
});