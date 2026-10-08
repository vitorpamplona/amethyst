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
package com.vitorpamplona.amethyst.commons.service.broadcast

import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.relay.client.EmptyNostrClient
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.assertTrue

class BroadcastTrackerTest {
    private val event =
        Event(
            id = "a".padEnd(64, '0'),
            pubKey = "pub".padEnd(64, '0'),
            createdAt = 0L,
            kind = 1,
            tags = emptyArray(),
            content = "",
            sig = "sig".padEnd(128, '0'),
        )

    @Test
    fun anEventWithNoRelaysIsNotTracked() =
        runTest {
            val tracker = BroadcastTracker(outboxRelays = { emptySet() })

            tracker.trackBroadcast(event = event, relays = emptySet(), client = EmptyNostrClient())

            assertTrue(tracker.activeBroadcasts.value.isEmpty(), "nothing was sent, so there is nothing to report")
        }
}
