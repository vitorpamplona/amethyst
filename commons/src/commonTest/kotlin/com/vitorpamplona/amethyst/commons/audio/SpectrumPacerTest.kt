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
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SpectrumPacerTest {
    private fun frame(id: Float) = Spectrum(floatArrayOf(id))

    private fun idOf(spectrum: Spectrum?) = spectrum?.bins?.first()

    @Test
    fun drainsOneFramePerTickInArrivalOrder() {
        val pacer = SpectrumPacer(capacity = 8)
        pacer.offer(frame(1f))
        pacer.offer(frame(2f))
        pacer.offer(frame(3f))

        assertEquals(1f, idOf(pacer.next()))
        assertEquals(2f, idOf(pacer.next()))
        assertEquals(3f, idOf(pacer.next()))
    }

    @Test
    fun returnsNullWhenStarvedSoTheCallerCanHoldTheLastFrame() {
        val pacer = SpectrumPacer(capacity = 8)
        assertNull(pacer.next())

        pacer.offer(frame(1f))
        assertEquals(1f, idOf(pacer.next()))
        assertNull(pacer.next())
    }

    @Test
    fun overCapacityDropsTheStalestFrameNotTheNewest() {
        val pacer = SpectrumPacer(capacity = 3)
        pacer.offer(frame(1f))
        pacer.offer(frame(2f))
        pacer.offer(frame(3f))
        pacer.offer(frame(4f)) // evicts 1

        assertEquals(2f, idOf(pacer.next()))
        assertEquals(3f, idOf(pacer.next()))
        assertEquals(4f, idOf(pacer.next()))
        assertNull(pacer.next())
    }
}
