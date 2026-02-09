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
package com.vitorpamplona.quartz.utils.mac

import javax.crypto.SecretKey

/**
 * Simple key spec that doesn't clone the key bytearray
 */
class FixedKey(
    val key: ByteArray,
    val algo: String,
) : SecretKey {
    override fun getAlgorithm() = algo

    override fun getEncoded() = key

    override fun getFormat() = "RAW"

    override fun hashCode() = key.contentHashCode() xor algo.hashCode()

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is FixedKey) return false

        if (!key.contentEquals(other.key)) return false

        // Old algorithm names
        val thatAlg = other.algorithm
        if (!(thatAlg.equals(this.algorithm, ignoreCase = true))) {
            if ((
                    !(thatAlg.equals("DESede", ignoreCase = true)) ||
                        !(this.algorithm.equals("TripleDES", ignoreCase = true))
                ) &&
                (
                    !(thatAlg.equals("TripleDES", ignoreCase = true)) ||
                        !(this.algorithm.equals("DESede", ignoreCase = true))
                )
            ) {
                return false
            }
        }
        return true
    }

    override fun destroy() = key.fill(0)

    override fun isDestroyed() = key.all { it.toInt() == 0 }
}
