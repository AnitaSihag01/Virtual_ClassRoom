(async () => {
  const user = await guard('ADMIN'); topbar(user);
  const tabs = [['overview', 'Overview'], ['teachers', 'Teachers'], ['students', 'Students'], ['classes', 'Classes'], ['sessions', 'Sessions'], ['logins', 'Login history']];
  let current = tabs.some(t => t[0] === location.hash.slice(1)) ? location.hash.slice(1) : 'overview';
  const nav = $('#tabs'), view = $('#view'); let cache = {};
  const cacheRows = rows => { cache = {}; rows.forEach(r => cache[r.id] = r); return rows; };

  const renderTabs = () => nav.innerHTML = tabs.map(([k, l]) => `<button class="tab ${k === current ? 'active' : ''}" data-k="${k}">${l}</button>`).join('');
  nav.onclick = e => { const b = e.target.closest('.tab'); if (b) { current = b.dataset.k; location.hash = current; show(); } };
  async function show() { renderTabs(); view.innerHTML = spinner; try { await views[current](); } catch (e) { if (!e.status && !e.network) console.error(e); } }
  const statusBadge = u => u.active ? badge('Active', 'ok') : badge('Deactivated', 'danger');

  /* ---------- overview ---------- */
  async function overview() {
    const [d, sessions] = await Promise.all([api('/api/admin/dashboard'), api('/api/admin/sessions')]);
    const card = (label, n, ic) => `<div class="card stat"><div class="ico">${icon(ic, 22)}</div><div class="muted small">${label}</div><div class="num">${n}</div></div>`;
    view.innerHTML = `<div class="stats">${card('Teachers', d.teachers, 'user')}${card('Students', d.students, 'users')}${card('Classes', d.classes, 'book')}${card('Sessions', d.sessions, 'video')}</div>
      <div class="section-head"><h2>Recent sessions</h2></div>` +
      (sessions.length ? sessionTable(sessions.slice(0, 6)) : emptyBox('🎥', 'No sessions yet', 'Sessions appear here once a teacher goes live.'));
  }
  const sessionTable = rows => table([
    { h: 'Class', f: s => `<div class="cell-main">${esc(s.className)}</div>` },
    { h: 'Teacher', f: s => esc(s.teacherName || '—') },
    { h: 'Started', f: s => fmt(s.startedAt) },
    { h: 'Ended', f: s => s.active ? badge('LIVE', 'live') : fmt(s.endedAt) },
    { h: 'Length', f: s => s.active ? '—' : duration(s.startedAt, s.endedAt) }], rows);

  /* ---------- teachers / students ---------- */
  async function people(role) {
    const label = role === 'TEACHER' ? 'Teacher' : 'Student';
    const rows = cacheRows(await api('/api/admin/users?role=' + role));
    const cols = [
      { h: 'Name', f: u => `<div class="inline" style="gap:12px"><span class="avatar" style="flex:none">${esc(initials(u.name))}</span><div><div class="cell-main">${esc(u.name)}</div><div class="cell-sub">${esc(u.email)}</div></div></div>` }];
    if (role === 'STUDENT') cols.push({ h: 'Enrollment no.', f: u => esc(u.enrollmentNumber || '—') });
    cols.push({ h: 'Status', f: statusBadge }, { h: '', cls: 'num', f: u => `<div class="row-actions">
      <button class="btn sm ghost" data-a="editUser" data-id="${u.id}">${icon('edit', 15)} Edit</button>
      <button class="btn sm ghost" data-a="resetPw" data-id="${u.id}">${icon('key', 15)} Reset</button>
      <button class="btn sm ${u.active ? 'danger' : 'ok'}" data-a="toggleUser" data-id="${u.id}">${icon('power', 15)} ${u.active ? 'Deactivate' : 'Activate'}</button></div>` });
    view.innerHTML = `<div class="section-head"><div><h2>${label}s</h2><p class="muted small">${rows.length} account${rows.length === 1 ? '' : 's'}</p></div>
      <button class="btn primary" data-a="addUser" data-role="${role}">${icon('plus', 16)} Add ${label.toLowerCase()}</button></div>` +
      (rows.length ? table(cols, rows) : emptyBox(role === 'TEACHER' ? '🎓' : '📚', `No ${label.toLowerCase()}s yet`, `Click "Add ${label.toLowerCase()}" to create the first account.`));
  }

  const actions = {
    addUser(_, b) {
      const role = b.dataset.role, student = role === 'STUDENT';
      const fields = [{ name: 'name', label: 'Full name' }, { name: 'email', label: 'Email', type: 'email' }];
      if (student) fields.push({ name: 'enrollmentNumber', label: 'Enrollment number' });
      fields.push({ name: 'password', label: 'Temporary password (min 6)', type: 'password', min: 6 });
      formModal({ title: 'Add ' + (student ? 'student' : 'teacher'), fields, submitText: 'Create account',
        note: 'The user must change this temporary password at first login.',
        onSubmit: async v => { await api('/api/admin/' + (student ? 'students' : 'teachers'), { method: 'POST', body: v }); toast('Account created', 'success'); show(); } });
    },
    editUser(id) {
      const u = cache[id], student = u.role === 'STUDENT';
      const fields = [{ name: 'name', label: 'Full name', value: u.name }, { name: 'email', label: 'Email', type: 'email', value: u.email }];
      if (student) fields.push({ name: 'enrollmentNumber', label: 'Enrollment number', value: u.enrollmentNumber });
      formModal({ title: 'Edit ' + u.name, fields, onSubmit: async v => { await api('/api/admin/users/' + id, { method: 'PUT', body: v }); toast('Saved', 'success'); show(); } });
    },
    async toggleUser(id) {
      const u = cache[id];
      if (u.active && !await confirmBox(`${u.name} will no longer be able to log in or join any room.`, 'Deactivate', true, 'Deactivate account?')) return;
      await api(`/api/admin/users/${id}/${u.active ? 'deactivate' : 'activate'}`, { method: 'PATCH' });
      toast(u.active ? 'Account deactivated' : 'Account reactivated', 'success'); show();
    },
    resetPw(id) {
      formModal({ title: 'Reset password · ' + cache[id].name, submitText: 'Reset password',
        fields: [{ name: 'newPassword', label: 'New temporary password (min 6)', type: 'password', min: 6 }],
        note: 'The user will be asked to choose a new password at next login.',
        onSubmit: async v => { await api(`/api/admin/users/${id}/reset-password`, { method: 'POST', body: v }); toast('Password reset', 'success'); } });
    },

    /* classes */
    async addClass() { await classForm(); },
    async editClass(id) { await classForm(cache[id]); },
    async deleteClass(id) {
      if (!await confirmBox(`"${cache[id].name}" and all its sessions, recordings and materials will be permanently deleted.`, 'Delete class', true, 'Delete class?')) return;
      await api('/api/admin/classes/' + id, { method: 'DELETE' }); toast('Class deleted', 'success'); show();
    },
    async classStudents(id) { await manageStudents(id); }
  };
  view.onclick = async e => { const b = e.target.closest('[data-a]'); if (b && actions[b.dataset.a]) await actions[b.dataset.a](+b.dataset.id, b).catch(() => {}); };

  /* ---------- classes ---------- */
  async function classes() {
    const rows = cacheRows(await api('/api/admin/classes'));
    view.innerHTML = `<div class="section-head"><div><h2>Classes</h2><p class="muted small">${rows.length} class${rows.length === 1 ? '' : 'es'}</p></div>
      <button class="btn primary" data-a="addClass">${icon('plus', 16)} New class</button></div>` +
      (rows.length ? table([
        { h: 'Class', f: c => `<div class="cell-main">${esc(c.name)}</div><div class="cell-sub">${esc(c.subject)} · Semester ${esc(c.semester)}</div>` },
        { h: 'Teacher', f: c => c.teacherName ? esc(c.teacherName) : badge('Unassigned', 'warn') },
        { h: '', cls: 'num', f: c => `<div class="row-actions">
          <button class="btn sm" data-a="classStudents" data-id="${c.id}">${icon('users', 15)} Students</button>
          <button class="btn sm ghost" data-a="editClass" data-id="${c.id}">${icon('edit', 15)} Edit</button>
          <button class="btn sm danger" data-a="deleteClass" data-id="${c.id}">${icon('trash', 15)}</button></div>` }], rows)
        : emptyBox('📖', 'No classes yet', 'Create a class, then assign a teacher and enroll students.'));
  }
  async function classForm(c) {
    const teachers = (await api('/api/admin/users?role=TEACHER')).filter(t => t.active || (c && c.teacherId === t.id));
    if (!teachers.length) { toast('Create a teacher account first', 'warn'); return; }
    formModal({ title: c ? 'Edit class' : 'New class', submitText: c ? 'Save changes' : 'Create class', fields: [
      { name: 'name', label: 'Class name', value: c?.name, placeholder: 'e.g. Data Structures – A' },
      { name: 'subject', label: 'Subject', value: c?.subject },
      { name: 'semester', label: 'Semester', value: c?.semester },
      { name: 'teacherId', label: 'Teacher', type: 'select', value: c?.teacherId, options: teachers.map(t => ({ value: t.id, label: t.name })) }],
      onSubmit: async v => {
        v.teacherId = +v.teacherId;
        await api(c ? '/api/admin/classes/' + c.id : '/api/admin/classes', { method: c ? 'PUT' : 'POST', body: v });
        toast(c ? 'Class updated' : 'Class created', 'success'); show();
      } });
  }
  async function manageStudents(classId) {
    const cls = cache[classId];
    const m = modal({ title: 'Students · ' + cls.name, html: spinner, wide: true });
    async function load() {
      const [enrolled, all] = await Promise.all([api(`/api/admin/classes/${classId}/students`), api('/api/admin/users?role=STUDENT')]);
      const ids = new Set(enrolled.map(s => s.id)), avail = all.filter(s => s.active && !ids.has(s.id));
      m.body.innerHTML = `<div class="inline" style="margin-bottom:18px">
          <select id="pick">${avail.length ? avail.map(s => `<option value="${s.id}">${esc(s.name)} · ${esc(s.enrollmentNumber || '')}</option>`).join('') : '<option value="">No more students available</option>'}</select>
          <button class="btn primary" id="enrollBtn" ${avail.length ? '' : 'disabled'}>${icon('plus', 16)} Enroll</button></div>
        <div class="muted small" style="margin-bottom:10px">${enrolled.length} enrolled</div>
        <div class="list">${enrolled.length ? enrolled.map(s => `<div class="list-item"><div class="grow"><b>${esc(s.name)}</b><span class="cell-sub">${esc(s.email)} · ${esc(s.enrollmentNumber || '')}</span></div>
          <button class="btn sm danger" data-rm="${s.id}">${icon('x', 14)} Remove</button></div>`).join('') : emptyBox('👥', 'No students enrolled', 'Pick a student above to enroll them.')}</div>`;
      $('#enrollBtn', m.body).onclick = async () => { const v = $('#pick', m.body).value; if (!v) return; await api(`/api/admin/classes/${classId}/students/${v}`, { method: 'POST' }); toast('Student enrolled', 'success'); load(); };
      $$('[data-rm]', m.body).forEach(b => b.onclick = async () => { await api(`/api/admin/classes/${classId}/students/${b.dataset.rm}`, { method: 'DELETE' }); toast('Removed from class', 'success'); load(); });
    }
    load();
  }

  /* ---------- sessions & logins ---------- */
  async function sessions() {
    const rows = await api('/api/admin/sessions');
    view.innerHTML = `<div class="section-head"><div><h2>All sessions</h2><p class="muted small">${rows.length} total</p></div></div>` +
      (rows.length ? sessionTable(rows) : emptyBox('🎥', 'No sessions yet'));
  }
  async function logins() {
    const rows = await api('/api/admin/login-history?limit=300');
    view.innerHTML = `<div class="section-head"><div><h2>Login history</h2><p class="muted small">Latest ${rows.length} attempts</p></div></div>` +
      (rows.length ? table([
        { h: 'Time', f: h => fmt(h.time) },
        { h: 'User', f: h => `<div class="cell-main">${esc(h.userName || 'Unknown')}</div><div class="cell-sub">${esc(h.email)}</div>` },
        { h: 'Result', f: h => h.success ? badge('Success', 'ok') : badge('Failed', 'danger') }], rows) : emptyBox('🔐', 'No logins recorded'));
  }

  const views = { overview, teachers: () => people('TEACHER'), students: () => people('STUDENT'), classes, sessions, logins };
  show();
})();
