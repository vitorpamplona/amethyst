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
import kotlinx.collections.immutable.toImmutableMap

data class MetaTag(
    private val attrs: Map<String, String>,
) {
    /**
     * Returns a value of an attribute specified by its name (case insensitive), or empty string if it doesn't exist.
     */
    fun attr(name: String): String = attrs[name.lowercase()] ?: ""
}

object MetaTagsParser {
    private val TAG_NAME = Regex("""[0-9a-zA-Z]+""")
    private val NON_ATTR_NAME_CHARS = setOf(Char(0x0), '"', '\'', '>', '/')
    private val NON_UNQUOTED_ATTR_VALUE_CHARS = setOf('"', '\'', '=', '>', '<', '`')

    /**
     * Lazily parse a partial HTML document and extract meta tags.
     */
    fun parse(input: String): Sequence<MetaTag> =
        sequence {
            val s = TagScanner(input)
            while (!s.exhausted()) {
                val t = s.nextTag() ?: continue
                if (t.name == "head" && t.isEnd) {
                    break
                }
                if (t.name == "meta") {
                    val attrs = parseAttrs(t.attrPart) ?: continue
                    yield(MetaTag(attrs))
                }
            }
        }

    private data class RawTag(
        val isEnd: Boolean,
        val name: String,
        val attrPart: String,
    )

    private class TagScanner(
        private val input: String,
    ) {
        private var p = 0

        fun exhausted(): Boolean = p >= input.length

        private fun peek(): Char = input[p]

        private fun consume(): Char = input[p++]

        private fun skipWhile(pred: (Char) -> Boolean) {
            while (!this.exhausted() && pred(this.peek())) {
                this.consume()
            }
        }

        private fun skipSpaces() {
            this.skipWhile { it.isWhitespace() }
        }

        private fun skipComment() {
            val end = input.indexOf("-->", p)
            p = if (end < 0) input.length else end + 3
        }

        private fun skipToTagEnd() {
            skipWhile { it != '>' }
            if (!exhausted()) consume()
        }

        /** Leaves [p] on the `</name` that closes a raw-text element, or at the end of the input. */
        private fun skipRawText(name: String) {
            val end = input.indexOf("</$name", p, ignoreCase = true)
            p = if (end < 0) input.length else end
        }

        fun nextTag(): RawTag? {
            skipWhile { it != '<' }
            if (this.exhausted()) return null
            consume()
            if (this.exhausted()) return null

            // `<!-- ... -->`, `<!DOCTYPE ...>` and `<?...>` are not element markup, so the
            // attribute-quote tracking below must not run over them. A comment holding an odd
            // number of quote characters -- an apostrophe in "we don't", a lone `"` -- would
            // otherwise leave the scanner inside a phantom quoted attribute value and make it
            // swallow every tag that follows, until the next matching quote character. That is
            // enough to hide a page's whole `<meta property="og:*">` block from the preview.
            if (peek() == '!' || peek() == '?') {
                if (input.startsWith("!--", p)) {
                    skipComment()
                } else {
                    skipToTagEnd()
                }
                return null
            }

            // read tag name
            val isEnd = peek() == '/'
            if (isEnd) {
                consume()
            }
            val nameStart = p
            skipWhile { !it.isWhitespace() && it != '>' }
            val nameEnd = p

            // seek to start of attrs part
            skipSpaces()
            val attrsStart = p

            // skip until end of tag
            var quote: Char? = null
            while (!exhausted()) {
                val c = consume()
                when {
                    // `/>` out of quote -> end of tag
                    quote == null && c == '/' && !exhausted() && peek() == '>' -> {
                        consume()
                        break
                    }

                    // `>` out of quote -> end of tag
                    quote == null && c == '>' -> {
                        break
                    }

                    // entering quote
                    quote == null && (c == '\'' || c == '"') -> {
                        quote = c
                    }

                    // leaving quote
                    quote != null && c == quote -> {
                        quote = null
                    }
                }
            }
            val attrsEnd = p - 1

            val name = input.slice(nameStart..<nameEnd)
            if (!name.matches(TAG_NAME)) {
                return null
            }
            val lowercaseName = name.lowercase()

            // Script and style bodies are raw text: a `<` in `for (i = 0; i < n; i++)` or a quote
            // in a JS string is not markup and must not be scanned as such, for the same reason
            // comments can't be.
            if (!isEnd && (lowercaseName == "script" || lowercaseName == "style")) {
                skipRawText(lowercaseName)
            }

            val attrsPart = input.slice(attrsStart..<attrsEnd)
            return RawTag(isEnd, lowercaseName, attrsPart)
        }
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

        fun add(attr: Pair<String, String>) {
            val name = attr.first.lowercase()
            if (attrs.containsKey(name)) {
                throw IllegalArgumentException("duplicated attribute name: $name")
            }
            val value = attr.second.replace(RE_CHAR_REF, Companion::replaceCharRefs)
            attrs += Pair(name, value)
        }

        fun freeze(): Map<String, String> = attrs.toImmutableMap()
    }

    private enum class State {
        NAME,
        BEFORE_EQ,
        AFTER_EQ,
        VALUE,
        SPACE,
    }

    private fun parseAttrs(input: String): Map<String, String>? {
        val attrs = Attrs()

        var state = State.NAME
        var nameBegin = 0
        var nameEnd = 0
        var valueBegin = 0
        var valueQuote: Char? = null

        input.forEachIndexed { i, c ->
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

                        NON_ATTR_NAME_CHARS.contains(c) || c.isISOControl() || !c.isDefined() -> {
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
                            runCatching { attrs.add(Pair(input.slice(nameBegin..<nameEnd), "")) }

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
                            valueQuote = null
                            state = State.VALUE
                        }
                    }
                }

                State.VALUE -> {
                    var attr: Pair<String, String>? = null
                    if (valueQuote != null) {
                        if (c == valueQuote) {
                            attr =
                                Pair(
                                    input.slice(nameBegin..<nameEnd),
                                    input.slice(valueBegin..<i),
                                )
                        }
                    } else {
                        when {
                            c.isWhitespace() -> {
                                attr =
                                    Pair(
                                        input.slice(nameBegin..<nameEnd),
                                        input.slice(valueBegin..<i),
                                    )
                            }

                            i == input.length - 1 -> {
                                attr =
                                    Pair(
                                        input.slice(nameBegin..<nameEnd),
                                        input.slice(valueBegin..i),
                                    )
                            }

                            NON_UNQUOTED_ATTR_VALUE_CHARS.contains(c) -> {
                                return null
                            }
                        }
                    }
                    if (attr != null) {
                        runCatching { attrs.add(attr) }.getOrNull() ?: return null
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
