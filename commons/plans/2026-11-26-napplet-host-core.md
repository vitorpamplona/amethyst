# iOS napplet host review: parity with Android and Desktop, and what to share

Read-only review of `Amethyst-ios-tor` (branch with the N2 moves staged), 2026-11-26. Plan reviewed:
`commons/plans/2026-11-26-ios-napplets.md`. Paths are shortened: `NH/` =
`nappletHost/src/main/kotlin/.../napplethost/`, `AM/` = `amethyst/src/main/java/.../napplet/`,
`DT/` = `desktopApp/src/jvmMain/kotlin/.../desktop/napplet/`, `CM/` =
`commons/src/commonMain/kotlin/.../commons/napplet/`, `JA/` = the same under `jvmAndroid`.
Every claim below comes from reading the code. Where I did not check a fact, the text says "unverified".

## 1. Security-layer checklist

"Plan" means plan §4/§6, and **GAP** means the plan does not cover the item.

| # | Protection (defends against) | Android | Desktop | iOS mechanism | Plan |
|---|---|---|---|---|---|
| 1 | Process isolation, no keys in the sandbox (a renderer exploit reaching keys) | WebView in `:napplet`, a separate process with no `Amethyst.instance`; the broker is in the main process (`AM/NappletBrokerService.kt:90-96`). Open issue: it shares the app's uid (security review, Open) | None: CEF runs in-process with `no_sandbox` (review, Open, Critical) | The WebContent process is out of process and sandboxed by WebKit; the broker stays in the app | §4.1 covered |
| 2 | Launch token (a compromised sandbox acting as another app) | 32 random bytes, minted in the main process (`AM/NappletLaunchRegistry.kt:102`) and bound to the account (`AM/NappletLauncher.kt:214-221`). The sandbox does receive author and identifier to fetch blobs (`NH/NappletHostActivity.kt:613-614`), but the broker resolves identity only from the token | None needed: the session object holds `launch` and `accountPubKey` (`DT/CefNappletSession.kt:121-124`) | Same as Desktop. The session is bound to the launching account | **GAP (small):** state that the session signs as the launching account, and close sessions when that account logs out, as `DesktopNappletHost` does |
| 3 | Bridge origin restriction (frames calling native) | `addWebMessageListener(..., setOf(ORIGIN))` plus `isMainFrame` (`NH/NappletHostActivity.kt:365-370`, `:843`; `NH/NappletHostService.kt:449`, `:857`) | `frame.isMain && frame.url == SHELL_URL`, with a random query name deleted from `window` (`DT/CefNappletSession.kt:393`, `:241-244`) | Named `WKContentWorld` handler plus `frameInfo.isMainFrame` plus `securityOrigin` | §3.2 / §4.3 covered |
| 4 | One-document binding and hello-once (a reload or navigation inheriting the channel) | Shared `shell.html:57-112` (`bindOneDocument`, `replaced()` removes the frame) | Same file | Same file, unchanged | Covered implicitly. **GAP (all hosts):** for WEBSITE/WEBXDC the shell checks only `e.source === iframe.contentWindow` (`shell.html:101`). If the site's frame is navigated off-origin, the foreign page can send its own hello and take over the port. Add `e.origin === '__APP_ORIGIN__'` for those profiles in the shared shell |
| 5 | CSPs: shell, `APP_CSP`, `WEBXDC_CSP`, `PAPER_CSP` | Shared `CM/NappletWebContract.kt:248-283`, chosen by `HostProfile.appCsp` (`CM/HostProfile.kt:70-77`), sent as headers by `JA/NappletSiteServer.kt:155,183` | Same `NappletSiteServer` | Scheme-handler `NSHTTPURLResponse` headers (spike Q1 shows they are enforced) | Covered |
| 6 | Off-origin requests from NAPPLET/WEBXDC (exfiltration if the CSP is bypassed) | Every request reaches `shouldInterceptRequest`, which returns 404 when `!allowsOffOrigin` (`JA/NappletSiteServer.kt:126-132`) | `cancelHandler` (`DT/CefNappletSession.kt:425-445`, `:546-553`) | **WKWebView cannot see http(s) requests**: the scheme handler only receives `napplet://` | **GAP (high):** without a second layer, iOS relies on the CSP alone. Fix: a `WKContentRuleList` per profile, generated from a shared policy: block `.*`, then `ignore-previous-rules` for `^napplet://<this launch's hosts>`, `about:`, `data:`, `blob:`. Add it to NAPPLET/WEBXDC/PAPER configurations, and to WEBSITE configurations to block `https?://[^/]*\.napplet\.local` (Desktop refuses those names on the network, `DT/DesktopSocksRouter.kt:52-53`) |
| 7 | WebRTC guard, and refusing to run without it (UDP around the proxy) | `NappletWebRtcGuard.isNeeded = runsScripts && (profile != WEBSITE \|\| useTor)` (`NH/NappletWebRtcGuard.kt:47-50`). The host refuses to start if the guard is unsupported (`NH/NappletHostActivity.kt:317`; Service `:417`) | Guard extension (`DT/DesktopCefEngine.kt:243-263`), `webrtc.ip_handling_policy=disable_non_proxied_udp` (`:415`), and the in-shell `WEBRTC_CHECK` that refuses to start (`DT/CefNappletSession.kt:1021-1026`, `:533-536`) | Page-world document-start `WKUserScript` in all frames (spike Q3) | **GAP:** §3.3 installs it only "on every napplet/webxdc configuration". Android also installs it for a WEBSITE on Tor. Use the shared `isNeeded(profile, useTor)`. Also put the shared `WEBRTC_CHECK` in the iOS shell head as the runtime refusal |
| 8 | Cookie guard (cross-app tracking through the shared `napplet.local` cookie jar) | Document start, every profile (`NH/NappletHostActivity.kt:670`) plus the prelude (`CM/NappletWebContract.kt:294`) | Extension `cookies.js` plus third-party cookies blocked (`DT/DesktopCefEngine.kt:263,416`) | Page-world document-start script; WebKit ITP blocks third-party cookies | §3.3 covered |
| 9 | Navigation policy: internal hosts stay, external links need a gesture and open in the system browser | `shouldOverrideUrlLoading` checks `isInternalHost`, `opensLinks && hasGesture() && http(s)`, and otherwise always cancels (`NH/NappletHostActivity.kt:747-769`, a near copy at `NH/NappletHostService.kt:741-752`) | `onBeforeBrowse` (`DT/CefNappletSession.kt:410-424`). A website's embeds may navigate **only below its own frame** (`frame.parent?.isMain == false`) | `decidePolicyForNavigationAction` with `navigationType == .linkActivated` | **GAP:** §4.4 allows "sub-frame navigations to http(s)" for sites. That includes the site's own frame, which is a child of the shell, and combined with row 4 this hands the port to a foreign page. `WKFrameInfo` exposes no parent, so deny when `targetFrame.securityOrigin` equals `appOrigin` (the site's own frame leaving its origin) and allow only frames of other origins. Also: links from a Tor-routed site open in Safari over clearnet. The review lists this as a known Desktop issue; decide the same way for iOS |
| 10 | `window.open` and popups | `setSupportMultipleWindows(false)`, `javaScriptCanOpenWindowsAutomatically=false` (`NH/NappletHostActivity.kt:654-655`) | `onBeforePopup` applies the link rule and returns true (`DT/CefNappletSession.kt:461-469`) | `createWebViewWithConfiguration` returns nil; `javaScriptCanOpenWindowsAutomatically=false` | Covered |
| 11 | File chooser limits (handing over the app's own files) | `NappletFileChooser` plus `NappletPickedFiles` (review #14); inline runs refused (#17) | `DesktopNappletFilePort` (NAP-FS) | WebKit's native picker (no app URIs involved) | Mostly N7. **GAP:** `iosApp/xcode/project.yml:32-33` has no `NSCameraUsageDescription`, and WebKit's picker can offer "Take Photo" (unverified whether it crashes or hides the option). Test it, and refuse the picker for inline runs as Android does |
| 12 | Downloads | No `DownloadListener` in the napplet hosts (grep), so nothing downloads | No download handler on `CefNappletSession` (grep; the browser has `BrowserDownloads`) | `decidePolicyFor navigationResponse`: cancel when `!canShowMIMEType`; never return `.download`; check `shouldPerformDownload` | Covered |
| 13 | Geolocation, camera, mic | `setGeolocationEnabled(false)`; no `onPermissionRequest` override, so the default denies | No permission handler found (grep); CEF's default is unverified | `requestMediaCapturePermissionFor` answers `.deny`. Note that `NSMicrophoneUsageDescription` **is** present (`project.yml:33`), so the deny is load-bearing | Covered. Make sure it applies to every profile and every frame |
| 14 | Mixed content | `MIXED_CONTENT_NEVER_ALLOW` (`NH/NappletHostActivity.kt:659`) | Chromium default | WebKit blocks or upgrades; not configurable | O3 open. Measure it in N6 |
| 15 | JS dialogs (phishing over chrome, spam) | Full-screen: default dialogs are shown. Embedded: `onJsAlert/Confirm/Prompt` cancel (`NH/NappletHostService.kt:600-630`) | No handler (CEF default) | `WKUIDelegate runJavaScript*Panel`. If it is not implemented, nothing shows (unverified) | **GAP:** not mentioned. Decide on a shared rule (cancel when inline or parked, show otherwise) and implement it |
| 16 | Safe Browsing | Enabled when supported (`NH/NappletHostActivity.kt:660-662`) | Off (background networking disabled) | `WKPreferences.isFraudulentWebsiteWarningEnabled` (on by default) | **GAP (minor):** set it explicitly |
| 17 | Storage separation per account | WebView `Profile` per account, the hash of the pubkey (`AM/NappletWebViewProfiles.kt:43-63`, applied first at `NH/NappletHostActivity.kt:346`). **Not separated per network** | Origin partitioned by account: `appId(partition = accountPubKey)` for `ownsOrigin` (`DT/CefNappletSession.kt:200-208`), in one Chromium profile, Tor and clearnet shared | `WKWebsiteDataStore(forIdentifier:)` per account and per network | §4.5 asks N6 to check Android. **Answered here:** Android is per account only. iOS per-network is stricter, but toggling Tor then loses a site's localStorage. That is a visible divergence; keep it deliberately and document it, or match Android |
| 18 | Tor fail-closed, plus `NappletProxyClaims` | Refuses with Tor on and no port (`NH/NappletHostActivity.kt:308-313`); `WebViewProxyPolicy` waits for the override to be in effect before loading (`NH/WebViewProxyPolicy.kt:36-41`, `NH/NappletHostActivity.kt:429-438`) | `WaitingForTor` / `ROUTE_BLOCKED`; the SOCKS router is `Blocked` by default (`DT/CefNappletSession.kt:266-287`, `DT/DesktopSocksRouter.kt:75`) | Per-store `proxyConfigurations` (spike Q4; no failover was measured) | Fail-closed is covered. **GAP:** §6 says the claims are "fed the same useTor decision". `NappletProxyClaims` exists to arbitrate **one process-wide route** ("Tor always wins"), and iOS proxies per store, so it does not apply. Do not use it on iOS, and never show `torForced` |
| 19 | Local network from a page (SSRF into LAN, router) | Not enforced for nSites on the open web (WebView) | `DesktopSocksRouter` refuses loopback, private, link-local, CGNAT and own addresses on **every** route (`:47-52`) | Direct store: none. Tor store: the exit can't reach the LAN | **GAP:** decide. Options: a `WKContentRuleList` blocking IP-literal private ranges (host names still resolve to the LAN), or proxying direct stores too through a small in-process SOCKS router like Desktop's (which would make its logic shareable) |
| 20 | DNS prefetch outside CSP | Not measured (review, Open) | `net.network_prediction_options=2` (review #22) | Unverified. In a proxied store this could resolve locally and leak under Tor | **GAP:** measure in N6 (count `getaddrinfo` calls or capture DNS while a napplet holds a `<link rel=dns-prefetch>`) |
| 21 | Blob fetch: public-only DNS, 20 MB cap, 45 s call timeout, no cookies | `JA/NappletBlobHttp.kt:48,78,93-113,119`. `publicOnly` is set **only for prefetch**; the content server's client is not (`NH/NappletContentServer.kt:84`) | Same code | `NappletHttp` port (§5.2) | Covered. §5.2 says "before a direct request" for every request, which is stricter than Android. Pass the same `publicOnly` flag so behaviour matches |
| 22 | Hash verification of blobs | `StaticSiteResolver.verify` before caching (`JA/NappletBlobStore.kt:126`); webxdc archive hash | Same | Shared after N3 | Covered |
| 23 | Consent gates: declaration, then ledger, then consent | Shared `NappletBroker` and `NappletPermissionLedger`; the token carries the minted declared set | Same | Same broker plus `NappletPromptQueues` | Covered |
| 24 | Consent timeout and unattended requests | No consent timeout (`ConsentQueue.ask`, `CM/.../consent/ConsentQueue.kt:77`); a dismissal fails closed with `ASK`. Requests that act for the user are **held while unattended**, 120 s (`CM/NappletHeldRequests.kt:88`, `NH/NappletHostActivity.kt:873-881`), and reads wait for attendance (`AM/NappletBrokerService.kt:590`) | `attendance.set(true)` at creation and never false (`DT/CefNappletSession.kt:367`, `:861`). No held requests | Scene/screen visibility drives attendance | **GAP:** the plan has no attendance. iOS must mark a session unattended when the screen is hidden or the app is backgrounded, and hold acting requests like Android (Desktop also lags here) |
| 25 | Revoking session grants | `ledger.endSession()` when the broker is destroyed (`AM/NappletBrokerService.kt:224-238`) | `revokeSessionGrants` (`DT/DesktopNappletHost.kt:593`) | `IosNappletHost` | **GAP:** say when the "session" ends on iOS (last session closed, or process death) |
| 26 | Screenshot protection | None for napplets (`FLAG_SECURE` only on the key backup screen) | None | None | Parity: none |
| 27 | Back navigation | Back goes to WebView history first, then closes (`NH/NappletHostActivity.kt:220-236`) | Toolbar back and forward | §4.6 "Back closes it", with gestures off | **GAP (behaviour):** an nSite's in-site history is lost. Use `canGoBack`/`goBack` before popping, as Android does |
| 28 | Renderer crash | `onRenderProcessGone` drops the view and offers a restart (`NH/NappletHostActivity.kt:731-745`) | Load error shown | `webViewWebContentProcessDidTerminate` | **GAP:** close the bridge state (subscriptions, INC) and show the shared error with Retry |
| 29 | Shell served only to the main-frame navigation; `nosniff` | Not done: the shell is served on any GET of `shellUrl` (`JA/NappletSiteServer.kt:126`); no `nosniff` header | Same | §4.2 adds both, for iOS only | Divergence: put both in the shared `NappletSiteServer` (an `isMainFrameNavigation` argument, and `nosniff` next to `NO_STORE`) so all hosts match |
| 30 | Web inspector | `setWebContentsDebuggingEnabled(false)` always | n/a | `isInspectable` in Debug only | Fine |

## 2. Engine-independent logic, duplicated

| Responsibility | Android | Desktop | iOS (planned) | Approx. lines |
|---|---|---|---|---|
| Launch plan: `appOrigin` (with/without account partition), `useTor`, `blobsUseTor`, fail-closed precheck | `AM/NappletLauncher.kt:228-236` (useTor); `NH/NappletHostActivity.kt:308-336`; `NH/NappletHostService.kt:417-426` | `DT/DesktopNappletHost.kt:202-208,227-236`; `DT/CefNappletSession.kt:200-212` | §6 pseudo-code, a third copy | ~25 / 30 / new. Note that Android and Desktop disagree on the partition (row 17) |
| Files and server construction (`NappletBlobStore` / `WebxdcFiles` / `PaperFiles` → `NappletSiteServer`) | `NH/NappletContentServer.kt:84-104`, built twice (Activity `:336`, Service `:427-443`) | `DT/CefNappletSession.kt:289-326` | §4.1 | ~40 / ~40 |
| Availability probe → Loading / WaitingForTor / Failed / Showing state machine | `probeAndMount` (`NH/NappletHostActivity.kt:401-417`) plus views | `State`/`Failure` plus `start()` (`DT/CefNappletSession.kt:148-172,262-379`) | §4.6 | ~40 / ~110 |
| Which guards are required, and the refusal | `NappletWebRtcGuard.isNeeded` plus checks in two places | `WEBRTC_CHECK`, `shellHead` choice (`:325`) | §3.3 | ~15 / ~10 |
| Request classification: serve, cancel, or network | Inside `NappletSiteServer.serve` (null vs 404) | `getResourceRequestHandler`, `isInternal`/`isLocal`/`isWebLink` (`:426-445,1033-1048`) | Scheme handler 404 rule (§4.2) plus a content rule list | ~0 / ~35 |
| Navigation decision (internal / local / embed / gesture link / popup) | Two copies (`NH/NappletHostActivity.kt:747-769`, `NH/NappletHostService.kt:741-752`) | `onBeforeBrowse` plus `onBeforePopup` (`:410-424,461-469`) | §4.4 | ~30 / ~25 |
| WebView hardening settings | **Copied verbatim** twice (`NH/NappletHostActivity.kt:639-673`, `NH/NappletHostService.kt:544-580`) | Engine args | §4.4 | ~70 (Android-internal dedupe) |
| Bridge gate (main frame plus shell origin) | 2× | 1× | 1× | ~5 each |
| Envelope handling: parse, fire-and-forget id (`fire-N`), reply id injection (`withString("id")` vs `withReplyId`) | `NH/NappletHostActivity.kt:836-889,899-924`; Service `:850-900,954-978` | `handle()` / `withReplyId` (`:679-726,1053-1060`) | §4.3 | ~90 / ~90 / ~60 |
| Routing: `routeInc` (ordered, on the arrival thread), `routeIntent`, `route` → Reply/Push/Open/CloseSubscription, `identityWatch` | Broker side `AM/NappletBrokerService.kt:540-608` | `handle()` | §4.1 | ~70 / ~45 |
| Held acting requests while unattended | Activity, Service, Browser×2 (`NH/...:873-881`, Service `:887-895`) | none | none | ~20 each |
| Reply batching to the page | One `postMessage` per reply | `send`/`flush` (`:809-837`) | `evaluateJavaScript` (same need) | 0 / ~30 |
| Teardown order: drop the app frame, close files, subscriptions, INC, intents, attendance, claims | `onDestroy` plus `releaseFromBroker` | `close()` (`:839-911`) | needed | ~30 / ~40 (engine part excluded) |
| Per-site network choice store | `AM/NsiteNetworkRegistry.kt` (65) | `DT/DesktopNappletHost.kt:106` | §6, a third copy | small, but a third copy |
| Consent presentation | `AM/NappletConsentCoordinator.kt` (77) plus an Activity | `NappletPromptQueues` plus `ConsentWindows` | `NappletPromptQueues` plus cards | — |

About 600-800 lines across the two hosts are engine-agnostic logic, not counting the Android IPC plumbing.

## 3. Proposal: `NappletSessionCore` plus a `NappletWebEngine` port

### 3.1 Shape (commons `commonMain`; no Compose needed)

The pure policies live in one place, each tested on its own:
- `NappletLaunchPlan.of(launch, accountPubKey, torEnabled, choices, appletsViaTor, origins)` returns `appOrigin`, `useTor`, `blobsUseTor`, `guards: Set<Guard>`, `requiresTorPort`. It resolves the partition disagreement once, in one place.
- `NappletNavigationPolicy.decide(url, frame: FrameKind{SHELL, APP, EMBED}, userGesture, isPopup, profile)` returns `Allow | Cancel | OpenExternal(url)`.
- `NappletRequestPolicy.classify(url, method, origins, appOrigin, profile)` returns `Serve | Cancel | Network`. It can also emit a WKContentRuleList JSON for iOS, from the same rules.
- `NappletBridgeGate.accepts(isMainFrame, originOrUrl, origins)`.
- `WEBRTC_CHECK` moves from `CefNappletSession` to `NappletWebContract`.

The session core splits into two halves, so that Android's process split still fits:
- `NappletPageBridge`, the page side: envelope parse, `fire-N` ids, reply-id injection, `NappletHeldRequests` and attendance, an outbox with batching, and an optional `NappletBridgeDocuments`. Android runs it in `:napplet`; Desktop and iOS run it in-process.
- `NappletSessionCore`, the trusted side: `routeInc`/`routeIntent`/`route`, `NappletLiveSubscriptions`, the INC bus, intents, `identityWatch`, ordered teardown, and a `StateFlow<NappletSessionState>` (Loading, WaitingForTor, Showing, Failed(reason), RendererGone). Android runs it in the broker service, keyed by the token. Desktop and iOS connect the two halves with an in-memory transport.

The port, which is the only per-engine code:

```kotlin
interface NappletWebEngine {
    fun addDocumentStartScript(js: String, allFrames: Boolean, isolated: Boolean = false): Boolean // false = unsupported → core refuses
    fun setRoute(route: NappletRoute)                        // iOS: store proxy; Android/Desktop: file a claim, onReady/onFailed
    fun loadShell(shellUrl: String)
    fun evaluateInShell(js: String)                          // batched bridge delivery
    fun canGoBack(): Boolean; fun goBack()
    fun close()
    var listener: NappletEngineListener?
}
interface NappletEngineListener {
    fun onBridgeMessage(raw: String, isMainFrame: Boolean, origin: String?)
    suspend fun onRequest(method: String, url: String, mainFrameNavigation: Boolean, acceptsHtml: Boolean): NappletSiteServer.Response?
    fun onNavigation(url: String, frame: FrameKind, userGesture: Boolean, isPopup: Boolean): NavDecision
    fun onRendererGone()
}
```

Each adapter owns its threading. Android has to block in `shouldInterceptRequest`, which it does today with `runBlocking`. iOS marshals `WKURLSchemeTask` calls to the thread the task arrived on (§4.2).

### 3.2 What shrinks

- **Desktop:** `CefNappletSession` (1060 lines) becomes a `CefNappletEngine` holding the CEF handlers, offscreen rendering, zoom, find and the close-after-detach dance, probably 500-550 lines. About 300 lines move out (start, handle, send/flush, policies, state).
- **Android:** `NappletHostActivity` and `NappletHostService` each lose the duplicated hardening, navigation, envelope and held-request code (about 150 lines each), and wrap the WebView in a `WebViewNappletEngine` adapter shared by both (about 200 lines). The routing block in `NappletBrokerService` (about 70 lines) becomes `NappletSessionCore` calls.
- **iOS:** `IosNappletSession` becomes the WK adapter only: configuration, scheme handler, content world, rule list, delegates. I estimate 400-500 lines, against about 1,000 if it mirrors `CefNappletSession`.

### 3.3 Sequencing (behaviour pinned at every step)

1. **N4.5a, after N4: pure policies.** Extract `NappletLaunchPlan`, the navigation and request policies, the bridge gate and `WEBRTC_CHECK`. Point the Android and Desktop call sites at them. In `commonTest`, write table tests whose expected values are copied from today's behaviour (gesture rule, embed rule, `isInternal` edge cases such as `@` and ports, the guard matrix, the useTor matrix). Then run a manual Android and Desktop open.
2. **N4.5b: `NappletSessionCore` plus `NappletPageBridge`,** with a `FakeNappletEngine`. Move Desktop first, because it is in-process like iOS. Then move the Android page side and broker side.
3. **N5:** write iOS directly against the core, adding the gaps from section 1 (content rule list, WEBSITE+Tor guard, attendance, the renderer-gone path).
4. Shared hardening that benefits everyone can land alongside, in the shared code: the `e.origin` check in the shell, main-frame-only shell serving and `nosniff` in `NappletSiteServer`.

### 3.4 Tests (`commonTest`, run on the JVM and `iosSimulatorArm64Test`)

Use the fake engine to check that:
- a subframe or foreign-origin bridge message is dropped;
- reply ids round-trip, `fire-N` ids are synthesized, and pushes are not re-id'd;
- the batch keeps order under concurrent sends;
- an acting request is held while unattended and expires with `EXPIRED`;
- Tor on with no port leads to WaitingForTor/Failed and `loadShell` is **never** called;
- `addDocumentStartScript` returning false refuses the load (Android parity);
- NAPPLET off-origin requests Cancel and WEBSITE off-origin requests go to Network;
- a gesture link opens externally while a non-gesture one is cancelled;
- `close()` releases subscriptions, INC, intents, attendance and the route;
- after `onRendererGone`, late broker replies are dropped.

Existing `NappletRequestRouterTest`, `NappletBridgeDocumentsTest` and `NappletSiteServerTest` stay as they are.

### 3.5 Other dedupe candidates

- **`NostrAppScreen`:** Desktop (`DT/NostrAppScreen.kt`, 197 lines) and the planned `IosNostrAppScreen` share everything except the surface. Make one commonsUI screen driven by `NappletSessionState` with a `@Composable surface(session)` slot, plus a shared toolbar and loading, waiting and failed panes. Android's full-screen host is an Activity in `:napplet` and can't use it directly, but its panes could later use the same composables.
- **Consent:** `NappletConsentCoordinator` (Android) and `NappletPromptQueues` (Desktop) both wrap `ConsentQueue`. Android could adopt `NappletPromptQueues` plus one activity, which leaves one prompt model on all three platforms.
- **Network choices:** add one factory, `NsiteNetworkChoices.forApp(appStores, scope, defaultViaTor)`, and the shared `NappletLaunchPlan.useTor` in place of three copies of the useTor expression.
- **`InlineNappletPane` (Desktop) and N7 `InlineNappletSurface`:** these would come out of the same core and slot.

## 4. Top fixes for the plan, in priority order

1. A `WKContentRuleList` as the second layer behind the CSP (row 6).
2. The WebRTC guard for WEBSITE on Tor (row 7).
3. Site-frame navigation, and the shell `e.origin` check (rows 4 and 9).
4. Attendance and held requests (row 24).
5. Drop `NappletProxyClaims` on iOS (row 18).
6. LAN reachability for direct stores, and DNS prefetch (rows 19 and 20).
7. Back history and renderer-gone handling (rows 27 and 28).
8. Put the iOS-only header and shell-serving hardening in the shared server (row 29).
