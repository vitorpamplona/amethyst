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
package com.vitorpamplona.quartz.nipXXPodcasting20

import com.vitorpamplona.quartz.graph.Link
import com.vitorpamplona.quartz.graph.LinkTarget
import com.vitorpamplona.quartz.graph.Relation
import com.vitorpamplona.quartz.nipXXPodcasting20.episode.Podcasting20EpisodeEvent
import kotlin.test.Test
import kotlin.test.assertEquals

class NipXXPodcasting20LinksTest {
    private val id = "0".repeat(64)
    private val author = "1".repeat(64)
    private val sig = "0".repeat(128)

    @Test
    fun episodeNamesItsOriginalPublication() {
        val original = "2".repeat(64)
        val event = Podcasting20EpisodeEvent(id, author, 1, arrayOf(arrayOf("d", "episode"), arrayOf("edit", original), arrayOf("t", "Tech")), "", sig)
        assertEquals(
            listOf(
                Link(Relation.EDITED, LinkTarget.Event(original), "edit"),
                Link(Relation.HASHTAG, LinkTarget.Tag("t", "tech"), "t"),
            ),
            event.links(),
        )
    }
}
