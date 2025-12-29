/**
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
package com.vitorpamplona.quartz.nip03Timestamp.ots

import com.vitorpamplona.quartz.nip03Timestamp.ots.op.OpAppend
import com.vitorpamplona.quartz.nip03Timestamp.ots.op.OpPrepend
import com.vitorpamplona.quartz.nip03Timestamp.ots.op.OpSHA256

/**
 * Utility functions for merkle trees
 */
object Merkle {
    /**
     * Concatenate left and right, then perform a unary operation on them left and right can be either timestamps or bytes.
     * Appropriate intermediary append/prepend operations will be created as needed for left and right.
     *
     * @param left  the left timestamp parameter
     * @param right the right timestamp parameter
     * @return the concatenation of left and right
     */
    fun catThenUnaryOp(
        left: Timestamp,
        right: Timestamp,
    ): Timestamp? {
        // rightPrependStamp = right.ops.add(OpPrepend(left.msg))
        val rightPrependStamp = right.add(OpPrepend(left.digest))

        // Left and right should produce the same thing, so we can set the timestamp of the left to the right.
        // left.ops[OpAppend(right.msg)] = right_prepend_stamp
        // leftAppendStamp = left.ops.add(OpAppend(right.msg))
        // Timestamp leftPrependStamp = left.add(new OpAppend(right.msg));
        left.ops[OpAppend(right.digest)] = rightPrependStamp

        // return rightPrependStamp.ops.add(unaryOpCls())
        val res = rightPrependStamp.add(OpSHA256())
        return res
    }

    fun catSha256(
        left: Timestamp,
        right: Timestamp,
    ): Timestamp = catThenUnaryOp(left, right)!!

    fun catSha256d(
        left: Timestamp,
        right: Timestamp,
    ): Timestamp? {
        val sha256Timestamp = catSha256(left, right)
        // res = sha256Timestamp.ops.add(OpSHA256());
        val res = sha256Timestamp.add(OpSHA256())
        return res
    }

    /**
     * Merkelize a set of timestamps.
     * A merkle tree of all the timestamps is built in-place using binop() to
     * timestamp each pair of timestamps. The exact algorithm used is structurally
     * identical to a merkle-mountain-range, although leaf sums aren't committed.
     * As this function is under the consensus-critical core, it's guaranteed that
     * the algorithm will not be changed in the future.
     *
     * @param timestamps a list of timestamps
     * @return the timestamp for the tip of the tree.
     */
    fun makeMerkleTree(timestamps: MutableList<Timestamp>): Timestamp? {
        var stamps = timestamps
        var prevStamp: Timestamp? = null
        var exit = false

        while (!exit) {
            if (stamps.isNotEmpty()) {
                prevStamp = stamps[0]
            }

            val subStamps: MutableList<Timestamp> = stamps.subList(1, stamps.size)
            val nextStamps: MutableList<Timestamp> = ArrayList()

            for (stamp in subStamps) {
                if (prevStamp == null) {
                    prevStamp = stamp
                } else {
                    nextStamps.add(catSha256(prevStamp, stamp))
                    prevStamp = null
                }
            }

            if (nextStamps.size == 0) {
                exit = true
            } else {
                if (prevStamp != null) {
                    nextStamps.add(prevStamp)
                }

                stamps = nextStamps
            }
        }

        return prevStamp
    }
}
