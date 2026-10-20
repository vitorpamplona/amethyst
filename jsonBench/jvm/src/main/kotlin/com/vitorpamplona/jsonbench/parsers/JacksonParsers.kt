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

import com.fasterxml.jackson.core.JsonFactory
import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.core.JsonToken
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.vitorpamplona.jsonbench.BytesInputParser
import com.vitorpamplona.jsonbench.EMPTY_TAGS
import com.vitorpamplona.jsonbench.Event
import com.vitorpamplona.jsonbench.StringInputParser
import com.vitorpamplona.jsonbench.TagCollector

/**
 * A hand-written field loop over jackson-core's streaming parser, the same shape as
 * Quartz's EventDeserializer but with no databind, no String.intern() and no EventFactory.
 */
class JacksonEventReader {
    private val tags = TagCollector()

    fun read(p: JsonParser): Event {
        var id = ""
        var pubKey = ""
        var createdAt = 0L
        var kind = 0
        var tagArray = EMPTY_TAGS
        var content = ""
        var sig = ""

        check(p.nextToken() == JsonToken.START_OBJECT) { "expected an object" }
        while (true) {
            val token = p.nextToken() ?: error("truncated object")
            if (token == JsonToken.END_OBJECT) break
            val name = p.currentName()
            p.nextToken()
            when (name) {
                "id" -> id = p.text
                "pubkey" -> pubKey = p.text
                "created_at" -> createdAt = p.longValue
                "kind" -> kind = p.intValue
                "tags" -> tagArray = readTags(p)
                "content" -> content = p.text
                "sig" -> sig = p.text
                else -> p.skipChildren()
            }
        }
        return Event(id, pubKey, createdAt, kind, tagArray, content, sig)
    }

    private fun readTags(p: JsonParser): Array<Array<String>> {
        check(p.currentToken() == JsonToken.START_ARRAY) { "tags is not an array" }
        while (p.nextToken() == JsonToken.START_ARRAY) {
            tags.startTag()
            while (p.nextToken() != JsonToken.END_ARRAY) tags.addValue(p.text)
            tags.endTag()
        }
        return tags.finish()
    }
}

class JacksonStreamingBytes : BytesInputParser() {
    private val factory = JsonFactory()
    private val reader = JacksonEventReader()

    private fun parse(input: ByteArray): Event = factory.createParser(input).use { reader.read(it) }

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

class JacksonStreamingString : StringInputParser() {
    private val factory = JsonFactory()
    private val reader = JacksonEventReader()

    private fun parse(input: String): Event = factory.createParser(input).use { reader.read(it) }

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

class JacksonTree : BytesInputParser() {
    private val mapper = ObjectMapper()

    override fun keep(input: String): Any = mapper.readTree(utf8(input))

    override fun decodeBytes(input: ByteArray): Event {
        val n = mapper.readTree(input)
        return Event(
            n.get("id").textValue(),
            n.get("pubkey").textValue(),
            n.get("created_at").longValue(),
            n.get("kind").intValue(),
            Array(n.get("tags").size()) { i ->
                val tag: JsonNode = n.get("tags").get(i)
                Array(tag.size()) { j -> tag.get(j).textValue() }
            },
            n.get("content").textValue(),
            n.get("sig").textValue(),
        )
    }

    override fun parseAll(inputs: Array<String>): Long {
        var sink = 0L
        for (s in inputs) {
            val b = utf8(s)
            val n = mapper.readTree(b)
            sink += n.get("kind").intValue() + n.get("tags").size()
        }
        return sink
    }
}
