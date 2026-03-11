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
package com.vitorpamplona.amethyst.commons.search

import com.vitorpamplona.quartz.nip19Bech32.decodePublicKeyAsHexOrNull
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toPersistentList

sealed interface Token {
    data class Operator(
        val name: String,
        val value: String,
        val raw: String,
    ) : Token

    data class Text(
        val value: String,
    ) : Token

    data object Or : Token

    data class Quoted(
        val value: String,
        val raw: String,
    ) : Token

    data class Negation(
        val term: String,
    ) : Token

    data class Hashtag(
        val tag: String,
    ) : Token
}

object QueryParser {
    private val KNOWN_OPERATORS = setOf("from", "kind", "since", "until", "lang", "domain")

    fun parse(input: String): SearchQuery {
        if (input.isBlank()) return SearchQuery.EMPTY
        val tokens = tokenize(input)
        return buildQuery(tokens)
    }

    internal fun tokenize(input: String): List<Token> {
        val tokens = mutableListOf<Token>()
        var i = 0
        val len = input.length

        while (i < len) {
            // Skip whitespace
            if (input[i].isWhitespace()) {
                i++
                continue
            }

            // Quoted phrase
            if (input[i] == '"') {
                val start = i
                i++ // skip opening quote
                val sb = StringBuilder()
                while (i < len && input[i] != '"') {
                    sb.append(input[i])
                    i++
                }
                if (i < len) i++ // skip closing quote
                val value = sb.toString()
                tokens.add(Token.Quoted(value, input.substring(start, i)))
                continue
            }

            // Negation
            if (input[i] == '-' && i + 1 < len && !input[i + 1].isWhitespace()) {
                i++ // skip -
                val word = readWord(input, i)
                i += word.length
                if (word.isNotEmpty()) {
                    tokens.add(Token.Negation(word))
                }
                continue
            }

            // Hashtag
            if (input[i] == '#' && i + 1 < len && !input[i + 1].isWhitespace()) {
                i++ // skip #
                val tag = readWord(input, i)
                i += tag.length
                if (tag.isNotEmpty()) {
                    tokens.add(Token.Hashtag(tag))
                }
                continue
            }

            // Read a word (may be operator:value, OR, or plain text)
            val word = readWord(input, i)
            i += word.length

            if (word.isEmpty()) {
                i++
                continue
            }

            // Check for OR keyword
            if (word == "OR") {
                tokens.add(Token.Or)
                continue
            }

            // Check for operator pattern (word:value)
            val colonIdx = word.indexOf(':')
            if (colonIdx > 0) {
                val opName = word.substring(0, colonIdx).lowercase()
                val opValue = word.substring(colonIdx + 1)
                if (opName in KNOWN_OPERATORS && opValue.isNotEmpty()) {
                    tokens.add(Token.Operator(opName, opValue, word))
                    continue
                }
                // Malformed operator (no value or unknown) → treat as text
            }

            tokens.add(Token.Text(word))
        }

        return tokens
    }

    private fun readWord(
        input: String,
        start: Int,
    ): String {
        var i = start
        while (i < input.length && !input[i].isWhitespace()) {
            i++
        }
        return input.substring(start, i)
    }

