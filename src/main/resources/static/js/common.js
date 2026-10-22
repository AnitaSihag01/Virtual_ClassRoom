/* Shared helpers: API, toasts, modals, forms, board drawing. */
const W = 1280, H = 720;   // whiteboard logical size (must match the server export)

const ICONS = {
  plus: '<path d="M12 5v14M5 12h14"/>',
  x: '<path d="M18 6L6 18M6 6l12 12"/>',
  trash: '<path d="M3 6h18M8 6V4h8v2M19 6l-1 14H6L5 6M10 11v6M14 11v6"/>',
  download: '<path d="M12 3v12M7 10l5 5 5-5M5 21h14"/>',
  upload: '<path d="M12 15V3M7 8l5-5 5 5M5 21h14"/>',
  send: '<path d="M22 2L11 13M22 2l-7 20-4-9-9-4z"/>',
  play: '<path fill="currentColor" stroke="none" d="M7 4l13 8-13 8z"/>',
  pause: '<path fill="currentColor" stroke="none" d="M6 4h4v16H6zM14 4h4v16h-4z"/>',
  pen: '<path d="M12 20h9M16.5 3.5a2.1 2.1 0 013 3L7 19l-4 1 1-4z"/>',
  eraser: '<path d="M20 20H9L3.5 14.5a2 2 0 010-2.8l8-8a2 2 0 012.8 0l6 6a2 2 0 010 2.8L13 20M7 11l6 6"/>',
  logout: '<path d="M9 21H5a2 2 0 01-2-2V5a2 2 0 012-2h4M16 17l5-5-5-5M21 12H9"/>',
  users: '<path d="M17 21v-2a4 4 0 00-4-4H5a4 4 0 00-4 4v2M9 11a4 4 0 100-8 4 4 0 000 8M23 21v-2a4 4 0 00-3-3.9M16 3.1a4 4 0 010 7.8"/>',
  user: '<path d="M20 21v-2a4 4 0 00-4-4H8a4 4 0 00-4 4v2M12 11a4 4 0 100-8 4 4 0 000 8"/>',
  file: '<path d="M14 2H6a2 2 0 00-2 2v16a2 2 0 002 2h12a2 2 0 002-2V8zM14 2v6h6"/>',
  book: '<path d="M4 19.5A2.5 2.5 0 016.5 17H20V3H6.5A2.5 2.5 0 004 5.5zM20 17v4H6.5A2.5 2.5 0 014 18.5"/>',
  mute: '<path d="M11 5L6 9H2v6h4l5 4zM23 9l-6 6M17 9l6 6"/>',
  volume: '<path d="M11 5L6 9H2v6h4l5 4zM15.5 8.5a5 5 0 010 7M19 5a10 14 0 010 14"/>',
  video: '<path d="M23 7l-7 5 7 5zM3 5h11a2 2 0 012 2v10a2 2 0 01-2 2H3a2 2 0 01-2-2V7a2 2 0 012-2z"/>',
  clock: '<circle cx="12" cy="12" r="10"/><path d="M12 6v6l4 2"/>',
  eye: '<path d="M1 12s4-8 11-8 11 8 11 8-4 8-11 8S1 12 1 12z"/><circle cx="12" cy="12" r="3"/>',
  back: '<path d="M19 12H5M12 19l-7-7 7-7"/>',
  stop: '<rect x="5" y="5" width="14" height="14" rx="2"/>',
  edit: '<path d="M11 4H4a2 2 0 00-2 2v14a2 2 0 002 2h14a2 2 0 002-2v-7M18.5 2.5a2.1 2.1 0 013 3L12 15l-4 1 1-4z"/>',
  key: '<path d="M21 2l-2 2m-7.6 7.6a5.5 5.5 0 11-7.8 7.8 5.5 5.5 0 017.8-7.8zm0 0L15.5 7.5m0 0l3 3L22 7l-3-3m-3.5 3.5L19 4"/>',
  power: '<path d="M18.4 6.6a9 9 0 11-12.8 0M12 2v10"/>',
  school: '<path d="M22 10L12 4 2 10l10 6zM6 12v5c0 1.5 2.7 3 6 3s6-1.5 6-3v-5"/>',
  layers: '<path d="M12 2L2 7l10 5 10-5zM2 17l10 5 10-5M2 12l10 5 10-5"/>',
  shield: '<path d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z"/>',
  eyeoff: '<path d="M17.9 17.9A10.1 10.1 0 0112 20C5 20 1 12 1 12a18 18 0 015.1-5.9M9.9 4.2A9 9 0 0112 4c7 0 11 8 11 8a18 18 0 01-2.2 3.2M1 1l22 22"/>'
};
const icon = (n, s = 18) => `<svg width="${s}" height="${s}" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">${ICONS[n] || ''}</svg>`;

