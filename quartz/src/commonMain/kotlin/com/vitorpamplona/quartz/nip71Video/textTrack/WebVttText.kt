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
package com.vitorpamplona.quartz.nip71Video.textTrack

import com.vitorpamplona.quartz.nip50Search.IndexableFieldVisitor

/**
 * The words a WebVTT document says: every cue's payload lines with their markup removed, and
 * nothing else.
 *
 * Search is the reason this exists. A text track's `content` is the whole document, so indexing
 * it raw puts `WEBVTT`, every `00:01:02.500 --> 00:01:04.000` timing line, cue settings
 * (`align:start`), cue ids, `NOTE` and `STYLE` blocks, and inline tags (`<v Roger>`, `<i>`,
 * `<00:00:01.000>`) into the index beside the dialogue. A query for "00" or "align" then
 * matches every caption ever published, and the terms that matter are diluted by markup.
 *
 * ## What counts as a cue line
 *
 * One pass over the lines, no model built. A line containing `-->` opens a cue (it is the
 * timing line; the optional identifier line before it is skipped because nothing is open yet),
 * and every non-blank line after it until the next blank line is payload. Everything outside a
 * cue — the `WEBVTT` header, `NOTE`, `STYLE`, `REGION` — is skipped by the same rule, since none
 * of it may contain `-->`. SRT happens to fit the same shape (a numeric id, a timing line, the
 * text), so a publisher that wraps SRT instead of WebVTT is read correctly too.
 *
 * A document with no timing line at all is not a caption file, so it is indexed as written
 * rather than discarded: [hasCues] is the switch, and callers pass such content through
 * unchanged.
 *
 * ## Markup
 *
 * Inline tags are removed, not their contents: `<i>really</i>` is `really`. Voice spans lose the
 * speaker name with the tag (`<v Roger>Hello` is `Hello`): the name is an annotation, not
 * something said. The HTML character references WebVTT allows are decoded — the named ones the
 * spec lists, plus numeric ones — so `Tom &amp; Jerry` is indexed as `Tom & Jerry`. An unknown
 * reference is kept as written.
 */
object WebVttText {
    private const val TIMING_ARROW = "-->"

    /** Whether [document] has at least one cue. Without one it is not a caption file. */
    fun hasCues(document: String) = document.contains(TIMING_ARROW)

    /**
     * The cue text of [document], one payload line per line. Callers should check [hasCues]
     * first; a document without cues yields an empty string here.
     */
    fun cueText(document: String): String {
        val out = StringBuilder(document.length)
        forEachCueLine(document) { line ->
            if (out.isNotEmpty()) out.append('\n')
            out.append(line)
            true
        }
        return out.toString()
    }

    /**
     * Hands each cleaned, non-empty payload line of [document] to [visitor] in order. Joining
     * them with `"\n"` gives exactly [cueText]. Returns false when the visitor stopped the walk.
     *
     * Lines without markup are passed as substrings; only a line carrying a tag or a character
     * reference is rebuilt.
     */
    fun forEachCueLine(
        document: String,
        visitor: IndexableFieldVisitor,
    ): Boolean {
        var inCue = false
        var start = 0
        val length = document.length
        while (start <= length) {
            var end = start
            while (end < length && document[end] != '\n' && document[end] != '\r') end++

            if (isBlank(document, start, end)) {
                inCue = false
            } else if (!inCue) {
                // Header, NOTE / STYLE / REGION blocks and cue identifiers all end up here and
                // are skipped; only a timing line opens a cue.
                if (hasArrow(document, start, end)) inCue = true
            } else {
                val line = clean(document, start, end)
                if (line != null && !visitor.visit(line)) return false
            }

            if (end >= length) break
            // A CRLF pair is one line break.
            start = if (document[end] == '\r' && end + 1 < length && document[end + 1] == '\n') end + 2 else end + 1
        }
        return true
    }

    /** Bounded to the line: an unbounded indexOf would rescan up to the next cue from every skipped line. */
    private fun hasArrow(
        s: String,
        start: Int,
        end: Int,
    ): Boolean {
        for (i in start until end - 2) {
            if (s[i] == '-' && s[i + 1] == '-' && s[i + 2] == '>') return true
        }
        return false
    }

    private fun isBlank(
        s: String,
        start: Int,
        end: Int,
    ): Boolean {
        for (i in start until end) if (!s[i].isWhitespace()) return false
        return true
    }

    /** The payload line in `[start, end)` without tags, references decoded and trimmed; null when nothing is left. */
    private fun clean(
        s: String,
        start: Int,
        end: Int,
    ): String? {
        var markup = false
        for (i in start until end) {
            val c = s[i]
            if (c == '<' || c == '&') {
                markup = true
                break
            }
        }
        if (!markup) return s.substring(start, end).trim().ifEmpty { null }

        val out = StringBuilder(end - start)
        var i = start
        while (i < end) {
            val c = s[i]
            when (c) {
                '<' -> {
                    val close = s.indexOf('>', i + 1)
                    // An unterminated tag runs to the end of the line, as the WebVTT parser reads it.
                    i = if (close < 0 || close >= end) end else close + 1
                }

                '&' -> {
                    val semi = s.indexOf(';', i + 1)
                    val decoded = if (semi in (i + 1) until end) decodeReference(s, i + 1, semi) else null
                    if (decoded == null) {
                        out.append(c)
                        i++
                    } else {
                        appendCodePoint(out, decoded)
                        i = semi + 1
                    }
                }

                else -> {
                    out.append(c)
                    i++
                }
            }
        }
        return out.toString().trim().ifEmpty { null }
    }

    /** The code point named by the reference body in `[start, end)` (between `&` and `;`), or null. */
    private fun decodeReference(
        s: String,
        start: Int,
        end: Int,
    ): Int? {
        if (end - start > 10) return null
        if (s[start] == '#') {
            val hex = start + 1 < end && (s[start + 1] == 'x' || s[start + 1] == 'X')
            val digits = s.substring(if (hex) start + 2 else start + 1, end)
            val code = digits.toIntOrNull(if (hex) 16 else 10) ?: return null
            return if (code in 1..0x10FFFF && code !in 0xD800..0xDFFF) code else null
        }
        return when (s.substring(start, end)) {
            "amp" -> '&'.code
            "lt" -> '<'.code
            "gt" -> '>'.code
            "quot" -> '"'.code
            "apos" -> '\''.code
            "nbsp" -> 0x00A0
            "lrm" -> 0x200E
            "rlm" -> 0x200F
            else -> null
        }
    }

    private fun appendCodePoint(
        out: StringBuilder,
        code: Int,
    ) {
        if (code <= 0xFFFF) {
            out.append(code.toChar())
        } else {
            val v = code - 0x10000
            out.append((0xD800 + (v shr 10)).toChar())
            out.append((0xDC00 + (v and 0x3FF)).toChar())
        }
    }
}
