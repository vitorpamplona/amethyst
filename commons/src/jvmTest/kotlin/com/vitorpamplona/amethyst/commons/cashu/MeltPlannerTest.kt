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
package com.vitorpamplona.amethyst.commons.cashu

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MeltPlannerTest {
    private val mintA = "https://mint.a.example"
    private val mintB = "https://mint.b.example"

    @Test
    fun `single invoice funded by single mint`() {
        val plan = MeltPlanner.plan(listOf(21L), mapOf(mintA to 100L))

        assertNotNull(plan)
        assertEquals(mapOf(mintA to listOf(0)), plan)
    }

    @Test
    fun `balance too low returns null`() {
        // 21 sats + 10 fee slack > 30 available
        assertNull(MeltPlanner.plan(listOf(21L), mapOf(mintA to 30L)))
    }

    @Test
    fun `slack exactly covered plans`() {
        // 21 sats + 10 fee slack == 31 available
        val plan = MeltPlanner.plan(listOf(21L), mapOf(mintA to 31L))

        assertEquals(mapOf(mintA to listOf(0)), plan)
    }

    @Test
    fun `split across two mints when one cannot cover both`() {
        // Two 21-sat invoices, each needing 31 with slack. The first takes the smaller
        // mint that fits it (A, 31); the second must go to B.
        val plan = MeltPlanner.plan(listOf(21L, 21L), mapOf(mintA to 31L, mintB to 40L))

        assertNotNull(plan)
        assertEquals(listOf(0), plan!!.getValue(mintA))
        assertEquals(listOf(1), plan.getValue(mintB))
    }

    @Test
    fun `every invoice index is assigned exactly once`() {
        val plan = MeltPlanner.plan(listOf(21L, 21L, 21L), mapOf(mintA to 1000L))

        assertNotNull(plan)
        assertEquals(listOf(0, 1, 2), plan!!.getValue(mintA).sorted())
    }

    @Test
    fun `an invoice takes the smallest mint that can pay it`() {
        // 21 sats need 31 with slack: mintB (60) pays it and mintA (520) stays whole.
        val plan = MeltPlanner.plan(listOf(21L), mapOf(mintA to 520L, mintB to 60L))

        assertNotNull(plan)
        assertEquals(mapOf(mintB to listOf(0)), plan)
    }

    @Test
    fun `a big invoice leaves the mints it does not need to the invoices after it`() {
        // 40 needs 40, which both mints fit. Taking B (50) leaves A's 62 for both
        // 21-sat invoices (31 each); taking A would leave the third invoice unfundable.
        val plan = MeltPlanner.plan(listOf(40L, 21L, 21L), mapOf(mintA to 62L, mintB to 50L))

        assertNotNull(plan)
        assertEquals(listOf(0), plan!!.getValue(mintB))
        assertEquals(listOf(1, 2), plan.getValue(mintA))
    }

    @Test
    fun `unfundable total returns null even across mints`() {
        assertNull(MeltPlanner.plan(listOf(100L, 100L), mapOf(mintA to 50L, mintB to 50L)))
    }

    @Test
    fun `no mints or no invoices returns null`() {
        assertNull(MeltPlanner.plan(listOf(21L), emptyMap()))
        assertNull(MeltPlanner.plan(emptyList(), mapOf(mintA to 100L)))
    }

    @Test
    fun `assignment respects per-mint capacity`() {
        val plan = MeltPlanner.plan(listOf(30L, 30L, 30L), mapOf(mintA to 100L, mintB to 100L))

        assertNotNull(plan)
        val planned =
            plan!!.entries.sumOf { (mint, idxs) ->
                idxs.size * (30L + MeltPlanner.feeSlackFor(30L))
            }
        // Two invoices fit per mint at 40 sats of capacity each; the third does not.
        assertTrue(planned <= 200L)
        assertEquals(3, plan.values.sumOf { it.size })
    }

    @Test
    fun `fee slack floors at ten sats`() {
        assertEquals(10L, MeltPlanner.feeSlackFor(1L))
        assertEquals(10L, MeltPlanner.feeSlackFor(400L))
    }

    @Test
    fun `fee slack scales with the amount above the floor`() {
        // Real LN fee_reserve is a percentage of the amount, not a flat constant:
        // a 5 000-sat zap reserves 100, a 50 000-sat one 1 000.
        assertEquals(100L, MeltPlanner.feeSlackFor(5_000L))
        assertEquals(1_000L, MeltPlanner.feeSlackFor(50_000L))
    }

    @Test
    fun `a large invoice plans only with proportional slack`() {
        // 5 000 + 100 slack = 5 100: fundable at 5 100, null at 5 099.
        assertNotNull(MeltPlanner.plan(listOf(5_000L), mapOf(mintA to 5_100L)))
        assertNull(MeltPlanner.plan(listOf(5_000L), mapOf(mintA to 5_099L)))
    }
}
