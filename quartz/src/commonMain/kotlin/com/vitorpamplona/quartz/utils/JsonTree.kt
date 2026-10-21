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
package com.vitorpamplona.quartz.utils

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.longOrNull

/*
 * Reading third-party JSON documents (block explorers, fee estimators) as kotlinx trees with the
 * habits of Jackson's JsonNode, which this code was written against: `node["key"]` is null when
 * the key is missing but a JSON `null` is a present value, and the `as*` readers coerce the way
 * JsonNode's do. A missing value read with an `as*` reader fails, as it did with JsonNode.
 */

internal fun parseJsonTree(json: String): JsonElement = Json.parseToJsonElement(json)

/** The field [key], or null when this is not an object or lacks it. */
internal operator fun JsonElement?.get(key: String): JsonElement? = (this as? JsonObject)?.get(key)

/** The array's entries, or the object's values; empty for anything else. */
internal fun JsonElement?.elements(): List<JsonElement> =
    when (this) {
        is JsonArray -> this
        is JsonObject -> values.toList()
        else -> emptyList()
    }

private fun JsonElement?.present(): JsonElement = this ?: throw IllegalArgumentException("missing JSON value")

/** A scalar's text, `"null"` for JSON null, `""` for a container. */
internal fun JsonElement?.asText(): String =
    when (val e = present()) {
        is JsonNull -> "null"
        is JsonPrimitive -> e.content
        else -> ""
    }

/** A number (a fraction truncated), numeric text or a boolean as 1/0; 0 otherwise. */
internal fun JsonElement?.asLong(): Long {
    val p = present() as? JsonPrimitive ?: return 0
    if (p is JsonNull) return 0
    if (p.isString) return p.content.trim().let { it.toLongOrNull() ?: it.toDoubleOrNull()?.toLong() } ?: 0
    return p.longOrNull ?: p.doubleOrNull?.toLong() ?: p.booleanOrNull?.let { if (it) 1L else 0L } ?: 0
}

internal fun JsonElement?.asInt(): Int = asLong().toInt()

/** A number, numeric text or a boolean as 1/0; 0.0 otherwise. */
internal fun JsonElement?.asDouble(): Double {
    val p = present() as? JsonPrimitive ?: return 0.0
    if (p is JsonNull) return 0.0
    if (p.isString) return p.content.trim().toDoubleOrNull() ?: 0.0
    return p.doubleOrNull ?: p.booleanOrNull?.let { if (it) 1.0 else 0.0 } ?: 0.0
}

/** True, a non-zero number or the text "true". */
internal fun JsonElement?.asBoolean(): Boolean {
    val p = present() as? JsonPrimitive ?: return false
    if (p is JsonNull) return false
    if (p.isString) return p.content.trim() == "true"
    return p.booleanOrNull ?: p.longOrNull?.let { it != 0L } ?: false
}
