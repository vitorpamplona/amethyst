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
package com.vitorpamplona.amethyst.commons.hashtag.datasource

import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.model.cache.LocalCache
import com.vitorpamplona.quartz.nip32Labeling.LabelEvent
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The hashtag screen's label pass reads the labels the screen holds, not every note in the cache:
 * a label the screen does not hold is not looked at, and of those it holds only the follows' count.
 */
class FollowLabelsForTest {
    private val follow = "f".repeat(64)
    private val stranger = "5".repeat(64)

    private fun label(
        id: Char,
        author: String,
    ): Note {
        val event =
            LabelEvent(
                id.toString().repeat(64),
                author,
                1L,
                arrayOf(arrayOf("L", "#t"), arrayOf("l", "nostr", "#t"), arrayOf("e", "9".repeat(64))),
                "",
                "",
            )
        return Note(event.id).also { it.event = event }
    }

    @Test
    fun readsTheHeldLabelsByFollowsOnly() {
        val byFollow = label('1', follow)
        val byStranger = label('2', stranger)
        // In the cache but not held: a walk would find it, the held set does not have it.
        val notHeld = label('3', follow)
        LocalCache.justConsume(notHeld.event!!, null, true)

        assertEquals(setOf(byFollow), followLabelsFor("nostr", setOf(follow), setOf(byFollow, byStranger)))
    }
}
