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

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.longOrNull
import java.io.File
import java.math.BigInteger
import java.net.URI
import java.util.Base64
import java.util.UUID

/**
 * amy's JSON, on kotlinx.serialization.
 *
 * Two jobs: the `--json` output contract, built from the plain maps, lists and scalars every
 * command assembles ([encode]), and amy's own files under `~/.amy` ([files], [sparseFiles]),
 * which keep the exact shape Jackson wrote so a data dir survives an upgrade either way.
 */
object CliJson {
    /**
     * Files whose classes Jackson wrote in full: every field, nulls included, in declaration
     * order. Unknown keys are skipped and a `null` where a default exists reads as the default.
     */
    val files =
        Json {
            encodeDefaults = true
            ignoreUnknownKeys = true
            coerceInputValues = true
        }

    /** Files whose classes Jackson wrote with `@JsonInclude(NON_NULL)`: absent nulls. */
    val sparseFiles =
        Json(files) {
            explicitNulls = false
        }

    /** Compact output, the `--json` line format. */
    private val output = Json

    /** A JSON text as a tree, for embedding parsed JSON (an event, a profile's content) in a result. */
    fun parse(json: String): JsonElement = Json.parseToJsonElement(json)

    /** [value] as one line of JSON. */
    fun encode(value: Any?): String = output.encodeToString(JsonElement.serializer(), toJsonElement(value))

    /**
     * The JSON for what commands put in their results: maps (keys by `toString`), lists, arrays
     * and sequences, strings, numbers, booleans, enums (by name), byte arrays (Base64, as Jackson
     * wrote them), files, URIs and UUIDs (as text) and already-parsed [JsonElement]s.
     *
     * Strings escape as kotlinx does, which differs from Jackson only in writing a control char's
     * `\u001f` escape in lowercase hex.
     */
    fun toJsonElement(value: Any?): JsonElement =
        when (value) {
            null -> JsonNull
            is JsonElement -> value
            is String -> JsonPrimitive(value)
            is Boolean -> JsonPrimitive(value)
            is Int, is Long, is Short, is Byte -> JsonPrimitive((value as Number).toLong())
            is Number -> JsonPrimitive(value)
            is Char -> JsonPrimitive(value.toString())
            is Enum<*> -> JsonPrimitive(value.name)
            is ByteArray -> JsonPrimitive(Base64.getEncoder().encodeToString(value))
            is Map<*, *> -> JsonObject(value.entries.associate { (k, v) -> k.toString() to toJsonElement(v) })
            is Iterable<*> -> JsonArray(value.map { toJsonElement(it) })
            is Array<*> -> JsonArray(value.map { toJsonElement(it) })
            is IntArray -> JsonArray(value.map { JsonPrimitive(it.toLong()) })
            is LongArray -> JsonArray(value.map { JsonPrimitive(it) })
            is Sequence<*> -> JsonArray(value.map { toJsonElement(it) }.toList())
            is File, is UUID, is URI -> JsonPrimitive(value.toString())
            // Anything else is a bug in the command that built the result. Failing beats writing
            // the object's toString(), which for a data class can include a key it holds.
            else -> throw IllegalArgumentException("amy cannot write a ${value::class.java.name} as JSON; put a map, list or scalar in the result")
        }

    /**
     * A parsed tree back into the plain values the text renderer walks: maps, lists, strings,
     * longs, doubles, booleans and nulls.
     */
    fun toPlain(value: JsonElement): Any? =
        when (value) {
            is JsonNull -> null
            is JsonObject -> value.mapValues { toPlain(it.value) }
            is JsonArray -> value.map { toPlain(it) }
            is JsonPrimitive ->
                when {
                    value.isString -> value.content
                    else -> value.booleanOrNull ?: value.longOrNull ?: bigOrDouble(value.content)
                }
        }

    private fun bigOrDouble(text: String): Any =
        text.toBigIntegerOrNull()?.let { if (it.bitLength() < 64) it.toLong() else it }
            ?: text.toDoubleOrNull()
            ?: text
}

private fun String.toBigIntegerOrNull(): BigInteger? = if (none { it == '.' || it == 'e' || it == 'E' }) runCatching { BigInteger(this) }.getOrNull() else null
