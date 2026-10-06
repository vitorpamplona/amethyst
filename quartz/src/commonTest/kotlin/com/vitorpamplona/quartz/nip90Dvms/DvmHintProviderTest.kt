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

import com.vitorpamplona.quartz.nip90Dvms.contentDiscoveryRequest.DvmContentDiscoveryRequestEvent
import com.vitorpamplona.quartz.nip90Dvms.contentDiscoveryResponse.DvmContentDiscoveryResponseEvent
import com.vitorpamplona.quartz.nip90Dvms.eventPublishSchedule.DvmEventPublishScheduleResponseEvent
import com.vitorpamplona.quartz.nip90Dvms.peopleSearch.DvmPeopleSearchResponseEvent
import com.vitorpamplona.quartz.nip90Dvms.status.DvmStatusEvent
import com.vitorpamplona.quartz.nip90Dvms.summarization.DvmSummarizationRequestEvent
import com.vitorpamplona.quartz.nip90Dvms.textGeneration.DvmTextGenerationResponseEvent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DvmHintProviderTest {
    private val customer = "460c25e682fda7832b52d1f22d3d22b3176d972f60dcdc3212ed8c92ef85065c"
    private val dvm = "99bb5591c9116600f845107d31f9b59e2f7c7e09a1ff802e84f1d43da557ca64"
    private val someone = "7d7ffd720b907fe597a7f454afe02f2dc1eca440baa029e9117b1c3209839377"
    private val request = "43575072239da152afe3d7b5c70ed2beb48db2b10e60c60da45229c09c877d2a"
    private val note = "b1791d7fc9ae3d38966568c257ffb3a02cbf8394cdb4805bc70f64fc3c0b6879"
    private val previousJob = "a1791d7fc9ae3d38966568c257ffb3a02cbf8394cdb4805bc70f64fc3c0b6879"
    private val relay = "wss://relay.damus.io/"
    private val relay2 = "wss://nos.lol/"
    private val id = "00".repeat(32)
    private val sig = "00".repeat(64)

    @Test
    fun requestsLinkEventAndJobInputsAndServiceProviders() {
        val event =
            DvmSummarizationRequestEvent(
                id,
                customer,
                1700000000,
                arrayOf(
                    arrayOf("i", note, "event", relay),
                    arrayOf("i", previousJob, "job"),
                    arrayOf("i", "https://example.com/article", "url"),
                    arrayOf("i", "some text", "text"),
                    arrayOf("p", dvm),
                ),
                "",
                sig,
            )

        assertEquals(listOf(note, previousJob), event.linkedEventIds())
        assertEquals(listOf(note to relay), event.eventHints().map { it.eventId to it.relay.url })
        assertEquals(listOf(dvm), event.linkedPubKeys())
        assertTrue(event.pubKeyHints().isEmpty())
    }

    @Test
    fun discoveryRequestsAlsoLinkTheUserParam() {
        val event =
            DvmContentDiscoveryRequestEvent(
                id,
                customer,
                1700000000,
                arrayOf(arrayOf("p", dvm), arrayOf("param", "user", someone), arrayOf("param", "max_results", "200")),
                "",
                sig,
            )

        assertEquals(listOf(dvm, someone), event.linkedPubKeys())
    }

    @Test
    fun responsesLinkTheJobRequestAndTheCustomer() {
        val event =
            DvmTextGenerationResponseEvent(
                id,
                dvm,
                1700000000,
                arrayOf(
                    arrayOf("request", "{}"),
                    arrayOf("e", request, relay),
                    arrayOf("i", note, "event", relay2),
                    arrayOf("p", customer),
                    arrayOf("amount", "1000"),
                ),
                "generated text",
                sig,
            )

        assertEquals(listOf(request, note), event.linkedEventIds())
        assertEquals(listOf(request to relay, note to relay2), event.eventHints().map { it.eventId to it.relay.url })
        assertEquals(listOf(customer), event.linkedPubKeys())
    }

    @Test
    fun feedbackSharesTheResponseShape() {
        val event = DvmStatusEvent(id, dvm, 1700000000, arrayOf(arrayOf("status", "processing"), arrayOf("e", request, relay), arrayOf("p", customer, relay2)), "", sig)

        assertEquals(listOf(request), event.linkedEventIds())
        assertEquals(listOf(customer to relay2), event.pubKeyHints().map { it.pubkey to it.relay.url })
    }

    @Test
    fun contentResultsAreLinkedToo() {
        val address = "30023:$someone:article"
        val event =
            DvmContentDiscoveryResponseEvent(
                id,
                dvm,
                1700000000,
                arrayOf(arrayOf("e", request, relay), arrayOf("p", customer)),
                """[["e","$note","$relay2"],["a","$address","$relay"],["e","bad"]]""",
                sig,
            )

        assertEquals(listOf(request, note), event.linkedEventIds())
        assertEquals(listOf(request to relay, note to relay2), event.eventHints().map { it.eventId to it.relay.url })
        assertEquals(listOf(address), event.linkedAddressIds())
        assertEquals(listOf(address to relay), event.addressHints().map { it.addressId to it.relay.url })
        // the legacy accessor still reads the same payload
        assertEquals(listOf(note, address, "bad"), event.innerTags())
    }

    @Test
    fun peopleResultsAreLinkedPubKeys() {
        val event =
            DvmPeopleSearchResponseEvent(
                id,
                dvm,
                1700000000,
                arrayOf(arrayOf("e", request), arrayOf("p", customer)),
                """[["p","$someone","$relay"],["p","$dvm"]]""",
                sig,
            )

        assertEquals(listOf(customer, someone, dvm), event.linkedPubKeys())
        assertEquals(listOf(someone to relay), event.pubKeyHints().map { it.pubkey to it.relay.url })
        assertEquals(listOf(someone, dvm), event.innerTags())
    }

    @Test
    fun malformedResultContentLinksNothingAndNeverThrows() {
        val event = DvmContentDiscoveryResponseEvent(id, dvm, 1700000000, arrayOf(arrayOf("e", request)), "not json", sig)
        assertEquals(listOf(request), event.linkedEventIds())
        assertTrue(event.linkedAddressIds().isEmpty())
        assertTrue(event.innerTags().isEmpty())
    }

    @Test
    fun publishedEventIdInContentIsLinked() {
        val event = DvmEventPublishScheduleResponseEvent(id, dvm, 1700000000, arrayOf(arrayOf("e", request, relay), arrayOf("p", customer)), note, sig)
        assertEquals(listOf(request, note), event.linkedEventIds())
    }
}
