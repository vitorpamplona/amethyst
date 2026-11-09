# Desktop in-app browser with NIP-07

**Date:** 2026-11-09. **Status:** phase 1 built (this doc); phases 2 and 3 open.

## Why

On Android the Browser tab, favorite web apps and the screens that launch a site (Buzz invites,
web-of-trust sites) open in Amethyst's own sandboxed WebView, which gives every page a NIP-07
`window.nostr` signed by the account, behind the shared consent. On the desktop the same shared
`BrowserScreen` ended in `DesktopAppLauncher.launchUrl` → `DesktopBrowser.open`: the system browser,
which has no signer. A Nostr web app opened from Amethyst could not log in with Amethyst.

The desktop already runs Chromium (JCEF) for napplets and nSites, with NIP-07 for nSites
(`2026-06-21-napplet-desktop-host.md`), so the browser reuses that engine, router and broker.

## Phases

1. **Built:** a plain-web window (`CefBrowserSession`) with NIP-07. `AppLauncher.launchUrl` and
   `FavoriteApp.WebApp` open in it; links in notes still go to the system browser.
2. **Open:** Chromium out of the process that holds the keys. JCEF runs with `no_sandbox`
   (`commons/plans/2026-10-31-embed-security-review.md`, Critical), and a browser shows pages from
   anywhere, so this matters more now than it did for napplets alone.
3. **Open, after 2:** an "Open in Amethyst browser" action on links, or a setting to make the in-app
   browser the default for every link.

## Phase 1: how it works

| Piece | Where | What |
|---|---|---|
| Window | `CefBrowserSession` | One Chromium browser per window, its own `CefClient`. State (loading, waiting for Tor, showing, failed) and page (url, title, back/forward, zoom) as flows. |
| NIP-07 | `DesktopBrowserBridge` + the engine's extension | A content script (`browser.js`, MAIN world, `document_start`, top frames, http/https, never `napplet.local`) finds the shared router's query function `__amethystBrowserQuery` (Chromium makes it non-configurable, so the page's own scripts can call it too, which gets them nothing the bridge doesn't), sets `window.__nappletBridge` and runs the shared `shim.js` with `__nappletDirectBridge` + `__nappletNip07`, the path Android's browser uses. |
| Replies | CEF message router | A request is a non-persistent query; its answer goes back on that query, which Chromium cancels with the document, so an answer never reaches the next page. Pushes (live subscriptions) go on one persistent query per document, its channel; when Chromium cancels it, the document's subscriptions close. |
| Trust | `CefBrowserSession.onQuery` | Only the main frame may ask, and only on an origin `BrowserOriginPolicy` allows (https, .onion, loopback). Each origin is its own app to the broker: `NappletIdentity("browser", origin)`, coordinate `browser:<origin>`, the key Android's ledger uses. Capabilities: `WEBSITE_CAPABILITIES`, as an nSite's. |
| Broker | `DesktopNappletHost.brokerFor` | The account's broker, shared with napplets and nSites; the window signs as the account that opened it and closes when that account leaves. |
| Consent | `ConsentWindows(apps, title, host)` | Now takes a set of apps: a browser window shows the questions of every origin it has asked under. |
| Network | `DesktopNappletRoutes` | The window claims a route as an nSite does: Tor when Tor is on, unless the user moved this host to the open web (`NsiteNetworkChoices` under `web:<host>`, Tor by default as Android's `WebAppNetworkRegistry`); `.onion` always Tor. Nothing loads while Tor is on and not running. |
| Navigation | `CefBrowserSession.navigationOf` / `mayFetch` | http/https load, except `napplet.local` by any spelling (the napplet hosts' origin is never served to a page). `nostr:`, `lightning:`, `mailto:` and a few other app schemes go to the system on a click. `file:`, `chrome:`, `view-source:`, `javascript:` and a top-level `data:` are refused. |
| Popups | `onBeforePopup` | `target=_blank` / `window.open` open another browser window, at most one a second, signing as the same account. |
| History | `BrowserHistoryRegistry` | A main-frame load that did not fail is recorded, so the Browser tab's Recent and omnibox see desktop visits. |
| Chrome | `DesktopBrowserWindows.kt` | Back, forward, reload/stop, an editable address (Enter resolves through `OmniboxInput` with the user's search engine), the transport (Tor / https / not secure; a certificate Chromium refused drops the lock for that origin, even past the warning), find, zoom (Chromium's per-host level), the onion switch while Tor is on, and Open in browser. |
| Launch | `DesktopAppLauncher` | `launchUrl` and favorite web apps open a browser window; without Chromium, without a login, or for a non-web link they fall back to `DesktopBrowser`. |

## Open (phase 1)

- **One cookie jar for every account.** JCEF has one profile; Android picks a WebView profile per
  account. Windows close when their account leaves, and NIP-07 grants are per account, but a site's
  own login cookie or localStorage from account A is still there when account B opens it. A
  per-account `CefRequestContext` with its own cache path would fix it, if the guard extension and
  the preferences can be applied to it.
- **No downloads**: Chromium cancels them without a download handler.
- **No `window.opener`**: a popup is a new window, so OAuth flows that post back to the opener fail.
- **No favicons** in history yet (JCEF reports no favicon URL).
- **No camera, microphone or location**: `--deny-permission-prompts` answers no, as for napplets.
- **No foreground hold** for NIP-07 while the window is in the background (Android's
  `NappletHeldRequests`), as for napplets.
- **Note links** still open in the system browser (phase 3).

## Verified (2026-11-09)

- `CefBrowserSessionTest` in Chromium (`AMETHYST_CEF_TEST=1 xvfb-run -a ./gradlew :desktopApp:test
  --tests '*CefBrowserSessionTest*'`): a page on `http://127.0.0.1` gets `window.nostr`,
  `getPublicKey` and `signEvent` reach the signer through the connect and signing prompts, keyed
  `browser:http://127.0.0.1:<port>`; a cross-origin frame inside it has no `window.nostr` and a
  direct call to the router from it is refused; `fetch('https://napplet.local/__shell__')` is
  refused; the title and the visit are recorded; closing one window leaves another's NIP-07
  working. The navigation and fetch policy, and a Tor window failing closed with Tor down, run
  without Chromium. `CefNappletSessionTest` still passes beside it (both share one engine,
  `SharedCefTestEngine`: CEF starts once per process).
- The app, headless: the Browser tab's Discover entry opens a browser window instead of the system
  browser; it waits for Tor, the onion switch reopens it on the open web, the address bar takes a
  typed host, back follows, and the certificate warning (the container's TLS proxy) drops the lock.
  Real sites could not load here (that proxy), so a live Nostr web app's login was not tried.
