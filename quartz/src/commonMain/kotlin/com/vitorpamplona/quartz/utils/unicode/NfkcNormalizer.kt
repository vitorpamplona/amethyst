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
package com.vitorpamplona.quartz.utils.unicode

/**
 * Pure-Kotlin Unicode NFKC (UAX #15) for targets whose platform has no
 * normalizer: Linux native has no ICU. JVM/Android use `java.text.Normalizer`
 * and Apple uses `NSString`.
 *
 * Runs the standard algorithm (full compatibility decomposition, canonical
 * ordering, canonical composition) over the [NfkcData] tables generated from
 * the Unicode Character Database. Hangul syllables are handled algorithmically.
 * The tables are parsed on first use of non-ASCII input.
 */
internal object NfkcNormalizer {
    private const val S_BASE = 0xAC00
    private const val L_BASE = 0x1100
    private const val V_BASE = 0x1161
    private const val T_BASE = 0x11A7
    private const val L_COUNT = 19
    private const val V_COUNT = 21
    private const val T_COUNT = 28
    private const val N_COUNT = V_COUNT * T_COUNT
    private const val S_COUNT = L_COUNT * N_COUNT

    // Every code point below U+00A0 is its own NFKC form.
    private const val FIRST_CHANGING_CHAR = ' '

    private val combiningClasses: Map<Int, Int> by lazy {
        val map = HashMap<Int, Int>()
        forEachEntry(NfkcData.CCC) { entry ->
            val dash = entry.indexOf('-')
            val colon = entry.indexOf(':')
            val combiningClass = entry.substring(colon + 1).toInt(16)
            for (cp in entry.substring(0, dash).toInt(16)..entry.substring(dash + 1, colon).toInt(16)) {
                map[cp] = combiningClass
            }
        }
        map
    }

    private val decompositions: Map<Int, IntArray> by lazy {
        val map = HashMap<Int, IntArray>()
        forEachEntry(NfkcData.DECOMP) { entry ->
            val colon = entry.indexOf(':')
            map[entry.substring(0, colon).toInt(16)] =
                entry
                    .substring(colon + 1)
                    .split(' ')
                    .map { it.toInt(16) }
                    .toIntArray()
        }
        map
    }

    private val compositions: Map<Long, Int> by lazy {
        val map = HashMap<Long, Int>()
        forEachEntry(NfkcData.COMPOSE) { entry ->
            val parts = entry.split(' ')
            map[pairKey(parts[0].toInt(16), parts[1].toInt(16))] = parts[2].toInt(16)
        }
        map
    }

    fun normalize(input: String): String {
        if (input.all { it < FIRST_CHANGING_CHAR }) return input

        val buffer = decompose(input)
        canonicalOrder(buffer)
        val length = compose(buffer)

        val out = StringBuilder(length)
        for (i in 0 until length) out.appendCodePoint(buffer.values[i])
        return out.toString()
    }

    private fun decompose(input: String): CodePointBuffer {
        val out = CodePointBuffer(input.length + 8)
        var i = 0
        while (i < input.length) {
            val c = input[i]
            val cp =
                if (c.isHighSurrogate() && i + 1 < input.length && input[i + 1].isLowSurrogate()) {
                    i++
                    0x10000 + ((c.code - 0xD800) shl 10) + (input[i].code - 0xDC00)
                } else {
                    c.code
                }
            i++

            if (cp >= S_BASE && cp < S_BASE + S_COUNT) {
                val index = cp - S_BASE
                out.add(L_BASE + index / N_COUNT)
                out.add(V_BASE + (index % N_COUNT) / T_COUNT)
                val trailing = index % T_COUNT
                if (trailing != 0) out.add(T_BASE + trailing)
            } else {
                val mapping = decompositions[cp]
                if (mapping != null) {
                    for (m in mapping) out.add(m)
                } else {
                    out.add(cp)
                }
            }
        }
        return out
    }

    /** Stable-sorts each run of non-starters by combining class (UAX #15 canonical ordering). */
    private fun canonicalOrder(buffer: CodePointBuffer) {
        val values = buffer.values
        for (i in 1 until buffer.size) {
            val cp = values[i]
            val cc = combiningClass(cp)
            if (cc == 0) continue
            var j = i
            while (j > 0 && combiningClass(values[j - 1]) > cc) {
                values[j] = values[j - 1]
                j--
            }
            values[j] = cp
        }
    }

    /** Canonical composition in place (the UAX #15 reference algorithm). Returns the new length. */
    private fun compose(buffer: CodePointBuffer): Int {
        val values = buffer.values
        if (buffer.size == 0) return 0

        var starterPos = 0
        var starter = values[0]
        // A leading non-starter has no starter to combine with: 256 blocks it.
        var lastClass = if (combiningClass(starter) == 0) 0 else 256
        var writePos = 1

        for (readPos in 1 until buffer.size) {
            val cp = values[readPos]
            val cc = combiningClass(cp)
            val composite = composePair(starter, cp)
            if (composite >= 0 && (lastClass < cc || lastClass == 0)) {
                values[starterPos] = composite
                starter = composite
            } else {
                if (cc == 0) {
                    starterPos = writePos
                    starter = cp
                }
                lastClass = cc
                values[writePos++] = cp
            }
        }
        return writePos
    }

    private fun composePair(
        first: Int,
        second: Int,
    ): Int {
        if (first >= L_BASE && first < L_BASE + L_COUNT && second >= V_BASE && second < V_BASE + V_COUNT) {
            return S_BASE + ((first - L_BASE) * V_COUNT + (second - V_BASE)) * T_COUNT
        }
        if (first >= S_BASE && first < S_BASE + S_COUNT && (first - S_BASE) % T_COUNT == 0 &&
            second > T_BASE && second < T_BASE + T_COUNT
        ) {
            return first + (second - T_BASE)
        }
        return compositions[pairKey(first, second)] ?: -1
    }

    private fun combiningClass(cp: Int): Int = if (cp < 0x300) 0 else combiningClasses[cp] ?: 0

    private fun pairKey(
        first: Int,
        second: Int,
    ): Long = (first.toLong() shl 21) or second.toLong()

    private inline fun forEachEntry(
        chunks: Array<String>,
        action: (String) -> Unit,
    ) {
        for (chunk in chunks) {
            for (entry in chunk.split(';')) action(entry)
        }
    }

    private fun StringBuilder.appendCodePoint(cp: Int) {
        if (cp < 0x10000) {
            append(cp.toChar())
        } else {
            val offset = cp - 0x10000
            append((0xD800 + (offset shr 10)).toChar())
            append((0xDC00 + (offset and 0x3FF)).toChar())
        }
    }

    private class CodePointBuffer(
        capacity: Int,
    ) {
        var values = IntArray(capacity)
        var size = 0

        fun add(cp: Int) {
            if (size == values.size) values = values.copyOf(size * 2)
            values[size++] = cp
        }
    }
}
