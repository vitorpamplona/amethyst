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
import kotlin.test.assertTrue

class ForumPostEventTest {
    private val channel = "3f2504e0-4f89-41d3-9a0c-0305e82c3301"
    private val alice = "a".repeat(64)
    private val bob = "b".repeat(64)

    @Test
    fun buildWithChannelAndMentions() {
        val t = ForumPostEvent.build(channel, "welcome to the forum", listOf(alice, bob))
        val ev = ForumPostEvent("00", "f".repeat(64), t.createdAt, t.tags, t.content, "sig")

        assertEquals(ForumPostEvent.KIND, ev.kind)
        assertEquals(45001, ev.kind)
        assertEquals("welcome to the forum", ev.body())
        assertEquals(channel, ev.channel())
        assertEquals(listOf(alice, bob), ev.mentions())
        // h tag comes first, matching build_forum_post.
        assertEquals("h", ev.tags.first()[0])
    }

    @Test
    fun buildWithoutMentions() {
        val t = ForumPostEvent.build(channel, "no mentions here")
        val ev = ForumPostEvent("00", "f".repeat(64), t.createdAt, t.tags, t.content, "sig")

        assertEquals(channel, ev.channel())
        assertTrue(ev.mentions().isEmpty())
    }

    @Test
    fun duplicateMentionsAreDeduplicated() {
        val t = ForumPostEvent.build(channel, "body", listOf(alice, alice))
        val ev = ForumPostEvent("00", "f".repeat(64), t.createdAt, t.tags, t.content, "sig")

        assertEquals(listOf(alice), ev.mentions())
    }
}
