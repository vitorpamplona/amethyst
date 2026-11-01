# Desktop napplet / nsite host — implementation plan

> **Status:** napplets (NIP-5D) implemented 2026-10-29 in `desktopApp/.../desktop/napplet/`; nSites
> (NIP-5A) not yet. See "Built" below for what exists and what is still open.

**Date:** 2026-06-21. Goal: a desktop NIP-5A (nsite) + NIP-5D (napplet) host that **reuses the
shared core** the Android host already runs on, so the two stay wire- and policy-identical. This
doc is the map: what already works for free, what desktop must build, and the decisions to make.

## What is already shared (reuse verbatim)

**quartz (`commonMain`)** — protocol, no work needed:
- NIP-5A/5D events, `NappletManifest`, `StaticSiteResolver` (path → hash resolution + per-blob
  sha256 verify), `StaticSitePathLookup` (`sniffContentType`, SPA `index.html` normalization),
  `SiteAggregateHash`.

**commons (`commonMain` / `jvmAndroid`)** — the security brain + wire, already platform-agnostic:
- `NappletBroker` (the trust boundary: declaration gate → ledger → consent → execute), `NappletCapability`,
  `NappletIdentity`, `NappletRequest`/`NappletResponse`, the permissions `Ledger`/`Store`/`GrantState`,
  and the gateway **interfaces** (`NappletRelayGateway`, `NappletStorage`, `NappletWalletGateway`,
  `NappletResourceGateway`, `NappletUploadGateway`, `NappletIdentityGateway`, `NappletConsentPrompt`).
- **`NappletProtocolJson`** (`commons/commonMain`) — the wire codec. Desktop's host marshals through
  the same object, so request/result/push shapes can never drift between platforms.

**The web contract (`commons/commonMain/composeResources/files/napplet/` + `NappletWebContract`)** —
`shell.html` (the trusted shell page that hosts the applet in an opaque-origin
`sandbox="allow-scripts"` iframe and bridges object↔string), `shim.js` (the injected
`window.napplet.*`), and `NappletWebContract` (the origin/URLs + both CSP strings + shell/shim
loaders). **Reused byte-for-byte** — they embody the envelope/push contract. Desktop reads the same
`Res.readBytes(...)` and serves the same CSP headers.

## What desktop must build (platform-specific)

| Concern | Android (today) | Desktop (to build) |
|---|---|---|
| **Web engine** | Android `WebView` (Chromium) | **KCEF/JCEF** (Chromium Embedded for the JVM). Gives the same CSP, opaque-origin iframe, and custom-scheme interception we rely on. Compose's experimental WebView is too limited. |
| **Isolation** | separate `:napplet` OS process (no keys) | ~~The CEF renderer is already sandboxed~~ (wrong: JCEF runs Chromium with `no_sandbox`, see Isolation below); combine with the same CSP (`connect-src 'none'`) + no-`allow-same-origin` iframe. For parity with Android's process model, evaluate running CEF in a **child JVM process**; at minimum rely on the renderer sandbox + CSP. |
| **Resource serving** | `WebViewClient.shouldInterceptRequest` → shell + verified blobs | a CEF **custom scheme handler** that serves `https://napplet.local/__shell__` + `/app/*` from `StaticSiteResolver` with the identical CSP headers. |
| **Transport** | `Messenger` across processes | in-process: a direct bridge (CEF JS-query ↔ broker). If child-process: stdio/socket carrying the **same JSON envelopes**. Either way, the payloads are `NappletProtocolJson`. |
| **Live subscriptions** | `INostrClient.subscribe` + push over Messenger | same `INostrClient.subscribe`; push `relay.event/eose/closed` over the desktop transport. |
| **Gateways** | impls bound to `Account`/`BlossomUploader`/DataStore/NWC in `NappletBrokerService` | implement the same interfaces against the desktop account + relay client + uploader (the back end is shared). |
| **Entry point** | Activity + bottom-nav; feed card | a desktop window/pane + sidebar; the same inert feed card. |

## Decisions to make first

1. **Web engine:** KCEF (Kotlin wrapper over JCEF) vs raw JCEF. KCEF is the lighter integration for
   Compose Desktop. Confirm license (JCEF/CEF is BSD — permissive, OK) before adding the dependency.
2. **Isolation model:** renderer-sandbox-only (simpler) vs CEF-in-child-process (closer to Android's
   keyless-process guarantee). Start with renderer + CSP; treat child-process as a hardening follow-up.
