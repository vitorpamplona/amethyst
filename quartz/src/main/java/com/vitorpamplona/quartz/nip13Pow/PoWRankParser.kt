/**
 * Copyright (c) 2024 Vitor Pamplona
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
package com.vitorpamplona.quartz.nip13Pow

import com.vitorpamplona.quartz.nip01Core.HexKey

class PoWRankParser {
    companion object {
        @JvmStatic
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

        @JvmStatic
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
    }
}
