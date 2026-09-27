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
package com.vitorpamplona.quartz.nip01Core.relay.client.pool

import com.vitorpamplona.quartz.nip01Core.relay.client.single.IRelayClient
import com.vitorpamplona.quartz.nip01Core.relay.commands.toClient.ClosedMessage
import com.vitorpamplona.quartz.nip01Core.relay.commands.toRelay.Command
import com.vitorpamplona.quartz.nip01Core.relay.commands.toRelay.ReqCmd
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * A relay with a kind allowlist CLOSES any REQ that names a kind it doesn't serve,
 * even when the other kinds in the filter are fine. relay.us.whitenoise.chat answers
 * a `{kinds:[1059, 21059], #p:[me]}` gift-wrap REQ with
 * `ERROR: bad req: filter validation failed: kind not allowed: 21059`, so an account
 * whose only DM relay it is never received a DM or a Marmot Welcome: the whole
 * subscription was refused because of the ephemeral kind riding along.
 */
class PoolRequestsKindNotAllowedTest {
    private val relay = NormalizedRelayUrl("wss://relay.us.whitenoise.chat/")
    private val other = NormalizedRelayUrl("wss://nos.lol/")
    private val refusal = "ERROR: bad req: filter validation failed: kind not allowed: 21059"

    private class RecordingRelayClient(
        override val url: NormalizedRelayUrl,
    ) : IRelayClient {
        val sent = mutableListOf<Command>()

        override fun connect() = Unit

        override fun needsToReconnect() = false

        override fun connectAndSyncFiltersIfDisconnected(ignoreRetryDelays: Boolean) = Unit

        override fun isConnected() = true

        override fun sendOrConnectAndSync(cmd: Command) {
            sent.add(cmd)
        }

        override fun sendIfConnected(cmd: Command) {
            sent.add(cmd)
        }

        override fun disconnect() = Unit
    }

    private fun giftWraps() = listOf(Filter(kinds = listOf(1059, 21059), tags = mapOf("p" to listOf("a".repeat(64)))))

    private fun sync(
        pool: PoolRequests,
        url: NormalizedRelayUrl,
    ): List<ReqCmd> {
        pool.onConnecting(url)
        val sent = mutableListOf<Command>()
        pool.syncState(url) { sent.add(it) }
        return sent.filterIsInstance<ReqCmd>()
    }

    @Test
    fun theRefusedKindIsDroppedAndTheRestIsRequestedAgainAtOnce() =
        kotlinx.coroutines.test.runTest {
            val pool = PoolRequests()
            pool.addOrUpdate("giftwraps", mapOf(relay to giftWraps()), null)
            assertEquals(
                listOf(1059, 21059),
                sync(pool, relay)
                    .single()
                    .filters
                    .single()
                    .kinds,
            )

            val client = RecordingRelayClient(relay)
            pool.onIncomingMessage(client, ClosedMessage("giftwraps", refusal))

            val retry = client.sent.filterIsInstance<ReqCmd>().single()
            assertEquals(listOf(1059), retry.filters.single().kinds, "the CLOSED sub comes straight back without 21059")
            assertEquals(mapOf("p" to listOf("a".repeat(64))), retry.filters.single().tags, "and nothing else about it changes")
        }

    @Test
    fun laterReconnectsNeverOfferTheRefusedKindAgain() =
        kotlinx.coroutines.test.runTest {
            val pool = PoolRequests()
            pool.addOrUpdate("giftwraps", mapOf(relay to giftWraps()), null)
            sync(pool, relay)
            pool.onIncomingMessage(RecordingRelayClient(relay), ClosedMessage("giftwraps", refusal))

            repeat(3) {
                assertEquals(
                    listOf(1059),
                    sync(pool, relay)
                        .single()
                        .filters
                        .single()
                        .kinds,
                )
            }
        }

    @Test
    fun aFilterAskingOnlyForTheRefusedKindIsNotSent() =
        kotlinx.coroutines.test.runTest {
            val pool = PoolRequests()
            pool.addOrUpdate("giftwraps", mapOf(relay to giftWraps()), null)
            sync(pool, relay)
            pool.onIncomingMessage(RecordingRelayClient(relay), ClosedMessage("giftwraps", refusal))

            pool.addOrUpdate("ephemeral", mapOf(relay to listOf(Filter(kinds = listOf(21059)))), null)
            val reqs = sync(pool, relay)
            assertTrue(reqs.none { it.subId == "ephemeral" }, "a REQ with no kind left would ask for EVERY kind")
        }

    @Test
    fun theKindIsOnlyRefusedOnTheRelayThatRefusedIt() =
        kotlinx.coroutines.test.runTest {
            val pool = PoolRequests()
            pool.addOrUpdate("giftwraps", mapOf(relay to giftWraps(), other to giftWraps()), null)
            sync(pool, relay)
            pool.onIncomingMessage(RecordingRelayClient(relay), ClosedMessage("giftwraps", refusal))

            assertEquals(
                listOf(1059, 21059),
                sync(pool, other)
                    .single()
                    .filters
                    .single()
                    .kinds,
            )
        }
}
