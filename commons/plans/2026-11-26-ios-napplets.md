# iOS napplets and nSites (NIP-5D, NIP-5A) on WKWebView, with Tor

Status: N0–N7 done, 2026-11-26: N4.5 is the shared session core with Desktop moved onto it, N5
runs NIP-5D napplets on iOS, N6 runs NIP-5A nSites (on Tor or the open web) and webxdc apps, and N7
runs them inside notes, shows the front page, adds files, uploads, the report menu and a SOCKS router
per store (what changed from §5 while doing them: §5.7; the core: §7b; N5: §7c; N6: §7d; N7: §7e).
What is left is Android's move onto the core, on its own branch (host-core review §8).
Branch `feat/ios-napplets`.
Builds on `commons/plans/2026-10-09-ios-app.md` §B9 and `commons/plans/2026-11-25-ios-tor.md`.
Security baseline: `commons/plans/2026-10-31-embed-security-review.md`,
`amethyst/plans/archive/2026-06-22-napplet-nsite-security.md`.

**Minimum iOS is 17.0** (maintainer decision, 2026-11-26; `iosApp/xcode/project.yml`
`deploymentTarget`). This plan therefore treats `WKWebsiteDataStore.proxyConfigurations`
and `WKWebsiteDataStore(forIdentifier:)` as always present and has no iOS 16 branch.

## 1. Goal

Run NIP-5D napplets, NIP-5A nSites, webxdc apps and papers in the iOS app with the same
sandbox, consent broker and Tor routing that Android (`:nappletHost`) and Desktop (CEF) have:
the same shell page, shim, CSPs and broker bytes from `commons`/`commonsUI`, and only the
browser glue native to iOS.

## 2. Spike (N0): what was measured

### 2.1 Harness

A throwaway Kotlin/Native harness drove `WKWebView` inside the real app on the simulator. It is
not committed. It lived in `iosApp/src/iosMain/.../napplet/spike/NappletSpike.kt`, reached
only with the launch argument `-AmethystNappletSpike`, which returns before `IosAppModules`
is built, so no account loaded and nothing was signed or published. It used:

- a `WKURLSchemeHandler` for scheme `napplet` that served every page from memory and logged
  each request;
- a `WKScriptMessageHandlerWithReply` named `spike` in the page world, and a
  `WKScriptMessageHandler` named `iso` in a named content world `napplet`;
- three `WKUserScript`s at document start: the bridge object (main frame only, page world),
  `NappletWebContract.WEBRTC_GUARD_JS` (all frames, page world), and a probe in the
  `napplet` world (all frames);
- Arti from `ArtiNative` (in process, SOCKS on 127.0.0.1:17400), and
  `ArtiNative.socksAcceptedCount()` to count the connections that reached the SOCKS port.

Environment: Xcode 27.1, simulator "iPhone 18 Pro" (F3961A2A-…), Kotlin 2.4.21. Runtimes on the
machine: iOS 17.4, 17.5, 18.0, 18.6, 26.2, 26.4, 26.5, 27.0, 27.1 (`xcrun simctl list runtimes`).
The first two runs used that simulator's runtime, **iOS 27.0**. The same harness was then run
on **iOS 17.4**, the new floor (simulator "iPhone 15 Pro", CA14C3C7-…, fresh, no account), from
a separate worktree at 4fe484ed804. Every Q1–Q5 result below holds on both runtimes unless a
row says otherwise; §2.3 lists the differences.

Two Kotlin/Native traps the harness hit, which the real code must avoid:
- `NSLog("…%@", kotlinString)` segfaults (`objc_opt_respondsToSelector` inside
  `_NSDescriptionWithStringProxyFunc`): a Kotlin `String` passed through C varargs is not an
  `NSString`. Log through Quartz `Log` (the real code does anyway) or pass a format with no
  arguments.
- `WKURLSchemeHandlerProtocol` declares `webView(_:startURLSchemeTask:)` and
  `webView(_:stopURLSchemeTask:)`, which collide in Kotlin. Annotate both overrides with
  `@ObjCSignatureOverride`, as `playback/cache/VideoResourceLoader.kt` already does.

### 2.2 Results

Every row below is a log line from the simulator (an `NSPIKE` line). "Shell" is
`napplet://napplet.local/__shell__`; "site A" and "site B" are `napplet://nabc.napplet.local/site`
and `napplet://ndef.napplet.local/site` in `sandbox="allow-scripts allow-same-origin"` iframes;
"srcdoc" is a `sandbox="allow-scripts"` iframe whose `srcdoc` was assigned exactly as
`NappletWebContract.shell` does (base64, `atob`, `TextDecoder`), with `APP_CSP` as its first
`<meta>`.

**Q1. A custom scheme gives everything https gives us.**

| Check | Shell | srcdoc (opaque) | Site A / B (custom-scheme subdomain) |
|---|---|---|---|
| `location.origin` | `napplet://napplet.local` | `null` | `napplet://nabc.napplet.local`, `napplet://ndef.napplet.local` |
| `isSecureContext` | true | true | true |
| `crypto.subtle` + a SHA-256 digest | ok | ok | ok |
| `parent.document` | n/a | SecurityError | SecurityError |
| localStorage | n/a | SecurityError (opaque origin, as in Chrome) | A writes `secret`, B reads `null` |
| IndexedDB | n/a | SecurityError | 27.0: A lists `[spike-cspHeader, spike-siteA]` (both nabc), B lists only `[spike-siteB]`. 17.4: `indexedDB.databases()` returns `[]` everywhere, so a record probe was used instead: A wrote `k` into a new `shared-probe` db, the same-origin cspHeader page found it existing (no upgrade), and B got a fresh db (upgrade fired) with no record |
| MessageChannel hello → ping → pong | n/a | ok | ok |

- So each subdomain of a custom scheme is its own origin, with its own storage, and a secure
  context. Nothing else is needed (no `https` interception, no fake TLS).
- **CSP from the scheme handler's `NSHTTPURLResponse` headers is enforced.**
  - Shell header `frame-src napplet://nabc.napplet.local napplet://ndef.napplet.local …`:
    a third iframe on `nzzz` was refused (`securitypolicyviolation frame-src
    napplet://nzzz.napplet.local`), and the handler never saw a request for it.
  - A site page served with header `connect-src 'self'`: `fetch('https://example.com')` was
    blocked, `fetch('napplet://napplet.local/leak')` was blocked, and `fetch(location.origin +
    '/self')` was allowed (`ok basic`). So `'self'` matches the custom-scheme origin, and
    `WEBXDC_CSP` works unchanged.
  - The srcdoc's `<meta>` `APP_CSP` (`connect-src 'none'`) blocked https, the shell scheme and
    everything else. The handler saw none of those requests.
- Requests the handler receives carry **no `Origin` header** (`origin=null` on every one), but
  they do carry `Referer` and `mainDocumentURL`. The handler cannot tell who asked, so it must
  route by URL host alone, and serve only this launch's hosts (see §4.2).
- A site with no `connect-src` reached the shell host with a no-cors fetch (`ok opaque`, the
  handler served it). Android has the same property. It is harmless only if the handler
  serves nothing secret there: it serves the shell only on a top-level main-frame
  navigation (§4.2).

**Q2. The bridge.** Both handler kinds work. The choice is about what other frames can see.

- With the handler in the **page world**, `window.webkit.messageHandlers.spike` is an object
  in **every** frame: the srcdoc, both sites, and their nested frames. Each frame could post
  to it (`direct-srcdoc`, `direct-siteA` arrived). Native sees the sender in
  `message.frameInfo`: `isMainFrame=false` and `securityOrigin` = `://` (opaque) or
  `napplet://nabc.napplet.local`. Checking those fields is enough to drop the messages, but
  every frame can still knock.
- With the handler in a **named content world** (`WKContentWorld.worldWithName("napplet")`),
  `window.webkit.messageHandlers.iso` is `undefined` in the page world of **every** frame,
  the shell included. Only scripts in that world reach it. The page world gets to it
  through the DOM: the shell's page script dispatched
  `document.dispatchEvent(new CustomEvent('napplet-out', {detail}))`, and a `napplet`-world user
  script (main frame only) relayed `detail` to native, which logged `event detail=from-page-world`.
  A cross-origin child cannot dispatch events on the shell's document.
- Reply path: `WKScriptMessageHandlerWithReply`'s promise resolved in the page
  (`replyValue = echo-reply`). Native → page: `evaluateJavaScript(…, inFrame = null,
  inContentWorld = pageWorld)` called `window.__nappletBridge.onmessage({data: json})`, and the
  shell received `{"type":"pong-from-native"}`. That is exactly the shape `shell.html`
  expects.

**Q3. The WebRTC guard reaches every frame.** `WEBRTC_GUARD_JS` was a page-world
`WKUserScript` at document start with `forMainFrameOnly = false`. It ran in every frame
(`guard:1`), and every frame reported `typeof RTCPeerConnection === 'undefined'` and
`typeof RTCDataChannel === 'undefined'`: the shell, the srcdoc, both sites, the cspHeader
page, and the frames those pages made themselves (`about:blank` with no src, `about:srcdoc`,
and a same-origin `/inner`). The same-origin `/inner` frame counts too.

