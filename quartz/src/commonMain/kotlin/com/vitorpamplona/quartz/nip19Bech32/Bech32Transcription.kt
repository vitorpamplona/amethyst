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
package com.vitorpamplona.quartz.nip19Bech32

/**
 * Helpers for copying a bech32 key (usually an `nsec1…`) by hand — onto paper, a
 * steel plate, or into another device's keyboard — and for reading it back.
 *
 * A 32-byte key encodes to 63 chars (`nsec1` + 52 data + 6 checksum), which splits
 * evenly into [ROWS_PER_KEY] rows of [ROW_PATTERN] (5-4-4-4-4 = 21):
 *
 * ```
 * nsec1 ptuh w9cg 9ays zgxs
 * nr6zm 5akh s9ju w2r9 eujr
 * ha0r5 q6fz wghj hsl4 vqtl
 * ```
 *
 * Bech32 is case-insensitive as long as the whole string uses one case, so the
 * transcription may be shown in UPPERCASE: the alphabet has no `b`, `i` or `o`,
 * and `1` only appears as the separator, which removes most handwriting mix-ups.
 */
object Bech32Transcription {
    /** Group sizes of one row. The leading 5 keeps the `nsec1` prefix in one group. */
    val ROW_PATTERN = intArrayOf(5, 4, 4, 4, 4)

    const val ROWS_PER_KEY = 3

    /**
     * Splits [bech32] into rows of groups following [ROW_PATTERN]. The last row may be
     * shorter when the input isn't a multiple of the row length.
     */
    fun groups(bech32: String): List<List<String>> {
        val rows = mutableListOf<List<String>>()
        var i = 0
        while (i < bech32.length) {
            val row = mutableListOf<String>()
            for (size in ROW_PATTERN) {
                if (i >= bech32.length) break
                val end = minOf(i + size, bech32.length)
                row.add(bech32.substring(i, end))
                i = end
            }
            rows.add(row)
        }
        return rows
    }

    /**
     * Undoes a hand transcription: if [input], once whitespace and `-` separators are
     * removed, starts with a NIP-19 / NIP-49 prefix (any case), returns it compacted and
     * lowercased so the bech32 decoder and prefix checks accept it. Anything else is
     * returned trimmed but otherwise untouched (hex keys, mnemonics, NIP-05 addresses).
     *
     * Neither whitespace nor `-` is in the bech32 alphabet, so dropping them can't turn
     * one valid key into another.
     */
    fun normalize(input: String): String {
        val trimmed = input.trim()
        val compact = trimmed.filterNot { it.isWhitespace() || it == '-' }
        val lower = compact.lowercase()
        return if (TRANSCRIBABLE_PREFIXES.any { lower.startsWith(it) }) lower else trimmed
    }

    private val TRANSCRIBABLE_PREFIXES = arrayOf("nsec1", "npub1", "ncryptsec1", "nprofile1")
}
