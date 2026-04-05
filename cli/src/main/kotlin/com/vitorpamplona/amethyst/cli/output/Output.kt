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
package com.vitorpamplona.amethyst.cli.output

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

object Output {
    private val json =
        Json {
            prettyPrint = true
            encodeDefaults = true
        }

    var jsonMode = false

    fun success(
        message: String,
        data: JsonElement? = null,
    ) {
        if (jsonMode) {
            val obj =
                buildJsonObject {
                    put("status", "ok")
                    put("message", message)
                    if (data != null) {
                        put("data", data)
                    }
                }
            println(json.encodeToString(obj))
        } else {
            println(message)
        }
    }

    fun error(message: String) {
        if (jsonMode) {
            val obj =
                buildJsonObject {
                    put("status", "error")
                    put("message", message)
                }
            println(json.encodeToString(obj))
        } else {
            System.err.println("Error: $message")
        }
    }

    fun data(obj: JsonElement) {
        println(json.encodeToString(obj))
    }

    fun table(
        headers: List<String>,
        rows: List<List<String>>,
    ) {
        if (jsonMode) {
            val arr =
                buildJsonArray {
                    for (row in rows) {
                        add(
                            buildJsonObject {
                                headers.zip(row).forEach { (h, v) ->
                                    put(h, v)
                                }
                            },
                        )
                    }
                }
            println(json.encodeToString(arr))
        } else {
            if (rows.isEmpty()) {
                println("(empty)")
                return
            }
            val widths =
                headers.indices.map { i ->
                    maxOf(headers[i].length, rows.maxOf { it.getOrElse(i) { "" }.length })
                }
            val header = headers.mapIndexed { i, h -> h.padEnd(widths[i]) }.joinToString("  ")
            val separator = widths.joinToString("  ") { "-".repeat(it) }
            println(header)
            println(separator)
            rows.forEach { row ->
                val line = row.mapIndexed { i, v -> v.padEnd(widths.getOrElse(i) { v.length }) }.joinToString("  ")
                println(line)
            }
        }
    }

    fun keyValue(pairs: List<Pair<String, String>>) {
        if (jsonMode) {
            val obj =
                buildJsonObject {
                    pairs.forEach { (k, v) -> put(k, v) }
                }
            println(json.encodeToString(obj))
        } else {
            val maxKey = pairs.maxOfOrNull { it.first.length } ?: 0
            pairs.forEach { (k, v) ->
                println("${k.padEnd(maxKey)}  $v")
            }
        }
    }
}
