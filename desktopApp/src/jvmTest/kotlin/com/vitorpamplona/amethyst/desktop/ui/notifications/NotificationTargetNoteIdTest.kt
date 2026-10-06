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
package com.vitorpamplona.amethyst.desktop.ui.notifications

import com.vitorpamplona.amethyst.desktop.ui.NotificationItem
import com.vitorpamplona.quartz.nip10Notes.TextNoteEvent
import com.vitorpamplona.quartz.nip25Reactions.ReactionEvent
import kotlin.test.Test
import kotlin.test.assertEquals

class NotificationTargetNoteIdTest {
    private val root = "aa".repeat(32)
    private val reply = "bb".repeat(32)
    private val someone = "cc".repeat(32)
    private val sig = "00".repeat(64)

    @Test
    fun reactionTargetsTheLastETag() {
        val reaction =
            ReactionEvent("11".repeat(32), someone, 1_000, arrayOf(arrayOf("e", root), arrayOf("e", reply)), "+", sig)

        assertEquals(reply, notificationTargetNoteId(reaction))

        val groups = groupNotifications(listOf(NotificationItem.Reaction(reaction, 1_000, "+")))
        assertEquals(reply, (groups.single() as NotificationGroup.ReactionsOn).targetNoteId)
    }

    @Test
    fun markedReplyTargetsItsParentNotTheRoot() {
        val note =
            TextNoteEvent(
                "22".repeat(32),
                someone,
                1_000,
                arrayOf(arrayOf("e", root, "", "root"), arrayOf("e", reply, "", "reply")),
                "hi",
                sig,
            )

        assertEquals(reply, notificationTargetNoteId(note))
    }
}
