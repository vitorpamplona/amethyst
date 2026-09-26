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

/** Jackson side of the `NQL` frames, with [NqlJsonValues]'s value rules. */
object NqlJackson {
    fun writeValue(
        v: Any?,
        gen: JsonGenerator,
    ) {
        when (v) {
            null -> gen.writeNull()
            is String -> gen.writeString(v)
            is Long -> gen.writeNumber(v)
            is Int -> gen.writeNumber(v)
            is Double -> gen.writeNumber(v)
            is Boolean -> gen.writeBoolean(v)
            else -> gen.writeString(v.toString())
        }
    }

    fun writeParams(
        params: List<Any?>,
        gen: JsonGenerator,
    ) {
        gen.writeStartArray()
        params.forEach { writeValue(it, gen) }
        gen.writeEndArray()
    }

    fun writeResult(
        r: NqlResult,
        gen: JsonGenerator,
    ) {
        gen.writeStartObject()
        gen.writeArrayFieldStart("columns")
        r.columns.forEach {
            gen.writeStartArray()
            gen.writeString(it.name)
            gen.writeString(it.type.name)
            gen.writeEndArray()
        }
        gen.writeEndArray()
        gen.writeArrayFieldStart("rows")
        r.rows.forEach { row ->
            gen.writeStartArray()
            row.forEach { writeValue(it, gen) }
            gen.writeEndArray()
        }
        gen.writeEndArray()
        gen.writeBooleanField("truncated", r.truncated)
        gen.writeEndObject()
    }

    /** A parameter's value: its type comes from its JSON form. */
    fun paramOf(node: JsonNode): Any? =
        when {
            node.isNull -> null
            node.isTextual -> node.textValue()
            node.isBoolean -> node.booleanValue()
            node.isIntegralNumber && node.canConvertToLong() -> node.longValue()
            node.isNumber -> node.doubleValue()
            else -> throw IllegalArgumentException("NQL values are strings, numbers, booleans or null")
        }

    private fun valueOf(
        node: JsonNode,
        type: NqlType,
    ): Any? =
        when {
            node.isNull -> null
            type == NqlType.INTEGER -> node.longValue()
            type == NqlType.REAL -> node.doubleValue()
            type == NqlType.BOOLEAN -> node.booleanValue()
            else -> node.asText()
        }

    /** Reads `<queryId>, <query>, <params>?` after the `"NQL"` label. */
    fun readCmd(jp: JsonParser): NqlCmd {
        val queryId = jp.nextTextValue()
        val query = jp.nextTextValue()
        var params: List<Any?> = emptyList()
        if (jp.nextToken() == JsonToken.START_ARRAY) {
            params = jp.readValueAsTree<JsonNode>().map { paramOf(it) }
            // Step off the params array so the caller's drain loop sees the frame's end.
            jp.nextToken()
        }
        return NqlCmd(queryId, query, params)
    }

    /** Reads `<queryId>, {columns, rows, truncated}` after the `"NQL"` label. */
    fun readResult(jp: JsonParser): NqlResultMessage {
        val queryId = jp.nextTextValue()
        jp.nextToken()
        val node = jp.readValueAsTree<JsonNode>()
        val columns = node.get("columns").map { NqlColumn(it.get(0).asText(), NqlType.valueOf(it.get(1).asText())) }
        val rows = node.get("rows").map { row -> row.mapIndexed { i, v -> valueOf(v, columns[i].type) } }
        return NqlResultMessage(queryId, NqlResult(columns, rows, node.get("truncated")?.asBoolean() ?: false))
    }
}
