// Injected window.napplet.* client shim. Namespaced to match the @napplet/shim SDK over the
// {type:"domain.action", id} envelope, so an applet built against that SDK runs unchanged here.
// This is loaded as an asset and injected into the applet document by NappletHostActivity.
(function(){
  if (window.__nappletShimInstalled) return; window.__nappletShimInstalled = true;
  var seq = 0, pending = {}, subs = {}, actions = {}, identityHandlers = [];
  function send(env){ env.id = env.id || ('r' + (seq++)); parent.postMessage(JSON.stringify(env), '*'); return env.id; }
  function call(type, fields){
    return new Promise(function(resolve, reject){
      var env = { type: type }; if (fields) for (var k in fields) env[k] = fields[k];
      var id = send(env);
      pending[id] = { resolve: resolve, reject: reject };
    });
  }
  // Fire-and-forget (no .result awaited), used for subscribe/unsubscribe.
  function post(type, fields){ var env = { type: type }; if (fields) for (var k in fields) env[k] = fields[k]; send(env); }
  window.addEventListener('message', function(e){
    if (e.source !== parent) return;
    var msg; if (typeof e.data === 'string') { try { msg = JSON.parse(e.data); } catch (_) { return; } } else { msg = e.data; }
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
    // identity.changed push: the active user's key changed (account switch / connect / disconnect).
    if (msg.type === 'identity.changed') { identityHandlers.slice().forEach(function(h){ try { h(msg.pubkey); } catch (_) {} }); return; }
    if (!msg.id) return;
    var p = pending[msg.id]; if (!p) return; delete pending[msg.id];
    if (msg.ok) p.resolve(msg);
    else { var err = new Error(msg.reason || msg.operation || msg.error || 'napplet error'); err.napplet = msg; p.reject(err); }
  });
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
    // keys = keyboard / command action binding (NOT signing). The shell acknowledges registration;
    // the global-key binding + keys.action push is a follow-up, so onAction won't fire yet.
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
})();
