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

import com.vitorpamplona.crypto.sha2.sha256
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/**
 * The single source of truth for the napplet **web contract** — the trusted shell page, the injected
 * `window.napplet.*` shim, the internal origin/URLs, and the two Content-Security-Policies that keep
 * the applet boxed in. Every host (the Android `:napplet` WebView today, a desktop CEF host
 * tomorrow) serves these exact bytes and headers, so an applet's sandbox can't differ by platform.
 *
 * The HTML/JS themselves are compose resources in commonsUI, read through [NappletWebResources]
 * there; everything here is headless, so any host builds its pages from the same rules.
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

    /**
     * Stable, unique, DNS-label-safe id for an applet's sandbox origin: a sha256 of
     * `author:identifier`, hex, truncated to 31 chars and letter-prefixed (`n`). The leading letter
     * avoids any numeric-host parsing quirk and keeps it ≤63 chars (a valid DNS label). The same
     * applet always derives the same id, so its origin, and therefore its localStorage/IndexedDB,
     * persists across launches; different applets derive different subdomains, so their storage is
     * isolated from one another. A host whose browser keeps one storage jar for every account passes
     * that account as [partition], so each account gets its own origin.
     */
    fun appId(
        author: String,
        identifier: String,
        partition: String? = null,
    ): String {
        val seed = if (partition == null) "$author:$identifier" else "$partition:$author:$identifier"
        return "n" + sha256(seed.encodeToByteArray()).toHexKey().take(31)
    }

    /** A filled-in shell page and the Content-Security-Policy header it must be served with. */
    class ShellPage(
        val html: ByteArray,
        val csp: String,
    )

    /**
     * The trusted shell for one launch: [shellHtml] with its placeholders filled for [profile].
     *
     * A NIP-5D napplet's identity is bound to the exact verified bytes that execute, so the host
     * resolves its sole `/index.html`, injects the prelude outside the aggregate hash, and passes the
     * result here as [injectedArtifact]; it reaches the opaque-origin child through `srcdoc`, never a
     * navigated `src`. A NIP-5A website keeps a dedicated real origin ([appOrigin]) with normal
     * storage and network, and NIP-07 behind the consent broker.
     */
    @OptIn(ExperimentalEncodingApi::class)
    fun shell(
        shellHtml: ByteArray,
        profile: HostProfile,
        appOrigin: String,
        injectedArtifact: ByteArray?,
    ): ShellPage {
        val sandbox: String
        val frameSource: String
        val bootstrap: String
        if (profile == HostProfile.NAPPLET) {
            val encoded = Base64.encode(requireNotNull(injectedArtifact) { "A napplet's shell needs its verified artifact" })
            sandbox = "allow-scripts"
            frameSource = "'self'"
            bootstrap =
                "var b=atob('$encoded'),u=new Uint8Array(b.length);" +
                "for(var j=0;j<b.length;j++)u[j]=b.charCodeAt(j);" +
                "iframe.srcdoc=new TextDecoder('utf-8').decode(u);"
        } else {
            sandbox = "allow-scripts allow-same-origin"
            frameSource = appOrigin
            bootstrap = "iframe.src = '$appOrigin/';"
        }
        val html =
            shellHtml
                .decodeToString()
                .replace(APP_ORIGIN_PLACEHOLDER, appOrigin)
                .replace(APP_SANDBOX_PLACEHOLDER, sandbox)
                .replace(APP_BOOTSTRAP_PLACEHOLDER, bootstrap)
                .encodeToByteArray()
        return ShellPage(html, shellCsp(frameSource))
    }

    /** True for the shell host and any per-applet subdomain — i.e. everything we serve internally. */
    fun isInternalHost(host: String?): Boolean = host == HOST || (host != null && host.endsWith(".$HOST"))

    /** Placeholders in the shell page (`files/napplet/shell.html`) replaced by [shell]. */
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
        "default-src 'none'; script-src 'unsafe-inline' 'wasm-unsafe-eval'; style-src 'unsafe-inline'; " +
            "frame-src $frameSource; base-uri 'none'; form-action 'none'; frame-ancestors 'none'"

    /**
     * Conservative NIP-5D CSP injected as the first element of the verified napplet's `head` before
     * the runtime prelude. A `srcdoc` napplet has an opaque origin, so self-hosted subresources are
     * intentionally unavailable; a conforming napplet is one self-contained `/index.html`.
     */
    const val APP_CSP: String =
        "default-src 'none'; script-src 'unsafe-inline' 'wasm-unsafe-eval'; style-src 'unsafe-inline'; " +
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
        // Establish the parser's head before *any* authored input. Searching for "<head" is unsafe:
        // it may be in a comment, quoted attribute, or after an already executable script. HTML's
        // parser merges a subsequent html element's attributes and ignores a second head start tag;
        // the authored head end/body start still closes this head normally. A repeated doctype is
        // ignored. The original artifact bytes used for verification remain untouched by this step.
        val injected = "<!doctype html><html><head>$prelude$text"
        return injected.encodeToByteArray()
    }

    private val NAP_DOMAIN = Regex("^[a-z][a-z0-9-]*$")
}
