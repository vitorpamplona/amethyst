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
package com.vitorpamplona.quartz.nip15Marketplace

import com.vitorpamplona.quartz.nip01Core.core.JsonMapper
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * Lenient reads of the JSON bodies NIP-15 puts in `content`.
 *
 * What relays hold on these kinds is far looser than the spec: prices written as strings
 * (`"price": "12.50"`), durations as floats, `shipping` as an object instead of a list, fields
 * missing, and other apps' formats on the same kind numbers (a typing game's scores on 30017,
 * an encrypted blob on 30020). A strict decoder drops the whole body on the first surprise, so
 * these read one field at a time and answer `null` (or an empty list) for any field that is
 * absent or of the wrong shape, never throwing.
 */
object Nip15Json {
    /** The body as a JSON object, or null when it is empty, not JSON, or not an object. */
    fun parseObject(content: String): JsonObject? {
        // Most junk on these kinds is not JSON at all (text, base64); skip the parser for it.
        val first = content.firstOrNull { !it.isWhitespace() }
        if (first != '{') return null
        return try {
            JsonMapper.jsonInstance.parseToJsonElement(content) as? JsonObject
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            null
        }
    }
}

/** A string field, trimmed; null when absent, blank or not a string. */
internal fun JsonObject.textOrNull(key: String): String? = (this[key] as? JsonPrimitive)?.textOrNull()

/** A finite number, from a JSON number or a numeric string (`"12.50"`); null otherwise. */
internal fun JsonObject.numberOrNull(key: String): Double? = (this[key] as? JsonPrimitive)?.numberOrNull()

/** [numberOrNull] truncated to a whole number (timestamps, seconds, stock counts). */
internal fun JsonObject.wholeNumberOrNull(key: String): Long? = numberOrNull(key)?.toLong()

/** The strings of an array field, trimmed, blanks and non-strings dropped. */
internal fun JsonObject.textList(key: String): List<String> = (this[key] as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.textOrNull() } ?: emptyList()

/** The objects of an array field; other entries (and a non-array field) are dropped. */
internal fun JsonObject.objectList(key: String): List<JsonObject> = (this[key] as? JsonArray)?.mapNotNull { it as? JsonObject } ?: emptyList()

/** An object field, or null. */
internal fun JsonObject.objectOrNull(key: String): JsonObject? = this[key] as? JsonObject

/**
 * The `[key, value]` pairs of a spec-style array (`[["color", "red"], ["size", 42]]`): each entry
 * must be an array whose first two items are primitives; the key must not be blank.
 */
internal fun JsonObject.pairList(key: String): List<List<String>> =
    (this[key] as? JsonArray)?.mapNotNull { entry ->
        val pair = entry as? JsonArray ?: return@mapNotNull null
        if (pair.size < 2) return@mapNotNull null
        val k = (pair[0] as? JsonPrimitive)?.textOrNull() ?: return@mapNotNull null
        val v = (pair[1] as? JsonPrimitive)?.takeIf { it !is JsonNull }?.content?.trim() ?: return@mapNotNull null
        listOf(k, v)
    } ?: emptyList()

private fun JsonPrimitive.textOrNull(): String? = if (isString) content.trim().ifEmpty { null } else null

private fun JsonPrimitive.numberOrNull(): Double? {
    if (this is JsonNull) return null
    return content.trim().toDoubleOrNull()?.takeIf { it.isFinite() }
}
