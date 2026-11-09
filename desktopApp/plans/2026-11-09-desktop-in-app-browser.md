# Desktop in-app browser with NIP-07

**Date:** 2026-11-09. **Status:** phase 1 built (this doc); phases 2 and 3 open.

## Why

On Android the Browser tab, favorite web apps and the screens that launch a site (Buzz invites,
web-of-trust sites) open in Amethyst's own sandboxed WebView, which gives every page a NIP-07
`window.nostr` signed by the account, behind the shared consent. On the desktop the same shared
`BrowserScreen` ended in `DesktopAppLauncher.launchUrl` → `DesktopBrowser.open`: the system browser,
which has no signer. A Nostr web app opened from Amethyst could not log in with Amethyst.

The desktop already runs Chromium (JCEF) for napplets and nSites, with NIP-07 for nSites
(`2026-06-21-napplet-desktop-host.md`), so the browser reuses that engine and broker.

## Phases

1. **Built:** a web page is a screen, `Route.WebPage`, with NIP-07, and so are napplets and nSites
   (`Route.NostrApp`, below). The navigation layout setting places each like any other screen: on
   the stack, in a deck column, in a tab or in a window. `AppLauncher.launchUrl`, favorites and the
   note cards' Open open them; links in notes still go to the system browser.
2. **Open:** Chromium out of the process that holds the keys. JCEF runs with `no_sandbox`
   (`commons/plans/2026-10-31-embed-security-review.md`, Critical), and a browser shows pages from
   anywhere, so this matters more now than it did for napplets alone.
3. **Open, after 2:** an "Open in Amethyst browser" action on links, or a setting to make the in-app
   browser the default for every link.

## Phase 1: how it works

