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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.embed

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull

/**
 * Lenient accessors over the IME bridge's JSON envelopes.
 *
 * The page and the host exchange loosely-shaped messages — a field that is
 * absent, null, or the wrong type must fall back rather than throw, because the
 * other end is a web page and a malformed message must not take the keyboard
 * down. That is exactly `org.json`'s `opt*` contract, which this reproduces on
 * kotlinx.serialization.
 *
 * kotlinx.serialization rather than `org.json` because it is multiplatform:
 * Android bundles `org.json` and no other target has it, so every use was a
 * platform dependency for no reason. (The obvious substitute, the
 * `org.json:json` artifact, carries the non-OSI JSON License that Debian,
 * Fedora and the ASF all reject, so it was never an option either.)
 */
internal val imeJson =
    Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

/** Parses one envelope, or null when the payload is not a JSON object at all. */
internal fun parseImeEnvelope(payload: String): JsonObject? = runCatching { imeJson.parseToJsonElement(payload) as? JsonObject }.getOrNull()

internal fun JsonObject.has(key: String): Boolean = this[key] is JsonPrimitive || this[key] is JsonObject

private fun JsonObject.primitive(key: String): JsonPrimitive? = this[key] as? JsonPrimitive

/** Numbers, and numeric strings, as `org.json` accepts both. */
internal fun JsonObject.optDouble(
    key: String,
    fallback: Double = 0.0,
): Double = primitive(key)?.let { it.doubleOrNull ?: it.contentOrNull?.toDoubleOrNull() } ?: fallback

internal fun JsonObject.optInt(
    key: String,
    fallback: Int = 0,
): Int = primitive(key)?.let { it.intOrNull ?: it.contentOrNull?.toIntOrNull() } ?: fallback

internal fun JsonObject.optBoolean(
    key: String,
    fallback: Boolean = false,
): Boolean = primitive(key)?.let { it.booleanOrNull ?: it.contentOrNull?.toBooleanStrictOrNull() } ?: fallback

/** `contentOrNull` is null for JSON null, which is what the fallback is for. */
internal fun JsonObject.optString(
    key: String,
    fallback: String = "",
): String = primitive(key)?.contentOrNull ?: fallback

internal fun JsonObject.optObject(key: String): JsonObject? = this[key] as? JsonObject
