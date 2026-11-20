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
package com.vitorpamplona.amethyst.desktop.app

import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DesktopDeepLinksSchemesTest {
    // A made-up pairing offer, shaped like the ones apps show (relay, secret, name).
    private val offer = "nostrconnect://83f3b2ae6aa368e8275397b9c26cf550101d63ebaab900d19dd4a4429f5ad8f5?relay=wss%3A%2F%2Frelay.example.com&secret=0s8j2djs&name=My+Client"

    @AfterTest
    fun clearQueue() {
        DesktopDeepLinks.pending.value = null
    }

    @Test
    fun anAppsPairingOfferIsADeepLink() {
        assertTrue(DesktopDeepLinks.isDeepLink(offer))
        assertEquals(offer, DesktopDeepLinks.extractFromArgs(arrayOf("--flag", offer)))

        DesktopDeepLinks.offer(offer)
        assertEquals(offer, DesktopDeepLinks.pending.value)
    }

    @Test
    fun theAppsOwnLoginCodeComingBackIsDropped() {
        val ownCode = "nostrconnect://f0e1d2c3b4a5968778695a4b3c2d1e0ff0e1d2c3b4a5968778695a4b3c2d1e0f?relay=wss%3A%2F%2Frelay.example.com&secret=mine"
        DesktopDeepLinks.markOwnHandoff(ownCode)

        // As Windows and Linux pass it: quoted, from the launcher's argument.
        DesktopDeepLinks.offer("\"$ownCode\"")
        assertNull(DesktopDeepLinks.pending.value)

        // Someone else's offer still goes through.
        DesktopDeepLinks.offer(offer)
        assertEquals(offer, DesktopDeepLinks.pending.value)
    }
}
