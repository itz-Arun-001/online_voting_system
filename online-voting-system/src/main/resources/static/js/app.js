/* Shared helpers for every page. */
const FONTS = '<link rel="preconnect" href="https://fonts.googleapis.com"><link rel="preconnect" href="https://fonts.gstatic.com" crossorigin><link rel="stylesheet" href="https://fonts.googleapis.com/css2?family=Public+Sans:wght@400;600&family=Source+Serif+4:wght@600&display=swap">';
document.head.insertAdjacentHTML('beforeend', FONTS);

const esc = (s) => String(s ?? '').replace(/[&<>"']/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
const fmt = (iso) => iso ? new Date(iso).toLocaleString([], { dateStyle: 'medium', timeStyle: 'short' }) : '';

async function api(path, opts = {}) {
  const headers = {};
  const token = sessionStorage.getItem('token');
  if (token) headers.Authorization = 'Bearer ' + token;
  let body;
  if (opts.body instanceof FormData) body = opts.body;
  else if (opts.body !== undefined) { headers['Content-Type'] = 'application/json'; body = JSON.stringify(opts.body); }
  const res = await fetch('/api' + path, { method: opts.method || 'GET', headers, body });
  let data = null;
  if ((res.headers.get('content-type') || '').includes('json')) data = await res.json().catch(() => null);
  if (res.status === 401 && token) {
    sessionStorage.clear();
    location.href = '/index.html';
  }
  if (!res.ok) {
    const err = new Error((data && (data.message || data.error)) || 'Request failed (' + res.status + ').');
    err.status = res.status;
    throw err;
  }
  return data;
}

async function download(path, filename) {
  const res = await fetch('/api' + path, { headers: { Authorization: 'Bearer ' + sessionStorage.getItem('token') } });
  if (!res.ok) throw new Error('Download failed.');
  const url = URL.createObjectURL(await res.blob());
  const a = Object.assign(document.createElement('a'), { href: url, download: filename });
  document.body.append(a); a.click(); a.remove();
  URL.revokeObjectURL(url);
}

function requireLogin(role) {
  const r = sessionStorage.getItem('role');
  if (!sessionStorage.getItem('token')) { location.href = '/index.html'; return null; }
  if (role === 'ADMIN' && r !== 'ADMIN' && r !== 'CO_ADMIN') { location.href = '/vote.html'; return null; }
  if (role && role !== 'ADMIN' && r !== role) { location.href = '/vote.html'; return null; }
  return r;
}

function renderNav() {
  const nav = document.getElementById('nav');
  const role = sessionStorage.getItem('role');
  if (!nav || !role) return;
  const isAdmin = role === 'ADMIN' || role === 'CO_ADMIN';
  nav.innerHTML = `<span class="who">${esc(sessionStorage.getItem('email'))}</span>`
    + (isAdmin ? '<a href="admin.html">Admin</a>' : '')
    + '<a href="vote.html">Elections</a><a href="#" id="signout">Sign out</a>';
  document.getElementById('signout').onclick = (e) => { e.preventDefault(); sessionStorage.clear(); location.href = '/index.html'; };
}

function statusTag(s) {
  const map = { ACTIVE: ['open', 'Open for voting'], UPCOMING: ['soon', 'Not started'], CLOSED: ['closed', 'Closed'] };
  const [cls, label] = map[s] || ['closed', s];
  return `<span class="tag ${cls}">${label}</span>`;
}

function notify(el, text, kind = 'ok') {
  el.innerHTML = text ? `<p class="msg ${kind}" role="${kind === 'err' ? 'alert' : 'status'}">${esc(text)}</p>` : '';
}

function resultRows(res) {
  const max = Math.max(1, ...res.candidates.map((c) => c.votes));
  const rows = res.candidates.map((c) => `<div class="result"><div>${esc(c.name)}</div>
    <div class="track"><div class="fill" style="width:${(c.votes / max) * 100}%"></div></div>
    <div class="count">${c.votes} ${c.votes === 1 ? 'vote' : 'votes'}</div></div>`).join('');
  return (rows || '<p class="empty">No candidates have been added.</p>') + `<p class="total">${res.totalVotes} ${res.totalVotes === 1 ? 'vote' : 'votes'} cast in total</p>`;
}

document.addEventListener('DOMContentLoaded', renderNav);

// ── Dark / Light mode ───────────────────────────────────────
function initTheme() {
  const saved = localStorage.getItem('theme') || 'light';
  document.documentElement.setAttribute('data-theme', saved);
  const btn = document.createElement('button');
  btn.className = 'theme-btn';
  btn.setAttribute('aria-label', 'Toggle dark/light mode');
  btn.textContent = saved === 'dark' ? '☀️ Light' : '🌙 Dark';
  btn.onclick = () => {
    const next = document.documentElement.getAttribute('data-theme') === 'dark' ? 'light' : 'dark';
    document.documentElement.setAttribute('data-theme', next);
    localStorage.setItem('theme', next);
    btn.textContent = next === 'dark' ? '☀️ Light' : '🌙 Dark';
  };
  document.body.appendChild(btn);
}
document.addEventListener('DOMContentLoaded', initTheme);

// ── Countdown timer helper ──────────────────────────────────
function startCountdown(targetIso, containerEl, onEnd) {
  function tick() {
    const diff = Math.max(0, new Date(targetIso) - Date.now());
    if (diff === 0) { onEnd && onEnd(); return; }
    const h = Math.floor(diff / 3600000);
    const m = Math.floor((diff % 3600000) / 60000);
    const s = Math.floor((diff % 60000) / 1000);
    containerEl.innerHTML = `<div class="countdown">
      ${h > 0 ? `<div class="unit"><span class="num">${String(h).padStart(2,'0')}</span><span class="lbl">hrs</span></div>` : ''}
      <div class="unit"><span class="num">${String(m).padStart(2,'0')}</span><span class="lbl">min</span></div>
      <div class="unit"><span class="num">${String(s).padStart(2,'0')}</span><span class="lbl">sec</span></div>
    </div>`;
    setTimeout(tick, 1000);
  }
  tick();
}

// ── Confetti ────────────────────────────────────────────────
function launchConfetti() {
  const canvas = document.createElement('canvas');
  canvas.style.cssText = 'position:fixed;top:0;left:0;width:100%;height:100%;pointer-events:none;z-index:9999';
  document.body.appendChild(canvas);
  const ctx = canvas.getContext('2d');
  canvas.width = window.innerWidth;
  canvas.height = window.innerHeight;
  const pieces = Array.from({length: 120}, () => ({
    x: Math.random() * canvas.width,
    y: -20,
    r: Math.random() * 8 + 4,
    d: Math.random() * 60 + 20,
    color: ['#1c3d9c','#d97706','#1d6b3a','#9c2323','#58a6ff','#3fb950'][Math.floor(Math.random()*6)],
    tilt: Math.random() * 10 - 10,
    tiltAngle: 0,
    tiltSpeed: Math.random() * 0.1 + 0.05,
  }));
  let frame = 0;
  function draw() {
    ctx.clearRect(0, 0, canvas.width, canvas.height);
    pieces.forEach(p => {
      p.tiltAngle += p.tiltSpeed;
      p.y += (Math.cos(frame / p.d) + 1.5);
      p.x += Math.sin(frame / 80) * 0.8;
      p.tilt = Math.sin(p.tiltAngle) * 12;
      ctx.beginPath();
      ctx.fillStyle = p.color;
      ctx.ellipse(p.x + p.tilt, p.y, p.r * 0.6, p.r, Math.PI / 4, 0, 2 * Math.PI);
      ctx.fill();
    });
    frame++;
    if (frame < 180) requestAnimationFrame(draw);
    else canvas.remove();
  }
  draw();
}
