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
package com.vitorpamplona.amethyst.commons.napplet

import com.vitorpamplona.amethyst.commons.resources.Res
import org.jetbrains.compose.resources.ExperimentalResourceApi

/**
 * The single source of truth for the napplet **web contract** — the trusted shell page, the injected
 * `window.napplet.*` shim, the internal origin/URLs, and the two Content-Security-Policies that keep
 * the applet boxed in. Every host (the Android `:napplet` WebView today, a desktop CEF host
 * tomorrow) serves these exact bytes and headers, so an applet's sandbox can't differ by platform.
 *
 * The HTML/JS live in `commonMain/composeResources/files/napplet/` and are read via the generated
 * [Res] accessor; the constants below are duplicated nowhere else.
 */
object NappletWebContract {
    /** Internal host the sandbox is served from (an opaque sandboxed origin, never the user's keys). */
    const val HOST = "napplet.local"
    const val ORIGIN = "https://napplet.local"

    /** The trusted shell document. */
    const val SHELL_URL = "$ORIGIN/__shell__"

    /** Base path the applet's own (verified) blobs are served under. */
    const val APP_BASE = "$ORIGIN/app/"

    /** Name of the origin-restricted native bridge the shell (and only the shell) can reach. */
    const val BRIDGE_NAME = "__nappletBridge"

    /**
     * CSP for the shell document: it may inline its own bridge script/style and frame the applet, but
     * has no network and cannot navigate or submit anywhere.
     */
    const val SHELL_CSP: String =
        "default-src 'none'; script-src 'unsafe-inline'; style-src 'unsafe-inline'; " +
            "frame-src https://napplet.local; base-uri 'none'; form-action 'none'"

    /**
     * CSP for the applet document. `'self'` does not match an opaque (sandboxed) origin, so the host
     * is listed explicitly. The key lever is `connect-src 'none'`: the applet gets **no** direct
     * network — every fetch goes through the brokered, consent-gated `resource.bytes`.
     */
    const val APP_CSP: String =
        "default-src 'self' https://napplet.local; " +
            "script-src 'self' https://napplet.local 'unsafe-inline'; " +
            "style-src 'self' https://napplet.local 'unsafe-inline'; " +
            "img-src 'self' https://napplet.local data: blob:; " +
            "font-src 'self' https://napplet.local data:; " +
            "media-src 'self' https://napplet.local blob: data:; " +
            "connect-src 'none'; frame-src 'none'; object-src 'none'; base-uri 'self'; form-action 'none'"

    private const val SHELL_HTML_PATH = "files/napplet/shell.html"
    private const val SHIM_JS_PATH = "files/napplet/shim.js"

    /** The trusted shell page's HTML, read from the shared bundle. */
    @OptIn(ExperimentalResourceApi::class)
    suspend fun shellHtml(): ByteArray = Res.readBytes(SHELL_HTML_PATH)

    /** The `window.napplet` client shim a host injects into the applet document. */
    @OptIn(ExperimentalResourceApi::class)
    suspend fun shimJs(): ByteArray = Res.readBytes(SHIM_JS_PATH)
}
