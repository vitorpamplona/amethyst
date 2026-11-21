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
package com.vitorpamplona.amethyst.commons.model

import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.relay.client.EmptyNostrClient
import com.vitorpamplona.quartz.nip01Core.relay.client.INostrClient
import com.vitorpamplona.quartz.nip01Core.relay.client.listeners.RelayConnectionListener
import com.vitorpamplona.quartz.nip01Core.relay.client.single.IRelayClient
import com.vitorpamplona.quartz.nip01Core.relay.commands.toClient.OkMessage
import com.vitorpamplona.quartz.nip01Core.relay.commands.toRelay.Command
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer
import com.vitorpamplona.quartz.nip29RelayGroups.GroupId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * A Buzz DM command on a cold socket: the relay answers `auth-required:`, the pool re-sends the event
 * once AUTH lands (during the warm-up read, with no publish waiting on its OK), and an explicit retry
 * would then get `duplicate: already processed`. The re-send's OK names the DM and must be what we read.
 */
class PublishAwaitingOkThroughAuthTest {
    private val relay = RelayUrlNormalizer.normalize("wss://buzz.example.com")
    private val from = "11111111-1111-4111-8111-111111111111"
    private val made = "22222222-2222-4222-8222-222222222222"
    private val madeOk = "response:{\"channel_id\":\"$made\",\"created\":true}"
    private val duplicate = "duplicate: already processed"
    private val authRequired = "auth-required: sign in first"

    private val event =
        Event(
            id = "a".padEnd(64, '0'),
            pubKey = "b".padEnd(64, '0'),
            createdAt = 0L,
            kind = 41011,
            tags = emptyArray(),
            content = "",
            sig = "c".padEnd(128, '0'),
        )

    /** Answers the n-th publish with the OKs in `answers[n]`; [deliver] sends one by itself (the pool's re-send). */
    private class ScriptedClient(
        val scope: CoroutineScope,
        val answers: List<List<String>>,
    ) : INostrClient by EmptyNostrClient() {
        val listeners = mutableListOf<RelayConnectionListener>()
        var publishes = 0

        override fun addConnectionListener(listener: RelayConnectionListener) {
            listeners.add(listener)
        }

        override fun removeConnectionListener(listener: RelayConnectionListener) {
            listeners.remove(listener)
        }

        override fun publish(
            event: Event,
            relayList: Set<NormalizedRelayUrl>,
        ) {
            val oks = answers.getOrNull(publishes++).orEmpty()
            relayList.forEach { url -> oks.forEach { deliver(event.id, url, it) } }
        }

        fun deliver(
            id: String,
            url: NormalizedRelayUrl,
            message: String,
        ) {
            val ok = OkMessage(id, success = !message.startsWith("auth-required:"), message = message)
            scope.launch { listeners.toList().forEach { it.onIncomingMessage(FakeRelay(url), "", ok) } }
        }
    }

    private class FakeRelay(
        override val url: NormalizedRelayUrl,
    ) : IRelayClient {
        override fun connect() {}

        override fun needsToReconnect() = false

        override fun connectAndSyncFiltersIfDisconnected(ignoreRetryDelays: Boolean) {}

        override fun isConnected() = true

        override fun sendOrConnectAndSync(cmd: Command) {}

        override fun sendIfConnected(cmd: Command) {}

        override fun disconnect() {}
    }

    @Test
    fun thePoolsResendDuringTheWarmUpIsTheAnswer() =
        runTest {
            // A retry, if one went out, would draw the duplicate.
            val client = ScriptedClient(this, listOf(listOf(authRequired), listOf(duplicate)))
            val results =
                client.publishAwaitingOkThroughAuth(event, relay, timeoutSecs = 5) {
                    // AUTH lands mid warm-up; the pool re-sends and the relay makes the DM.
                    client.deliver(event.id, relay, madeOk)
                    // The read itself would idle on for its full timeout.
                    delay(5_000)
                }
            assertEquals(BuzzDmAddResult.Added(GroupId(made, relay), moved = true), buzzDmAddResult(results, relay, from))
            // The re-send's OK ended the warm-up, so no retry went out.
            assertEquals(1, client.publishes)
        }

    @Test
    fun aResendOkThatLandsAfterTheWarmUpStillWinsOverTheRetrysDuplicate() =
        runTest {
            // The warm-up ends before the re-send's OK is in; it lands while the retry is out, and the
            // retry's own answer is the duplicate.
            val client = ScriptedClient(this, listOf(listOf(authRequired), listOf(duplicate)))
            val results =
                client.publishAwaitingOkThroughAuth(event, relay, timeoutSecs = 5) {
                    client.deliver(event.id, relay, madeOk)
                }
            assertEquals(BuzzDmAddResult.Added(GroupId(made, relay), moved = true), buzzDmAddResult(results, relay, from))
        }

    @Test
    fun aBareDuplicateIsStillNoAnswer() =
        runTest {
            // No accepted OK was ever seen: the duplicate names no DM.
            val client = ScriptedClient(this, listOf(listOf(authRequired), listOf(duplicate)))
            val results = client.publishAwaitingOkThroughAuth(event, relay, timeoutSecs = 5) {}
            assertEquals(2, client.publishes)
            assertEquals(BuzzDmAddResult.NoAnswer, buzzDmAddResult(results, relay, from))
        }

    @Test
    fun aWarmSocketIsOneRoundTrip() =
        runTest {
            val client = ScriptedClient(this, listOf(listOf(madeOk)))
            var warmed = false
            val results = client.publishAwaitingOkThroughAuth(event, relay, timeoutSecs = 5) { warmed = true }
            assertEquals(BuzzDmAddResult.Added(GroupId(made, relay), moved = true), buzzDmAddResult(results, relay, from))
            assertEquals(1, client.publishes)
            assertEquals(false, warmed)
        }
}
