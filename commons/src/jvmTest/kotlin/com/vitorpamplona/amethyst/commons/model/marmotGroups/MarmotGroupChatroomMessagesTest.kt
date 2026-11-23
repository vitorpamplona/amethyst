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

import com.vitorpamplona.amethyst.commons.model.Note
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.measureTime

/**
 * The message set of a Marmot group: an immutable snapshot readers iterate without the lock, which
 * must not cost a copy of the whole history per added message (a restore re-adds it one by one).
 */
class MarmotGroupChatroomMessagesTest {
    private fun note(i: Int) = Note(i.toString(16).padStart(64, '0'))

    @Test
    fun aSnapshotDoesNotChangeUnderLaterWrites() {
        val room = MarmotGroupChatroom("d".repeat(64))
        val first = note(1)
        room.addMessageSync(first)
        val snapshot = room.messages

        room.addMessageSync(note(2))
        room.removeMessageSync(first)

        assertEquals(setOf(first), snapshot, "a reader's snapshot is immutable")
        assertEquals(setOf(note(2)).map { it.idHex }, room.messages.map { it.idHex })
    }

    @Test
    fun addingTwiceKeepsOneAndReportsTheDuplicate() {
        val room = MarmotGroupChatroom("d".repeat(64))
        val n = note(1)
        assertTrue(room.addMessageSync(n))
        assertFalse(room.addMessageSync(n))
        assertEquals(1, room.messages.size)
    }

    @Test
    fun restoringALongHistoryOneByOneIsNotQuadratic() {
        // 50k one-by-one adds copied ~1.25 billion references with a copy-on-write set (tens of
        // seconds); with structural sharing it takes a fraction of one.
        val room = MarmotGroupChatroom("d".repeat(64))
        val notes = (0 until 50_000).map(::note)
        val took = measureTime { notes.forEach(room::addMessageSync) }

        assertEquals(50_000, room.messages.size)
        assertTrue(took.inWholeSeconds < 10, "50k adds took $took")
    }
}
