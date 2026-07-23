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

import com.vitorpamplona.quartz.buzz.forum.tags.VoteDirection
import kotlin.test.Test
import kotlin.test.assertEquals

class ForumVoteEventTest {
    private val channel = "3f2504e0-4f89-41d3-9a0c-0305e82c3301"
    private val target = "d".repeat(64)

    @Test
    fun buildUpvote() {
        val t = ForumVoteEvent.build(channel, target, VoteDirection.UP)
        val ev = ForumVoteEvent("00", "f".repeat(64), t.createdAt, t.tags, t.content, "sig")

        assertEquals(45002, ev.kind)
        assertEquals("+", ev.content)
        assertEquals(VoteDirection.UP, ev.direction())
        assertEquals(channel, ev.channel())
        assertEquals(target, ev.target())
    }

    @Test
    fun buildDownvote() {
        val t = ForumVoteEvent.build(channel, target, VoteDirection.DOWN)
        val ev = ForumVoteEvent("00", "f".repeat(64), t.createdAt, t.tags, t.content, "sig")

        assertEquals("-", ev.content)
        assertEquals(VoteDirection.DOWN, ev.direction())
    }

    @Test
    fun directionParsesFromContent() {
        assertEquals(VoteDirection.UP, VoteDirection.fromContent("+"))
        assertEquals(VoteDirection.DOWN, VoteDirection.fromContent("-"))
        assertEquals(null, VoteDirection.fromContent("x"))
    }
}
