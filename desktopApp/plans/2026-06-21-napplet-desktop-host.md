# Desktop napplet / nsite host — implementation plan

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
- **`NappletProtocolJson`** (`commons/jvmAndroid`) — the wire codec. Desktop's host marshals through
  the same object, so request/result/push shapes can never drift between platforms.

**The web contract (`amethyst/assets/napplet/`)** — `shell.html` (the trusted shell page that hosts
the applet in an opaque-origin `sandbox="allow-scripts"` iframe and bridges object↔string) and
`shim.js` (the injected `window.napplet.*`). **These must be reused byte-for-byte** — they embody
the envelope/push contract. See "Shared web assets" below for how to share them.

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
- **Shared web assets.** Move `shell.html` + `shim.js` into `commons/commonMain/composeResources/files/napplet/`
  and read them via the generated `Res.readBytes("files/napplet/...")` on both platforms (the
  Material Symbols font already lives in commons composeResources). This makes the web contract
  single-sourced. (Android's host then reads them via `Res` instead of `assets.open`.)
- **The inert feed card.** `RenderStaticWebsite` (+ the napplet/nsite branches) in
  `amethyst/.../note/types/StaticWebsite.kt` is plain Compose; move it to `commons` so the desktop
  feed renders the identical card and launches the desktop host.

## Security parity checklist (desktop must match Android)

- Opaque-origin `sandbox="allow-scripts"` iframe (no `allow-same-origin`).
- CSP `connect-src 'none'` (applet has no direct network) + `default-src` locked to the internal origin.
- Serve **only** the manifest's declared paths, each **sha256-verified** before serving (reuse `StaticSiteResolver`).
- The web engine/renderer holds **no keys**; all signing/consent stays in the broker.
- Capability **declaration gate** + consent + signer-aware deferral (all already in `NappletBroker`).
- Foreground-only execution (pause the engine when the pane is hidden) — mirror Android's `onPause`.
- Route external links to the system browser only on a user gesture; never navigate the sandbox away.

## Status

Shared core (broker, protocol, codec, resolver) is ready and unit-tested. The codec now lives in
`commons/jvmAndroid` specifically so this desktop host can consume it. The remaining work is the
desktop **edge** (engine + scheme handler + transport + gateways + UI) plus the three recommended
extractions above — and on-device/desktop verification of the whole round-trip.
