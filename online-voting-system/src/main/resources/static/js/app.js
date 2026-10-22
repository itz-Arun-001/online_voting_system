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
  if (role && r !== role) { location.href = '/vote.html'; return null; }
  return r;
}

function renderNav() {
  const nav = document.getElementById('nav');
  const role = sessionStorage.getItem('role');
  if (!nav || !role) return;
  nav.innerHTML = `<span class="who">${esc(sessionStorage.getItem('email'))}</span>`
    + (role === 'ADMIN' ? '<a href="admin.html">Admin</a>' : '')
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
