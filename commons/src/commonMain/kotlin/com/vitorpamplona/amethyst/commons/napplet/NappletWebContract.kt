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
    /** Internal host the trusted **shell** is served from (never the user's keys). */
    const val HOST = "napplet.local"
    const val ORIGIN = "https://napplet.local"

    /** The trusted shell document (top frame, on the shell [ORIGIN], where the native bridge lives). */
    const val SHELL_URL = "$ORIGIN/__shell__"

    /**
     * Per-site origin retained for Amethyst's NIP-5A WEBSITE profile. NIP-5D napplets never navigate
     * here: their verified, self-contained `/index.html` is assigned through `srcdoc` and therefore
     * executes with an opaque origin in an `allow-scripts`-only sandbox.
     */
    fun appOrigin(appId: String): String = "https://$appId.$HOST"

    /** True for the shell host and any per-applet subdomain — i.e. everything we serve internally. */
    fun isInternalHost(host: String?): Boolean = host == HOST || (host != null && host.endsWith(".$HOST"))

    /** Placeholders in [SHELL_HTML_PATH] replaced by the host before serving the trusted shell. */
    const val APP_ORIGIN_PLACEHOLDER = "__APP_ORIGIN__"
    const val APP_SANDBOX_PLACEHOLDER = "__APP_SANDBOX__"
    const val APP_BOOTSTRAP_PLACEHOLDER = "__APP_BOOTSTRAP__"

    /** Name of the origin-restricted native bridge the shell (and only the shell) can reach. */
    const val BRIDGE_NAME = "__nappletBridge"

    /**
     * CSP for the shell document: it may inline its own bridge script/style and frame **only this
     * applet's** origin, but has no network and cannot navigate or submit anywhere.
     */
    fun shellCsp(frameSource: String): String =
        "default-src 'none'; script-src 'unsafe-inline'; style-src 'unsafe-inline'; " +
            "frame-src $frameSource; base-uri 'none'; form-action 'none'; frame-ancestors 'none'"

    /**
     * Conservative NIP-5D CSP injected as the first element of the verified napplet's `head` before
     * the runtime prelude. A `srcdoc` napplet has an opaque origin, so self-hosted subresources are
     * intentionally unavailable; a conforming napplet is one self-contained `/index.html`.
     */
    const val APP_CSP: String =
        "default-src 'none'; script-src 'unsafe-inline'; style-src 'unsafe-inline'; " +
            "img-src data: blob:; font-src data:; connect-src 'none'; worker-src 'none'; " +
            "child-src 'none'; frame-src 'none'; media-src 'none'; object-src 'none'; " +
            "manifest-src 'none'; base-uri 'none'; form-action 'none'"

    /**
     * Injects host-owned policy and the runtime prelude before any authored element in `head`.
     * [locked] is the NIP-5D posture; WEBSITE callers deliberately retain Amethyst's NIP-07 and
     * normal-origin behavior. Only syntactically valid, explicitly authorized NAP domains are
     * projected onto `window.napplet` by the trusted [shimJs].
     */
    fun injectPrelude(
        html: ByteArray,
        shimJs: String,
        declaredDomains: List<String>,
        locked: Boolean,
        injectNip07: Boolean = false,
        imeProxy: Boolean = false,
    ): ByteArray {
        val text = html.decodeToString()
        val policy =
            if (locked) {
                "<meta http-equiv=\"Content-Security-Policy\" content=\"$APP_CSP\">"
            } else {
                ""
            }
        val style = "<style>html,body{overscroll-behavior:none !important}</style>"
        val flags =
            "<script>window.__nappletNip07=$injectNip07;" +
                (if (imeProxy) "window.__nappletImeProxy=true;" else "") +
                "</script>"
        val safeDomains =
            declaredDomains
                .filter { it.matches(NAP_DOMAIN) && it in NappletCapability.supportedNapDomains }
                .distinct()
        val domainsJson = safeDomains.joinToString(prefix = "[", postfix = "]") { "\"$it\"" }
        val prelude = "$policy$style$flags<script>window.__nappletDomains=$domainsJson;$shimJs</script>"
        val headIdx = text.indexOf("<head", ignoreCase = true)
        val injected =
            when {
                headIdx >= 0 -> {
                    val close = text.indexOf('>', headIdx)
                    if (close >= 0) text.substring(0, close + 1) + prelude + text.substring(close + 1) else prelude + text
                }
                else -> {
                    val htmlIdx = text.indexOf("<html", ignoreCase = true)
                    val htmlClose = if (htmlIdx >= 0) text.indexOf('>', htmlIdx) else -1
                    if (htmlClose >= 0) {
                        text.substring(0, htmlClose + 1) + "<head>$prelude</head>" + text.substring(htmlClose + 1)
                    } else {
                        "<head>$prelude</head>$text"
                    }
                }
            }
        return injected.encodeToByteArray()
    }

    const val SHELL_HTML_PATH = "files/napplet/shell.html"
    const val SHIM_JS_PATH = "files/napplet/shim.js"

    /**
     * Asset directory the compose-resources of `:commons` are packaged under on Android (mirrors the
     * prefix the generated [Res] accessor prepends). Hosts that run in a process without an
     * initialized compose-resources context — e.g. the isolated `:napplet` process, which skips app
     * init to stay key-free — read [SHELL_HTML_PATH]/[SHIM_JS_PATH] straight from `assets` under this
     * root instead of going through [shellHtml]/[shimJs].
     */
    const val RESOURCE_ASSET_ROOT = "composeResources/com.vitorpamplona.amethyst.commons.resources/"

    /** The trusted shell page's HTML, read from the shared bundle. */
    @OptIn(ExperimentalResourceApi::class)
    suspend fun shellHtml(): ByteArray = Res.readBytes(SHELL_HTML_PATH)

    /** The `window.napplet` client shim a host injects into the applet document. */
    @OptIn(ExperimentalResourceApi::class)
    suspend fun shimJs(): ByteArray = Res.readBytes(SHIM_JS_PATH)

    private val NAP_DOMAIN = Regex("^[a-z][a-z0-9-]*$")
}
