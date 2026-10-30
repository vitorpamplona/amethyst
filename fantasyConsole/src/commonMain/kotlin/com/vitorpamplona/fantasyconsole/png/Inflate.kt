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
package com.vitorpamplona.fantasyconsole.png

/** DEFLATE (RFC 1951) and its zlib wrapper (RFC 1950), enough for PNG image data. */
object Inflate {
    class Corrupt(
        message: String,
    ) : Exception(message)

    /**
     * Inflates a zlib stream: a 2-byte header, the deflate data, then a checksum this ignores.
     * Throws [Corrupt] past [maxOutput] bytes, so a small stream cannot expand without bound.
     */
    fun zlib(
        data: ByteArray,
        maxOutput: Int = Int.MAX_VALUE,
    ): ByteArray {
        if (data.size < 2) throw Corrupt("truncated zlib header")
        val cmf = data[0].toInt() and 0xff
        if (cmf and 0x0f != 8) throw Corrupt("not deflate")
        if (data[1].toInt() and 0x20 != 0) throw Corrupt("preset dictionary not supported")
        return raw(data, 2, maxOutput)
    }

    fun raw(
        data: ByteArray,
        start: Int,
        maxOutput: Int = Int.MAX_VALUE,
    ): ByteArray {
        val r = BitReader(data, start)
        val out = Output(maxOutput)
        do {
            val last = r.bits(1) == 1
            when (r.bits(2)) {
                0 -> stored(r, out)
                1 -> block(r, out, FIXED_LITERALS, FIXED_DISTANCES)
                2 -> {
                    val (lit, dist) = dynamicTables(r)
                    block(r, out, lit, dist)
                }
                else -> throw Corrupt("bad block type")
            }
        } while (!last)
        return out.toByteArray()
    }

    private class Output(
        val max: Int,
    ) {
        var buf = ByteArray(minOf(1 shl 16, max))
        var size = 0

        fun put(b: Int) {
            if (size == buf.size) {
                if (size >= max) throw Corrupt("inflates past $max bytes")
                buf = buf.copyOf(minOf(buf.size.toLong() * 2, max.toLong()).toInt())
            }
            buf[size++] = b.toByte()
        }

        fun toByteArray() = buf.copyOf(size)
    }

    private class BitReader(
        val data: ByteArray,
        var pos: Int,
    ) {
        var bitBuf = 0
        var bitCount = 0

        fun bits(n: Int): Int {
            while (bitCount < n) {
                if (pos >= data.size) throw Corrupt("unexpected end of data")
                bitBuf = bitBuf or ((data[pos++].toInt() and 0xff) shl bitCount)
                bitCount += 8
            }
            val v = bitBuf and ((1 shl n) - 1)
            bitBuf = bitBuf ushr n
            bitCount -= n
            return v
        }

        fun alignToByte() {
            bitBuf = 0
            bitCount = 0
        }
    }

    /** A canonical Huffman code as counts per length and symbols in code order (as in zlib's "puff"). */
    private class Huffman(
        lengths: IntArray,
    ) {
        val counts = IntArray(16)
        val symbols = IntArray(lengths.size)

        init {
            for (l in lengths) counts[l]++
            counts[0] = 0
            val offsets = IntArray(16)
            for (i in 1 until 15) offsets[i + 1] = offsets[i] + counts[i]
            for (s in lengths.indices) if (lengths[s] != 0) symbols[offsets[lengths[s]]++] = s
        }

        fun decode(r: BitReader): Int {
            var code = 0
            var first = 0
            var index = 0
            for (len in 1..15) {
                code = code or r.bits(1)
                val count = counts[len]
                if (code - count < first) return symbols[index + (code - first)]
                index += count
                first += count
                first = first shl 1
                code = code shl 1
            }
            throw Corrupt("bad huffman code")
        }
    }

