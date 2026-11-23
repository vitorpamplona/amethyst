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
package com.vitorpamplona.amethyst.commons.model.marmotGroups

import com.vitorpamplona.amethyst.commons.model.AddressableNote
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.model.User
import com.vitorpamplona.amethyst.commons.model.UserContext
import com.vitorpamplona.quartz.nip01Core.core.Event
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Three messages sent within one second (White Noise: "Hello", "Second", "Third") share a
 * `created_at`. Neither the Marmot spec nor MDK orders them by anything a sender controls (MDK
 * breaks the tie by message id, which is random), so the group keeps the order they were decrypted
 * in, which is the order they were sent, and the stored history is replayed in. The id tiebreak
 * of every other feed showed them shuffled, and the chat list previewed the first of them.
 */
class MarmotGroupMessageOrderTest {
    private val peer = "b".repeat(64)
    private val context = UserContext { addr -> AddressableNote(addr) }

    private fun chat(
        id: String,
        createdAt: Long,
        content: String,
    ): Note {
        val event = Event(id, peer, createdAt, 9, emptyArray(), content, "")
        return Note(event.id).also { it.loadEvent(event, User(peer, context), emptyList()) }
    }

    // Ids chosen so that the id order is neither the send order nor its reverse.
    private val hello = chat("2".repeat(64), 1_800_000_000L, "Hello")
    private val second = chat("3".repeat(64), 1_800_000_000L, "Second")
    private val third = chat("1".repeat(64), 1_800_000_000L, "Third")

    @Test
    fun sameSecondMessagesKeepTheirArrivalOrder() {
        val room = MarmotGroupChatroom("c".repeat(64))
        listOf(hello, second, third).forEach(room::addMessageSync)

        // Feeds are newest first; the chat draws them bottom-up.
        assertEquals(listOf("Third", "Second", "Hello"), room.messages.sortedWith(room.feedOrder).map { it.event?.content })
    }

    @Test
    fun thePreviewIsTheLastOfTheSameSecondMessages() {
        val room = MarmotGroupChatroom("c".repeat(64))
        listOf(hello, second, third).forEach(room::addMessageSync)
        assertEquals("Third", room.newestMessage?.event?.content)

        room.removeMessageSync(third)
        assertEquals("Second", room.newestMessage?.event?.content)
    }

    @Test
    fun anOlderSecondStillSortsBelow() {
        val room = MarmotGroupChatroom("c".repeat(64))
        val newer = chat("4".repeat(64), 1_800_000_001L, "Fourth")
        // A catch-up can deliver the newer second first; created_at still decides between seconds.
        listOf(newer, hello, second).forEach(room::addMessageSync)
        assertEquals(listOf("Fourth", "Second", "Hello"), room.messages.sortedWith(room.feedOrder).map { it.event?.content })
        assertEquals("Fourth", room.newestMessage?.event?.content)
    }

    @Test
    fun aDeletedPlaceholderKeepsItsMessagesPlace() {
        val room = MarmotGroupChatroom("c".repeat(64))
        listOf(hello, second, third).forEach(room::addMessageSync)
        room.removeMessageSync(second)
        room.addMessageSync(MarmotDeletedMessageNote(second.idHex, second.author, 1_800_000_000L))

        assertEquals(
            listOf(third.idHex, MarmotDeletedMessageNote.idFor(second.idHex), hello.idHex),
            room.messages.sortedWith(room.feedOrder).map { it.idHex },
        )
    }
}
