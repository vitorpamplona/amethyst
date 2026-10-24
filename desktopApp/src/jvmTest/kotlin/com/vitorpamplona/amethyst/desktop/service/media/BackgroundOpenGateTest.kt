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
package com.vitorpamplona.amethyst.desktop.service.media

import com.vitorpamplona.amethyst.desktop.service.media.BackgroundOpenGate.Verdict
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * When the engines opened paused beside the watched video may start downloading. Holding them
 * while nothing played left a Home feed's video cards blank for the whole hold; opening them at
 * once then let a screen's cards, which get engines before it picks a video, crowd that video out.
 */
class BackgroundOpenGateTest {
    private val url = "https://media.example/a.mp4"

    private fun starting() = MediaPlaybackState(url = url, isBuffering = true)

    private fun playing(at: Long) = MediaPlaybackState(url = url, isPlaying = true, currentTime = at, position = at / 60_000f)

    @Test
    fun waitsOnlyAMomentWhenNoVideoIsOpen() {
        // A text note in the middle of the feed, or a screen that has not picked its video yet.
        assertEquals(Verdict.IDLE, BackgroundOpenGate().check(MediaPlaybackState(), pauseRequested = false))
    }

    @Test
    fun holdsWhileTheWatchedVideoStarts() {
        val gate = BackgroundOpenGate()
        assertEquals(Verdict.IDLE, gate.check(MediaPlaybackState(), pauseRequested = false))
        assertEquals(Verdict.HOLD, gate.check(starting(), pauseRequested = false))
        // It says it plays a moment before its picture moves.
        assertEquals(Verdict.HOLD, gate.check(playing(0), pauseRequested = false))
        assertEquals(Verdict.OPEN, gate.check(playing(250), pauseRequested = false))
    }

    @Test
    fun aVideoResumedWhereItWasLeftStartsOnlyOnceItsClockMoves() {
        val gate = BackgroundOpenGate()
        assertEquals(Verdict.HOLD, gate.check(starting(), pauseRequested = false))
        // Seeked back to 12 s: past zero, but not moving yet.
        assertEquals(Verdict.HOLD, gate.check(playing(12_000), pauseRequested = false))
        assertEquals(Verdict.HOLD, gate.check(playing(12_000), pauseRequested = false))
        assertEquals(Verdict.OPEN, gate.check(playing(12_250), pauseRequested = false))
    }

    @Test
    fun opensWhenTheUserPausedIt() {
        assertEquals(Verdict.OPEN, BackgroundOpenGate().check(MediaPlaybackState(url = url, currentTime = 5_000), pauseRequested = true))
    }

    @Test
    fun opensWhenItEndedOrFailed() {
        assertEquals(Verdict.OPEN, BackgroundOpenGate().check(MediaPlaybackState(url = url, position = 1f, currentTime = 60_000), pauseRequested = false))
        assertEquals(Verdict.OPEN, BackgroundOpenGate().check(starting().copy(errorReason = "Source: 404"), pauseRequested = false))
    }

    @Test
    fun anotherVideoStartsOver() {
        val gate = BackgroundOpenGate()
        assertEquals(Verdict.HOLD, gate.check(playing(1_000), pauseRequested = false))
        // The feed moved on before the first one moved: the next one's clock is what counts.
        val next = MediaPlaybackState(url = "https://media.example/b.mp4", isPlaying = true, currentTime = 1_500)
        assertEquals(Verdict.HOLD, gate.check(next, pauseRequested = false))
        assertEquals(Verdict.OPEN, gate.check(next.copy(currentTime = 1_750), pauseRequested = false))
    }
}
