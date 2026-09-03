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
package com.vitorpamplona.amethyst.ui.components

import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.CompletionInfo
import android.view.inputmethod.CorrectionInfo
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.view.inputmethod.InputConnectionWrapper
import android.view.inputmethod.InputMethodManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.InterceptPlatformTextInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.PlatformTextInputInterceptor
import androidx.compose.ui.platform.PlatformTextInputMethodRequest
import androidx.compose.ui.platform.PlatformTextInputSession
import androidx.core.content.getSystemService
import com.vitorpamplona.quartz.utils.Log

private const val TAG = "StaleImeCommandGuard"

/**
 * Makes every IME connection created below [content] survive an edit command that
 * no longer fits the text it was computed against.
 *
 * ### Why this exists
 *
 * Compose's `BasicTextField` applies IME commands lazily: `beginBatchEdit` queues
 * them and `endBatchEdit` replays them against the buffer. The offset mapping used
 * to translate them back from the [androidx.compose.foundation.text.input.OutputTransformation]'s
 * *transformed* space into the raw buffer is deliberately read from the state as it
 * was **before** the batch — `ImeEditCommandScope` documents that "ongoing edits are
 * not visible to this transform function yet". `setSelection` then clamps the IME's
 * offsets in transformed space and maps them back with:
 *
 * ```
 * val transformedSpaceLength = mapToTransformed(TextRange(0, length))   // stale mapping, live length
 * ...
 * selection = mapFromTransformed(TextRange(clampedStart, clampedEnd))   // stale mapping
 * ```
 *
 * The clamp is applied *before* the back-mapping and never re-applied after, so an
 * offset that is in range in transformed space can map to an untransformed offset
 * past the end of the buffer. `TextFieldBuffer.selection`'s setter then fails its
 * `requireValidRange` precondition and throws `IllegalArgumentException` out of the
 * IME's `Handler` callback, killing the process.
 *
 * [UrlUserTagOutputTransformation][com.vitorpamplona.amethyst.ui.actions.UrlUserTagOutputTransformation]
 * makes Amethyst hit this: it collapses a 64-character `@npub1…` down to a short
 * `@DisplayName`, which is exactly the kind of wide "wedge" whose two edges are far
 * apart in the two coordinate spaces. When a batch shortens the buffer into the
 * middle of a mention — an IME deleting the tail of the bech32, which is also what
 * [MentionPreservingInputTransformation][com.vitorpamplona.amethyst.ui.actions.MentionPreservingInputTransformation]
 * cleans up, but only *after* the batch commits — the queued `setSelection` maps
 * back to the mention's old end offset and blows up. The production report reads
 * `Expected TextRange(130, 130) to be in TextRange(0, 80)`: 130 is where the mention
 * used to end, 80 is what is left of the buffer.
 *
 * ### What the guard does
 *
 * It wraps the [InputConnection] Compose hands to the platform and turns those
 * failures into a dropped command plus an `InputMethodManager.restartInput`. The
 * restart is what makes this safe rather than a papered-over corruption: Compose
 * builds a fresh `DefaultImeEditCommandScope` per `createInputConnection`, so the
 * half-applied batch — including the command that threw, which Compose leaves in
 * its queue and would otherwise replay forever — is discarded, and the keyboard
 * re-reads the field's real contents.
 *
 * The user-visible cost is one lost cursor move; the alternative is a crash.
 */
@Composable
fun GuardAgainstStaleImeCommands(content: @Composable () -> Unit) {
    val view = LocalView.current
    // Must be stable: a new interceptor instance tears down and restarts any
    // active text input session.
    val interceptor = remember(view) { StaleImeCommandInterceptor(view) }

    InterceptPlatformTextInput(interceptor, content)
}

private class StaleImeCommandInterceptor(
    private val view: View,
) : PlatformTextInputInterceptor {
    override suspend fun interceptStartInputMethod(
        request: PlatformTextInputMethodRequest,
        nextHandler: PlatformTextInputSession,
    ): Nothing =
        nextHandler.startInputMethod(
            object : PlatformTextInputMethodRequest {
                override fun createInputConnection(outAttributes: EditorInfo): InputConnection = GuardedInputConnection(request.createInputConnection(outAttributes), ::restartInput)
            },
        )

    private fun restartInput(
        command: String,
        e: RuntimeException,
    ) {
        Log.w(TAG, "Dropped IME $command that no longer fits the field. Restarting input.", e)

        // Not inline: we are inside the IME's own call into the connection and
        // restarting from here would re-enter it.
        view.post {
            view.context
                .getSystemService<InputMethodManager>()
                ?.restartInput(view)
        }
    }
}

/**
 * Delegates everything to Compose's own connection, but catches the range
 * preconditions that a stale IME command can trip on the editing calls. Read-only
 * calls (`getTextBeforeCursor` and friends) are left alone — they can't desync the
 * buffer, and swallowing their result would hide real bugs.
 */
internal class GuardedInputConnection(
    delegate: InputConnection,
    private val onDesync: (command: String, e: RuntimeException) -> Unit,
) : InputConnectionWrapper(delegate, false) {
    private fun <T> guard(
        command: String,
        fallback: T,
        block: () -> T,
    ): T =
        try {
            block()
        } catch (e: IllegalArgumentException) {
            onDesync(command, e)
            fallback
        } catch (e: IndexOutOfBoundsException) {
            onDesync(command, e)
            fallback
        }

    override fun beginBatchEdit(): Boolean = guard("beginBatchEdit", true) { super.beginBatchEdit() }

    override fun endBatchEdit(): Boolean = guard("endBatchEdit", false) { super.endBatchEdit() }

    override fun setSelection(
        start: Int,
        end: Int,
    ): Boolean = guard("setSelection", true) { super.setSelection(start, end) }

    override fun setComposingRegion(
        start: Int,
        end: Int,
    ): Boolean = guard("setComposingRegion", true) { super.setComposingRegion(start, end) }

    override fun setComposingText(
        text: CharSequence?,
        newCursorPosition: Int,
    ): Boolean = guard("setComposingText", true) { super.setComposingText(text, newCursorPosition) }

    override fun finishComposingText(): Boolean = guard("finishComposingText", true) { super.finishComposingText() }

    override fun commitText(
        text: CharSequence?,
        newCursorPosition: Int,
    ): Boolean = guard("commitText", true) { super.commitText(text, newCursorPosition) }

    override fun commitCompletion(text: CompletionInfo?): Boolean = guard("commitCompletion", true) { super.commitCompletion(text) }

    override fun commitCorrection(correctionInfo: CorrectionInfo?): Boolean = guard("commitCorrection", true) { super.commitCorrection(correctionInfo) }

    override fun deleteSurroundingText(
        beforeLength: Int,
        afterLength: Int,
    ): Boolean = guard("deleteSurroundingText", true) { super.deleteSurroundingText(beforeLength, afterLength) }

    override fun deleteSurroundingTextInCodePoints(
        beforeLength: Int,
        afterLength: Int,
    ): Boolean =
        guard("deleteSurroundingTextInCodePoints", true) {
            super.deleteSurroundingTextInCodePoints(beforeLength, afterLength)
        }

    override fun sendKeyEvent(event: KeyEvent?): Boolean = guard("sendKeyEvent", true) { super.sendKeyEvent(event) }

    override fun performContextMenuAction(id: Int): Boolean = guard("performContextMenuAction", true) { super.performContextMenuAction(id) }
}
