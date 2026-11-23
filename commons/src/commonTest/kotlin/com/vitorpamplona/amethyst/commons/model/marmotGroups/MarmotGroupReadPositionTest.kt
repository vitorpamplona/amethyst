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
import kotlin.test.assertNull

/**
 * The read marker a device group shares names (epoch, created_at, id) of the furthest message
 * read; the room picks it among the messages at or before the last-read time whose epoch it saw.
 */
class MarmotGroupReadPositionTest {
    private val peer = "b".repeat(64)
    private val context = UserContext { addr -> AddressableNote(addr) }

    private fun chat(
        id: String,
        createdAt: Long,
    ): Note {
        val event = Event(id, peer, createdAt, 9, emptyArray(), "hi", "")
        return Note(event.id).also { it.loadEvent(event, User(peer, context), emptyList()) }
    }

    private val first = chat("1".repeat(64), 100)
    private val second = chat("2".repeat(64), 200)
    private val third = chat("3".repeat(64), 300)

    @Test
    fun theFurthestMessageAtOrBeforeTheReadTimeWins() {
        val room = MarmotGroupChatroom("c".repeat(64))
        listOf(first, second, third).forEach(room::addMessageSync)
        room.recordEpoch(first.idHex, 4)
        room.recordEpoch(second.idHex, 5)
        room.recordEpoch(third.idHex, 5)

        assertEquals(MarmotGroupChatroom.ReadPosition(5, 200, second.idHex), room.readPositionAt(250))
        assertEquals(MarmotGroupChatroom.ReadPosition(5, 300, third.idHex), room.readPositionAt(300))
        assertNull(room.readPositionAt(50))
    }

    @Test
    fun aMessageWithoutAnEpochIsSkipped() {
        val room = MarmotGroupChatroom("c".repeat(64))
        // Restored from the store: no epoch known for the newer one.
        listOf(first, second).forEach(room::addMessageSync)
        room.recordEpoch(first.idHex, 4)
        assertEquals(MarmotGroupChatroom.ReadPosition(4, 100, first.idHex), room.readPositionAt(1_000))
    }

    @Test
    fun theEpochOrdersBeforeTheTime() {
        val room = MarmotGroupChatroom("c".repeat(64))
        listOf(first, second).forEach(room::addMessageSync)
        // A clock-skewed sender: an older created_at in a later epoch is still further.
        room.recordEpoch(first.idHex, 9)
        room.recordEpoch(second.idHex, 8)
        assertEquals(MarmotGroupChatroom.ReadPosition(9, 100, first.idHex), room.readPositionAt(1_000))
    }

    @Test
    fun aRemovedMessageForgetsItsEpoch() {
        val room = MarmotGroupChatroom("c".repeat(64))
        listOf(first, second).forEach(room::addMessageSync)
        room.recordEpoch(first.idHex, 4)
        room.recordEpoch(second.idHex, 4)
        room.removeMessageSync(second)
        assertEquals(MarmotGroupChatroom.ReadPosition(4, 100, first.idHex), room.readPositionAt(1_000))
    }
}
