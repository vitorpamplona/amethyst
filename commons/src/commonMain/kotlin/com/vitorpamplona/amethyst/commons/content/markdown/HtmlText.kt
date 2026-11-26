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
package com.vitorpamplona.amethyst.commons.content.markdown

/**
 * The little HTML Markdown text needs without interpreting HTML (NIP-23): entity references, and
 * telling a line break tag from the rest.
 */
object HtmlText {
    private val TAG = Regex("<[^>\\n]*>")
    private val BREAK = Regex("<\\s*br\\s*/?\\s*>", RegexOption.IGNORE_CASE)
    private val ENTITY = Regex("&(#[0-9]{1,7}|#[xX][0-9a-fA-F]{1,6}|[a-zA-Z][a-zA-Z0-9]{1,31});")

    private val NAMED =
        mapOf(
            "amp" to "&",
            "lt" to "<",
            "gt" to ">",
            "quot" to "\"",
            "apos" to "'",
            "nbsp" to "\u00A0",
            "copy" to "\u00A9",
            "reg" to "\u00AE",
            "trade" to "\u2122",
            "hellip" to "\u2026",
            "mdash" to "\u2014",
            "ndash" to "\u2013",
            "lsquo" to "\u2018",
            "rsquo" to "\u2019",
            "ldquo" to "\u201C",
            "rdquo" to "\u201D",
            "laquo" to "\u00AB",
            "raquo" to "\u00BB",
            "middot" to "\u00B7",
            "bull" to "\u2022",
            "deg" to "\u00B0",
            "times" to "\u00D7",
            "divide" to "\u00F7",
            "euro" to "\u20AC",
            "pound" to "\u00A3",
            "yen" to "\u00A5",
            "cent" to "\u00A2",
            "sect" to "\u00A7",
            "para" to "\u00B6",
            "larr" to "\u2190",
            "rarr" to "\u2192",
            "uarr" to "\u2191",
            "darr" to "\u2193",
        )

    fun isLineBreak(tag: String): Boolean = BREAK.matches(tag.trim())

    fun strip(html: String): String = TAG.replace(BREAK.replace(html, "\n"), "")

    fun decodeEntities(text: String): String {
        if (!text.contains('&')) return text
        return ENTITY.replace(text) { match ->
            val name = match.groupValues[1]
            when {
                name.startsWith("#x") || name.startsWith("#X") -> codePoint(name.substring(2).toIntOrNull(16)) ?: match.value
                name.startsWith("#") -> codePoint(name.substring(1).toIntOrNull()) ?: match.value
                else -> NAMED[name] ?: match.value
            }
        }
    }

    private fun codePoint(cp: Int?): String? {
        if (cp == null || cp == 0 || cp > 0x10FFFF || cp in 0xD800..0xDFFF) return if (cp == 0) "\uFFFD" else null
        return if (cp < 0x10000) {
            cp.toChar().toString()
        } else {
            val v = cp - 0x10000
            charArrayOf((0xD800 + (v shr 10)).toChar(), (0xDC00 + (v and 0x3FF)).toChar()).concatToString()
        }
    }
}
