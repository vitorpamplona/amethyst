# iOS napplets and nSites (NIP-5D, NIP-5A) on WKWebView, with Tor

Status: N0–N4 done, 2026-11-26 (what changed from §5 while doing them: §5.7). Branch `feat/ios-napplets`.
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
The runs used that simulator's runtime, **iOS 27.0**. **The runs were not repeated on the 17.x
runtimes**; see the N1 test plan.

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
| IndexedDB | n/a | SecurityError | A lists `[spike-cspHeader, spike-siteA]` (both nabc), B lists only `[spike-siteB]` |
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
| `dataStoreForIdentifier(uuid)` + SOCKS proxy | `IsTor=true` | +2 |
| `nonPersistentDataStore()` + SOCKS proxy | `IsTor=true` | +2 |
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

Cleanup: `removeDataStoreForIdentifier` failed with "Data store is in use" while a WKWebView on
it was still alive. Deleting a per-account store (logout, "clear site data") must first tear
down every WKWebView on it.

**Q5. Scheme-handled loads never reach the network on their own.** Every `napplet://` request
reached the handler (`scheme-request …`), proxied store or not, and none of them moved the
SOCKS counter. WebKit has no other path for them: the bytes the page gets are the bytes the
handler hands it. So blob fetching stays ours (Ktor, with Tor chosen per launch, §6),
exactly as on Android, where `shouldInterceptRequest` plays that role.

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
- **N5. iOS session + scheme handler + bridge, NIP-5D only, no network.** `IosNappletSession`,
  `NappletSchemeHandler`, the content-world bridge, the guards, the navigation policy and
  `Route.NostrApp` with `AppLauncher.openNapplet`. Test: open a known napplet on the simulator.
  Check its hello/round trip, that `RTCPeerConnection` is undefined in its frame, a consent
  card shown and answered, and that no network leaves the web view (empty Tor/SOCKS counters
  and a CSP violation for a test fetch). Add an `iosSimulatorArm64Test` that drives the
  session headless if WKWebView can run in the test executable; it is unknown whether it
  can, so check that first.
- **N6. nSites + Tor.** WEBSITE and WEBXDC profiles, per-account/per-network data stores,
  `NsiteNetworkChoices`, proxied stores, fail-closed states and re-point/close on port
  change. Test: an nSite with Tor on shows `IsTor=true` for a page fetch to
  check.torproject.org (open it as a top-level frame in the site's store), and with Tor off
  `IsTor=false`. Tor on but not bootstrapped shows the wait state and creates no web view.
  Two accounts' sites don't share localStorage.
- **N7. Polish.** PAPER profile, inline surfaces (`InlineNappletSurface`), the report/block
  action (4.7), link handoff to the in-app browser, the file picker for uploads (`<input
  type=file>` works in WKWebView; make sure it is limited to the picker), and logout cleanup
  of the stores.

## 9. Test plan (beyond the per-phase checks)

- Repeat the spike's Q1–Q4 checks on the **iOS 17.4/17.5 runtimes**, the new floor (a fresh
  simulator with no account; the `-AmethystNappletSpike` hook pattern, debug-only). The
  spike ran only on the default runtime, and `proxyConfigurations` behaviour is what could
  differ between versions.
- Run a device check of the Tor rows on real hardware before release: the simulator
  shares the Mac's network stack.
- Run the security review's attack list from `2026-10-31-embed-security-review.md` against the
  iOS host: a frame navigating the top, `window.open` loops, a napplet reloading itself to
  get a second hello, a site posting to the bridge, a site framing another app's subdomain,
  a no-cors fetch of the shell, a WebRTC constructor saved before the guard (impossible:
  document start), and a `Worker` or `SharedWorker` re-creating `RTCPeerConnection` (workers
  don't expose it; confirm).
- Regression: Android `:nappletHost` and Desktop napplet screens after N1–N4, opened by hand.
- Logs: during every on-device check, watch for `publishMarmotKeyPackage` / `signed kind
  30443` after a logged-in launch, and never sign or publish from a test.

## 10. Open questions

- O1. Is there a WKWebView-level way to turn WebRTC off outright, rather than relying on the
  JS guard? The guard is proven in every frame kind tested. There is no public
  `WKPreferences` switch.
- O2. Does WKWebView run inside an `iosSimulatorArm64Test` executable (no UIApplication)? It
  decides whether N5's tests can be headless.
- O3. `http://*.onion` sub-resources from a secure (`napplet://`) page: are they upgraded or
  blocked? This affects nSites that call onion APIs. Android has the same mixed-content
  question.
- O4. Service workers: WebKit does not register them on custom schemes (expected; not
  measured), so an nSite that needs one degrades. Measure it in N6 and add it to the site
  compatibility notes.
