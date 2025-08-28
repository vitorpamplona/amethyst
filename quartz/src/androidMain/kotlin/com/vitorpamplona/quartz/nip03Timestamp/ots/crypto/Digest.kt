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
package com.vitorpamplona.quartz.nip03Timestamp.ots.crypto

/**
 * Message digest interface
 */
interface Digest {
    /**
     * Return the algorithm name
     *
     * @return the algorithm name
     */
    fun getAlgorithmName(): String

    /**
     * Return the size, in bytes, of the digest produced by this message digest.
     *
     * @return the size, in bytes, of the digest produced by this message digest.
     */
    fun getDigestSize(): Int

    /**
     * Update the message digest with a single byte.
     *
     * @param in the input byte to be entered.
     */
    fun update(`in`: Byte)

    /**
     * Update the message digest with a block of bytes.
     *
     * @param in    the byte array containing the data.
     * @param inOff the offset into the byte array where the data starts.
     * @param len   the length of the data.
     */
    fun update(
        `in`: ByteArray,
        inOff: Int,
        len: Int,
    )

    /**
     * Close the digest, producing the final digest value. The doFinal
     * call also resets the digest.
     *
     * @param out    the array the digest is to be copied into.
     * @param outOff the offset into the out array the digest is to start at.
     * @return something
     * @see .reset
     */
    fun doFinal(
        out: ByteArray,
        outOff: Int,
    ): Int

    /**
     * Reset the digest back to it's initial state.
     */
    fun reset()
}
