// ── Cursor glow effect ───────────────────────────────────────────────────────
const cursor = document.createElement('div');
cursor.style.cssText = `
  position: fixed; pointer-events: none; z-index: 9999;
  width: 320px; height: 320px;
  background: radial-gradient(circle, rgba(0,255,163,0.04) 0%, transparent 70%);
  border-radius: 50%;
  transform: translate(-50%, -50%);
  transition: opacity 0.3s;
`;
document.body.appendChild(cursor);

document.addEventListener('mousemove', e => {
  cursor.style.left = e.clientX + 'px';
  cursor.style.top  = e.clientY + 'px';
});

// ── Intersection observer for scroll reveals ──────────────────────────────────
const revealEls = document.querySelectorAll(
  '.project-card, .post-row, .skill-tag'
);

const observer = new IntersectionObserver(entries => {
  entries.forEach((entry, i) => {
    if (entry.isIntersecting) {
      setTimeout(() => {
        entry.target.style.opacity    = '1';
        entry.target.style.transform  = 'translateY(0)';
      }, i * 40);
      observer.unobserve(entry.target);
    }
  });
}, { threshold: 0.1 });

revealEls.forEach(el => {
  el.style.opacity   = '0';
  el.style.transform = 'translateY(12px)';
  el.style.transition = 'opacity 0.5s ease, transform 0.5s ease';
  observer.observe(el);
});

// ── Active nav highlight ──────────────────────────────────────────────────────
const path = window.location.pathname;
document.querySelectorAll('.nav-link').forEach(link => {
  const href = link.getAttribute('href');
  if (href === path || (href !== '/' && path.startsWith(href))) {
    link.classList.add('active');
  }
});
