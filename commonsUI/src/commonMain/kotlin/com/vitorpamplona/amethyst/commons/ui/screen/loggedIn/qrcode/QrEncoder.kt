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
package com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.qrcode

import kotlin.math.abs

/**
 * A QR code encoder (ISO/IEC 18004) in plain Kotlin, so every platform draws the same code from
 * the same rules. It follows ZXing's encoder step for step (mode, version, padding, block
 * interleaving, the mask chosen by the four penalty rules), so a code drawn here is module for
 * module the one Android drew with ZXing; QrEncoderZxingParityTest checks that on the JVM.
 *
 * Text is encoded in numeric, alphanumeric or byte mode, byte mode as UTF-8 behind an ECI header,
 * as ZXing does when given a character set. Kanji mode is not used (ZXing uses it only for
 * Shift_JIS).
 */
internal object QrEncoder {
    /** Error-correction levels, in the order of the tables below. [bits] is the format-info value. */
    enum class Level(
        val bits: Int,
    ) {
        L(1),
        M(0),
        Q(3),
        H(2),
    }

    private enum class Mode(
        val bits: Int,
        private val countBits: IntArray,
    ) {
        NUMERIC(0x1, intArrayOf(10, 12, 14)),
        ALPHANUMERIC(0x2, intArrayOf(9, 11, 13)),
        BYTE(0x4, intArrayOf(8, 16, 16)),
        ;

        fun characterCountBits(version: Int): Int =
            countBits[
                if (version <= 9) {
                    0
                } else if (version <= 26) {
                    1
                } else {
                    2
                },
            ]
    }

    private const val ECI_MODE_BITS = 0x7
    private const val UTF8_ECI = 26

    /**
     * The modules of [contents] at [level], row by row (true is dark), without the quiet zone, or
     * null when it does not fit in a version-40 symbol.
     */
    fun encode(
        contents: String,
        level: Level,
        /** A mask to use instead of the best one, for tests; -1 picks by penalty. */
        forcedMask: Int = -1,
    ): QrMatrix? {
        val mode = chooseMode(contents)
        val header = BitBuffer()
        val utf8 = if (mode == Mode.BYTE) contents.encodeToByteArray() else null
        if (mode == Mode.BYTE) {
            header.append(ECI_MODE_BITS, 4)
            header.append(UTF8_ECI, 8)
        }
        header.append(mode.bits, 4)

        val data = BitBuffer()
        when (mode) {
            Mode.NUMERIC -> appendNumeric(contents, data)
            Mode.ALPHANUMERIC -> appendAlphanumeric(contents, data)
            Mode.BYTE -> utf8!!.forEach { data.append(it.toInt() and 0xFF, 8) }
        }

        val provisional = chooseVersion(header.size + mode.characterCountBits(1) + data.size, level) ?: return null
        val version = chooseVersion(header.size + mode.characterCountBits(provisional) + data.size, level) ?: return null

        val bits = BitBuffer()
        bits.append(header)
        val letters = if (mode == Mode.BYTE) utf8!!.size else contents.length
        val countBits = mode.characterCountBits(version)
        if (letters >= (1 shl countBits)) return null
        bits.append(letters, countBits)
        bits.append(data)

        val totalCodewords = TOTAL_CODEWORDS[version - 1]
        val blocks = EC_BLOCKS[(version - 1) * 4 + level.ordinal]
        val dataCodewords = totalCodewords - EC_CODEWORDS_PER_BLOCK[(version - 1) * 4 + level.ordinal] * blocks
        if (!terminate(dataCodewords, bits)) return null
        val final = interleave(bits, totalCodewords, dataCodewords, blocks)

        val dimension = 17 + 4 * version
        val matrix = ByteMatrix(dimension)
        var bestMask = forcedMask
        var bestPenalty = Int.MAX_VALUE
        for (mask in 0 until 8) {
            if (forcedMask >= 0) break
            buildMatrix(final, level, version, mask, matrix)
            val penalty = maskPenalty(matrix)
            if (penalty < bestPenalty) {
                bestPenalty = penalty
                bestMask = mask
            }
        }
        buildMatrix(final, level, version, bestMask, matrix)
        return QrMatrix(dimension, dimension, BooleanArray(dimension * dimension) { matrix.cells[it] == ONE })
    }

