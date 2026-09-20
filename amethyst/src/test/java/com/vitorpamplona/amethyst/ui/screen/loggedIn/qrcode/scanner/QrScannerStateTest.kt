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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.qrcode.scanner

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class QrScannerStateTest {
    private val frame = ScanFrame(720, 1280)

    private fun result(
        text: String,
        sequenceId: String? = null,
        sequenceIndex: Int = -1,
        sequenceSize: Int = -1,
    ) = ScanResult(
        text = text,
        bounds = null,
        sequenceId = sequenceId,
        sequenceIndex = sequenceIndex,
        sequenceSize = sequenceSize,
    )

    private fun scan(
        vararg results: ScanResult,
        brightness: Float = 1f,
    ) = FrameScan(results.toList(), frame, brightness)

    // ---- the tap path ----

    @Test
    fun `a tapped candidate is accepted even right after the same code was submitted`() {
        val state = QrScannerState()

        // Alone in frame, so it is auto-accepted.
        assertEquals("npub1aaa", state.onFrame(scan(result("npub1aaa")), 1_000L))

        // The caller could not use it; the user dismisses the sheet.
        state.onRejected(classified())
        state.dismissRejection(1_100L)

        // A second code enters the frame, so nothing is auto-accepted any more...
        assertNull(state.onFrame(scan(result("npub1aaa"), result("npub1bbb")), 1_200L))

        // ...and the user taps the first one deliberately, inside the dedupe window.
        assertEquals("npub1aaa", state.onCandidateTapped(result("npub1aaa"), 1_300L))
    }

    // ---- multi-part sequences ----

    @Test
    fun `a half-captured sequence is dropped once an unrelated code is being scanned`() {
        val state = QrScannerState()

        assertNull(state.onFrame(scan(result("part0", "seq", 0, 3)), 1_000L))
        assertEquals(1 to 3, state.sequenceProgress)

        // The user gives up and scans an ordinary code instead, for well past the timeout.
        var now = 2_000L
        repeat(5) {
            state.onFrame(scan(result("npub1zzz")), now)
            now += StructuredAppendAccumulator.DEFAULT_TIMEOUT_MS / 2
        }

        assertNull("the abandoned sequence hint is still on screen", state.sequenceProgress)
    }

    @Test
    fun `a part claiming an index outside its own size never joins into the payload`() {
        val state = QrScannerState()

        // Two parts arrive for a 2-part sequence, but the second claims index 7. The count is
        // satisfied while index 1 is still missing, and splicing "a" with a part that does not
        // belong at that position would hand the caller a corrupt payload.
        assertNull(state.onFrame(scan(result("a", "seq", 0, 2)), 1_000L))
        assertNull(state.onFrame(scan(result("b", "seq", 7, 2)), 1_100L))

        // The genuine part 1 completes it, and the stray index is not spliced in.
        assertEquals("ab!", state.onFrame(scan(result("b!", "seq", 1, 2)), 1_200L))
    }

    // ---- the rules that already work, pinned so they keep working ----

    @Test
    fun `one code alone in frame is accepted, and not again while it is held there`() {
        val state = QrScannerState()

        assertEquals("npub1aaa", state.onFrame(scan(result("npub1aaa")), 1_000L))
        assertNull(state.onFrame(scan(result("npub1aaa")), 1_100L))
        assertNull(state.onFrame(scan(result("npub1aaa")), 1_000L + QrScannerState.DEDUPE_MS - 1))
        assertEquals("npub1aaa", state.onFrame(scan(result("npub1aaa")), 1_000L + QrScannerState.DEDUPE_MS))
    }

    @Test
    fun `several codes in frame are drawn but none is chosen`() {
        val state = QrScannerState()

        assertNull(state.onFrame(scan(result("npub1aaa"), result("npub1bbb")), 1_000L))
        assertEquals(2, state.candidates.size)
    }

    @Test
    fun `nothing is decided while the cannot-open sheet is up`() {
        val state = QrScannerState()

        state.onRejected(classified())
        assertNull(state.onFrame(scan(result("npub1bbb")), 1_000L))
        assertTrue(state.candidates.isEmpty())
    }

    @Test
    fun `the torch is offered only after the scene has been dark for a while`() {
        val state = QrScannerState()

        state.onFrame(scan(brightness = 0.05f), 1_000L)
        assertFalse(state.isDark)

        state.onFrame(scan(brightness = 0.05f), 1_000L + QrScannerState.DARK_DWELL_MS)
        assertTrue(state.isDark)

        state.onFrame(scan(brightness = 0.9f), 2_500L)
        assertFalse(state.isDark)
    }

    private fun classified() = classifyScannedPayload("not something this screen takes")
}
