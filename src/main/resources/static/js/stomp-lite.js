/* Tiny STOMP 1.2 client over a plain WebSocket (no external library needed, works offline on a LAN). */
class StompLite {
  constructor(url, handlers = {}) { this.url = url; this.h = handlers; this.subs = {}; this.n = 0; this.connected = false; this.manual = false; }
  connect() {
    this.ws = new WebSocket(this.url);
    this.ws.onopen = () => this._frame('CONNECT', { 'accept-version': '1.2', 'heart-beat': '0,0' });
    this.ws.onmessage = ev => this._onData(ev.data);
    this.ws.onclose = () => { const was = this.connected; this.connected = false; this.h.onClose && this.h.onClose(was); };
    this.ws.onerror = () => {};
  }
  _frame(cmd, headers = {}, body = '') {
    if (!this.ws || this.ws.readyState !== 1) return;
    let s = cmd + '\n';
    for (const k in headers) s += k + ':' + headers[k] + '\n';
    this.ws.send(s + '\n' + body + '\0');
  }
  _onData(data) {
    if (typeof data !== 'string') return;
    data.split('\0').forEach(raw => {
      raw = raw.replace(/^\n+/, '');
      if (!raw) return;
      const i = raw.indexOf('\n\n');
      const head = i < 0 ? raw : raw.slice(0, i), body = i < 0 ? '' : raw.slice(i + 2);
      const lines = head.split('\n'), cmd = lines.shift(), headers = {};
      lines.forEach(l => { const k = l.indexOf(':'); if (k > 0 && !(l.slice(0, k) in headers)) headers[l.slice(0, k)] = l.slice(k + 1); });
      this._handle(cmd, headers, body);
    });
  }
  _handle(cmd, h, body) {
    if (cmd === 'CONNECTED') { this.connected = true; this.h.onConnect && this.h.onConnect(); }
    else if (cmd === 'MESSAGE') {
      const cb = this.subs[h.subscription];
      if (cb) { let p = body; try { p = JSON.parse(body); } catch (e) {} cb(p, h); }
    } else if (cmd === 'ERROR') { this.h.onError && this.h.onError(h.message || body); }
  }
  subscribe(dest, cb) { const id = 'sub-' + (this.n++); this.subs[id] = cb; this._frame('SUBSCRIBE', { id, destination: dest }); return id; }
  send(dest, obj) {
    const body = obj === undefined ? '' : JSON.stringify(obj);
    this._frame('SEND', { destination: dest, 'content-type': 'application/json', 'content-length': new TextEncoder().encode(body).length }, body);
  }
  close() { this.manual = true; try { this._frame('DISCONNECT'); } catch (e) {} try { this.ws.close(); } catch (e) {} }
}