3. **Transport:** in-process bridge (fine if isolation is renderer-only) vs child-process IPC.

## Recommended shared extractions (do as part of, or just before, desktop work)

These reduce desktop reimplementation and prevent drift:

- **DONE: `NappletRequestRouter` (commons/jvmAndroid).** The orchestration (readType → `relay.close`
  / `resource.cancel` short-circuit → decode → `broker.handle` → encode reply / detect
  `Subscribed`) used to live in Android's `NappletBrokerService.handleMessage`. It is now a pure
  router returning a small `Outcome` (`Ignore` / `Reply(payload)` / `OpenSubscription(subId, filters)` /
  `CloseSubscription(subId)` / `Push(payloads)`), so both hosts share the brain and only supply the
  broker + transport + live relay subscription. Unit-tested in `commons/jvmTest`
  (`NappletRequestRouterTest`); Android's service consumes it via a `when (outcome)` dispatch. The
  desktop host will route the exact same way.
- **DONE: Shared web assets + contract.** `shell.html` + `shim.js` now live in
  `commons/commonMain/composeResources/files/napplet/` and are read via `Res.readBytes("files/napplet/...")`.
  A new `commons/commonMain/.../napplet/NappletWebContract` single-sources the whole web contract:
  the shell/shim loaders **plus** the origin/host/URLs and both CSP strings (`SHELL_CSP`, `APP_CSP`).
  Android's host preloads the bytes in `onCreate` and reads every origin/CSP constant from
  `NappletWebContract`; the desktop scheme handler will serve the same bytes + headers.
- **DONE: The inert feed card.** The preview card is now
  `commons/commonMain/.../ui/note/StaticWebsiteCard` — self-contained (commons compose-resource
  strings, `LocalUriHandler` for links, inlined card chrome), parameterized by `isNapplet` and an
  `onOpen` launch slot. Android's `note/types/StaticWebsite.kt` is now a thin set of event→card
  adapters; the desktop feed renders the identical card and supplies its own `onOpen`.

## Security parity checklist (desktop must match Android)

- Opaque-origin `sandbox="allow-scripts"` iframe (no `allow-same-origin`).
- CSP `connect-src 'none'` (applet has no direct network) + `default-src` locked to the internal origin.
- Serve **only** the manifest's declared paths, each **sha256-verified** before serving (reuse `StaticSiteResolver`).
- The web engine/renderer holds **no keys**; all signing/consent stays in the broker.
- Capability **declaration gate** + consent + signer-aware deferral (all already in `NappletBroker`).
- Foreground-only execution (pause the engine when the pane is hidden) — mirror Android's `onPause`.
- Route external links to the system browser only on a user gesture; never navigate the sandbox away.

## Built (2026-10-29)

Napplets and nSites open from the feed card's Open button and from favorites and the browser home,
each in its own window. The card is the shared one every platform renders (`StaticWebsiteManifestCard`,
with the favorite star and a prefetch of its files while on screen); Open goes through the shared
`AppLauncher`, which `DesktopAppPlatform` implements with the host.

- **Engine:** raw JCEF through jcefmaven (`me.friwi:jcefmaven`, Apache-2.0; JCEF and CEF BSD-3;
  Chromium's `libcef.so` includes FFmpeg, LGPL-2.1). KCEF was dropped: no release since 2025-03.
  `DesktopCefEngine` downloads the native bundle into the data dir on first use, with progress in
  the window, and runs Chromium with background networking off (its startup otherwise waits on
  Google services, forever behind a TLS-breaking proxy). Chromium's own services (account
  `ListAccounts`, search prefetch, network time, DNS over HTTPS) ignore that flag, so every
  connection Chromium makes, loopback included, goes through `DesktopSocksRouter`, a SOCKS5 server
  in the app that refuses all of it unless an open nSite asked for a route (a net log of the test
  run showed nothing else leaving). The router resolves a name before connecting directly and
  refuses one that lands on this machine (DNS rebinding, `::ffff:127.0.0.1`), and closes every
  refused connection; its pumps run on virtual threads. WebRTC can't go around it: UDP ignores a
  SOCKS proxy, so the profile sets `webrtc.ip_handling_policy` to `disable_non_proxied_udp` (CEF
  ignores the command-line switch; preferences can only be set on its UI thread, once the global
  context starts, and the engine fails closed if one is refused). Third-party cookies are blocked
  (`profile.cookie_controls_mode`). Permission prompts (camera, microphone, location) are denied.
  The profile is `jcef-cache/Default`: a napplet's opaque srcdoc keeps nothing there, an nSite's
  localStorage, IndexedDB and cookies persist under its own origin.
