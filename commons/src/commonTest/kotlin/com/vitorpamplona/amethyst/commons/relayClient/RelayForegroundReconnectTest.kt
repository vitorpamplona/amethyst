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
package com.vitorpamplona.amethyst.commons.relayClient

import com.vitorpamplona.quartz.nip01Core.relay.client.EmptyNostrClient
import com.vitorpamplona.quartz.nip01Core.relay.client.INostrClient
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RelayForegroundReconnectTest {
    /** Records, in order, what the foreground hook asked the pool to do. */
    private class RecordingClient : INostrClient by EmptyNostrClient() {
        val calls = mutableListOf<String>()

        override fun resetBackoff() {
            calls.add("resetBackoff")
        }

        override fun reconnect(
            onlyIfChanged: Boolean,
            ignoreRetryDelays: Boolean,
        ) {
            calls.add("reconnect(onlyIfChanged=$onlyIfChanged, ignoreRetryDelays=$ignoreRetryDelays)")
        }
    }

    private val redial = listOf("resetBackoff", "reconnect(onlyIfChanged=true, ignoreRetryDelays=true)")

    private var now = 1_000_000L
    private val client = RecordingClient()
    private val foreground = RelayForegroundReconnect(client, minAwayMs = 5_000, minIntervalMs = 30_000, nowMs = { now })

    @Test
    fun comingBackFromTheBackgroundForgivesTheBackoffAndRedials() {
        foreground.windowStarted()
        foreground.windowStopped()
        now += 60_000

        assertTrue(foreground.windowStarted())
        assertEquals(redial, client.calls)
    }

    @Test
    fun theFirstStartIsNotAReturn() {
        // Cold start: the relay services' own connect() dials; nothing was backgrounded.
        assertFalse(foreground.windowStarted())
        assertTrue(client.calls.isEmpty())
    }

    @Test
    fun aQuickTripAwayDoesNotRedial() {
        foreground.windowStarted()
        foreground.windowStopped()
        now += 2_000

        assertFalse(foreground.windowStarted())
        assertTrue(client.calls.isEmpty())
    }

    @Test
    fun movingBetweenTheAppsOwnWindowsIsNotABackgroundTrip() {
        // Android starts the next activity before it stops the last one; a desktop has several windows.
        foreground.windowStarted()
        foreground.windowStarted()
        foreground.windowStopped()
        now += 60_000
        foreground.windowStopped()
        now += 1_000

        assertFalse(foreground.windowStarted())
        assertTrue(client.calls.isEmpty())
    }

    @Test
    fun repeatedReturnsRedialAtMostOncePerInterval() {
        foreground.windowStarted()
        repeat(5) {
            foreground.windowStopped()
            now += 6_000
            foreground.windowStarted()
        }
        assertEquals(redial, client.calls)

        foreground.windowStopped()
        now += 31_000
        assertTrue(foreground.windowStarted())
        assertEquals(redial + redial, client.calls)
    }

    @Test
    fun theAwayTimeCountsFromTheLastWindowToStop() {
        foreground.windowStarted()
        foreground.windowStarted()
        foreground.windowStopped()
        now += 60_000
        // Only now is no window left: the app goes to the background here.
        foreground.windowStopped()
        now += 6_000

        assertTrue(foreground.windowStarted())
        assertEquals(redial, client.calls)
    }
}
