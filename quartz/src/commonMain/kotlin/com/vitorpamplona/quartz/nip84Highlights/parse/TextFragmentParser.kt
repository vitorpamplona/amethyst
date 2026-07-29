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
package com.vitorpamplona.quartz.nip84Highlights.parse

/**
 * The decoded pieces of a WICG Text Fragment directive
 * (`#:~:text=[prefix-,]textStart[,textEnd][,-suffix]`), the anchor browsers append when a
 * user shares a "link to highlight" of selected text.
 *
 * All fields are percent-decoded and never blank (empty pieces become null).
 *
 * @property prefix the text immediately before the match, used for disambiguation
 * @property start the beginning of the matched (highlighted) text — the whole match when [end] is null
 * @property end the end of the matched text when the browser split a long selection into start/end bounds
 * @property suffix the text immediately after the match, used for disambiguation
 */
class TextFragment(
    val prefix: String?,
    val start: String?,
    val end: String?,
    val suffix: String?,
)

/**
 * Parses (and strips) WICG Text Fragment directives from a URL.
 *
 * Text fragments live in the URL fragment after a `:~:` delimiter, e.g.
 * `https://example.com/page#:~:text=prefix-,highlighted%20text,-suffix`. This is what
 * Chrome/Edge/Safari emit when a user shares a link to selected text; the same encoding
 * is used by the "Copy link to highlight" and text-selection share actions.
 */
object TextFragmentParser {
    private const val DIRECTIVE_DELIMITER = ":~:"
    private const val TEXT_PARAM = "text="

    /**
     * Extracts the first `text=` directive from [url]'s fragment, or null when there is
     * no text fragment. Only the first `text=` directive is read — a URL may carry several
     * (`&text=`), but a single highlight maps to one passage.
     */
    fun parse(url: String): TextFragment? {
        val hashIndex = url.indexOf('#')
        if (hashIndex < 0) return null

        val fragment = url.substring(hashIndex + 1)
        val directiveIndex = fragment.indexOf(DIRECTIVE_DELIMITER)
        if (directiveIndex < 0) return null

        val directives = fragment.substring(directiveIndex + DIRECTIVE_DELIMITER.length)
        val textParam = directives.split("&").firstOrNull { it.startsWith(TEXT_PARAM) } ?: return null
        val value = textParam.substring(TEXT_PARAM.length)
        if (value.isEmpty()) return null

        // Commas that belong to the highlighted text itself are percent-encoded (%2C), so the
        // raw commas here are always the directive's own start/end/prefix/suffix separators.
        val tokens = value.split(",").toMutableList()

        var prefix: String? = null
        var suffix: String? = null

        if (tokens.isNotEmpty() && tokens.first().endsWith("-")) {
            prefix = tokens.removeAt(0).dropLast(1)
        }
        if (tokens.isNotEmpty() && tokens.last().startsWith("-")) {
            suffix = tokens.removeAt(tokens.size - 1).drop(1)
        }

        val start = tokens.getOrNull(0)
        val end = tokens.getOrNull(1)

        return TextFragment(
            prefix = decode(prefix),
            start = decode(start),
            end = decode(end),
            suffix = decode(suffix),
        )
    }

    /**
     * Returns [url] with any `:~:` text-fragment directive removed, so it can be stored as a
     * clean `r` source reference. A surrounding `#` that only introduced the directive is
     * dropped too; a real element-id fragment before the `:~:` is kept.
     */
    fun stripTextFragment(url: String): String {
        val hashIndex = url.indexOf('#')
        if (hashIndex < 0) return url

        val fragment = url.substring(hashIndex + 1)
        val directiveIndex = fragment.indexOf(DIRECTIVE_DELIMITER)
        if (directiveIndex < 0) return url

        val beforeDirective = fragment.substring(0, directiveIndex)
        val base = url.substring(0, hashIndex)

        return if (beforeDirective.isEmpty()) base else "$base#$beforeDirective"
    }

    private fun decode(value: String?): String? {
        if (value.isNullOrEmpty()) return null
        return percentDecode(value).takeIf { it.isNotEmpty() }
    }

    /**
     * Percent-decodes a text-fragment component. Unlike form decoding it leaves `+`
     * verbatim (a literal plus in the text is `+`, not a space — spaces arrive as `%20`),
     * and decodes multi-byte UTF-8 sequences byte-by-byte.
     */
    private fun percentDecode(input: String): String {
        if (!input.contains('%')) return input

        val bytes = ArrayList<Byte>(input.length)
        var i = 0
        while (i < input.length) {
            val c = input[i]
            if (c == '%' && i + 2 < input.length) {
                val hi = hexValue(input[i + 1])
                val lo = hexValue(input[i + 2])
                if (hi >= 0 && lo >= 0) {
                    bytes.add(((hi shl 4) or lo).toByte())
                    i += 3
                    continue
                }
            }
            // Non-escape character: re-encode as UTF-8 so it round-trips with decoded bytes.
            c.toString().encodeToByteArray().forEach { bytes.add(it) }
            i++
        }
        return bytes.toByteArray().decodeToString()
    }

    private fun hexValue(c: Char): Int =
        when (c) {
            in '0'..'9' -> c - '0'
            in 'a'..'f' -> c - 'a' + 10
            in 'A'..'F' -> c - 'A' + 10
            else -> -1
        }
}