    private fun stored(
        r: BitReader,
        out: Output,
    ) {
        r.alignToByte()
        val d = r.data
        if (r.pos + 4 > d.size) throw Corrupt("truncated stored block")
        val len = (d[r.pos].toInt() and 0xff) or ((d[r.pos + 1].toInt() and 0xff) shl 8)
        r.pos += 4
        if (r.pos + len > d.size) throw Corrupt("truncated stored block")
        for (i in 0 until len) out.put(d[r.pos + i].toInt())
        r.pos += len
    }

    private val LENGTH_BASE = intArrayOf(3, 4, 5, 6, 7, 8, 9, 10, 11, 13, 15, 17, 19, 23, 27, 31, 35, 43, 51, 59, 67, 83, 99, 115, 131, 163, 195, 227, 258)
    private val LENGTH_EXTRA = intArrayOf(0, 0, 0, 0, 0, 0, 0, 0, 1, 1, 1, 1, 2, 2, 2, 2, 3, 3, 3, 3, 4, 4, 4, 4, 5, 5, 5, 5, 0)
    private val DIST_BASE =
        intArrayOf(1, 2, 3, 4, 5, 7, 9, 13, 17, 25, 33, 49, 65, 97, 129, 193, 257, 385, 513, 769, 1025, 1537, 2049, 3073, 4097, 6145, 8193, 12289, 16385, 24577)
    private val DIST_EXTRA = intArrayOf(0, 0, 0, 0, 1, 1, 2, 2, 3, 3, 4, 4, 5, 5, 6, 6, 7, 7, 8, 8, 9, 9, 10, 10, 11, 11, 12, 12, 13, 13)
    private val CODE_LENGTH_ORDER = intArrayOf(16, 17, 18, 0, 8, 7, 9, 6, 10, 5, 11, 4, 12, 3, 13, 2, 14, 1, 15)

    private val FIXED_LITERALS =
        Huffman(
            IntArray(288) {
                when {
                    it < 144 -> 8
                    it < 256 -> 9
                    it < 280 -> 7
                    else -> 8
                }
            },
        )
    private val FIXED_DISTANCES = Huffman(IntArray(30) { 5 })

    private fun block(
        r: BitReader,
        out: Output,
        literals: Huffman,
        distances: Huffman,
    ) {
        while (true) {
            val sym = literals.decode(r)
            when {
                sym < 256 -> out.put(sym)
                sym == 256 -> return
                else -> {
                    val li = sym - 257
                    if (li >= 29) throw Corrupt("bad length symbol")
                    val length = LENGTH_BASE[li] + r.bits(LENGTH_EXTRA[li])
                    val di = distances.decode(r)
                    if (di >= 30) throw Corrupt("bad distance symbol")
                    val dist = DIST_BASE[di] + r.bits(DIST_EXTRA[di])
                    if (dist > out.size) throw Corrupt("distance too far back")
                    for (i in 0 until length) out.put(out.buf[out.size - dist].toInt())
                }
            }
        }
    }

    private fun dynamicTables(r: BitReader): Pair<Huffman, Huffman> {
        val nLit = r.bits(5) + 257
        val nDist = r.bits(5) + 1
        val nCode = r.bits(4) + 4
        val codeLengths = IntArray(19)
        for (i in 0 until nCode) codeLengths[CODE_LENGTH_ORDER[i]] = r.bits(3)
        val codeHuffman = Huffman(codeLengths)
        val lengths = IntArray(nLit + nDist)
        var i = 0
        while (i < nLit + nDist) {
            val sym = codeHuffman.decode(r)
            when {
                sym < 16 -> lengths[i++] = sym
                else -> {
                    val (value, repeat) =
                        when (sym) {
                            16 -> {
                                if (i == 0) throw Corrupt("repeat with no previous length")
                                lengths[i - 1] to 3 + r.bits(2)
                            }
                            17 -> 0 to 3 + r.bits(3)
                            else -> 0 to 11 + r.bits(7)
                        }
                    if (i + repeat > lengths.size) throw Corrupt("too many lengths")
                    repeat(repeat) { lengths[i++] = value }
                }
            }
        }
        return Huffman(lengths.copyOfRange(0, nLit)) to Huffman(lengths.copyOfRange(nLit, nLit + nDist))
    }
}
