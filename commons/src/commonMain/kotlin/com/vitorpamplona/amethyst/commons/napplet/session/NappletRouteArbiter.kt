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
package com.vitorpamplona.amethyst.commons.napplet.session

import com.vitorpamplona.amethyst.commons.napplet.NappletProxyClaims
import com.vitorpamplona.amethyst.commons.util.KmpLock
import com.vitorpamplona.amethyst.commons.util.withLock

/** Where the traffic of every page sharing one proxy goes. */
sealed interface NappletRoute {
    /** Nothing leaves: no site is open, or one wants Tor and Tor's port isn't known. */
    data object Blocked : NappletRoute

    /** The open web, no proxy. */
    data object Direct : NappletRoute

    /** Through Tor's SOCKS listener on [port]. */
    data class Tor(
        val port: Int,
    ) : NappletRoute
}

/**
 * The one route of every site that shares a proxy, from what each asked for: Android's sandbox
 * process (one WebView proxy), Desktop's SOCKS router, one iOS data store. [NappletProxyClaims]
 * decides it, so **Tor always wins**: while any site wants Tor every site goes through Tor; with only
 * open-web sites, direct; with none, or a Tor site while Tor's port is unknown, nothing leaves.
 *
 * Each site's claim is filed again with the port every time the route is computed, so a Tor restart
 * moves the route and Tor stopping blocks it. Thread-safe.
 */
class NappletRouteArbiter {
    private val lock = KmpLock()

    // Which open site wants Tor; the claims are rebuilt from it with the current port.
    private val wantsTor = LinkedHashMap<Any, Boolean>()
    private val claims = NappletProxyClaims()

    /** Records that [owner] wants Tor ([useTor]) or the open web, and returns the route on [torPort]. */
    fun want(
        owner: Any,
        useTor: Boolean,
        torPort: Int?,
    ): NappletRoute =
        lock.withLock {
            wantsTor[owner] = useTor
            computeLocked(torPort)
        }

    /** [owner] is gone; returns the route the others are left on. */
    fun release(
        owner: Any,
        torPort: Int?,
    ): NappletRoute =
        lock.withLock {
            wantsTor.remove(owner)
            claims.release(owner)
            computeLocked(torPort)
        }

    /** The route on [torPort] (Tor's port now, null when it has none). */
    fun route(torPort: Int?): NappletRoute = lock.withLock { computeLocked(torPort) }

    /** Whether an open site other than [owner] wants Tor: moving [owner] to the open web changes nothing then. */
    fun torWantedByOthers(owner: Any): Boolean = lock.withLock { wantsTor.any { (other, tor) -> other !== owner && tor } }

    val isEmpty: Boolean get() = lock.withLock { wantsTor.isEmpty() }

    private fun computeLocked(torPort: Int?): NappletRoute {
        val port = torPort ?: NappletProxyClaims.TOR_NOT_READY
        wantsTor.forEach { (owner, tor) -> claims.claim(owner, if (tor) port else NappletProxyClaims.NO_PROXY) }
        val shared = claims.route()
        return when {
            claims.isEmpty() || shared.blocked -> NappletRoute.Blocked
            shared.usesTor -> NappletRoute.Tor(shared.torPort)
            else -> NappletRoute.Direct
        }
    }
}
