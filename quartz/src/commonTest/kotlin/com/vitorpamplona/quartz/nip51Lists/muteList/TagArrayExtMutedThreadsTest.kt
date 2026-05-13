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
package com.vitorpamplona.quartz.nip51Lists.muteList

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TagArrayExtMutedThreadsTest {
    private val id1 = "3ae34f70016c33d36f9e6ad395591ea36fee7ac488d1dad383ae64ae3d988f50"
    private val id2 = "00000000016c33d36f9e6ad395591ea36fee7ac488d1dad383ae64ae3d988f50"

    @Test fun mutedThreads_returnsEventTagsOnly() {
        val tags =
            arrayOf(
                arrayOf("e", id1),
                arrayOf("p", id2),
                arrayOf("word", "spam"),
                arrayOf("e", id2, "wss://relay.damus.io"),
            )
        val parsed = tags.mutedThreads()
        assertEquals(2, parsed.size)
        assertEquals(id1, parsed[0].eventId)
        assertEquals(id2, parsed[1].eventId)
    }

    @Test fun mutedThreadIdSet_extractsIdsAcrossMixedTags() {
        val tags =
            arrayOf(
                arrayOf("e", id1),
                arrayOf("p", id2),
                arrayOf("e", id2),
            )
        val ids = tags.mutedThreadIdSet()
        assertEquals(setOf(id1, id2), ids)
    }

    @Test fun mutedThreadIdSet_emptyOnNoEventTags() {
        val tags = arrayOf(arrayOf("p", id1), arrayOf("word", "spam"))
        assertTrue(tags.mutedThreadIdSet().isEmpty())
    }
}
