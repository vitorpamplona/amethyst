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

import com.vitorpamplona.geode.fixtures.SyntheticEvents
import com.vitorpamplona.geode.testing.RelayClientTest
import com.vitorpamplona.geode.testing.preload
import com.vitorpamplona.quartz.nip01Core.metadata.MetadataEvent
import com.vitorpamplona.quartz.nip01Core.relay.client.listeners.RelayConnectionListener
import com.vitorpamplona.quartz.nip01Core.relay.client.single.IRelayClient
import com.vitorpamplona.quartz.nip01Core.relay.commands.toClient.EoseMessage
import com.vitorpamplona.quartz.nip01Core.relay.commands.toClient.EventMessage
import com.vitorpamplona.quartz.nip01Core.relay.commands.toClient.Message
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip65RelayList.AdvertisedRelayListEvent
import com.vitorpamplona.quartz.utils.Log
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.Channel.Factory.UNLIMITED
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.test.Test
import kotlin.test.assertEquals

class NostrClientRepeatSubTest : RelayClientTest() {
    @Test
    fun testRepeatSubEvents() =
        runBlocking {
            // Each replaceable kind needs unique pubkeys.
            defaultRelay.preload(
                (1..150).map {
                    SyntheticEvents.fakeEvent(
                        idSeed = it,
                        kind = MetadataEvent.KIND,
                        pubKey = SyntheticEvents.hexId(it),
                    )
                },
            )
            defaultRelay.preload(
                (1..50).map {
                    SyntheticEvents.fakeEvent(
                        idSeed = 100_000 + it,
                        kind = AdvertisedRelayListEvent.KIND,
                        pubKey = SyntheticEvents.hexId(100_000 + it),
                    )
                },
            )

            val resultChannel = Channel<String>(UNLIMITED)
            val events = mutableListOf<String>()
            val mySubId = "test-sub-id-2"

            val listener =
                object : RelayConnectionListener {
                    override fun onIncomingMessage(
                        relay: IRelayClient,
                        msgStr: String,
                        msg: Message,
                    ) {
                        Log.d("Test") { "Receiving message: $msgStr" }
                        when (msg) {
                            is EventMessage -> {
                                if (mySubId == msg.subId) {
                                    resultChannel.trySend(msg.event.id)
                                }
                            }

                            is EoseMessage -> {
                                if (mySubId == msg.subId) {
                                    resultChannel.trySend("EOSE")
                                }
                            }
                        }
                    }
                }

            client.addConnectionListener(listener)

            val filters = mapOf(defaultRelayUrl to listOf(Filter(kinds = listOf(MetadataEvent.KIND), limit = 100)))
            val filtersShouldIgnore =
                mapOf(defaultRelayUrl to listOf(Filter(kinds = listOf(AdvertisedRelayListEvent.KIND), limit = 500)))
            val filtersShouldSendAfterEOSE =
                mapOf(defaultRelayUrl to listOf(Filter(kinds = listOf(AdvertisedRelayListEvent.KIND), limit = 10)))

            coroutineScope {
                launch {
                    // Drain every message the relay sends. Re-subscribing on the
                    // same id mid-stream lets the relay collapse the superseded
                    // subscription *without* an EOSE — NIP-01 says a re-REQ
                    // silently replaces the previous one (see
                    // RelaySession.handleReq, which cancels the running query).
                    // So the number of EOSEs that actually arrive is
                    // timing-dependent: anywhere from 1 (only the final filter
                    // survives to EOSE) to 3. We must not assume a fixed count;
                    // instead we drain until the relay goes quiet and check the
                    // invariants that hold for every interleaving.
                    while (true) {
                        Log.d("Test") { "Processing message ${events.size}" }
                        // simulates updates in the middle of the sub
                        if (events.size == 1) {
                            client.subscribe(mySubId, filtersShouldIgnore)
                        }
                        if (events.size == 5) {
                            client.subscribe(mySubId, filtersShouldSendAfterEOSE)
                        }
                        // Once every (collapsed) subscription has finished its
                        // historical replay the relay only holds a live tail, and
                        // nothing is being inserted, so receive() would block
                        // forever. A short idle gap is the deterministic
                        // "stream finished" signal — far longer than the
                        // sub-millisecond gaps of in-process delivery, so it never
                        // fires mid-stream.
                        val msg = withTimeoutOrNull(IDLE_DRAIN_MS) { resultChannel.receive() } ?: break
                        events.add(msg)
                    }
                }

                launch {
                    client.subscribe(mySubId, filters)
                }
            }

            client.unsubscribe(mySubId)
            client.removeConnectionListener(listener)

            // The split between the three subscriptions is inherently racy: the
            // consumer fires resub1 (filtersShouldIgnore) at events.size==1 and
            // resub2 (filtersShouldSendAfterEOSE) at events.size==5, and the relay
            // may collapse either earlier subscription before it reaches EOSE.
            // Verify only invariants that hold for every interleaving.
            val eoseCount = events.count { it == "EOSE" }
            val nonEose = events.filter { it != "EOSE" }

            // The final filter (filtersShouldSendAfterEOSE) is never superseded,
            // so its EOSE always reaches the client.
            assertEquals(true, eoseCount >= 1)
            // Every non-EOSE entry is a valid 64-char event id.
            assertEquals(true, nonEose.all { it.length == 64 })
            // The mid-stream re-REQ actually switched filters: the final
            // advertised-relay-list subscription streamed its events (seeded at
            // 100_000+). Without the switch we'd only ever see kind-0 ids (<=150).
            assertEquals(true, nonEose.any { (it.trimStart('0').toLongOrNull() ?: 0L) >= 100_000L })
            // Upper bound: metadata (<=100) + filtersShouldIgnore (<=50) +
            // filtersShouldSendAfterEOSE (<=10) + up to 3 EOSEs, plus a small
            // allowance for relays that overshoot their limit.
            assertEquals(true, events.size <= 175)
        }

    companion object {
        // No message arrives more than this long after the previous one once the
        // relay is still streaming, so this idle gap reliably marks end-of-stream.
        private const val IDLE_DRAIN_MS = 5_000L
    }
}
