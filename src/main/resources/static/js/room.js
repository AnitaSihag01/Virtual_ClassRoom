(async () => {
  const user = await me();
  if (user.mustChangePassword) { location.href = '/change-password.html'; return; }
  if (user.role === 'ADMIN') { location.href = '/error/403.html'; return; }
  const q = new URLSearchParams(location.search);
  const sid = +q.get('session'), title = q.get('title') || 'Live class';
  if (!sid) { location.href = homeFor(user.role); return; }
  const isTeacher = user.role === 'TEACHER';
  document.title = title + ' · Live class';

  topbar(user, `<span class="room-title">${esc(title)}</span>
    <span class="status wait" id="status"><span class="dot"></span><span id="statusText">Connecting…</span></span>
    ${isTeacher ? `<button class="btn sm danger" id="endBtn">${icon('stop', 14)} End class</button>` : `<button class="btn sm" id="leaveBtn">${icon('back', 14)} Leave</button>`}`);

  /* ---------- state ---------- */
  const S = { drawerId: null, muted: false, mutedIds: [], people: [], ended: false, live: false };
  const cv = $('#board'), ctx = setupCanvas(cv);
  const COLORS = ['#111827', '#ef4444', '#f59e0b', '#10b981', '#3b82f6', '#8b5cf6', '#ec4899'];
  let color = COLORS[0], size = 4, eraser = false;
  const brush = () => Math.min(50, eraser ? size * 3 : size);
  const canDraw = () => S.live && (isTeacher || S.drawerId === user.id);

  /* ---------- toolbar ---------- */
  $('#penTool').innerHTML = icon('pen'); $('#eraserTool').innerHTML = icon('eraser'); $('#sendBtn').innerHTML = icon('send', 18);
  $('#swatches').innerHTML = COLORS.map((c, i) => `<button class="swatch ${i ? '' : 'active'}" style="background:${c}" data-c="${c}" title="${c}"></button>`).join('');
  $('#swatches').onclick = e => { const b = e.target.closest('.swatch'); if (!b) return; color = b.dataset.c; eraser = false; syncTools(); };
  $('#penTool').onclick = () => { eraser = false; syncTools(); };
  $('#eraserTool').onclick = () => { eraser = true; syncTools(); };
  $('#size').oninput = e => { size = +e.target.value; $('#sizeVal').textContent = size; };
  function syncTools() {
    $('#penTool').classList.toggle('active', !eraser); $('#eraserTool').classList.toggle('active', eraser);
    $$('.swatch').forEach(s => s.classList.toggle('active', !eraser && s.dataset.c === color));
  }
  if (isTeacher) {
    $('#teacherTools').innerHTML = `<button class="btn sm danger" id="clearBoardBtn">${icon('trash', 14)} Clear board</button>
      <a class="btn sm" href="/api/teacher/sessions/${sid}/whiteboard.png" title="Download board as image">${icon('download', 14)} PNG</a>
      <a class="btn sm" href="/api/teacher/sessions/${sid}/whiteboard.pdf" title="Download board as PDF">${icon('download', 14)} PDF</a>`;
    $('#clearBoardBtn').onclick = async () => {
      if (await confirmBox('This clears the board for everyone in the room.', 'Clear board', true, 'Clear the whiteboard?')) stomp && stomp.send(`/app/room/${sid}/draw`, { action: 'clear' });
    };
    $('#chatTools').innerHTML = `<button class="btn sm ghost" id="clearChatBtn">Clear chat</button>`;
    $('#clearChatBtn').onclick = () => api(`/api/teacher/sessions/${sid}/chat`, { method: 'DELETE' }).catch(() => {});
  }
  function updateTools() {
    const can = canDraw();
    $('#toolbar').classList.toggle('locked', !can);
    cv.classList.toggle('can-draw', can);
    const note = $('#lockNote');
    if (isTeacher) note.innerHTML = '';
    else note.innerHTML = can ? `${badge('You can draw', 'ok')}` : `${icon('shield', 15)} View only — the teacher can give you drawing permission`;
  }

  /* ---------- drawing ---------- */
  let drawing = false, buf = [], sentLast = null, lastPt = null, strokeId = '', timer = null;
  const pos = e => { const r = cv.getBoundingClientRect(); return [Math.round((e.clientX - r.left) * W / r.width * 10) / 10, Math.round((e.clientY - r.top) * H / r.height * 10) / 10]; };
  function localDraw(pts) { drawStroke(ctx, { points: lastPt ? [lastPt, ...pts] : pts, color, width: brush(), eraser }); lastPt = pts[pts.length - 1]; }
  function flush() {
    if (!buf.length || !stomp) return;
    const pts = sentLast ? [sentLast, ...buf] : [...buf];
    sentLast = buf[buf.length - 1]; buf = [];
    stomp.send(`/app/room/${sid}/draw`, { action: 'draw', strokeId, color, width: brush(), eraser, points: pts });
  }
  cv.addEventListener('pointerdown', e => {
    if (!canDraw() || e.button > 0) return;
    cv.setPointerCapture(e.pointerId); drawing = true;
    strokeId = user.id + '-' + Date.now().toString(36) + Math.random().toString(36).slice(2, 6);
    sentLast = null; lastPt = null;
    const p = pos(e); buf = [p]; localDraw([p]);
    timer = setInterval(flush, 80);
  });
  cv.addEventListener('pointermove', e => {
    if (!drawing) return;
    const p = pos(e); buf.push(p); localDraw([p]);
    if (buf.length >= 200) flush();
  });
  const stop = () => { if (!drawing) return; drawing = false; clearInterval(timer); flush(); sentLast = null; lastPt = null; };
  cv.addEventListener('pointerup', stop); cv.addEventListener('pointercancel', stop);

  /* ---------- chat ---------- */
  const msgs = $('#msgs'), input = $('#chatInput');
  const addNode = n => { msgs.appendChild(n); msgs.scrollTop = msgs.scrollHeight; };
  function updateChat() {
    input.disabled = S.muted || !S.live;
    input.placeholder = S.muted ? 'You are muted by the teacher' : S.live ? 'Type a message…' : 'Connecting…';
    $('#sendBtn').disabled = input.disabled;
  }
  $('#composer').onsubmit = e => {
    e.preventDefault();
    const text = input.value.trim(); if (!text || !stomp || input.disabled) return;
    stomp.send(`/app/room/${sid}/chat`, { text }); input.value = '';
  };

  /* ---------- people ---------- */
  const peopleBox = $('#people');
  function renderPeople() {
    const list = [...S.people].sort((a, b) => (a.role === 'TEACHER' ? -1 : 1) - (b.role === 'TEACHER' ? -1 : 1) || a.name.localeCompare(b.name));
    $('#count').textContent = list.length;
    peopleBox.innerHTML = list.map(p => {
      const t = p.role === 'TEACHER', me_ = p.userId === user.id, drawer = S.drawerId === p.userId, muted = S.mutedIds.includes(p.userId);
      let acts = '';
      if (isTeacher && !t) acts = `<button class="icon-btn ${drawer ? 'on' : ''}" data-act="draw" data-id="${p.userId}" title="${drawer ? 'Take back drawing' : 'Allow drawing'}">${icon('pen', 16)}</button>
        <button class="icon-btn ${muted ? 'warn-on' : ''}" data-act="mute" data-id="${p.userId}" title="${muted ? 'Unmute in chat' : 'Mute in chat'}">${icon(muted ? 'mute' : 'volume', 16)}</button>
        <button class="icon-btn bad" data-act="kick" data-id="${p.userId}" title="Remove from session">${icon('x', 16)}</button>`;
      else if (!t) acts = (drawer ? `<span style="color:var(--accent2)" title="Can draw">${icon('pen', 16)}</span>` : '') + (muted ? `<span style="color:var(--warn);margin-left:6px" title="Muted">${icon('mute', 16)}</span>` : '');
      return `<div class="person"><span class="avatar" style="${t ? 'background:linear-gradient(135deg,#22d3ee,#6d5dfc)' : ''}">${esc(initials(p.name))}</span>
        <div class="nm">${esc(p.name)}${me_ ? ' (you)' : ''}<small>${t ? 'Teacher' : drawer ? 'Can draw' : muted ? 'Muted' : 'Student'}</small></div><div class="acts">${acts}</div></div>`;
    }).join('') || '<div class="muted small" style="padding:10px">Nobody here yet.</div>';
  }
  peopleBox.onclick = async e => {
    const b = e.target.closest('[data-act]'); if (!b) return;
    const id = +b.dataset.id, base = `/api/teacher/sessions/${sid}`;
    try {
      if (b.dataset.act === 'draw') await api(S.drawerId === id ? `${base}/drawing` : `${base}/drawing/${id}`, { method: S.drawerId === id ? 'DELETE' : 'PUT' });
      else if (b.dataset.act === 'mute') await api(`${base}/mute/${id}`, { method: S.mutedIds.includes(id) ? 'DELETE' : 'PUT' });
      else if (b.dataset.act === 'kick') {
        const p = S.people.find(x => x.userId === id);
        if (await confirmBox(`${p ? p.name : 'This student'} will be removed and cannot rejoin this session.`, 'Remove', true, 'Remove student?')) await api(`${base}/students/${id}`, { method: 'DELETE' });
      }
    } catch (err) {}
  };

  /* ---------- live messages ---------- */
  function onDraw(m) {
    if (m.action === 'clear') { clearBoard(ctx); return; }
    if (m.senderId === user.id) return;           // already drawn locally
    drawStroke(ctx, m);
  }
  function onChat(m) {
    if (m.action === 'clear') { msgs.innerHTML = ''; addNode(sysEl('Chat was cleared by the teacher')); return; }
    addNode(msgEl(m, user.id));
  }
  function onControl(m) {
    if (m.type === 'drawing') {
      const was = S.drawerId; S.drawerId = m.userId ?? null;
      if (S.drawerId === user.id) toast('You can now draw on the board', 'success');
      else if (was === user.id) toast('Drawing permission was removed', 'warn');
      updateTools(); renderPeople();
    } else if (m.type === 'muted') {
      S.mutedIds = S.mutedIds.filter(x => x !== m.userId); if (m.muted) S.mutedIds.push(m.userId);
      if (m.userId === user.id) { S.muted = m.muted; toast(m.muted ? 'You were muted by the teacher' : 'You can chat again', m.muted ? 'warn' : 'success'); updateChat(); }
      renderPeople();
    } else if (m.type === 'kicked') { if (m.userId === user.id) endRoom('removed'); }
    else if (m.type === 'ended') endRoom('ended');
  }

  async function loadState() {
    const s = await api(`/api/common/sessions/${sid}/state`);
    if (!s.active) { endRoom('ended'); return false; }
    S.drawerId = s.drawerId; S.mutedIds = s.mutedIds; S.muted = s.youAreMuted; S.people = s.participants;
    clearBoard(ctx); s.strokes.forEach(st => drawStroke(ctx, st));
    msgs.innerHTML = ''; s.chat.forEach(m => addNode(msgEl(m, user.id)));
    renderPeople(); return true;
  }

  /* ---------- live video (WebRTC) ----------
     Teacher camera + mic go to every student. The video goes browser to browser,
     our server only passes the setup messages (hello / offer / answer / candidate). */
  const ICE = { iceServers: [{ urls: 'stun:stun.l.google.com:19302' }] };
  const videoEl = $('#liveVideo'), videoMsg = $('#videoMsg'), videoBar = $('#videoBar');
  let myStream = null;                 // teacher only: camera + mic
  const peers = {};                    // teacher only: studentId -> { pc, cid, waiting: [] }
  let teacherPc = null, teacherCid = '', waitingCands = [];   // student only

  const showVideoMsg = text => { videoMsg.textContent = text; videoMsg.classList.remove('hide'); };
  const hideVideoMsg = () => videoMsg.classList.add('hide');
  const sendSignal = (to, type, cid, data) => stomp && stomp.send(`/app/room/${sid}/signal`, { to, type, cid, data });
  const teacherId = () => { const t = S.people.find(p => p.role === 'TEACHER'); return t ? t.userId : null; };

  /* ----- teacher side ----- */
  async function startCamera() {
    if (!navigator.mediaDevices || !navigator.mediaDevices.getUserMedia) {
      showVideoMsg('Camera is blocked. Open this site on http://localhost or on https.');
      return;
    }
    try {
      myStream = await navigator.mediaDevices.getUserMedia({ video: { width: { ideal: 1280 }, height: { ideal: 720 } }, audio: true });
    } catch (e) {
      try { myStream = await navigator.mediaDevices.getUserMedia({ video: true }); }   // maybe there is no mic
      catch (e2) { showVideoMsg('Could not open the camera. Please allow camera access and refresh.'); return; }
    }
    videoEl.srcObject = myStream; videoEl.muted = true; videoEl.play().catch(() => {});
    hideVideoMsg();
    // students who are already connected: give them the tracks now
    Object.values(peers).forEach(p => addMyTracks(p.pc));
    drawVideoButtons();
  }
  function addMyTracks(pc) {
    if (!myStream) return;
    const have = pc.getSenders().map(s => s.track);
    myStream.getTracks().forEach(t => { if (!have.includes(t)) pc.addTrack(t, myStream); });
  }
  function drawVideoButtons() {
    const cam = myStream && myStream.getVideoTracks()[0], mic = myStream && myStream.getAudioTracks()[0];
    videoBar.innerHTML = `<button class="btn sm ${cam && cam.enabled ? '' : 'danger'}" id="camBtn" ${cam ? '' : 'disabled'}>Camera ${cam && cam.enabled ? 'on' : 'off'}</button>
      <button class="btn sm ${mic && mic.enabled ? '' : 'danger'}" id="micBtn" ${mic ? '' : 'disabled'}>Mic ${mic && mic.enabled ? 'on' : 'off'}</button>
      ${myStream ? '' : '<button class="btn sm primary" id="startCamBtn">Start camera</button>'}`;
    const c = $('#camBtn'), m = $('#micBtn'), st = $('#startCamBtn');
    if (c) c.onclick = () => { cam.enabled = !cam.enabled; drawVideoButtons(); };
    if (m) m.onclick = () => { mic.enabled = !mic.enabled; drawVideoButtons(); };
    if (st) st.onclick = startCamera;
  }
  function callStudent(studentId) {
    if (peers[studentId]) peers[studentId].pc.close();
    const cid = Math.random().toString(36).slice(2, 10);
    const pc = new RTCPeerConnection(ICE);
    peers[studentId] = { pc, cid, waiting: [] };
    pc.onicecandidate = e => { if (e.candidate) sendSignal(studentId, 'candidate', cid, e.candidate.toJSON()); };
    // runs when we add camera/mic tracks, then we make the offer
    pc.onnegotiationneeded = async () => {
      try {
        await pc.setLocalDescription();
        sendSignal(studentId, 'offer', cid, { type: pc.localDescription.type, sdp: pc.localDescription.sdp });
      } catch (e) {}
    };
    addMyTracks(pc);
  }
  async function teacherGotSignal(m) {
    if (m.type === 'hello') { callStudent(m.from); return; }
    const p = peers[m.from];
    if (!p || p.cid !== m.cid) return;
    try {
      if (m.type === 'answer') {
        await p.pc.setRemoteDescription(m.data);
        for (const c of p.waiting) await p.pc.addIceCandidate(c);
        p.waiting = [];
      } else if (m.type === 'candidate') {
        if (p.pc.remoteDescription) await p.pc.addIceCandidate(m.data); else p.waiting.push(m.data);
      }
    } catch (e) {}
  }
  function closeLeftStudents() {
    if (!isTeacher) return;
    const here = S.people.map(p => p.userId);
    Object.keys(peers).forEach(id => { if (!here.includes(+id)) { peers[id].pc.close(); delete peers[id]; } });
  }

  /* ----- student side ----- */
  function studentGotOffer(m) {
    if (!teacherPc || teacherCid !== m.cid) {          // new call from the teacher
      if (teacherPc) teacherPc.close();
      teacherCid = m.cid; waitingCands = [];
      teacherPc = new RTCPeerConnection(ICE);
      const pc = teacherPc;
      pc.onicecandidate = e => { if (e.candidate) sendSignal(m.from, 'candidate', m.cid, e.candidate.toJSON()); };
      pc.ontrack = e => {
        if (videoEl.srcObject !== e.streams[0]) videoEl.srcObject = e.streams[0];
        videoEl.play().catch(() => {});
      };
      pc.onconnectionstatechange = () => {
        if (pc !== teacherPc) return;
        if (pc.connectionState === 'failed' || pc.connectionState === 'disconnected') showVideoMsg('Video connection lost. Trying again…');
      };
    }
    const pc = teacherPc;
    (async () => {
      try {
        await pc.setRemoteDescription(m.data);
        for (const c of waitingCands) await pc.addIceCandidate(c);
        waitingCands = [];
        await pc.setLocalDescription();
        sendSignal(m.from, 'answer', m.cid, { type: pc.localDescription.type, sdp: pc.localDescription.sdp });
      } catch (e) {}
    })();
  }
  async function studentGotSignal(m) {
    if (m.type === 'ready') { sendHello(); return; }
    if (m.type === 'offer') { studentGotOffer(m); return; }
    if (m.type === 'candidate' && teacherPc && teacherCid === m.cid) {
      try { if (teacherPc.remoteDescription) await teacherPc.addIceCandidate(m.data); else waitingCands.push(m.data); } catch (e) {}
    }
  }
  function sendHello() { const t = teacherId(); if (t) sendSignal(t, 'hello', ''); }
  function drawStudentVideoButtons() {
    videoBar.innerHTML = `<button class="btn sm" id="unmuteBtn">Unmute teacher</button>`;
    $('#unmuteBtn').onclick = () => {
      videoEl.muted = !videoEl.muted; videoEl.play().catch(() => {});
      $('#unmuteBtn').textContent = videoEl.muted ? 'Unmute teacher' : 'Mute teacher';
    };
  }

  /* ----- both ----- */
  function onSignal(m) { if (m.sessionId !== sid) return; (isTeacher ? teacherGotSignal : studentGotSignal)(m); }
  videoEl.addEventListener('playing', hideVideoMsg);
  function videoOnConnect() { if (isTeacher) sendSignal(null, 'ready', ''); else sendHello(); }
  function stopVideo() {
    Object.values(peers).forEach(p => p.pc.close());
    if (teacherPc) teacherPc.close();
    if (myStream) myStream.getTracks().forEach(t => t.stop());
    videoEl.srcObject = null;
  }
  if (isTeacher) { drawVideoButtons(); startCamera(); }
  else { drawStudentVideoButtons(); }

  /* ---------- connection ---------- */
  const wsUrl = (location.protocol === 'https:' ? 'wss://' : 'ws://') + location.host + '/ws';
  let stomp = null, retry = 0, errors = 0;
  function setStatus(kind, text) {
    const el = $('#status'); el.className = 'status ' + kind; $('#statusText').textContent = text;
  }
  function subscribeAll(s) {
    const t = n => `/topic/room.${sid}.${n}`;
    s.subscribe(t('draw'), onDraw); s.subscribe(t('chat'), onChat);
    s.subscribe(t('presence'), m => { S.people = m.participants; renderPeople(); closeLeftStudents(); });
    s.subscribe(t('control'), onControl);
    s.subscribe('/user/queue/signal', onSignal);
    s.subscribe('/user/queue/errors', m => toast(m.message, 'error'));
    s.send(`/app/room/${sid}/join`);
  }
  function connect() {
    setStatus('wait', retry ? 'Reconnecting…' : 'Connecting…');
    const s = new StompLite(wsUrl, {
      onConnect: async () => {
        subscribeAll(s);
        setTimeout(() => { if (s.connected) errors = 0; }, 10000);
        try {
          if (await loadState()) {
            S.live = true; setStatus('live', 'Live'); updateTools(); updateChat();
            videoOnConnect();
            if (retry) toast('Reconnected', 'success'); retry = 0;
          }
        } catch (e) {}
      },
      onError: () => { errors++; if (errors >= 2) endRoom('removed'); },
      onClose: () => {
        if (S.ended || s.manual) return;
        S.live = false; setStatus('wait', 'Reconnecting…'); updateTools(); updateChat(); scheduleReconnect();
      }
    });
    stomp = s; s.connect();
  }
  function scheduleReconnect() {
    retry++;
    setTimeout(async () => {
      if (S.ended) return;
      try {
        const st = await api(`/api/common/sessions/${sid}/state`, { silent: true, noRedirect: true });
        if (!st.active) { endRoom('ended'); return; }
      } catch (e) { if (e.status === 403) { endRoom('denied'); return; } scheduleReconnect(); return; }
      connect();
    }, Math.min(1000 * retry, 5000));
  }

  function endRoom(reason) {
    if (S.ended) return;
    S.ended = true; S.live = false;
    try { stomp && stomp.close(); } catch (e) {}
    stopVideo();
    setStatus('off', 'Ended'); updateTools(); updateChat();
    const info = {
      ended: isTeacher ? ['🏁', 'Class ended', 'The session has been closed for all students. The board and chat were saved for replay.']
                       : ['🏁', 'Class ended', 'The teacher ended this session. You can watch the replay from your dashboard.'],
      removed: ['🚪', 'Removed from session', 'The teacher removed you from this session, or it is no longer available.'],
      denied: ['🔒', 'Cannot join', 'This session is not available to you.']
    }[reason];
    const o = document.createElement('div'); o.className = 'overlay';
    o.innerHTML = `<div class="card"><div class="big">${info[0]}</div><h2>${info[1]}</h2><p class="muted">${info[2]}</p>
      <div class="btn-row"><a class="btn primary" href="${homeFor(user.role)}">Back to dashboard</a>
      ${reason === 'ended' ? `<a class="btn" href="/replay.html?session=${sid}&title=${encodeURIComponent(title)}">${icon('play', 12)} Watch replay</a>` : ''}</div></div>`;
    document.body.appendChild(o);
  }

  /* ---------- top bar buttons ---------- */
  if (isTeacher) $('#endBtn').onclick = async () => {
    if (!await confirmBox('The room closes for all students and the session is saved for replay.', 'End class', true, 'End this class?')) return;
    try { await api(`/api/teacher/sessions/${sid}/end`, { method: 'POST' }); endRoom('ended'); } catch (e) {}
  };
  else $('#leaveBtn').onclick = () => { S.ended = true; try { stomp.send(`/app/room/${sid}/leave`); stomp.close(); } catch (e) {} location.href = homeFor(user.role); };

  // Live traffic goes over WebSocket, which does not count as HTTP activity. Ping every 5 min so the
  // 15-minute idle logout (SYS-5) does not end the login of someone who is actively in a class.
  setInterval(() => api('/api/common/me', { silent: true, noRedirect: true }).catch(() => {}), 5 * 60 * 1000);

  updateTools(); updateChat(); renderPeople(); connect();
})();
