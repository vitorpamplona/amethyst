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

import com.jsoniter.JsonIterator
import com.vitorpamplona.jsonbench.BytesInputParser
import com.vitorpamplona.jsonbench.EMPTY_TAGS
import com.vitorpamplona.jsonbench.Event
import com.vitorpamplona.jsonbench.StringInputParser
import com.vitorpamplona.jsonbench.TagCollector
import jakarta.json.spi.JsonProvider
import jakarta.json.stream.JsonParser
import net.minidev.json.parser.JSONParser
import org.json.JSONObject
import java.io.StringReader
import com.eclipsesource.json.Json as MinimalJson
import net.minidev.json.JSONArray as SmartArray
import net.minidev.json.JSONObject as SmartObject

/** org.json, the API Android ships in the platform (android.jar's org.json is a fork of it). */
class OrgJsonDom : StringInputParser() {
    override fun keep(input: String): Any = JSONObject(input)

    override fun decode(input: String): Event {
        val o = JSONObject(input)
        val tags = o.getJSONArray("tags")
        return Event(
            o.getString("id"),
            o.getString("pubkey"),
            o.getLong("created_at"),
            o.getInt("kind"),
            Array(tags.length()) { i ->
                val tag = tags.getJSONArray(i)
                Array(tag.length()) { j -> tag.getString(j) }
            },
            o.getString("content"),
            o.getString("sig"),
        )
    }

    override fun parseAll(inputs: Array<String>): Long {
        var sink = 0L
        for (s in inputs) {
            val o = JSONObject(s)
            sink += o.getInt("kind") + o.getJSONArray("tags").length()
        }
        return sink
    }
}

/** jsoniter (Java) iterator API over one reused [JsonIterator]; no codegen. */
class JsoniterIterator : BytesInputParser() {
    private val iter = JsonIterator.parse(ByteArray(0))
    private val tags = TagCollector()

    private fun parse(input: ByteArray): Event {
        val it = iter
        it.reset(input)
        var id = ""
        var pubKey = ""
        var createdAt = 0L
        var kind = 0
        var tagArray = EMPTY_TAGS
        var content = ""
        var sig = ""
        var field = it.readObject()
        while (field != null) {
            when (field) {
                "id" -> id = it.readString()
                "pubkey" -> pubKey = it.readString()
                "created_at" -> createdAt = it.readLong()
                "kind" -> kind = it.readInt()
                "tags" -> {
                    while (it.readArray()) {
                        tags.startTag()
                        while (it.readArray()) tags.addValue(it.readString())
                        tags.endTag()
                    }
                    tagArray = tags.finish()
                }
                "content" -> content = it.readString()
                "sig" -> sig = it.readString()
                else -> it.skip()
            }
            field = it.readObject()
        }
        return Event(id, pubKey, createdAt, kind, tagArray, content, sig)
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
}

/** Jakarta JSON-P streaming parser (Eclipse Parsson, the reference implementation). */
class ParssonStreaming : StringInputParser() {
    private val factory = JsonProvider.provider().createParserFactory(emptyMap<String, Any>())
    private val tags = TagCollector()

    private fun parse(input: String): Event {
        factory.createParser(StringReader(input)).use { p ->
            var id = ""
            var pubKey = ""
            var createdAt = 0L
            var kind = 0
            var tagArray = EMPTY_TAGS
            var content = ""
            var sig = ""
            check(p.next() == JsonParser.Event.START_OBJECT) { "expected an object" }
            while (p.next() == JsonParser.Event.KEY_NAME) {
                val name = p.string
                val value = p.next()
                when (name) {
                    "id" -> id = p.string
                    "pubkey" -> pubKey = p.string
                    "created_at" -> createdAt = p.long
                    "kind" -> kind = p.int
                    "tags" -> {
                        while (p.next() == JsonParser.Event.START_ARRAY) {
                            tags.startTag()
                            while (p.next() == JsonParser.Event.VALUE_STRING) tags.addValue(p.string)
                            tags.endTag()
                        }
                        tagArray = tags.finish()
                    }
                    "content" -> content = p.string
                    "sig" -> sig = p.string
                    else ->
                        when (value) {
                            JsonParser.Event.START_OBJECT -> p.skipObject()
                            JsonParser.Event.START_ARRAY -> p.skipArray()
                            else -> {}
                        }
                }
            }
            return Event(id, pubKey, createdAt, kind, tagArray, content, sig)
        }
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

class MinimalJsonDom : StringInputParser() {
    override fun keep(input: String): Any = MinimalJson.parse(input)

    override fun decode(input: String): Event {
        val o = MinimalJson.parse(input).asObject()
        val tags = o.get("tags").asArray()
        return Event(
            o.get("id").asString(),
            o.get("pubkey").asString(),
            o.get("created_at").asLong(),
            o.get("kind").asInt(),
            Array(tags.size()) { i ->
                val tag = tags.get(i).asArray()
                Array(tag.size()) { j -> tag.get(j).asString() }
            },
            o.get("content").asString(),
            o.get("sig").asString(),
        )
    }

    override fun parseAll(inputs: Array<String>): Long {
        var sink = 0L
        for (s in inputs) {
            val o = MinimalJson.parse(s).asObject()
            sink += o.get("kind").asInt() + o.get("tags").asArray().size()
        }
        return sink
    }
}

/** json-smart in strict RFC 4627 mode; its tree is plain HashMap/ArrayList subclasses. */
class JsonSmartDom : StringInputParser() {
    private val parser = JSONParser(JSONParser.MODE_RFC4627)

    override fun keep(input: String): Any = parser.parse(input)

    override fun decode(input: String): Event {
        val o = parser.parse(input) as SmartObject
        val tags = o["tags"] as SmartArray
        return Event(
            o["id"] as String,
            o["pubkey"] as String,
            (o["created_at"] as Number).toLong(),
            (o["kind"] as Number).toInt(),
            Array(tags.size) { i ->
                val tag = tags[i] as SmartArray
                Array(tag.size) { j -> tag[j] as String }
            },
            o["content"] as String,
            o["sig"] as String,
        )
    }

    override fun parseAll(inputs: Array<String>): Long {
        var sink = 0L
        for (s in inputs) {
            val o = parser.parse(s) as SmartObject
            sink += (o["kind"] as Number).toInt() + (o["tags"] as SmartArray).size
        }
        return sink
    }
}
