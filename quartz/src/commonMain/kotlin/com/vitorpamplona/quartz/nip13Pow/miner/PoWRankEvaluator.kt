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
package com.vitorpamplona.quartz.nip13Pow.miner

import com.vitorpamplona.quartz.nip01Core.core.HexKey

class PoWRankEvaluator {
    companion object {
        const val R8 = 0b00000000.toByte()
        const val R7 = 0b00000001.toByte()
        const val R6 = 0b00000010.toByte()
        const val R5 = 0b00000100.toByte()
        const val R4 = 0b00001000.toByte()
        const val R3 = 0b00010000.toByte()
        const val R2 = 0b00100000.toByte()
        const val R1 = 0b01000000.toByte()
        const val NEGATIVE = 0b10000000.toByte()

        fun compute(
            id: HexKey,
            commitedPoW: Int?,
        ): Int {
            val actualRank = calculatePowRankOf(id)

            return if (commitedPoW == null) {
                actualRank
            } else {
                if (actualRank >= commitedPoW) {
                    commitedPoW
                } else {
                    actualRank
                }
            }
        }

        fun calculatePowRankOf(id: HexKey): Int {
            var rank = 0
            for (i in 0..id.length) {
                if (id[i] == '0') {
                    rank += 4
                } else if (id[i] in '4'..'7') {
                    rank += 1
                    break
                } else if (id[i] in '2'..'3') {
                    rank += 2
                    break
                } else if (id[i] == '1') {
                    rank += 3
                    break
                } else {
                    break
                }
            }
            return rank
        }

        fun calculatePowRankOf(id: ByteArray): Int {
            var rank = 0
            for (byte in id) {
                if (byte == R8) {
                    rank += 8
                } else if (byte < 0) {
                    break
                } else {
                    if (byte < R6) {
                        rank += 7
                    } else if (byte < R5) {
                        rank += 6
                    } else if (byte < R4) {
                        rank += 5
                    } else if (byte < R3) {
                        rank += 4
                    } else if (byte < R2) {
                        rank += 3
                    } else if (byte < R1) {
                        rank += 2
                    } else {
                        rank += 1
                    }
                    break
                }
            }
            return rank
        }

        fun atLeastPowRank(
            id: ByteArray,
            minPoW: Int,
            emptyBytes: Int,
        ): Boolean {
            for (index in 0 until emptyBytes) {
                if (id[index] != R8) return false
            }

            return calculatePowRankOf(id) >= minPoW
        }
    }
}