    // ---- data -----------------------------------------------------------------------------

    private fun alphanumericCode(c: Char): Int =
        when (c) {
            in '0'..'9' -> c - '0'
            in 'A'..'Z' -> c - 'A' + 10
            ' ' -> 36
            '$' -> 37
            '%' -> 38
            '*' -> 39
            '+' -> 40
            '-' -> 41
            '.' -> 42
            '/' -> 43
            ':' -> 44
            else -> -1
        }

    private fun chooseMode(contents: String): Mode {
        var hasNumeric = false
        var hasAlphanumeric = false
        for (c in contents) {
            if (c in '0'..'9') {
                hasNumeric = true
            } else if (alphanumericCode(c) != -1) {
                hasAlphanumeric = true
            } else {
                return Mode.BYTE
            }
        }
        return when {
            hasAlphanumeric -> Mode.ALPHANUMERIC
            hasNumeric -> Mode.NUMERIC
            else -> Mode.BYTE
        }
    }

    private fun appendNumeric(
        contents: String,
        bits: BitBuffer,
    ) {
        var i = 0
        while (i < contents.length) {
            val d1 = contents[i] - '0'
            if (i + 2 < contents.length) {
                bits.append(d1 * 100 + (contents[i + 1] - '0') * 10 + (contents[i + 2] - '0'), 10)
                i += 3
            } else if (i + 1 < contents.length) {
                bits.append(d1 * 10 + (contents[i + 1] - '0'), 7)
                i += 2
            } else {
                bits.append(d1, 4)
                i++
            }
        }
    }

    private fun appendAlphanumeric(
        contents: String,
        bits: BitBuffer,
    ) {
        var i = 0
        while (i < contents.length) {
            val c1 = alphanumericCode(contents[i])
            if (i + 1 < contents.length) {
                bits.append(c1 * 45 + alphanumericCode(contents[i + 1]), 11)
                i += 2
            } else {
                bits.append(c1, 6)
                i++
            }
        }
    }

    /** The smallest version whose data codewords hold [bitsNeeded] bits at [level], or null. */
    private fun chooseVersion(
        bitsNeeded: Int,
        level: Level,
    ): Int? {
        val bytesNeeded = (bitsNeeded + 7) / 8
        for (version in 1..40) {
            val index = (version - 1) * 4 + level.ordinal
            val dataBytes = TOTAL_CODEWORDS[version - 1] - EC_CODEWORDS_PER_BLOCK[index] * EC_BLOCKS[index]
            if (dataBytes >= bytesNeeded) return version
        }
        return null
    }

    /** The terminator, byte alignment, then the 0xEC 0x11 pad codewords up to [dataCodewords]. */
    private fun terminate(
        dataCodewords: Int,
        bits: BitBuffer,
    ): Boolean {
        val capacity = dataCodewords * 8
        if (bits.size > capacity) return false
        var i = 0
        while (i < 4 && bits.size < capacity) {
            bits.append(0, 1)
            i++
        }
        val inLastByte = bits.size and 7
        if (inLastByte > 0) bits.append(0, 8 - inLastByte)
        val padding = dataCodewords - bits.size / 8
        for (p in 0 until padding) bits.append(if (p and 1 == 0) 0xEC else 0x11, 8)
        return bits.size == capacity
    }

