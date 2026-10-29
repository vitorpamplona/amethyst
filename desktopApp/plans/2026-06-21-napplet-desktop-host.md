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
| **Isolation** | separate `:napplet` OS process (no keys) | The CEF **renderer is already sandboxed**; combine with the same CSP (`connect-src 'none'`) + no-`allow-same-origin` iframe. For parity with Android's process model, evaluate running CEF in a **child JVM process**; at minimum rely on the renderer sandbox + CSP. |
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

Napplets open from the feed card's Open button (`DesktopNotePlatform` → `DesktopNappletCard`) and
from favorites and the browser home (`DesktopAppPlatform`'s launcher), each in its own window.

- **Engine:** raw JCEF through jcefmaven (`me.friwi:jcefmaven`, Apache-2.0; JCEF and CEF BSD-3;
  Chromium's `libcef.so` includes FFmpeg, LGPL-2.1). KCEF was dropped: no release since 2025-03.
  `DesktopCefEngine` downloads the native bundle into the data dir on first use, with progress in
  the window, and runs Chromium with background networking off (its startup otherwise waits on
  Google services, forever behind a TLS-breaking proxy) and no disk cache. Chromium's own
  services (account `ListAccounts`, search prefetch, network time, DNS over HTTPS) ignore that
  flag, so all of its traffic goes to a proxy that does not exist (`127.0.0.1:9`, loopback
  included); a net log of the test run shows no other connection.
- **Isolation:** renderer sandbox + the shared CSPs + opaque-origin srcdoc, in process. No child
  JVM: the trust boundary is the bridge check below.
- **Serving:** `DesktopNappletSession` serves only `SHELL_URL` from memory (shell CSP header) and
  cancels every other request before it leaves (data:, blob: and about:srcdoc stay local).
  `DesktopNappletBlobs` fetches the artifact from the manifest's servers plus the author's
  kind:10063 list into a content-addressed cache, writing only bytes that hash to their name;
  quartz's `StaticSiteResolver` hashes again on every launch.
- **Transport:** CEF's message router. Chromium injects its query function into every frame,
  so only a query from the main frame at `SHELL_URL` is accepted; the shell captures the function
  and deletes it from its window. Replies go back through `executeJavaScript` into the shell's
  `__nappletBridge.onmessage`, with the request id added as on Android.
- **Broker:** `DesktopNappletHost` builds one per account with the gateways now shared in
  commons (`AccountNappletRelayGateway`, `AccountNappletWalletGateway`, `NappletResourceFetcher`,
  `AccountIdentityReader`, `DataStoreNappletStorage`) and the persisted
  `DataStoreNappletPermissionStore` ledger. Live subscriptions use the now-generic
  `NappletLiveSubscriptions`. Windows close when their launching account is no longer logged in.
- **Consent:** `NappletConsentCard` (commonsUI, now also Android's dialog body) in a separate
  `DialogWindow`, so Chromium's heavyweight view can't cover it; text from the shared
  `NappletConsentSummary`.
- **Theme:** `theme.get` and `theme.changed` follow the app's Material colors.
- **Tests:** `DesktopNappletSessionTest`: a tampered blob never loads; with `AMETHYST_CEF_TEST=1`
  under a display, a probe napplet's direct call to the bridge from its iframe is refused while
  the shim's storage round trip works and its `fetch` never reaches the server, and the Doom
  napplet boots and asks for its saves.

Open:

- nSites (NIP-5A website posture: real origin, NIP-07) are not hosted on desktop yet.
- Not offered: upload, notify, keys.onAction, the inc bus (not in Amethyst's supported domains).
- No foreground hold (`NappletHeldRequests`) or `identity.changed` push yet; acting requests are
  still consent-gated.
- Native Wayland (JBR's `WaylandWindow`) is unverified: CEF's windowed mode is X11.
- macOS: the CEF bundle downloads unsigned into the data dir; notarized builds may need it signed
  or shipped inside the app. Windows is untested.

## Status

Shared core (broker, protocol, codec, resolver) is ready and unit-tested. The codec now lives in
`commons/jvmAndroid` specifically so this desktop host can consume it. The remaining work is the
desktop **edge** (engine + scheme handler + transport + gateways + UI) plus the three recommended
extractions above — and on-device/desktop verification of the whole round-trip.
