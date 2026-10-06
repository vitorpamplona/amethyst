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
package com.vitorpamplona.quartz.nip64Chess

/**
 * The natural-language part of a PGN, for NIP-50 search.
 *
 * Move text (`1. e4 e5 2. Nf3`) is notation nobody types into a search box; what people search a
 * game by is who played it, where, and what the annotator said. That is the tag pairs naming
 * players, events, places and openings, plus the `{...}` move comments. Everything else — result,
 * date, ECO code, FEN, clock data — is a machine value and stays out.
 *
 * A hand-rolled scan rather than [PGNParser]: search only needs the text, and parsing the moves
 * and replaying every position per indexed event would be wasted work. Never throws.
 */
object PgnSearchText {
    /** Tag pairs whose values are names a person would search for. */
    val SEARCHABLE_HEADERS = setOf("Event", "Site", "White", "Black", "Annotator", "Opening", "Variation", "WhiteTeam", "BlackTeam")

    class Fields(
        /** Searchable tag-pair values, in the order the PGN lists them. */
        val headers: List<String>,
        /** `{...}` comments, trimmed, in move order. */
        val comments: List<String>,
    ) {
        fun all() = headers + comments
    }

    fun extract(pgn: String): Fields {
        if (pgn.isBlank()) return Fields(emptyList(), emptyList())

        val headers = mutableListOf<String>()
        val comments = mutableListOf<String>()

        var inComment = false
        val comment = StringBuilder()

        pgn.lineSequence().forEach { raw ->
            val line = raw.trim()
            if (!inComment && line.startsWith("[") && line.endsWith("]")) {
                headerValue(line)?.let { headers.add(it) }
                return@forEach
            }

            for (c in line) {
                if (inComment) {
                    if (c == '}') {
                        inComment = false
                        comment
                            .toString()
                            .trim()
                            .ifEmpty { null }
                            ?.let { comments.add(it) }
                        comment.clear()
                    } else {
                        comment.append(c)
                    }
                } else if (c == '{') {
                    inComment = true
                }
            }
            // A comment may span lines; keep the words apart.
            if (inComment) comment.append(' ')
        }

        return Fields(headers, comments)
    }

    // `[Key "Value"]` -> Value, when Key is searchable and the value is not a `?` placeholder.
    private fun headerValue(line: String): String? {
        val keyEnd = line.indexOfFirst { it.isWhitespace() }
        if (keyEnd <= 1) return null
        val key = line.substring(1, keyEnd)
        if (key !in SEARCHABLE_HEADERS) return null

        val open = line.indexOf('"', keyEnd)
        val close = line.lastIndexOf('"')
        if (open < 0 || close <= open) return null

        val value = line.substring(open + 1, close).replace("\\\"", "\"").trim()
        if (value.isEmpty() || value.all { it == '?' || it == '-' }) return null
        return value
    }
}
