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

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper

/**
 * Stdout/stderr emitter for amy.
 *
 * Default mode is human-readable text. Pass `--json` on the command line
 * to switch to the machine contract: a single JSON object on stdout per
 * successful command, JSON `{"error": ..., "detail": ...}` on stderr for
 * failures. The JSON shape is the public API — the text shape is not.
 */
object Output {
    enum class Mode { TEXT, JSON }

    @Volatile var mode: Mode = Mode.TEXT

    val mapper: ObjectMapper = jacksonObjectMapper()

    fun emit(result: Any?) {
        when (mode) {
            Mode.JSON -> println(mapper.writeValueAsString(result))
            Mode.TEXT -> println(renderText(result))
        }
    }

    fun error(
        code: String,
        detail: String? = null,
    ): Int {
        when (mode) {
            Mode.JSON -> {
                val payload = mutableMapOf<String, Any>("error" to code)
                if (detail != null) payload["detail"] = detail
                System.err.println(mapper.writeValueAsString(payload))
            }

            Mode.TEXT -> {
                System.err.println(if (detail != null) "error: $code: $detail" else "error: $code")
            }
        }
        return 1
    }

    private fun renderText(value: Any?): String {
        val out = StringBuilder()
        when (value) {
            null -> {}

            is Map<*, *> -> {
                renderMapBody(out, value, "")
            }

            is List<*> -> {
                renderListBody(out, value, "")
            }

            else -> {
                out.append(value.toString()).append('\n')
            }
        }
        return out.toString().trimEnd('\n')
    }

    private fun renderMapBody(
        out: StringBuilder,
        map: Map<*, *>,
        prefix: String,
    ) {
        for ((k, v) in map) {
            if (v == null) continue
            val key = k.toString()
            when (v) {
                is Map<*, *> -> {
                    if (v.isEmpty()) {
                        out.append(prefix).append(key).append(": {}\n")
                    } else {
                        out.append(prefix).append(key).append(":\n")
                        renderMapBody(out, v, "$prefix  ")
                    }
                }

                is List<*> -> {
                    if (v.isEmpty()) {
                        out.append(prefix).append(key).append(": []\n")
                    } else {
                        out.append(prefix).append(key).append(":\n")
                        renderListBody(out, v, "$prefix  ")
                    }
                }

                else -> {
                    out
                        .append(prefix)
                        .append(key)
                        .append(": ")
                        .append(v.toString())
                        .append('\n')
                }
            }
        }
    }

    private fun renderListBody(
        out: StringBuilder,
        list: List<*>,
        prefix: String,
    ) {
        for (item in list) {
            when (item) {
                null -> {}

                is Map<*, *> -> {
                    val entries = item.entries.filter { it.value != null }
                    if (entries.isEmpty()) {
                        out.append(prefix).append("- {}\n")
                        continue
                    }
                    val first = entries.first()
                    val firstV = first.value
                    if (firstV is Map<*, *> || firstV is List<*>) {
                        out.append(prefix).append("-\n")
                        renderMapBody(out, item, "$prefix  ")
                    } else {
                        out
                            .append(prefix)
                            .append("- ")
                            .append(first.key)
                            .append(": ")
                            .append(firstV.toString())
                            .append('\n')
                        for ((rk, rv) in entries.drop(1)) {
                            when (rv) {
                                is Map<*, *> -> {
                                    if (rv.isEmpty()) {
                                        out.append("$prefix  ").append(rk).append(": {}\n")
                                    } else {
                                        out.append("$prefix  ").append(rk).append(":\n")
                                        renderMapBody(out, rv, "$prefix    ")
                                    }
                                }

                                is List<*> -> {
                                    if (rv.isEmpty()) {
                                        out.append("$prefix  ").append(rk).append(": []\n")
                                    } else {
                                        out.append("$prefix  ").append(rk).append(":\n")
                                        renderListBody(out, rv, "$prefix    ")
                                    }
                                }

                                else -> {
                                    out
                                        .append("$prefix  ")
                                        .append(rk)
                                        .append(": ")
                                        .append(rv.toString())
                                        .append('\n')
                                }
                            }
                        }
                    }
                }

                is List<*> -> {
                    out.append(prefix).append("-\n")
                    renderListBody(out, item, "$prefix  ")
                }

                else -> {
                    out
                        .append(prefix)
                        .append("- ")
                        .append(item.toString())
                        .append('\n')
                }
            }
        }
    }
}
