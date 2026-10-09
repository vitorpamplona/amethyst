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
package com.vitorpamplona.amethyst.commons.ui.navigation.deck

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow

/** What a keyboard shortcut or menu item asks the deck to do. */
sealed interface DeckCommand {
    /** Opens the add-column picker. */
    data object AddColumn : DeckCommand

    /** Closes the focused column. */
    data object CloseColumn : DeckCommand

    /** Moves the focused column [delta] places. */
    data class MoveColumn(
        val delta: Int,
    ) : DeckCommand

    /** Focuses the column at [index] (0-based) and scrolls it into view. */
    data class FocusColumn(
        val index: Int,
    ) : DeckCommand

    /** Asks for a name and saves the columns as a new workspace. */
    data object SaveWorkspace : DeckCommand
}

/**
 * How the desktop's menu bar and shortcuts reach the deck, which lives deep in the shared shell.
 * One window shows one deck, so one bus is enough; nothing is delivered while no deck is shown.
 */
object DeckCommandBus {
    private val flow = MutableSharedFlow<DeckCommand>(extraBufferCapacity = 8)

    val commands: SharedFlow<DeckCommand> = flow

    fun send(command: DeckCommand) {
        flow.tryEmit(command)
    }
}
