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
package com.vitorpamplona.quartz.nip66RelayMonitor.reachability

import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.relay.client.EmptyNostrClient
import com.vitorpamplona.quartz.nip01Core.relay.client.INostrClient
import com.vitorpamplona.quartz.nip01Core.relay.client.listeners.RelayConnectionListener
import com.vitorpamplona.quartz.nip01Core.relay.client.reqs.SubscriptionListener
import com.vitorpamplona.quartz.nip01Core.relay.client.single.IRelayClient
import com.vitorpamplona.quartz.nip01Core.relay.commands.toClient.OkMessage
import com.vitorpamplona.quartz.nip01Core.relay.commands.toRelay.Command
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer
import com.vitorpamplona.quartz.nip01Core.signers.EventTemplate
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerInternal
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Pins [RelayProber.probeFlow]'s streaming contract: an answering relay's verdict
 * is emitted the moment its terminal arrives, while silent relays only resolve at
 * the wave deadline — and pins the observed-facts-only tag set of
 * [toDiscoveryEventTemplate].
 */
@OptIn(ExperimentalCoroutinesApi::class)
class RelayProberFlowTest {
    /** Captures the probe subscription and publish so the test can play the relays. */
    private class ScriptedClient : INostrClient by EmptyNostrClient() {
        var listener: SubscriptionListener? = null
        var sentFilters: Map<NormalizedRelayUrl, List<Filter>>? = null
        var published: Event? = null
        val connListeners = mutableListOf<RelayConnectionListener>()

        override fun subscribe(
            subId: String,
            filters: Map<NormalizedRelayUrl, List<Filter>>,
            listener: SubscriptionListener?,
        ) {
            this.listener = listener
            this.sentFilters = filters
        }

        override fun publish(
            event: Event,
            relayList: Set<NormalizedRelayUrl>,
        ) {
            published = event
        }

        override fun addConnectionListener(listener: RelayConnectionListener) {
            connListeners += listener
        }

        override fun removeConnectionListener(listener: RelayConnectionListener) {
            connListeners -= listener
        }

        /** Plays a relay's OK answer for the published event to every armed listener. */
        suspend fun answerOk(
            relay: NormalizedRelayUrl,
            success: Boolean,
            message: String,
        ) {
            val ok = OkMessage(published!!.id, success, message)
            connListeners.toList().forEach { it.onIncomingMessage(FakeRelayClient(relay), "", ok) }
        }
    }

    private class FakeRelayClient(
        override val url: NormalizedRelayUrl,
    ) : IRelayClient {
        override fun connect() = Unit

        override fun needsToReconnect() = false

        override fun connectAndSyncFiltersIfDisconnected(ignoreRetryDelays: Boolean) = Unit

        override fun isConnected() = true

        override fun sendOrConnectAndSync(cmd: Command) = Unit

        override fun sendIfConnected(cmd: Command) = Unit

        override fun disconnect() = Unit
    }

    private val fast = RelayUrlNormalizer.normalize("wss://fast.example.com")
    private val silent = RelayUrlNormalizer.normalize("wss://silent.example.com")
    private val walled = RelayUrlNormalizer.normalize("wss://walled.example.com")

    @Test
    fun answeringRelayStreamsBeforeTheWaveDeadline() =
        runTest {
            val client = ScriptedClient()
            val arrivals = mutableListOf<Pair<RelayProber.Verdict, Long>>()

            val collector =
                launch {
                    RelayProber(client)
                        .probeFlow(listOf(fast, silent), timeoutMs = 10_000)
                        .collect { arrivals += it to currentTime }
                }
            launch {
                delay(200)
                client.listener!!.onEose(fast, null)
            }
            collector.join()

            assertEquals(listOf(fast, silent), arrivals.map { it.first.relay })
            // The EOSE'd relay resolved when it answered, not at the deadline …
            val (fastVerdict, fastAt) = arrivals[0]
            assertTrue(fastVerdict.reachable)
            assertTrue(fastAt < 1_000, "verdict should stream at answer time, arrived at ${fastAt}ms")
            // … while the silent one waited out the wave and is dead (socket never opened).
            val (silentVerdict, silentAt) = arrivals[1]
            assertFalse(silentVerdict.reachable)
            assertTrue(silentAt >= 10_000, "silent relay must wait for the deadline, arrived at ${silentAt}ms")
        }

