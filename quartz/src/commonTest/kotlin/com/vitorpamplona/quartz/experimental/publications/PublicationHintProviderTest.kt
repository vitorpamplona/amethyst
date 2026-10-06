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
package com.vitorpamplona.quartz.experimental.publications

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PublicationHintProviderTest {
    private val pk1 = "460c25e682fda7832b52d1f22d3d22b3176d972f60dcdc3212ed8c92ef85065c"
    private val pk2 = "99bb5591c9116600f845107d31f9b59e2f7c7e09a1ff802e84f1d43da557ca64"
    private val eid = "43575072239da152afe3d7b5c70ed2beb48db2b10e60c60da45229c09c877d2a"
    private val eid2 = "98b574c3527f0ffb30b7271084e3f07480733c7289f8de424d29eae82e36c758"
    private val relay = "wss://relay.damus.io/"
    private val zero = "00".repeat(32)
    private val sig = "00".repeat(64)

    @Test
    fun indexLinksSectionsByIdAndTheOriginalSource() {
        val section = "30041:$pk1:chapter-1"
        val original = "30040:$pk2:the-book"
        val event =
            PublicationIndexEvent(
                zero,
                pk1,
                1,
                arrayOf(
                    arrayOf("d", "my-book"),
                    arrayOf("a", section, relay),
                    arrayOf("e", eid, "Chapter Two"),
                    arrayOf("A", original, relay),
                    arrayOf("E", eid2, relay, pk2),
                ),
                "",
                sig,
            )
        assertEquals(listOf(section, original), event.linkedAddressIds())
        assertEquals(listOf(section, original), event.addressHints().map { it.addressId })
        assertEquals(listOf(eid, eid2), event.linkedEventIds())
        assertEquals(listOf(eid2), event.eventHints().map { it.eventId })
    }

    @Test
    fun aDottedSectionTitleIsNotCompletedIntoARelay() {
        val event =
            PublicationIndexEvent(
                zero,
                pk1,
                1,
                arrayOf(
                    arrayOf("d", "my-book"),
                    arrayOf("e", eid, "Node.js"),
                    arrayOf("e", eid2, "Vol.2", "2"),
                    arrayOf("e", eid2, relay, "2"),
                ),
                "",
                sig,
            )
        assertEquals(listOf(eid, eid2, eid2), event.linkedEventIds())
        // the titles look like schemeless hosts; only the schemed relay is a hint
        assertEquals(listOf(relay), event.eventHints().map { it.relay.url })
    }

    @Test
    fun sectionExposesWikilinksAndItsIndex() {
        val event =
            PublicationContentEvent(
                zero,
                pk1,
                1,
                arrayOf(
                    arrayOf("d", "chapter-1"),
                    arrayOf("T", "my-book"),
                    arrayOf("wikilink", "the-farmer", pk2, relay, eid),
                    arrayOf("wikilink", "the-dog"),
                    arrayOf("wikilink", "bad", "nothex", relay, "nothex"),
                ),
                "text",
                sig,
            )
        assertEquals(listOf(pk2), event.linkedPubKeys())
        assertEquals(listOf(pk2), event.pubKeyHints().map { it.pubkey })
        assertEquals(listOf(eid), event.linkedEventIds())
        assertEquals(listOf(relay), event.eventHints().map { it.relay.url })
        assertEquals(listOf("30040:$pk1:my-book"), event.linkedAddressIds())
        assertTrue(event.addressHints().isEmpty())
    }
}
