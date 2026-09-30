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
package com.vitorpamplona.quartz.buzz.stream

import com.vitorpamplona.quartz.nip01Core.signers.EventTemplate
import com.vitorpamplona.quartz.nipC7Chats.ChatEvent
import kotlin.test.Test
import kotlin.test.assertEquals

/** The kind-9 Buzz message must carry exactly the tags `build_message` emits, in its order. */
class BuzzChatMessageTest {
    private val channel = "6a39da2f-33c0-44f6-a050-c4da0138644a"
    private val root = "a".repeat(64)
    private val parent = "b".repeat(64)
    private val alice = "c".repeat(64)

    private fun tags(t: EventTemplate<ChatEvent>) = t.tags.map { it.toList() }

    @Test
    fun plainMessageIsKind9WithOnlyTheChannelTag() {
        val t = BuzzChatMessage.build(channel, "hello")
        assertEquals(ChatEvent.KIND, t.kind)
        assertEquals("hello", t.content)
        assertEquals(listOf(listOf("h", channel)), tags(t))
    }

    @Test
    fun directReplyCollapsesToOneReplyMarker() {
        val t = BuzzChatMessage.build(channel, "hi", threadRoot = parent, replyTo = parent, mentions = listOf(alice))
        assertEquals(
            listOf(
                listOf("h", channel),
                listOf("e", parent, "", "reply"),
                listOf("p", alice),
            ),
            tags(t),
        )
    }

    @Test
    fun nestedBroadcastReplyCarriesRootReplyMentionsThenBroadcast() {
        val t = BuzzChatMessage.build(channel, "hi", threadRoot = root, replyTo = parent, mentions = listOf(alice, alice), broadcast = true)
        assertEquals(
            listOf(
                listOf("h", channel),
                listOf("e", root, "", "root"),
                listOf("e", parent, "", "reply"),
                listOf("p", alice),
                listOf("broadcast", "1"),
            ),
            tags(t),
        )
    }
}
