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

import com.vitorpamplona.quartz.nip03Timestamp.ots.crypto.Pack.bigEndianToInt
import com.vitorpamplona.quartz.nip03Timestamp.ots.crypto.Pack.bigEndianToLong
import com.vitorpamplona.quartz.nip03Timestamp.ots.crypto.Pack.intToBigEndian
import com.vitorpamplona.quartz.nip03Timestamp.ots.crypto.Pack.longToBigEndian
import kotlin.math.max

/**
 * Base implementation of MD4 family style digest as outlined in
 * "Handbook of Applied Cryptography", pages 344 - 347.
 */
abstract class GeneralDigest :
    ExtendedDigest,
    Memoable {
    private val xBuf = ByteArray(4)
    private var xBufOff = 0

    private var byteCount: Long = 0

    /**
     * Standard constructor
     */
    protected constructor() {
        xBufOff = 0
    }

    /**
     * Copy constructor. We are using copy constructors in place
     * of the Object.clone() interface as this interface is not
     * supported by J2ME.
     *
     * @param t the GeneralDigest
     */
    protected constructor(t: GeneralDigest) {
        copyIn(t)
    }

    protected constructor(encodedState: ByteArray) {
        System.arraycopy(encodedState, 0, xBuf, 0, xBuf.size)
        xBufOff = bigEndianToInt(encodedState, 4)
        byteCount = bigEndianToLong(encodedState, 8)
    }

    protected fun copyIn(t: GeneralDigest) {
        System.arraycopy(t.xBuf, 0, xBuf, 0, t.xBuf.size)

        xBufOff = t.xBufOff
        byteCount = t.byteCount
    }

    override fun update(`in`: Byte) {
        xBuf[xBufOff++] = `in`

        if (xBufOff == xBuf.size) {
            processWord(xBuf, 0)
            xBufOff = 0
        }

        byteCount++
    }

    override fun update(
        `in`: ByteArray,
        inOff: Int,
        len: Int,
    ) {
        var len = len
        len = max(0, len)

        //
        // fill the current word
        //
        var i = 0

        if (xBufOff != 0) {
            while (i < len) {
                xBuf[xBufOff++] = `in`[inOff + i++]

                if (xBufOff == 4) {
                    processWord(xBuf, 0)
                    xBufOff = 0
                    break
                }
            }
        }

        //
        // process whole words.
        //
        val limit = ((len - i) and 3.inv()) + i

        while (i < limit) {
            processWord(`in`, inOff + i)
            i += 4
        }

        //
        // load in the remainder.
        //
        while (i < len) {
            xBuf[xBufOff++] = `in`[inOff + i++]
        }

        byteCount += len.toLong()
    }

    fun finish() {
        val bitLength = (byteCount shl 3)

        //
        // add the pad bytes.
        //
        update(128.toByte())

        while (xBufOff != 0) {
            update(0.toByte())
        }

        processLength(bitLength)
        processBlock()
    }

    override fun reset() {
        byteCount = 0

        xBufOff = 0

        for (i in xBuf.indices) {
            xBuf[i] = 0
        }
    }

    protected fun populateState(state: ByteArray) {
        System.arraycopy(xBuf, 0, state, 0, xBufOff)
        intToBigEndian(xBufOff, state, 4)
        longToBigEndian(byteCount, state, 8)
    }

    override fun getByteLength(): Int = BYTE_LENGTH

    protected abstract fun processWord(
        `in`: ByteArray,
        inOff: Int,
    )

    protected abstract fun processLength(bitLength: Long)

    protected abstract fun processBlock()

    companion object {
        private const val BYTE_LENGTH = 64
    }
}
