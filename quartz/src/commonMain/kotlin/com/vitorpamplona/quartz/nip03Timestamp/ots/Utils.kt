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

/**
 * Utility functions for (mostly) manipulating byte arrays.
 */
object Utils {
    /**
     * Compares two byte arrays.
     *
     * @param left the left byte array to compare with
     * @param right the right byte array to compare with
     * @return 0 if the arrays are identical, negative if left &lt; right, positive if left &gt; right
     */

    fun compare(
        left: ByteArray,
        right: ByteArray,
    ): Int {
        var i = 0
        var j = 0
        while (i < left.size && j < right.size) {
            val a = (left[i].toInt() and 0xff)
            val b = (right[j].toInt() and 0xff)

            if (a != b) {
                return a - b
            }
            i++
            j++
        }

        return left.size - right.size
    }
}
