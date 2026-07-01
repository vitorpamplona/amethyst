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
     * The applet runs on its **own per-applet origin** — a unique subdomain of [HOST] — served at the
     * origin root, NOT on the shell [ORIGIN]. Two reasons, both load-bearing:
     *
     *  1. **A real (non-opaque) origin is what gives the applet working, persistent storage.** An
     *     `allow-scripts`-only opaque-origin iframe has no `localStorage`/`IndexedDB`/service worker
     *     (reads throw `SecurityError`), which crash-loops essentially every SPA. A real origin with
     *     `allow-same-origin` has them, scoped and isolated per applet (subdomains don't share
     *     storage), so applets can't read each other's data.
     *  2. **Keeping it on a DISTINCT origin from the shell is what preserves the trust boundary.** The
     *     native bridge is origin-restricted to the shell [ORIGIN]; the applet, being cross-origin,
     *     still can't reach it (nor read the shell DOM) — it talks only via `postMessage`, which the
     *     shell relays. `allow-same-origin` is therefore safe here precisely because the applet is
     *     same-origin only with *itself*, never with the shell.
     *
     * The applet is served at its origin root because SPA bundlers (Vite, CRA, webpack, nsyte, …) emit
     * **absolute** asset URLs (`/assets/app.js`, `/fonts/x.woff2`) that resolve against the origin root.
     *
     * [appId] must be a stable, unique, DNS-label-safe token per applet (the host derives it from the
     * applet's author + identifier), so the same applet keeps its storage across launches.
     */
    fun appOrigin(appId: String): String = "https://$appId.$HOST"

    /** True for the shell host and any per-applet subdomain — i.e. everything we serve internally. */
    fun isInternalHost(host: String?): Boolean = host == HOST || (host != null && host.endsWith(".$HOST"))

    /** Placeholder in [SHELL_HTML_PATH] the host replaces with the per-applet [appOrigin] before serving. */
    const val APP_ORIGIN_PLACEHOLDER = "__APP_ORIGIN__"

    /** Name of the origin-restricted native bridge the shell (and only the shell) can reach. */
    const val BRIDGE_NAME = "__nappletBridge"

    /**
     * CSP for the shell document: it may inline its own bridge script/style and frame **only this
     * applet's** origin, but has no network and cannot navigate or submit anywhere.
     */
    fun shellCsp(appOrigin: String): String =
        "default-src 'none'; script-src 'unsafe-inline'; style-src 'unsafe-inline'; " +
            "frame-src $appOrigin; base-uri 'none'; form-action 'none'"

    /**
     * CSP for the applet document. The applet has a real origin now, so `'self'` resolves to its own
     * per-applet origin and the shell origin is deliberately NOT granted. The key lever is
     * `connect-src 'none'`: the applet gets **no** direct network — every fetch goes through the
     * brokered, consent-gated `resource.bytes`.
     */
    const val APP_CSP: String =
        "default-src 'self'; " +
            "script-src 'self' 'unsafe-inline'; " +
            "style-src 'self' 'unsafe-inline'; " +
            "img-src 'self' data: blob:; " +
            "font-src 'self' data:; " +
            "media-src 'self' blob: data:; " +
            "connect-src 'none'; frame-src 'none'; object-src 'none'; base-uri 'self'; form-action 'none'"

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
}
