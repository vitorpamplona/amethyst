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
package com.vitorpamplona.amethyst.commons.model.accountLifecycle

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.jsonPrimitive

internal object StrictLifecycleJson {
    fun parse(raw: String): JsonElement {
        DuplicateKeyScanner(raw).scan()
        return Json.parseToJsonElement(raw)
    }

    private class DuplicateKeyScanner(
        private val raw: String,
    ) {
        private var index = 0

        fun scan() {
            scanValue(0)
            skipWhitespace()
            require(index == raw.length) { "trailing JSON data" }
        }

        private fun scanValue(depth: Int) {
            require(depth <= 64) { "JSON nesting is too deep" }
            skipWhitespace()
            require(index < raw.length) { "missing JSON value" }
            when (raw[index]) {
                '{' -> scanObject(depth + 1)
                '[' -> scanArray(depth + 1)
                '"' -> scanString()
                else -> scanPrimitive()
            }
        }

        private fun scanObject(depth: Int) {
            index++
            skipWhitespace()
            if (consume('}')) return
            val keys = mutableSetOf<String>()
            while (true) {
                skipWhitespace()
                val key = scanString()
                require(keys.add(key)) { "duplicate JSON key" }
                skipWhitespace()
                require(consume(':')) { "missing object colon" }
                scanValue(depth)
                skipWhitespace()
                if (consume('}')) return
                require(consume(',')) { "missing object comma" }
            }
        }

        private fun scanArray(depth: Int) {
            index++
            skipWhitespace()
            if (consume(']')) return
            while (true) {
                scanValue(depth)
                skipWhitespace()
                if (consume(']')) return
                require(consume(',')) { "missing array comma" }
            }
        }

        private fun scanString(): String {
            val start = index
            require(index < raw.length && raw[index] == '"') { "expected JSON string" }
            index++
            var escaped = false
            while (index < raw.length) {
                val char = raw[index++]
                if (escaped) {
                    escaped = false
                } else if (char == '\\') {
                    escaped = true
                } else if (char == '"') {
                    val encoded = raw.substring(start, index)
                    return Json.parseToJsonElement(encoded).jsonPrimitive.content
                } else if (char < ' ') {
                    throw IllegalArgumentException("unescaped JSON control character")
                }
            }
            throw IllegalArgumentException("unterminated JSON string")
        }

        private fun scanPrimitive() {
            val start = index
            while (index < raw.length && raw[index] !in ",]} \t\r\n") index++
            require(index > start) { "missing JSON primitive" }
        }

        private fun skipWhitespace() {
            while (index < raw.length && raw[index] in " \t\r\n") index++
        }

        private fun consume(expected: Char): Boolean {
            if (index >= raw.length || raw[index] != expected) return false
            index++
            return true
        }
    }
}
