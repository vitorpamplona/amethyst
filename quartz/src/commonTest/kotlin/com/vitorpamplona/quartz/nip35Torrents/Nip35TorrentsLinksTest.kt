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
package com.vitorpamplona.quartz.nip35Torrents

import com.vitorpamplona.quartz.graph.Link
import com.vitorpamplona.quartz.graph.LinkTarget
import com.vitorpamplona.quartz.graph.Relation
import kotlin.test.Test
import kotlin.test.assertEquals

class Nip35TorrentsLinksTest {
    private val id = "0".repeat(64)
    private val author = "1".repeat(64)
    private val sig = "0".repeat(128)
    private val torrent = "2".repeat(64)
    private val middle = "3".repeat(64)
    private val parent = "4".repeat(64)
    private val quoted = "5".repeat(64)
    private val parentAuthor = "a".repeat(64)
    private val other = "b".repeat(64)

    @Test
    fun torrentLinksItsCatalogueIdsAndTheDescriptionsReferences() {
        val event =
            TorrentEvent(
                id,
                author,
                1,
                arrayOf(
                    arrayOf("title", "Movie"),
                    arrayOf("x", "infohash"),
                    arrayOf("i", "imdb:tt0000001"),
                    arrayOf("t", "Movie"),
                    arrayOf("q", quoted),
                    arrayOf("p", other),
                    arrayOf("r", "https://example.com"),
                ),
                "",
                sig,
            )
        assertEquals(
            listOf(
                Link(Relation.TAG, LinkTarget.Tag("i", "imdb:tt0000001"), "i"),
                Link(Relation.HASHTAG, LinkTarget.Tag("t", "movie"), "t"),
                Link(Relation.QUOTE, LinkTarget.Event(quoted), "q"),
                Link(Relation.MENTION, LinkTarget.User(other), "p"),
                Link(Relation.TAG, LinkTarget.Tag("r", "https://example.com"), "r"),
            ),
            event.links(),
        )
    }

    @Suppress("DEPRECATION")
    @Test
    fun markedCommentNamesTheParentsAuthor() {
        val event =
            TorrentCommentEvent(
                id,
                author,
                1,
                arrayOf(
                    arrayOf("e", torrent, "", "root"),
                    arrayOf("e", parent, "", "reply", parentAuthor),
                    arrayOf("p", parentAuthor),
                    arrayOf("p", other),
                ),
                "",
                sig,
            )
        assertEquals(
            listOf(
                Link(Relation.ROOT, LinkTarget.Event(torrent), "e"),
                Link(Relation.PARENT, LinkTarget.Event(parent), "e"),
                Link(Relation.PARENT_AUTHOR, LinkTarget.User(parentAuthor), "p"),
                Link(Relation.MENTION, LinkTarget.User(other), "p"),
            ),
            event.links(),
        )
    }

    @Suppress("DEPRECATION")
    @Test
    fun unmarkedCommentIsPositional() {
        val event =
            TorrentCommentEvent(
                id,
                author,
                1,
                arrayOf(arrayOf("e", torrent), arrayOf("e", middle), arrayOf("e", parent), arrayOf("p", parentAuthor)),
                "",
                sig,
            )
        assertEquals(
            listOf(
                Link(Relation.ROOT, LinkTarget.Event(torrent), "e"),
                Link(Relation.MENTION, LinkTarget.Event(middle), "e"),
                Link(Relation.PARENT, LinkTarget.Event(parent), "e"),
                Link(Relation.MENTION, LinkTarget.User(parentAuthor), "p"),
            ),
            event.links(),
        )
    }
}