    /** Splits the data into [blocks] Reed-Solomon blocks, adds their EC codewords, and interleaves. */
    private fun interleave(
        bits: BitBuffer,
        totalCodewords: Int,
        dataCodewords: Int,
        blocks: Int,
    ): BitBuffer {
        val inGroup2 = totalCodewords % blocks
        val inGroup1 = blocks - inGroup2
        val totalInGroup1 = totalCodewords / blocks
        val dataInGroup1 = dataCodewords / blocks
        val ecInGroup1 = totalInGroup1 - dataInGroup1
        val ecInGroup2 = (totalInGroup1 + 1) - (dataInGroup1 + 1)

        val dataBlocks = ArrayList<IntArray>(blocks)
        val ecBlocks = ArrayList<IntArray>(blocks)
        var offset = 0
        var maxData = 0
        var maxEc = 0
        for (block in 0 until blocks) {
            val size = if (block < inGroup1) dataInGroup1 else dataInGroup1 + 1
            val ecSize = if (block < inGroup1) ecInGroup1 else ecInGroup2
            val dataBytes = IntArray(size) { bits.byteAt(offset + it) }
            dataBlocks.add(dataBytes)
            ecBlocks.add(ReedSolomon.remainder(dataBytes, ecSize))
            maxData = maxOf(maxData, size)
            maxEc = maxOf(maxEc, ecSize)
            offset += size
        }
        val out = BitBuffer()
        for (i in 0 until maxData) dataBlocks.forEach { if (i < it.size) out.append(it[i], 8) }
        for (i in 0 until maxEc) ecBlocks.forEach { if (i < it.size) out.append(it[i], 8) }
        return out
    }

    // ---- matrix ---------------------------------------------------------------------------

    private const val EMPTY: Byte = -1
    private const val ZERO: Byte = 0
    private const val ONE: Byte = 1

    private class ByteMatrix(
        val size: Int,
    ) {
        val cells = ByteArray(size * size)

        operator fun get(
            x: Int,
            y: Int,
        ): Byte = cells[y * size + x]

        operator fun set(
            x: Int,
            y: Int,
            value: Byte,
        ) {
            cells[y * size + x] = value
        }

        fun set(
            x: Int,
            y: Int,
            dark: Boolean,
        ) = set(x, y, if (dark) ONE else ZERO)
    }

    private fun buildMatrix(
        data: BitBuffer,
        level: Level,
        version: Int,
        mask: Int,
        matrix: ByteMatrix,
    ) {
        matrix.cells.fill(EMPTY)
        embedBasicPatterns(version, matrix)
        embedTypeInfo(level, mask, matrix)
        embedVersionInfo(version, matrix)
        embedData(data, mask, matrix)
    }

    private fun embedBasicPatterns(
        version: Int,
        matrix: ByteMatrix,
    ) {
        val size = matrix.size
        // Finder patterns and their light separators.
        embedFinder(0, 0, matrix)
        embedFinder(size - 7, 0, matrix)
        embedFinder(0, size - 7, matrix)
        for (x in 0 until 8) {
            matrix[x, 7] = ZERO
            matrix[size - 8 + x, 7] = ZERO
            matrix[x, size - 8] = ZERO
        }
        for (y in 0 until 7) {
            matrix[7, y] = ZERO
            matrix[size - 8, y] = ZERO
            matrix[7, size - 7 + y] = ZERO
        }
        // The dark module beside the bottom-left finder.
        matrix[8, size - 8] = ONE
        // Alignment patterns, where they do not overlap a finder.
        if (version >= 2) {
            val centers = ALIGNMENT_CENTERS[version - 1]
            for (y in centers) {
                for (x in centers) {
                    if (matrix[x, y] == EMPTY) embedAlignment(x - 2, y - 2, matrix)
                }
            }
        }
        // Timing patterns.
        for (i in 8 until size - 8) {
            val dark = (i + 1) % 2 == 1
            if (matrix[i, 6] == EMPTY) matrix.set(i, 6, dark)
            if (matrix[6, i] == EMPTY) matrix.set(6, i, dark)
        }
    }

    private fun embedFinder(
        left: Int,
        top: Int,
        matrix: ByteMatrix,
    ) {
        for (y in 0 until 7) {
            for (x in 0 until 7) {
                val ring = maxOf(abs(x - 3), abs(y - 3))
                matrix.set(left + x, top + y, ring != 2)
            }
        }
    }

    private fun embedAlignment(
        left: Int,
        top: Int,
        matrix: ByteMatrix,
    ) {
        for (y in 0 until 5) {
            for (x in 0 until 5) {
                val ring = maxOf(abs(x - 2), abs(y - 2))
                matrix.set(left + x, top + y, ring != 1)
            }
        }
    }