The parent cannot inspect frames nested inside the opaque srcdoc (SecurityError, both
sandboxed), so run 2 had each nested `srcdoc` frame post its own result straight to the
page-world handler. Each one, inside the srcdoc and inside sites A, B and cspHeader, reported
`{"rtc":"undefined","guard":1}`, with `frameInfo.isMainFrame=false`. Every one of those
frames also ran the `napplet`-world user script (`iso … frameUrl=about:blank` and
`about:srcdoc` with `secOrigin=://`), which shows that WebKit injects user scripts into
`about:blank`/`about:srcdoc` frames inside sandboxed parents.

**Q4. Tor through `WKWebsiteDataStore.proxyConfigurations`.** The property is in the K/N WebKit
klib as a typed `var proxyConfigurations: List<*>?`, so no KVC is needed.
`nw_proxy_config_create_socksv5(nw_endpoint_create_host("127.0.0.1", port))` comes from
`platform.Network`. `respondsToSelector("setProxyConfigurations:")` = true.

| Store | Top-level GET `https://check.torproject.org/api/ip` | SOCKS connections |
|---|---|---|
| `dataStoreForIdentifier(uuid)` + SOCKS proxy | `IsTor=true` | +2 (17.4: +1) |
| `nonPersistentDataStore()` + SOCKS proxy | `IsTor=true` | +2 (17.4: +1) |
| `nonPersistentDataStore()`, no proxy | `IsTor=false` | +0 |

These were three WKWebViews in one process, each with its own store, so **the proxy is per
data store**: a napplet's store and a site's store can differ, at the same time.

Sub-resources from a page served by the scheme handler (`napplet://nnet.napplet.local/net`), one
step at a time, each to a different host so that connection reuse can't hide a step:

| Step | Proxied store | Direct store |
|---|---|---|
| `fetch` no-cors to www.torproject.org | +1 | +0 |
| `<img>` from check.torproject.org | +1 | +0 |
| `WebSocket` wss://support.torproject.org | +1 | +0 |
| `<video src>` from blog.torproject.org (media loader) | +1 | +0 |
| `<img>` from an `http://….onion` | +1 | +0 |

So fetch, images, **WebSocket and media** all honour the store's proxy. The `.onion` load
reached the proxy, which means the host name was handed to the proxy rather than resolved
locally (an onion name can't be resolved locally). The image itself still reported `error`.
That is expected: either the site has no `/favicon.ico`, or WebKit upgrades or blocks `http:`
sub-resources on a secure page. Either way it doesn't matter for napplets. It does matter for
an nSite that calls an `http://*.onion` API (open question O3).

**Failing closed (run 2).** With Arti's SOCKS listener stopped (`stopSocksProxy() = 0`), a
store still pointed at the dead port failed the load with `NSURLErrorDomain -1004 Could not
connect to the server`. There was no `IsTor=false` and no direct fallback, and the counter
stayed at +0. The nw proxy config has no failover unless it is asked for.

**Re-pointing a live store.** After the listener was restarted on 17401, setting
`proxyConfigurations` again on the **same** store object and loading again gave `IsTor=true`
(+1). So a port change can be applied to an open session's store without rebuilding it.

The scheme-served page itself loaded in the proxied store without touching the proxy (the
handler logged it, and no SOCKS connection was counted before the first step).

Both runtimes behaved the same for every sub-resource row, the dead port (`-1004`, +0) and the
re-pointed live store (`IsTor=true`).

Cleanup: `removeDataStoreForIdentifier` failed with "Data store is in use" while a WKWebView on
it was still alive. Deleting a per-account store (logout, "clear site data") must first tear
down every WKWebView on it.

**Q5. Scheme-handled loads never reach the network on their own.** Every `napplet://` request
reached the handler (`scheme-request …`), proxied store or not, and none of them moved the
SOCKS counter. WebKit has no other path for them: the bytes the page gets are the bytes the
handler hands it. So blob fetching stays ours (Ktor, with Tor chosen per launch, §6),
exactly as on Android, where `shouldInterceptRequest` plays that role.

### 2.3 iOS 17.4 vs iOS 27.0

Same harness, same checks; only the simulator runtime differs.

| Question | 17.4 vs 27.0 | Evidence on 17.4 |
|---|---|---|
| Q1 secure context, `crypto.subtle` | same | `secure:true`, digest `ok` in the shell, srcdoc and sites |
| Q1 origins per subdomain | same | `napplet://nabc…`, `napplet://ndef…`, srcdoc `null` |
| Q1 localStorage isolation | same | A `lsWrite=siteA`, B `lsRead=null`; srcdoc SecurityError |
| Q1 IndexedDB isolation | same result, different probe | `indexedDB.databases()` returns `[]` on 17.4 (it listed names on 27.0); the record probe shows isolation (A `fresh:true wrote`, same-origin `fresh:false`, B `fresh:true value:none`) |
| Q1 header CSP / meta CSP / `'self'` / `frame-src` | same | cspHeader: https + shell blocked, self `ok basic`; srcdoc: all blocked; `frame-src napplet://nzzz…` violation and no handler request for nzzz |
| Q1 MessageChannel | same | `pong ping` from srcdoc, A, B, cspHeader |
| Q2 bridge (reply, native→page, page vs named world, `frameInfo`) | same | `replyValue=echo-reply`, `nativeToPage pong-from-native`, `iso` undefined in every page world, `event detail=from-page-world`, children seen as `main=false` with their origins |
| Q3 WebRTC guard in every frame | same | `rtc:"undefined", guard:1` in the shell, srcdoc, sites, nested about:blank/srcdoc/same-origin, and `selfNested-*` inside the opaque srcdoc |
| Q4 proxy per store, IsTor | same (SOCKS +1 instead of +2 for the top-level check) | `IsTor=true` for the persistent and non-persistent proxied stores, `IsTor=false` direct |
| Q4 fetch / img / WebSocket / media / onion | same | proxied +1 each, direct +0 each |
| Q4 dead proxy, re-point | same | `-1004`, +0; re-pointed `IsTor=true` |
| Q5 scheme loads stay local | same | every `napplet://` request reached the handler, and the counter did not move for them |

The extra connection on 27.0's top-level check is not a routing difference: the direct store
was +0 on both. It is probably a second connection (a favicon, or a speculative preconnect) that
17.4 does not open.

**O2 answered (17.4).** WKWebView runs inside the bare `iosSimulatorArm64Test` executable
when the test runs on a booted simulator (`-Pamethyst.ios.testSimulator=<udid>`, so
`standalone=false`): `loadHTMLString` + `evaluateJavaScript` returned `"42"` after 1.2 s, with the test
pumping `NSRunLoop.mainRunLoop` on the main thread (`isMainThread=true`). A custom scheme
handler and the message bridge were not tried in the test executable.

## 3. Decisions

1. **Origins.** `napplet://napplet.local` is the shell, and `napplet://<appId>.napplet.local` is
   each site. Webxdc apps go on `napplet://<appId>.xdc.napplet.local`. The host and subdomain
   scheme are the ones Android and Desktop already use, with a different scheme. A real
   `https` origin is impossible here: Apple documents that `setURLSchemeHandler` raises for
   schemes WebKit handles itself (`https`). The spike did not exercise that.
2. **Bridge in a named content world.** The handler is a `WKScriptMessageHandlerWithReply` in
   `WKContentWorld.worldWithName("amethyst-napplet")`. Its main-frame-only user script in that
   world defines the relay. The shell's page-world `window.__nappletBridge` (installed in the
   shell's `<head>` through `ShellPage`'s `head` parameter, as Desktop already does) posts
   through a DOM `CustomEvent`. Native still checks `frameInfo.isMainFrame` and
   `securityOrigin == napplet://napplet.local` on every message: that is defence in depth,
   not the only check. Native → page uses `evaluateJavaScript` in the page world on the main
   frame.
   - Why not the page world: every frame, hostile ones included, would see the handler and could
     call it. Native would drop the calls, but each one is a message to parse and drop.
3. **Guards.** `WEBRTC_GUARD_JS` (and `COOKIE_GUARD_JS`) are added as page-world document-start
   user scripts with `forMainFrameOnly = false`, on every napplet/webxdc configuration. As on
   Android, the host refuses to run a profile that needs them if they can't be installed. On
   iOS they always can be, but the check stays in the code.
4. **Tor.** Each launch gets a WKWebView whose data store has the proxy set or not, decided
   once, before load (§6).
5. **iOS 17.0 minimum.** No legacy branch.

## 4. iOS host design

### 4.1 Session (`IosNappletSession`, iosApp)

One per open app. It owns:
- the `WKWebViewConfiguration`: data store (§4.5), scheme handler, user scripts, bridge, and
  `preferences.javaScriptCanOpenWindowsAutomatically = false`;
- the `NappletSiteServer` for the launch;
- the `NappletRequestRouter` (decode → broker → encode), the live subscriptions and the INC
  endpoint;
