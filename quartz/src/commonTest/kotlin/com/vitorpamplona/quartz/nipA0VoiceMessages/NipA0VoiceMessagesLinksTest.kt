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
package com.vitorpamplona.quartz.nipA0VoiceMessages

import com.vitorpamplona.quartz.graph.Link
import com.vitorpamplona.quartz.graph.LinkTarget
import com.vitorpamplona.quartz.graph.Relation
import kotlin.test.Test
import kotlin.test.assertEquals

class NipA0VoiceMessagesLinksTest {
    private val id = "0".repeat(64)
    private val author = "1".repeat(64)
    private val sig = "0".repeat(128)
    private val root = "2".repeat(64)
    private val parent = "3".repeat(64)
    private val rootAuthor = "a".repeat(64)
    private val parentAuthor = "b".repeat(64)

    @Test
    fun replyFollowsNip22() {
        val event =
            VoiceReplyEvent(
                id,
                author,
                1,
                arrayOf(
                    arrayOf("E", root, "", rootAuthor),
                    arrayOf("K", "1222"),
                    arrayOf("P", rootAuthor),
                    arrayOf("e", parent, "", parentAuthor),
                    arrayOf("k", "1244"),
                    arrayOf("p", parentAuthor),
                    arrayOf("imeta", "url https://audio.example/a.m4a"),
                ),
                "https://audio.example/a.m4a",
                sig,
            )
        assertEquals(
            listOf(
                Link(Relation.ROOT, LinkTarget.Event(root), "E"),
                Link(Relation.TAG, LinkTarget.Tag("K", "1222"), "K"),
                Link(Relation.ROOT_AUTHOR, LinkTarget.User(rootAuthor), "P"),
                Link(Relation.PARENT, LinkTarget.Event(parent), "e"),
                Link(Relation.TAG, LinkTarget.Tag("k", "1244"), "k"),
                Link(Relation.PARENT_AUTHOR, LinkTarget.User(parentAuthor), "p"),
            ),
            event.links(),
        )
    }

    @Test
    fun olderReplyToAVoiceMessageIsRootedAtItsParent() {
        val event =
            VoiceReplyEvent(
                id,
                author,
                1,
                arrayOf(arrayOf("e", parent), arrayOf("k", "1222"), arrayOf("p", parentAuthor)),
                "https://audio.example/a.m4a",
                sig,
            )
        assertEquals(
            listOf(
                Link(Relation.ROOT, LinkTarget.Event(parent), "e"),
                Link(Relation.ROOT_AUTHOR, LinkTarget.User(parentAuthor), "p"),
                Link(Relation.PARENT, LinkTarget.Event(parent), "e"),
                Link(Relation.TAG, LinkTarget.Tag("k", "1222"), "k"),
                Link(Relation.PARENT_AUTHOR, LinkTarget.User(parentAuthor), "p"),
            ),
            event.links(),
        )
    }
}