- **Isolation:** the shared CSPs + opaque-origin srcdoc, in process. No child JVM: the trust
  boundary is the bridge check below. **There is no renderer sandbox:** JCEF hard-codes
  `no_sandbox = true` and exposes no setting to change it, so a Chromium renderer exploit (an
  nSite, a webxdc app, a video played through `ChromiumMediaSession`) runs as the user in the
  process family that holds the keys. The 2026-10-31 security review rates this Critical; the fix
  is the child process this plan deferred (Chromium in a process with no account and no keys, as
  on Android) or OS-level confinement of `jcef_helper` (bubblewrap / Flatpak portal on Linux,
  `sandbox-exec` on macOS). See `commons/plans/2026-10-31-embed-security-review.md`.
- **Serving:** shared with Android: `NappletSiteServer` (commons) decides every response and the
  session only translates it for Chromium. A napplet gets only `SHELL_URL` (shell CSP header); every
  other request is cancelled before it leaves (data:, blob: and about:srcdoc stay local).
  `NappletBlobStore` fetches each file from the event's servers into the content-addressed
  `NappletBlobCache`, writing only bytes that hash to their name, with one download per blob however
  many requests miss it at once. Files stream from the cache (a cancelled request closes its file);
  only HTML is read whole, to prepend the prelude, which is built once per launch. The shell
  base64-encodes the napplet into one presized array: a 20 MB napplet allocates its 28 MB page and
  nothing more. The cache trims to 256 MB least recently used first, from one job as windows close.
- **Transport:** CEF's message router. Chromium injects its query function into every frame,
  so only a query from the main frame at `SHELL_URL` is accepted; the shell captures the function
  and deletes it from its window. Replies go back through `executeJavaScript` into the shell's
  `__nappletBridge.onmessage`, with the request id added as on Android; replies queued together go
  in one script, in order.
- **Broker:** one per account from `buildAccountNappletBroker` (commonsUI), the factory Android
  uses too: the account's relays, wallet, resource fetches and identity reads, its signer without
  Amethyst's client tag, and the persisted `DataStoreNappletPermissionStore` ledger. Live subscriptions use the now-generic
  `NappletLiveSubscriptions`. Windows close when their launching account is no longer logged in.
- **Consent:** questions wait in `NappletPromptQueues` (commons, on the `ConsentQueue` Android's
  coordinators also use) under their app; each window shows its app's oldest in a separate
  `DialogWindow`, so Chromium's heavyweight view can't cover it, with the shared cards
  (`NappletConsentCard`, `SignerConnectCard`, `SignerConsentCard`, `BatchedConsentCard`).
- **Theme:** `theme.get` and `theme.changed` follow the app's Material colors.
- **nSites** (`HostProfile.WEBSITE`, now in commonsUI and shared with Android): the shell from the
  shared `NappletWebContract.shell`, the site at its own `napplet.local` origin with
  `allow-scripts allow-same-origin`. Every file from that origin is a verified blob from the event
  (a document navigation to an unlisted path falls back to `/`, a missing script or image is a 404);
  HTML gets the prelude with NIP-07. The origin includes the account (`NappletWebContract.appId`'s
  `partition`), because CEF keeps one storage jar where Android picks a WebView profile per
  account. Requests to other hosts go out on the site's route. The shell and the site's own frame
  never navigate away (a clicked link opens the system browser); embeds inside the site may.
- **Network:** `NsiteNetworkChoices` (commons, Android's `NsiteNetworkRegistry` now delegates
  to it) keeps each site's Tor or open-web choice, Tor by default while Tor is on.
  `DesktopNappletRoutes` arbitrates the router's one route like Android's process-wide WebView
  proxy: any site on Tor takes everything through Tor (an open-web site then says so), otherwise
  any site on the open web goes direct, otherwise nothing leaves. With Tor on and not running, a
  site, and a napplet's blob fetch, waits up to 30 s and then fails closed with "nothing left this
  computer". Switching a site reopens its window, as Android relaunches its activity.
