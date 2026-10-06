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

import com.vitorpamplona.quartz.experimental.publications.tags.WikilinkTag
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** NKBIP-01 wikilinks are positional: `["wikilink", <target>, <pubkey>, <relay>, <event id>]`. */
class WikilinkTagSlotsTest {
    private val author = "b".repeat(64)
    private val eventId = "c".repeat(64)
    private val relay = RelayUrlNormalizer.normalizeOrNull("wss://thecitadel.nostr1.com/")!!

    private fun section(vararg tags: Array<String>) = PublicationContentEvent("00".repeat(32), "a".repeat(64), 0L, arrayOf(*tags), "body", "00".repeat(64))

    @Test
    fun assembleKeepsAnEmptyRelaySlotSoTheEventIdStaysInPlace() {
        val tag = WikilinkTag.assemble("fable", author, null, eventId)
        assertEquals(listOf("wikilink", "fable", author, "", eventId), tag.toList())

        val event = section(tag)
        assertEquals(listOf(eventId), event.linkedEventIds())
        assertEquals(listOf(author), event.linkedPubKeys())
        assertTrue(event.eventHints().isEmpty())
        assertTrue(event.pubKeyHints().isEmpty())

        val link = event.wikilinks().single()
        assertEquals(eventId, link.eventId)
        assertNull(link.relay)
    }

    @Test
    fun assembleTrimsOnlyTrailingEmptySlots() {
        assertEquals(listOf("wikilink", "fable"), WikilinkTag.assemble("fable").toList())
        assertEquals(listOf("wikilink", "fable", author), WikilinkTag.assemble("fable", author).toList())
        assertEquals(listOf("wikilink", "fable", "", "", eventId), WikilinkTag.assemble("fable", eventId = eventId).toList())
        assertEquals(listOf("wikilink", "fable", author, relay.url, eventId), WikilinkTag.assemble("fable", author, relay, eventId).toList())
    }

    @Test
    fun aDroppedRelaySlotStillLinksTheEventAndNeverHintsIt() {
        // A writer that dropped the empty relay slot: the id shifted into it.
        val event = section(arrayOf("wikilink", "fable", author, eventId))

        assertEquals(listOf(eventId), event.linkedEventIds())
        assertEquals(listOf(author), event.linkedPubKeys())
        assertTrue(event.eventHints().isEmpty())
        assertTrue(event.pubKeyHints().isEmpty())

        val link = event.wikilinks().single()
        assertEquals(author, link.pubKey)
        assertEquals(eventId, link.eventId)
        assertNull(link.relay)
    }

    @Test
    fun aLabelInTheRelaySlotIsNotARelay() {
        val event = section(arrayOf("wikilink", "fable", author, "inspired-by", eventId))

        assertEquals(listOf(eventId), event.linkedEventIds())
        assertTrue(event.eventHints().isEmpty())
        assertTrue(event.pubKeyHints().isEmpty())
        assertNull(event.wikilinks().single().relay)
    }

    @Test
    fun aRealRelayHintsBothTheAuthorAndTheEvent() {
        val event = section(WikilinkTag.assemble("fable", author, relay, eventId))

        assertEquals(listOf(author to relay), event.pubKeyHints().map { it.pubkey to it.relay })
        assertEquals(listOf(eventId to relay), event.eventHints().map { it.eventId to it.relay })
    }
}