- a `WKNavigationDelegate` + `WKUIDelegate` (§4.4).

It mirrors `CefNappletSession` (desktop) and `NappletHostActivity` (Android). No separate
process is needed: WKWebView's web content already runs out of process, and on iOS the
broker lives with the account, as on Desktop.

### 4.2 Scheme handler (`NappletSchemeHandler`)

- It is registered for `napplet` on this session's configuration only, and knows only this
  launch's `appOrigin`.
- `napplet.local` + `/__shell__` → `ShellPage(html, csp)` with
  `Content-Security-Policy: shellCsp(appOrigin)`. It serves this only for the main-frame
  navigation (`request.mainDocumentURL == request.URL`). Any other request for the shell host
  gets a 404, so a site's no-cors fetch reads nothing.
- `appOrigin`'s host → `NappletSiteServer.serve(path, …)`. Every other `*.napplet.local`
  host → 404.
- The response is an `NSHTTPURLResponse` with `Content-Type`, the profile's CSP, `Cache-Control:
  no-store`, and `X-Content-Type-Options: nosniff`. It never sends a `Set-Cookie`.
- Bodies stream: `didReceiveData` in chunks read from the `NappletSiteServer.Response` source
  (§5.1), on a background coroutine. Each callback is marshalled back to the main thread,
  because WKURLSchemeTask must be called on the thread it arrived on.
- `stopURLSchemeTask` cancels the coroutine. Track the stopped tasks: calling a task after
  stop raises an Objective-C exception.

### 4.3 Bridge

The shell's `head` script (iOS variant of desktop's `DesktopBrowserBridge` head):
`window.__nappletBridge = {postMessage(s){document.dispatchEvent(new CustomEvent('amethyst-napplet-out',{detail:String(s)}))}, onmessage:null}`.
The isolated-world script listens for that event and calls
`webkit.messageHandlers.amethystNapplet.postMessage(detail)`. Replies and pushes come back
through `evaluateJavaScript("window.__nappletBridge.onmessage({data: …})")`, with the JSON
string-escaped by Kotlin (a JSON string literal of the JSON). `shell.html` does not change: it already reads `window.__nappletBridge` and assigns `onmessage`.

An open spike point to confirm in N2: the isolated world's event listener must not be
spoofable by a child, and it can't be, because a child can't reach the shell's `document`.
Also confirm that a page-world shell script can't remove the listener (it lives in another
world, so it can't).

### 4.4 Navigation policy, links and hardening

`decidePolicyForNavigationAction`:
- allow `napplet://` URLs whose host `NappletWebContract.isInternalHost` accepts, plus
  `about:blank` and `about:srcdoc`;
- for a NIP-5A site with `exposesNetwork`, allow its sub-frame navigations to http(s) (the
  same as Android, where sites can frame the web);
- cancel everything else. When `profile.opensLinks` is set, the URL is http(s), the
  navigation targets the main frame or a new window, and `navigationType == linkActivated`
  (iOS's user-gesture signal; `WKNavigationAction.navigationType`), open it with
  `UIApplication.openURL` in the system browser, or in the in-app browser screen when it
  exists. This matches Android's `request.hasGesture()` rule;
- `createWebViewWithConfiguration` (window.open, target=_blank) applies the same link rule
  and always returns null.

Hardening:
- `allowsLinkPreview = false` and `allowsBackForwardNavigationGestures = false` for napplets;
- no `UIDelegate` media-capture grant. `requestMediaCapturePermissionFor…` answers `.deny`
  (Android denies `onPermissionRequest` for napplets);
- no geolocation: `NSLocationWhenInUseUsageDescription` is absent, so WebKit cannot ask;
- downloads: `navigationResponse` cancels anything that isn't rendered inline (no
  `WKDownload`);
- `isInspectable` only in Debug builds;
- `limitsNavigationsToAppBoundDomains` stays off: it would block our own scheme in subframes,
  and the policy above already covers it;
- `WKWebpagePreferences.allowsContentJavaScript = false` for PAPER (no scripts), on top of its
  CSP.

### 4.5 Data stores per account and per network

The shell, sites and napplets of one account share a store, so a napplet's storage
persists across launches (`appId` is already partitioned by account where needed). Two
accounts never share one.
- Store id: `NSUUID` derived from `sha256("napplet-store:" + accountPubKey + ":" + net)`
  (formatted as a v4-shaped UUID), with `net ∈ {direct, tor}`. Keeping Tor and clearnet stores
  apart stops a site from linking its Tor visits to its clearnet ones through storage. N6 checks
  how Android partitions WebView storage per account and per network and matches it. This plan
  did not verify Android's rule.
- PAPER and any one-shot view: `nonPersistentDataStore()`.
- Logout: close the sessions, then `removeDataStoreForIdentifier` for both of the account's
  stores.

### 4.6 Screen, launcher, consent

- `Route.NostrApp`: register `IosNostrAppScreen(route, nav, host)` in `IosAppRoot`, as
  `DesktopAppRoot` does. It is a `UIKitView` wrapping the session's WKWebView. Back closes it.
  The title bar comes from the shared chrome.
- `IosAppPlatform.rememberAppLauncher()`: `opensNapplets = true`. `openNapplet`, `openSite` and
  `openWebxdc` build a `NappletLaunch` and push `Route.NostrApp`. `runsNappletsInline = false`
  at first; inline (`InlineNappletSurface`) comes later (N7).
- Consent: the broker's `NappletPrompts` is a `NappletPromptQueues`. The screen shows the
  shared `NappletConsentCard`s from commonsUI over the web view, the same way Desktop's
  `NostrAppScreen` does. No iOS-specific dialog.
- `IosNappletHost` (iosApp, one per process) holds the per-account broker
  (`buildAccountNappletBroker`), `NsiteNetworkChoices`, `DataStoreNappletStorage`, the ledgers
  (`nappletPermissionLedger` already exists in `IosAppModules`), and the INC bus. It mirrors
  `DesktopNappletHost`.

## 5. What moves to commonMain (and how Android and Desktop keep working)

Rule: **behaviour does not change on Android or Desktop.** Every move keeps the JVM path's
classes, and the JVM tests run unchanged. HTTP goes behind a small port, not through Ktor
on the JVM.

### 5.1 `NappletSiteServer` (jvmAndroid → commonMain)

- Its `Response.source: () -> InputStream` becomes `() -> okio.Source` (okio is already `api` in
  commons commonMain). `readBody()` keeps its name.
- Android's `WebResourceResponse` and Desktop's CEF handler take an `InputStream`: add a
  jvmAndroid extension `Response.openStream(): InputStream = open().buffer().inputStream()`.
  Both hosts change only that one call.
- `SequenceInputStream`/`ByteArrayInputStream` uses become an okio `Buffer`, or a small
  concatenating `Source` (prelude + file).
- `NappletFiles`, `PaperFiles` follow it (they have no JVM imports today).

### 5.2 Blobs: `NappletBlobStore`, `NappletBlobCache`, `NappletBlobHttp`, `NappletBlobPrefetcher`

- `java.io.File` → okio `FileSystem` + `Path` (`FileSystem.SYSTEM` on all three platforms). The
  atomic temp-file-then-rename write becomes `FileSystem.atomicMove`. Check the `Boolean`
  results the way AGENTS.md asks (okio throws, which is fine).
- `ConcurrentHashMap` → a `KmpLock` (`commons.util.KmpLock`, already used across commonMain)
  around a `HashMap`. `AtomicLong` → `kotlin.concurrent.atomics.AtomicLong`.
- HTTP: a new commonMain port

  ```kotlin
  interface NappletHttp {
      /** GET [url]; null on 404. Streams; enforces maxBytes; throws on transport error. */
      suspend fun get(url: String, viaTor: Boolean, maxBytes: Long, timeoutMs: Long): NappletHttpBody?
  }
  ```

  It has two implementations:
  - `OkHttpNappletHttp` (jvmAndroid): the body of today's `NappletBlobHttp`, unchanged, with
    the SOCKS `Proxy`, the public-only `Dns` and no `CookieJar`;
  - `KtorNappletHttp` (iosMain in commons): `IRoleBasedHttpClientBuilder`'s Tor/direct Ktor
    clients, with no cookies and redirects capped.
  - **Public-only DNS on iOS:** NSURLSession has no resolver hook. Before a direct
    (non-Tor) request, resolve with `getaddrinfo` and refuse private, loopback and
    link-local results. Over Tor the exit resolves, so this isn't needed (see the
    phone-SSRF memory for why this is defence in depth, not a hard boundary).
- `NappletBlobPrefetcher` uses `toHttpUrlOrNull`: replace it with quartz's/commons' URL
  normalizer (`Rfc3986`), or keep it in jvmAndroid with the prefetch disabled on iOS at first.

### 5.3 `NappletLiveSubscriptions`, `NappletIncBus`, `NappletIntentDispatcher`

