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

import com.vitorpamplona.jsonbench.Event
import com.vitorpamplona.jsonbench.StringInputParser
import java.util.function.Function
import java.util.function.ToLongFunction

/**
 * jsoniter-scala's macro-generated codec. The codec and its timed loop are Scala
 * (src/main/scala/.../JsoniterScalaParser.scala); this class only bridges to it. It reads the
 * frame's String with jsoniter-scala's own `readFromString`.
 */
class JsoniterScala : StringInputParser() {
    private val impl: Any =
        Class
            .forName("com.vitorpamplona.jsonbench.scalacodec.JsoniterScalaParser")
            .getDeclaredConstructor()
            .newInstance()

    @Suppress("UNCHECKED_CAST")
    private val loop = impl as ToLongFunction<Array<String>>

    @Suppress("UNCHECKED_CAST")
    private val one = impl as Function<String, Array<Any>>

    @Suppress("UNCHECKED_CAST")
    override fun decode(input: String): Event {
        val f = one.apply(input)
        return Event(
            f[0] as String,
            f[1] as String,
            f[2] as Long,
            f[3] as Int,
            f[4] as Array<Array<String>>,
            f[5] as String,
            f[6] as String,
        )
    }

    override fun parseAll(inputs: Array<String>): Long = loop.applyAsLong(inputs)
}