const $ = (s, r = document) => r.querySelector(s);
const $$ = (s, r = document) => [...r.querySelectorAll(s)];
const esc = s => String(s ?? '').replace(/[&<>"']/g, c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
const fmt = d => d ? new Date(d).toLocaleString([], { dateStyle: 'medium', timeStyle: 'short' }) : '—';
const fmtClock = ms => { const s = Math.max(0, Math.floor(ms / 1000)); return String(Math.floor(s / 60)).padStart(2, '0') + ':' + String(s % 60).padStart(2, '0'); };
const duration = (a, b) => { if (!a || !b) return '—'; const m = Math.round((new Date(b) - new Date(a)) / 60000); return m < 1 ? '<1 min' : m + ' min'; };
const initials = n => (n || '?').split(/\s+/).slice(0, 2).map(x => x[0]).join('').toUpperCase();
const homeFor = role => `/${String(role).toLowerCase()}/home.html`;
const roomUrl = (id, title) => `/room.html?session=${id}&title=${encodeURIComponent(title || 'Live class')}`;
const badge = (t, k = '') => `<span class="badge ${k}">${esc(t)}</span>`;
const spinner = '<div class="loading"><span class="spinner"></span></div>';
const emptyBox = (emoji, title, sub = '') => `<div class="empty"><div class="big">${emoji}</div><b style="color:var(--text)">${esc(title)}</b><div class="small" style="margin-top:4px">${esc(sub)}</div></div>`;

/* ---------- toast ---------- */
function toast(msg, type = 'info', ms = 3800) {
  let box = $('#toasts');
  if (!box) { box = document.createElement('div'); box.id = 'toasts'; document.body.appendChild(box); }
  const t = document.createElement('div');
  t.className = 'toast ' + type;
  t.textContent = msg;
  box.appendChild(t);
  setTimeout(() => { t.classList.add('out'); setTimeout(() => t.remove(), 260); }, ms);
}

/* ---------- API ---------- */
async function api(url, o = {}) {
  const opt = { method: o.method || 'GET', credentials: 'same-origin', headers: {} };
  if (o.body !== undefined) { opt.headers['Content-Type'] = 'application/json'; opt.body = JSON.stringify(o.body); }
  if (o.form) opt.body = o.form;
  let r;
  try { r = await fetch(url, opt); } catch (e) { if (!o.silent) toast('Cannot reach the server', 'error'); const er = new Error('network'); er.network = true; throw er; }
  if (r.status === 401 && !o.noRedirect) { location.href = '/'; throw new Error('401'); }
  if (r.status === 204) return null;
  const ct = r.headers.get('content-type') || '';
  const data = ct.includes('json') ? await r.json().catch(() => null) : null;
  if (!r.ok) {
    const msg = (data && data.message) || (r.status === 403 ? 'Access denied' : 'Request failed (' + r.status + ')');
    if (r.status === 403 && /temporary password/i.test(msg) && !location.pathname.includes('change-password')) {
      location.href = '/change-password.html'; throw new Error(msg);
    }
    if (!o.silent) toast(msg, 'error');
    const err = new Error(msg); err.status = r.status; throw err;
  }
  return data;
}
const me = () => api('/api/common/me');
async function guard(role) {
  const u = await me();
  if (u.mustChangePassword) { location.href = '/change-password.html'; throw new Error('pw'); }
  if (role && u.role !== role) { location.href = '/error/403.html'; throw new Error('role'); }
  return u;
}
async function logout() {
  try { await fetch('/api/auth/logout', { method: 'POST', credentials: 'same-origin' }); } catch (e) {}
  location.href = '/';
}

/* ---------- top bar ---------- */
function topbar(user, extra = '') {
  const el = $('#topbar');
  el.className = 'topbar';
  el.innerHTML = `
    <a class="brand" href="${user ? homeFor(user.role) : '/'}"><span class="logo">VC</span><span>Virtual Classroom</span></a>
    <div class="topbar-extra">${extra}</div>
    <div class="spacer"></div>
    ${user ? `<div class="user-chip"><span class="avatar">${esc(initials(user.name))}</span>
      <div class="who"><b>${esc(user.name)}</b><span>${esc(user.role)}</span></div>
      <button class="icon-btn" id="logoutBtn" title="Log out">${icon('logout')}</button></div>` : ''}`;
  const b = $('#logoutBtn'); if (b) b.onclick = logout;
}

/* ---------- modal / forms ---------- */
function modal({ title, html = '', wide = false }) {
  const wrap = document.createElement('div');
  wrap.className = 'modal-backdrop';
  wrap.innerHTML = `<div class="modal ${wide ? 'wide' : ''}" role="dialog"><div class="modal-head"><h3>${esc(title)}</h3>
    <button class="icon-btn" data-close>${icon('x')}</button></div><div class="modal-body">${html}</div></div>`;
  document.body.appendChild(wrap);
  const onKey = e => { if (e.key === 'Escape') close(); };
  const close = () => { wrap.classList.add('out'); setTimeout(() => wrap.remove(), 160); document.removeEventListener('keydown', onKey); };
  document.addEventListener('keydown', onKey);
  wrap.addEventListener('mousedown', e => { if (e.target === wrap || e.target.closest('[data-close]')) close(); });
  requestAnimationFrame(() => wrap.classList.add('in'));
  return { el: wrap, body: $('.modal-body', wrap), close };
}

function formModal({ title, fields, submitText = 'Save', onSubmit, note = '' }) {
  const rows = fields.map(f => {
    const id = 'f_' + f.name; let ctl;
    if (f.type === 'select')
      ctl = `<select id="${id}" name="${f.name}" required>${(f.options || []).map(o =>
        `<option value="${esc(o.value)}" ${String(o.value) === String(f.value ?? '') ? 'selected' : ''}>${esc(o.label)}</option>`).join('')}</select>`;
    else
      ctl = `<input id="${id}" name="${f.name}" type="${f.type || 'text'}" value="${esc(f.value ?? '')}" placeholder="${esc(f.placeholder || '')}"
        ${f.required === false ? '' : 'required'} ${f.min ? `minlength="${f.min}"` : ''} autocomplete="off">`;
    return `<label class="field"><span>${esc(f.label)}</span>${ctl}</label>`;
  }).join('');
  const m = modal({ title, html: `<form class="form">${rows}${note ? `<p class="muted small" style="margin:0">${note}</p>` : ''}
    <div class="form-actions"><button type="button" class="btn ghost" data-close>Cancel</button>
    <button class="btn primary" type="submit">${esc(submitText)}</button></div></form>` });
  const form = $('form', m.el);
  const first = form.querySelector('input,select'); if (first) first.focus();
  form.onsubmit = async e => {
    e.preventDefault();
    const btn = form.querySelector('[type=submit]'); btn.disabled = true;
    const values = {};
    fields.forEach(f => { const v = form.elements[f.name].value; values[f.name] = f.type === 'password' ? v : v.trim(); });
    try { await onSubmit(values); m.close(); } catch (err) { btn.disabled = false; }
  };
  return m;
}

function confirmBox(message, okText = 'Confirm', danger = false, title = 'Are you sure?') {
  return new Promise(res => {
    const m = modal({ title, html: `<p style="margin-top:0;color:#c9cfe3">${esc(message)}</p>
      <div class="form-actions"><button class="btn ghost" data-close>Cancel</button>
      <button class="btn ${danger ? 'danger' : 'primary'}" id="cbOk">${esc(okText)}</button></div>` });
    let done = false;
    $('#cbOk', m.el).onclick = () => { done = true; m.close(); res(true); };
    new MutationObserver((_, o) => { if (!document.body.contains(m.el)) { o.disconnect(); if (!done) res(false); } })
      .observe(document.body, { childList: true });
  });
}

/* ---------- table ---------- */
function table(cols, rows) {
  return `<div class="table-wrap"><table><thead><tr>${cols.map(c => `<th class="${c.cls || ''}">${c.h}</th>`).join('')}</tr></thead>
    <tbody>${rows.map(r => `<tr>${cols.map(c => `<td class="${c.cls || ''}">${c.f(r)}</td>`).join('')}</tr>`).join('')}</tbody></table></div>`;
}

/* ---------- whiteboard drawing (shared by room, replay) ---------- */
function clearBoard(ctx) { ctx.fillStyle = '#ffffff'; ctx.fillRect(0, 0, W, H); }
function drawStroke(ctx, s) {
  const p = s.points; if (!p || !p.length) return;
  ctx.lineCap = 'round'; ctx.lineJoin = 'round';
  ctx.strokeStyle = ctx.fillStyle = s.eraser ? '#ffffff' : (s.color || '#000000');
  ctx.lineWidth = s.width || 3;
  if (p.length === 1) { ctx.beginPath(); ctx.arc(p[0][0], p[0][1], (s.width || 3) / 2, 0, Math.PI * 2); ctx.fill(); return; }
  ctx.beginPath(); ctx.moveTo(p[0][0], p[0][1]);
  for (let i = 1; i < p.length; i++) ctx.lineTo(p[i][0], p[i][1]);
  ctx.stroke();
}
function setupCanvas(cv) { cv.width = W; cv.height = H; const ctx = cv.getContext('2d'); clearBoard(ctx); return ctx; }

/* ---------- chat bubbles (room + replay) ---------- */
function msgEl(m, meId) {
  const mine = m.senderId === meId, teacher = m.role === 'TEACHER';
  const d = document.createElement('div');
  d.className = 'msg' + (mine ? ' me' : '') + (teacher ? ' teacher' : '');
  const time = m.time ? new Date(m.time).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' }) : '';
  d.innerHTML = `<div class="meta"><b>${esc(mine ? 'You' : m.senderName)}</b>${teacher ? '<span class="badge accent" style="padding:1px 7px;font-size:10px">TEACHER</span>' : ''}<span>${time}</span></div>
    <div class="bubble">${esc(m.text)}</div>`;
  return d;
}
function sysEl(text) { const d = document.createElement('div'); d.className = 'sys'; d.textContent = text; return d; }