    private val TYPE_INFO_X = intArrayOf(8, 8, 8, 8, 8, 8, 8, 8, 7, 5, 4, 3, 2, 1, 0)
    private val TYPE_INFO_Y = intArrayOf(0, 1, 2, 3, 4, 5, 7, 8, 8, 8, 8, 8, 8, 8, 8)

    private const val TYPE_INFO_POLY = 0x537
    private const val TYPE_INFO_MASK = 0x5412
    private const val VERSION_INFO_POLY = 0x1F25

    private fun embedTypeInfo(
        level: Level,
        mask: Int,
        matrix: ByteMatrix,
    ) {
        val typeInfo = (level.bits shl 3) or mask
        val bits = ((typeInfo shl 10) or bchCode(typeInfo, TYPE_INFO_POLY)) xor TYPE_INFO_MASK
        val size = matrix.size
        for (i in 0 until 15) {
            // Least significant bit first.
            val dark = (bits shr i) and 1 == 1
            matrix.set(TYPE_INFO_X[i], TYPE_INFO_Y[i], dark)
            if (i < 8) {
                matrix.set(size - i - 1, 8, dark)
            } else {
                matrix.set(8, size - 7 + (i - 8), dark)
            }
        }
    }

    private fun embedVersionInfo(
        version: Int,
        matrix: ByteMatrix,
    ) {
        if (version < 7) return
        val bits = (version shl 12) or bchCode(version, VERSION_INFO_POLY)
        val size = matrix.size
        // The 18 bits from the least significant up, three to a row.
        var index = 0
        for (i in 0 until 6) {
            for (j in 0 until 3) {
                val dark = (bits shr index) and 1 == 1
                index++
                matrix.set(i, size - 11 + j, dark)
                matrix.set(size - 11 + j, i, dark)
            }
        }
    }

    private fun msbSet(value: Int): Int = 32 - value.countLeadingZeroBits()

    private fun bchCode(
        value: Int,
        poly: Int,
    ): Int {
        val polyMsb = msbSet(poly)
        var v = value shl (polyMsb - 1)
        while (msbSet(v) >= polyMsb) v = v xor (poly shl (msbSet(v) - polyMsb))
        return v
    }

    private fun embedData(
        data: BitBuffer,
        mask: Int,
        matrix: ByteMatrix,
    ) {
        val size = matrix.size
        var bitIndex = 0
        var direction = -1
        var x = size - 1
        var y = size - 1
        while (x > 0) {
            // Skip the vertical timing pattern.
            if (x == 6) x -= 1
            while (y in 0 until size) {
                for (i in 0 until 2) {
                    val xx = x - i
                    if (matrix[xx, y] != EMPTY) continue
                    var dark =
                        if (bitIndex < data.size) {
                            data[bitIndex++]
                        } else {
                            false
                        }
                    if (dataMaskBit(mask, xx, y)) dark = !dark
                    matrix.set(xx, y, dark)
                }
                y += direction
            }
            direction = -direction
            y += direction
            x -= 2
        }
    }

    private fun dataMaskBit(
        mask: Int,
        x: Int,
        y: Int,
    ): Boolean {
        val intermediate =
            when (mask) {
                0 -> (y + x) and 1
                1 -> y and 1
                2 -> x % 3
                3 -> (y + x) % 3
                4 -> ((y / 2) + (x / 3)) and 1
                5 -> (y * x).let { (it and 1) + (it % 3) }
                6 -> (y * x).let { ((it and 1) + (it % 3)) and 1 }
                else -> ((y * x % 3) + ((y + x) and 1)) and 1
            }
        return intermediate == 0
    }

    // ---- mask penalty (the four rules of ISO/IEC 18004 8.8.2) -----------------------------

    private fun maskPenalty(matrix: ByteMatrix): Int = penaltyRule1(matrix, true) + penaltyRule1(matrix, false) + penaltyRule2(matrix) + penaltyRule3(matrix) + penaltyRule4(matrix)

