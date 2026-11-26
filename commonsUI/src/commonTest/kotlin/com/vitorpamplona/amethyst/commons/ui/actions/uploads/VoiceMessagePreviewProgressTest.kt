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
package com.vitorpamplona.amethyst.commons.ui.actions.uploads

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * The preview's bar and scrub, which now read the shared player: its duration is
 * [com.vitorpamplona.amethyst.commons.ui.playback.player.AmethystPlayer.TIME_UNSET] (a large
 * negative number) until the file is loaded, where MediaPlayer had it at once.
 */
class VoiceMessagePreviewProgressTest {
    private val unset = Long.MIN_VALUE + 1

    @Test
    fun progressIsTheShareOfTheClipPlayed() {
        assertEquals(0.25f, calculateProgress(2_500, 10_000))
    }

    @Test
    fun progressStaysAtTheStartWhileTheDurationIsUnknown() {
        assertEquals(0f, calculateProgress(0, unset))
        assertEquals(0f, calculateProgress(1_000, 0))
    }

    @Test
    fun progressNeverPassesTheEnds() {
        assertEquals(1f, calculateProgress(12_000, 10_000))
        assertEquals(0f, calculateProgress(-5, 10_000))
    }

    @Test
    fun aScrubLandsInsideTheClip() {
        assertEquals(5_000L, scrubPositionMs(0.5f, 10_000))
        assertEquals(10_000L, scrubPositionMs(1f, 10_000))
    }

    @Test
    fun aScrubCannotSeekBeforeTheClipIsLoaded() {
        assertNull(scrubPositionMs(0.5f, unset))
        assertNull(scrubPositionMs(0.5f, 0))
    }

    @Test
    fun anOutOfRangeScrubIsIgnored() {
        assertNull(scrubPositionMs(1.5f, 10_000))
        assertNull(scrubPositionMs(-0.1f, 10_000))
        assertNull(scrubPositionMs(Float.NaN, 10_000))
    }
}
