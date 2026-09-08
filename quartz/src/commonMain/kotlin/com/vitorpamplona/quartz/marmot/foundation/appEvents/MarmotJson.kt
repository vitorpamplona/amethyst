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
package com.vitorpamplona.quartz.marmot.foundation.appEvents

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive

/**
 * A parsed top-level JSON object, keeping the one thing every JSON library
 * throws away: which keys appeared more than once.
 *
 * `foundation/application-messages.md` requires a decoder to REJECT a Marmot app
 * payload with duplicate keys, and a `Map`-shaped parse cannot tell you that —
 * it has already picked a winner. "Last one wins" and "first one wins" are both
 * defensible, which is exactly the problem: two implementations would derive
 * different events, and therefore different ids, from identical bytes.
 */
class MarmotJsonObject(
    private val values: JsonObject,
    /** Keys that appeared more than once at the top level. */
    val duplicateKeys: Set<String>,
) {
    val keys: Set<String> get() = values.keys

    fun containsKey(name: String) = values.containsKey(name)

    fun string(name: String): String = requireNotNull(values[name]).jsonPrimitive.content

    fun long(name: String): Long =
        requireNotNull(requireNotNull(values[name]).jsonPrimitive.content.toLongOrNull()) {
            "$name is not an integer"
        }

    fun int(name: String): Int =
        requireNotNull(requireNotNull(values[name]).jsonPrimitive.content.toIntOrNull()) {
            "$name is not an integer"
        }

    fun tags(name: String): Array<Array<String>> {
        val array = values[name] as? JsonArray ?: throw IllegalArgumentException("$name is not an array")
        return Array(array.size) { i ->
            val tag = array[i] as? JsonArray ?: throw IllegalArgumentException("$name[$i] is not an array")
            Array(tag.size) { j ->
                (tag[j] as? JsonPrimitive)?.content
                    ?: throw IllegalArgumentException("$name[$i][$j] is not a string")
            }
        }
    }

    /** The raw element, for callers that need a nested object. */
    fun element(name: String) = values[name]
}

/** Strict parsing helpers for Marmot app payloads. */
object MarmotJson {
    private val parser = Json { ignoreUnknownKeys = false }

    fun parseObject(json: String): MarmotJsonObject {
        val element = parser.parseToJsonElement(json)
        val obj = element as? JsonObject ?: throw IllegalArgumentException("payload is not a JSON object")
        return MarmotJsonObject(obj, findDuplicateTopLevelKeys(json))
    }

    /**
     * Scan the raw text for repeated top-level member names.
     *
     * A hand-rolled scan rather than a library call, because every JSON library
     * this codebase has resolves duplicates before the caller sees them. It only
     * has to be right about ONE thing — where a top-level key sits — so it
     * tracks nesting depth and string boundaries and reads nothing else.
     */
    private fun findDuplicateTopLevelKeys(json: String): Set<String> {
        val seen = mutableSetOf<String>()
        val duplicates = mutableSetOf<String>()
        var depth = 0
        var i = 0
        var expectingKey = false

        while (i < json.length) {
            when (val ch = json[i]) {
                '{' -> {
                    depth++
                    if (depth == 1) expectingKey = true
                }

                '}' -> depth--
                '[' -> depth++
                ']' -> depth--
                ',' -> if (depth == 1) expectingKey = true
                '"' -> {
                    val end = endOfString(json, i)
                    if (depth == 1 && expectingKey) {
                        val key = unescape(json.substring(i + 1, end))
                        if (!seen.add(key)) duplicates.add(key)
                        expectingKey = false
                    }
                    i = end
                }

                else ->
                    if (ch != ' ' && ch != '\n' && ch != '\r' && ch != '\t' && ch != ':') {
                        expectingKey = false
                    }
            }
            i++
        }
        return duplicates
    }

    /** Index of the closing quote of the string starting at [start]. */
    private fun endOfString(
        json: String,
        start: Int,
    ): Int {
        var i = start + 1
        while (i < json.length) {
            when (json[i]) {
                '\\' -> i++
                '"' -> return i
            }
            i++
        }
        throw IllegalArgumentException("unterminated string in payload")
    }

    private fun unescape(raw: String): String {
        if ('\\' !in raw) return raw
        val sb = StringBuilder(raw.length)
        var i = 0
        while (i < raw.length) {
            val ch = raw[i]
            if (ch != '\\' || i + 1 >= raw.length) {
                sb.append(ch)
                i++
                continue
            }
            when (val esc = raw[i + 1]) {
                'n' -> sb.append('\n')
                'r' -> sb.append('\r')
                't' -> sb.append('\t')
                'b' -> sb.append('\u0008')
                'f' -> sb.append('\u000C')
                'u' -> {
                    val hex = raw.substring(i + 2, minOf(i + 6, raw.length))
                    sb.append(hex.toInt(16).toChar())
                    i += 4
                }

                else -> sb.append(esc)
            }
            i += 2
        }
        return sb.toString()
    }
}
