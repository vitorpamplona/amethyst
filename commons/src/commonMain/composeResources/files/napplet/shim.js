// Injected window.napplet.* client shim. Namespaced to match the @napplet/shim SDK over the
// {type:"domain.action", id} envelope, so an applet built against that SDK runs unchanged here.
// This is loaded as an asset and injected into the applet document by NappletHostActivity.
(function(){
  if (window.__nappletShimInstalled) return; window.__nappletShimInstalled = true;

  // Web Storage polyfill. The applet runs in an `allow-scripts` (no `allow-same-origin`) iframe, so
  // its origin is opaque and reading `window.localStorage`/`sessionStorage` throws a SecurityError —
  // which aborts the bootstrap of essentially every bundler-built SPA (they read storage at init) and
  // leaves a blank page. We can't grant `allow-same-origin` (that would hand the applet the
  // napplet.local origin the native bridge trusts — a sandbox escape), so we shadow the throwing
  // native accessor with a synchronous in-memory Storage. It is per-launch (not persisted); durable
  // storage is available separately and asynchronously via `window.napplet.storage.*`. This inline
  // shim runs before the applet's deferred module script, so the polyfill is in place first.
  (function(){
    function makeStorage(){
      var data = Object.create(null);
      var methods = {
        getItem: function(k){ k = String(k); return Object.prototype.hasOwnProperty.call(data, k) ? data[k] : null; },
        setItem: function(k, v){ data[String(k)] = String(v); },
        removeItem: function(k){ delete data[String(k)]; },
        clear: function(){ data = Object.create(null); },
        key: function(i){ var ks = Object.keys(data); i = i >>> 0; return i < ks.length ? ks[i] : null; }
      };
      return new Proxy(methods, {
        get: function(t, p){
          if (p === 'length') return Object.keys(data).length;
          if (typeof p !== 'string' || p in t) return t[p];
          return Object.prototype.hasOwnProperty.call(data, p) ? data[p] : undefined;
        },
        set: function(t, p, v){ if (p in t) { t[p] = v; } else { data[String(p)] = String(v); } return true; },
        has: function(t, p){ return (p in t) || (p === 'length') || Object.prototype.hasOwnProperty.call(data, p); },
        deleteProperty: function(t, p){ delete data[p]; return true; },
        ownKeys: function(){ return Object.keys(data); },
        getOwnPropertyDescriptor: function(t, p){
          if (Object.prototype.hasOwnProperty.call(data, p)) return { value: data[p], writable: true, enumerable: true, configurable: true };
          return undefined;
        }
      });
    }
    function install(name){
      try { if (window[name]) return; } catch (_) { /* native getter threw — install the polyfill */ }
      try { Object.defineProperty(window, name, { value: makeStorage(), configurable: true, enumerable: true, writable: false }); } catch (_) {}
    }
    install('localStorage');
    install('sessionStorage');
  })();

  var seq = 0, pending = {}, subs = {}, actions = {}, identityHandlers = [];
  // Transport. A napplet/nSite runs inside the trusted shell's iframe and talks to the shell via
  // postMessage (the shell relays to the native bridge). A top-level page opened in the in-app browser
  // has no shell parent, so it talks to the origin-scoped native bridge object (__nappletBridge,
  // injected for the page) directly. __nappletDirectBridge selects that path.
  var DIRECT = false; try { DIRECT = !!window.__nappletDirectBridge; } catch (_) {}
  var recvWired = false;
  function rawSend(s){
    if (DIRECT) {
      var b = window.__nappletBridge; if (!b) return;
      // Wire the reply channel before the first send, so no reply can arrive before we listen.
      if (!recvWired) { recvWired = true; b.onmessage = function(e){ onIncoming(e.data); }; }
      b.postMessage(s);
    } else {
      parent.postMessage(s, '*');
    }
  }
  function send(env){ env.id = env.id || ('r' + (seq++)); rawSend(JSON.stringify(env)); return env.id; }
  function call(type, fields){
    return new Promise(function(resolve, reject){
      var env = { type: type }; if (fields) for (var k in fields) env[k] = fields[k];
      var id = send(env);
      pending[id] = { resolve: resolve, reject: reject };
    });
  }
  // Fire-and-forget (no .result awaited), used for subscribe/unsubscribe.
  function post(type, fields){ var env = { type: type }; if (fields) for (var k in fields) env[k] = fields[k]; send(env); }
  function onIncoming(raw){
    var msg; if (typeof raw === 'string') { try { msg = JSON.parse(raw); } catch (_) { return; } } else { msg = raw; }
    if (!msg) return;
    // Subscription pushes are keyed by subId, not a request id.
    if (msg.type === 'relay.event' || msg.type === 'relay.eose' || msg.type === 'relay.closed') {
      var sub = subs[msg.subId]; if (!sub) return;
      if (msg.type === 'relay.event') { if (sub.onEvent) sub.onEvent(msg.event); }
      else if (msg.type === 'relay.eose') { if (sub.onEose) sub.onEose(); }
      else { delete subs[msg.subId]; if (sub.onClosed) sub.onClosed(msg.reason); }
      return;
    }
    // keys.action push: the shell triggers a registered keyboard/command action.
    if (msg.type === 'keys.action') { var cb = actions[msg.actionId]; if (cb) cb(); return; }
    // IME ops (host keyboard -> focused page field). Only the direct-bridge browser installs the agent.
    if (msg.type && msg.type.indexOf('ime.') === 0) { if (window.__nappletImeHandle) window.__nappletImeHandle(msg); return; }
    // identity.changed push: the active user's key changed (account switch / connect / disconnect).
    if (msg.type === 'identity.changed') { identityHandlers.slice().forEach(function(h){ try { h(msg.pubkey); } catch (_) {} }); return; }
    if (!msg.id) return;
    var p = pending[msg.id]; if (!p) return; delete pending[msg.id];
    if (msg.ok) p.resolve(msg);
    else { var err = new Error(msg.reason || msg.operation || msg.error || 'napplet error'); err.napplet = msg; p.reject(err); }
  }
  // The shell-relayed path listens for window messages from the parent; the direct-bridge path wires
  // its receive channel in rawSend (above) the first time it posts.
  if (!DIRECT) {
    window.addEventListener('message', function(e){ if (e.source !== parent) return; onIncoming(e.data); });
  }
  function field(promise, name){ return promise.then(function(m){ return m[name]; }); }
  function normFilters(filters){ return Array.isArray(filters) ? { filters: filters } : { filter: filters || {} }; }
  function bytesToB64(bytes){ var u = bytes instanceof Uint8Array ? bytes : new Uint8Array(bytes); var s=''; for (var i=0;i<u.length;i++) s+=String.fromCharCode(u[i]); return btoa(s); }
  var napplet = {
    shell: {
      // supports() is synchronous in @napplet/shim; we expose a sync proxy backed by an async check.
      supports: function(domain, protocol){ return field(call('shell.supports', { domain: domain, protocol: protocol }), 'supported'); },
      ready: function(){ return Promise.resolve({}); },
      onReady: function(cb){ if (typeof cb === 'function') cb({}); return { close: function(){} }; },
      services: []
    },
    identity: {
      getPublicKey: function(){ return field(call('identity.getPublicKey'), 'pubkey'); },
      getProfile: function(){ return field(call('identity.getProfile'), 'profile'); },
      getRelays: function(){ return field(call('identity.getRelays'), 'relays'); },
      getFollows: function(){ return field(call('identity.getFollows'), 'pubkeys'); },
      getMutes: function(){ return field(call('identity.getMutes'), 'pubkeys'); },
      getBlocked: function(){ return field(call('identity.getBlocked'), 'pubkeys'); },
      getList: function(listType){ return field(call('identity.getList', { listType: listType }), 'entries'); },
      getZaps: function(){ return field(call('identity.getZaps'), 'zaps'); },
      getBadges: function(){ return field(call('identity.getBadges'), 'badges'); },
      // onChanged: the shell pushes identity.changed when the active user's key changes. The first
      // handler opens the watch (identity.watch); closing the last one stops it (identity.unwatch).
      onChanged: function(handler){
        if (typeof handler !== 'function') return { close: function(){} };
        identityHandlers.push(handler);
        if (identityHandlers.length === 1) post('identity.watch');
        return { close: function(){
          var i = identityHandlers.indexOf(handler); if (i >= 0) identityHandlers.splice(i, 1);
          if (identityHandlers.length === 0) post('identity.unwatch');
        } };
      }
    },
    // keys = keyboard / command action binding (NOT signing). The shell honors the action's defaultKey
    // and pushes keys.action when the bound combo is pressed, which fires the onAction callback.
    keys: {
      registerAction: function(action){ return call('keys.registerAction', { action: action }).then(function(m){ return { actionId: m.actionId, binding: m.binding }; }); },
      unregisterAction: function(actionId){ post('keys.unregisterAction', { actionId: actionId }); },
      onAction: function(actionId, cb){ actions[actionId] = cb; return { close: function(){ delete actions[actionId]; } }; }
    },
    relay: {
      // publish takes an UNSIGNED template (carried in the `event` field per @napplet/shim); the
      // shell signs it and resolves to the signed event.
      publish: function(template, options){ return field(call('relay.publish', { event: template, options: options }), 'event'); },
      publishEncrypted: function(template, recipient, encryption){ return field(call('relay.publishEncrypted', { event: template, recipient: recipient, encryption: encryption || 'nip44' }), 'event'); },
      query: function(filters){ return field(call('relay.query', normFilters(filters)), 'events'); },
      // subscribe is a live tail: the shell streams relay.event (stored + live), one relay.eose, and
      // relay.closed keyed by subId until close() sends relay.close.
      subscribe: function(filters, onEvent, onEose, options){
        var subId = 's' + (seq++);
        subs[subId] = { onEvent: onEvent, onEose: onEose };
        var env = normFilters(filters); env.subId = subId;
        post('relay.subscribe', env);
        return { close: function(){ delete subs[subId]; post('relay.close', { subId: subId }); } };
      }
    },
    storage: {
      // SDK method names are getItem/setItem/removeItem/keys; the wire types are storage.get/set/remove/keys.
      getItem: function(key){ return field(call('storage.get', { key: key }), 'value'); },
      setItem: function(key, value){ return call('storage.set', { key: key, value: value }).then(function(){}); },
      removeItem: function(key){ return call('storage.remove', { key: key }).then(function(){}); },
      keys: function(){ return field(call('storage.keys'), 'keys'); }
    },
    // value.payInvoice is an Amethyst-specific extension (not part of @napplet/shim).
    value: {
      payInvoice: function(invoice){ return field(call('value.payInvoice', { invoice: invoice }), 'preimage'); }
    },
    resource: {
      // The shell rebuilds the Blob from the host's base64 before this resolves.
      bytes: function(url){ return field(call('resource.bytes', { url: url }), 'blob'); },
      bytesAsObjectURL: function(url){ return field(call('resource.bytes', { url: url }), 'blob').then(function(blob){ return URL.createObjectURL(blob); }); }
    },
    upload: {
      // Sends the SDK's upload.upload; we inline the bytes as base64 (shell.html does the same for
      // a Blob from a stock napplet). Resolves to the uploaded URL.
      blob: function(bytes, contentType){ return field(call('upload.upload', { request: { dataBase64: bytesToB64(bytes), mimeType: contentType } }), 'url'); }
    }
  };
  window.napplet = Object.freeze(napplet);

  // ---- IME agent (in-app browser only) -------------------------------------------------------
  // The embedded browser surface renders cross-process via SurfaceControlViewHost, which forwards
  // touch but NOT the soft keyboard (the embedded window can't be an IME target). So the host shows
  // the keyboard in the main app window and relays editing here, where we apply it to the focused
  // field with real input/composition events. Installed on any EMBEDDED surface (browser via the direct
  // bridge, napplet/nSite via the shell relay); both transports go through send(). The full-screen
  // hosts set neither flag (they have a native keyboard).
  var IME_PROXY = false; try { IME_PROXY = !!window.__nappletImeProxy; } catch (_) {}
  if (IME_PROXY) (function(){
    var el = null;            // the focused editable element, or null
    var inComposition = false;
    function perfNow(){ try { return performance.now(); } catch (_) { return 0; } }

    function isEditable(n){
      if (!n) return false;
      if (n.isContentEditable) return true;
      var t = (n.tagName || '').toUpperCase();
      if (t === 'TEXTAREA') return true;
      if (t === 'INPUT') {
        var ty = (n.type || 'text').toLowerCase();
        return ['text','search','url','email','tel','password','number',''].indexOf(ty) >= 0;
      }
      return false;
    }
    function isCE(n){ return !!(n && n.isContentEditable); }
    function valOf(n){ return isCE(n) ? n.textContent : (n.value || ''); }
    // Controlled-input frameworks (React, Preact, …) install an INSTANCE-level `value` setter on the
    // <input>/<textarea> that records the last value they wrote, and then suppress their onChange whenever
    // the element's value already equals that recorded value. A plain `n.value = v` assignment goes through
    // that tracker, so our programmatic edit looks like a no-op to the framework: onChange never fires, its
    // state stays stale, and on the next render it reconciles the field straight back to the stale value —
    // wiping what we just typed. Writing through the NATIVE prototype setter sets the real value without
    // touching the tracker, so the framework's input handler sees value != tracked, detects the change, and
    // commits it. Identical to `n.value = v` for plain (non-framework) pages.
    var nativeInputValueSet = (function(){ try { return Object.getOwnPropertyDescriptor(window.HTMLInputElement.prototype, 'value').set; } catch (_) { return null; } })();
    var nativeAreaValueSet = (function(){ try { return Object.getOwnPropertyDescriptor(window.HTMLTextAreaElement.prototype, 'value').set; } catch (_) { return null; } })();
    function setVal(n, v){
      if (isCE(n)) { n.textContent = v; return; }
      var set = (typeof HTMLTextAreaElement !== 'undefined' && n instanceof HTMLTextAreaElement) ? nativeAreaValueSet : nativeInputValueSet;
      if (set) { try { set.call(n, v); return; } catch (_) {} }
      n.value = v;
    }

    // --- contenteditable selection/replacement, mapped through char offsets into textContent ---
    // We can't use setSelectionRange/value on a contenteditable root; instead we map a char offset
    // into root.textContent to a DOM (node, offset) position via Range, so we mutate in place and
    // preserve the surrounding element structure + caret rather than blowing away textContent.
    function ceTextNodes(root){
      var out = [], w = document.createTreeWalker(root, NodeFilter.SHOW_TEXT, null, false), n;
      while ((n = w.nextNode())) out.push(n);
      return out;
    }
    // Char offset within root.textContent for a DOM (container, nodeOffset) boundary.
    function ceOffsetOf(root, container, nodeOffset){
      try { var r = document.createRange(); r.setStart(root, 0); r.setEnd(container, nodeOffset); return r.toString().length; }
      catch (_) { return 0; }
    }
    // DOM {node, offset} position for a char offset into root.textContent.
    function cePoint(root, off){
      var nodes = ceTextNodes(root), acc = 0;
      for (var i = 0; i < nodes.length; i++){
        var len = nodes[i].data.length;
        if (off <= acc + len) return { node: nodes[i], offset: off - acc };
        acc += len;
      }
      if (nodes.length) { var last = nodes[nodes.length - 1]; return { node: last, offset: last.data.length }; }
      return { node: root, offset: 0 };
    }
    function ceSelOf(root){
      var s = window.getSelection();
      if (!s || s.rangeCount === 0) { var v = root.textContent.length; return [v, v]; }
      var r = s.getRangeAt(0);
      if (!root.contains(r.startContainer) || !root.contains(r.endContainer)) { var v2 = root.textContent.length; return [v2, v2]; }
      return [ceOffsetOf(root, r.startContainer, r.startOffset), ceOffsetOf(root, r.endContainer, r.endOffset)];
    }
    function ceSetSel(root, s, e){
      try {
        var a = cePoint(root, s), b = cePoint(root, e), r = document.createRange();
        r.setStart(a.node, a.offset); r.setEnd(b.node, b.offset);
        var sel = window.getSelection(); sel.removeAllRanges(); sel.addRange(r);
      } catch (_) {}
    }
    // Replace char range [from, to) in root with ins, leaving the rest of the DOM intact.
    function ceReplace(root, from, to, ins){
      try {
        var a = cePoint(root, from), b = cePoint(root, to), r = document.createRange();
        r.setStart(a.node, a.offset); r.setEnd(b.node, b.offset);
        r.deleteContents();
        if (ins) r.insertNode(document.createTextNode(ins));
      } catch (_) { root.textContent = root.textContent.slice(0, from) + (ins || '') + root.textContent.slice(to); }
    }

    function selOf(n){
      if (isCE(n)) return ceSelOf(n);
      return [n.selectionStart || 0, n.selectionEnd || 0];
    }
    function setSel(n, s, e){
      // No-op if already there: re-applying the same selection still fires `select`/`selectionchange`, and
      // every such redundant event ripples into a host report → geometry update → toolbar/handle recompose
      // (visible churn). Our re-asserts/re-applies frequently target the current range, so guard them here.
      var cur = selOf(n);
      if (cur[0] === s && cur[1] === e) return;
      lastSelActivityAt = perfNow(); // a real selection write → the field may auto-scroll to reveal it
      if (isCE(n)) ceSetSel(n, s, e);
      else { try { n.setSelectionRange(s, e); } catch (_) {} }
    }
    // The DOM exposes no caret/selection rect for a position inside an <input>/<textarea>, so we mirror the
    // field into a hidden div (same font/padding/wrapping) and measure where a marker span lands — the
    // well-known "textarea-caret-position" technique. Used to place the insertion handle (and drag it).
    var CARET_PROPS = ['direction','boxSizing','width','height','overflowX','overflowY','borderTopWidth','borderRightWidth','borderBottomWidth','borderLeftWidth','paddingTop','paddingRight','paddingBottom','paddingLeft','fontStyle','fontVariant','fontWeight','fontStretch','fontSize','fontSizeAdjust','lineHeight','fontFamily','textAlign','textTransform','textIndent','textDecoration','letterSpacing','wordSpacing','tabSize','MozTabSize'];
    function caretCoords(n, position){
      try {
        var isInput = (n.nodeName || '').toUpperCase() === 'INPUT';
        var computed = window.getComputedStyle(n);
        var div = document.createElement('div');
        var s = div.style;
        s.position = 'absolute'; s.visibility = 'hidden'; s.whiteSpace = isInput ? 'nowrap' : 'pre-wrap'; s.wordWrap = 'break-word'; s.overflow = 'hidden';
        for (var i = 0; i < CARET_PROPS.length; i++) { s[CARET_PROPS[i]] = computed[CARET_PROPS[i]]; }
        var val = valOf(n);
        div.textContent = val.substring(0, position);
        if (isInput) div.textContent = div.textContent.replace(/\s/g, ' ');
        var span = document.createElement('span');
        span.textContent = val.substring(position) || '.';
        div.appendChild(span);
        document.body.appendChild(div);
        var caretL = span.offsetLeft, caretT = span.offsetTop;
        var lineHeight = parseInt(computed.lineHeight) || parseInt(computed.fontSize) || 16;
        document.body.removeChild(div);
        // offsetLeft/Top are measured from the mirror's *inner* (padding) edge, but getBoundingClientRect is the
        // *outer* border box — so add the field's border widths to land on the real caret.
        var bl = parseFloat(computed.borderLeftWidth) || 0, bt = parseFloat(computed.borderTopWidth) || 0;
        var rect = n.getBoundingClientRect();
        var x = rect.left + bl + caretL - n.scrollLeft;
        var top = rect.top + bt + caretT - n.scrollTop;
        return { x: x, top: top, bottom: top + lineHeight };
      } catch (_) { return null; }
    }
    // Inverse: the char offset whose caret is nearest the CSS-px point (x,y). Binary search in reading order
    // (offset increases left-to-right, top-to-bottom), so a handle drag maps back to a cursor position. Y is
    // first clamped into the field's text rows, so dragging the handle (which sits below the line) or off the
    // field keeps the cursor on the nearest line and lets X drive the column — like Android.
    function offsetFromPoint(n, x, y){
      var len = valOf(n).length;
      var first = caretCoords(n, 0), last = caretCoords(n, len);
      if (first && y < first.top) y = (first.top + first.bottom) / 2;
      else if (last && y > last.bottom) y = (last.top + last.bottom) / 2;
      var lo = 0, hi = len;
      while (lo < hi) {
        var mid = (lo + hi) >> 1, c = caretCoords(n, mid);
        if (!c) break;
        if (y < c.top) hi = mid;
        else if (y > c.bottom) lo = mid + 1;
        else if (x < c.x) hi = mid;
        else lo = mid + 1;
      }
      // The search lands on the boundary just RIGHT of x; round to the NEAREST boundary instead (native
      // getOffsetForPosition) so tapping the left half of a glyph doesn't advance the caret past it. Only
      // compare within the same line (skip when lo sits at a wrap, where lo-1 is on the previous row).
      if (lo > 0) {
        var cl = caretCoords(n, lo - 1), cr = caretCoords(n, lo);
        if (cl && cr && cl.top === cr.top && (x - cl.x) < (cr.x - x)) lo = lo - 1;
      }
      return lo;
    }
    // The focused field's bounding box in CSS px (toolbar anchor). When the selection is a bare caret it also
    // carries the caret rect (cx/ct/cb) so the host can show a draggable insertion handle.
    function fieldGeom(n){
      try {
        var b = n.getBoundingClientRect();
        var g = { l: b.left, t: b.top, r: b.right, b: b.bottom, sx: b.left, sb: b.bottom, ex: b.right, eb: b.bottom, vw: window.innerWidth };
        if (!isCE(n)) {
          var sel = selOf(n);
          if (sel[0] === sel[1]) {
            // Bare caret → carry the caret rect so the host shows the insertion handle.
            var c = caretCoords(n, sel[0]);
            if (c) { g.cx = c.x; g.ct = c.top; g.cb = c.bottom; }
          } else {
            // Range → carry the start/end caret feet so the host shows draggable selection handles, and
            // tighten the box to the selected line span so the toolbar anchors above the selection.
            var cs = caretCoords(n, sel[0]), ce = caretCoords(n, sel[1]);
            if (cs && ce) {
              g.rng = true; // marks a real range so the host keeps these feet through Chrome's collapse fight
              g.sx = cs.x; g.sb = cs.bottom; g.ex = ce.x; g.eb = ce.bottom;
              g.t = Math.min(cs.top, ce.top); g.b = Math.max(cs.bottom, ce.bottom);
            }
          }
        }
        return g;
      } catch (_) { return null; }
    }
    function focusInfo(n){
      var t = (n.tagName || '').toUpperCase();
      var multiline = isCE(n) || t === 'TEXTAREA';
      var inputType = isCE(n) ? 'text' : (t === 'TEXTAREA' ? 'textarea' : (n.type || 'text').toLowerCase());
      var sel = selOf(n);
      return { type:'ime.focus', inputType: inputType, enterKeyHint: (n.enterKeyHint || ''),
               multiline: multiline, text: valOf(n), selStart: sel[0], selEnd: sel[1], geom: fieldGeom(n) };
    }
    // Last selection we either applied (applyState) or already reported, so the asynchronous
    // selectionchange our own setSel triggers doesn't echo back to the host as a fresh edit.
    var lastSel = null;
    // The live non-collapsed field range we protect from Chrome's off-window abandonment (see the
    // selectionchange handler). Tracked centrally so handle-extends and select-all keep it current.
    var lastFieldRange = null, lastFieldAt = -1;
    // When the selection last changed (Chrome-driven OR our own setSel). Forming/re-asserting a selection makes
    // the browser auto-scroll the field to reveal it, firing `scroll` events that are NOT a user content scroll.
    // The hide-on-scroll path uses this to ignore those: hiding the host overlays on a selection-reveal scroll
    // makes the handles/toolbar blink off-and-on every time a selection settles. See onAnyScroll.
    var lastSelActivityAt = -1;
    function sameSel(a, b){ return !!(a && b && a[0] === b[0] && a[1] === b[1]); }
    function noteSel(sel){
      lastSel = sel;
      if (el && !isCE(el)) {
        if (sel[0] !== sel[1]) { lastFieldRange = [sel[0], sel[1]]; lastFieldAt = perfNow(); }
        else { lastFieldRange = null; }
      }
    }
    function reportState(){
      if (!el) return;
      var sel = selOf(el);
      noteSel(sel);
      send({ type:'ime.state', text: valOf(el), selStart: sel[0], selEnd: sel[1], geom: fieldGeom(el) });
    }

    document.addEventListener('focusin', function(e){
      if (isEditable(e.target)) {
        el = e.target; inComposition = false; lastSel = selOf(el); send(focusInfo(el));
        // Focusing a field clears any page-text selection in the browser. The page selectionchange handler is
        // muted while a field is focused (el is set), so it never emits the `active:false` — emit it here, or
        // the host's page handles + Copy bar linger ABOVE the field overlays (and, being z-above, steal the
        // caret/selection-handle drag so the caret can't be moved).
        if (pageSelText) { lastPageRange = null; sendPageSel(false, null); }
        // Cancel any in-flight scroll-hide from the page phase so the new field overlays aren't suppressed by a
        // stale `scrolling` state (its settle would otherwise keep getting re-armed by the field reveal-scrolls).
        scrolling = false; if (scrollTimer) { clearTimeout(scrollTimer); scrollTimer = null; }
        // The host shrinks the surface for the keyboard, but also nudge the field into view in case IME
        // insets aren't delivered (some hosts) so it never sits behind the keyboard.
        try { el.scrollIntoView({ block: 'center', inline: 'nearest' }); } catch (_) {}
      } else if (el) { el = null; inComposition = false; send({ type:'ime.blur' }); }
    }, true);
    document.addEventListener('focusout', function(e){
      if (e.target === el) { el = null; inComposition = false; send({ type:'ime.blur' }); }
    }, true);
    // The page (its own JS, autofill) changed the field: resync the host keyboard's view of it.
    document.addEventListener('input', function(e){ if (e.target === el && !el.__nappletIme) reportState(); }, true);
    // Mirror selection changes inside the focused editable to the host. Off-window Chrome abandons a field
    // selection by collapsing the caret to one of its endpoints; we re-assert it RIGHT HERE, synchronously,
    // the same way the page-text path does — reverting before the collapse paints, so it doesn't blink (the
    // old path round-tripped through the host EditText, leaving a visible collapsed frame each cycle). The
    // host's own re-assert in RemoteImeView.onPageState stays as a fallback for collapses we don't catch.
    document.addEventListener('selectionchange', function(){
      if (!el || el.__nappletIme) return; // our own applyState/setSel
      lastSelActivityAt = perfNow(); // Chrome moved the selection → an imminent reveal-scroll isn't a user scroll
      var sel = selOf(el);
      if (sameSel(sel, lastSel)) return; // echo of what we just applied
      if (!isCE(el) && lastFieldRange && sel[0] === sel[1] &&
          (sel[0] === lastFieldRange[0] || sel[0] === lastFieldRange[1]) &&
          (perfNow() - lastFieldAt) < 1500) {
        // Chrome's off-window abandonment collapsed our live range to an endpoint → snap it back
        // synchronously (reverts before paint, so no blink) and keep the window open. The host already
        // holds this range, so we don't re-report (which would feed the slow round-trip loop).
        el.__nappletIme = true;
        setSel(el, lastFieldRange[0], lastFieldRange[1]);
        el.__nappletIme = false;
        lastSel = selOf(el);
        lastFieldAt = perfNow();
        return;
      }
      reportState();
    }, true);
    // Tap handling on the focused editable. A single tap collapses any selection to a caret at the tap point
    // and shows the insertion handle (native; off-window Chrome won't collapse-on-tap itself). A DOUBLE tap
    // selects the word — but `click` fires before `dblclick`, so instead of guessing with timing we DEFER the
    // collapse and let the real `dblclick` cancel it. This is robust to Chrome's own double-click timing
    // (a timing guess raced it and sometimes ate the word selection → "cursor jumps to end of word").
    var collapseTimer = null;
    function clearCollapse() { if (collapseTimer) { clearTimeout(collapseTimer); collapseTimer = null; } }
    document.addEventListener('dblclick', function(e){
      if (!el || isCE(el)) return;
      clearCollapse(); // a real double-tap → don't collapse; keep Chrome's word selection
      var s = selOf(el);
      if (s[0] !== s[1] && !sameSel(s, lastSel)) reportState(); // report the word only if not already sent
    }, true);
    document.addEventListener('click', function(e){
      if (!el || isCE(el) || e.target !== el) return;
      var sel = selOf(el);
      if (sel[0] !== sel[1]) {
        // Tap landed on a selection. Defer the collapse: if a dblclick follows (within the tap window) it
        // cancels this and the word stays selected; otherwise this fires and collapses to the tapped offset.
        var x = e.clientX, y = e.clientY;
        clearCollapse();
        collapseTimer = setTimeout(function(){
          collapseTimer = null;
          if (!el) return;
          var s = selOf(el);
          if (s[0] === s[1]) return; // already collapsed
          var off = offsetFromPoint(el, x, y);
          el.__nappletIme = true;
          setSel(el, off, off);
          el.__nappletIme = false;
          lastSel = selOf(el);
          reportState();
          send({ type:'ime.carettap', geom: fieldGeom(el) });
        }, 300);
      } else {
        // Tap on a bare caret → (re-)show the insertion handle. If a double-tap follows, dblclick selects the
        // word and supersedes this.
        send({ type:'ime.carettap', geom: fieldGeom(el) });
      }
    }, true);

    function enter(n){
      if (!n) return;
      var t = (n.tagName || '').toUpperCase();
      if (isCE(n) || t === 'TEXTAREA') {
        var s = selOf(n);
        if (isCE(n)) ceReplace(n, s[0], s[1], '\n');
        else { var v = valOf(n); setVal(n, v.slice(0, s[0]) + '\n' + v.slice(s[1])); }
        setSel(n, s[0] + 1, s[0] + 1); fireInput(n, 'insertLineBreak', '\n', false); return;
      }
      ['keydown','keyup'].forEach(function(type){ try { n.dispatchEvent(new KeyboardEvent(type, { bubbles: true, cancelable: true, key: 'Enter', keyCode: 13, which: 13 })); } catch (_) {} });
      if (n.form) { try { (n.form.requestSubmit ? n.form.requestSubmit() : n.form.submit()); } catch (_) {} }
    }
    function fireInput(n, inputType, data, isComposing){
      try { n.dispatchEvent(new InputEvent('input', { bubbles: true, inputType: inputType, data: data == null ? null : data, isComposing: !!isComposing })); }
      catch (_) { n.dispatchEvent(new Event('input', { bubbles: true })); }
    }
    function fireComp(n, type, data){
      try { n.dispatchEvent(new CompositionEvent(type, { bubbles: true, cancelable: false, data: data || '' })); } catch (_) {}
    }
    function isHigh(c){ return c >= 0xD800 && c <= 0xDBFF; }
    function isLow(c){ return c >= 0xDC00 && c <= 0xDFFF; }
    // Common-prefix/suffix diff so we can classify the edit (insert vs delete vs replace). Operates on
    // UTF-16 code units but snaps both boundaries off surrogate halves, so a changed astral char (emoji,
    // CJK-supplement) is never split into a lone surrogate in `inserted`/`removed` or the replace range.
    function diff(prev, next){
      var pl = prev.length, nl = next.length, p = 0;
      while (p < pl && p < nl && prev.charCodeAt(p) === next.charCodeAt(p)) p++;
      if (p > 0 && isHigh(prev.charCodeAt(p - 1))) p--; // don't cut a pair at the prefix boundary
      var s1 = pl, s2 = nl;
      while (s1 > p && s2 > p && prev.charCodeAt(s1 - 1) === next.charCodeAt(s2 - 1)) { s1--; s2--; }
      // pl - s1 === nl - s2 (suffixes share length), so s1 < pl iff s2 < nl.
      if (s1 < pl && (isLow(prev.charCodeAt(s1)) || isLow(next.charCodeAt(s2)))) { s1++; s2++; }
      return { from: p, prevEnd: s1, inserted: next.slice(p, s2), removed: prev.slice(p, s1) };
    }
    // Adopt the host's authoritative editing STATE (Flutter's model) and synthesize the matching DOM
    // input/composition events so the page's framework reacts as if typed natively.
    function applyState(msg){
      if (!el) return;
      var n = el;
      var prev = valOf(n);
      var next = (msg.text != null) ? String(msg.text) : prev;
      var cs = (msg.composingStart != null) ? msg.composingStart : -1;
      var ce = (msg.composingEnd != null) ? msg.composingEnd : -1;
      var composingActive = (cs >= 0 && ce > cs);
      var d = diff(prev, next);

      n.__nappletIme = true;
      try {
        if (composingActive && !inComposition) { inComposition = true; fireComp(n, 'compositionstart', ''); }
        if (next !== prev) {
          if (isCE(n)) ceReplace(n, d.from, d.prevEnd, d.inserted); // in-place, preserves structure
          else setVal(n, next);
          var inputType = composingActive ? 'insertCompositionText'
            : (d.inserted && !d.removed) ? 'insertText'
            : (d.removed && !d.inserted) ? 'deleteContentBackward'
            : 'insertReplacementText';
          if (inComposition) fireComp(n, 'compositionupdate', next.slice(cs, ce));
          fireInput(n, inputType, d.inserted, composingActive);
        }
        setSel(n, msg.selStart, msg.selEnd);
        if (!composingActive && inComposition) { inComposition = false; fireComp(n, 'compositionend', d.inserted || ''); }
      } finally { n.__nappletIme = false; noteSel(selOf(n)); }
    }

    // --- Page (non-editable) text selection re-hosting ---
    // Chrome can't present its selection handles/toolbar in the cross-process embedded surface, so a
    // long-press on ordinary page text selects a word and then ~60ms later abandons (collapses) it, the
    // same way it does inside inputs. Mirror the document selection: re-assert it when it collapses right
    // after forming, and report the selected text so the host can show its own Copy bar over the page.
    var pageSelText = '', lastPageRange = null, lastPageAt = -1, pageReasserting = false;
    // Selection geometry in CSS px (viewport coords). The host maps these to screen px (scale = surface
    // width / vw) to draw the toolbar above the selection and a handle at each end. l/t/r/b is the bounding
    // box; (sx,sb) the start-caret foot, (ex,eb) the end-caret foot; vw lets the host derive the scale.
    function pageGeom(r){
      try {
        var b = r.getBoundingClientRect();
        var sr = r.cloneRange(); sr.collapse(true); var s = sr.getBoundingClientRect();
        var er = r.cloneRange(); er.collapse(false); var e = er.getBoundingClientRect();
        return { l: b.left, t: b.top, r: b.right, b: b.bottom, sx: s.left, sb: s.bottom, ex: e.left, eb: e.bottom, vw: window.innerWidth };
      } catch (_) { return null; }
    }
    function sendPageSel(active, r){
      var text = active ? String(window.getSelection()) : '';
      pageSelText = text;
      send({ type: 'ime.pagesel', active: active, text: text, geom: active && r ? pageGeom(r) : null });
    }
    document.addEventListener('selectionchange', function(){
      if (el || pageReasserting) return; // selections inside an editable are handled above
      lastSelActivityAt = perfNow(); // page selection moved → an imminent reveal-scroll isn't a user scroll
      var s = window.getSelection();
      if (s && s.rangeCount && !s.isCollapsed) {
        var r = s.getRangeAt(0);
        lastPageRange = r.cloneRange(); lastPageAt = perfNow();
        sendPageSel(true, r);
      } else if (lastPageRange && (perfNow() - lastPageAt) < 400) {
        pageReasserting = true;
        try { s.removeAllRanges(); s.addRange(lastPageRange); } catch (_) {}
        pageReasserting = false;
        lastPageAt = perfNow();
      } else if (pageSelText) {
        lastPageRange = null;
        sendPageSel(false, null);
      }
    }, true);
    // Host drag of a selection handle: move the dragged edge to the text position under (x,y) CSS px,
    // keeping the opposite edge anchored. setBaseAndExtent tolerates either drag direction.
    function pageExtend(edge, x, y){
      try {
        var pt = document.caretRangeFromPoint && document.caretRangeFromPoint(x, y);
        var s = window.getSelection();
        if (!pt || !s.rangeCount) return;
        var cur = s.getRangeAt(0);
        var aN, aO;
        if (edge === 'start') { aN = cur.endContainer; aO = cur.endOffset; } else { aN = cur.startContainer; aO = cur.startOffset; }
        pageReasserting = true;
        s.setBaseAndExtent(aN, aO, pt.startContainer, pt.startOffset);
        pageReasserting = false;
        if (!s.isCollapsed) {
          var nr = s.getRangeAt(0);
          lastPageRange = nr.cloneRange(); lastPageAt = perfNow();
          sendPageSel(true, nr);
        }
      } catch (_) {}
    }

    // Word-granularity snapping (native: dragging a word selection's handle extends a word at a time). The
    // end handle snaps to the end of the word at/after the offset; the start handle to the start of the word
    // at/before it. Whitespace between words extends to the adjacent word so you never stop mid-gap.
    function isWordChar(c){ return c != null && /\S/.test(c); }
    function wordEndAt(text, off){
      var i = off;
      while (i < text.length && !isWordChar(text[i])) i++;
      while (i < text.length && isWordChar(text[i])) i++;
      return i;
    }
    function wordStartAt(text, off){
      var i = off;
      while (i > 0 && !isWordChar(text[i - 1])) i--;
      while (i > 0 && isWordChar(text[i - 1])) i--;
      return i;
    }

    // Per-drag state for the hybrid word/char handle extend below. `fieldDragWordEnd`/`fieldDragWordStart`
    // remember how far the dragged edge has been word-snapped so far this gesture; a >250ms gap between
    // `ime.fieldextend` ops (or a switch of edge) means a NEW drag, so we re-baseline to the live selection.
    var fieldDragAt = -1, fieldDragEdge = null, fieldDragWordEnd = -1, fieldDragWordStart = -1;
    // Host drag of an in-field selection handle: move the dragged edge to the offset under (x,y) CSS px,
    // keeping the other edge anchored, clamped so it can't cross the anchor. HYBRID granularity, matching
    // native `Editor` word-selection drags (#5): the gesture starts anchored to the current selection edge,
    // and as the finger sweeps PAST that word's far boundary it snaps the dragged edge to the next WHOLE word
    // (so sweeping across words grabs them whole and never stops mid-gap); moving WITHIN or back from the
    // furthest-reached word gives CHARACTER precision (so you can fine-tune to a single character).
    function fieldExtend(edge, x, y){
      if (!el || isCE(el)) return;
      try {
        var off = offsetFromPoint(el, x, y);
        var text = valOf(el);
        var sel = selOf(el);
        var now = perfNow();
        var fresh = (now - fieldDragAt > 250) || edge !== fieldDragEdge;
        fieldDragAt = now; fieldDragEdge = edge;
        var s, e;
        if (edge === 'start') {
          e = sel[1];
          if (fresh) fieldDragWordStart = sel[0]; // baseline at the current selection start
          if (off < fieldDragWordStart) { s = wordStartAt(text, off); fieldDragWordStart = s; } // swept into a new word → snap whole
          else s = off; // within / back from the furthest word → character precision
          s = Math.max(0, Math.min(s, e));
        } else {
          s = sel[0];
          if (fresh) fieldDragWordEnd = sel[1]; // baseline at the current selection end
          if (off > fieldDragWordEnd) { e = wordEndAt(text, off); fieldDragWordEnd = e; } // swept into a new word → snap whole
          else e = off; // within / back from the furthest word → character precision
          e = Math.min(text.length, Math.max(e, s));
        }
        el.__nappletIme = true;
        setSel(el, s, e);
        el.__nappletIme = false;
        lastSel = selOf(el);
        reportState();
      } catch (_) {}
    }

    // While the page scrolls, host-drawn selection UI (toolbar + handles) would float at stale positions, so
    // the host hides it on scroll-start and we re-report fresh geometry on scroll-idle so it reappears in the
    // right place — like Android. Only signal when there's a selection to hide (a field range or page text).
    var scrolling = false, scrollTimer = null, autoScrolling = false;
    // How long after a selection change a scroll is treated as the browser's auto-reveal of that selection
    // (not a user content scroll). Generous enough to catch the reveal-scroll that fires a frame or two later.
    var SCROLL_SEL_GUARD_MS = 350;
    function hasSelectionUi(){ return !!pageSelText || !!(el && (function(s){ return s[0] !== s[1]; })(selOf(el))); }
    function onAnyScroll(){
      if (autoScrolling) return; // our own drag-to-edge auto-scroll: keep the overlays up, don't hide them
      if (!hasSelectionUi()) return;
      if ((perfNow() - lastSelActivityAt) < SCROLL_SEL_GUARD_MS) {
        // The browser auto-scrolled to reveal a just-changed selection (forming/re-asserting a range scrolls a
        // textarea). That's not a user content scroll: hiding here would blink the host overlays off-and-on every
        // time a selection settles. Reposition them in place instead (geometry shifted by the reveal-scroll).
        // Crucially we do NOT touch the hide-on-scroll timer: if a real scroll-hide is somehow active, these
        // reveal-scrolls must not keep re-arming it (that would leave the overlays hidden indefinitely).
        if (el) reportState();
        else { var sr = window.getSelection(); if (sr && sr.rangeCount && !sr.isCollapsed) sendPageSel(true, sr.getRangeAt(0)); }
        return;
      }
      if (!scrolling) { scrolling = true; send({ type:'ime.scroll', active: true }); }
      if (scrollTimer) clearTimeout(scrollTimer);
      scrollTimer = setTimeout(function(){
        scrolling = false; scrollTimer = null;
        // Refresh geometry FIRST (so overlays reposition), then tell the host to show them again.
        if (el) reportState();
        else { var s = window.getSelection(); if (s && s.rangeCount && !s.isCollapsed) sendPageSel(true, s.getRangeAt(0)); }
        send({ type:'ime.scroll', active: false });
      }, 150);
    }
    document.addEventListener('scroll', onAnyScroll, true); // capture: any scroller, not just the document

    window.__nappletImeHandle = function(msg){
      if (msg.type === 'ime.set') applyState(msg);
      else if (msg.type === 'ime.action') enter(el);
      else if (msg.type === 'ime.pageextend') pageExtend(msg.edge, msg.x, msg.y);
      else if (msg.type === 'ime.fieldextend') fieldExtend(msg.edge, msg.x, msg.y);
      else if (msg.type === 'ime.autoscroll') {
        // Host drag of a handle near the surface's top/bottom edge → scroll the content (the textarea if it
        // scrolls, else the page) so the selection can keep extending, then re-report geometry so the
        // overlays follow. Flagged so our own scroll doesn't trip the hide-on-scroll path above.
        var dy = msg.dy || 0;
        autoScrolling = true;
        try {
          if (el && (el.tagName || '').toUpperCase() === 'TEXTAREA') el.scrollTop += dy;
          window.scrollBy(0, dy);
        } catch (_) {}
        if (el) reportState();
        else { var s = window.getSelection(); if (s && s.rangeCount && !s.isCollapsed) sendPageSel(true, s.getRangeAt(0)); }
        setTimeout(function(){ autoScrolling = false; }, 0);
      }
      else if (msg.type === 'ime.caretmove') {
        if (el && !isCE(el)) {
          var off = offsetFromPoint(el, msg.x, msg.y);
          el.__nappletIme = true;
          setSel(el, off, off);
          el.__nappletIme = false;
          lastSel = selOf(el);
          reportState();
        }
      }
    };
  })();

  // NIP-07 provider (window.nostr), installed only for nSites in website mode (the host sets
  // window.__nappletNip07 synchronously before this shim). Lets standard Nostr web apps "log in with
  // Amethyst" and sign, bridged to the same consent-gated signer: getPublicKey + getRelays reuse the
  // identity reads; signEvent is sign-only (no publish) and honors the app's created_at.
  if (window.__nappletNip07 && !window.nostr) {
    window.nostr = Object.freeze({
      getPublicKey: function(){ return field(call('identity.getPublicKey'), 'pubkey'); },
      getRelays: function(){ return field(call('identity.getRelays'), 'relays'); },
      signEvent: function(event){ return field(call('nostr.signEvent', { event: event }), 'event'); }
    });
  }
})();