| Piece | Where | What |
|---|---|---|
| Route | `Route.WebPage(url, preferTor, id)` (commons) | A screen like any other, so `NavigationLayoutType` decides where it goes. `id` tells two pages of the same address apart (each tab or window has its own title). Android never opens it: its browser is an activity in its own task. |
| Screen | `WebPageScreen` + `WebPageViewModel` (desktopApp) | Registered by `DesktopAppRoot` (`composableFromEndArgs`). The ViewModel is scoped to the nav entry, so the page runs as long as the screen exists (a tab behind another, a screen under the top one) and closes with it. Retry and the onion switch start the page again in place. |
| Page | `CefBrowserSession` | Chromium paints it off-screen (`OffscreenCefBrowser` into `OffscreenNappletView`) and the screen draws it (`OffscreenPage`, shared with the napplet in a note), so it clips, layers and moves with the app: Compose menus and toasts draw over it, which Chromium's native view would have covered. Out of sight it paints one frame a second. State (loading, waiting for Tor, showing, failed) and page (url, title, back/forward, zoom) as flows. |
| Titles | `AppPlatform.webPageTitle`, `routeTitle` | A tab or window shows the page's own title, else its host; the globe icon; Copy link copies the address. |
| NIP-07 | `DesktopBrowserBridge` + the engine's extension | Each page has its own message router under its own name (`__amethystBrowserQuery_<uuid>`): removing a router unregisters its name in the renderer for every page there, so one shared name let closing a page take `window.nostr` from the pages opened after it. A content script (`browser.js`, MAIN world, `document_start`, top frames, http/https, never `napplet.local`) opens its channel on every browser router function it finds, keeps the one whose page answers (another name reaches a client with no router for it), sets `window.__nappletBridge` and runs the shared `shim.js` with `__nappletDirectBridge` + `__nappletNip07`, the path Android's browser uses. |
| Replies | CEF message router | The channel's first message is a token the host mints for the document, and every request carries it. A request is judged by that document's origin, never by where the frame is when it is read, and one from a page already navigating away is refused. A request's answer goes back on its own query, which Chromium cancels with the document. Pushes (live subscriptions) go on the channel; when Chromium cancels it, the document's subscriptions close. |
| Trust | `CefBrowserSession.onQuery` | Only the main frame may ask, and only on an origin `BrowserOriginPolicy` allows (https, .onion, loopback). Each origin is its own app to the broker: `NappletIdentity("browser", origin)`, coordinate `browser:<origin>`, the key Android's ledger uses. Capabilities: `WEBSITE_CAPABILITIES`, as an nSite's. The router functions are non-configurable, so the page's own scripts can call them, which gets them nothing the bridge doesn't. |
| Broker | `DesktopNappletHost.brokerFor` | The account's broker, shared with napplets and nSites; a page signs as the account that opened it and closes when that account leaves. |
| Consent | `ConsentWindows`, `DesktopNappletHost.consentWindows` | Questions open in dialog windows above the app. Each `browser:<origin>` question shows for one page, the last that asked as that origin; it moves to another page on the site when that one closes. |
| Network | `DesktopNappletRoutes` | A page claims a route as an nSite does: Tor when Tor is on, unless the user moved this host to the open web (`NsiteNetworkChoices` under `web:<host>`, Tor by default as Android's `WebAppNetworkRegistry`); `.onion` always Tor. Nothing loads while Tor is on and not running. |
| Navigation | `CefBrowserSession.navigationOf` / `mayFetch` | http/https load, except `napplet.local` by any spelling (the napplet hosts' origin is never served to a page). `nostr:`, `lightning:`, `mailto:` and a few other app schemes go to the system on a click. `file:`, `chrome:`, `view-source:`, `javascript:` and a top-level `data:` are refused. |
| New windows | content script, `BrowserPopupGate` | Chromium refuses an off-screen page's own new windows before asking the app (`onBeforePopup` and `onOpenURLFromTab` never fire; `window.open` returns null). So the content script turns `window.open(url)` and `target=_blank`, Ctrl- and middle-clicked links into an open request over the bridge; the page's screen opens a `Route.WebPage` for it, on Tor if the opener was, which the layout places as a tab, a window or a push. All pages share one budget: one new window a second, at most four page-opened ones at once; one asked for while the opener is out of sight waits for it. Blank and `blob:` targets are refused. `window.open` hands the page no window back. |
| History | `BrowserHistoryRegistry` | A main-frame load that did not fail is recorded off Chromium's thread, once the page has named itself (or after 1.5 s), so the Browser tab's Recent and omnibox see desktop visits. |
| Toolbar | `WebPageScreen.kt` | Back (the page's history, then out of the screen where the layout has one to go back to), forward, reload/stop, an editable address (a click selects it; Enter resolves through `OmniboxInput` with the user's search engine), the transport (Tor / https / not secure; a certificate Chromium refused shows a warning for that origin, even past Chromium's page), find, zoom (Chromium's per-host level, read back on each navigation and passed to every page on the host), the onion switch while Tor is on, and Open in browser. It narrows with the screen: the Tor caption goes first, then zoom and Open in browser. |
| Napplets, nSites | `Route.NostrApp(coordinate)`, `NostrAppScreen` + `NostrAppViewModel` | The route a favorite app pinned to the bar already used (Android's embedded tab; the desktop showed "unavailable" for it), now also what a card's or a favorite's Open pushes. The screen reads the app's event from the cache by its `kind:pubkey:d` coordinate, fetching it from relays when a restored screen comes before it, and runs a `CefNappletSession` off-screen at 60 frames a second (`SCREEN_FRAME_RATE`; one a second out of sight) under the window's sandbox toolbar (`SandboxToolbar`, shared). An nSite keeps its own Tor choice. Tab and window titles come from the app's name; sites get a globe icon, napplets a grid. A web app pinned to the bar (`Route.WebApp`) opens as a `WebPageScreen` too. Webxdc apps and intent handlers (`NappletIntentDispatcher` needs the session at once, with no screen to open on) still open windows. |
| Launch | `DesktopAppLauncher` | `launchUrl(url, preferTor, nav)` and favorite web apps push a `Route.WebPage` on the calling screen's `nav`; without Chromium, without a `nav`, without a login, or for a non-web link they hand the link to `DesktopBrowser`. `openNapplet` / `openSite` (and favorite Nostr apps) push a `Route.NostrApp` when they have a `nav` and the app can run; otherwise they open its window, which says why it can't. |

## Open (phase 1)

- **One cookie jar for every account.** JCEF has one profile; Android picks a WebView profile per
  account. Pages close when their account leaves, and NIP-07 grants are per account, but a site's
  own login cookie or localStorage from account A is still there when account B opens it. JCEF's
  `CefRequestContext.createContext` makes only in-memory contexts (no cache path), and the guard
  extension and preferences would have to be applied to each.
- **No IME composition** in pages: off-screen Chromium gets key events, not an input method.
- **Frames are copied** twice (Chromium's buffer, then a Skia bitmap) at up to 60 a second while
  the screen is on show.
- **No downloads**: Chromium cancels them without a download handler.
- **No `window.opener`**: a new window is a new page, so OAuth flows that post back to the opener
  fail.
- **No favicons** in history yet (JCEF reports no favicon URL).
- **No camera, microphone or location**: `--deny-permission-prompts` answers no, as for napplets.
- **No foreground hold** for NIP-07 while the page is out of sight (Android's
  `NappletHeldRequests`), as for napplets.
- **Note links** still open in the system browser (phase 3).

## Verified (2026-11-09)

- `CefBrowserSessionTest` in Chromium (`AMETHYST_CEF_TEST=1 xvfb-run -a ./gradlew :desktopApp:test
  --tests '*CefBrowserSessionTest*'`), off-screen as the screen runs it: a page on
  `http://127.0.0.1` gets `window.nostr`; `getPublicKey` and `signEvent` reach the signer through
  the connect and signing prompts, keyed `browser:http://127.0.0.1:<port>`; a request without the
  document's token is refused; a cross-origin frame has no `window.nostr` and its direct call to
  the router is refused; `fetch('https://napplet.local/__shell__')` is refused; a WebSocket reaches
  the server; the title and the visit are recorded; closing one page leaves another's NIP-07
  working (three runs in a row, after the per-page router names); a `target=_blank` link and
  `window.open` each ask for a screen and a blank one doesn't. The navigation and fetch policy, the
  popup budget and a Tor page failing closed with Tor down run without Chromium.
  `CefNappletSessionTest` still passes beside it (both share one engine, `SharedCefTestEngine`: CEF
  starts once per process).
- `CefNappletSessionTest.aSiteRunsAsAScreenOffScreenWithNip07`: an nSite run as a screen runs it
  (off-screen, screen frame rate) paints, survives being hidden and shown, and reaches the signer
  through `window.nostr`.
- The app, headless: a napplet opens from its card as a window of its own (Windows) and as a tab
  named after it (Tabs), running, with its connect question over it; an nSite (LN Watchdog) runs
  in a tab with its live title; Ctrl+W closes a screen window and its app with it.
- The app, headless: from the Browser tab, a site opens inside the main column (Stack, with back
  leaving the screen), as a tab named after the page (Tabs), and as a window of its own (Windows);
  the toolbar narrows with the screen; the address bar selects on click and takes a typed host;
  error and certificate pages show their own address and warning. Deck was not tried by hand. Real
  sites could not load here (the container's TLS proxy), so a live Nostr web app's login was not
  tried.
