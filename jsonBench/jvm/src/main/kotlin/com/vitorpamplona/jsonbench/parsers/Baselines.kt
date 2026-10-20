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
package com.vitorpamplona.jsonbench.parsers

import com.vitorpamplona.jsonbench.RawBytesInputParser
import com.vitorpamplona.jsonbench.StringInputParser

/** What `bytes -> String` costs: add it to a string parser fed from the socket's bytes. */
class Utf8Decode : RawBytesInputParser() {
    override fun decode(input: ByteArray) = null

    override fun parseAll(inputs: Array<ByteArray>): Long {
        var sink = 0L
        for (b in inputs) sink += String(b, Charsets.UTF_8).length
        return sink
    }
}

/** What `String -> bytes` costs: add it to a byte parser fed from an app's String. */
class Utf8Encode : StringInputParser() {
    override fun decode(input: String) = null

    override fun parseAll(inputs: Array<String>): Long {
        var sink = 0L
        for (s in inputs) sink += s.encodeToByteArray().size
        return sink
    }
}
