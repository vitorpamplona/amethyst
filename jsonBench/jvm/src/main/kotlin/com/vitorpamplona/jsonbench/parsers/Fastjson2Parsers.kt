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

import com.alibaba.fastjson2.JSON
import com.alibaba.fastjson2.JSONReader
import com.alibaba.fastjson2.annotation.JSONField
import com.alibaba.fastjson2.util.Fnv
import com.vitorpamplona.jsonbench.BytesInputParser
import com.vitorpamplona.jsonbench.EMPTY_TAGS
import com.vitorpamplona.jsonbench.Event
import com.vitorpamplona.jsonbench.StringInputParser
import com.vitorpamplona.jsonbench.TagCollector

/** fastjson2's reflective (ASM-generated reader) binding target. */
class Fastjson2Event {
    @JvmField var id: String = ""

    @JvmField var pubkey: String = ""

    @JvmField
    @JSONField(name = "created_at")
    var createdAt: Long = 0

    @JvmField var kind: Int = 0

    @JvmField var tags: Array<Array<String>> = EMPTY_TAGS

    @JvmField var content: String = ""

    @JvmField var sig: String = ""

    fun toBench() = Event(id, pubkey, createdAt, kind, tags, content, sig)
}

class Fastjson2ReflectBytes : BytesInputParser() {
    override fun decodeBytes(input: ByteArray) = JSON.parseObject(input, Fastjson2Event::class.java).toBench()

    override fun parseAll(inputs: Array<String>): Long {
        var sink = 0L
        for (s in inputs) {
            val b = utf8(s)
            val e = JSON.parseObject(b, Fastjson2Event::class.java)
            sink += e.kind + e.tags.size + e.content.length
        }
        return sink
    }
}

class Fastjson2ReflectString : StringInputParser() {
    override fun decode(input: String) = JSON.parseObject(input, Fastjson2Event::class.java).toBench()

    override fun parseAll(inputs: Array<String>): Long {
        var sink = 0L
        for (s in inputs) {
            val e = JSON.parseObject(s, Fastjson2Event::class.java)
            sink += e.kind + e.tags.size + e.content.length
        }
        return sink
    }
}

/** A hand-written loop over fastjson2's JSONReader, dispatching on its 64-bit field-name hash. */
class Fastjson2Reader : BytesInputParser() {
    private val tags = TagCollector()

    private fun parse(input: ByteArray): Event {
        JSONReader.of(input).use { r ->
            var id = ""
            var pubKey = ""
            var createdAt = 0L
            var kind = 0
            var tagArray = EMPTY_TAGS
            var content = ""
            var sig = ""
            check(r.nextIfObjectStart()) { "expected an object" }
            while (!r.nextIfObjectEnd()) {
                when (r.readFieldNameHashCode()) {
                    H_ID -> id = r.readString()
                    H_PUBKEY -> pubKey = r.readString()
                    H_CREATED_AT -> createdAt = r.readInt64Value()
                    H_KIND -> kind = r.readInt32Value()
                    H_TAGS -> {
                        check(r.nextIfArrayStart()) { "tags is not an array" }
                        while (!r.nextIfArrayEnd()) {
                            check(r.nextIfArrayStart()) { "tag is not an array" }
                            tags.startTag()
                            while (!r.nextIfArrayEnd()) tags.addValue(r.readString())
                            tags.endTag()
                        }
                        tagArray = tags.finish()
                    }
                    H_CONTENT -> content = r.readString()
                    H_SIG -> sig = r.readString()
                    else -> r.skipValue()
                }
            }
            return Event(id, pubKey, createdAt, kind, tagArray, content, sig)
        }
    }

    override fun decodeBytes(input: ByteArray) = parse(input)

    override fun parseAll(inputs: Array<String>): Long {
        var sink = 0L
        for (s in inputs) {
            val b = utf8(s)
            val e = parse(b)
            sink += e.kind + e.tags.size + e.content.length
        }
        return sink
    }

    companion object {
        val H_ID = Fnv.hashCode64("id")
        val H_PUBKEY = Fnv.hashCode64("pubkey")
        val H_CREATED_AT = Fnv.hashCode64("created_at")
        val H_KIND = Fnv.hashCode64("kind")
        val H_TAGS = Fnv.hashCode64("tags")
        val H_CONTENT = Fnv.hashCode64("content")
        val H_SIG = Fnv.hashCode64("sig")
    }
}

class Fastjson2Dom : BytesInputParser() {
    override fun keep(input: String): Any = JSON.parseObject(utf8(input))

    override fun decodeBytes(input: ByteArray): Event {
        val o = JSON.parseObject(input)
        val tags = o.getJSONArray("tags")
        return Event(
            o.getString("id"),
            o.getString("pubkey"),
            o.getLongValue("created_at"),
            o.getIntValue("kind"),
            Array(tags.size) { i ->
                val tag = tags.getJSONArray(i)
                Array(tag.size) { j -> tag.getString(j) }
            },
            o.getString("content"),
            o.getString("sig"),
        )
    }

    override fun parseAll(inputs: Array<String>): Long {
        var sink = 0L
        for (s in inputs) {
            val b = utf8(s)
            val o = JSON.parseObject(b)
            sink += o.getIntValue("kind") + o.getJSONArray("tags").size
        }
        return sink
    }
}
