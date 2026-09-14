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
package com.vitorpamplona.amethyst.model

import com.vitorpamplona.amethyst.commons.model.ThreadAssembler
import com.vitorpamplona.quartz.experimental.notifications.wake.WakeUpEvent
import com.vitorpamplona.quartz.nip10Notes.TextNoteEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A kind:23903 wake-up is a transport-level nudge, not a conversation entry: it e-tags the
 * events the device should come online for. Threading it as a reply put an empty, unrenderable
 * card into the thread view of every note a wake-up pointed at (and inflated its reply count).
 *
 * `LocalCache` is a process-wide object and JUnit 4 runs methods in hash order, so every test
 * here uses its own pubkeys/ids.
 */
class WakeUpEventThreadingTest {
    private val authorKey = "a1".repeat(32)
    private val wakerKey = "a2".repeat(32)

    private fun note(id: String) =
        TextNoteEvent(
            id = id,
            pubKey = authorKey,
            createdAt = 1_760_000_000L,
            tags = emptyArray(),
            content = "the note a wake-up points at",
            sig = "bb".repeat(64),
        )

    private fun wakeUp(
        id: String,
        about: String,
    ) = WakeUpEvent(
        id = id,
        pubKey = wakerKey,
        createdAt = 1_760_000_100L,
        tags = arrayOf(arrayOf("e", about), arrayOf("p", authorKey), arrayOf("k", "1")),
        content = "",
        sig = "cc".repeat(64),
    )

    @Test
    fun aWakeUpIsNotAReplyToTheEventItPointsAt() {
        val target = "b1".repeat(32)
        val waker = "b2".repeat(32)

        LocalCache.justConsume(note(target), null, true)
        LocalCache.justConsume(wakeUp(waker, about = target), null, true)

        val targetNote = LocalCache.getOrCreateNote(target)
        val wakeNote = LocalCache.getOrCreateNote(waker)

        assertTrue("the wake-up must not land in the target's replies", targetNote.replies.none { it == wakeNote })
        assertEquals("the target's reply counter must stay at zero", 0, targetNote.replies.size)
        assertTrue("the wake-up must not carry a thread parent", wakeNote.replyTo.isNullOrEmpty())
    }

    @Test
    fun aWakeUpDoesNotShowUpInTheThreadOfTheEventItPointsAt() {
        val target = "b3".repeat(32)
        val waker = "b4".repeat(32)

        LocalCache.justConsume(note(target), null, true)
        LocalCache.justConsume(wakeUp(waker, about = target), null, true)

        val thread = ThreadAssembler(LocalCache).findThreadFor(target)

        assertTrue("the target itself is in its own thread", thread!!.allNotes.any { it.idHex == target })
        assertFalse("the wake-up must not be in the thread", thread.allNotes.any { it.idHex == waker })
    }
}
