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
package com.vitorpamplona.quartz.nip17Dm.files

import com.vitorpamplona.quartz.experimental.audio.header.tags.WaveformTag
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * The waveform an encrypted voice message carries.
 *
 * The renderer asks the event for this to decide between drawing real bars and
 * a bare transport, so the tag name has to keep matching what a sender writes —
 * which is what these assert, rather than the parser, which is WaveformTag's.
 */
class ChatMessageEncryptedFileHeaderWaveformTest {
    private fun event(vararg tags: Array<String>) =
        ChatMessageEncryptedFileHeaderEvent(
            id = "00".repeat(32),
            pubKey = "11".repeat(32),
            createdAt = 1_700_000_000L,
            tags = arrayOf(*tags),
            content = "https://blossom.example/abc",
            sig = "sig",
        )

    @Test
    fun readsTheWaveformASenderAttached() {
        val wave = listOf(0.0f, 0.5f, 1.0f)

        assertEquals(wave, event(WaveformTag.assemble(wave)).waveform())
    }

    @Test
    fun aFileWithNoWaveformHasNone() {
        assertNull(event(arrayOf("m", "audio/mp4")).waveform())
    }

    @Test
    fun anUnparseableWaveformIsNoWaveformRatherThanACrash() {
        // A peer can put anything in a tag, and a voice message that fails to
        // render at all is worse than one that renders without bars.
        assertNull(event(arrayOf(WaveformTag.TAG_NAME, "not json")).waveform())
        assertNull(event(arrayOf(WaveformTag.TAG_NAME, "[]")).waveform())
    }
}
