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

import java.security.NoSuchAlgorithmException
import java.security.SecureRandom

/**
 * Utility functions for (mostly) manipulating byte arrays.
 */
object Utils {
    /**
     * Fills a byte array with the given byte value.
     *
     * @param array the array to fill
     * @param value the value to fill the array with
     */
    @JvmStatic
    fun arrayFill(
        array: ByteArray,
        value: Byte,
    ) {
        for (i in array.indices) {
            array[i] = value
        }
    }

    /**
     * Returns the first value that is not null. If all objects are null, then it returns null.
     *
     * @param items the array of Ts
     * @param <T> This describes my type parameter
     * @return the first value that is not null. If all objects are null, then it returns null.
     </T> */
    @Deprecated("Not used by Java OpenTimestamps itself, and doesn't offer much useful functionality.")
    fun <T> coalesce(vararg items: T?): T? {
        for (i in items) {
            if (i != null) {
                return i
            }
        }

        return null
    }

    /**
     * Returns a copy of the byte array argument, or null if the byte array argument is null.
     *
     * @param data the array of bytes to copy
     * @return the copied byte array
     */
    @JvmStatic
    fun arraysCopy(data: ByteArray): ByteArray {
        val copy = ByteArray(data.size)
        System.arraycopy(data, 0, copy, 0, data.size)

        return copy
    }

    /**
     * Returns a byte array which is the result of concatenating the two passed in byte arrays.
     * None of the passed in arrays may be null.
     *
     * @param array1 the first array of bytes
     * @param array2 the second array of bytes
     * @return a copy of array1 and array2 concatenated together
     */
    @JvmStatic
    fun arraysConcat(
        array1: ByteArray,
        array2: ByteArray,
    ): ByteArray {
        val array1and2 = ByteArray(array1.size + array2.size)
        System.arraycopy(array1, 0, array1and2, 0, array1.size)
        System.arraycopy(array2, 0, array1and2, array1.size, array2.size)

        return array1and2
    }

    /**
     * Returns a given length array of random bytes.
     *
     * @param length the requested length of the byte array
     * @return a given length array of random bytes
     * @throws NoSuchAlgorithmException for Java 8 implementations
     */
    @JvmStatic
    @Throws(NoSuchAlgorithmException::class)
    fun randBytes(length: Int): ByteArray {
        // Java 6 & 7:
        val random = SecureRandom()
        val bytes = ByteArray(length)
        random.nextBytes(bytes)

        // Java 8 (even more secure):
        // SecureRandom.getInstanceStrong().nextBytes(bytes);
        return bytes
    }

    /**
     * Returns a reversed copy of the passed in byte array.
     *
     * @param array the byte array to reverse
     * @return a copy of the byte array, reversed
     */
    @JvmStatic
    fun arrayReverse(array: ByteArray): ByteArray {
        val reversedArray = ByteArray(array.size)

        var i = array.size - 1
        var j = 0
        while (i >= 0) {
            reversedArray[j] = array[i]
            i--
            j++
        }

        return reversedArray
    }

    /**
     * Compares two byte arrays.
     *
     * @param left the left byte array to compare with
     * @param right the right byte array to compare with
     * @return 0 if the arrays are identical, negative if left &lt; right, positive if left &gt; right
     */
    @JvmStatic
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
