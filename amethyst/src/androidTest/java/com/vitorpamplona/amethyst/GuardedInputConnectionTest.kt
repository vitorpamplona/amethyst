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
package com.vitorpamplona.amethyst

import android.view.View
import android.view.inputmethod.BaseInputConnection
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.vitorpamplona.amethyst.ui.components.GuardedInputConnection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The production crash arrives as an `IllegalArgumentException` thrown out of
 * Compose's `StatelessInputConnection` while the IME replays a batch whose offsets
 * no longer fit the buffer:
 *
 * ```
 * java.lang.IllegalArgumentException: Expected TextRange(130, 130) to be in TextRange(0, 80)
 *   …TextFieldBuffer.setSelection-5zc-tL8
 *   …ImeEditCommand_androidKt.setSelection$lambda$0
 *   …DefaultImeEditCommandScope.endBatchEdit
 *   …StatelessInputConnection.endBatchEdit
 * ```
 *
 * These tests stand in a throwing connection for Compose's and pin the contract of
 * [GuardedInputConnection]: range failures on editing calls are reported and
 * dropped, everything else keeps its normal behaviour.
 */
@RunWith(AndroidJUnit4::class)
class GuardedInputConnectionTest {
    private val hostView = View(InstrumentationRegistry.getInstrumentation().targetContext)

    private inner class RecordingConnection(
        private val failure: (() -> Nothing)? = null,
    ) : BaseInputConnection(hostView, false) {
        val calls = mutableListOf<String>()

        private fun record(name: String): Boolean {
            calls.add(name)
            failure?.invoke()
            return true
        }

        override fun beginBatchEdit(): Boolean = record("beginBatchEdit")

        override fun endBatchEdit(): Boolean = record("endBatchEdit")

        override fun setSelection(
            start: Int,
            end: Int,
        ): Boolean = record("setSelection")

        override fun commitText(
            text: CharSequence?,
            newCursorPosition: Int,
        ): Boolean = record("commitText")

        override fun getTextBeforeCursor(
            length: Int,
            flags: Int,
        ): CharSequence {
            calls.add("getTextBeforeCursor")
            failure?.invoke()
            return "before"
        }
    }

    private class Desyncs {
        val commands = mutableListOf<String>()
        val errors = mutableListOf<RuntimeException>()

        fun record(
            command: String,
            e: RuntimeException,
        ) {
            commands.add(command)
            errors.add(e)
        }
    }

    /** The exact shape Compose's `requireValidRange` throws. */
    private fun staleRange(): Nothing = throw IllegalArgumentException("Expected TextRange(130, 130) to be in TextRange(0, 80)")

    @Test
    fun setSelectionOutOfRange_isDroppedAndReported() {
        val desyncs = Desyncs()
        val guarded = GuardedInputConnection(RecordingConnection(::staleRange), desyncs::record)

        // Must not throw: this is the call that kills the process today.
        assertTrue(guarded.setSelection(130, 130))

        assertEquals(listOf("setSelection"), desyncs.commands)
        assertEquals(
            "Expected TextRange(130, 130) to be in TextRange(0, 80)",
            desyncs.errors.single().message,
        )
    }

    @Test
    fun endBatchEditOutOfRange_isDroppedAndReportsNoOngoingBatch() {
        val desyncs = Desyncs()
        val guarded = GuardedInputConnection(RecordingConnection(::staleRange), desyncs::record)

        assertEquals(false, guarded.endBatchEdit())
        assertEquals(listOf("endBatchEdit"), desyncs.commands)
    }

    @Test
    fun otherEditingCallsAreGuardedToo() {
        val desyncs = Desyncs()
        val guarded = GuardedInputConnection(RecordingConnection(::staleRange), desyncs::record)

        assertTrue(guarded.beginBatchEdit())
        assertTrue(guarded.commitText("hello", 1))

        assertEquals(listOf("beginBatchEdit", "commitText"), desyncs.commands)
    }

    @Test
    fun indexOutOfBoundsIsGuarded() {
        val desyncs = Desyncs()
        val guarded =
            GuardedInputConnection(
                RecordingConnection { throw IndexOutOfBoundsException("index 130, length 80") },
                desyncs::record,
            )

        assertTrue(guarded.setSelection(130, 130))
        assertEquals(listOf("setSelection"), desyncs.commands)
    }

    @Test
    fun healthyCallsPassThroughUntouched() {
        val desyncs = Desyncs()
        val delegate = RecordingConnection()
        val guarded = GuardedInputConnection(delegate, desyncs::record)

        assertTrue(guarded.beginBatchEdit())
        assertTrue(guarded.setSelection(3, 3))
        assertTrue(guarded.commitText("hi", 1))
        assertTrue(guarded.endBatchEdit())

        assertEquals(
            listOf("beginBatchEdit", "setSelection", "commitText", "endBatchEdit"),
            delegate.calls,
        )
        assertTrue(desyncs.commands.isEmpty())
    }

    @Test
    fun readOnlyCallsAreNotGuarded() {
        val desyncs = Desyncs()
        val guarded = GuardedInputConnection(RecordingConnection(::staleRange), desyncs::record)

        // Reads can't desync the buffer, so a failure there is a real bug and
        // must keep propagating instead of being silently swallowed.
        val thrown =
            runCatching { guarded.getTextBeforeCursor(10, 0) }
                .exceptionOrNull()

        assertTrue(thrown is IllegalArgumentException)
        assertTrue(desyncs.commands.isEmpty())
    }
}