    @Test
    fun authWalledRelayIsReachableWithTheWallRecorded() =
        runTest {
            val client = ScriptedClient()
            val arrivals = mutableListOf<RelayProber.Verdict>()

            val collector =
                launch {
                    RelayProber(client)
                        .probeFlow(listOf(walled), timeoutMs = 10_000)
                        .collect { arrivals += it }
                }
            launch {
                delay(100)
                client.listener!!.onClosed("auth-required: sign in first", walled, null)
            }
            collector.join()

            assertEquals(1, arrivals.size)
            assertTrue(arrivals[0].reachable, "an auth wall is an app-level answer: the relay works")
            assertEquals("closed:auth-required: sign in first", arrivals[0].error)
        }

    @Test
    fun connectFailureStreamsImmediatelyAsDead() =
        runTest {
            val client = ScriptedClient()
            val arrivals = mutableListOf<Pair<RelayProber.Verdict, Long>>()

            val collector =
                launch {
                    RelayProber(client)
                        .probeFlow(listOf(fast), timeoutMs = 10_000)
                        .collect { arrivals += it to currentTime }
                }
            launch {
                delay(50)
                client.listener!!.onCannotConnect(fast, "dns failure", null)
            }
            collector.join()

            val (verdict, at) = arrivals.single()
            assertFalse(verdict.reachable)
            assertEquals("cannot:dns failure", verdict.error)
            assertTrue(at < 1_000, "a failed dial must not wait for the deadline, arrived at ${at}ms")
        }

    // ------------------------------------------------------------------
    // Check options — liveness default, read-test override, write-test event
    // ------------------------------------------------------------------

    @Test
    fun livenessFilterIsTheDefaultCheck() =
        runTest {
            val client = ScriptedClient()
            val collector =
                launch {
                    RelayProber(client).probeFlow(listOf(fast), timeoutMs = 1_000).collect {}
                }
            launch {
                delay(10)
                assertEquals(RelayProber.LIVENESS_FILTERS, client.sentFilters!![fast])
                client.listener!!.onEose(fast, null)
            }
            collector.join()
        }

    @Test
    fun readTestFilterIsSentWhenChosen() =
        runTest {
            val client = ScriptedClient()
            val collector =
                launch {
                    RelayProber(client)
                        .probeFlow(listOf(fast), timeoutMs = 1_000, filters = RelayProber.readTestFilter())
                        .collect {}
                }
            launch {
                delay(10)
                val sent = client.sentFilters!![fast]!!.single()
                assertEquals(1, sent.limit, "read test defaults to limit 1")
                assertEquals(listOf(0), sent.kinds, "read test defaults to kind 0 — accepted by purpose relays too")
                assertNull(sent.ids, "read test must query real events, not the impossible id")
                client.listener!!.onEose(fast, null)
            }
            collector.join()
        }

    @Test
    fun writeTestEventIsEphemeralAndSelfExpiring() =
        kotlinx.coroutines.test.runTest {
            val template = RelayProbeWriteTest.build(createdAt = 5000)

            assertEquals(20166, template.kind)
            assertTrue(template.kind in 20000..29999, "the write probe must be an ephemeral kind")
            assertTrue(listOf("expiration", "5060") in template.tags.map { it.toList() })
        }

    // ------------------------------------------------------------------
    // readWriteCheck — honest read + write measurements, nothing claimed
    // ------------------------------------------------------------------

    private suspend fun ScriptedClient.playReadThenWrite(
        relay: NormalizedRelayUrl,
        ok: Boolean?,
        okMessage: String = "",
    ) {
        delay(50)
        listener!!.onEose(relay, null) // read phase answers
        while (published == null) delay(10) // write phase begins
        if (ok != null) answerOk(relay, ok, okMessage)
    }

    @Test
    fun readWriteCheckMeasuresBothSides() =
        runTest {
            val client = ScriptedClient()
            val signer = NostrSignerInternal(KeyPair())
            var result: Map<NormalizedRelayUrl, RelayProber.ReadWriteVerdict>? = null

            val check =
                launch {
                    result = RelayProber(client).readWriteCheck(listOf(fast), signer, timeoutMs = 5_000)
                }
            launch { client.playReadThenWrite(fast, ok = true) }
            check.join()

            val verdict = result!![fast]!!
            assertTrue(verdict.rttReadMs >= 0, "an answered read must be measured")
            assertTrue(verdict.rttWriteMs >= 0, "an answered write must be measured")
            assertEquals(true, verdict.writeAccepted)
            assertEquals(20166, client.published!!.kind, "the write test must use the ephemeral probe event")
        }

