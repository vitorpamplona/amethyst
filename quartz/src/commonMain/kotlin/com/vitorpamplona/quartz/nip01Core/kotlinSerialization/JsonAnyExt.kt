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
package com.vitorpamplona.quartz.nip01Core.kotlinSerialization

import com.vitorpamplona.quartz.nip01Core.core.RawJson
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.JsonUnquotedLiteral
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Encodes an untyped `Any?` tree — the shape several Nostr RPCs carry as a free-form
 * `Map<String, Any?>` — into a [JsonElement].
 *
 * `Json.encodeToJsonElement` CANNOT do this: it resolves a serializer from the STATIC
 * type, and `Any` has none, so it throws `SerializationException: Serializer for class
 * 'Any' is not found` at runtime for every populated map. This walks the value instead.
 *
 * DECLARED AT nip01Core LEVEL, beside the other kotlinx serializers, because [RawJson]
 * is: it is registered globally on the Jackson side, so a per-NIP copy of this function
 * would leave the kotlinx backend supporting raw JSON in some packages and silently
 * quoting it into a string in others — the exact corruption [RawJson] exists to prevent.
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
