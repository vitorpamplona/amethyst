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
package com.vitorpamplona.amethyst.commons.model.buzz

import com.vitorpamplona.quartz.nip29RelayGroups.metadata.GroupMetadataEvent
import com.vitorpamplona.quartz.utils.EventFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Buzz channel labels (NIP-CL), checked against buzz `b624eb0f2`'s `channel.rs` tests and fixture. */
class BuzzChannelLabelsTest {
    private val creator = "11".repeat(32)

    private fun metadata(vararg tags: Array<String>): GroupMetadataEvent = EventFactory.create("00".repeat(32), "aa".repeat(32), 100, GroupMetadataEvent.KIND, arrayOf(arrayOf("d", "g"), *tags), "", "22".repeat(64)) as GroupMetadataEvent

    @Test
    fun soloCreationFixtureLabelsLeaveTheChannelTypeOut() {
        // test-fixtures/solo-creation.json: the identity tags a 9007 with ["t","buzz.solo"] yields.
        val event = metadata(arrayOf("t", "stream"), arrayOf("t", "buzz.solo"), arrayOf("P", creator))
        assertEquals(listOf("buzz.solo"), BuzzChannelLabels.of(event))
        assertTrue(BuzzChannelLabels.looksLikeBuzzGroupState(event))
    }

    @Test
    fun aPreLabelsRelayOrANip29GroupIsNotMistakenForOne() {
        // A pre-NIP-CL Buzz 39000: the type alone, no creator.
        assertEquals(emptyList(), BuzzChannelLabels.of(metadata(arrayOf("t", "forum"))))
        assertFalse(BuzzChannelLabels.looksLikeBuzzGroupState(metadata(arrayOf("t", "forum"))))
        // NIP-29 hashtags.
        assertFalse(BuzzChannelLabels.looksLikeBuzzGroupState(metadata(arrayOf("t", "bitcoin"), arrayOf("t", "nostr"))))
    }

    @Test
    fun acceptsTheAllowedAlphabet() {
        assertTrue(BuzzChannelLabels.isValidSet(listOf("agent-attention", "v1.2:x", "agent-attention")))
        assertTrue(BuzzChannelLabels.isValid("a".repeat(64)))
    }

    @Test
    fun refusesBadCharactersLengthsAndTypeNames() {
        listOf("Upper", "has space", "under_score", "emoji🙂", "", "a".repeat(65)).forEach {
            assertFalse(BuzzChannelLabels.isValid(it), it)
        }
        listOf("stream", "forum", "dm", "workflow", "system").forEach {
            assertFalse(BuzzChannelLabels.isValid(it), it)
        }
    }

    @Test
    fun capsTheDistinctCount() {
        val names = (0..BuzzChannelLabels.MAX).map { "l$it" }
        assertFalse(BuzzChannelLabels.isValidSet(names))
        assertTrue(BuzzChannelLabels.isValidSet(names.take(BuzzChannelLabels.MAX)))
        // Nine tags with one repeat are 8 distinct labels.
        assertTrue(BuzzChannelLabels.isValidSet(names.take(BuzzChannelLabels.MAX) + names[0]))
    }

    @Test
    fun editTagsLeaveClearOrReplace() {
        assertEquals(emptyList(), BuzzChannelLabels.editTags(null))
        assertEquals(listOf(listOf("t", "")), BuzzChannelLabels.editTags(emptyList()).map { it.toList() })
        assertEquals(
            listOf(listOf("t", "workspace"), listOf("t", "team:core")),
            BuzzChannelLabels.editTags(listOf("workspace", "team:core", "workspace")).map { it.toList() },
        )
        assertFailsWith<IllegalArgumentException> { BuzzChannelLabels.editTags(listOf("stream")) }
    }
}
