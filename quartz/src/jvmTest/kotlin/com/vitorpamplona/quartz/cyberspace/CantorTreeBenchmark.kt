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
package com.vitorpamplona.quartz.cyberspace

import com.vitorpamplona.quartz.utils.bigint.UBigInt
import java.math.BigInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * What a region key costs, and what writing the arithmetic cost against
 * aliasing Java's.
 *
 * §7's whole feasibility argument is a number, and the plan's budget model —
 * what a §7.7 sweep may be offered without asking — is read straight off it. A
 * change that quietly makes a key five times slower does not break a test
 * anywhere else; it makes a card lie about how long a search will take. So the
 * number lives here.
 *
 * The comparison against `java.math.BigInteger` is the price of portability.
 * [UBigInt] exists because a `expect`/`actual` would need a hand-written Apple
 * and Linux implementation regardless, and two implementations of a consensus
 * value is two chances to disagree — but Java's Toom-Cook is real and this
 * measures how much of it was given up.
 */
class CantorTreeBenchmark {
    private fun javaPair(
        a: BigInteger,
        b: BigInteger,
    ): BigInteger {
        val s = a.add(b)
        return s.multiply(s.add(BigInteger.ONE)).shiftRight(1).add(b)
    }

    private fun javaRoot(
        base: BigInteger,
        height: Int,
    ): BigInteger {
        if (height == 0) return base
        val values = arrayOfNulls<BigInteger>(height + 2)
        val levels = IntArray(height + 2)
        var top = 0
        for (i in 0 until (1L shl height)) {
            var v = base.add(BigInteger.valueOf(i))
            var lvl = 0
            while (top > 0 && levels[top - 1] == lvl) {
                top--
                v = javaPair(values[top]!!, v)
                lvl++
            }
            values[top] = v
            levels[top] = lvl
            top++
        }
        return values[0]!!
    }

    private fun millis(block: () -> Unit): Double {
        val start = System.nanoTime()
        block()
        return (System.nanoTime() - start) / 1e6
    }

    @Test
    fun aRegionKeyCostsWhatThePlanSaysItDoes() {
        val point = CyberspaceCoordinate.decode("c492492492492492492492edf5bee7267451c787d95ba4d7840c76d1e33c9940")!!
        repeat(30) { RegionKey.at(point, 6) }
        repeat(30) { javaRoot(BigInteger.valueOf(123456789L), 6) }

        // The two costs a §7.7 sweep is made of: a box of gap G needs
        // 3 * 2^(G/3) axis roots and 2^G combines, and the second dominates.
        println("height | axis root ms | combine+2sha ms | java root ms | ratio")
        for (height in listOf(8, 10, 12, 14)) {
            val base = point.alignedBase(height).x.toUBigInt()
            val ours = millis { CantorTree.subtreeRoot(base, height) }
            val root = CantorTree.subtreeRoot(base, height)
            val reps = if (height <= 10) 50 else 5
            val combine = millis { repeat(reps) { RegionKey.derive(CantorTree.cantorPair(CantorTree.cantorPair(root, root), root)) } } / reps
            val java = millis { javaRoot(BigInteger(1, base.toMinimalBytes()), height) }
            println("$height | ${fmt(ours)} | ${fmt(combine)} | ${fmt(java)} | ${fmt(ours / java)}x")
        }

        // Loose on purpose — a shared box is noisy, and what would invalidate
        // the budget model is an order of magnitude, not a busy minute.
        val atEight = millis { RegionKey.at(point, 8) }
        assertTrue(atEight < 500.0, "a height-8 key should be milliseconds, was ${fmt(atEight)} ms")
    }

    @Test
    fun theTwoImplementationsAgreeOnTheRootItself() {
        // The differential test covers the arithmetic; this covers the fold
        // built on it, which is where an off-by-one in the stack would live.
        for (height in 0..12) {
            // A base with bits set high and low, so the fold is not walking zeros.
            val base = UBigInt.of((1L shl 62) + 1_234_567L + height)
            val mine = CantorTree.subtreeRoot(base, height)
            val theirs = javaRoot(BigInteger(1, base.toMinimalBytes()), height)
            assertEquals(theirs, BigInteger(1, mine.toMinimalBytes()), "height $height")
        }
    }

    private fun fmt(value: Double) = ((value * 100).toLong() / 100.0).toString()
}
