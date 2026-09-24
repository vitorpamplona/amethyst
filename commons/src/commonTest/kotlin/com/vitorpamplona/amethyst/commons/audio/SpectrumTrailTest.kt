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
package com.vitorpamplona.amethyst.commons.audio

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertNotSame
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The whole per-displayed-frame step the visualizer runs: take one queued frame, apply the decay
 * trail against what is already drawn, and hand back a fresh array. Extracted from SpectrumCanvas
 * so it is testable without a Compose harness — the composable is left as plumbing around this.
 */
class SpectrumTrailTest {
    private fun frame(vararg bins: Float) = Spectrum(bins)

    @Test
    fun yieldsNothingWhenStarvedSoTheDrawnFrameIsHeld() {
        val trail = SpectrumTrail(decay = 0.5f)
        assertNull(trail.nextOrNull())
    }

    @Test
    fun theFirstFrameIsDrawnAsIsWithNothingToDecayFrom() {
        val trail = SpectrumTrail(decay = 0.5f)
        trail.offer(frame(1f, 0.5f, 0f))

        assertContentEquals(floatArrayOf(1f, 0.5f, 0f), trail.nextOrNull())
    }

    @Test
    fun aRisingBinTakesItsNewValueImmediately() {
        val trail = SpectrumTrail(decay = 0.5f)
        trail.offer(frame(0.2f))
        trail.nextOrNull()
        trail.offer(frame(0.9f))

        assertContentEquals(floatArrayOf(0.9f), trail.nextOrNull())
    }

    @Test
    fun aFallingBinDecaysFromWhatWasDrawnRatherThanSnapping() {
        val trail = SpectrumTrail(decay = 0.5f)
        trail.offer(frame(1f))
        trail.nextOrNull()
        trail.offer(frame(0f))

        // 1f * 0.5 decay beats the new 0f, so the bar falls gradually.
        assertContentEquals(floatArrayOf(0.5f), trail.nextOrNull())
    }

    @Test
    fun eachDrawGetsAFreshArraySoComposeSeesTheChange() {
        val trail = SpectrumTrail(decay = 0.5f)
        trail.offer(frame(1f))
        trail.offer(frame(1f))

        val first = trail.nextOrNull()
        val second = trail.nextOrNull()

        // mutableStateOf compares by reference; reusing one array would never trigger a redraw.
        assertNotSame(first, second)
    }

    @Test
    fun aBacklogIsBoundedByDroppingTheStalestFrame() {
        val trail = SpectrumTrail(decay = 0f, capacity = 2)
        trail.offer(frame(1f))
        trail.offer(frame(2f))
        trail.offer(frame(3f)) // evicts the 1f frame

        assertContentEquals(floatArrayOf(2f), trail.nextOrNull())
        assertContentEquals(floatArrayOf(3f), trail.nextOrNull())
        assertTrue(trail.nextOrNull() == null)
    }
}
