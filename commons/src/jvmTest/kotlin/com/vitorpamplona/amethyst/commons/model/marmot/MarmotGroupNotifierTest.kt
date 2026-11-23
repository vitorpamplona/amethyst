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
package com.vitorpamplona.amethyst.commons.model.marmot

import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip09Deletions.DeletionRequestEvent
import com.vitorpamplona.quartz.nip22Comments.CommentEvent
import com.vitorpamplona.quartz.nip25Reactions.ReactionEvent
import com.vitorpamplona.quartz.nipC7Chats.ChatEvent
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Which inner events of a Marmot group raise a notification. MDK's reply contract
 * (docs/marmot-architecture/overview/thread-messages.md, mdk#3581) makes a NIP-22 thread
 * message chat: it notifies and mentions like kind 9, so a White Noise user answering in a
 * thread must not land silently here.
 */
class MarmotGroupNotifierTest {
    private val author = "a".repeat(64)
    private val root = "b".repeat(64)

    private fun <T : Event> make(
        build: (String, String, Long, Array<Array<String>>, String, String) -> T,
        tags: Array<Array<String>> = emptyArray(),
    ) = build("c".repeat(64), author, 1_700_000_000L, tags, "hi", "")

    @Test
    fun aChatMessageNotifies() {
        assertTrue(MarmotGroupNotifier.notifies(make(::ChatEvent)))
    }

    @Test
    fun aThreadMessageNotifies() {
        val tags =
            arrayOf(
                arrayOf("E", root, "", author),
                arrayOf("K", "9"),
                arrayOf("P", author),
                arrayOf("e", root, "", author),
                arrayOf("k", "9"),
            )
        assertTrue(MarmotGroupNotifier.notifies(make(::CommentEvent, tags)))
    }

    @Test
    fun anOlderThreadMessageWithOnlyAParentNotifies() {
        // White Noise Linux before 2026-11-17: `e` alone, which MDK still reads as a thread message.
        val tags = arrayOf(arrayOf("e", root, "", author))
        assertTrue(MarmotGroupNotifier.notifies(make(::CommentEvent, tags)))
    }

    @Test
    fun aCommentWithoutAParentStaysSilent() {
        // A numeric `k` but no lowercase `e`: a comment without a parent, a custom row, not chat.
        val tags =
            arrayOf(
                arrayOf("E", root, "", author),
                arrayOf("K", "9"),
                arrayOf("P", author),
                arrayOf("k", "9"),
            )
        assertFalse(MarmotGroupNotifier.notifies(make(::CommentEvent, tags)))
    }

    @Test
    fun aCustomKind1111StaysSilent() {
        // Neither a lowercase `e` nor a `k`: a plain custom event.
        assertFalse(MarmotGroupNotifier.notifies(make(::CommentEvent)))
        assertFalse(MarmotGroupNotifier.notifies(make(::CommentEvent, arrayOf(arrayOf("p", author)))))
    }

    @Test
    fun reactionsAndDeletionsStaySilent() {
        assertFalse(MarmotGroupNotifier.notifies(make(::ReactionEvent)))
        assertFalse(MarmotGroupNotifier.notifies(make(::DeletionRequestEvent)))
    }
}
