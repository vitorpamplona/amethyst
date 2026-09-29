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

import androidx.webkit.ProxyConfig
import androidx.webkit.ProxyController
import androidx.webkit.WebViewFeature
import com.vitorpamplona.amethyst.commons.browser.OmniboxInput
import com.vitorpamplona.amethyst.commons.napplet.NappletProxyClaims
import com.vitorpamplona.amethyst.commons.napplet.NappletProxyClaims.Claim
import com.vitorpamplona.amethyst.commons.napplet.NappletProxyClaims.Route
import com.vitorpamplona.quartz.utils.Log
import java.util.concurrent.Executor

/**
 * The single owner of the `:napplet` process's WebView proxy override. Every surface — embedded browser
 * tab, embedded nSite, full-screen browser or host — files a claim here instead of setting the override
 * itself; [NappletProxyClaims] derives the one route they all share (see there for the policy).
 *
 * The override applies asynchronously, so a surface's page load waits for [claim]'s `onReady`: a Tor
 * page's first request can no longer leave before the Tor route is in place.
 *
 * Main thread only.
 */
object WebViewProxyPolicy {
    private const val TAG = "WebViewProxyPolicy"

    private val claims = NappletProxyClaims()

    // What the WebView currently runs with (a fresh process has no override), and what is being applied.
    private var applied: Route = NappletProxyClaims.DIRECT
    private var applying: Route? = null
    private val waiting = mutableListOf<() -> Unit>()

    private val supported by lazy { WebViewFeature.isFeatureSupported(WebViewFeature.PROXY_OVERRIDE) }

    /**
     * Files [owner]'s route: through Tor on [torPort] (> 0), or the open web ([NappletProxyClaims.NO_PROXY])
     * with [directHosts] exempt from any Tor route other surfaces need. [onReady] runs on the main thread
     * once the resulting process route is in effect — immediately when nothing had to change.
     */
    fun claim(
        owner: Any,
        torPort: Int,
        directHosts: Set<String> = emptySet(),
        onReady: () -> Unit = {},
    ) {
        claims.claim(owner, Claim(torPort, directHosts))
        sync(onReady)
    }

    /** Withdraws [owner]'s claim; the route relaxes once no remaining surface needs it. */
    fun release(owner: Any) {
        claims.release(owner)
        sync {}
    }

    /**
     * The hosts an open-web surface showing [url] exempts from other surfaces' Tor route: its site, and its
     * subdomains through the bypass rule. Only for web pages, and never an onion (those only resolve via Tor).
     */
    fun directHostsOf(url: String?): Set<String> {
        if (url == null || !(url.startsWith("https://") || url.startsWith("http://"))) return emptySet()
        val host = OmniboxInput.hostOf(url)?.lowercase()?.removePrefix("www.") ?: return emptySet()
        return if (host.endsWith(".onion")) emptySet() else setOf(host)
    }

    /** Runs [onReady] once no route change is in flight (e.g. a navigation right after a Tor toggle). */
    fun whenApplied(onReady: () -> Unit) = sync(onReady)

    private fun sync(onReady: () -> Unit) {
        if (!supported) {
            onReady()
            return
        }
        val target = claims.route()
        if (target == applied && applying == null) {
            onReady()
            return
        }
        waiting += onReady
        if (target == applying) return
        applying = target
        apply(target) {
            applied = target
            // A newer route superseded this one while it applied: its own callback releases the waiters.
            if (applying == target) {
                applying = null
                val ready = waiting.toList()
                waiting.clear()
                ready.forEach { it() }
            }
        }
    }

    private fun apply(
        route: Route,
        onApplied: () -> Unit,
    ) {
        val executor = Executor { it.run() }
        runCatching {
            if (route.usesTor) {
                val config =
                    ProxyConfig
                        .Builder()
                        .addProxyRule("socks5://127.0.0.1:${route.torPort}")
                        .apply {
                            route.bypassHosts.forEach { host ->
                                addBypassRule(host)
                                addBypassRule("*.$host")
                            }
                        }.build()
                ProxyController.getInstance().setProxyOverride(config, executor, onApplied)
            } else {
                ProxyController.getInstance().clearProxyOverride(executor, onApplied)
            }
        }.onFailure {
            Log.w(TAG, "Failed to apply WebView proxy override", it)
            onApplied()
        }
    }
}
