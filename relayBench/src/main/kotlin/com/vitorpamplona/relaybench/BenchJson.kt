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
package com.vitorpamplona.relaybench

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.longOrNull
import java.io.Reader

/**
 * The bench's JSON reads, on kotlinx: relay frames and NIP-11 documents walked as trees with
 * Jackson's `JsonNode` habits (`node[0]?.asText()` is null when the element is missing), and a
 * streaming split of a corpus that is one huge JSON array.
 */
object BenchJson {
    val pretty =
        Json {
            prettyPrint = true
            encodeDefaults = true
        }

    fun parse(json: String): JsonElement = Json.parseToJsonElement(json)

    /**
     * Calls [action] with the text of each object at the top level of the array [reader] is
     * positioned on, one at a time, so a corpus never has to fit in memory as a tree. Stops when
     * [action] returns false.
     */
    fun forEachArrayObject(
        reader: Reader,
        action: (String) -> Boolean,
    ) {
        val buf = CharArray(1 shl 16)
        val sb = StringBuilder()
        var depth = 0
        var inString = false
        var escaped = false
        var n = reader.read(buf)
        while (n > 0) {
            for (i in 0 until n) {
                val c = buf[i]
                if (depth > 0) sb.append(c)
                when {
                    escaped -> {
                        escaped = false
                    }

                    inString -> {
                        if (c == '\\') {
                            escaped = true
                        } else if (c == '"') {
                            inString = false
                        }
                    }

                    c == '"' -> {
                        inString = true
                    }

                    c == '{' -> {
                        if (depth == 0) sb.append(c)
                        depth++
                    }

                    c == '}' -> {
                        depth--
                        if (depth == 0) {
                            if (!action(sb.toString())) return
                            sb.setLength(0)
                        }
                    }
                }
            }
            n = reader.read(buf)
        }
    }
}

/** The array element at [index], or null when this is not an array or is too short. */
operator fun JsonElement?.get(index: Int): JsonElement? = (this as? JsonArray)?.getOrNull(index)

/** The field [key], or null when this is not an object or lacks it. */
operator fun JsonElement?.get(key: String): JsonElement? = (this as? JsonObject)?.get(key)

/** Jackson's `asText()`: a scalar's text, `"null"` for JSON null, `""` for a container. */
fun JsonElement.asText(): String =
    when (this) {
        is JsonNull -> "null"
        is JsonPrimitive -> content
        else -> ""
    }

/** Jackson's `asBoolean()`: true, a non-zero number or the text "true". */
fun JsonElement.asBoolean(): Boolean {
    val p = this as? JsonPrimitive ?: return false
    if (p is JsonNull) return false
    if (p.isString) return p.content.trim() == "true"
    return p.booleanOrNull ?: p.longOrNull?.let { it != 0L } ?: false
}

/** Jackson's `asLong()`: a number (a fraction truncated) or numeric text, else 0. */
fun JsonElement.asLong(): Long {
    val p = this as? JsonPrimitive ?: return 0
    if (p is JsonNull) return 0
    return p.longOrNull ?: p.doubleOrNull?.toLong() ?: 0
}

fun JsonElement.asInt(): Int = asLong().toInt()
