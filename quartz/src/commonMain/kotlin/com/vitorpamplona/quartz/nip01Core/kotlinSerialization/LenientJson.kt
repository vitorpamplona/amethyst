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

import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * Field readers for JSON written by somebody else.
 *
 * These back the hand-written NIP-47 and CLINK serializers, where every byte
 * arrives from a third-party wallet or client. The rule they all follow: a field
 * that is missing, null, or of the wrong shape reads as `null` and the rest of
 * the message still parses. Nothing here throws.
 *
 * That is deliberately MORE forgiving than what Jackson did on this path. Jackson
 * ignored unknown properties but still raised MismatchedInputException when a
 * declared `String` arrived as an object, taking the whole message down over one
 * bad field. A payment response whose `fees_paid` is `"12"` instead of `12`, or
 * whose `metadata` is a string instead of an object, is worth reading for the
 * preimage it does carry.
 *
 * Use `jsonObject` / `jsonPrimitive` / `.content` directly only where the value
 * is ours and its shape is guaranteed — they throw, which is what these avoid.
 *
 * The base case: JSON `null` carries no more information than an absent key, so
 * every reader here treats the two alike.
 */
private fun JsonElement?.presentOrNull(): JsonElement? = if (this == null || this is JsonNull) null else this

fun JsonElement?.asObjectOrNull(): JsonObject? = presentOrNull() as? JsonObject

/**
 * The element as an array — wrapping a lone value in a one-element array, which is
 * what Jackson's ACCEPT_SINGLE_VALUE_AS_ARRAY did here. Several Nostr-native RPCs
 * (CLINK Manage `details`, typed `OfferData | OfferData[]`) answer with a bare
 * object for a single result and an array for a list.
 */
fun JsonElement?.asArrayOrNull(): JsonArray? =
    when (val e = presentOrNull()) {
        null -> null
        is JsonArray -> e
        else -> JsonArray(listOf(e))
    }

private fun JsonElement?.asPrimitiveOrNull(): JsonPrimitive? = presentOrNull() as? JsonPrimitive

// ---- object field readers ---------------------------------------------------

fun JsonObject.objOrNull(key: String): JsonObject? = this[key].asObjectOrNull()

fun JsonObject.arrayOrNull(key: String): JsonArray? = this[key].asArrayOrNull()

/** Text content, or null when the key is absent, null, an object or an array. */
fun JsonObject.stringOrNull(key: String): String? = this[key].asPrimitiveOrNull()?.content

/**
 * Like [stringOrNull] but drops the empty string too, for the many fields where a
 * peer writes `""` to mean "I have nothing for this".
 */
fun JsonObject.nonEmptyStringOrNull(key: String): String? = stringOrNull(key)?.ifBlank { null }

/** Accepts `12`, `"12"` and `12.0` — wallets send all three for the same field. */
fun JsonObject.longOrNull(key: String): Long? {
    val raw = this[key].asPrimitiveOrNull()?.content ?: return null
    return raw.toLongOrNull() ?: raw.toDoubleOrNull()?.takeIf { it.isFinite() }?.toLong()
}

fun JsonObject.intOrNull(key: String): Int? = longOrNull(key)?.toInt()

fun JsonObject.doubleOrNull(key: String): Double? = this[key].asPrimitiveOrNull()?.content?.toDoubleOrNull()

/** Accepts `true`/`false`, `"true"`/`"false"`, and the `1`/`0` some wallets send. */
fun JsonObject.booleanOrNull(key: String): Boolean? {
    val raw = this[key].asPrimitiveOrNull()?.content ?: return null
    return raw.toBooleanStrictOrNull()
        ?: when (raw) {
            "1" -> true
            "0" -> false
            else -> null
        }
}

/**
 * Every string in the array, skipping entries that are not primitives rather than
 * failing the array. A lone string is read as a single-element list.
 */
fun JsonObject.stringListOrNull(key: String): List<String>? = arrayOrNull(key)?.mapNotNull { it.asPrimitiveOrNull()?.content }

/** The object as a plain map, or null when the key holds anything else. */
fun JsonObject.anyMapOrNull(key: String): Map<String, Any?>? = objOrNull(key)?.toAnyMap()

/** Every object in the array, skipping entries that are not objects. */
fun JsonObject.objectListOrNull(key: String): List<JsonObject>? = arrayOrNull(key)?.mapNotNull { it.asObjectOrNull() }

/** A shape name for error messages that survives R8 — `::class.simpleName` does not. */
private fun JsonElement.shapeName(): String =
    when (this) {
        is JsonNull -> "null"
        is JsonPrimitive -> if (isString) "a string" else "a number or boolean"
        is JsonArray -> "an array"
        is JsonObject -> "an object"
    }

/**
 * The root of the message as an object.
 *
 * The one place these serializers still refuse input: a payload whose root is an
 * array, a bare string or null is not a partially-readable message, it is not a
 * message. Fails with an [IllegalArgumentException] naming what arrived, rather
 * than the ClassCastException `decodeJsonElement().jsonObject` raises.
 */
fun Decoder.decodeRootJsonObject(what: String): JsonObject {
    val decoder =
        this as? JsonDecoder
            ?: throw IllegalArgumentException("$what can only be read from JSON")
    val element = decoder.decodeJsonElement()
    return element as? JsonObject
        ?: throw IllegalArgumentException("$what must be a JSON object, but the payload is ${element.shapeName()}")
}
