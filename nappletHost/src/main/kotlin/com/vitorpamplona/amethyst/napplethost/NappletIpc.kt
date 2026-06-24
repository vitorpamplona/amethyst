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

    const val KEY_REQUEST_ID = "requestId"
    const val KEY_PAYLOAD = "payload"

    /** The bare host (e.g. `example.com`) a browser Tor choice belongs to. */
    const val KEY_WEB_HOST = "webHost"

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
