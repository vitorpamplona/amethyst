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

import com.google.gson.Gson
import com.google.gson.JsonParser
import com.google.gson.annotations.SerializedName
import com.google.gson.stream.JsonReader
import com.vitorpamplona.jsonbench.EMPTY_TAGS
import com.vitorpamplona.jsonbench.Event
import com.vitorpamplona.jsonbench.StringInputParser
import com.vitorpamplona.jsonbench.TagCollector
import java.io.StringReader

class GsonStreaming : StringInputParser() {
    private val tags = TagCollector()

    private fun parse(input: String): Event {
        val r = JsonReader(StringReader(input))
        var id = ""
        var pubKey = ""
        var createdAt = 0L
        var kind = 0
        var tagArray = EMPTY_TAGS
        var content = ""
        var sig = ""
        r.beginObject()
        while (r.hasNext()) {
            when (r.nextName()) {
                "id" -> id = r.nextString()
                "pubkey" -> pubKey = r.nextString()
                "created_at" -> createdAt = r.nextLong()
                "kind" -> kind = r.nextInt()
                "tags" -> {
                    r.beginArray()
                    while (r.hasNext()) {
                        tags.startTag()
                        r.beginArray()
                        while (r.hasNext()) tags.addValue(r.nextString())
                        r.endArray()
                        tags.endTag()
                    }
                    r.endArray()
                    tagArray = tags.finish()
                }
                "content" -> content = r.nextString()
                "sig" -> sig = r.nextString()
                else -> r.skipValue()
            }
        }
        r.endObject()
        return Event(id, pubKey, createdAt, kind, tagArray, content, sig)
    }

    override fun decode(input: String) = parse(input)

    override fun parseAll(inputs: Array<String>): Long {
        var sink = 0L
        for (s in inputs) {
            val e = parse(s)
            sink += e.kind + e.tags.size + e.content.length
        }
        return sink
    }
}

class GsonTree : StringInputParser() {
    override fun keep(input: String): Any = JsonParser.parseString(input)

    override fun decode(input: String): Event {
        val o = JsonParser.parseString(input).asJsonObject
        val tags = o.getAsJsonArray("tags")
        return Event(
            o.get("id").asString,
            o.get("pubkey").asString,
            o.get("created_at").asLong,
            o.get("kind").asInt,
            Array(tags.size()) { i ->
                val tag = tags.get(i).asJsonArray
                Array(tag.size()) { j -> tag.get(j).asString }
            },
            o.get("content").asString,
            o.get("sig").asString,
        )
    }

    override fun parseAll(inputs: Array<String>): Long {
        var sink = 0L
        for (s in inputs) {
            val o = JsonParser.parseString(s).asJsonObject
            sink += o.get("kind").asInt + o.getAsJsonArray("tags").size()
        }
        return sink
    }
}

/** Gson's reflective binding target: public fields, a no-arg constructor. */
class GsonEvent {
    @JvmField var id: String = ""

    @JvmField var pubkey: String = ""

    @JvmField
    @SerializedName("created_at")
    var createdAt: Long = 0

    @JvmField var kind: Int = 0

    @JvmField var tags: Array<Array<String>> = EMPTY_TAGS

    @JvmField var content: String = ""

    @JvmField var sig: String = ""
}

class GsonReflect : StringInputParser() {
    private val gson = Gson()

    override fun decode(input: String): Event {
        val e = gson.fromJson(input, GsonEvent::class.java)
        return Event(e.id, e.pubkey, e.createdAt, e.kind, e.tags, e.content, e.sig)
    }

    override fun parseAll(inputs: Array<String>): Long {
        var sink = 0L
        for (s in inputs) {
            val e = gson.fromJson(s, GsonEvent::class.java)
            sink += e.kind + e.tags.size + e.content.length
        }
        return sink
    }
}
