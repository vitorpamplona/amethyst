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
package com.vitorpamplona.quartz.nip18Reposts

import com.vitorpamplona.quartz.nip01Core.core.TagArrayBuilder
import com.vitorpamplona.quartz.nip10Notes.TextNoteEvent
import com.vitorpamplona.quartz.nip10Notes.content.findNostrUris
import com.vitorpamplona.quartz.nip18Reposts.quotes.quotes
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/**
 * Regression test for the inverted guard in TagArrayBuilder.addUniqueValueIfNew that
 * silently dropped every `q` (quote) tag from events built with the TagArrayBuilder DSL.
 */
class QuoteTagBuilderTest {
    @Test
    fun addressableQuoteAddsQTag() {
        // naddr for a kind-36787 music track (see investigated event 759e543b...).
        val content =
            "nostr:naddr1qq8hgunpvd4j6at0v5mkcv35x9hqzxthwden5te0wfjkccte9eekummjwsh8xmmrd9skctczyrt5unwa5r40e3uv8z2h3e6rvkpfj8cfxrsv9fcj93w2hl9d06lz5qcyqqqglvc3w67s7"

        val entities = findNostrUris(content)
        assertEquals(1, entities.size, "the naddr should be parsed into a single entity")

        val template =
            TextNoteEvent.build(content) {
                quotes(findNostrUris(content))
            }

        val qTag = template.tags.firstOrNull { it.isNotEmpty() && it[0] == "q" }
        assertNotNull(qTag, "a `q` tag must be added for the quoted naddr")
        assertEquals(
            "36787:d74e4ddda0eafcc78c389578e7436582991f0930e0c2a7122c5cabfcad7ebe2a:track-uoe7l241n",
            qTag[1],
        )
    }

    @Test
    fun addUniqueValueIfNewGuardSemantics() {
        val builder = TagArrayBuilder<TextNoteEvent>()

        // well-formed tag is kept
        builder.addUniqueValueIfNew(arrayOf("q", "value1"))
        // a name-only tag is ignored (no value at index 1)
        builder.addUniqueValueIfNew(arrayOf("q"))
        // a duplicate value is not added twice
        builder.addUniqueValueIfNew(arrayOf("q", "value1"))
        // a distinct value is kept
        builder.addUniqueValueIfNew(arrayOf("q", "value2"))

        val qTags = builder.build().filter { it.isNotEmpty() && it[0] == "q" }
        assertEquals(listOf("value1", "value2"), qTags.map { it[1] })
    }
}
