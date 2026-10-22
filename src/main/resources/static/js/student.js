(async () => {
  const user = await guard('STUDENT'); topbar(user);
  $('#hello').textContent = 'Welcome, ' + user.name.split(' ')[0];
  const grid = $('#classes'); let classes = [], actives = [];

  async function load() {
    classes = await api('/api/student/classes');
    if (!classes.length) { grid.innerHTML = `<div style="grid-column:1/-1">${emptyBox('📚', 'You are not enrolled in any class yet', 'Ask the admin to enroll you.')}</div>`; return; }
    actives = await Promise.all(classes.map(c => api(`/api/common/classes/${c.id}/active-session`, { silent: true }).catch(() => null)));
    grid.innerHTML = classes.map((c, i) => `<article class="card class-card hover" style="--h:${(c.id * 53) % 360}">
      <div class="top"><div><h3>${esc(c.name)}</h3><div class="muted small">${esc(c.subject)} · Semester ${esc(c.semester)}</div>
        <div class="muted small" style="margin-top:4px">${icon('user', 13)} ${esc(c.teacherName || 'No teacher yet')}</div></div>
        ${actives[i] ? badge('LIVE', 'live') : badge('Not live')}</div>
      <div class="actions">
        <button class="btn primary" data-a="join" data-i="${i}" ${actives[i] ? '' : 'disabled'}>${icon('video', 16)} ${actives[i] ? 'Join live class' : 'Waiting for teacher…'}</button>
        <button class="btn" data-a="materials" data-i="${i}">${icon('file', 15)} Materials</button>
        <button class="btn" data-a="sessions" data-i="${i}">${icon('clock', 15)} Past sessions</button>
      </div></article>`).join('');
  }

  grid.onclick = e => {
    const b = e.target.closest('[data-a]'); if (!b) return;
    const i = +b.dataset.i, c = classes[i];
    if (b.dataset.a === 'join') location.href = roomUrl(actives[i].id, c.name);
    else if (b.dataset.a === 'materials') materials(c);
    else if (b.dataset.a === 'sessions') sessions(c);
  };
  setInterval(() => { if (!document.querySelector('.modal-backdrop')) load().catch(() => {}); }, 15000);   // pick up newly started sessions

  function materials(c) {
    const m = modal({ title: 'Materials · ' + c.name, html: spinner, wide: true });
    (async () => {
      const list = await api(`/api/common/classes/${c.id}/materials`);
      m.body.innerHTML = list.length ? `<div class="list">${list.map(x => `<div class="list-item"><div class="grow"><b>${esc(x.fileName)}</b>
        <span class="cell-sub">${esc(x.uploadedBy)} · ${fmt(x.uploadedAt)}</span></div>
        <a class="btn sm primary" href="/api/common/materials/${x.id}/download">${icon('download', 14)} Download</a></div>`).join('')}</div>`
        : emptyBox('📄', 'No materials yet', 'Your teacher has not uploaded anything.');
    })();
  }

  function sessions(c) {
    const m = modal({ title: 'Past sessions · ' + c.name, html: spinner, wide: true });
    (async () => {
      const [list, att] = await Promise.all([api(`/api/common/classes/${c.id}/sessions`), api(`/api/student/attendance?classId=${c.id}`)]);
      const past = list.filter(s => !s.active);
      if (!past.length) { m.body.innerHTML = emptyBox('🎥', 'No past sessions yet', 'Recordings appear here after a class ends.'); return; }
      const mine = id => { const r = att.filter(a => a.sessionId === id); return r.length ? { j: r[0].joinedAt, l: r[r.length - 1].leftAt } : null; };
      m.body.innerHTML = table([
        { h: 'Date', f: s => `<div class="cell-main">${fmt(s.startedAt)}</div><div class="cell-sub">${duration(s.startedAt, s.endedAt)}</div>` },
        { h: 'Your attendance', f: s => { const a = mine(s.id); return a ? `${badge('Present', 'ok')} <span class="cell-sub">${fmt(a.j)} → ${a.l ? fmt(a.l) : '—'}</span>` : badge('Absent', 'danger'); } },
        { h: '', cls: 'num', f: s => `<a class="btn sm primary" href="/replay.html?session=${s.id}&title=${encodeURIComponent(c.name)}">${icon('play', 12)} Replay</a>` }], past);
    })();
  }

  load();
})();
