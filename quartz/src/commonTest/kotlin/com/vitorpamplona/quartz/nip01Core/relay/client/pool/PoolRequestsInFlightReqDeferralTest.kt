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
import com.vitorpamplona.quartz.nip01Core.relay.commands.toClient.EoseMessage
import com.vitorpamplona.quartz.nip01Core.relay.commands.toRelay.Command
import com.vitorpamplona.quartz.nip01Core.relay.commands.toRelay.ReqCmd
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * A filter change on a sub whose previous REQ is still awaiting EOSE is not sent: it
 * waits for that EOSE. Pinned as today's behaviour, not as the goal.
 *
 * NIP-47 shares one sub id across requests and publishes the request event right after
 * widening the filter with its id, so inside this window the wallet's ephemeral
 * kind-23195 reply can reach the relay before the relay knows to forward it. An instant
 * refusal (QUOTA_EXCEEDED) can land there; a reply after routing (PAYMENT_FAILED) cannot.
 * That is the suspected cause of a refusal the BrollyZapper field trip saw vanish
 * (dkamy-cy6.4); the fix is tracked in dkamy-cy6.6.
 */
class PoolRequestsInFlightReqDeferralTest {
    private val relay = NormalizedRelayUrl("wss://nwc.example/")
    private val subId = "nwc"

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

    private fun nwcReplies(vararg requestIds: String) = listOf(Filter(kinds = listOf(23195), tags = mapOf("e" to requestIds.toList())))

    private fun PoolRequests.update(
        client: RecordingRelayClient,
        filters: List<Filter>,
    ) {
        val affected = addOrUpdate(subId, mapOf(relay to filters), null)
        sendToRelayIfChanged(subId, affected) { _, cmd -> client.sendOrConnectAndSync(cmd) }
    }

    @Test
    fun aFilterChangeWaitsForTheInFlightReqsEose() =
        runTest {
            val pool = PoolRequests()
            val client = RecordingRelayClient(relay)

            pool.update(client, nwcReplies("balance-poll"))
            assertEquals(1, client.sent.filterIsInstance<ReqCmd>().size, "the first REQ goes out")

            // The zap's request id is added before its EOSE arrives: nothing is sent, so a
            // request event published now is on the wire ahead of the filter that catches its reply.
            pool.update(client, nwcReplies("balance-poll", "zap"))
            assertEquals(1, client.sent.filterIsInstance<ReqCmd>().size, "the widened REQ is held back")

            pool.onIncomingMessage(client, EoseMessage(subId))
            val reqs = client.sent.filterIsInstance<ReqCmd>()
            assertEquals(2, reqs.size, "the EOSE releases it")
            assertTrue(
                reqs.last().filters.any { it.tags?.get("e")?.contains("zap") == true },
                "only then does the relay learn to forward the zap's reply",
            )
        }
}