    private fun buildQuery(tokens: List<Token>): SearchQuery {
        val authors = mutableListOf<String>()
        val authorNames = mutableListOf<String>()
        val kinds = mutableListOf<Int>()
        val hashtags = mutableListOf<String>()
        val excludeTerms = mutableListOf<String>()
        val pseudoKinds = mutableListOf<String>()
        val textParts = mutableListOf<String>()
        val orTerms = mutableListOf<String>()
        var since: Long? = null
        var until: Long? = null
        var language: String? = null
        var domain: String? = null

        // Collect OR groups: text terms separated by OR
        var i = 0
        while (i < tokens.size) {
            when (val token = tokens[i]) {
                is Token.Operator -> {
                    when (token.name) {
                        "from" -> {
                            val hex = decodePublicKeyAsHexOrNull(token.value)
                            if (hex != null) {
                                authors.add(hex)
                            } else {
                                authorNames.add(token.value)
                            }
                        }

                        "kind" -> {
                            if (KindRegistry.isPseudoKind(token.value)) {
                                pseudoKinds.add(token.value.lowercase())
                            } else {
                                val resolved = KindRegistry.resolve(token.value)
                                if (resolved != null) {
                                    kinds.addAll(resolved)
                                } else {
                                    token.value.toIntOrNull()?.let { kinds.add(it) }
                                        ?: textParts.add(token.raw)
                                }
                            }
                        }

                        "since" -> {
                            val ts = parseDateToTimestamp(token.value)
                            if (ts != null) {
                                since = ts
                            } else {
                                textParts.add(token.raw)
                            }
                        }

                        "until" -> {
                            val ts = parseDateToTimestamp(token.value)
                            if (ts != null) {
                                until = ts
                            } else {
                                textParts.add(token.raw)
                            }
                        }

                        "lang" -> {
                            language = token.value.lowercase()
                        }

                        "domain" -> {
                            domain = token.value.lowercase()
                        }
                    }
                }

                is Token.Text -> {
                    // Check if this is part of an OR chain
                    if (i + 2 < tokens.size && tokens[i + 1] is Token.Or && tokens[i + 2] is Token.Text) {
                        // Start of OR chain: collect all terms
                        orTerms.add(token.value)
                        i++ // skip to OR
                        while (i < tokens.size && tokens[i] is Token.Or && i + 1 < tokens.size && tokens[i + 1] is Token.Text) {
                            i++ // skip OR
                            orTerms.add((tokens[i] as Token.Text).value)
                            i++ // skip text
                        }
                        continue
                    } else {
                        textParts.add(token.value)
                    }
                }

                is Token.Quoted -> {
                    textParts.add(token.raw)
                }

                is Token.Negation -> {
                    excludeTerms.add(token.term)
                }

                is Token.Hashtag -> {
                    hashtags.add(token.tag)
                }

                is Token.Or -> {
                    // Orphaned OR (no adjacent text terms) → treat as text
                    textParts.add("OR")
                }
            }
            i++
        }

        // Cap OR terms at 3
        val cappedOrTerms = orTerms.take(3)

        return SearchQuery(
            text = textParts.joinToString(" "),
            authors = authors.distinct().toImmutableList(),
            authorNames = authorNames.distinct().toImmutableList(),
            kinds = kinds.distinct().toImmutableList(),
            since = since,
            until = until,
            hashtags = hashtags.distinct().toImmutableList(),
            excludeTerms = excludeTerms.distinct().toImmutableList(),
            language = language,
            domain = domain,
            orTerms = cappedOrTerms.toPersistentList(),
            pseudoKinds = pseudoKinds.distinct().toImmutableList(),
        )
    }

    fun parseDateToTimestamp(dateStr: String): Long? {
        // ISO 8601 formats: YYYY, YYYY-MM, YYYY-MM-DD
        return try {
            val parts = dateStr.split("-")
            when (parts.size) {
                1 -> {
                    val year = parts[0].toIntOrNull() ?: return null
                    if (year < 1970 || year > 2100) return null
                    dateToUnix(year, 1, 1)
                }

                2 -> {
                    val year = parts[0].toIntOrNull() ?: return null
                    val month = parts[1].toIntOrNull() ?: return null
                    if (year < 1970 || year > 2100 || month < 1 || month > 12) return null
                    dateToUnix(year, month, 1)
                }

                3 -> {
                    val year = parts[0].toIntOrNull() ?: return null
                    val month = parts[1].toIntOrNull() ?: return null
                    val day = parts[2].toIntOrNull() ?: return null
                    if (year < 1970 || year > 2100 || month < 1 || month > 12 || day < 1 || day > 31) return null
                    dateToUnix(year, month, day)
                }

                else -> {
                    null
                }
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun dateToUnix(
        year: Int,
        month: Int,
        day: Int,
    ): Long = DateUtils.dateToUnix(year, month, day)
}
