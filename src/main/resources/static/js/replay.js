(async () => {
  const user = await me();
  if (user.mustChangePassword) { location.href = '/change-password.html'; return; }
  if (user.role === 'ADMIN') { location.href = '/error/403.html'; return; }
  const q = new URLSearchParams(location.search), sid = +q.get('session'), title = q.get('title') || 'Session';
  if (!sid) { location.href = homeFor(user.role); return; }
  document.title = title + ' · Replay';
  topbar(user, `<a class="btn sm ghost" href="${homeFor(user.role)}">${icon('back', 14)} Dashboard</a><span class="room-title">${esc(title)} · Replay</span>`);

  const ctx = setupCanvas($('#board')), msgs = $('#msgs'), seekEl = $('#seek'), playBtn = $('#play');
  const events = await api(`/api/common/sessions/${sid}/replay`);
  if (!events.length) {
    document.querySelector('.replay').innerHTML = `<div style="grid-column:1/-1">${emptyBox('🎞️', 'Nothing was recorded in this session', 'No strokes or messages were saved.')}</div>`;
    return;
  }
  const total = events[events.length - 1].offsetMs + 500;
  seekEl.max = total;
  let t = 0, idx = 0, playing = false, last = 0, speed = 1;

  const setIcon = () => playBtn.innerHTML = icon(playing ? 'pause' : 'play', 20);
  function ui() { seekEl.value = t; $('#time').textContent = fmtClock(t) + ' / ' + fmtClock(total); }
  function apply(e) {
    const d = e.data;
    if (e.type === 'STROKE') { if (d.action === 'clear') clearBoard(ctx); else drawStroke(ctx, d); }
    else if (d.action === 'clear') { msgs.innerHTML = ''; msgs.appendChild(sysEl('Chat was cleared')); }
    else { msgs.appendChild(msgEl(d, user.id)); msgs.scrollTop = msgs.scrollHeight; }
  }
  function seek(ms) {
    t = Math.max(0, Math.min(total, ms)); clearBoard(ctx); msgs.innerHTML = ''; idx = 0;
    while (idx < events.length && events[idx].offsetMs <= t) apply(events[idx++]);
    ui();
  }
  function tick(now) {
    if (!playing) return;
    t += (now - last) * speed; last = now;
    while (idx < events.length && events[idx].offsetMs <= t) apply(events[idx++]);
    if (t >= total) { t = total; playing = false; setIcon(); }
    ui(); if (playing) requestAnimationFrame(tick);
  }
  playBtn.onclick = () => {
    if (!playing && t >= total) seek(0);
    playing = !playing; setIcon();
    if (playing) { last = performance.now(); requestAnimationFrame(tick); }
  };
  seekEl.oninput = () => seek(+seekEl.value);
  $('#speed').onchange = e => speed = +e.target.value;
  document.addEventListener('keydown', e => { if (e.code === 'Space' && e.target === document.body) { e.preventDefault(); playBtn.click(); } });
  setIcon(); ui();
})();
