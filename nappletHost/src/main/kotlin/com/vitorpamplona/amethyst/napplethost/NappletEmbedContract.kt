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
 * Messenger contract between the main app (client) and [NappletHostService] (provider, in the keyless
 * `:napplet` process) for an **embedded** nsite/napplet — the in-app-tab counterpart of the full-screen
 * [NappletHostActivity]. The provider hosts the verified-blob WebView and ships its rendered surface
 * back through `androidx.privacysandbox.ui` (SurfaceControlViewHost), so applet JS never runs in the
 * key-holding main process. The session config (verified manifest paths/hashes/servers, identity,
 * launch token, …) is carried in the [MSG_CREATE_SESSION] bundle using the same keys as
 * [NappletHostContract] (the activity's intent extras), so the two host paths stay in lockstep.
 *
 * The trusted chrome (shield, title, "what it can access") is drawn by the main process around the
 * embedded surface — the sandbox can't draw chrome the user should trust.
 */
object NappletEmbedContract {
    /** FQN of the provider service, bound by name so the client needs no compile-time reference. */
    const val SERVICE_CLASS = "com.vitorpamplona.amethyst.napplethost.NappletHostService"

    /** Client → provider: create the session. Bundle uses [NappletHostContract] EXTRA_* keys. */
    const val MSG_CREATE_SESSION = 1

    /** Client → provider: go back in the applet's page history. */
    const val MSG_BACK = 2

    /** Client → provider: reload. */
    const val MSG_RELOAD = 3

    /**
     * Client → provider: pause the applet's JS + timers (the tab left the foreground). Mirrors
     * [NappletHostActivity]'s onPause gating, so a backgrounded napplet — even one with an "allow
     * always" capability — can't act on the user's behalf while they aren't looking at it.
     */
    const val MSG_PAUSE = 4

    /** Client → provider: resume the applet's JS + timers (the tab returned to the foreground). */
    const val MSG_RESUME = 5

    /** Provider → client: the session's [KEY_CORE_LIB_INFO] (the SandboxedUiAdapter handle). */
    const val MSG_SESSION_READY = 10

    /** Provider → client: navigation state changed; carries [KEY_CAN_GO_BACK]. */
    const val MSG_STATE = 11

    /**
     * Provider → client: a sensitive "allow always" capability just acted on the user's behalf
     * (carries [KEY_NOTICE] — one of [NOTICE_PUBLISHED] / [NOTICE_UPLOADED] / [NOTICE_PAID]). The main
     * process surfaces it so a granted relay-publish / upload / payment can never run fully silently.
     */
    const val MSG_NOTICE = 12

    /**
     * Provider → client: the applet reported an IME event (focused editable / blur / external change);
     * raw JSON in [KEY_IME_PAYLOAD]. The embedded surface can't host the keyboard, so the main app does.
     */
    const val MSG_IME_EVENT = 13

    /** Client → provider: an IME editing op for the focused field; raw JSON in [KEY_IME_PAYLOAD]. */
    const val MSG_IME_OP = 14

    /**
     * Provider → client: the main-frame load state changed. Carries [KEY_IS_LOADING] (a load is in
     * flight) and [KEY_LOAD_FAILED] (the main frame errored). Lets the main process draw a loading
     * spinner / error+retry overlay over the embedded surface instead of a bare black/white void.
     */
    const val MSG_LOAD_STATE = 15

    /**
     * Client → provider: capture a magnified slice of the live page for the selection loupe. Host-side
     * `PixelCopy` of the sandbox surface returns `ERROR_SOURCE_NO_DATA` (the WebView pixels live in a child
     * SurfaceControl), so capture happens here, in the provider, where the WebView is a real in-window view.
     * Carries [KEY_MAG_X]/[KEY_MAG_Y] (surface px center), [KEY_MAG_BOX_W]/[KEY_MAG_BOX_H] (source rect, px),
     * [KEY_MAG_ZOOM], and [KEY_MAG_REQ_T] (echoed send stamp). Mirrors the browser path.
     */
    const val MSG_MAGNIFIER_REQUEST = 16

    /** Provider → client: the captured loupe frame — [KEY_MAG_BYTES] PNG, [KEY_MAG_W]/[KEY_MAG_H], [KEY_MAG_CAPTURE_MS], echoed [KEY_MAG_REQ_T]. */
    const val MSG_MAGNIFIER_FRAME = 17

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

    const val KEY_CORE_LIB_INFO = "coreLibInfo"
    const val KEY_CAN_GO_BACK = "canGoBack"
    const val KEY_IS_LOADING = "isLoading"
    const val KEY_LOAD_FAILED = "loadFailed"
    const val KEY_NOTICE = "notice"
    const val KEY_IME_PAYLOAD = "imePayload"

    /**
     * Opaque per-tab session id the client stamps on [MSG_CREATE_SESSION] and every control message. A
     * single provider instance is shared by all embedded napplet/nSite tabs (they bind the same Intent),
     * so this scopes a control to the right surface and routes state/notices/IME back to the right tab.
     */
    const val KEY_SESSION_ID = "sessionId"

    const val NOTICE_PUBLISHED = "published"
    const val NOTICE_UPLOADED = "uploaded"
    const val NOTICE_PAID = "paid"
}
