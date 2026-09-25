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
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull

/**
 * Scalar values on the SQL wire: `String`, `Long`, `Double`, `Boolean`
 * (parameters only) and `null`. Shared by the kotlinx serializers; the
 * Jackson ones mirror these rules.
 */
object SqlJsonValues {
    /** JSON has no Infinity/NaN; SQLite spells them `Inf` / `-Inf` when it casts to text. */
    fun nonFiniteText(d: Double) =
        when {
            d.isNaN() -> "NaN"
            d > 0 -> "Inf"
            else -> "-Inf"
        }

    fun toElement(v: Any?): JsonElement =
        when (v) {
            null -> JsonNull
            is String -> JsonPrimitive(v)
            is Long -> JsonPrimitive(v)
            is Int -> JsonPrimitive(v)
            is Double -> if (v.isFinite()) JsonPrimitive(v) else JsonPrimitive(nonFiniteText(v))
            is Boolean -> JsonPrimitive(v)
            else -> JsonPrimitive(v.toString())
        }

    fun fromElement(e: JsonElement): Any? =
        when (e) {
            is JsonNull -> null
            is JsonPrimitive -> if (e.isString) e.content else e.booleanOrNull ?: e.longOrNull ?: e.doubleOrNull ?: e.content
            else -> throw IllegalArgumentException("SQL values must be strings, numbers, booleans or null")
        }

    fun optionsToElement(cmd: SqlCmd): JsonObject? {
        if (cmd.params.isEmpty() && cmd.named.isEmpty() && cmd.pageSize == null) return null
        return buildJsonObject {
            if (cmd.named.isNotEmpty()) {
                put("params", buildJsonObject { cmd.named.forEach { (k, v) -> put(k, toElement(v)) } })
            } else if (cmd.params.isNotEmpty()) {
                put("params", buildJsonArray { cmd.params.forEach { add(toElement(it)) } })
            }
            cmd.pageSize?.let { put("page", JsonPrimitive(it)) }
        }
    }

    fun sqlCmd(
        queryId: String,
        sql: String,
        options: JsonObject?,
    ): SqlCmd {
        val params = options?.get("params")
        return SqlCmd(
            queryId = queryId,
            sql = sql,
            params = (params as? JsonArray)?.map { fromElement(it) } ?: emptyList(),
            named = (params as? JsonObject)?.mapValues { fromElement(it.value) } ?: emptyMap(),
            pageSize = (options?.get("page") as? JsonPrimitive)?.intOrNull,
        )
    }

    fun rowsToElement(rows: List<List<Any?>>): JsonArray =
        buildJsonArray {
            rows.forEach { row -> add(buildJsonArray { row.forEach { add(toElement(it)) } }) }
        }

    fun rowsFromElement(e: JsonElement): List<List<Any?>> = (e as JsonArray).map { row -> (row as JsonArray).map { fromElement(it) } }
}
