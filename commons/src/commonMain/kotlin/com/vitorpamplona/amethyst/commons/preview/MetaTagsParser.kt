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
package com.vitorpamplona.amethyst.commons.preview

import com.vitorpamplona.amethyst.commons.util.codePointToChars

data class MetaTag(
    private val attrs: Map<String, String>,
) {
    /**
     * Returns a value of an attribute specified by its name (case insensitive), or empty string if it doesn't exist.
     */
    fun attr(name: String): String = attrs[name.lowercase()] ?: ""
}

/**
 * Extracts `<meta>` tags out of a (possibly partial) HTML document.
 *
 * This runs on every link preview, over bytes straight off the network, so the scan touches each
 * character once and allocates nothing until an actual `<meta>` shows up: `<` is found with
 * [String.indexOf], tag names are compared in place against the four names that matter, and only a
 * meta tag's attribute span is ever handed to [parseAttrs].
 */
object MetaTagsParser {
    private const val NO_QUOTE = ' '

    private const val META = "meta"
    private const val HEAD = "head"
    private const val SCRIPT = "script"
    private const val STYLE = "style"

    private const val SCRIPT_END = "</script"
    private const val STYLE_END = "</style"
    private const val COMMENT_START = "!--"
    private const val COMMENT_END = "-->"

    private enum class TagKind {
        /** A `<meta …>` start tag: its attribute span is [TagScanner.attrsStart]..<[TagScanner.attrsEnd]. */
        META,

        /** The `</head>` that ends the interesting part of the document. */
        HEAD_END,

        /** Anything else: other elements, comments, declarations, unparseable markup. */
        OTHER,
    }

    /**
     * Lazily parse a partial HTML document and extract meta tags.
     */
    fun parse(input: String): Sequence<MetaTag> =
        sequence {
            val s = TagScanner(input)
            while (!s.exhausted()) {
                val kind = s.nextTag()
                if (kind == TagKind.HEAD_END) break
                if (kind == TagKind.META) {
                    val attrs = parseAttrs(input, s.attrsStart, s.attrsEnd) ?: continue
                    yield(MetaTag(attrs))
                }
            }
        }

