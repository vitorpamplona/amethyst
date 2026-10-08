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
package com.vitorpamplona.quartz.experimental.topEight

import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.hints.types.PubKeyHint
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.tags.people.PTag
import com.vitorpamplona.quartz.nip50Search.SearchableEvent
import com.vitorpamplona.quartz.utils.EventFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TopEightEventTest {
    // Synthetic keys: a Top 8 names real people, so no live sample is copied here.
    private fun key(n: Int) = n.toString(16).padStart(2, '0').repeat(32)

    private val author = key(0xaa)
    private val relay = NormalizedRelayUrl("wss://relay.example.com/")

    /** Shaped like the live Ditto event: ranked `p` tags, an alt and Ditto's client tag. */
    private fun fixture(): Event =
        EventFactory.create(
            id = "00".repeat(32),
            pubKey = author,
            createdAt = 1_791_391_740L,
            kind = 18678,
            tags =
                arrayOf(
                    arrayOf("alt", "Top 8: this user's eight favorite people, in order"),
                    arrayOf("p", key(1), relay.url),
                    arrayOf("p", key(2)),
                    arrayOf("p", "not-a-key"),
                    arrayOf("p", key(1)),
                    arrayOf("p", key(3)),
                    arrayOf("client", "Ditto", "31990:781a1527055f74c1f70230f10384609b34548f8ab6a0a6caa74025827f9fdae5:ditto"),
                    arrayOf("published_at", "1791391740"),
                ),
            content = "",
            sig = "00".repeat(64),
        )

    private fun topEight(vararg tags: Array<String>) = TopEightEvent("00".repeat(32), author, 1L, arrayOf(*tags), "", "00".repeat(64))

    @Test
    fun factoryDispatch() {
        val event = fixture()
        assertIs<TopEightEvent>(event)
        assertTrue(EventFactory.isKnownKind(TopEightEvent.KIND))
        assertFalse(event is SearchableEvent)
        assertEquals("", (event as? TopEightEvent)?.dTag())
    }

    @Test
    fun ranksInTagOrderDroppingDuplicatesAndMalformedKeys() {
        val event = assertIs<TopEightEvent>(fixture())
        assertEquals(listOf(key(1), key(2), key(3)), event.rankedKeys())
        assertEquals(1, event.rankOf(key(1)))
        assertEquals(3, event.rankOf(key(3)))
        assertNull(event.rankOf(key(9)))
        assertFalse(event.isEmpty())
    }

    @Test
    fun ignoresEntriesPastTheEighth() {
        val event = topEight(*(1..20).map { arrayOf("p", key(it)) }.toTypedArray())
        assertEquals((1..8).map { key(it) }, event.rankedKeys())
        assertEquals((1..8).map { key(it) }, event.linkedPubKeys())
    }

    @Test
    fun hintsAndLinks() {
        val event = assertIs<TopEightEvent>(fixture())
        assertEquals(listOf(key(1), key(2), key(3)), event.linkedPubKeys())
        assertEquals(listOf(PubKeyHint(key(1), relay)), event.pubKeyHints())
    }

    @Test
    fun listWithNoValidPeople() {
        val event = topEight(arrayOf("alt", "Top 8"), arrayOf("p"), arrayOf("p", ""))
        assertTrue(event.isEmpty())
        assertEquals(emptyList(), event.linkedPubKeys())
        assertEquals(emptyList(), event.pubKeyHints())
    }

    @Test
    fun buildCapsAndKeepsOrder() {
        val people = (10 downTo 1).map { PTag(key(it)) } + PTag(key(10))
        val template = TopEightEvent.build(people, createdAt = 1L)
        assertEquals(TopEightEvent.KIND, template.kind)
        assertEquals("", template.content)
        assertEquals(8, template.tags.count { it[0] == "p" })
        assertEquals((10 downTo 3).map { key(it) }, topEight(*template.tags).rankedKeys())
    }

    @Test
    fun rerankKeepsUnknownTagsAndContent() {
        val current = assertIs<TopEightEvent>(fixture())
        val template = TopEightEvent.rerank(current, listOf(PTag(key(3)), PTag(key(1), relay)), createdAt = 2L)
        val updated = topEight(*template.tags)
        assertEquals(listOf(key(3), key(1)), updated.rankedKeys())
        assertTrue(template.tags.any { it[0] == "alt" })
        assertTrue(template.tags.any { it[0] == "client" })
        assertEquals(current.content, template.content)
    }
}
