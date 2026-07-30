function initContactForm() {
  const form = document.getElementById('contact-form');
  if (!form) return; // pagina senza il form di contatto

  const statusEl = document.getElementById('contact-status');
  const submitBtn = document.getElementById('contact-submit');

  function setStatus(text, kind) {
    statusEl.textContent = text;
    statusEl.className = 'contact-status' + (kind ? ' ' + kind : '');
  }

  form.addEventListener('submit', async (e) => {
    e.preventDefault();
    setStatus('Sending…', '');
    submitBtn.disabled = true;

    const payload = {
      name: form.name.value,
      email: form.email.value,
      message: form.message.value,
      consent: form.consent.checked,
      website: form.website.value // honeypot
    };

    try {
      const r = await fetch('/api/contact', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(payload)
      });
      const d = await r.json().catch(() => ({}));
      if (r.ok && d.success) {
        setStatus("Thanks! Your message has been sent — I'll get back to you soon.", 'success');
        form.reset();
      } else {
        setStatus(d.error || 'Something went wrong. Please try again later.', 'error');
      }
    } catch (err) {
      setStatus('Network error. Please try again later.', 'error');
    } finally {
      submitBtn.disabled = false;
    }
  });
}

document.addEventListener('DOMContentLoaded', initContactForm);
