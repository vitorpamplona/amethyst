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

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Android and Desktop keep serving the exact origins they always have: the scheme became a
 * parameter for iOS, and these pin the https values it defaults to.
 */
class NappletOriginsTest {
    @Test
    fun theHttpsOriginsAreTheOnesAndroidAndDesktopServe() {
        assertEquals("https://napplet.local", NappletWebContract.ORIGIN)
        assertEquals("https://napplet.local/__shell__", NappletWebContract.SHELL_URL)
        assertEquals(NappletWebContract.ORIGIN, NappletWebContract.HTTPS.origin)
        assertEquals(NappletWebContract.SHELL_URL, NappletWebContract.HTTPS.shellUrl)
    }

    @Test
    fun appOriginsDefaultToHttps() {
        assertEquals("https://nabc.napplet.local", NappletWebContract.appOrigin("nabc"))
        assertEquals("https://nabc.napplet.local", NappletWebContract.appOrigin("nabc", HostProfile.NAPPLET))
        assertEquals("https://nabc.xdc.napplet.local", NappletWebContract.appOrigin("nabc", HostProfile.WEBXDC))
        HostProfile.entries.forEach { profile ->
            assertEquals(NappletWebContract.appOrigin("nabc", profile), NappletWebContract.HTTPS.appOrigin("nabc", profile))
        }
    }

    @Test
    fun theCustomSchemeKeepsTheHostsAndChangesOnlyTheScheme() {
        val origins = NappletWebContract.NAPPLET_SCHEME
        assertEquals("napplet://napplet.local", origins.origin)
        assertEquals("napplet://napplet.local/__shell__", origins.shellUrl)
        assertEquals("napplet://nabc.napplet.local", origins.appOrigin("nabc"))
        assertEquals("napplet://nabc.xdc.napplet.local", origins.appOrigin("nabc", HostProfile.WEBXDC))
    }
}
