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
package com.vitorpamplona.quartz.buzz.forum

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ForumCommentEventTest {
    private val channel = "3f2504e0-4f89-41d3-9a0c-0305e82c3301"
    private val root = "a".repeat(64)
    private val parent = "b".repeat(64)
    private val alice = "c".repeat(64)

    @Test
    fun directReplyEmitsSingleReplyMarker() {
        // root == parent -> ["e", root, "", "reply"] only (matches thread_tags).
        val t = ForumCommentEvent.build(channel, "nice post", rootEventId = root, parentEventId = root)
        val ev = ForumCommentEvent("00", "f".repeat(64), t.createdAt, t.tags, t.content, "sig")

        assertEquals(45003, ev.kind)
        assertEquals("nice post", ev.body())
        assertEquals(channel, ev.channel())
        assertEquals(root, ev.replyTo())
        assertNull(ev.threadRoot(), "a direct reply carries no root marker")

        val eTags = ev.tags.filter { it[0] == "e" }
        assertEquals(1, eTags.size)
        assertEquals(arrayOf("e", root, "", "reply").toList(), eTags.first().toList())
    }

    @Test
    fun nestedReplyEmitsRootAndReplyMarkers() {
        val t =
            ForumCommentEvent.build(
                channel,
                "replying deep",
                rootEventId = root,
                parentEventId = parent,
                mentions = listOf(alice),
            )
        val ev = ForumCommentEvent("00", "f".repeat(64), t.createdAt, t.tags, t.content, "sig")

        assertEquals(root, ev.threadRoot())
        assertEquals(parent, ev.replyTo())
        assertEquals(listOf(alice), ev.mentions())
        assertTrueTag(ev, arrayOf("e", root, "", "root"))
        assertTrueTag(ev, arrayOf("e", parent, "", "reply"))
    }

    private fun assertTrueTag(
        ev: ForumCommentEvent,
        expected: Array<String>,
    ) {
        assertTrue(ev.tags.any { it.toList() == expected.toList() }, "missing tag ${expected.toList()}")
    }
}
