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
import com.vitorpamplona.jsonbench.Padded
import com.vitorpamplona.jsonbench.PaddedInputParser
import org.simdjson.JsonValue
import org.simdjson.SimdJsonParser
import org.simdjson.annotations.JsonFieldName

/**
 * simdjson-java's tape DOM. The returned [JsonValue] points into the parser's reusable tape
 * and is invalidated by the next parse, so an app would still have to copy what it keeps.
 * Inputs carry 64 spare bytes so the parser does not copy them into its own padded buffer.
 */
class SimdjsonDom : PaddedInputParser() {
    private val parser = SimdJsonParser()

    /** The JsonValue is a view into the reused tape: results cannot all be kept. */
    override fun keep(input: String): Any? = null

    override fun decodePadded(input: Padded): Event {
        val v = parser.parse(input.buf, input.len)
        val tags = v.get("tags")
        val tagArray = ArrayList<Array<String>>()
        val it = tags.arrayIterator()
        while (it.hasNext()) {
            val tag = it.next()
            val values = ArrayList<String>()
            val inner = tag.arrayIterator()
            while (inner.hasNext()) values.add(inner.next().asString())
            tagArray.add(values.toTypedArray())
        }
        return Event(
            v.get("id").asString(),
            v.get("pubkey").asString(),
            v.get("created_at").asLong(),
            v.get("kind").asLong().toInt(),
            tagArray.toTypedArray(),
            v.get("content").asString(),
            v.get("sig").asString(),
        )
    }

    override fun parseAll(inputs: Array<String>): Long {
        var sink = 0L
        val p = parser
        for (s in inputs) {
            val b = padded(s)
            val v = p.parse(b.buf, b.len)
            sink += v.get("kind").asLong() + v.get("tags").getSize()
        }
        return sink
    }
}

/**
 * simdjson-java's schema-based (on-demand) parsing straight into a record. simdjson-java
 * binds through the canonical constructor, so the Kotlin class is a `@JvmRecord`.
 */
@JvmRecord
data class SimdEvent(
    @param:JsonFieldName("id") val id: String,
    @param:JsonFieldName("pubkey") val pubkey: String,
    @param:JsonFieldName("created_at") val createdAt: Long,
    @param:JsonFieldName("kind") val kind: Int,
    @param:JsonFieldName("tags") val tags: Array<Array<String>>,
    @param:JsonFieldName("content") val content: String,
    @param:JsonFieldName("sig") val sig: String,
)

class SimdjsonSchema : PaddedInputParser() {
    private val parser = SimdJsonParser()

    override fun decodePadded(input: Padded): Event {
        val e = parser.parse(input.buf, input.len, SimdEvent::class.java)
        return Event(e.id, e.pubkey, e.createdAt, e.kind, e.tags, e.content, e.sig)
    }

    override fun parseAll(inputs: Array<String>): Long {
        var sink = 0L
        val p = parser
        for (s in inputs) {
            val b = padded(s)
            val e = p.parse(b.buf, b.len, SimdEvent::class.java)
            sink += e.kind + e.tags.size + e.content.length
        }
        return sink
    }
}