    /** Runs of five or more same-coloured modules in a row or column. */
    private fun penaltyRule1(
        matrix: ByteMatrix,
        horizontal: Boolean,
    ): Int {
        val size = matrix.size
        var penalty = 0
        for (i in 0 until size) {
            var same = 0
            var previous: Byte = EMPTY
            for (j in 0 until size) {
                val bit = if (horizontal) matrix[j, i] else matrix[i, j]
                if (bit == previous) {
                    same++
                } else {
                    if (same >= 5) penalty += 3 + (same - 5)
                    same = 1
                    previous = bit
                }
            }
            if (same >= 5) penalty += 3 + (same - 5)
        }
        return penalty
    }

    /** 2x2 blocks of one colour. */
    private fun penaltyRule2(matrix: ByteMatrix): Int {
        val size = matrix.size
        var penalty = 0
        for (y in 0 until size - 1) {
            for (x in 0 until size - 1) {
                val value = matrix[x, y]
                if (value == matrix[x + 1, y] && value == matrix[x, y + 1] && value == matrix[x + 1, y + 1]) penalty++
            }
        }
        return 3 * penalty
    }

    /** 1:1:3:1:1 finder-like runs with four light modules on either side, inside the symbol. */
    private fun penaltyRule3(matrix: ByteMatrix): Int {
        val size = matrix.size
        var count = 0
        for (y in 0 until size) {
            for (x in 0 until size) {
                if (x + 6 < size &&
                    matrix[x, y] == ONE && matrix[x + 1, y] == ZERO && matrix[x + 2, y] == ONE && matrix[x + 3, y] == ONE &&
                    matrix[x + 4, y] == ONE && matrix[x + 5, y] == ZERO && matrix[x + 6, y] == ONE &&
                    (isLightRow(matrix, y, x - 4, x) || isLightRow(matrix, y, x + 7, x + 11))
                ) {
                    count++
                }
                if (y + 6 < size &&
                    matrix[x, y] == ONE && matrix[x, y + 1] == ZERO && matrix[x, y + 2] == ONE && matrix[x, y + 3] == ONE &&
                    matrix[x, y + 4] == ONE && matrix[x, y + 5] == ZERO && matrix[x, y + 6] == ONE &&
                    (isLightColumn(matrix, x, y - 4, y) || isLightColumn(matrix, x, y + 7, y + 11))
                ) {
                    count++
                }
            }
        }
        return count * 40
    }

    // A light run that would reach past the edge of the symbol does not count as light: so the
    // finder patterns, which sit at the edges, are not penalised for themselves (as ZXing does).
    private fun isLightRow(
        matrix: ByteMatrix,
        y: Int,
        from: Int,
        to: Int,
    ): Boolean {
        if (from < 0 || to > matrix.size) return false
        for (x in from until to) if (matrix[x, y] == ONE) return false
        return true
    }

    private fun isLightColumn(
        matrix: ByteMatrix,
        x: Int,
        from: Int,
        to: Int,
    ): Boolean {
        if (from < 0 || to > matrix.size) return false
        for (y in from until to) if (matrix[x, y] == ONE) return false
        return true
    }

    /** How far the share of dark modules is from half, in steps of 5%. */
    private fun penaltyRule4(matrix: ByteMatrix): Int {
        val total = matrix.cells.size
        val dark = matrix.cells.count { it == ONE }
        return abs(dark * 2 - total) * 10 / total * 10
    }

    // ---- bits and Reed-Solomon ------------------------------------------------------------

    private class BitBuffer {
        private var words = IntArray(32)
        var size = 0
            private set

        operator fun get(index: Int): Boolean = (words[index ushr 5] ushr (31 - (index and 31))) and 1 == 1

        /** The [count] low bits of [value], most significant first. */
        fun append(
            value: Int,
            count: Int,
        ) {
            for (i in count - 1 downTo 0) appendBit((value ushr i) and 1 == 1)
        }

        fun append(other: BitBuffer) {
            for (i in 0 until other.size) appendBit(other[i])
        }

        private fun appendBit(bit: Boolean) {
            if (size ushr 5 >= words.size) words = words.copyOf(words.size * 2)
            if (bit) words[size ushr 5] = words[size ushr 5] or (1 shl (31 - (size and 31)))
            size++
        }

