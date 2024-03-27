/**
 * Copyright (c) 2024 Vitor Pamplona
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

import kotlinx.collections.immutable.toImmutableMap

data class MetaTag(private val attrs: Map<String, String>) {
    /**
     * Returns a value of an attribute specified by its name (case insensitive), or empty string if it doesn't exist.
     */
    fun attr(name: String): String = attrs[name.lowercase()] ?: ""
}

object MetaTagsParser {
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

    private data class RawTag(val isEnd: Boolean, val name: String, val attrPart: String)

    private class TagScanner(private val input: String) {
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

        fun nextTag(): RawTag? {
            skipWhile { it != '<' }
            consume()

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
                    quote == null && c == '/' && peek() == '>' -> {
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
            if (!name.matches(Regex("""[0-9a-zA-Z]+"""))) {
                return null
            }
            val attrsPart = input.slice(attrsStart..<attrsEnd)
            return RawTag(isEnd, name.lowercase(), attrsPart)
        }
    }

    // map of HTML element attribute name to its value, with additional logics:
    // - attribute names are matched in a case-insensitive manner
    // - attribute names never duplicate
    // - commonly used character references in attribute values are resolved
    private class Attrs {
        companion object {
            val RE_CHAR_REF = Regex("""&(\w+)(;?)""")
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
                )
            val CHAR_REFS =
                mapOf(
                    "apos" to "'",
                    "equals" to "=",
                    "grave" to "`",
                    "DiacriticalGrave" to "`",
                )

            fun replaceCharRefs(match: MatchResult): String {
                val bcr = BASE_CHAR_REFS[match.groupValues[1]]
                if (bcr != null) {
                    return bcr
                }
                // non-base char refs must be terminated by ';'
                if (match.groupValues[2].isNotEmpty()) {
                    val cr = CHAR_REFS[match.groupValues[1]]
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
                        else -> return null
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
                    when {
                        valueQuote != null -> {
                            if (c == valueQuote) {
                                attr =
                                    Pair(
                                        input.slice(nameBegin..<nameEnd),
                                        input.slice(valueBegin..<i),
                                    )
                            }
                        }

                        valueQuote == null -> {
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
