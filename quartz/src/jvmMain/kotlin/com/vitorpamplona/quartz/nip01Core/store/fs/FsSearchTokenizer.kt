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
package com.vitorpamplona.quartz.nip01Core.store.fs

/**
 * NIP-50 search tokenizer for the file-backed store.
 *
 * Approximates SQLite FTS5's `unicode61` default tokenizer:
 *  - Splits on any non letter-or-digit character (Unicode aware via
 *    `Char.isLetterOrDigit`).
 *  - Lowercases each emitted token.
 *  - Drops empty tokens.
 *  - Truncates pathologically long tokens to keep filenames within
 *    typical filesystem limits (255 bytes on ext4 / NTFS / APFS).
 *
 * The same function is called for indexing and querying, so any
 * behaviour drift cancels out.
 */
internal object FsSearchTokenizer {
    /** Returns the unique set of search tokens contained in [content]. */
    fun tokenize(content: String): Set<String> {
        if (content.isEmpty()) return emptySet()
        val out = HashSet<String>()
        val sb = StringBuilder()
        for (ch in content) {
            if (ch.isLetterOrDigit()) {
                sb.append(ch.lowercaseChar())
            } else if (sb.isNotEmpty()) {
                emit(sb, out)
            }
        }
        if (sb.isNotEmpty()) emit(sb, out)
        return out
    }

    private fun emit(
        sb: StringBuilder,
        out: HashSet<String>,
    ) {
        val token = if (sb.length <= MAX_TOKEN_LEN) sb.toString() else sb.substring(0, MAX_TOKEN_LEN)
        out.add(token)
        sb.setLength(0)
    }

    /**
     * Cap on token length. UTF-8 of an all-ASCII string at this length is
     * 200 bytes — well under the 255-byte path-component limit common to
     * ext4, APFS and NTFS even when wrapped in the `<ts>-<id>` filename
     * pattern (76 extra bytes).
     */
    private const val MAX_TOKEN_LEN = 100
}
