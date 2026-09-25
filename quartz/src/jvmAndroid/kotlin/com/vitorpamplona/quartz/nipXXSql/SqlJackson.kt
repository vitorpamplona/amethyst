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

import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.core.JsonToken
import com.fasterxml.jackson.databind.JsonNode

/**
 * Jackson side of the SQL frames. Same value rules as [SqlJsonValues]:
 * strings, integers (as `Long`), reals (as `Double`), booleans, null.
 */
object SqlJackson {
    fun writeValue(
        v: Any?,
        gen: JsonGenerator,
    ) {
        when (v) {
            null -> gen.writeNull()
            is String -> gen.writeString(v)
            is Long -> gen.writeNumber(v)
            is Int -> gen.writeNumber(v)
            is Double -> if (v.isFinite()) gen.writeNumber(v) else gen.writeString(SqlJsonValues.nonFiniteText(v))
            is Boolean -> gen.writeBoolean(v)
            else -> gen.writeString(v.toString())
        }
    }

    fun writeOptions(
        cmd: SqlCmd,
        gen: JsonGenerator,
    ) {
        if (cmd.params.isEmpty() && cmd.named.isEmpty() && cmd.pageSize == null) return
        gen.writeStartObject()
        if (cmd.named.isNotEmpty()) {
            gen.writeObjectFieldStart("params")
            cmd.named.forEach { (k, v) ->
                gen.writeFieldName(k)
                writeValue(v, gen)
            }
            gen.writeEndObject()
        } else if (cmd.params.isNotEmpty()) {
            gen.writeArrayFieldStart("params")
            cmd.params.forEach { writeValue(it, gen) }
            gen.writeEndArray()
        }
        cmd.pageSize?.let { gen.writeNumberField("page", it) }
        gen.writeEndObject()
    }

    fun writeRows(
        rows: List<List<Any?>>,
        gen: JsonGenerator,
    ) {
        gen.writeStartArray()
        rows.forEach { row ->
            gen.writeStartArray()
            row.forEach { writeValue(it, gen) }
            gen.writeEndArray()
        }
        gen.writeEndArray()
    }

    fun valueOf(node: JsonNode): Any? =
        when {
            node.isNull -> null
            node.isTextual -> node.textValue()
            node.isBoolean -> node.booleanValue()
            node.isIntegralNumber && node.canConvertToLong() -> node.longValue()
            node.isNumber -> node.doubleValue()
            else -> throw IllegalArgumentException("SQL values must be strings, numbers, booleans or null")
        }

    /** Reads `<queryId>, <sql>, <options>?` after the `"SQL"` label. */
    fun readSqlCmd(jp: JsonParser): SqlCmd {
        val queryId = jp.nextTextValue()
        val sql = jp.nextTextValue()
        var params: List<Any?> = emptyList()
        var named: Map<String, Any?> = emptyMap()
        var page: Int? = null
        if (jp.nextToken() == JsonToken.START_OBJECT) {
            val options = jp.readValueAsTree<JsonNode>()
            val p = options.get("params")
            if (p != null && p.isArray) params = p.map { valueOf(it) }
            if (p != null && p.isObject) named = p.properties().associate { it.key to valueOf(it.value) }
            options.get("page")?.takeIf { it.canConvertToInt() }?.let { page = it.intValue() }
        }
        return SqlCmd(queryId, sql, params, named, page)
    }

    /** Reads `<queryId>, [<name>, …]` after the `"SQL-COLS"` label. */
    fun readCols(jp: JsonParser): SqlColsMessage {
        val queryId = jp.nextTextValue()
        val columns = ArrayList<String>()
        if (jp.nextToken() == JsonToken.START_ARRAY) {
            while (jp.nextToken() != JsonToken.END_ARRAY) columns.add(jp.text)
            // Step off the inner array so the caller's drain loop sees the outer one.
            jp.nextToken()
        }
        return SqlColsMessage(queryId, columns)
    }

    /** Reads `<queryId>, [[…], …], "more" | "done"` after the `"SQL-ROWS"` label. */
    fun readRows(jp: JsonParser): SqlRowsMessage {
        val queryId = jp.nextTextValue()
        val rows =
            if (jp.nextToken() == JsonToken.START_ARRAY) {
                jp.readValueAsTree<JsonNode>().map { row -> row.map { valueOf(it) } }
            } else {
                emptyList()
            }
        val status = jp.nextTextValue()
        return SqlRowsMessage(queryId, rows, status == SqlRowsMessage.DONE)
    }
}
