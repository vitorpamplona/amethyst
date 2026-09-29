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

/**
 * Decides the ONE proxy route shared by every WebView in the sandbox process.
 *
 * Android's WebView proxy override is process-global, yet Tor is chosen per surface (per site): a
 * browser tab, an nSite, a full-screen page. When each surface set or cleared the override for itself,
 * the last one to do so won for everyone — opening an open-web page silently moved an already-open Tor
 * page onto the open web, with no reload and nothing on screen to say so.
 *
 * So every live surface files a claim, and the route is derived from all of them:
 * - while ANY claim wants Tor, the whole process goes through Tor (the most recent Tor claim's port — a
 *   Tor restart can move it). A Tor page is never downgraded because another surface opened.
 * - an open-web claim can name the hosts it was opted out for ([Claim.directHosts]); those, and only those,
 *   bypass the proxy. Without that, one Tor favorite pinned to the bottom bar would force every site the
 *   user took off Tor back onto it for good. The cost: a Tor page requesting one of those exact hosts
 *   reaches it directly too — only hosts the user explicitly put on the open web.
 * - with no Tor claim at all, there is no proxy.
 *
 * Not thread-safe: the caller serializes access (the sandbox touches it only on its main thread).
 */
class NappletProxyClaims {
    data class Claim(
        /** The Tor SOCKS port this surface wants, or [NO_PROXY] for the open web. */
        val torPort: Int,
        /** For an open-web claim: hosts that must go direct even while Tor is on for others. */
        val directHosts: Set<String> = emptySet(),
    )

    /** The route to apply: [torPort] > 0 routes through Tor except [bypassHosts]; else no proxy. */
    data class Route(
        val torPort: Int,
        val bypassHosts: Set<String>,
    ) {
        val usesTor: Boolean get() = torPort > 0
    }

    // Insertion-ordered; a re-claim moves the owner to the end, so the last entry is the latest claim.
    private val claims = LinkedHashMap<Any, Claim>()

    /** Files (or replaces) [owner]'s claim and returns the resulting route. */
    fun claim(
        owner: Any,
        claim: Claim,
    ): Route {
        claims.remove(owner)
        claims[owner] = claim
        return route()
    }

    /** Withdraws [owner]'s claim (the surface is gone) and returns the resulting route. */
    fun release(owner: Any): Route {
        claims.remove(owner)
        return route()
    }

    fun route(): Route {
        val torPort = claims.values.lastOrNull { it.torPort > 0 }?.torPort ?: return DIRECT
        val bypass =
            claims.values
                .filter { it.torPort <= 0 }
                .flatMapTo(HashSet()) { it.directHosts }
        return Route(torPort, bypass)
    }

    companion object {
        const val NO_PROXY = -1
        val DIRECT = Route(NO_PROXY, emptySet())
    }
}
