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
package com.vitorpamplona.amethyst.commons.service.uploads

/**
 * A marker holds a picked file's place in a composer's text until the post is sent, when the
 * file's URL replaces it. It is plain text, so undo, cut and paste, and moving it around all
 * work; the editor draws it as a card or a chip, never as these characters. It is never
 * published: the composer swaps every marker for a URL before signing, or removes it.
 *
 * Its brackets are two private-use characters a keyboard cannot type ([OPEN], [CLOSE]), so text a
 * person writes, `[[media:1]]` included, is never taken for a marker and removed on Post. The id
 * has at most 9 digits, so it always fits an `Int`.
 */
object MediaMarkers {
    const val OPEN = '\uE000'
    const val CLOSE = '\uE001'

    val REGEX = Regex("\uE000media:(\\d{1,9})\uE001")

    /** Cheap check before running [REGEX] on every keystroke. */
    const val PREFIX = "\uE000media:"

    fun of(id: Int) = "${OPEN}media:$id$CLOSE"

    /** The ids of every marker in [text], in reading order; a pasted copy of a marker repeats its id. */
    fun idsIn(text: CharSequence): List<Int> = if (!text.contains(PREFIX)) emptyList() else REGEX.findAll(text).map { it.groupValues[1].toInt() }.toList()

    /**
     * What [text] reads like with each marker swapped for [replacement] of its id, on a line of its
     * own; a marker it maps to null goes away together with the line break that followed it, so no
     * empty line is left.
     */
    fun replace(
        text: String,
        replacement: (Int) -> String?,
    ): String {
        if (!text.contains(PREFIX)) return text
        val out = StringBuilder(text.length)
        var last = 0
        REGEX.findAll(text).forEach { match ->
            out.append(text, last, match.range.first)
            val url = replacement(match.groupValues[1].toInt())
            last = match.range.last + 1
            if (url != null) {
                // A URL touching other text stops being one: keep it on a line of its own, even
                // after the user deleted the line break around the marker.
                if (out.isNotEmpty() && !out.last().isWhitespace()) out.append('\n')
                out.append(url)
                if (last < text.length && !text[last].isWhitespace()) out.append('\n')
            } else if (last < text.length && text[last] == '\n') {
                last++
            }
        }
        out.append(text, last, text.length)
        return out.toString()
    }

    /** The id of the marker that starts or ends at [cursor] in [text]: the one the cursor sits against. */
    fun markerAt(
        text: CharSequence,
        cursor: Int,
    ): Int? {
        if (!text.contains(PREFIX)) return null
        return REGEX
            .findAll(text)
            .firstOrNull { cursor == it.range.first || cursor == it.range.last + 1 }
            ?.groupValues
            ?.get(1)
            ?.toInt()
    }

    /** The id of the marker that starts at [rawStart] in [text]. */
    fun idAt(
        text: CharSequence,
        rawStart: Int,
    ): Int? =
        REGEX
            .find(text, rawStart)
            ?.takeIf { it.range.first == rawStart }
            ?.groupValues
            ?.get(1)
            ?.toInt()

    /** [text] without its markers: what a draft saves, since the picked files can't travel with it. */
    fun strip(text: String): String = replace(text) { null }

    /**
     * Where and what to insert so the marker sits on a line of its own at [cursor] in [text]: a line
     * break before it unless the cursor starts a line, and one after it unless one follows.
     */
    fun insertion(
        text: CharSequence,
        cursor: Int,
        id: Int,
    ): String {
        val before = if (cursor > 0 && text[cursor - 1] != '\n') "\n" else ""
        val after = if (cursor < text.length && text[cursor] == '\n') "" else "\n"
        return before + of(id) + after
    }
}