        /** The byte that starts at bit [byteIndex] * 8. */
        fun byteAt(byteIndex: Int): Int {
            var value = 0
            for (i in 0 until 8) value = (value shl 1) or (if (this[byteIndex * 8 + i]) 1 else 0)
            return value
        }
    }

    /** Reed-Solomon over GF(256) with the QR polynomial x^8 + x^4 + x^3 + x^2 + 1, generator base 0. */
    private object ReedSolomon {
        private val exp = IntArray(512)
        private val log = IntArray(256)

        init {
            var x = 1
            for (i in 0 until 255) {
                exp[i] = x
                log[x] = i
                x = x shl 1
                if (x >= 256) x = x xor 0x11D
            }
            for (i in 255 until 512) exp[i] = exp[i - 255]
        }

        private fun multiply(
            a: Int,
            b: Int,
        ): Int = if (a == 0 || b == 0) 0 else exp[log[a] + log[b]]

        /** The generator polynomial of [degree], highest coefficient first. Cheap enough not to cache. */
        private fun generator(degree: Int): IntArray {
            var g = intArrayOf(1)
            for (i in 0 until degree) {
                val next = IntArray(g.size + 1)
                for (j in g.indices) {
                    next[j] = next[j] xor g[j]
                    next[j + 1] = next[j + 1] xor multiply(g[j], exp[i])
                }
                g = next
            }
            return g
        }

        /** The [ecSize] error-correction codewords of [data]. */
        fun remainder(
            data: IntArray,
            ecSize: Int,
        ): IntArray {
            val gen = generator(ecSize)
            val work = IntArray(data.size + ecSize)
            data.copyInto(work)
            for (i in data.indices) {
                val factor = work[i]
                if (factor != 0) {
                    for (j in gen.indices) work[i + j] = work[i + j] xor multiply(gen[j], factor)
                }
            }
            return work.copyOfRange(data.size, work.size)
        }
    }

    /** Codewords in a symbol of each version, 1 to 40. */
    private val TOTAL_CODEWORDS =
        intArrayOf(
            26,
            44,
            70,
            100,
            134,
            172,
            196,
            242,
            292,
            346,
            404,
            466,
            532,
            581,
            655,
            733,
            815,
            901,
            991,
            1085,
            1156,
            1258,
            1364,
            1474,
            1588,
            1706,
            1828,
            1921,
            2051,
            2185,
            2323,
            2465,
            2611,
            2761,
            2876,
            3034,
            3196,
            3362,
            3532,
            3706,
        )

    /** Error-correction codewords in each block, by version and then level (L, M, Q, H). */
    private val EC_CODEWORDS_PER_BLOCK =
        intArrayOf(
            7,
            10,
            13,
            17,
            10,
            16,
            22,
            28,
            15,
            26,
            18,
            22,
            20,
            18,
            26,
            16,
            26,
            24,
            18,
            22,
            18,
            16,
            24,
            28,
            20,
            18,
            18,
            26,
            24,
            22,
            22,
            26,
            30,
            22,
            20,
            24,
            18,
            26,
            24,
            28,
            20,
            30,
            28,
            24,
            24,
            22,
            26,
            28,
            26,
            22,
            24,
            22,
            30,
            24,
            20,
            24,
            22,
            24,
            30,
            24,
            24,
            28,
            24,
            30,
            28,
            28,
            28,
            28,
            30,
            26,
            28,
            28,
            28,
            26,
            26,
            26,
            28,
            26,
            30,
            28,
            28,
            26,
            28,
            30,
            28,
            28,
            30,
            24,
            30,
            28,
            30,
            30,
            30,
            28,
            30,
            30,
            26,
            28,
            30,
            30,
            28,
            28,
            28,
            30,
            30,
            28,
            30,
            30,
            30,
            28,
            30,
            30,
            30,
            28,
            30,
            30,
            30,
            28,
            30,
            30,
            30,
            28,
            30,
            30,
            30,
            28,
            30,
            30,
            30,
            28,
            30,
            30,
            30,
            28,
            30,
            30,
            30,
            28,
            30,
            30,
            30,
            28,
            30,
            30,
            30,
            28,
            30,
            30,
            30,
            28,
            30,
            30,
            30,
            28,
            30,
            30,
            30,
            28,
            30,
            30,
        )

