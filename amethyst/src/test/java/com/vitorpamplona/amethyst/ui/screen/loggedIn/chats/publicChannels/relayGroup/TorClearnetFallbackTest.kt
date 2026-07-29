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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.publicChannels.relayGroup

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The "Can't reach this relay over Tor" banner used to be driven by a bare 6s timer, so it appeared on
 * a relay that was connected and delivering. These pin the liveness gates that replaced it.
 */
class TorClearnetFallbackTest {
    private fun offer(
        usesTor: Boolean = true,
        torIsUp: Boolean = true,
        isConnected: Boolean = false,
        hasLoadedContent: Boolean = false,
        graceElapsed: Boolean = true,
    ) = shouldOfferTorClearnetFallback(usesTor, torIsUp, isConnected, hasLoadedContent, graceElapsed)

    @Test
    fun offersWhenTorRoutedRelayNeverAnswers() {
        assertTrue(offer())
    }

    @Test
    fun silentWhileTheRelayIsConnected() {
        // The regression: the socket is open and the relay is replying, so there is nothing to escape.
        assertFalse(offer(isConnected = true))
        assertFalse(offer(isConnected = true, hasLoadedContent = true))
    }

    @Test
    fun silentWhenContentFromTheRelayIsOnScreen() {
        // A momentary disconnect over a loaded channel list is a reconnect, not an unreachable relay.
        assertFalse(offer(hasLoadedContent = true))
    }

    @Test
    fun silentWhenTheRelayIsNotRoutedOverTor() {
        // Tor can be on globally while this relay is dialed over clearnet (onion/localhost/trusted
        // presets) — blaming Tor for that relay's silence would be wrong.
        assertFalse(offer(usesTor = false))
    }

    @Test
    fun silentWhileTorItselfIsStillBootstrapping() {
        // Nothing Tor-routed connects during bootstrap — that's Tor's failure, not the relay's.
        assertFalse(offer(torIsUp = false))
    }

    @Test
    fun silentDuringTheStartupGrace() {
        assertFalse(offer(graceElapsed = false))
    }
}
