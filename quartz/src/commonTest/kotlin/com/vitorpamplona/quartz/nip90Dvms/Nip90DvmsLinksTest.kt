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
package com.vitorpamplona.quartz.nip90Dvms

import com.vitorpamplona.quartz.nip01Core.links.Link
import com.vitorpamplona.quartz.nip01Core.links.LinkTarget
import com.vitorpamplona.quartz.nip01Core.links.Relation
import com.vitorpamplona.quartz.nip90Dvms.contentDiscoveryRequest.DvmContentDiscoveryRequestEvent
import com.vitorpamplona.quartz.nip90Dvms.contentDiscoveryResponse.DvmContentDiscoveryResponseEvent
import com.vitorpamplona.quartz.nip90Dvms.contentSearch.DvmContentSearchRequestEvent
import com.vitorpamplona.quartz.nip90Dvms.eventPublishSchedule.DvmEventPublishScheduleResponseEvent
import com.vitorpamplona.quartz.nip90Dvms.eventTimestamping.DvmEventTimestampingResponseEvent
import com.vitorpamplona.quartz.nip90Dvms.status.DvmStatusEvent
import com.vitorpamplona.quartz.nip90Dvms.summarization.DvmSummarizationRequestEvent
import com.vitorpamplona.quartz.nip90Dvms.textGeneration.DvmTextGenerationResponseEvent
import kotlin.test.Test
import kotlin.test.assertEquals

class Nip90DvmsLinksTest {
    private val id = "0".repeat(64)
    private val sig = "0".repeat(128)
    private val author = "f".repeat(64)
    private val dvm = "1".repeat(64)
    private val customer = "2".repeat(64)
    private val request = "3".repeat(64)
    private val inputEvent = "4".repeat(64)
    private val inputJob = "5".repeat(64)
    private val proof = "6".repeat(64)

    @Test
    fun requestLinksItsInputsAndServiceProviders() {
        val event =
            DvmSummarizationRequestEvent(
                id,
                author,
                1,
                arrayOf(
                    arrayOf("i", inputEvent, "event", "wss://relay.example/"),
                    arrayOf("i", inputJob, "job"),
                    arrayOf("i", "https://example.com/talk.mp3", "url"),
                    arrayOf("i", "summarize this", "text"),
                    arrayOf("i", "no-type"),
                    arrayOf("p", dvm),
                    arrayOf("p", "not-a-key"),
                    arrayOf("param", "user", customer),
                ),
                "",
                sig,
            )
        assertEquals(
            listOf(
                Link(Relation.INPUT, LinkTarget.Event(inputEvent), "i"),
                Link(Relation.INPUT_JOB, LinkTarget.Event(inputJob), "i"),
                Link(Relation.TAG, LinkTarget.Tag("i", "https://example.com/talk.mp3"), "i"),
                Link(Relation.SERVICE_PROVIDER, LinkTarget.User(dvm), "p"),
            ),
            event.links(),
        )
    }

    @Test
    fun discoveryRequestLinksTheUserItComputesFor() {
        val event =
            DvmContentDiscoveryRequestEvent(
                id,
                author,
                1,
                arrayOf(
                    arrayOf("p", dvm),
                    arrayOf("relays", "wss://relay.example/"),
                    arrayOf("param", "max_results", "200"),
                    arrayOf("param", "user", customer),
                ),
                "",
                sig,
            )
        assertEquals(
            listOf(
                Link(Relation.SERVICE_PROVIDER, LinkTarget.User(dvm), "p"),
                Link(Relation.FOR_USER, LinkTarget.User(customer), "param"),
            ),
            event.links(),
        )
    }

    @Test
    fun searchRequestDoesNotParseItsJsonUsersParam() {
        val event =
            DvmContentSearchRequestEvent(
                id,
                author,
                1,
                arrayOf(
                    arrayOf("i", "bitcoin", "text"),
                    arrayOf("param", "users", "[[\"p\",\"$customer\"]]"),
                ),
                "",
                sig,
            )
        assertEquals(emptyList(), event.links())
    }

    @Test
    fun resultLinksItsRequestCustomerAndInputs() {
        val event =
            DvmTextGenerationResponseEvent(
                id,
                author,
                1,
                arrayOf(
                    arrayOf("request", "{\"id\":\"$request\"}"),
                    arrayOf("e", request, "wss://relay.example/"),
                    arrayOf("i", inputEvent, "event"),
                    arrayOf("p", customer),
                    arrayOf("amount", "1000"),
                ),
                "the answer",
                sig,
            )
        assertEquals(
            listOf(
                Link(Relation.REQUEST, LinkTarget.Event(request), "e"),
                Link(Relation.INPUT, LinkTarget.Event(inputEvent), "i"),
                Link(Relation.REQUEST_AUTHOR, LinkTarget.User(customer), "p"),
            ),
            event.links(),
        )
    }

    @Test
    fun resultIdsInContentAreResultsButContentJsonIsNotParsed() {
        val tags = arrayOf(arrayOf("e", request), arrayOf("p", customer))
        val requestLinks =
            listOf(
                Link(Relation.REQUEST, LinkTarget.Event(request), "e"),
                Link(Relation.REQUEST_AUTHOR, LinkTarget.User(customer), "p"),
            )

        assertEquals(
            requestLinks + Link(Relation.RESULT, LinkTarget.Event(proof), Link.VIA_CONTENT),
            DvmEventTimestampingResponseEvent(id, author, 1, tags, proof, sig).links(),
        )
        assertEquals(
            requestLinks + Link(Relation.RESULT, LinkTarget.Event(proof), Link.VIA_CONTENT),
            DvmEventPublishScheduleResponseEvent(id, author, 1, tags, proof, sig).links(),
        )
        assertEquals(
            requestLinks,
            DvmEventTimestampingResponseEvent(id, author, 1, tags, "not an id", sig).links(),
        )
        assertEquals(
            requestLinks,
            DvmContentDiscoveryResponseEvent(id, author, 1, tags, "[[\"e\",\"$proof\"]]", sig).links(),
        )
    }

    @Test
    fun feedbackLinksOnlyItsRequestAndCustomer() {
        val event =
            DvmStatusEvent(
                id,
                author,
                1,
                arrayOf(
                    arrayOf("status", "processing"),
                    arrayOf("e", request),
                    arrayOf("p", customer),
                    arrayOf("i", inputEvent, "event"),
                ),
                "",
                sig,
            )
        assertEquals(
            listOf(
                Link(Relation.REQUEST, LinkTarget.Event(request), "e"),
                Link(Relation.REQUEST_AUTHOR, LinkTarget.User(customer), "p"),
            ),
            event.links(),
        )
    }
}