    /** Reed-Solomon blocks, by version and then level (L, M, Q, H). */
    private val EC_BLOCKS =
        intArrayOf(
            1,
            1,
            1,
            1,
            1,
            1,
            1,
            1,
            1,
            1,
            2,
            2,
            1,
            2,
            2,
            4,
            1,
            2,
            4,
            4,
            2,
            4,
            4,
            4,
            2,
            4,
            6,
            5,
            2,
            4,
            6,
            6,
            2,
            5,
            8,
            8,
            4,
            5,
            8,
            8,
            4,
            5,
            8,
            11,
            4,
            8,
            10,
            11,
            4,
            9,
            12,
            16,
            4,
            9,
            16,
            16,
            6,
            10,
            12,
            18,
            6,
            10,
            17,
            16,
            6,
            11,
            16,
            19,
            6,
            13,
            18,
            21,
            7,
            14,
            21,
            25,
            8,
            16,
            20,
            25,
            8,
            17,
            23,
            25,
            9,
            17,
            23,
            34,
            9,
            18,
            25,
            30,
            10,
            20,
            27,
            32,
            12,
            21,
            29,
            35,
            12,
            23,
            34,
            37,
            12,
            25,
            34,
            40,
            13,
            26,
            35,
            42,
            14,
            28,
            38,
            45,
            15,
            29,
            40,
            48,
            16,
            31,
            43,
            51,
            17,
            33,
            45,
            54,
            18,
            35,
            48,
            57,
            19,
            37,
            51,
            60,
            19,
            38,
            53,
            63,
            20,
            40,
            56,
            66,
            21,
            43,
            59,
            70,
            22,
            45,
            62,
            74,
            24,
            47,
            65,
            77,
            25,
            49,
            68,
            81,
        )

    /** Alignment pattern centres of each version, 1 to 40. */
    private val ALIGNMENT_CENTERS =
        arrayOf(
            intArrayOf(),
            intArrayOf(6, 18),
            intArrayOf(6, 22),
            intArrayOf(6, 26),
            intArrayOf(6, 30),
            intArrayOf(6, 34),
            intArrayOf(6, 22, 38),
            intArrayOf(6, 24, 42),
            intArrayOf(6, 26, 46),
            intArrayOf(6, 28, 50),
            intArrayOf(6, 30, 54),
            intArrayOf(6, 32, 58),
            intArrayOf(6, 34, 62),
            intArrayOf(6, 26, 46, 66),
            intArrayOf(6, 26, 48, 70),
            intArrayOf(6, 26, 50, 74),
            intArrayOf(6, 30, 54, 78),
            intArrayOf(6, 30, 56, 82),
            intArrayOf(6, 30, 58, 86),
            intArrayOf(6, 34, 62, 90),
            intArrayOf(6, 28, 50, 72, 94),
            intArrayOf(6, 26, 50, 74, 98),
            intArrayOf(6, 30, 54, 78, 102),
            intArrayOf(6, 28, 54, 80, 106),
            intArrayOf(6, 32, 58, 84, 110),
            intArrayOf(6, 30, 58, 86, 114),
            intArrayOf(6, 34, 62, 90, 118),
            intArrayOf(6, 26, 50, 74, 98, 122),
            intArrayOf(6, 30, 54, 78, 102, 126),
            intArrayOf(6, 26, 52, 78, 104, 130),
            intArrayOf(6, 30, 56, 82, 108, 134),
            intArrayOf(6, 34, 60, 86, 112, 138),
            intArrayOf(6, 30, 58, 86, 114, 142),
            intArrayOf(6, 34, 62, 90, 118, 146),
            intArrayOf(6, 30, 54, 78, 102, 126, 150),
            intArrayOf(6, 24, 50, 76, 102, 128, 154),
            intArrayOf(6, 28, 54, 80, 106, 132, 158),
            intArrayOf(6, 32, 58, 84, 110, 136, 162),
            intArrayOf(6, 26, 54, 82, 110, 138, 166),
            intArrayOf(6, 30, 58, 86, 114, 142, 170),
        )
}
