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
package com.vitorpamplona.jsonbench

/**
 * The plain event every `typed` parser without a model of its own decodes into: owned
 * Strings, primitive numbers and the same `Array<Array<String>>` tag layout Quartz uses
 * (`TagArray`), so the only difference from Quartz's own path is the parser.
 */
class Event(
    val id: String,
    val pubKey: String,
    val createdAt: Long,
    val kind: Int,
    val tags: Array<Array<String>>,
    val content: String,
    val sig: String,
)

val EMPTY_TAGS: Array<Array<String>> = emptyArray()

/** Collects tags without knowing their count up front, then hands out exact-size arrays. */
class TagCollector {
    private val tags = ArrayList<Array<String>>(16)
    private val values = ArrayList<String>(8)

    fun startTag() = values.clear()

    fun addValue(value: String) {
        values.add(value)
    }

    fun endTag() {
        tags.add(values.toTypedArray())
    }

    fun finish(): Array<Array<String>> {
        if (tags.isEmpty()) return EMPTY_TAGS
        val result = tags.toTypedArray()
        tags.clear()
        return result
    }
}
