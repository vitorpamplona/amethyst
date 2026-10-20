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
import com.vitorpamplona.quartz.nip01Core.jackson.JacksonMapper
import com.vitorpamplona.quartz.nip01Core.kotlinSerialization.KotlinSerializationMapper
import com.vitorpamplona.quartz.nip01Core.core.Event as QuartzEvent

private fun QuartzEvent.toBench() = Event(id, pubKey, createdAt, kind, tags, content, sig)

/** The production path: Quartz's EventDeserializer on jackson-databind, interning + EventFactory. */
class QuartzJackson : StringInputParser() {
    override fun decode(input: String) = JacksonMapper.fromJson(input).toBench()

    override fun keep(input: String): Any = JacksonMapper.fromJson(input)

    override fun parseAll(inputs: Array<String>): Long {
        var sink = 0L
        for (s in inputs) {
            val e = JacksonMapper.fromJson(s)
            sink += e.kind + e.tags.size + e.content.length
        }
        return sink
    }
}

/** Quartz's kotlinx.serialization path (the one native/iOS targets use). */
class QuartzKotlinx : StringInputParser() {
    override fun decode(input: String) = KotlinSerializationMapper.fromJson(input).toBench()

    override fun keep(input: String): Any = KotlinSerializationMapper.fromJson(input)

    override fun parseAll(inputs: Array<String>): Long {
        var sink = 0L
        for (s in inputs) {
            val e = KotlinSerializationMapper.fromJson(s)
            sink += e.kind + e.tags.size + e.content.length
        }
        return sink
    }
}
