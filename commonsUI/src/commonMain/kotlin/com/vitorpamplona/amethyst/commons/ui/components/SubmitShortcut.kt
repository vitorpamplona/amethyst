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
package com.vitorpamplona.amethyst.commons.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type

/**
 * Ctrl+Enter (Cmd+Enter on macOS) anywhere inside runs [onSubmit], the same action as the
 * composer's Post button, when [isActive] says that button is enabled. Put it on the composer's
 * outermost layout: it sees the key in the preview pass, before the focused text field would
 * turn it into a line break, so a plain Enter still starts a new line.
 *
 * Holding the keys down fires once: the key repeats are swallowed until Enter is released.
 */
@Composable
fun Modifier.submitOnCtrlEnter(
    isActive: () -> Boolean,
    onSubmit: () -> Unit,
): Modifier {
    val currentIsActive = rememberUpdatedState(isActive)
    val currentOnSubmit = rememberUpdatedState(onSubmit)
    val latch = remember { SubmitLatch() }
    return onPreviewKeyEvent { event ->
        latch.handle(event, { it.isSubmitChord() }, { currentIsActive.value() }, { currentOnSubmit.value() })
    }
}

private class SubmitLatch {
    private var armed = true

    fun handle(
        event: KeyEvent,
        isChord: (KeyEvent) -> Boolean,
        isActive: () -> Boolean,
        onSubmit: () -> Unit,
    ): Boolean {
        if (event.key != Key.Enter && event.key != Key.NumPadEnter) return false
        if (event.type == KeyEventType.KeyUp) {
            armed = true
        }
        if (!isChord(event)) return false
        if (event.type == KeyEventType.KeyDown && armed) {
            armed = false
            if (isActive()) onSubmit()
        }
        // Swallows every part of the chord, the typed character included, so it never becomes a
        // line break, even while the button is disabled.
        return true
    }
}

private fun KeyEvent.isSubmitChord(): Boolean = (isCtrlPressed || isMetaPressed) && !isAltPressed && !isShiftPressed

/**
 * A chat composer's Enter: when [enabled] (see [rememberEnterSendsMessage]), a plain Enter runs
 * [onSend] when [isActive] says the Send button is enabled, and is swallowed either way so it never
 * becomes a line break; Shift+Enter still starts a new line. Ctrl/Cmd+Enter sends too, as in the
 * other composers ([submitOnCtrlEnter]). Put it on the composer's text field: it sees the key in the
 * preview pass, before the field does. Held keys fire once.
 *
 * While a mention or emoji suggestion list is open, [onPickSuggestion] gets a plain Enter instead
 * (it returns false when there is nothing to pick, and the Enter then does what it would have),
 * and while [isComposing] says an input method holds uncommitted text, Enter is left to it.
 */
@Composable
fun Modifier.sendOnEnter(
    enabled: Boolean,
    isActive: () -> Boolean,
    onSend: () -> Unit,
    isComposing: () -> Boolean = { false },
    onPickSuggestion: () -> Boolean = { false },
): Modifier {
    val currentIsActive = rememberUpdatedState(isActive)
    val currentOnSend = rememberUpdatedState(onSend)
    val currentIsComposing = rememberUpdatedState(isComposing)
    val currentOnPick = rememberUpdatedState(onPickSuggestion)
    val latch = remember { SubmitLatch() }
    val currentEnabled = rememberUpdatedState(enabled)
    return onPreviewKeyEvent { event ->
        val action =
            if (event.key != Key.Enter && event.key != Key.NumPadEnter) {
                EnterKeyAction.PASS
            } else {
                enterKeyAction(
                    submitChord = event.isSubmitChord(),
                    plainEnter = event.isPlainEnter(),
                    enterSends = currentEnabled.value,
                    composing = currentIsComposing.value(),
                )
            }
        when (action) {
            // Still seen by the latch, so a released Enter re-arms it.
            EnterKeyAction.PASS -> latch.handle(event, { false }, { false }, {})
            EnterKeyAction.SEND -> latch.handle(event, { true }, { currentIsActive.value() }, { currentOnSend.value() })
            EnterKeyAction.PICK_OR_SEND ->
                // Picking fires once per press like sending, so holding Enter never picks and then sends.
                latch.handle(event, { true }, { true }, { if (!currentOnPick.value() && currentIsActive.value()) currentOnSend.value() })
        }
    }
}

/** What a composer does with an Enter key ([sendOnEnter]). */
internal enum class EnterKeyAction {
    /** Not ours: the field (or the input method) gets it. */
    PASS,

    /** Send, and swallow the key. */
    SEND,

    /** Pick the open suggestion if there is one, else send; swallow the key. */
    PICK_OR_SEND,
}

/**
 * [sendOnEnter]'s decision for one Enter: an input method composing text owns it, Ctrl/Cmd+Enter
 * always sends, and a plain Enter picks a suggestion before it sends, when Enter sends at all.
 */
internal fun enterKeyAction(
    submitChord: Boolean,
    plainEnter: Boolean,
    enterSends: Boolean,
    composing: Boolean,
): EnterKeyAction =
    when {
        composing -> EnterKeyAction.PASS
        submitChord -> EnterKeyAction.SEND
        plainEnter && enterSends -> EnterKeyAction.PICK_OR_SEND
        else -> EnterKeyAction.PASS
    }

private fun KeyEvent.isPlainEnter(): Boolean = !isShiftPressed && !isCtrlPressed && !isMetaPressed && !isAltPressed
