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
package com.vitorpamplona.quartz.nip47WalletConnect.kotlinSerialization

import com.vitorpamplona.quartz.nip01Core.core.RawJson
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.JsonUnquotedLiteral
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

// Helper function to convert JsonElement to standard Kotlin types recursively
fun JsonElement.toAnyValue(): Any =
    when (this) {
        is JsonPrimitive -> {
            if (isString) {
                content
            } else {
                content.toBooleanStrictOrNull() ?: content.toDoubleOrNull() ?: content.toLongOrNull() ?: content
            }
        }

        is JsonObject -> {
            toAnyMap()
        }

        is JsonArray -> {
            map { it.toAnyValue() }
        }
    }

fun JsonObject.toAnyMap(): Map<String, Any?> = entries.associate { it.key to it.value.toAnyValue() }

/**
 * The inverse of [toAnyValue], for the `Map<String, Any?>` blobs NIP-47 carries as
 * `metadata`.
 *
 * `Json.encodeToJsonElement` CANNOT do this — it needs a serializer for the static
 * type, and `Any` has none, so it throws `SerializerException: Serializer for class
 * 'Any' is not found` at runtime for every populated metadata object. This walks
 * the value instead.
 *
 * [RawJson] becomes a `JsonUnquotedLiteral` so pre-serialized JSON reaches the wire
 * byte-for-byte; that is what lets a zap request still hash to the invoice's
 * `description_hash` after a round trip through this map.
 */
fun anyToJsonElement(value: Any?): JsonElement =
    when (value) {
        null -> JsonNull
        is RawJson -> JsonUnquotedLiteral(value.json)
        is JsonElement -> value
        is String -> JsonPrimitive(value)
        is Boolean -> JsonPrimitive(value)
        is Number -> JsonPrimitive(value)
        is Map<*, *> -> buildJsonObject { value.forEach { (k, v) -> put(k.toString(), anyToJsonElement(v)) } }
        is Iterable<*> -> buildJsonArray { value.forEach { add(anyToJsonElement(it)) } }
        is Array<*> -> buildJsonArray { value.forEach { add(anyToJsonElement(it)) } }
        else -> JsonPrimitive(value.toString())
    }