- `synchronized(lock)` → `KmpLock.withLock`. `WeakHashMap` → a plain map keyed by session id,
  removed on session close: iOS has no weak map, and the hosts already close sessions
  explicitly. `Collections`/`AtomicBoolean`/`AtomicInteger` → kotlin atomics.
- No API changes, so the Android and Desktop call sites stay as they are.

### 5.4 `NappletResourceFetcher` and the upload gateway

- `NappletResourceFetcher` (537 lines) is the largest. Its OkHttp use (streaming, a byte cap,
  a `CodingErrorAction` charset check, a `Base64` encoder, `InetAddress` checks) goes behind
  the same `NappletHttp` port, plus a `NappletHttpBody` that exposes `contentType`, `length`
  and a `Source`. Base64 → `kotlin.io.encoding.Base64`. The charset check → `decodeToString`
  with `throwOnInvalidSequence = true`. The `InetAddress` public-address check → the
  iOS `getaddrinfo` check above behind an `expect fun isPublicAddress`.
- `AccountNappletUploadGateway` takes `(Boolean) -> OkHttpClient` only to hand it to Blossom.
  Point it at the existing `IRoleBasedHttpClientBuilder` uploads role instead (iOS has
  `ktorClientForUploads`).

### 5.5 `buildAccountNappletBroker` (commonsUI jvmAndroid → commonMain)

- `httpClient: (Boolean) -> OkHttpClient` becomes `http: NappletHttp` (plus the upload role).
  Android and Desktop pass `OkHttpNappletHttp(okHttpClients::getHttpClient)`; iOS passes
  `KtorNappletHttp(roleBasedClients)`.

### 5.6 `NappletWebContract`: scheme as a parameter, with the same defaults

Today `ORIGIN`/`SHELL_URL`/`appOrigin` hardcode `https://`. Add:

```kotlin
class NappletOrigins(val scheme: String) {           // "https" on Android/Desktop, "napplet" on iOS
    val origin = "$scheme://$HOST"
    val shellUrl = "$origin/__shell__"
    fun appOrigin(appId: String, profile: HostProfile = HostProfile.WEBSITE) = …
}
object NappletWebContract {
    val HTTPS = NappletOrigins("https")
    const val ORIGIN = "https://napplet.local"      // kept: = HTTPS.origin
    const val SHELL_URL = "$ORIGIN/__shell__"         // kept
    fun appOrigin(appId, profile) = HTTPS.appOrigin(appId, profile)   // kept
    …
}
```

- Android and Desktop keep calling the existing members, which are byte-identical. A unit
  test pins `HTTPS.origin == ORIGIN` and the same for `appOrigin`.
- `shell(...)`, `shellCsp(frameSource)` and `isInternalHost(host)` are host-only or take the
  origin as an argument already, so they don't change. `NappletSiteServer` already takes
  `appOrigin`.
- The other hardcoded uses (grepped): `NappletWebContract.appOrigin`'s `https://` for both
  profiles, and `NappletSiteServer.serve`, which compares against `SHELL_URL`/`ORIGIN`
  (lines ~131-136). `NappletSiteServer` gains `origins: NappletOrigins =
  NappletWebContract.HTTPS`, and iOS passes `NappletOrigins("napplet")`.
  `NappletResourceFetcher` line ~261 prefixes `https://` onto *servers*: that is the network,
  not our origin, so it stays.
- `shim.js` and `shell.html` contain no scheme or `napplet.local` checks (grepped), so neither
  changes.

### 5.7 As built (N1–N4)

- `NappletFiles`/`PaperFiles` moved with the blobs in N3, not N2: they return `NappletBlobStore.Blob`.
- The port is `NappletHttp.get(NappletHttpRequest): NappletHttpResponse`. The request carries `url`,
  `viaTor`, `maxBytes`, `timeoutMs`, `followRedirects` and `publicOnly`. The response holds the
  status, `location`, the body as bytes (a 2xx one within the cap), `tooLarge` and `received`.
  Timeouts throw `NappletHttpTimeoutException`, refused addresses `NappletHttpBlockedException`. Bodies
  come back whole rather than as a `Source`: every caller reads the whole capped body anyway.
- `OkHttpNappletHttp` (jvmAndroid) runs a plain request on the given client as it is, and a request
  that is `publicOnly` or doesn't follow redirects on the fetcher's hardened copy. `KtorNappletHttp`
  (nonJvmMain) follows redirects itself, hop by hop. `iosNappletHttp(DualKtorClients)` (iosMain) adds the
  `getaddrinfo` check.
- `NappletPublicAddress` (commonMain, raw bytes) is pinned to the JVM's `NappletInetAddresses` by a
  jvmTest.
- `NappletBlobPrefetcher` stays in jvmAndroid on `NappletBlobHttp`. iOS has no prefetch yet.
- Gaps against the JVM, in `KtorNappletHttp`'s KDoc: the DNS check runs before NSURLSession resolves
  again (rebinding window), and there is no check of the address actually connected to. In the
  other direction, the JVM fetcher's hardened client never checks an IP literal (OkHttp asks no `Dns`
  for one), so `https://192.168.1.1/` passes there and is refused on iOS. That was left as it was
  (behaviour must not change on Android/Desktop); it is worth a separate fix.
- `IosNappletHost` (iosApp `napplet/`, `IosAppModules.nappletHost`, lazy) holds the broker per account,
  `NappletPromptQueues`, `NsiteNetworkChoices`, storage, ledgers, the blob cache (Caches) and the
  `NappletHttp`. It offers no upload or notify domain yet. The INC bus, live subscriptions and
  attendance are typed to the iOS session, so they come with N5.

## 6. Tor

The decision is the one Android's `NappletLauncher` makes:

```
torEnabled = torPrefs.torType != OFF
useTor = torEnabled && (profile.exposesNetwork
            ? NsiteNetworkChoices.useTor(coordinate)            // per-site, falls back to Tor while loading
            : torPrefs.appletsAndWebsitesViaTor)
```

`NsiteNetworkChoices` is commonMain already. iOS builds one on
`appStores.getDataStore("napplet_network")`, with `appletsAndWebsitesViaTor` as its default,
and awaits `awaitReady()` before deciding, as `NsiteNetworkRegistry` does.

Two different things are routed:
1. **Blob and resource fetches** (ours, through Ktor): `NappletHttp.get(viaTor = useTor)`. This
   covers every profile, napplets included (their files are fetched, never loaded from the
   web by WebKit).
2. **The page's own network** (only for profiles where `exposesNetwork`, which means nSites):
   - if `useTor`, the WKWebView's data store gets
     `proxyConfigurations = [socksv5(127.0.0.1:torManager.activePortOrNull)]`;
   - otherwise there is no proxy.
   - Napplets and webxdc apps have `connect-src 'none'`/`'self'`, so a proxy doesn't matter
     for them. Still, give them the same store choice so that nothing depends on the CSP alone.

Fail closed:
- `useTor` and Tor not ready (`!torManager.isTorReady()`, or `activePortOrNull == null`) → show
  the shared "waiting for Tor" state and don't create the web view. Never fall back to the
  direct store. `sessionBypass` (the user's "skip Tor this session") counts as direct, exactly
  as for relays.
- A SOCKS listener that dies mid-session: loads fail (`-1004`, measured) and never go direct.
  The screen shows the network error and offers a retry once Tor is back.
- A listener that restarts on another port (`listenerRestarts`): re-point the open
  sessions' stores. Re-pointing a live store was measured to work. Requests already in
  flight on the old port fail, and the page sees them as network errors.
- Toggling the per-site choice while the site is open re-creates the session on the other
  store. It never re-points a live store from Tor to direct.

Proxy claims: `NappletProxyClaims` (what the shell tells the applet about its network) is fed
the same `useTor` decision as on Android.

**As built (N6), two corrections to this section:** the session bypass does *not* make a Tor site
direct (Android's launcher and Desktop's router ignore it too: a Tor site without a port fails
closed), and because one account's store is shared by all its sites, the store's route is
arbitrated like Android's process-wide proxy (`NappletRouteArbiter` over `NappletProxyClaims`, Tor
always wins). See §7d.

Media: `<video>`/`<audio>` in a proxied store go through the proxy (Q4). Video that the
**native** player plays (a NAP media request handed to AVPlayer) keeps using the app's
`IosVideoCache`/loopback relay rules from the Tor plan.

## 7. App Store, guideline 4.7

This is paraphrased from memory of the guideline. Re-read the current text of 4.7 before
submission. 4.7 allows HTML5 mini-apps and games when they don't give the code access to native platform
APIs beyond what WebKit already offers, and when the app doesn't sell or distribute them in a
way that bypasses the App Store. The 4.7.2/4.7.3 requirements are that the software is free or
uses in-app purchase for digital goods, and that the operator offers an index of the
mini-apps plus a way to report content.
- **No native APIs.** The bridge exposes Nostr operations (sign, encrypt, publish, query
  relays, the NIP-07-style ones) behind per-capability consent. These are the app's own features
  and not iOS APIs: no camera, contacts, files, location or push. Uploads go through the user's
  own file picker. Write this case down for review.
