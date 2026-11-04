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
package com.vitorpamplona.amethyst.cli

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.longOrNull

/**
 * The few `JsonNode` reads these tests make on amy's `--json` output, over kotlinx: `node["key"]`,
 * `node[i]`, iteration over an array, and Jackson's `asText` / `asInt` / `asBoolean` coercions.
 * A missing key gives a node that reads as empty, like Jackson's `path()`.
 */
class JsonNode(
    val element: JsonElement?,
) : Iterable<JsonNode> {
    operator fun get(key: String): JsonNode = JsonNode((element as? JsonObject)?.get(key))

    operator fun get(index: Int): JsonNode = JsonNode((element as? JsonArray)?.getOrNull(index))

    override fun iterator(): Iterator<JsonNode> =
        when (element) {
            is JsonArray -> element.map { JsonNode(it) }.iterator()
            is JsonObject -> element.values.map { JsonNode(it) }.iterator()
            else -> emptyList<JsonNode>().iterator()
        }

    fun has(key: String): Boolean = (element as? JsonObject)?.containsKey(key) == true

    fun size(): Int =
        when (element) {
            is JsonArray -> element.size
            is JsonObject -> element.size
            else -> 0
        }

    val isEmpty: Boolean get() = size() == 0

    fun fieldNames(): Iterator<String> = ((element as? JsonObject)?.keys ?: emptySet()).iterator()

    val isNull: Boolean get() = element is JsonNull

    val isArray: Boolean get() = element is JsonArray

    /** A scalar's text, `"null"` for JSON null, `""` for a container or a missing node. */
    fun asText(): String =
        when (element) {
            is JsonNull -> "null"
            is JsonPrimitive -> element.content
            else -> ""
        }

    fun asLong(): Long {
        val p = element as? JsonPrimitive ?: return 0
        if (p is JsonNull) return 0
        return p.longOrNull ?: p.doubleOrNull?.toLong() ?: p.booleanOrNull?.let { if (it) 1L else 0L } ?: 0
    }

    fun asInt(): Int = asLong().toInt()

    fun asBoolean(): Boolean {
        val p = element as? JsonPrimitive ?: return false
        if (p is JsonNull) return false
        if (p.isString) return p.content.trim() == "true"
        return p.booleanOrNull ?: p.longOrNull?.let { it != 0L } ?: false
    }

    override fun toString(): String = element.toString()

    companion object {
        fun parse(json: String): JsonNode = JsonNode(CliJson.parse(json))
    }
}
