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

import com.vitorpamplona.quartz.nip01Core.relay.client.EmptyNostrClient
import com.vitorpamplona.quartz.nip01Core.relay.client.INostrClient
import com.vitorpamplona.quartz.nip01Core.relay.client.reqs.SubscriptionListener
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer
import com.vitorpamplona.quartz.nip01Core.signers.EventTemplate
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
    /** Captures the probe subscription so the test can play the relays. */
    private class ScriptedClient : INostrClient by EmptyNostrClient() {
        var listener: SubscriptionListener? = null

        override fun subscribe(
            subId: String,
            filters: Map<NormalizedRelayUrl, List<Filter>>,
            listener: SubscriptionListener?,
        ) {
            this.listener = listener
        }
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
    // toDiscoveryEventTemplate — only observed facts become tags
    // ------------------------------------------------------------------

    private fun tagsOf(template: EventTemplate<*>) = template.tags.map { it.toList() }

    @Test
    fun reachableVerdictTemplateCarriesLivenessAndNetwork() {
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
    fun deadVerdictTemplateHasNoRttOpen() {
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
    fun reachableWithoutMeasuredLatencyWritesZeroFlag() {
        val template =
            RelayProber
                .Verdict(fast, reachable = true, rttOpenMs = -1, rttEoseMs = 300, error = null)
                .toDiscoveryEventTemplate()

        // 0 = "reachable, latency not observed": the flag form, never an invented number.
        assertTrue(listOf("rtt-open", "0") in tagsOf(template))
    }

    @Test
    fun observedAuthWallBecomesARequirementTag() {
        val template =
            RelayProber
                .Verdict(walled, reachable = true, rttOpenMs = 90, rttEoseMs = -1, error = "closed:auth-required: sign in")
                .toDiscoveryEventTemplate()

        assertTrue(listOf("R", "auth") in tagsOf(template))
    }

    @Test
    fun policyClosedIsNotAnAuthRequirement() {
        val template =
            RelayProber
                .Verdict(walled, reachable = true, rttOpenMs = 90, rttEoseMs = -1, error = "closed:blocked: not welcome")
                .toDiscoveryEventTemplate()

        assertNull(tagsOf(template).firstOrNull { it[0] == "R" })
    }

    @Test
    fun onionRelayIsTaggedTor() {
        val onion = RelayUrlNormalizer.normalize("ws://someonionaddressabcdefghijklmnop.onion")
        val template =
            RelayProber
                .Verdict(onion, reachable = true, rttOpenMs = 900, rttEoseMs = -1, error = null)
                .toDiscoveryEventTemplate()

        assertTrue(listOf("n", "tor") in tagsOf(template))
    }
}
