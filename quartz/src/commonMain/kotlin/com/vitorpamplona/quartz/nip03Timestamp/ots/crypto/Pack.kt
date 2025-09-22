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
 * Utility methods for converting byte arrays into ints and longs, and back again.
 */
object Pack {
    fun bigEndianToShort(
        bs: ByteArray,
        off: Int,
    ): Short {
        var off = off
        var n = (bs[off].toInt() and 0xff) shl 8
        n = n or (bs[++off].toInt() and 0xff)
        return n.toShort()
    }

    fun bigEndianToInt(
        bs: ByteArray,
        off: Int,
    ): Int {
        var off = off
        var n = bs[off].toInt() shl 24
        n = n or ((bs[++off].toInt() and 0xff) shl 16)
        n = n or ((bs[++off].toInt() and 0xff) shl 8)
        n = n or (bs[++off].toInt() and 0xff)
        return n
    }

    fun bigEndianToInt(
        bs: ByteArray,
        off: Int,
        ns: IntArray,
    ) {
        var off = off
        for (i in ns.indices) {
            ns[i] = bigEndianToInt(bs, off)
            off += 4
        }
    }

    fun intToBigEndian(n: Int): ByteArray {
        val bs = ByteArray(4)
        intToBigEndian(n, bs, 0)
        return bs
    }

    fun intToBigEndian(
        n: Int,
        bs: ByteArray,
        off: Int,
    ) {
        var off = off
        bs[off] = (n ushr 24).toByte()
        bs[++off] = (n ushr 16).toByte()
        bs[++off] = (n ushr 8).toByte()
        bs[++off] = (n).toByte()
    }

    fun intToBigEndian(ns: IntArray): ByteArray {
        val bs = ByteArray(4 * ns.size)
        intToBigEndian(ns, bs, 0)
        return bs
    }

    fun intToBigEndian(
        ns: IntArray,
        bs: ByteArray,
        off: Int,
    ) {
        var off = off
        for (i in ns.indices) {
            intToBigEndian(ns[i], bs, off)
            off += 4
        }
    }

    fun bigEndianToLong(
        bs: ByteArray,
        off: Int,
    ): Long {
        val hi = bigEndianToInt(bs, off)
        val lo = bigEndianToInt(bs, off + 4)
        return ((hi.toLong() and 0xffffffffL) shl 32) or (lo.toLong() and 0xffffffffL)
    }

    fun bigEndianToLong(
        bs: ByteArray,
        off: Int,
        ns: LongArray,
    ) {
        var off = off
        for (i in ns.indices) {
            ns[i] = bigEndianToLong(bs, off)
            off += 8
        }
    }

    fun longToBigEndian(n: Long): ByteArray {
        val bs = ByteArray(8)
        longToBigEndian(n, bs, 0)
        return bs
    }

    fun longToBigEndian(
        n: Long,
        bs: ByteArray,
        off: Int,
    ) {
        intToBigEndian((n ushr 32).toInt(), bs, off)
        intToBigEndian((n and 0xffffffffL).toInt(), bs, off + 4)
    }

    fun longToBigEndian(ns: LongArray): ByteArray {
        val bs = ByteArray(8 * ns.size)
        longToBigEndian(ns, bs, 0)
        return bs
    }

    fun longToBigEndian(
        ns: LongArray,
        bs: ByteArray,
        off: Int,
    ) {
        var off = off
        for (i in ns.indices) {
            longToBigEndian(ns[i], bs, off)
            off += 8
        }
    }

    fun littleEndianToShort(
        bs: ByteArray,
        off: Int,
    ): Short {
        var off = off
        var n = bs[off].toInt() and 0xff
        n = n or ((bs[++off].toInt() and 0xff) shl 8)
        return n.toShort()
    }

