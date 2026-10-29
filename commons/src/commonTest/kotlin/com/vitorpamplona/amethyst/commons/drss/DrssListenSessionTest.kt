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
package com.vitorpamplona.amethyst.commons.drss

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DrssListenSessionTest {
    /** Plays from [from] to [to] seconds, one sample a second, collecting what the session saves. */
    private fun DrssListenSession.play(
        from: Int,
        to: Int,
        duration: Double = 1000.0,
        startAt: Long = from * 1000L,
    ): List<DrssListenSession.Save> =
        (from..to).mapNotNull { second ->
            onSample(second.toDouble(), duration, isPlaying = true, hasEnded = false, nowMillis = startAt + (second - from) * 1000L)
        }

    @Test
    fun savesOnceAMinuteWhilePlayingButNotOnTheFirstTick() {
        val saves = DrssListenSession().play(1, 130)
        assertEquals(listOf(61L, 121L), saves.map { it.position })
    }

    @Test
    fun savesOnPauseAndOnLeave() {
        val session = DrssListenSession()
        session.play(1, 20)
        val paused = session.onSample(20.0, 1000.0, isPlaying = false, hasEnded = false, nowMillis = 21_000)
        assertEquals(DrssListenSession.Save(20, 1000, countsAPlay = false, ended = false), paused)
        assertNull(session.onLeave(), "the same place is not saved twice")
    }

    @Test
    fun countsAPlayWhenPlaybackCrossesNinetyPercent() {
        val session = DrssListenSession()
        val saves = session.play(880, 905)
        assertEquals(listOf(true), saves.map { it.countsAPlay })
        assertEquals(900L, saves.single().position)
    }

    @Test
    fun aSeekPastNinetyPercentIsNotAPlay() {
        val session = DrssListenSession()
        session.play(10, 20)
        // Jumps from 20 s to 950 s in one second: a seek.
        val jumped = session.onSample(950.0, 1000.0, isPlaying = true, hasEnded = false, nowMillis = 21_000)
        assertNull(jumped)
        val after = session.play(951, 960, startAt = 22_000)
        assertEquals(emptyList(), after.filter { it.countsAPlay })
    }

    @Test
    fun theEndIsSavedOnceWithoutAPlayhead() {
        val session = DrssListenSession()
        session.play(990, 999)
        val end = session.onSample(1000.0, 1000.0, isPlaying = false, hasEnded = true, nowMillis = 11_000)
        assertEquals(DrssListenSession.Save(null, 1000, countsAPlay = false, ended = true), end)
        assertNull(session.onSample(1000.0, 1000.0, isPlaying = false, hasEnded = true, nowMillis = 12_000))
        assertNull(session.onLeave())
    }

    @Test
    fun loadingAtTheResumePointSavesNothing() {
        val session = DrssListenSession()
        assertNull(session.onSample(0.0, 1000.0, isPlaying = false, hasEnded = false, nowMillis = 0))
        assertNull(session.onSample(271.0, 1000.0, isPlaying = false, hasEnded = false, nowMillis = 500))
        assertNull(session.onLeave(), "opened at the resume point but never played")
    }
}
