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
package com.vitorpamplona.amethyst.service.previews

import kotlinx.collections.immutable.toImmutableMap
import java.lang.StringBuilder

internal data class MetaTag(private val attrs: Map<String, String>) {
    fun attr(name: String): String = attrs[name.lowercase()] ?: ""
}

// parse a partial HTML document and extract meta tags
internal object MetaTagsParser {
    private val NON_ATTR_NAME_CHARS = setOf(Char(0x0), '"', '\'', '>', '/')
    private val NON_UNQUOTED_ATTR_VALUE_CHARS = setOf('"', '\'', '=', '>', '<', '`')

    fun parse(input: String): Sequence<MetaTag> =
        sequence {
            val s = TagScanner(input)
            while (!s.exhausted()) {
                val t = s.nextTag() ?: continue
                if (t.name == "/head") {
                    break
                }
                if (t.name == "meta") {
                    val attrs = parseAttrs(t.attrPart) ?: continue
                    yield(MetaTag(attrs))
                }
            }
        }

    private data class RawTag(val name: String, val attrPart: String)

    private class TagScanner(private val input: String) {
        var p = 0

        fun exhausted(): Boolean = p >= input.length

        private fun peek(): Char = input[p]

        private fun consume(): Char {
            return input[p++]
        }

        private fun consumeChar(c: Char): Boolean {
            if (this.peek() == c) {
                this.consume()
                return true
            }
            return false
        }

        private fun skipSpaces() {
            while (!this.exhausted() && this.peek().isWhitespace()) {
                this.consume()
            }
        }

        private fun skipUntil(c: Char) {
            while (!this.exhausted() && this.peek() != c) {
                this.consume()
            }
        }

        private fun readWhile(pred: (Char) -> Boolean): String {
            val sb = StringBuilder()
            while (!this.exhausted() && pred(this.peek())) {
                sb.append(this.consume())
            }
            return sb.toString()
        }

        fun nextTag(): RawTag? {
            skipUntil('<')
            consume()

            // read tag name
            val name = StringBuilder()
            if (consumeChar('/')) {
                name.append('/')
            }
            val n = readWhile { !it.isWhitespace() && it != '>' }
            skipSpaces()

            // read until end of tag
            val attrsPart = StringBuilder()
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
                attrsPart.append(c)
            }

            if (!n.matches(Regex("""[0-9a-zA-Z]+"""))) {
                return null
            }
            return RawTag(name.append(n).toString().lowercase(), attrsPart.toString())
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