    fun littleEndianToInt(
        bs: ByteArray,
        off: Int,
    ): Int {
        var off = off
        var n = bs[off].toInt() and 0xff
        n = n or ((bs[++off].toInt() and 0xff) shl 8)
        n = n or ((bs[++off].toInt() and 0xff) shl 16)
        n = n or (bs[++off].toInt() shl 24)
        return n
    }

    fun littleEndianToInt(
        bs: ByteArray,
        off: Int,
        ns: IntArray,
    ) {
        var off = off
        for (i in ns.indices) {
            ns[i] = littleEndianToInt(bs, off)
            off += 4
        }
    }

    fun littleEndianToInt(
        bs: ByteArray,
        bOff: Int,
        ns: IntArray,
        nOff: Int,
        count: Int,
    ) {
        var bOff = bOff
        for (i in 0..<count) {
            ns[nOff + i] = littleEndianToInt(bs, bOff)
            bOff += 4
        }
    }

    fun littleEndianToInt(
        bs: ByteArray,
        off: Int,
        count: Int,
    ): IntArray {
        var off = off
        val ns = IntArray(count)
        for (i in ns.indices) {
            ns[i] = littleEndianToInt(bs, off)
            off += 4
        }
        return ns
    }

    fun shortToLittleEndian(n: Short): ByteArray {
        val bs = ByteArray(2)
        shortToLittleEndian(n, bs, 0)
        return bs
    }

    fun shortToLittleEndian(
        n: Short,
        bs: ByteArray,
        off: Int,
    ) {
        var off = off
        bs[off] = (n).toByte()
        bs[++off] = (n.toInt() ushr 8).toByte()
    }

    fun intToLittleEndian(n: Int): ByteArray {
        val bs = ByteArray(4)
        intToLittleEndian(n, bs, 0)
        return bs
    }

    fun intToLittleEndian(
        n: Int,
        bs: ByteArray,
        off: Int,
    ) {
        var off = off
        bs[off] = (n).toByte()
        bs[++off] = (n ushr 8).toByte()
        bs[++off] = (n ushr 16).toByte()
        bs[++off] = (n ushr 24).toByte()
    }

    fun intToLittleEndian(ns: IntArray): ByteArray {
        val bs = ByteArray(4 * ns.size)
        intToLittleEndian(ns, bs, 0)
        return bs
    }

    fun intToLittleEndian(
        ns: IntArray,
        bs: ByteArray,
        off: Int,
    ) {
        var off = off
        for (i in ns.indices) {
            intToLittleEndian(ns[i], bs, off)
            off += 4
        }
    }

    fun littleEndianToLong(
        bs: ByteArray,
        off: Int,
    ): Long {
        val lo = littleEndianToInt(bs, off)
        val hi = littleEndianToInt(bs, off + 4)
        return ((hi.toLong() and 0xffffffffL) shl 32) or (lo.toLong() and 0xffffffffL)
    }

    fun littleEndianToLong(
        bs: ByteArray,
        off: Int,
        ns: LongArray,
    ) {
        var off = off
        for (i in ns.indices) {
            ns[i] = littleEndianToLong(bs, off)
            off += 8
        }
    }

    fun longToLittleEndian(n: Long): ByteArray {
        val bs = ByteArray(8)
        longToLittleEndian(n, bs, 0)
        return bs
    }

    fun longToLittleEndian(
        n: Long,
        bs: ByteArray,
        off: Int,
    ) {
        intToLittleEndian((n and 0xffffffffL).toInt(), bs, off)
        intToLittleEndian((n ushr 32).toInt(), bs, off + 4)
    }

    fun longToLittleEndian(ns: LongArray): ByteArray {
        val bs = ByteArray(8 * ns.size)
        longToLittleEndian(ns, bs, 0)
        return bs
    }

    fun longToLittleEndian(
        ns: LongArray,
        bs: ByteArray,
        off: Int,
    ) {
        var off = off
        for (i in ns.indices) {
            longToLittleEndian(ns[i], bs, off)
            off += 8
        }
    }
}
