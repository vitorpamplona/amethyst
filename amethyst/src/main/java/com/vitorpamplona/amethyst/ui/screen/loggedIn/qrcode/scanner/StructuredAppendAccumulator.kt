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

/**
 * Reassembles a Structured Append payload — one logical string split across several physical QR
 * codes, each tagged with a sequence id, an index and a total.
 *
 * Why bother: a QR code's capacity falls off a cliff as the payload grows, because more data
 * means more modules in the same physical space, and small modules are exactly what defeats a
 * camera at arm's length. Splitting is how a long payload (a key backup, an `naddr` with several
 * relay hints) stays scannable, and Structured Append is the standard way to do it. We could not
 * read one at all before.
 *
 * Not thread-safe; it is driven from the analysis executor only.
 */
class StructuredAppendAccumulator(
    private val timeoutMs: Long = DEFAULT_TIMEOUT_MS,
) {
    private var sequenceId: String? = null
    private var expected: Int = 0
    private var lastUpdateMs: Long = 0
    private val parts = mutableMapOf<Int, String>()

    /** Parts captured so far for the sequence in progress. */
    val captured: Int get() = parts.size

    /** How many parts the sequence in progress needs in total, or 0 when idle. */
    val total: Int get() = expected

    /**
     * Feeds one decoded part in.
     *
     * Returns the joined payload once every part has been seen, or null while the sequence is
     * still incomplete. A part from a different sequence, or one arriving after [timeoutMs] of
     * silence, restarts the accumulation rather than corrupting it — someone who gives up
     * halfway and points the camera at a different code should not get a splice of the two.
     */
    fun add(
        result: ScanResult,
        nowMs: Long,
    ): String? {
        if (!result.isPartOfSequence) return null

        val id = result.sequenceId
        val stale = nowMs - lastUpdateMs > timeoutMs
        if (id != sequenceId || expected != result.sequenceSize || stale) {
            reset()
            sequenceId = id
            expected = result.sequenceSize
        }

        lastUpdateMs = nowMs
        parts[result.sequenceIndex] = result.text

        if (parts.size < expected) return null

        val joined = (0 until expected).joinToString("") { parts[it] ?: return null }
        reset()
        return joined
    }

    fun reset() {
        sequenceId = null
        expected = 0
        parts.clear()
    }

    companion object {
        /**
         * Long enough to walk around a poster and catch the parts, short enough that an abandoned
         * half-sequence does not linger into the next scan.
         */
        const val DEFAULT_TIMEOUT_MS = 30_000L
    }
}
