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
package com.vitorpamplona.amethyst.commons.frontpage

import com.vitorpamplona.amethyst.commons.frontpage.Fixtures.OBSERVER
import com.vitorpamplona.amethyst.commons.frontpage.Fixtures.event
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.relay.client.EmptyNostrClient
import com.vitorpamplona.quartz.nip01Core.relay.client.INostrClient
import com.vitorpamplona.quartz.nip01Core.relay.client.reqs.SubscriptionListener
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class FrontPagePressTest {
    private val relay = RelayUrlNormalizer.normalize("wss://search.brainstorm.world")

    /** A search relay that answers by who is asking: the observer, include:spam, or names. */
    private class ScriptedRelay(
        val ranked: (Filter) -> List<Event>,
        val anonymous: (Filter) -> List<Event>,
    ) : INostrClient by EmptyNostrClient() {
        val filters = mutableListOf<Filter>()

        override fun subscribe(
            subId: String,
            filters: Map<NormalizedRelayUrl, List<Filter>>,
            listener: SubscriptionListener?,
        ) {
            filters.forEach { (relay, list) ->
                list.forEach { filter ->
                    synchronized(this.filters) { this.filters.add(filter) }
                    val answer = if (filter.search.orEmpty().startsWith("observer:")) ranked(filter) else anonymous(filter)
                    answer.forEach { runBlocking { listener?.onEvent(it, isLive = false, relay = relay, forFilters = null) } }
                }
                listener?.onEose(relay, forFilters = null)
            }
        }

        override fun unsubscribe(subId: String) {}
    }

    private val note = event(1, content = "The relay answered in three seconds flat.")
    private val goodPage = "<!doctype html><html><body><q>The relay answered in three seconds flat.</q></body></html>"
    private val badPage = "<!doctype html><html><body><q>The relay never answered.</q></body></html>"

    private fun relayWithNotes() =
        ScriptedRelay(
            ranked = { if (it.kinds == listOf(1)) listOf(note) else emptyList() },
            anonymous = { if (it.kinds == listOf(1)) listOf(note, event(2)) else emptyList() },
        )

    private fun press(
        client: INostrClient,
        pages: MutableList<String>,
        calls: MutableList<List<ChatMessage>> = mutableListOf(),
    ) = FrontPagePress(
        client = client,
        relay = relay,
        boundary = JsoupPaperBoundary,
        write = { messages, onText ->
            calls.add(messages.toList())
            val text = pages.removeAt(0)
            onText(text.length)
            Written(text, Usage(100, 50))
        },
        deskIdleMs = 2_000,
        probeIdleMs = 2_000,
        now = { 1_794_268_800L },
    )

    @Test
    fun printsACleanPageFromTheRankedDesks() =
        runBlocking {
            val client = relayWithNotes()
            val steps = mutableListOf<PrintStep>()
            val result = press(client, mutableListOf(goodPage)).print(OBSERVER, "Alice") { steps.add(it) }
            val printed = assertIs<PrintResult.Printed>(result)
            assertEquals(1, printed.rounds)
            assertEquals(1, printed.quotes)
            assertEquals(listOf(note), printed.corpus.desks["notes"])
            assertEquals(1, printed.corpus.overlap)
            assertEquals(150L, printed.usage?.total)
            assertTrue(steps.any { it is PrintStep.Pull && it.desksDone == DESKS.size + 1 })
            // Every ranked desk carries the floor; the control and names say include:spam.
            assertTrue(client.filters.filter { it.search!!.startsWith("observer:") && it.search!!.contains("gte") }.size == DESKS.size)
            assertTrue(client.filters.any { it.kinds == listOf(0) && it.search == INCLUDE_SPAM })
        }

    @Test
    fun aRefusedPageGetsOneRepairRound() =
        runBlocking {
            val calls = mutableListOf<List<ChatMessage>>()
            val result = press(relayWithNotes(), mutableListOf(badPage, goodPage), calls).print(OBSERVER, "Alice")
            assertEquals(2, assertIs<PrintResult.Printed>(result).rounds)
            assertTrue(calls[1].last().content.contains("QUOTE"))
            assertEquals("assistant", calls[1][2].role)
        }

    @Test
    fun aSecondRefusalShowsNoPage() =
        runBlocking {
            val calls = mutableListOf<List<ChatMessage>>()
            val result = press(relayWithNotes(), mutableListOf(badPage, badPage), calls).print(OBSERVER, "Alice")
            assertEquals(listOf(Violation.Kind.QUOTE), assertIs<PrintResult.Refused>(result).violations.map { it.kind })
            assertEquals(2, calls.size)
        }

    @Test
    fun rankingsNotYetProjectedStopBeforeSpending() =
        runBlocking {
            val client = ScriptedRelay(ranked = { emptyList() }, anonymous = { listOf(event(9)) })
            val calls = mutableListOf<List<ChatMessage>>()
            val result = press(client, mutableListOf(goodPage), calls).print(OBSERVER, "Alice")
            assertEquals(ProbeVerdict.PROJECTION_PENDING, assertIs<PrintResult.NotReady>(result).verdict)
            assertEquals(0, calls.size)
        }
}
