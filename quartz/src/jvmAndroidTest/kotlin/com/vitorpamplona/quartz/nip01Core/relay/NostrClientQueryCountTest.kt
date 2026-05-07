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

import com.vitorpamplona.quartz.nip01Core.relay.client.NostrClient
import com.vitorpamplona.quartz.nip01Core.relay.client.accessories.count
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.normalizeRelayUrl
import com.vitorpamplona.quartz.testrelay.SyntheticEvents
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals

class NostrClientQueryCountTest : BaseNostrClientTest() {
    private val relayA = "ws://127.0.0.1:7771/".normalizeRelayUrl()
    private val relayB = "ws://127.0.0.1:7772/".normalizeRelayUrl()

    private val metadata = Filter(kinds = listOf(0))
    private val outboxRelays = Filter(kinds = listOf(10002))

    private suspend fun seed() {
        // 5 metadata + 3 outbox relay events on A, 2 metadata + 7 outbox on B.
        // Each event needs a distinct (kind, pubkey, dTag) to avoid replaceable-event collisions.
        fun pk(seed: Int) = SyntheticEvents.hexId(seed)
        relayHub.getOrCreate(relayA).preload(
            (1..5).map { SyntheticEvents.fakeEvent(idSeed = it, kind = 0, pubKey = pk(it)) },
        )
        relayHub.getOrCreate(relayA).preload(
            (1..3).map { SyntheticEvents.fakeEvent(idSeed = 1000 + it, kind = 10002, pubKey = pk(1000 + it)) },
        )
        relayHub.getOrCreate(relayB).preload(
            (1..2).map { SyntheticEvents.fakeEvent(idSeed = 2000 + it, kind = 0, pubKey = pk(2000 + it)) },
        )
        relayHub.getOrCreate(relayB).preload(
            (1..7).map { SyntheticEvents.fakeEvent(idSeed = 3000 + it, kind = 10002, pubKey = pk(3000 + it)) },
        )
    }

    @Test
    fun testQueryCountSuspend() =
        runBlocking {
            seed()
            val appScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
            val client = NostrClient(socketBuilder, appScope)

            val result = client.count(relayA, metadata)

            assertEquals(5, result?.count)

            client.disconnect()
            appScope.cancel()
            relayHub.close()
        }

    @Test
    fun testQueryCountSuspendAllEvents() =
        runBlocking {
            seed()
            val appScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
            val client = NostrClient(socketBuilder, appScope)

            val result = client.count(relayA, Filter())

            assertEquals(8, result?.count)

            client.disconnect()
            appScope.cancel()
            relayHub.close()
        }

    @Test
    fun testQueryCountSuspendMultipleRelays() =
        runBlocking {
            seed()
            val appScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
            val client = NostrClient(socketBuilder, appScope)

            val results =
                client.count(
                    mapOf(
                        relayA to listOf(metadata, outboxRelays),
                        relayB to listOf(metadata, outboxRelays),
                    ),
                )

            assertEquals(8, results[relayA]?.count)
            assertEquals(9, results[relayB]?.count)

            client.disconnect()
            appScope.cancel()
            relayHub.close()
        }
}