    private class TagScanner(
        private val input: String,
    ) {
        private val length = input.length
        private var p = 0

        /** Attribute span of the tag [nextTag] last reported as [TagKind.META]. */
        var attrsStart = 0
            private set
        var attrsEnd = 0
            private set

        fun exhausted(): Boolean = p >= length

        /**
         * True when `input[from..<to]` equals [lower], ASCII-case-insensitively. [lower] must hold
         * only lowercase ASCII letters: `code or 0x20` lands on such a letter only when the input
         * char is that same letter in either case, so the fold cannot produce a false positive.
         */
        private fun nameIs(
            from: Int,
            to: Int,
            lower: String,
        ): Boolean {
            if (to - from != lower.length) return false
            for (i in lower.indices) {
                val c = input[from + i]
                if (c != lower[i] && (c.code or 0x20).toChar() != lower[i]) return false
            }
            return true
        }

        private fun skipComment() {
            val end = input.indexOf(COMMENT_END, p)
            p = if (end < 0) length else end + COMMENT_END.length
        }

        private fun skipToTagEnd() {
            val end = input.indexOf('>', p)
            p = if (end < 0) length else end + 1
        }

        /** Leaves [p] on the `</name` that closes a raw-text element, or at the end of the input. */
        private fun skipRawText(endTag: String) {
            var i = p
            while (true) {
                i = input.indexOf('<', i)
                if (i < 0) {
                    p = length
                    return
                }
                if (input.regionMatches(i, endTag, 0, endTag.length, ignoreCase = true)) {
                    p = i
                    return
                }
                i++
            }
        }

        fun nextTag(): TagKind {
            val lt = input.indexOf('<', p)
            if (lt < 0) {
                p = length
                return TagKind.OTHER
            }
            p = lt + 1
            if (p >= length) return TagKind.OTHER

            // `<!-- ... -->`, `<!DOCTYPE ...>` and `<?...?>` are not element markup, so the
            // attribute-quote tracking below must not run over them. A comment holding an odd
            // number of quote characters -- an apostrophe in "we don't", a lone `"` -- would
            // otherwise leave the scanner inside a phantom quoted attribute value and make it
            // swallow every tag that follows, until the next matching quote character. That is
            // enough to hide a page's whole `<meta property="og:*">` block from the preview.
            val first = input[p]
            if (first == '!' || first == '?') {
                if (input.startsWith(COMMENT_START, p)) {
                    skipComment()
                } else {
                    skipToTagEnd()
                }
                return TagKind.OTHER
            }

            // read the tag name
            val isEnd = first == '/'
            if (isEnd) p++
            val nameStart = p
            while (p < length && !input[p].isWhitespace() && input[p] != '>') p++
            val nameEnd = p

            // seek to the start of the attrs part
            while (p < length && input[p].isWhitespace()) p++
            attrsStart = p

            // skip to the end of the tag, tracking quoted values so a `>` inside one doesn't end it
            var i = p
            var quote = NO_QUOTE
            while (i < length) {
                val c = input[i]
                if (quote == NO_QUOTE) {
                    // `>` or `/>` out of quote -> end of tag
                    if (c == '>') {
                        i++
                        break
                    }
                    if (c == '/' && i + 1 < length && input[i + 1] == '>') {
                        i += 2
                        break
                    }
                    if (c == '"' || c == '\'') quote = c
                } else if (c == quote) {
                    quote = NO_QUOTE
                }
                i++
            }
            p = i
            attrsEnd = i - 1

            if (isEnd) {
                return if (nameIs(nameStart, nameEnd, HEAD)) TagKind.HEAD_END else TagKind.OTHER
            }

            if (nameIs(nameStart, nameEnd, META)) return TagKind.META

            // Script and style bodies are raw text: a `<` in `for (i = 0; i < n; i++)` or a quote
            // in a JS string is not markup and must not be scanned as such, for the same reason
            // comments can't be.
            if (nameIs(nameStart, nameEnd, SCRIPT)) {
                skipRawText(SCRIPT_END)
            } else if (nameIs(nameStart, nameEnd, STYLE)) {
                skipRawText(STYLE_END)
            }

            return TagKind.OTHER
        }
    }

    // These two are `when` branches rather than a `Set<Char>` because `Set<Char>.contains` boxes
    // the char, once per attribute character of every meta tag.
    private fun isNonAttrNameChar(c: Char): Boolean =
        when (c) {
            '\u0000', '"', '\'', '>', '/' -> true
            else -> false
        }

    private fun isNonUnquotedAttrValueChar(c: Char): Boolean =
        when (c) {
            '"', '\'', '=', '>', '<', '`' -> true
            else -> false
        }

    // map of HTML element attribute name to its value, with additional logics:
    // - attribute names are matched in a case-insensitive manner
    // - attribute names never duplicate
    // - commonly used character references in attribute values are resolved
    private class Attrs {
        companion object {
            val RE_CHAR_REF = Regex("""&(#?)(\w+)(;?)""")
            val BASE_CHAR_REFS =
                mapOf(
                    "amp" to "&",
                    "AMP" to "&",
                    "quot" to "\"",
                    "QUOT" to "\"",
                    "lt" to "<",
                    "LT" to "<",
                    "gt" to ">",
                    "GT" to ">",
                    "nbsp" to " ",
                    "NBSP" to " ",
                    "middot" to "·",
                )
            val CHAR_REFS =
                mapOf(
                    "apos" to "'",
                    "equals" to "=",
                    "grave" to "`",
                    "DiacriticalGrave" to "`",
                    "039" to "'",
                    "8217" to "’",
                    "8216" to "‘",
                    "8220" to "“",
                    "8230" to "…",
                    "39" to "'",
                    "ldquo" to "“",
                    "rdquo" to "”",
                    "mdash" to "—",
                    "hellip" to "…",
                    "x27" to "'",
                    "nbsp" to " ",
                    "x2d" to "-",
                )

            fun replaceCharRefs(match: MatchResult): String {
                val isNumeric = match.groupValues[1].isNotEmpty()
                val ref = match.groupValues[2]
                val terminated = match.groupValues[3].isNotEmpty()

                // Numeric character references (&#34; / &#x22;) must be terminated by ';'
                if (isNumeric) {
                    if (!terminated) return match.value
                    val codePoint =
                        if (ref.startsWith("x", ignoreCase = true)) {
                            ref.drop(1).toIntOrNull(16)
                        } else {
                            ref.toIntOrNull(10)
                        } ?: return match.value
                    if (codePoint !in 0..0x10FFFF || codePoint in 0xD800..0xDFFF) {
                        return match.value
                    }
                    return codePointToChars(codePoint).concatToString()
                }

                val bcr = BASE_CHAR_REFS[ref]
                if (bcr != null) {
                    return bcr
                }
                // non-base char refs must be terminated by ';'
                if (terminated) {
                    val cr = CHAR_REFS[ref]
                    if (cr != null) {
                        return cr
                    }
                }
                return match.value
            }
        }

