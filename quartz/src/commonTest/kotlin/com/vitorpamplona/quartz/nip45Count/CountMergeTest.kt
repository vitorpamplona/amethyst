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
package com.vitorpamplona.quartz.nip45Count

import com.vitorpamplona.quartz.nip01Core.relay.commands.toClient.CountResult
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CountMergeTest {
    /** Register-index offset into the key, as a relay counting by `#e` would use. */
    private val offset = 8

    /** A relay that answered with a plain count and no registers. */
    private fun plain(count: Int) = CountResult(count = count, approximate = false, hll = null)

    /**
     * A relay that answered with HLL registers built from [ids] distinct 32-byte keys.
     *
     * Real registers rather than hand-written bytes, so the union assertions exercise the same
     * add/merge/estimate path a relay's answer would.
     */
    private fun hll(ids: Iterable<Int>): CountResult {
        val builder = HllBuilder(offset = offset)
        ids.forEach { builder.add(key(it)) }
        return builder.toCountResult()
    }

    /**
     * Deterministic distinct 32-byte key.
     *
     * Pseudo-random bytes rather than a counter written into the array: HyperLogLog reads a
     * register index and a leading-zero run straight out of the key and assumes those bits are
     * uniformly distributed, which is true of real event ids and false of counters — structured
     * keys pile identical values into the registers and the estimator goes wild.
     */
    private fun key(n: Int): ByteArray = Random(n).nextBytes(32)

    @Test
    fun noAnswersMergeToNull() {
        assertNull(mergeCountResults(emptyList()))
    }

    @Test
    fun plainCountsTakeTheLargestNeverTheSum() {
        val merged = mergeCountResults(listOf(plain(10), plain(500), plain(120)))

        assertNotNull(merged)
        // 630 would be the sum. Relays mirror each other, so the union is at least the biggest
        // single relay and at most the sum — only the lower bound is defensible.
        assertEquals(500, merged.count)
        assertEquals(null, merged.hll)
    }

    @Test
    fun aSingleRelayPassesItsOwnAnswerThrough() {
        val merged = mergeCountResults(listOf(plain(42)))

        assertNotNull(merged)
        assertEquals(42, merged.count)
    }

    @Test
    fun overlappingHllRegistersCollapseInsteadOfStacking() {
        // Two relays holding heavily overlapping sets: 0..199 and 100..299.
        val a = hll(0 until 200)
        val b = hll(100 until 300)
        val merged = mergeCountResults(listOf(a, b))

        assertNotNull(merged)
        assertTrue(merged.approximate)
        assertNotNull(merged.hll)

        // The claim under test is the merge property, not the estimator's accuracy: merging two
        // relays' registers must give exactly what one relay holding the union would have reported.
        // (HLL at m=256 has no bias correction and reads high around n≈m, so asserting a band
        // around the true 300 would be testing the estimator, not this function.)
        assertEquals(hll(0 until 300).count, merged.count)

        // And it must land nowhere near the 400 a naive sum would claim.
        assertTrue(merged.count < a.count + b.count, "merging must collapse the overlap, not stack it")
    }

    @Test
    fun disjointHllRegistersMergeToTheUnionToo() {
        val merged = mergeCountResults(listOf(hll(0 until 150), hll(500 until 650)))

        assertNotNull(merged)
        assertEquals(hll((0 until 150) + (500 until 650)).count, merged.count)
    }

    @Test
    fun aPlainCountLargerThanTheHllUnionIsNotDiscarded() {
        // The regression this test exists for: a relay outside the register set can hold far more
        // than the relays that supplied registers. Estimating from the registers alone threw that
        // relay's answer away and reported ~50 instead of 5000.
        val merged = mergeCountResults(listOf(hll(0 until 50), plain(5000)))

        assertNotNull(merged)
        assertEquals(5000, merged.count)
        // Still approximate, and the registers are still carried so a caller can merge again.
        assertTrue(merged.approximate)
        assertNotNull(merged.hll)
    }

    @Test
    fun aPlainCountSmallerThanTheHllUnionDoesNotDragItDown() {
        val union = hll(0 until 300)
        val merged = mergeCountResults(listOf(union, plain(5)))

        assertNotNull(merged)
        assertEquals(union.count, merged.count)
    }

    @Test
    fun mergedRegistersAreTheUnionOfTheInputs() {
        val a = hll(0 until 100)
        val b = hll(100 until 200)
        val merged = mergeCountResults(listOf(a, b))

        assertNotNull(merged)
        val expected = HyperLogLog.merge(listOf(a.hll!!, b.hll!!))
        assertEquals(expected.toList(), merged.hll!!.toList())
    }

    @Test
    fun theResultIsAlwaysAtLeastEveryIndividualAnswer() {
        // The property that makes this safe to show as "at least this many".
        val answers = listOf(plain(7), plain(31), hll(0 until 20), plain(12))
        val merged = mergeCountResults(answers)

        assertNotNull(merged)
        assertTrue(merged.count >= answers.filter { it.hll == null }.maxOf { it.count })
    }
}
