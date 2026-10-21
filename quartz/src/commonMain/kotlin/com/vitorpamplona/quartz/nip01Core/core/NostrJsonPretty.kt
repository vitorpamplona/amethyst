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
package com.vitorpamplona.quartz.nip01Core.core

/**
 * Human-readable JSON in the layout Quartz has always shown events in: two-space indentation,
 * `"key": value`, one array element per line for the top levels, and anything nested three or
 * more containers deep (a tag inside an event's `tags`) kept on one line as `["e", "…"]`.
 *
 * It re-indents JSON text rather than re-serialising a tree, so strings and numbers come out
 * exactly as they went in. That matches the Jackson pretty printer it replaces
 * (`InliningTagArrayPrettyPrinter`) for every JSON Quartz writes itself; for arbitrary input it
 * only differs where Jackson would have rewritten an escape or a number. Lines end in `\n` on
 * every platform.
 */
object NostrJsonPretty {
    private const val INDENT = "  "

    /** [json] (any valid JSON text) re-indented; whitespace between tokens is dropped first. */
    fun format(json: String): String {
        val out = StringBuilder(json.length + json.length / 4)
        // Per open container: whether it is an object, and how many values or entries it holds.
        val isObject = BooleanArray(MAX_DEPTH)
        val counts = IntArray(MAX_DEPTH)
        // Inside an object, whether the next string is a key.
        val expectingKey = BooleanArray(MAX_DEPTH)
        var depth = 0
        var i = 0
        val n = json.length

        fun newline(level: Int) {
            out.append('\n')
            repeat(level) { out.append(INDENT) }
        }

        // Called before the first char of a value (or of a key, in an object).
        fun beforeValue() {
            if (depth == 0) return
            val d = depth - 1
            if (isObject[d]) {
                if (!expectingKey[d]) return
                expectingKey[d] = false
                if (counts[d] == 0) newline(depth)
                counts[d]++
            } else {
                if (counts[d] == 0 && depth < 3) newline(depth)
                counts[d]++
            }
        }

        while (i < n) {
            val c = json[i]
            when (c) {
                ' ', '\t', '\n', '\r' -> {
                    i++
                }

                '"' -> {
                    beforeValue()
                    val start = i
                    i++
                    while (i < n) {
                        val s = json[i]
                        if (s == '\\') {
                            i += 2
                        } else {
                            i++
                            if (s == '"') break
                        }
                    }
                    out.append(json, start, minOf(i, n))
                }

                '{', '[' -> {
                    beforeValue()
                    require(depth < MAX_DEPTH) { "JSON nested too deeply to format" }
                    isObject[depth] = c == '{'
                    counts[depth] = 0
                    expectingKey[depth] = c == '{'
                    depth++
                    out.append(c)
                    i++
                }

                '}' -> {
                    depth--
                    if (counts[depth] > 0) newline(depth) else out.append(' ')
                    out.append('}')
                    i++
                }

                ']' -> {
                    depth--
                    if (counts[depth] > 0 && depth < 2) newline(depth)
                    out.append(']')
                    i++
                }

                ':' -> {
                    out.append(": ")
                    i++
                }

                ',' -> {
                    out.append(',')
                    val d = depth - 1
                    if (isObject[d]) {
                        expectingKey[d] = true
                        newline(depth)
                    } else if (depth < 3) {
                        newline(depth)
                    } else {
                        out.append(' ')
                    }
                    i++
                }

                else -> {
                    // A number or a literal: copy up to the next structural char or whitespace.
                    beforeValue()
                    val start = i
                    while (i < n && json[i] !in STOPS) i++
                    out.append(json, start, i)
                }
            }
        }
        return out.toString()
    }

    private const val MAX_DEPTH = 1000
    private const val STOPS = ",:]} \t\n\r"
}