        private val attrs = mutableMapOf<String, String>()

        /** Adds an attribute, returning false if that name was already set (the first value wins). */
        fun add(
            name: String,
            value: String,
        ): Boolean {
            val key = name.lowercase()
            if (attrs.containsKey(key)) return false
            // Resolving character references is the expensive half of an attribute and almost no
            // value has an `&` in it, so the scan for one pays for itself.
            attrs[key] = if (value.indexOf('&') < 0) value else value.replace(RE_CHAR_REF, Companion::replaceCharRefs)
            return true
        }

        fun freeze(): Map<String, String> = attrs
    }

    private enum class State {
        NAME,
        BEFORE_EQ,
        AFTER_EQ,
        VALUE,
        SPACE,
    }

    /** Parses the attributes of a single tag, held in `input[from..<to]`. */
    private fun parseAttrs(
        input: String,
        from: Int,
        to: Int,
    ): Map<String, String>? {
        val attrs = Attrs()

        var state = State.NAME
        var nameBegin = from
        var nameEnd = from
        var valueBegin = from
        var valueQuote = NO_QUOTE

        for (i in from..<to) {
            val c = input[i]
            when (state) {
                State.NAME -> {
                    when {
                        c == '=' -> {
                            nameEnd = i
                            state = State.AFTER_EQ
                        }

                        c.isWhitespace() -> {
                            nameEnd = i
                            state = State.BEFORE_EQ
                        }

                        isNonAttrNameChar(c) || c.isISOControl() || !c.isDefined() -> {
                            return null
                        }
                    }
                }

                State.BEFORE_EQ -> {
                    when {
                        c == '=' -> {
                            state = State.AFTER_EQ
                        }

                        c.isWhitespace() -> {}

                        else -> {
                            // if it is expecting = but gets another name, starts another property
                            attrs.add(input.substring(nameBegin, nameEnd), "")

                            nameBegin = i
                            state = State.NAME
                        }
                    }
                }

                State.AFTER_EQ -> {
                    when {
                        c.isWhitespace() -> {}

                        c == '\'' || c == '"' -> {
                            valueBegin = i + 1
                            valueQuote = c
                            state = State.VALUE
                        }

                        else -> {
                            valueBegin = i
                            valueQuote = NO_QUOTE
                            state = State.VALUE
                        }
                    }
                }

                State.VALUE -> {
                    // -1 while the value is still running; otherwise the index one past its last char
                    var valueEnd = -1
                    if (valueQuote != NO_QUOTE) {
                        if (c == valueQuote) valueEnd = i
                    } else {
                        when {
                            c.isWhitespace() -> valueEnd = i

                            i == to - 1 -> valueEnd = i + 1

                            isNonUnquotedAttrValueChar(c) -> return null
                        }
                    }
                    if (valueEnd >= 0) {
                        val added =
                            attrs.add(
                                input.substring(nameBegin, nameEnd),
                                input.substring(valueBegin, valueEnd),
                            )
                        if (!added) return null
                        state = State.SPACE
                    }
                }

                State.SPACE -> {
                    if (!c.isWhitespace()) {
                        nameBegin = i
                        state = State.NAME
                    }
                }
            }
        }
        return attrs.freeze()
    }
}
