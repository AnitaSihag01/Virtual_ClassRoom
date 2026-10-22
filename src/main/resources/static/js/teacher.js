(async () => {
  const user = await guard('TEACHER'); topbar(user);
  $('#hello').textContent = 'Welcome, ' + user.name.split(' ')[0];
  const grid = $('#classes'); let classes = [], actives = [];

  async function load() {
    classes = await api('/api/teacher/classes');
    if (!classes.length) { grid.innerHTML = `<div style="grid-column:1/-1">${emptyBox('🎓', 'No classes assigned yet', 'Ask the admin to assign a class to you.')}</div>`; return; }
    actives = await Promise.all(classes.map(c => api(`/api/common/classes/${c.id}/active-session`, { silent: true }).catch(() => null)));
    grid.innerHTML = classes.map((c, i) => `<article class="card class-card hover" style="--h:${(c.id * 53) % 360}">
      <div class="top"><div><h3>${esc(c.name)}</h3><div class="muted small">${esc(c.subject)} · Semester ${esc(c.semester)}</div></div>
        ${actives[i] ? badge('LIVE', 'live') : badge('Offline')}</div>
      <div class="actions">
        <button class="btn primary" data-a="${actives[i] ? 'enter' : 'start'}" data-i="${i}">${icon('video', 16)} ${actives[i] ? 'Enter live room' : 'Start live class'}</button>
        <button class="btn" data-a="materials" data-i="${i}">${icon('file', 15)} Materials</button>
        <button class="btn" data-a="sessions" data-i="${i}">${icon('clock', 15)} Sessions</button>
      </div></article>`).join('');
  }

  grid.onclick = async e => {
    const b = e.target.closest('[data-a]'); if (!b) return;
    const i = +b.dataset.i, c = classes[i];
    try {
      if (b.dataset.a === 'start') { b.disabled = true; const s = await api(`/api/teacher/classes/${c.id}/sessions`, { method: 'POST' }); location.href = roomUrl(s.id, c.name); }
      else if (b.dataset.a === 'enter') location.href = roomUrl(actives[i].id, c.name);
      else if (b.dataset.a === 'materials') materials(c);
      else if (b.dataset.a === 'sessions') sessions(c);
    } catch (err) { b.disabled = false; }
  };

  /* ---------- materials ---------- */
  function materials(c) {
    const m = modal({ title: 'Materials · ' + c.name, html: spinner, wide: true });
    async function render() {
      const list = await api(`/api/common/classes/${c.id}/materials`);
      m.body.innerHTML = `<form class="inline" id="up" style="margin-bottom:20px">
          <input type="file" id="file" accept=".pdf,.ppt,.pptx,.png,.jpg,.jpeg" required>
          <button class="btn primary" type="submit">${icon('upload', 16)} Upload</button></form>
        <p class="muted small" style="margin:-8px 0 16px">PDF, PPT, PPTX, PNG or JPG · up to 20 MB</p>
        <div class="list">${list.length ? list.map(x => `<div class="list-item"><div class="grow"><b>${esc(x.fileName)}</b>
          <span class="cell-sub">${esc(x.uploadedBy)} · ${fmt(x.uploadedAt)}</span></div>
          <a class="btn sm" href="/api/common/materials/${x.id}/download">${icon('download', 14)} Download</a>
          <button class="btn sm danger" data-del="${x.id}">${icon('trash', 14)}</button></div>`).join('') : emptyBox('📄', 'No materials yet', 'Upload slides or notes for your students.')}</div>`;
      $('#up', m.body).onsubmit = async ev => {
        ev.preventDefault(); const f = $('#file', m.body).files[0];
        if (!f) return; if (f.size > 20 * 1024 * 1024) { toast('File is larger than 20 MB', 'error'); return; }
        const fd = new FormData(); fd.append('file', f);
        const btn = ev.submitter; btn.disabled = true;
        try { await api(`/api/teacher/classes/${c.id}/materials`, { method: 'POST', form: fd }); toast('Material uploaded', 'success'); render(); } catch (err) { btn.disabled = false; }
      };
      $$('[data-del]', m.body).forEach(d => d.onclick = async () => {
        if (!await confirmBox('This file will be removed for all students.', 'Delete', true, 'Delete material?')) return;
        try { await api('/api/teacher/materials/' + d.dataset.del, { method: 'DELETE' }); toast('Deleted', 'success'); render(); } catch (err) {}
      });
    }
    render();
  }

  /* ---------- sessions ---------- */
  function sessions(c) {
    const m = modal({ title: 'Sessions · ' + c.name, html: spinner, wide: true });
    (async () => {
      const list = await api(`/api/common/classes/${c.id}/sessions`);
      if (!list.length) { m.body.innerHTML = emptyBox('🎥', 'No sessions yet', 'Start a live class to create the first one.'); return; }
      m.body.innerHTML = table([
        { h: 'Started', f: s => fmt(s.startedAt) },
        { h: 'Length', f: s => s.active ? badge('LIVE', 'live') : duration(s.startedAt, s.endedAt) },
        { h: '', cls: 'num', f: s => `<div class="row-actions">
          <button class="btn sm" data-att="${s.id}">${icon('users', 14)} Attendance</button>
          <a class="btn sm" href="/replay.html?session=${s.id}&title=${encodeURIComponent(c.name)}">${icon('play', 12)} Replay</a>
          <a class="btn sm ghost" href="/api/teacher/sessions/${s.id}/whiteboard.png" title="Download board as image">${icon('download', 14)} PNG</a>
          <a class="btn sm ghost" href="/api/teacher/sessions/${s.id}/whiteboard.pdf" title="Download board as PDF">${icon('download', 14)} PDF</a></div>` }], list);
      $$('[data-att]', m.body).forEach(b => b.onclick = () => attendance(+b.dataset.att));
    })();
  }
  async function attendance(id) {
    const m = modal({ title: 'Attendance', html: spinner, wide: true });
    const rows = await api(`/api/teacher/sessions/${id}/attendance`);
    m.body.innerHTML = rows.length ? table([
      { h: 'Student', f: r => `<span class="cell-main">${esc(r.name)}</span>` },
      { h: 'Joined', f: r => fmt(r.joinedAt) },
      { h: 'Left', f: r => r.leftAt ? fmt(r.leftAt) : badge('In room', 'ok') },
      { h: 'Time', f: r => duration(r.joinedAt, r.leftAt) }], rows) : emptyBox('🙋', 'Nobody attended this session');
  }

  load();
})();
