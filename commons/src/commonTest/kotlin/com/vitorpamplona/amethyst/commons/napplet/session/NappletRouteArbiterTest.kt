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

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The route every site sharing a proxy gets: Tor always wins, and Tor without a port blocks. */
class NappletRouteArbiterTest {
    private val tor = "tor site"
    private val web = "open-web site"

    @Test
    fun noSiteMeansNothingLeaves() {
        assertEquals(NappletRoute.Blocked, NappletRouteArbiter().route(9050))
    }

    @Test
    fun anOpenWebSiteAloneGoesDirect() {
        assertEquals(NappletRoute.Direct, NappletRouteArbiter().want(web, useTor = false, torPort = 9050))
    }

    @Test
    fun torWinsForEverySiteWhileOneWantsIt() {
        val arbiter = NappletRouteArbiter()
        arbiter.want(web, useTor = false, torPort = 9050)
        assertEquals(NappletRoute.Tor(9050), arbiter.want(tor, useTor = true, torPort = 9050))
        assertTrue(arbiter.torWantedByOthers(web))
        assertFalse(arbiter.torWantedByOthers(tor))
        // The Tor site leaves: the open-web one goes back to the open web.
        assertEquals(NappletRoute.Direct, arbiter.release(tor, torPort = 9050))
    }

    @Test
    fun aTorSiteWithoutTorsPortBlocksEveryone() {
        val arbiter = NappletRouteArbiter()
        arbiter.want(web, useTor = false, torPort = null)
        assertEquals(NappletRoute.Blocked, arbiter.want(tor, useTor = true, torPort = null))
        // Tor comes up, then restarts on another port: the route follows it.
        assertEquals(NappletRoute.Tor(9050), arbiter.route(9050))
        assertEquals(NappletRoute.Tor(9150), arbiter.route(9150))
        // And stops: blocked again, never direct.
        assertEquals(NappletRoute.Blocked, arbiter.route(null))
    }
}
