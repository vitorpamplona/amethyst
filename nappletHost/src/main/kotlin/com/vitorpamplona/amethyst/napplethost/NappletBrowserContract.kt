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
 * Messenger contract between the main app (client) and [NappletBrowserService] (provider, in the
 * keyless `:napplet` process) for the **embedded** in-app browser. The provider hosts the WebView and
 * ships its rendered surface back through `androidx.privacysandbox.ui` (SurfaceControlViewHost), so the
 * page never renders in the key-holding main process. This channel only carries the surface handshake
 * (the `coreLibInfo` Bundle) plus chrome controls (navigate/reload/back/Tor) and URL updates — the
 * trusted address bar is drawn by the main process around the embedded surface.
 */
object NappletBrowserContract {
    /** FQN of the provider service, bound by name so the client needs no compile-time reference. */
    const val BROWSER_SERVICE_CLASS = "com.vitorpamplona.amethyst.napplethost.NappletBrowserService"

    /** Client → provider: create a browser session. Carries [KEY_URL], [KEY_PROXY_PORT], [KEY_USE_TOR], [KEY_BG_COLOR]. */
    const val MSG_CREATE_SESSION = 1

    /** Provider → client: the session's [KEY_CORE_LIB_INFO] Bundle (the SandboxedUiAdapter handle). */
    const val MSG_SESSION_READY = 2

    /** Client → provider: load [KEY_URL]. */
    const val MSG_NAVIGATE = 3

    /** Client → provider: reload the current page. */
    const val MSG_RELOAD = 4

    /** Client → provider: go back in the page history. */
    const val MSG_BACK = 5

    /** Client → provider: route this session over Tor ([KEY_USE_TOR]) or the open web. */
    const val MSG_SET_TOR = 6

    /** Provider → client: the page navigated; carries [KEY_URL] and [KEY_CAN_GO_BACK]. */
    const val MSG_URL_CHANGED = 7

    /**
     * Provider → client: the page reported an IME event (a focused editable, a blur, or an external
     * text change). The raw JSON envelope (`{type:"ime.focus"|...}`) is carried in [KEY_IME_PAYLOAD]; the
     * embedded surface can't host the soft keyboard, so the main app shows it and relays editing.
     */
    const val MSG_IME_EVENT = 8

    /** Client → provider: an IME editing op for the focused field; raw JSON in [KEY_IME_PAYLOAD]. */
    const val MSG_IME_OP = 9

    /**
     * Provider → client: the main-frame load state changed. Carries [KEY_IS_LOADING] (a navigation is in
     * flight), [KEY_LOAD_FAILED] (the main frame errored), and [KEY_URL] (the page it settled on). Lets
     * the main process draw a loading spinner / error overlay over the embedded surface, and recover a
     * favorite whose session came up on a blank page (re-navigate to its real URL).
     */
    const val MSG_LOAD_STATE = 10

    /**
     * Provider → client: a JavaScript console message (console.log / warn / error / debug). Carries
     * [KEY_CONSOLE_LEVEL], [KEY_CONSOLE_MESSAGE], [KEY_CONSOLE_SOURCE], and [KEY_CONSOLE_LINE].
     */
    const val MSG_CONSOLE_LOG = 11

    /**
     * Client → provider: capture a magnified slice of the live page for the native-style selection loupe.
     * Host-side `PixelCopy` of the sandboxed surface returns `ERROR_SOURCE_NO_DATA` (the WebView pixels live
     * in a child SurfaceControl the host never draws into), so we capture INSIDE the provider — where the
     * WebView is a real in-window view. Carries [KEY_MAG_X]/[KEY_MAG_Y] (surface px center),
     * [KEY_MAG_BOX_W]/[KEY_MAG_BOX_H] (source rectangle, px), [KEY_MAG_ZOOM], and [KEY_MAG_REQ_T] (the
     * client's `nanoTime` send stamp, echoed back so the client can drop stale out-of-order frames).
     */
    const val MSG_MAGNIFIER_REQUEST = 12

    /**
     * Provider → client: the captured loupe frame. Carries [KEY_MAG_BYTES] (a PNG well under the Binder
     * limit), [KEY_MAG_W]/[KEY_MAG_H], [KEY_MAG_CAPTURE_MS] (provider-side draw+encode time), and the echoed
     * [KEY_MAG_REQ_T] so the client matches it to its request / drops stale frames.
     */
    const val MSG_MAGNIFIER_FRAME = 13

    const val KEY_MAG_X = "magX"
    const val KEY_MAG_Y = "magY"
    const val KEY_MAG_BOX_W = "magBoxW"
    const val KEY_MAG_BOX_H = "magBoxH"
    const val KEY_MAG_ZOOM = "magZoom"
    const val KEY_MAG_REQ_T = "magReqT"
    const val KEY_MAG_BYTES = "magBytes"
    const val KEY_MAG_W = "magW"
    const val KEY_MAG_H = "magH"
    const val KEY_MAG_CAPTURE_MS = "magCaptureMs"

    const val KEY_IS_LOADING = "isLoading"
    const val KEY_LOAD_FAILED = "loadFailed"

    const val KEY_CONSOLE_LEVEL = "consoleLevel"
    const val KEY_CONSOLE_MESSAGE = "consoleMessage"
    const val KEY_CONSOLE_SOURCE = "consoleSource"
    const val KEY_CONSOLE_LINE = "consoleLine"

    const val KEY_IME_PAYLOAD = "imePayload"

    const val KEY_URL = "url"
    const val KEY_PROXY_PORT = "proxyPort"
    const val KEY_USE_TOR = "useTor"
    const val KEY_CORE_LIB_INFO = "coreLibInfo"
    const val KEY_CAN_GO_BACK = "canGoBack"

    /**
     * ARGB of Amethyst's theme background, passed from the main process. The WebView (and the surface
     * before its first frame) lives in the keyless `:napplet` process, which has no access to the main
     * app's Compose theme, so without this the pre-load background defaults to white instead of the
     * app's (often dark) background.
     */
    const val KEY_BG_COLOR = "bgColor"

    /**
     * The user's theme preference name ("DARK", "LIGHT", or "SYSTEM"), passed from the main process so
     * the keyless `:napplet` process can apply the matching night mode and let embedded WebView content
     * respond correctly to `prefers-color-scheme`.
     */
    const val KEY_THEME = "theme"

    /**
     * Opaque per-tab session id the client stamps on [MSG_CREATE_SESSION] and every control message
     * ([MSG_NAVIGATE]/[MSG_RELOAD]/[MSG_BACK]/[MSG_SET_TOR]). A single provider instance is shared by all
     * embedded browser tabs (they bind the same Intent), so this scopes a control to the right surface —
     * and routes URL updates / NIP-07 traffic back to the right tab.
     */
    const val KEY_SESSION_ID = "sessionId"
}