    @Test
    fun writeRejectionIsAnAnswerNotAFailure() =
        runTest {
            val client = ScriptedClient()
            val signer = NostrSignerInternal(KeyPair())
            var result: Map<NormalizedRelayUrl, RelayProber.ReadWriteVerdict>? = null

            val check =
                launch {
                    result = RelayProber(client).readWriteCheck(listOf(walled), signer, timeoutMs = 5_000)
                }
            launch { client.playReadThenWrite(walled, ok = false, okMessage = "pow: 28 bits needed") }
            check.join()

            val verdict = result!![walled]!!
            assertEquals(false, verdict.writeAccepted, "OK false is a measured policy answer")
            assertEquals("pow: 28 bits needed", verdict.writeMessage)
            assertTrue(verdict.rttWriteMs >= 0, "a rejection is still a round trip")
        }

    @Test
    fun foreignRelayOkDoesNotEndTheWriteConfirmationEarly() =
        runTest {
            // A relay OUTSIDE the checked set answering with the same event id (a
            // straggler from an earlier wave that got the same probe event) must not
            // count toward the confirmation window — before the relayList guard in
            // publishAndCollectResults, it ended the wait early and misreported the
            // real relay as silent.
            val client = ScriptedClient()
            val signer = NostrSignerInternal(KeyPair())
            val foreign = RelayUrlNormalizer.normalize("wss://foreign.example.com")
            var result: Map<NormalizedRelayUrl, RelayProber.ReadWriteVerdict>? = null

            val check =
                launch {
                    result = RelayProber(client).readWriteCheck(listOf(fast), signer, timeoutMs = 5_000)
                }
            launch {
                delay(50)
                client.listener!!.onEose(fast, null)
                while (client.published == null) delay(10)
                client.answerOk(foreign, true, "")
                delay(100)
                client.answerOk(fast, true, "")
            }
            check.join()

            val verdict = result!![fast]!!
            assertEquals(true, verdict.writeAccepted, "the listed relay's OK must still be awaited and recorded")
            assertNull(result!![foreign], "the foreign relay must not appear in the result")
        }

    @Test
    fun silentWriteLeavesTheWriteSideUnobserved() =
        runTest {
            val client = ScriptedClient()
            val signer = NostrSignerInternal(KeyPair())
            var result: Map<NormalizedRelayUrl, RelayProber.ReadWriteVerdict>? = null

            val check =
                launch {
                    result = RelayProber(client).readWriteCheck(listOf(fast), signer, timeoutMs = 2_000)
                }
            launch { client.playReadThenWrite(fast, ok = null) }
            check.join()

            val verdict = result!![fast]!!
            assertTrue(verdict.rttReadMs >= 0)
            assertNull(verdict.writeAccepted, "silence is not evidence about the write path")
            assertEquals(-1, verdict.rttWriteMs)
        }

    // ------------------------------------------------------------------
    // toDiscoveryEventTemplate — only observed facts become tags
    // ------------------------------------------------------------------

    private fun tagsOf(template: EventTemplate<*>) = template.tags.map { it.toList() }

    @Test
    fun reachableVerdictTemplateCarriesLivenessAndNetwork() =
        kotlinx.coroutines.test.runTest {
            val template =
                RelayProber
                    .Verdict(fast, reachable = true, rttOpenMs = 150, rttEoseMs = 480, error = null)
                    .toDiscoveryEventTemplate(createdAt = 1000)

            val tags = tagsOf(template)
            assertEquals(30166, template.kind)
            assertEquals(1000, template.createdAt)
            assertTrue(listOf("d", fast.url) in tags)
            assertTrue(listOf("n", "clearnet") in tags)
            assertTrue(listOf("rtt-open", "150") in tags)
            // rtt-eose is wave-relative (dial + queue + read) — never published as rtt-read.
            assertNull(tags.firstOrNull { it[0] == "rtt-read" })
        }

    @Test
    fun deadVerdictTemplateHasNoRttOpen() =
        kotlinx.coroutines.test.runTest {
            val template =
                RelayProber
                    .Verdict(silent, reachable = false, rttOpenMs = -1, rttEoseMs = -1, error = "cannot:timeout")
                    .toDiscoveryEventTemplate()

            val tags = tagsOf(template)
            assertTrue(listOf("d", silent.url) in tags)
            // Liveness is the PRESENCE of rtt-open; a dead record must not carry one.
            assertNull(tags.firstOrNull { it[0] == "rtt-open" })
        }