- **Discovery and reporting.** Napplets are found in feeds and in the app directory, which is
  already an "index". Add a "Report" action on the NostrApp screen (it reuses NIP-56 reports)
  and a way to block an author's apps.
- **Payments.** Napplets can ask for zaps or Cashu through the broker, which goes through the
  app's existing wallet flows. That is the same exposure as zaps in notes, so it adds nothing
  new for review. Do not add a napplet "store" with prices.
- **Code from the network.** Napplets are interpreted web content running in WebKit, which
  4.7 / 2.5.2 accepts. Nothing downloads native code.

## 7a. Host review and the shared session core (decided 2026-11-26)

`2026-11-26-napplet-host-core.md` reviews Android's and Desktop's hosts against this plan: a
security-layer checklist with the iOS counterpart of each protection, the engine-independent
logic the two hosts duplicate, and a `NappletSessionCore` + `NappletWebEngine` port proposal.
Decisions taken from it:

- **Order:** after N4, extract the shared policies and `NappletSessionCore` (commonTest with a
  fake engine, JVM + simulator), move **Desktop** onto it first (checked by hand), write the
  iOS host (N5-N7) against the core from day one, and move **Android** last, on its own branch,
  merged only after a device test.
- **Off-origin requests on iOS** never reach the scheme handler, so the CSP alone is not
  enough: install a per-profile `WKContentRuleList` built from the shared request policy, the
  counterpart of Android's 404 and Desktop's cancel handler.
- **WebRTC guard** wherever Android's `NappletWebRtcGuard.isNeeded` says (napplets, webxdc and a
  WEBSITE on Tor), plus Desktop's in-shell refusal to run without it.
- **Hello origin:** the shared `shell.html` checks `e.origin` against the app origin for sites
  and webxdc, not just `e.source` (defence in depth: the shell's `frame-src` already keeps a
  foreign page out of the frame on Chromium); a site's own frame may not navigate off-origin.
- **Attendance:** requests that act for the user are held while the napplet is unattended, as
  Android does; the core owns this for every host.
- **Proxy:** iOS proxies per data store, so `NappletProxyClaims` (one route per process) is not
  used there; §6 is read with that correction.
- **Storage:** split per account, as Android, not per account and network.
- Also from the review: JS dialogs, the web-content-process crash path, back through the page's
  history first, session-grant lifetime, refusing local-network addresses for open-web sites,
  DNS prefetch off, and `NSCameraUsageDescription` for the file picker.

### 7b. As built: the shared core (N4.5, 2026-11-26)

In `commons/commonMain`, package `commons.napplet.session` (no Compose needed):

- `NappletLaunchPlan.of(launch, account, useTor, appletBlobsUseTor, sharedStorageJar, origins)`:
  `appOrigin` (the account is in the seed only for a shared jar, Desktop; iOS passes `false`, as
  Android's origin), `useTor`, `blobsUseTor`, `guards` (`WEBRTC` per Android's `isNeeded`, a WEBSITE
  on Tor included, and `COOKIES`), `entryPath`, `shellHead(bridgeHead)` (puts
  `NappletWebContract.WEBRTC_CHECK` first wherever WEBRTC is needed). `useTorFor(profile, torEnabled,
  siteViaTor)` is the one useTor rule for iOS's launcher.
- `NappletRequestPolicy`: `isInternal(url, origins)`, `classify(...)` → `LOCAL | SERVE | PAPER_ART |
  NETWORK | CANCEL`, and `contentRules(profile, appOrigin, origins)` + `contentRuleListJson(...)`, the
  WKContentRuleList for N5 (block-all then allow our hosts for NAPPLET/WEBXDC; block
  `https?://…napplet.local` for WEBSITE/PAPER).
- `NappletNavigationPolicy.decide(url, frame, userGesture, profile, origins)` → `Allow | Cancel |
  OpenLink(url)`, `popup(...)`, `frameOf(isMain, parentIsMain)` (Chromium) and `frameByOrigin(isMain,
  frameOrigin, appOrigin)` (WebKit: a frame at the app origin is the app's frame and may not leave).
  `NappletBridgeGate.acceptsUrl` / `acceptsOrigin`.
- `NappletPageBridge` (page side): holds acting requests while unattended (EXPIRED / TOO_MANY
  replies, as Android), batches deliveries into one `deliveryScript` per burst, in order.
- `NappletSessionRouting<K>` (trusted side): `routeInc` on the arrival thread, `routeIntent`,
  `route` → reply / push / live subscription, teardown of subscriptions, INC, intents, attendance.
- `NappletSessionCore<K>`: the state machine (`Loading`, `WaitingForTor`, `Showing`,
  `Failed(UNREACHABLE | ENGINE | ROUTE_BLOCKED | RENDERER_GONE)`), fail-closed route, files, server,
  guard refusal, the engine callbacks, `setAttended`, ordered `close()`.
- Ports: `NappletWebEngine` (`installGuard`, `bridgeHead`, `suspend loadShell`, `evaluateInShell`,
  `close`), `NappletRoutePort` (`torRunning`, `claim`, `awaitTor`, `release`), `NappletLinkOpener`.

Desktop's `CefNappletSession` is now the CEF adapter (1,062 → 798 lines). Android is unchanged.
Deviations from the review's §3, and why, are in `2026-11-26-napplet-host-core.md` §5.

### 7c. As built: N5, napplets on iOS (2026-11-26)

`iosApp/.../napplet/`. iOS is a WebKit adapter of `NappletSessionCore`; everything else is shared.

- **`IosNappletWebEngine`** (the `NappletWebEngine`):
  - **Serving.** A `WKURLSchemeHandler` on `napplet` streams `core.serve(...)` in 64 KB chunks,
    calling the task on the main thread and never after `stopURLSchemeTask`. Every request goes
    through `core.classifyRequest`. Each response adds `X-Content-Type-Options: nosniff` and
    `X-DNS-Prefetch-Control: off`.
  - **The shell.** It is served only to the top-frame navigation the engine allowed
    (`shellNavigationPending`, set in `decidePolicyForNavigationAction`). Any other request for it
    gets a 404. §4.2's `mainDocumentURL == URL` test alone would not work: a sub-resource of the
    shell page has the same `mainDocumentURL`.
  - **The bridge.** The handler is a `WKScriptMessageHandlerWithReply` in the
    `amethyst-napplet` content world. A main-frame-only user script in that world relays the
    `amethyst-napplet-out` DOM event that the page-world `bridgeHead()` object fires. The core
    gate is `onShellMessageFromOrigin`. Native-to-page delivery is `evaluateJavaScript` in the
    page world.
  - **Guards.** These are page-world document-start user scripts with `forMainFrameOnly = false`.
    `installGuard` refuses once the web view exists. After the shell loads, `typeof
    RTCPeerConnection` is checked in the shell, and if WebRTC is there the launch fails through
    `onConsoleMessage(WEBRTC_PRESENT)`. WebKit has no console callback to report it otherwise.
  - **Request filter.** The rule list is `NappletRequestPolicy.contentRuleListJson`, compiled into
    a `WKContentRuleList` (named by the JSON's hash, in the default store) before the web view
    exists. If it doesn't compile, `loadShell` throws and the launch shows `Failed(ENGINE)`.
  - **Navigation and popups.** Navigation uses `frameByOrigin`, with `linkActivated` as the user
    gesture. The top frame may hold only the shell URL, not another internal host. `shouldPerformDownload`
    is cancelled, and so is any response that can't be shown inline. Popups call `onPopup` and the
    delegate returns nil.
  - **UI delegate.** Camera, microphone and motion sensor requests are denied. JS
    `alert`/`confirm`/`prompt` show as system alerts titled with the app's name, but only while
    the screen is attended. Otherwise they are answered as cancelled.
  - **Crashes and view settings.** `webViewWebContentProcessDidTerminate` calls `onRendererGone`.
    Link previews, data detectors and swipe navigation are off. `inspectable` is on only in debug
    builds (`Platform.isDebugBinary`). Fraud warnings are on.
  - **Teardown.** `close()` drops the app frame first. It then removes the handlers, scripts and
    rule lists, blanks the view, and detaches the delegates so that no ObjC cycle holds the engine.
- **`IosNappletSession`**:
  - It builds the plan with `sharedStorageJar = false` on `NAPPLET_SCHEME`. Blobs come from
    `NappletBlobStore` through `iosNappletHttp`, with `viaTor = plan.blobsUseTor`.
  - The route port only waits for Tor, up to 30 s (a port, or the session bypass). `claim` fails
    closed until N6. `NappletProxyClaims` is not used (§7a).
  - Links: http(s) opens in Safari through the core's gesture rule. A `nostr:` link (PAPER only)
    goes through `uriToRoute` into the screen's nav.
- **`IosNappletHost`**:
  - It holds attendance, live subscriptions and the INC bus (`NappletSessionServices`). Intents
    are null for now, so they answer `intent unavailable`.
  - It keeps the screens, closes those of an account that logged out, and ends session grants
    when the last screen closes. It also trims the blob cache.
  - **Storage per account.** The data store is
    `WKWebsiteDataStore(forIdentifier: NappletStorageProfile.uuidFor(pubKey))`. That is Android's
    `NappletWebViewProfiles` derivation, moved to commons and pinned by a test vector. This settles
    §4.5: storage is per account, not per network, as on Android.
- **Screen.** `IosNostrAppScreen` mirrors Desktop's `NostrAppScreen` (a ViewModel per back-stack
  entry, the event fetched if missing).
  - Panes: it shows the shared top bar, the WKWebView in a `UIKitView`, and the new shared panes
    `NappletFetchingPane`/`NappletWaitingPane`/`NappletMessagePane` (Desktop's panes now delegate
    to them).
  - Consent: `NappletConsentDialogs` is a `Dialog` with the shared cards (capability,
    connect, signer, batched) behind `ConsentInputGuard`.
  - Attendance is true while the screen is composed and the lifecycle is RESUMED
    (`attendedWhenShown = false`).
  - Back goes through page history (`canGoBack`/`goBack`), then pops.
  - The shared screen with an engine slot (review §3.5) was not done. Desktop's screen is tied to
    CEF in its toolbar, find, zoom, consent windows and engine panes, and sharing it would have
    changed Desktop. The panes and the consent dialog are what is shared.
- **Launcher.** `IosAppPlatform` is now a class over `IosAppModules`. Its launcher has
  `opensNapplets = true` and opens `Route.NostrApp`.
  - New `AppLauncher.opensSites`/`opensWebxdc` default to `opensNapplets`, so Android and
    Desktop are unchanged. iOS sets both false, so site and webxdc cards and the front page offer
    no Open, and a favorite site says it can't open.
- **Tests.** `IosNappletWebEngineTest` (3) runs on a booted simulator with the real
  `shell.html`, read from the checkout through a generated path:
  - the shell loads over `napplet://`;
  - `identity.getPublicKey` round-trips through the core;
  - `RTCPeerConnection` is undefined in the napplet, in a frame it makes, and in the shell;
  - no page world sees the handler;
  - fetch, image and WebSocket requests to a local listener are blocked and the listener accepts
    0 connections;
  - the shell can't be read again;
  - a reloaded document loses the channel and the frame is dropped;
  - the rule list alone keeps a CSP-less page off the network, with a control page that does
    reach the listener;
  - after a napplet loads, `canGoBack` is false.
- **By hand** (fresh "Napplet Test" iPhone 16 Pro, iOS 27.0, throwaway read-only npub):
  - Fokus opened and ran.
  - Emoji Pack Creator asked connect, then identity, storage and relays. The answers were
    Connect with "paranoid", Allow once, Never allow and Not now. Nothing was signed.
  - Logs show `bridge <- identity.getPublicKey`, then the reply and the next requests. Only
    `napplet://` was served.
  - Backgrounding the app flips attendance false, then true on return. The back arrow closes the
    session.
  - The left-edge swipe opens the app's drawer, not back. That is the shared shell's own gesture
    on iOS, so in-page history back is reachable only where that gesture reaches
    `PlatformBackHandler`.
  - No `publishMarmotKeyPackage`/30443 lines appeared.
- **Still open for iOS**, from the checklist:
  - Main-frame-only shell serving and `nosniff` are in the iOS adapter, not yet in the shared
    `NappletSiteServer` (row 29).
  - File picker and `NSCameraUsageDescription` (N7, row 11).
  - Process isolation is WebKit's (row 1).
  - Rows 19–20 (LAN from direct stores, DNS prefetch in proxied stores) wait for N6, when pages
    get a network.

### 7d. As built: N6, nSites and webxdc on iOS (2026-11-26)

- **Launch.** `IosNappletHost.launchOf` takes napplets and nSites (`RootSiteEvent`/`NamedSiteEvent`).
  An nSite's Tor is `NappletLaunchPlan.useTorFor(profile, torEnabled, siteViaTor)` over the per-site
  `NsiteNetworkChoices`, which is Android's `NappletLauncher` rule (Desktop's hosts use the same call
  now). `opensSites`/`opensWebxdc` are on. Webxdc apps have no event, so the launcher readies the launch
  in the host (`prepareWebxdc`) and pushes `Route.NostrApp("webxdc:<stage key>")` on the window's nav
  (`LocalAppNav`, new in commonsUI: the card sits deep in a note with no nav of its own). A restored
  webxdc screen has nothing to start from and says it can't open.