- **NIP-07 consent:** the broker gets the signer ledger, the connect prompt and the per-operation
  prompt (120 s, then deny). Their UI is shared now: `SignerConnectCard`, `SignerConsentCard` and
  `BatchedConsentCard` (commonsUI) with `buildConnectInfo`/`buildSignerConsentInfo`, and
  `Nip46ConsentInfoBuilder` in commons; Android's activities wrap the same cards.
- **Window chrome:** a toolbar above the page, which the page can't draw over: back, forward,
  reload/stop, the title and posture (opens Android's `AccessInfoSheet`), find, zoom, the onion
  switch for a site while Tor is on, and Close. Loading, waiting for Tor and each failure have
  their own pane with Try again.
- **Tests:** `CefNappletSessionTest`: a tampered blob never loads; a Tor site doesn't open
  while Tor is down and fetches nothing; with `AMETHYST_CEF_TEST=1` under a display, a probe
  napplet's direct call to the bridge from its iframe is refused while the shim's storage round
  trip works and its `fetch` never reaches the server, the Doom napplet boots and asks for its
  saves, and a site runs at its own per-account origin (verified files, 404 for unlisted ones, the
  client-route fallback, localStorage, `window.nostr` getPublicKey and signEvent through the
  connect and signer prompts, and no navigation away). `DesktopSocksRouterTest` covers the routes,
  the loopback refusal, Tor carrying the connection, cutting open connections on a switch, closing
  a refused connection, and the arbitration. With Chromium, a napplet's `RTCPeerConnection` sends
  no packet to a STUN server on the machine's LAN address, and a burst of 100 replies reaches its
  requests whole.
- **Checked in the running app:** published nSites (Hallway logged in through NIP-07 and kept its
  login across a restart) and napplets (Mini Samurai) open from their cards; waiting for Tor,
  the fail-closed pane, the onion switch, find, zoom, the access window and Close.

Open:

- "Manage permissions" is hidden on desktop: the per-app permission screen
  (`ConnectedAppDetailScreen`) is still Android's.
- A site's service worker can't register on either platform: neither host serves worker scripts.
- While any site is on the open web, Chromium's own services go direct too (one process-wide
  route, as on Android).
- No developer console panel: console messages and load errors go to the log.
- Not offered: upload, notify, keys.onAction. The inc bus is offered since 2026-11-01 (`commons/plans/2026-11-01-nap-inc.md`).
- No foreground hold (`NappletHeldRequests`) or `identity.changed` push yet; acting requests are
  still consent-gated.
- Native Wayland (JBR's `WaylandWindow`) is unverified: CEF's windowed mode is X11.
- macOS: the CEF bundle downloads unsigned into the data dir; notarized builds may need it signed
  or shipped inside the app. Windows is untested.

## Status

Built and shipping behind the feed's Open button; the open items above remain. A 2026-10-29 audit
fixed: blob downloads read with an API-33 call on Android (minSdk 26), WebRTC leaking around the
router, refused SOCKS connections left open, names resolving to loopback, a broker kept for a
logged-out account, races between a window's start and close and between route claims, and the
performance items folded into Serving and Transport above.

Then the structural review's remaining steps: the broker factory and the consent queue are shared
(above), the card is the shared renderer's, and names say what each class is: `CefNappletSession`
(the JCEF session), `NsiteNetworkChoices`, the string-free consent helpers in commons
(`SignerConsentFacts.kt`).

A second audit (2026-10-30) fixed, each with a test where one can run here:
- **WebRTC:** a napplet could open TURN over TCP through an open route. A content-script extension
  (`DesktopCefEngine.installGuard`, script `NappletWebContract.WEBRTC_GUARD_JS`) takes WebRTC away
  from the shell and every frame under it; the shell refuses to start a napplet if WebRTC is still
  there. Android runs the same script in every frame of napplet and Tor-routed WebViews (untested on
  a device).
- **Router:** local networks are refused on every route, the app's own `*.napplet.local` names never
  leave, every resolved address is tried, half-closes pass through, and a failed window releases
  its route claim.
- **Cache:** partitioned per account and site (`NappletBlobCache.forSite`), so a site can't learn
  from a cache hit what else this device opened.
- **Serving:** percent-encoded paths, first-wins duplicate tags, 502 for a listed file that failed,
  no exception into the browser.
- **Consent:** an encrypted publish is judged by the kind it signs, every prompt times out, grants
  and storage belong to the broker's account, subscriptions keep request order, and clicks are
  ignored for the first moment a question is up.
- **Zoom:** CSS zoom on the shell's frame, per window (Chromium's zoom level is per host).
