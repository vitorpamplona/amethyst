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
package com.vitorpamplona.amethyst.commons.ui.feeds

import androidx.compose.runtime.saveable.SaverScope
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * A feed's position survives a trip through the saved-instance bundle within one process (back
 * stack, activity recreation) but not a process death: the restarted process rebuilds the feed from
 * scratch, and the old index pointed months back into it.
 */
class SameProcessSaverTest {
    private data class Position(
        val index: Int,
        val offset: Int,
    )

    private val saver = sameProcessSaver<Position>({ it.index }, { it.offset }) { i, o -> Position(i, o) }

    private val scope = SaverScope { true }

    @Test
    fun restoresInTheProcessThatSaved() {
        val saved = with(saver) { scope.save(Position(42, 7)) }!!
        assertEquals(Position(42, 7), saver.restore(saved))
    }

    @Test
    fun startsAtTheTopAfterAProcessDeath() {
        @Suppress("UNCHECKED_CAST")
        val saved = with(saver) { scope.save(Position(42, 7)) } as List<Any>
        // What a bundle written by an earlier process holds: another process's token.
        val fromAnotherProcess = listOf((saved[0] as Long) + 1, saved[1], saved[2])
        assertEquals(Position(0, 0), saver.restore(fromAnotherProcess))
    }
}
