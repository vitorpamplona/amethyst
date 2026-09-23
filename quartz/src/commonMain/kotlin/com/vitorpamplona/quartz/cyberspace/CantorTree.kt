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

/**
 * `CYBERSPACE_V2.md` §4.5 and §4.6: the Cantor number of an aligned subtree.
 *
 * A region is not a box somebody drew, it is an **aligned** subtree: its base is
 * a multiple of `2^h` and it covers exactly `2^h` leaves, so the boundaries are
 * fixed by arithmetic rather than by anyone's movement. That is the whole reason
 * §7's location keys work — "Two people standing in the same neighbourhood will
 * compute the same Cantor root without ever communicating, because they are both
 * computing the root of the same aligned subtree."
 *
 * The root is that subtree's leaves paired bottom-up until one number is left.
 * It is `O(2^h)` and there is no closed form; the numbers double in width at
 * every level, so a root at height `h` runs to about `86 · 2^h` bits and the
 * cost grows by roughly 2.2x per height — twice the leaves times wider operands.
 * That is not an implementation detail to optimise away, it is the *point*: §7.1
 * makes the work the price of admission, and "looking and walking cost the same"
 * only because this is expensive.
 */
object CantorTree {
    /**
     * The tallest subtree this will build, matching `cyberspace-core`'s
     * `DEFAULT_MAX_COMPUTE_HEIGHT` and `cyberspace-cli`'s `max_compute_height`.
     *
     * Both references refuse rather than try, and so does this: one height past
     * it is twice the leaves and a root twice as wide, and the difference
     * between a request that takes a minute and one that takes the afternoon is
     * a single integer a stranger chose. A caller that wants more says so.
     */
    const val DEFAULT_MAX_COMPUTE_HEIGHT = 20

    /**
     * §4.6: `cantor_pair(a, b) = (a + b)(a + b + 1) / 2 + b`.
     *
     * A bijection on pairs of non-negative integers, which is what makes a root
     * identify one region and no other. The halving is exact because `s(s + 1)`
     * is a product of consecutive integers and therefore even.
     */
    fun cantorPair(
        a: UBigInt,
        b: UBigInt,
    ): UBigInt {
        val sum = a + b
        return (sum * (sum + UBigInt.ONE)).shiftRight(1) + b
    }

    /**
     * The root of the aligned subtree of [height] whose lowest leaf is [base].
     *
     * Folded leaf by leaf against a stack of partial roots rather than a level
     * at a time. The root is the same either way — a pairing at level `k` joins
     * the same two subtrees in the same order whichever way the tree is walked —
     * but a level at a time holds every leaf at once, a million of them at
     * height 20, while the stack holds at most `height + 1` numbers, which
     * together come to about one level's worth.
     */
    fun subtreeRoot(
        base: UBigInt,
        height: Int,
        maxComputeHeight: Int = DEFAULT_MAX_COMPUTE_HEIGHT,
    ): UBigInt {
        require(height >= 0) { "height must be >= 0" }
        require(height <= maxComputeHeight) { "height $height exceeds maxComputeHeight $maxComputeHeight" }
        if (height == 0) return base

        val values = arrayOfNulls<UBigInt>(height + 1)
        val levels = IntArray(height + 1)
        var top = 0

        val leaves = 1L shl height
        for (i in 0 until leaves) {
            var value = base + UBigInt.of(i)
            var level = 0
            while (top > 0 && levels[top - 1] == level) {
                top--
                value = cantorPair(values[top]!!, value)
                level++
            }
            values[top] = value
            levels[top] = level
            top++
        }
        return values[0]!!
    }

    /**
     * §4.5: the height of the smallest aligned subtree holding both values —
     * `bit_length(v1 XOR v2)`, which is how far up the tree they first meet.
     */
    fun lcaHeight(
        a: CyberspaceAxis,
        b: CyberspaceAxis,
    ): Int {
        val high = a.high xor b.high
        val low = a.low xor b.low
        return if (high != 0L) Long.SIZE_BITS + bitLength(high) else bitLength(low)
    }

    private fun bitLength(value: Long): Int {
        var bits = 0
        var v = value
        while (v != 0L) {
            bits++
            v = v ushr 1
        }
        return bits
    }
}
