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

import com.vitorpamplona.geode.InProcessRelays
import com.vitorpamplona.quartz.nip01Core.relay.client.NostrClient
import com.vitorpamplona.quartz.nip01Core.relay.client.auth.IAuthStatus
import com.vitorpamplona.quartz.nip01Core.relay.client.listeners.RelayConnectionListener
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import java.util.concurrent.CyclicBarrier
import kotlin.concurrent.thread
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Listener and auth-responder registration races: every publish, COUNT and negentropy pass
 * adds and removes its own listener from an IO thread, so registrations are concurrent. A
 * plain `set = set + x` lost one of two concurrent writes — a caller that never heard its
 * own OKs, or a removed listener that kept running on every message.
 */
class NostrClientListenerRaceTest {
    private val threads = 8
    private val perThread = 500

    private fun <T> race(
        make: (Int) -> T,
        op: (T) -> Unit,
    ): List<T> {
        val items = List(threads * perThread, make)
        val barrier = CyclicBarrier(threads)
        (0 until threads)
            .map { t ->
                thread {
                    barrier.await()
                    for (i in 0 until perThread) op(items[t * perThread + i])
                }
            }.forEach { it.join() }
        return items
    }

    @Test
    fun concurrentListenerRegistrationsAreNeverLost() {
        val hub = InProcessRelays()
        val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
        val client = NostrClient(hub, scope)
        try {
            val added = race({ object : RelayConnectionListener {} }) { client.addConnectionListener(it) }
            val missing = added.count { !client.hasConnectionListener(it) }
            assertEquals(0, missing, "listeners lost to concurrent adds")

            race({ added[it] }) { client.removeConnectionListener(it) }
            assertTrue(added.none { client.hasConnectionListener(it) }, "listeners resurrected by concurrent removes")
        } finally {
            client.close()
            scope.cancel()
            hub.close()
        }
    }

    @Test
    fun concurrentAuthResponderRegistrationsAreNeverLost() {
        val hub = InProcessRelays()
        val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
        val client = NostrClient(hub, scope)
        try {
            val added =
                race({
                    object : IAuthStatus {
                        override fun hasFinishedAuthentication(relay: NormalizedRelayUrl) = true
                    }
                }) { client.addAuthResponder(it) }
            assertEquals(added.size, client.authResponders().size, "auth responders lost to concurrent adds")

            race({ added[it] }) { client.removeAuthResponder(it) }
            assertTrue(client.authResponders().isEmpty(), "auth responders resurrected by concurrent removes")
        } finally {
            client.close()
            scope.cancel()
            hub.close()
        }
    }
}
