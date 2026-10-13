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
package com.vitorpamplona.quartz.nip01Core.relay

import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.relay.client.pool.PoolEventOutboxState
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerInternal
import com.vitorpamplona.quartz.nip10Notes.TextNoteEvent
import kotlinx.coroutines.runBlocking
import java.util.concurrent.CountDownLatch
import kotlin.concurrent.thread
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Each relay's OK arrives on that relay's own socket thread. Removing a relay from the remaining set
 * was an unguarded read-modify-write, so two OKs landing together could leave one relay behind and
 * the event never finished: it stayed in the outbox and was sent again on every reconnect.
 */
class PoolEventOutboxStateConcurrencyTest {
    @Test
    fun simultaneousOksFromEveryRelayFinishTheEvent() {
        val event = runBlocking { NostrSignerInternal(KeyPair()).sign(TextNoteEvent.build("acked everywhere")) }
        val relays = (1..32).map { RelayUrlNormalizer.normalize("wss://relay$it.example.com/") }

        repeat(200) { round ->
            val state = PoolEventOutboxState(event, relays.toSet())
            val start = CountDownLatch(1)
            val threads =
                relays.map { url ->
                    thread {
                        start.await()
                        state.newResponse(url, true, "")
                    }
                }
            start.countDown()
            threads.forEach { it.join() }

            assertTrue(state.isDone(), "round $round left ${state.relaysLeft().size} relays waiting for an OK they already sent")
        }
    }
}
