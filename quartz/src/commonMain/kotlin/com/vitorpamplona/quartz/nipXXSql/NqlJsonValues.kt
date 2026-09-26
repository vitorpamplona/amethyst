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
package com.vitorpamplona.quartz.nipXXSql

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive

/**
 * NQL values on the wire, for the kotlinx serializers (the Jackson ones
 * mirror these rules). A JSON number without a fraction or exponent that fits
 * in 64 bits is an INTEGER (`Long`); any other number is a REAL (`Double`).
 */
object NqlJsonValues {
    fun toElement(v: Any?): JsonElement =
        when (v) {
            null -> JsonNull
            is String -> JsonPrimitive(v)
            is Long -> JsonPrimitive(v)
            is Int -> JsonPrimitive(v)
            is Double -> JsonPrimitive(v)
            is Boolean -> JsonPrimitive(v)
            else -> JsonPrimitive(v.toString())
        }

    /** A parameter's value: its type comes from its JSON form. */
    fun fromElement(e: JsonElement): Any? =
        when (e) {
            is JsonNull -> {
                null
            }

            is JsonPrimitive -> {
                if (e.isString) {
                    e.content
                } else {
                    e.booleanOrNull ?: number(e.content)
                }
            }

            else -> {
                throw IllegalArgumentException("NQL values are strings, numbers, booleans or null")
            }
        }

    fun number(text: String): Any = if (text.any { it == '.' || it == 'e' || it == 'E' }) text.toDouble() else text.toLongOrNull() ?: text.toDouble()

    /** A result value read as its column's [type]. */
    fun fromElement(
        e: JsonElement,
        type: NqlType,
    ): Any? {
        if (e is JsonNull) return null
        val p = e.jsonPrimitive
        return when (type) {
            NqlType.INTEGER -> p.content.toLong()
            NqlType.REAL -> p.content.toDouble()
            NqlType.BOOLEAN -> p.booleanOrNull
            else -> p.content
        }
    }

    fun paramsToElement(params: List<Any?>): JsonArray = buildJsonArray { params.forEach { add(toElement(it)) } }

    fun resultToElement(r: NqlResult): JsonObject =
        buildJsonObject {
            put(
                "columns",
                buildJsonArray {
                    r.columns.forEach { c ->
                        add(
                            buildJsonArray {
                                add(JsonPrimitive(c.name))
                                add(JsonPrimitive(c.type.name))
                            },
                        )
                    }
                },
            )
            put("rows", buildJsonArray { r.rows.forEach { row -> add(buildJsonArray { row.forEach { add(toElement(it)) } }) } })
            put("truncated", JsonPrimitive(r.truncated))
        }

    fun resultFromElement(e: JsonElement): NqlResult {
        val o = e as JsonObject
        val columns = o["columns"]!!.jsonArray.map { c -> NqlColumn(c.jsonArray[0].jsonPrimitive.content, NqlType.valueOf(c.jsonArray[1].jsonPrimitive.content)) }
        val rows = o["rows"]!!.jsonArray.map { row -> row.jsonArray.mapIndexed { i, v -> fromElement(v, columns[i].type) } }
        return NqlResult(columns, rows, o["truncated"]?.jsonPrimitive?.booleanOrNull ?: false)
    }
}