- **One store per account, one route per store.** WebKit proxies per data store, and every app of an
  account shares the account's store (storage per account, as Android). So the store's route is
  arbitrated like Android's process-wide WebView proxy and Desktop's router:
  `NappletRouteArbiter` (new, commons; Desktop's `DesktopNappletRoutes` now runs on it) over
  `NappletProxyClaims`, Tor always wins. `IosNappletStores` applies it:
  `proxyConfigurations = [nw_proxy_config_create_socksv5(127.0.0.1:<Arti port>)]` for Tor, `null`
  for direct, and a SOCKS proxy on port 1 (nothing listens) for **Blocked** (a Tor site while Tor has
  no port), so loads fail and never go direct. Napplets and webxdc apps claim nothing. A store follows
  Tor's port (collected only while an app is open, since watching Tor keeps it running): a restart
  elsewhere re-points it, Tor going away blocks it. The session bypass does not make a Tor site direct.
- **Fail closed.** `IosNappletStoreRoute.claim(useTor)`: on Tor with no port the core shows
  `WaitingForTor`, no web view exists, and after 30 s it fails `ROUTE_BLOCKED`.
- **Onion switch.** The shared `NappletOnionSwitch` (Desktop's toolbar control, moved to commonsUI)
  sits in the screen's top bar for an nSite while Tor is on, without its detail line (it is the
  screen reader's description). Switching restarts the site on the other route, as Android and Desktop
  do. "Routed over Tor anyway" shows when another open site of the account wants Tor.
- **Webxdc.** `WebxdcFiles` is commonMain now and serves a `WebxdcBundleSource`: the JVM's
  `WebxdcBundles` (unchanged) or `OkioWebxdcBundles` (iOS: `NappletHttp` on the launch's route, hash
  checked, unpacked with okio's zip reader under the shared `WebxdcArchiveRules` limits, into Caches).
  Differences from the JVM: the archive comes in whole (in memory, up to 256 MB; streamed to disk since N7), progress shows once,
  nothing trims (Caches is purgeable), and an entry climbing out (`../x`) lands inside the app as `x`
  instead of being skipped. PAPER's files are supported by the session; nothing launches a paper on
  iOS yet (inline surfaces are N7).
- **Logout.** `AccountSessionHooks.onAccountRemoved` (iOS had none) closes the account's screens and
  removes its store. Measured: WebKit keeps refusing ("Data store is in use") for as long as a closed
  web view lingers, which in the test executable was every time, even after a full Kotlin GC. So
  after six tries the store's data is wiped in place (`removeDataOfTypes(allWebsiteDataTypes)`, which
  works while in use), and the empty store is removed at the next start (`removePendingStores`, a
  list in `NSUserDefaults`). The test checks the site's localStorage is gone.
- **Compiled request filters** are reference-counted per open launch and removed from WebKit's rule
  store by the last one; a sweep at host start removes what a crash left. (Every napplet's list names
  its own origin, so they accumulated.)
- **Shared serving.** Main-frame-only shell serving and `nosniff` moved into `NappletSiteServer`
  (`serve(…, mainFrameNavigation)`): Android passes `isForMainFrame`, Desktop `RT_MAIN_FRAME`, iOS its
  pending top-frame navigation. `X-DNS-Prefetch-Control: off` stays an iOS response header.

**Checklist rows (host-core review §1).**

- **14, mixed content.** Measured: WebKit doesn't treat `napplet://` as a secure page for mixed
  content: `fetch`, `<img>` and `<script>` over `http` went out as plain http. The request filter now
  blocks `http`/`ws` (Android's `MIXED_CONTENT_NEVER_ALLOW`) except to `.onion` names. After it, a Tor
  site's probes showed CONNECTs only for `check.torproject.org:443` and the two `.onion` names on `:80`.
- **19, local network.** The request filter refuses IP literals of loopback, `0/8`, RFC 1918,
  link-local, CGNAT and IPv6 loopback/ULA/link-local/mapped, and `localhost`, `.local`, `.lan`,
  `.internal`, `.home.arpa`, for any route (measured: `fetch('http://127.0.0.1:<port>/lan')` never
  reached the listener). Not covered: a public name that resolves to a private address
  (`localtest.me` → 127.0.0.1 reached the loopback listener from a direct store before mixed content
  was blocked; over https it still would). On Tor the name goes to the exit by name (the CONNECT
  carried the host name), which can't reach the user's LAN. Closing that gap on direct stores needs
  Desktop's approach: a small in-process SOCKS router for direct stores too. (Done in N7, §7e.)
- **20, DNS prefetch.** A Tor store's `<link rel=preconnect>` went to the SOCKS listener (a CONNECT
  for `preconnect-probe.torproject.org:443`), so speculative connections follow the store's proxy.
  `<link rel=dns-prefetch>` produced nothing observable; WebKit's DNS prefetching is on only for
  `http:` documents (`Document::initDNSPrefetch`, read in WebKit's source, not measured), and every
  response says `X-DNS-Prefetch-Control: off` besides. DNS lookups themselves were not captured.

**Tests.** `IosNappletSiteTest` (8, booted simulator, a fake SOCKS5 listener that records each
CONNECT and refuses it):
- Tor: CONNECTs `check.torproject.org:443`, `amethystprobeonionfetch.onion:80`,
  `amethystprobeonionimage.onion:80`, `preconnect-probe.torproject.org:443`; the loopback listener
  saw 0; `RTCPeerConnection` undefined.
- Direct: 0 CONNECTs; `https` fetch `ok`; `RTCPeerConnection` is a function (Android's matrix).
- Tor wanted, no port: `WaitingForTor`, no web view, store `Blocked`, then `Failed(ROUTE_BLOCKED)`.
- Re-point: Tor moved to a second listener, the next fetch reached it; Tor gone, the next fetch
  reached neither listener nor went direct.
- Webxdc: 0 connections anywhere (fetch, image, WebSocket, another internal host).
- A site frame navigated to a `data:` document that says hello: no channel.
- The site request filter compiles; a removed account's site storage is gone.
- O4: `navigator.serviceWorker` is `undefined` on `napplet://`.

**By hand** ("Napplet Test", iOS 27.0, the N5 throwaway read-only npub, Tor internal; Armada preview
`35128:821c52af…:sw-d256c5`). The simulator's taps could not be driven here, so a temporary,
uncommitted launch-argument driver set the Tor choices and pushed the route, and a temporary probe
loaded `https://check.torproject.org/api/ip` in a hidden web view on the site's own store once it
showed:
- Site on Tor: `useTor=true`, store `Tor(port=17393)`, probe `{"IsTor":true,"IP":"80.67.167.81"}`;
  the top bar shows the onion switch on.
- The same site moved to the open web: store `Direct`, probe `IsTor:false` (the Mac's address); the
  switch shows off.
- Tor off for the app (site still set to Tor): `useTor=false`, `IsTor:false`, no onion switch.
- Waiting for Tor could not be caught in the app: Arti binds its SOCKS port 6–15 ms after it starts,
  before bootstrapping, and the route waits for the port (as relays and Desktop's router do). The
  simulator test covers it.
- Webxdc "Dino" (kind 1063, `3df305c8…`) downloaded, unpacked and ran offline; no onion switch.
- No `publishMarmotKeyPackage`/`30443` line in any run's log; nothing was signed.

### 7e. As built: N7, inline runs, the front page, files, reports and the router (2026-11-26)

- **Inline surfaces.** `IosAppPlatform` runs napplets inline (`runsNappletsInline = true`, so
  `showsPapersInline` too): `InlineNappletSurface`, `InlineWebxdcSurface` and `InlinePaperSurface`
  draw `IosInlineNappletPane`, the session's WKWebView in the note on the same core and engine as a
  screen. `IosNappletHost.openInline`/`openInlineWebxdc`/`openInlinePaper` mirror Desktop's: the
  launch is `NappletLaunch.inline` (the broker never asks), the sessions close with the note, and
  with the account that left. In a feed the interop is cooperative (the feed keeps vertical drags);
  the front page takes touches at once. Attended while composed with the app in front. No JS dialogs
  (answered at once, as Android's embedded runs), no NAP-FS, and no `<input type=file>` picker:
  `NappletWebContract.FILE_PICKER_REFUSAL_JS` in every frame, since WebKit on iOS has no chooser
  callback. The status is the shared `inlineStatusOf` (Desktop's pane calls it too); "painted" is the
  shell's `didFinishNavigation`. No poster frame is kept, as on Android.
- **The front page (PAPER).** `IosHeldPaperSession` holds the page per back-stack entry, as Desktop's
  `HeldPaperSession`, so a tab switch keeps its scroll. Its art follows the site Tor rule
  (`useTorFor(PAPER, …)`), through the account store's route. The web view keeps JavaScript on: the
  shell is script, and the paper's own frame runs none by `PAPER_CSP` (`default-src 'none'`), as on
  Android and Desktop. (N6's `allowsContentJavaScript = runsScripts` would have stopped the shell.)
- **Prefetch.** `PrefetchNappletFiles` warms the blob cache through the shared `NappletBlobPrefetch`
  on `NappletHttp` (public addresses only; Tor whenever it has a port; nothing while Tor is on without
  one; not when images don't load by themselves), as Desktop does. The JVM path is unchanged:
  `NappletBlobPrefetcher` is its OkHttp entry on the same core.
- **Files (row 11).** On a screen, `<input type=file>` opens WebKit's own picker (photo library,
  camera, files). The photo library is the system's out-of-process picker and needs no usage text;
  the camera does, so `project.yml` now has `NSCameraUsageDescription`. Nothing else gets the camera
  (getUserMedia stays denied). NAP-FS goes through `IosNappletFilePort` (one per session): the system
  document picker (`asCopy = false`), reads and writes inside the picked URL's security scope, saves
  into a folder the user picks; handles are random ids. NAP-UPLOAD is `KtorNappletUploadGateway`
  (commons, on the shared `AccountNappletBlossomGateway`): the account's Blossom servers, the uploads
  role's clients (Tor as the settings say for uploads), as the app's own media.
- **Report and block (4.7).** The screen's top bar has the app's note menu (`MoreOptionsButton`, the
  feed's ⋮ with Block / Report and the rest): the napplet's or nSite's event, or the post a webxdc app
  came in. Android and Desktop offer the same menu on the app's card; nothing new in moderation.
- **Links.** Android's sandbox hands an http(s) link to the system browser (`ACTION_VIEW`), not to
  an in-app browser. iOS does the same (Safari, on a link activation); a front page's nostr: links open
  in the app. Nothing to change.
- **Row 19, closed.** Every store a site claims is proxied to its own loopback router,
  `IosNappletSocksRouter` (commons iosMain), Desktop's `DesktopSocksRouter` design on the shared
  `NappletSocks5` codec and `NappletSocksPolicy` (commonMain): Blocked refuses everything (no more
  dead-port proxy), Tor hands each name to Arti unresolved, Direct resolves the name with
  `getaddrinfo`, keeps public addresses only and connects by the address it checked, so neither a
  public name that resolves to this device or its network (`localtest.me`) nor DNS rebinding gets
  through. The app's `napplet.local` names never leave. A route change cuts the open tunnels; a
  router whose socket iOS reclaimed while suspended listens again on return (and the store follows).
  Android does not enforce this for open-web nSites at all; iOS now matches Desktop. Not done: the
  router has no SOCKS authentication, so another app on the device could use it as an open proxy to
  the public internet (the same exposure Arti's SOCKS port already has); WebKit's `nw_proxy_config`
  can carry a username and password, which would close it, untested.
- **Cleanup at start.** `removePendingStores` and the rule-list sweep run at every app start
  (`IosAppModules`), not when the napplet host is first built.
- **Webxdc streaming.** `NappletHttp.download` streams a body into a sink (`KtorNappletHttp` under the
  cap); `OkioWebxdcBundles` hashes the archive on its way to disk, with live progress.

**Tests.** `IosNappletWebEngineTest` +2 (the inline picker refusal with a control; the escape
attempts), `IosNappletSiteTest` +1 (another app's origin) and the direct-site test now asserts the
rebind name reaches nothing; commons `IosNappletSocksRouterTest` (4, real sockets),
`NappletSocks5Test` (7) and `NappletBlobPrefetchTest` (3), commonTest so on the JVM too.

**By hand** ("Napplet Test", iOS 27.0, the throwaway read-only npub; screenshots in
`/private/tmp/claude-501/n7/`). Taps still can't be driven (idb's HID connection is invalidated), so a
temporary, uncommitted launch-argument driver opened each surface:
- The Napplets feed shows "Run here" on the Fokus card now (seen in a first screenshot, since
  overwritten); the driver
  drew `InlineNappletSurface` for Fokus: LOADING, then SHOWING, the timer running in the note
  (`01-inline.png`).
- `InlinePaperSurface` with a sample front page: SHOWING, script-free, its https art loaded through
  the account store's router on the open web (`02-paper.png`; the router bound, the store's proxy
  pointed at it, route Direct). One earlier run went on Tor (the site choice not loaded yet, which
  falls back to Tor) and an upload.wikimedia.org picture did not load there; not looked into further.
- The Fokus screen's top bar has the note menu ⋮ (`03-screen-topbar.png`); the menu it opens
  (`04-report-menu.png`) is the feed's Note Actions, with Block / Report further down the list.
- `IosNappletFilePort.pickOpen` presented the system document picker (`05-file-picker.png`); the app
  was then terminated, nothing picked or uploaded. WebKit's own `<input type=file>` menu needs a real
  tap (a scripted click has no user activation), so it was not opened here.
- No `publishMarmotKeyPackage`/`30443` line in any run; nothing was signed or published.

## 8. Phases

Each phase is testable on its own and lands as its own PR.

- **N1. Contract parameterised.** Add `NappletOrigins`, and add the `origins` parameter to
  `NappletSiteServer`. Test: commonTest pins the https values, and
  Android and Desktop are unchanged (`:commons:jvmTest`, Desktop napplet open, Android
  napplet open).
- **N2. Commons moves, part 1: headless.** `NappletLiveSubscriptions`, `NappletIncBus`,
  `NappletIntentDispatcher`, `NappletFiles`/`PaperFiles` go to commonMain (locks to `KmpLock`).
  Test: their existing jvmTests move to commonTest and run on JVM and
  `iosSimulatorArm64Test`.
- **N3. Commons moves, part 2: blobs + site server.** okio `Source`/`FileSystem`, the
  `NappletHttp` port, the OkHttp impl (the old code) and the Ktor impl. Test: the blob
  store/cache tests in commonTest against a fake `NappletHttp`, a Ktor `MockEngine` test of
  `KtorNappletHttp` (cap, 404, no cookies), and a JVM smoke test that
  `OkHttpNappletHttp` behaves as before.
- **N4. Broker on iOS.** `NappletResourceFetcher` and `buildAccountNappletBroker` go to
  commonMain. `IosNappletHost` builds the broker. Test: a `NappletRequestRouter` round trip
  on the simulator with a fake prompt queue.
- **N4.5. Shared session core (done 2026-11-26, §7b).** The policies, `NappletPageBridge`,
  `NappletSessionRouting` and `NappletSessionCore` in commonMain; Desktop's `CefNappletSession`
  becomes its CEF adapter; the shared shell checks a site's hello origin. Test: commonTest with a
  fake engine on JVM and `iosSimulatorArm64Test`, and a Desktop napplet and nSite opened by hand.
  Android moves later, on its own branch, after a device test.
- **N5 (done 2026-11-26, §7c). iOS session + scheme handler + bridge, NIP-5D only, no network.** `IosNappletSession`,
  `NappletSchemeHandler`, the content-world bridge, the guards, the navigation policy and
  `Route.NostrApp` with `AppLauncher.openNapplet`. Test: open a known napplet on the simulator.
  Check its hello/round trip, that `RTCPeerConnection` is undefined in its frame, a consent
  card shown and answered, and that no network leaves the web view (empty Tor/SOCKS counters
  and a CSP violation for a test fetch). Add an `iosSimulatorArm64Test` that drives the
  session headless. WKWebView runs in the test executable (O2, §2.3); pump
  `NSRunLoop.mainRunLoop` while waiting.
- **N6. nSites + Tor.** WEBSITE and WEBXDC profiles, per-account/per-network data stores,
  `NsiteNetworkChoices`, proxied stores, fail-closed states and re-point/close on port
  change. Test: an nSite with Tor on shows `IsTor=true` for a page fetch to
  check.torproject.org (open it as a top-level frame in the site's store), and with Tor off
  `IsTor=false`. Tor on but not bootstrapped shows the wait state and creates no web view.
  Two accounts' sites don't share localStorage.
- **N6 (done 2026-11-26, §7d).** As planned, except: storage stays per account (no per-network
  store), and the store's route is shared by its sites (Tor always wins).
- **N7. Polish.** PAPER profile, inline surfaces (`InlineNappletSurface`), the report/block
  action (4.7), link handoff to the in-app browser, the file picker for uploads (`<input
  type=file>` works in WKWebView; make sure it is limited to the picker), and logout cleanup
  of the stores.
- **N7 (done 2026-11-26, §7e).** As planned, plus prefetch, NAP-FS and NAP-UPLOAD, webxdc streaming
  and a SOCKS router per store (row 19). Links go to Safari, as Android's go to the system browser.

## 9. Test plan (beyond the per-phase checks)

- Done 2026-11-26: Q1–Q5 repeated on **iOS 17.4**, the floor, with no differences that matter
  (§2.3). Repeat the Tor rows on the floor for each release, because `proxyConfigurations` is
  the most version-sensitive piece. Don't rely on `indexedDB.databases()` for storage checks:
  it returns `[]` on 17.4.
- Run a device check of the Tor rows on real hardware before release: the simulator
  shares the Mac's network stack.
- Run the security review's attack list from `2026-10-31-embed-security-review.md` against the
  iOS host: a frame navigating the top, `window.open` loops, a napplet reloading itself to
  get a second hello, a site posting to the bridge, a site framing another app's subdomain,
  a no-cors fetch of the shell, a WebRTC constructor saved before the guard (impossible:
  document start), and a `Worker` or `SharedWorker` re-creating `RTCPeerConnection` (workers
  don't expose it; confirm).
  - **Done 2026-11-26 (N7), on the simulator (iOS 27.0), headless tests unless said:**

    | Attack | Result | Where |
    |---|---|---|
    | A frame navigates the top (`top.location` to the web, and to another of our own pages) | Refused: the top frame still holds the shell URL and the napplet still runs. The srcdoc's sandbox has no `allow-top-navigation`, and the engine lets the top frame hold only the shell | `IosNappletWebEngineTest.aFrameCantNavigateTheTopNorOpenWindows` |
    | `window.open` loop (50, no gesture) | No window (the UI delegate returns nil) and nothing handed to Safari (the gesture rule) | same test |
    | A napplet reloads itself for a second hello | The shell drops the frame; the second document never reaches the bridge | `aSecondDocumentInTheFrameLosesTheChannel` (N5) |
    | A site/frame posts to the bridge | No page world sees the handler (`undefined` in the napplet, its nested frame and the shell); the relay's DOM event fired on the frame's own document went nowhere, and `parent.document` throws | `aNappletLoadsOver…` (N5), `aFrameCantNavigate…` (N7) |
    | A site frames or loads another app's subdomain | Not served: the scheme handler answers only this launch's host (the same picture loads from the site's own origin, errors from another app's), and the other origin's request never reaches the files | `IosNappletSiteTest.aSiteCantFrameOrLoadAnotherAppsOrigin` |
    | A no-cors fetch of the shell | Refused by the napplet's CSP, and the handler serves the shell only to the navigation that loaded it (404 otherwise) | `aNappletLoadsOver…` (N5) |
    | A site's frame leaves its origin and says hello | No channel (the shell checks the hello's origin) | `aSitesFrameThatLeavesItsOriginCantSayHello` (N6) |
    | WebRTC constructor saved before the guard | Not possible: the guard is a document-start user script in every frame (spike Q3) | — |
    | A `Worker` / `SharedWorker` re-creating `RTCPeerConnection` | Neither can be made: a blob: `Worker` and `SharedWorker` both fail to start in a napplet's frame (`error`, its CSP), so no worker context exists to look for WebRTC in. Not checked: a worker in an nSite (no CSP of ours), where WebKit's workers have no `RTCPeerConnection` anyway (unmeasured) | `aFrameCantNavigate…` |
    | An inline run opens the file picker | Cancelled every way (click, label, a closed shadow root made open, `showPicker`, a nested frame); a screen's app clicks normally | `aRunInsideANoteCantOpenTheFilePicker` |
    | A public name that resolves to loopback (`localtest.me`) from a direct site | Refused by the store's router; the loopback listener saw no connection | `aSiteOnTheOpenWebNeverTouchesTheSocksListener`, `IosNappletSocksRouterTest` |
- Regression: Android `:nappletHost` and Desktop napplet screens after N1–N4, opened by hand.
- Logs: during every on-device check, watch for `publishMarmotKeyPackage` / `signed kind
  30443` after a logged-in launch, and never sign or publish from a test.

## 10. Open questions

- O1. Is there a WKWebView-level way to turn WebRTC off outright, rather than relying on the
  JS guard? The guard is proven in every frame kind tested. There is no public
  `WKPreferences` switch.
- O2. **Answered: yes** on a booted simulator (§2.3). WKWebView loads and evaluates JS inside
  the `iosSimulatorArm64Test` executable if the test pumps the main run loop. N5's session
  tests can run headless. They still have to confirm that a `WKURLSchemeHandler` and the
  message bridge work there too.
- O3. **Answered (N6, measured):** neither. WebKit does not treat a `napplet://` page as a secure
  page for mixed content, so `fetch`, `<img>` and `<script>` over plain `http` all went out as
  plain http (SOCKS CONNECTs to `:80` for `.onion` and clearnet names alike). The host now does what
  Android's `MIXED_CONTENT_NEVER_ALLOW` does, in the request filter: plain `http`/`ws` is blocked,
  except to `.onion` names (end-to-end encrypted and authenticated), so an nSite can call an onion
  API over Tor. That one exception is more permissive than Android, deliberately.
- O4. **Answered (N6, measured):** `navigator.serviceWorker` is `undefined` in an nSite's frame on
  `napplet://`, so a site that needs a service worker degrades (no offline mode, no push). The same
  site on Android/Desktop (`https://…napplet.local`) gets one.
