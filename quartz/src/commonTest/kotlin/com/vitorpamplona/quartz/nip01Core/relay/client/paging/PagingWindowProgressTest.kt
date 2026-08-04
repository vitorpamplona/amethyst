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
package com.vitorpamplona.quartz.nip01Core.relay.client.paging

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PagingWindowProgressTest {
    private fun assertClose(
        expected: Double,
        actual: Double?,
        message: String? = null,
    ) {
        assertTrue(actual != null && kotlin.math.abs(expected - actual) < 0.001, "${message ?: ""} expected $expected got $actual")
    }

    @Test
    fun `progress is the paged share of the time window`() {
        val p = PagingWindowProgress()
        p.begin("a", top = 1_000L, bottom = 0L)

        assertClose(0.0, p.fraction(), "nothing paged yet")

        p.mark("a", 750L)
        assertClose(0.25, p.fraction())

        p.mark("a", 100L)
        assertClose(0.90, p.fraction())
    }

    @Test
    fun `a page that jumps backwards cannot un-advance the pagination`() {
        // Pages arrive from one relay in order, but nothing in the protocol
        // guarantees it, and a percentage that goes DOWN is worse than one that
        // is slightly wrong — it reads as the sync having lost ground.
        val p = PagingWindowProgress()
        p.begin("a", top = 1_000L, bottom = 0L)

        p.mark("a", 200L)
        p.mark("a", 900L)

        assertClose(0.80, p.fraction(), "the later higher until is ignored")
    }

    @Test
    fun `windows average rather than sum`() {
        // Two relays each paging their own window: one done and one untouched
        // is half way — not 100% as summing would give.
        val p = PagingWindowProgress()
        p.begin("a", top = 1_000L, bottom = 0L)
        p.begin("b", top = 500L, bottom = 0L)

        p.mark("a", 0L)

        assertClose(0.5, p.fraction())
    }

    @Test
    fun `a finished window leaves the average`() {
        val p = PagingWindowProgress()
        p.begin("a", top = 1_000L, bottom = 0L)
        p.begin("b", top = 1_000L, bottom = 0L)
        p.mark("b", 500L)

        p.finish("a")

        assertClose(0.5, p.fraction(), "only b is still paging")
        p.finish("b")
        assertNull(p.fraction(), "nothing paging means no number to report")
    }

    @Test
    fun `a group prefix scopes the numbers to its own paginations`() {
        // One instance serves many concurrent paginations; without the scope two
        // streams would print each other's percentages.
        val p = PagingWindowProgress()
        p.begin("streamA|wss://r1", top = 1_000L, bottom = 0L)
        p.begin("streamB|wss://r2", top = 1_000L, bottom = 0L)
        p.mark("streamA|wss://r1", 0L)

        assertClose(1.0, p.fraction("streamA"))
        assertClose(0.0, p.fraction("streamB"))
        assertClose(0.5, p.fraction())
    }

    @Test
    fun `an inverted window is not a pagination`() {
        // A leg whose since is above its until asks for a range nothing can be
        // in. Dividing by that span would produce infinities on the status line.
        val p = PagingWindowProgress()

        p.begin("a", top = 100L, bottom = 900L)

        assertNull(p.fraction())
    }

    @Test
    fun `a single-second window is a pagination`() {
        // Coverage legs re-read a band's edge second: since == until is a
        // real, one-second range, not an inverted one.
        val p = PagingWindowProgress()

        p.begin("a", top = 500L, bottom = 500L)

        assertClose(0.0, p.fraction(), "tracked from its start")
        p.mark("a", 500L)
        assertClose(0.0, p.fraction(), "still at its only second")
        p.finish("a")
        assertNull(p.fraction())
    }

    @Test
    fun `no ETA before the estimate means anything`() {
        val p = PagingWindowProgress()
        p.begin("a", top = 1_000_000L, bottom = 0L)

        p.mark("a", 999_000L)

        // 0.1% in: extrapolating here yields days-long ETAs from connect
        // latency alone, which is worse than printing nothing.
        assertNull(p.etaMs(), "too early to extrapolate")
    }

    @Test
    fun `ETA extrapolates from the rate achieved so far`() {
        var clock = 1_000_000L
        val p = PagingWindowProgress(nowMillis = { clock })
        p.begin("a", top = 1_000L, bottom = 0L)
        p.mark("a", 500L)

        // Half way after six seconds: whatever has elapsed is also what remains.
        clock += 6_000
        assertEquals(6_000L, p.etaMs())
    }
}
