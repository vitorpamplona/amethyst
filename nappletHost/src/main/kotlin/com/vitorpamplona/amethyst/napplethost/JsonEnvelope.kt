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
package com.vitorpamplona.amethyst.napplethost

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull

// Tree-level JSON helpers (kotlinx.serialization) for the ad-hoc bridge/broker envelopes this
// process relays ({type, id, ...}). The envelopes come from untrusted pages, so every accessor
// is total: absent/mistyped fields degrade to the empty/false default instead of throwing.

/** Parses [raw] as a JSON object, or null when it is malformed or not an object. */
internal fun parseJsonObject(raw: String): JsonObject? = runCatching { Json.parseToJsonElement(raw) as? JsonObject }.getOrNull()

/** The value at [key] rendered as a string, or "" when absent, null, or not a primitive. */
internal fun JsonObject.stringOrEmpty(key: String): String = (this[key] as? JsonPrimitive)?.contentOrNull ?: ""

/** The value at [key] as a boolean, or false when absent or not a boolean. */
internal fun JsonObject.booleanOrFalse(key: String): Boolean = (this[key] as? JsonPrimitive)?.booleanOrNull ?: false

/** A copy of this envelope with [key] set to [value] ([JsonObject] is immutable). */
internal fun JsonObject.withString(
    key: String,
    value: String,
): JsonObject = JsonObject(this + (key to JsonPrimitive(value)))
