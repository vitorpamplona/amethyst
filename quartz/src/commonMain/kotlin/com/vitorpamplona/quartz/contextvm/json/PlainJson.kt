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
package com.vitorpamplona.quartz.contextvm.json

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.longOrNull

/**
 * Converts a kotlinx JSON tree into the plain Kotlin types
 * `JsonCanonicalization` works on.
 *
 * The canonicalizer deliberately takes plain types so it stays usable from any
 * module regardless of serializer, and this is the bridge for our side.
 *
 * Numbers: an integral value becomes a [Long] and anything else a [Double].
 * Either way the canonicalizer renders it through the same ECMAScript path, so
 * the distinction does not change the output — it just avoids widening large
 * integers through [Double] any earlier than JCS already does.
 */
fun JsonElement.toPlainJson(): Any? =
    when (this) {
        is JsonNull -> null
        is JsonObject -> mapValues { (_, value) -> value.toPlainJson() }
        is JsonArray -> map { it.toPlainJson() }
        is JsonPrimitive -> {
            if (isString) {
                content
            } else {
                booleanOrNull
                    ?: longOrNull
                    ?: doubleOrNull
                    ?: throw IllegalArgumentException("unsupported JSON primitive: $content")
            }
        }
    }
