/*
 * Copyright (c) 2025 Vitor Pamplona
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of
 * this software and associated documentation files (the "Software"), to deal in
 * the Software without restriction, including without limitation the rights to use,
 * copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the
 * Software, and to permit persons to whom the Software is furnished to do so,
 * subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
 * FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
 * COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN
 * AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION
 * WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package com.vitorpamplona.amethyst.napplethost

/**
 * The Messenger wire contract between the untrusted `:napplet` process (the WebView host) and
 * the main-process [NappletBrokerService]. Kept tiny and string-only on purpose: nothing the
 * applet controls is ever interpreted as a Binder object, and the only payloads are JSON
 * strings (`NappletProtocolJson`, in commons) plus the applet's identity coordinate.
 */
object NappletIpc {
    /** Host → broker: a capability request. Carries [KEY_REQUEST_ID], the identity keys, and [KEY_PAYLOAD]. */
    const val MSG_REQUEST = 1

    /** Broker → host: the matching reply. Carries [KEY_REQUEST_ID] and [KEY_PAYLOAD]. */
    const val MSG_RESPONSE = 2

    /**
     * Broker → host: an **unsolicited push** (a live `relay.event`/`relay.eose` for a subscription).
     * Carries only [KEY_PAYLOAD] — a full envelope keyed by the applet's `subId`, not a request id —
     * which the host forwards to the applet verbatim.
     */
    const val MSG_PUSH = 3

    /**
     * Host → broker: persist this nSite's network-routing choice (Tor vs open web). Carries
     * [KEY_LAUNCH_TOKEN] (so the broker resolves the trusted coordinate) and [KEY_NETWORK_USE_TOR].
     * The sandbox never persists anything itself; the main process owns the preference.
     */
    const val MSG_SET_NETWORK_MODE = 4

    /**
     * Host → broker (browser mode): mint a per-origin launch token for the visited origin in
     * [KEY_BROWSER_ORIGIN]. The broker registers a synthetic per-origin identity (so NIP-07 consent is
     * scoped to that one site) and answers with [MSG_BROWSER_TOKEN].
     */
    const val MSG_MINT_BROWSER_TOKEN = 5

    /** Broker → host: the [KEY_LAUNCH_TOKEN] minted for [KEY_BROWSER_ORIGIN]. */
    const val MSG_BROWSER_TOKEN = 6

    /**
     * Host → broker: this sandbox surface entered ([KEY_FOREGROUND] true) or left ([KEY_FOREGROUND]
     * false) the foreground. The `:napplet` host runs in its own process and so can't touch the main
     * process's resource lifecycle directly; this lets the broker hold the main process resumed (Tor,
     * relays, AUTH) while a napplet/nSite is foreground, since launching it backgrounded `MainActivity`.
     * Keyed by [KEY_LAUNCH_TOKEN] so the broker tracks each surface independently.
     */
    const val MSG_SET_FOREGROUND = 7

    /**
     * Host → broker: persist the per-host Tor choice for the direct-WebView browser
     * ([NappletBrowserActivity]). Carries [KEY_WEB_HOST] and [KEY_NETWORK_USE_TOR]. The `:napplet`
     * process can't touch the main process's preference store, so it relays the choice here.
     */
    const val MSG_SET_WEB_TOR = 8

    /**
     * Host → broker (browser mode): record a *successfully loaded* page in the device-local visit history
     * (main process only). Carries [KEY_HISTORY_URL] (the landed URL) and [KEY_HISTORY_TITLE]. Sent only
     * after a clean main-frame page-finish — never for a typed-but-failed address — so misspellings never
     * enter history. The `:napplet` process can't touch the main process's store, so it relays it here.
     */
    const val MSG_RECORD_HISTORY = 9

    /**
     * Host → broker (browser mode): store the favicon for a visited site. Carries [KEY_ICON_HOST] and
     * [KEY_ICON_BYTES] (a small PNG, scaled down by the host before sending). Captured from the WebView
     * that loaded the page, so it rides the page's own (Tor-routed) network path; the main process never
     * fetches it itself. Bytes stay well under the Binder transaction limit.
     */
    const val MSG_RECORD_ICON = 10

    /**
     * Host → broker (browser mode): toggle a URL in the main-process favorites registry. Carries
     * [KEY_FAVORITE_URL] and [KEY_FAVORITE_LABEL]. The broker adds the URL if it isn't already
     * a favorite, or removes it if it is — identical to the in-app star toggle on the home screen.
     * Fire-and-forget; no reply needed.
     */
    const val MSG_TOGGLE_WEB_FAVORITE = 11

    const val KEY_REQUEST_ID = "requestId"
    const val KEY_PAYLOAD = "payload"

    /** The landed URL of a successfully loaded browser page, for the visit-history record. */
    const val KEY_HISTORY_URL = "historyUrl"

    /** The page title of a successfully loaded browser page, for the visit-history record. */
    const val KEY_HISTORY_TITLE = "historyTitle"

    /** The host a captured favicon belongs to. */
    const val KEY_ICON_HOST = "iconHost"

    /** The captured favicon as PNG bytes. */
    const val KEY_ICON_BYTES = "iconBytes"

    /** The bare host (e.g. `example.com`) a browser Tor choice belongs to. */
    const val KEY_WEB_HOST = "webHost"

    /** The full URL (e.g. `https://example.com`) to toggle as a web favorite. */
    const val KEY_FAVORITE_URL = "favoriteUrl"

    /** A human-readable label for the favorited URL (typically the host). */
    const val KEY_FAVORITE_LABEL = "favoriteLabel"

    /** Boolean: this sandbox surface is now foreground (true) or backgrounded (false). */
    const val KEY_FOREGROUND = "foreground"

    /** The visited web origin (e.g. `https://example.com`) a browser-mode request belongs to. */
    const val KEY_BROWSER_ORIGIN = "browserOrigin"

    /** Boolean: route this site through Tor (true) or over the open web (false). */
    const val KEY_NETWORK_USE_TOR = "networkUseTor"

    /**
     * Opaque per-launch token. The sandbox cannot be trusted to state its own identity, so it sends
     * only this token; the broker resolves it through [NappletLaunchRegistry] (main process) back to
     * the trusted identity + declared capability set the launch was registered with.
     */
    const val KEY_LAUNCH_TOKEN = "launchToken"
}