    @Test
    fun reachableWithoutMeasuredLatencyWritesZeroFlag() =
        kotlinx.coroutines.test.runTest {
            val template =
                RelayProber
                    .Verdict(fast, reachable = true, rttOpenMs = -1, rttEoseMs = 300, error = null)
                    .toDiscoveryEventTemplate()

            // 0 = "reachable, latency not observed": the flag form, never an invented number.
            assertTrue(listOf("rtt-open", "0") in tagsOf(template))
        }

    @Test
    fun observedAuthWallBecomesARequirementTag() =
        kotlinx.coroutines.test.runTest {
            val template =
                RelayProber
                    .Verdict(walled, reachable = true, rttOpenMs = 90, rttEoseMs = -1, error = "closed:auth-required: sign in")
                    .toDiscoveryEventTemplate()

            assertTrue(listOf("R", "auth") in tagsOf(template))
        }

    @Test
    fun policyClosedIsNotAnAuthRequirement() =
        kotlinx.coroutines.test.runTest {
            val template =
                RelayProber
                    .Verdict(walled, reachable = true, rttOpenMs = 90, rttEoseMs = -1, error = "closed:blocked: not welcome")
                    .toDiscoveryEventTemplate()

            assertNull(tagsOf(template).firstOrNull { it[0] == "R" })
        }

    @Test
    fun readWriteResultsBecomeRttTags() =
        kotlinx.coroutines.test.runTest {
            val verdict = RelayProber.Verdict(fast, reachable = true, rttOpenMs = 100, rttEoseMs = 300, error = null)
            val readWrite = RelayProber.ReadWriteVerdict(fast, rttReadMs = 40, rttWriteMs = 55, writeAccepted = true, writeMessage = "")

            val tags = tagsOf(verdict.toDiscoveryEventTemplate(readWrite = readWrite))
            assertTrue(listOf("rtt-read", "40") in tags)
            assertTrue(listOf("rtt-write", "55") in tags)
        }

    @Test
    fun unobservedReadWriteSidesStayUntagged() =
        kotlinx.coroutines.test.runTest {
            val verdict = RelayProber.Verdict(fast, reachable = true, rttOpenMs = 100, rttEoseMs = -1, error = null)
            val readWrite = RelayProber.ReadWriteVerdict(fast, rttReadMs = -1, rttWriteMs = -1, writeAccepted = null, writeMessage = null)

            val tags = tagsOf(verdict.toDiscoveryEventTemplate(readWrite = readWrite))
            assertNull(tags.firstOrNull { it[0] == "rtt-read" })
            assertNull(tags.firstOrNull { it[0] == "rtt-write" })
        }

    @Test
    fun writeRejectionReasonsBecomeRequirementTags() =
        kotlinx.coroutines.test.runTest {
            val verdict = RelayProber.Verdict(walled, reachable = true, rttOpenMs = 100, rttEoseMs = -1, error = null)

            val pow = RelayProber.ReadWriteVerdict(walled, -1, 30, writeAccepted = false, writeMessage = "pow: 28 bits needed")
            assertTrue(listOf("R", "pow") in tagsOf(verdict.toDiscoveryEventTemplate(readWrite = pow)))

            val auth = RelayProber.ReadWriteVerdict(walled, -1, 30, writeAccepted = false, writeMessage = "auth-required: sign in")
            assertTrue(listOf("R", "auth") in tagsOf(verdict.toDiscoveryEventTemplate(readWrite = auth)))

            val blocked = RelayProber.ReadWriteVerdict(walled, -1, 30, writeAccepted = false, writeMessage = "blocked: not welcome")
            assertNull(tagsOf(verdict.toDiscoveryEventTemplate(readWrite = blocked)).firstOrNull { it[0] == "R" })
        }

    @Test
    fun onionRelayIsTaggedTor() =
        kotlinx.coroutines.test.runTest {
            val onion = RelayUrlNormalizer.normalize("ws://someonionaddressabcdefghijklmnop.onion")
            val template =
                RelayProber
                    .Verdict(onion, reachable = true, rttOpenMs = 900, rttEoseMs = -1, error = null)
                    .toDiscoveryEventTemplate()

            assertTrue(listOf("n", "tor") in tagsOf(template))
        }
}
