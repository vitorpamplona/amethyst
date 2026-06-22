# Napplet / nsite security review (2026-06-22)

A review of the attack surface for NIP-5A static sites (nsites) and NIP-5D napplets,
the protections in place, and the residual risks — with what was fixed in this pass
and what remains as future work.

## Trust model (what holds)

- **Keys never enter the sandbox.** The `:napplet` process holds no account/signer.
  Signing happens only in the main process (`signer` fixes `pubkey`, host clock
  prevents backdating). *"Even a full WebView/renderer escape into this process yields
  no secret."*
- **Content integrity.** Every blob is sha256-verified against the **signed** manifest
  before serving (`StaticSiteResolver`); cache is content-addressed + re-verified, so a
  poisoned/stale cache can't be served. nsites launch with **zero** capabilities.
- **Network containment.** App CSP `connect-src 'none'`; egress only via the brokered,
  consent-gated `resource.bytes`, Tor-routed. Bridge is origin-restricted + main-frame.
- **IPC binding** is `exported=false` + same-UID (`onBind` UID check). Payments always
  prompt per-use with the amount.

## Fixed in this pass

1. **Cross-napplet identity/storage spoofing (was: per-message identity).** The broker
   used to trust `author`/`identifier`/`declared` sent on **every** IPC message from the
   `:napplet` process. A WebView→native escape could forge another napplet's coordinate
   and read/act as it. Now the **main process** mints a random launch token
   (`NappletLaunchRegistry`), hands only that token to the sandbox, and the broker
   resolves it back to the trusted identity + declared set. A compromised sandbox can act
   as nothing but the napplet it was launched as (it holds only its own token).

2. **Private mute/block leak via `identity.getMutes`/`getBlocked`.** These read
   `muteList.flow` / `blockPeopleList.flow`, which contain **decrypted private** entries.
   Now they read the events' **public** tags only (`MuteListEvent.publicMutes()`,
   `PeopleListEvent.publicUsersIdSet()`).

3. **Silent "allow-always" actions + UI-redress.** Added persistent **trusted chrome**
   (a sandbox bar the applet can't draw over: shield + name + tap-to-see "what it can
   access") and a **live toast** when a granted RELAY/UPLOAD/VALUE op runs — so an
   allow-always grant can't act completely silently. Also fixes the host drawing under
   the status/navigation bars (edge-to-edge insets).

## Residual risks / future work

- **Coarse, persistent grants.** Non-payment capabilities persist as ALLOW_ALWAYS. A
  consented napplet can still, thereafter, publish as you (RELAY), read your social graph
  (IDENTITY), and make arbitrary network calls (RESOURCE) without re-prompting. The new
  chrome + toasts make this *visible*, but the model is still allow-forever. **Proposed:**
  a session-scoped grant ("Allow while open", cleared on close) in `GrantState` +
  consent dialog, defaulted for RESOURCE/RELAY; and per-origin consent for cross-origin
  `resource.bytes` https fetches.
- **`resource.bytes` as exfil channel.** Once RESOURCE is allow-always, the applet can
  encode data into arbitrary https URLs through the Tor proxy. Tor hides the IP, not the
  payload. Tie to the per-origin/session proposal above.
- **`'unsafe-inline'` in app `script-src`.** Required to inject the shim; the applet is
  the author's own (content sha256-pinned), so it's not an escalation. `connect-src
  'none'` remains the real boundary. Residual, accepted.
- **Trust pivots on the author key.** A compromised author key lets an attacker push a
  new *signed* manifest and own the app — expected Nostr trust model. Aggregate `x` hash
  is enforced when present (only *recommended* by the spec); per-path hashes always
  protect.
- **Sandbox isolation is now structural.** The `:napplet` runtime
  (`NappletHostActivity`, content server, IPC, key actions) lives in its own
  `:nappletHost` module that depends only on `:commons` + `:quartz` — so it is
  *compile-time incapable* of importing `Amethyst`/`LocalCache`/`Account`. The
  broker-side (signer, gateways, `NappletLaunchRegistry`) stays in `:amethyst`;
  the activity binds the broker service by class-name string and the two halves
  communicate only over Messenger IPC.
- **Launch-token lifecycle.** Tokens are capped (LRU, 128) rather than explicitly
  unregistered on sandbox close (the sandbox is a separate process and can't reach the
  main-process registry). A long-backgrounded napplet whose token was evicted would need
  relaunch. Acceptable; revisit if it bites.
