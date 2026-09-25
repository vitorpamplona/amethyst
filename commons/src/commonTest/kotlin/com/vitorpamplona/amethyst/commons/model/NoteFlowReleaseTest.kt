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
package com.vitorpamplona.amethyst.commons.model

import kotlin.test.Test
import kotlin.test.assertNotSame
import kotlin.test.assertNull
import kotlin.test.assertSame

/**
 * The memory trimmer calls [Note.clearFlow] on every app switch, when lifecycle-aware collectors
 * are paused and so nothing looks subscribed. The composables still remember the flows they
 * grabbed from [Note.flow], and resubscribe to exactly those when the app comes back. A like
 * made after that must still reach them, or the heart stays gray while the counter and the
 * reaction gallery (which subscribe afresh) show it.
 */
class NoteFlowReleaseTest {
    @Test
    fun aReleasedFlowStillHeldByTheUiKeepsReceivingUpdates() {
        val note = Note("aa".repeat(32))
        val remembered = note.flow().reactions.stateFlow

        // App goes to the background: no collectors, so the trimmer releases the set.
        note.clearFlow()

        val before = remembered.value
        note.addReaction(Note("bb".repeat(32)))

        assertNotSame(before, remembered.value, "the remembered flow must see the new reaction")
        assertSame(remembered, note.flow().reactions.stateFlow, "flow() must hand back the set the UI holds")
    }

    @Test
    fun aNeverObservedNoteHasNoFlowSet() {
        assertNull(Note("cc".repeat(32)).flowSet)
    }
}
