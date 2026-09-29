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
package com.vitorpamplona.quartz.nip37Drafts

import com.vitorpamplona.quartz.nip01Core.links.Link
import com.vitorpamplona.quartz.nip01Core.links.LinkTarget
import com.vitorpamplona.quartz.nip01Core.links.Relation
import kotlin.test.Test
import kotlin.test.assertEquals

class Nip37DraftsLinksTest {
    private val id = "0".repeat(64)
    private val sig = "0".repeat(128)
    private val author = "1".repeat(64)
    private val channel = "2".repeat(64)
    private val parent = "3".repeat(64)
    private val mentioned = "4".repeat(64)

    @Test
    fun channelMessageDraftLinksItsExposedAnchors() {
        val event =
            DraftWrapEvent(
                id,
                author,
                1,
                arrayOf(
                    arrayOf("d", "draft-1"),
                    arrayOf("k", "42"),
                    arrayOf("expiration", "1700000000"),
                    arrayOf("e", channel, "wss://relay.example/", "root"),
                    arrayOf("e", parent, "wss://relay.example/", "reply", author),
                    arrayOf("e", mentioned),
                ),
                "encrypted",
                sig,
            )
        assertEquals(
            listOf(
                Link(Relation.TAG, LinkTarget.Tag("k", "42"), "k"),
                Link(Relation.ROOT, LinkTarget.Event(channel), "e"),
                Link(Relation.PARENT, LinkTarget.Event(parent), "e"),
            ),
            event.links(),
        )
    }

    @Test
    fun liveChatDraftLinksItsActivityAsRoot() {
        val activity = "30311:$author:stream"
        val event =
            DraftWrapEvent(
                id,
                author,
                1,
                arrayOf(arrayOf("d", "draft-2"), arrayOf("k", "1311"), arrayOf("a", activity)),
                "encrypted",
                sig,
            )
        assertEquals(
            listOf(
                Link(Relation.TAG, LinkTarget.Tag("k", "1311"), "k"),
                Link(Relation.ROOT, LinkTarget.Address(activity), "a"),
            ),
            event.links(),
        )
    }
}
