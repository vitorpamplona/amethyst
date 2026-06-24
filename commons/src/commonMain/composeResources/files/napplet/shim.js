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
    function setVal(n, v){ if (isCE(n)) n.textContent = v; else n.value = v; }

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
      if (isCE(n)) ceSetSel(n, s, e);
      else { try { n.setSelectionRange(s, e); } catch (_) {} }
    }
    function focusInfo(n){
      var t = (n.tagName || '').toUpperCase();
      var multiline = isCE(n) || t === 'TEXTAREA';
      var inputType = isCE(n) ? 'text' : (t === 'TEXTAREA' ? 'textarea' : (n.type || 'text').toLowerCase());
      var sel = selOf(n);
      return { type:'ime.focus', inputType: inputType, enterKeyHint: (n.enterKeyHint || ''),
               multiline: multiline, text: valOf(n), selStart: sel[0], selEnd: sel[1] };
    }
    // Last selection we either applied (applyState) or already reported, so the asynchronous
    // selectionchange our own setSel triggers doesn't echo back to the host as a fresh edit.
    var lastSel = null;
    function sameSel(a, b){ return !!(a && b && a[0] === b[0] && a[1] === b[1]); }
    function reportState(){
      if (!el) return;
      var sel = selOf(el);
      lastSel = sel;
      send({ type:'ime.state', text: valOf(el), selStart: sel[0], selEnd: sel[1] });
    }

    document.addEventListener('focusin', function(e){
      if (isEditable(e.target)) {
        el = e.target; inComposition = false; lastSel = selOf(el); send(focusInfo(el));
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
    document.addEventListener('selectionchange', function(){
      if (!el || el.__nappletIme) return;
      if (sameSel(selOf(el), lastSel)) return; // our own applyState/setSel echoing back
      reportState();
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
      } finally { n.__nappletIme = false; lastSel = selOf(n); }
    }

    window.__nappletImeHandle = function(msg){
      if (msg.type === 'ime.set') applyState(msg);
      else if (msg.type === 'ime.action') enter(el);
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
