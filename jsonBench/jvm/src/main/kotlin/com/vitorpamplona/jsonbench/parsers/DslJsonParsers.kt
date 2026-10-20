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

import com.dslplatform.json.DslJson
import com.dslplatform.json.JsonReader
import com.dslplatform.json.NumberConverter
import com.dslplatform.json.runtime.Settings
import com.vitorpamplona.jsonbench.BytesInputParser
import com.vitorpamplona.jsonbench.EMPTY_TAGS
import com.vitorpamplona.jsonbench.Event
import com.vitorpamplona.jsonbench.TagCollector

/**
 * A hand-written loop over DSL-JSON's byte-level [JsonReader], dispatching on the same
 * 32-bit name hash its generated converters use ([JsonReader.fillName]). No annotation
 * processing: this is what `@CompiledJson` would generate, written out by hand.
 */
class DslJsonBytes : BytesInputParser() {
    private val dsl = DslJson<Any>()
    private val reader: JsonReader<Any> = dsl.newReader()
    private val tags = TagCollector()

    // The hashes come from the reader itself, so they always match its hash function.
    private val hId = nameHash("id")
    private val hPubKey = nameHash("pubkey")
    private val hCreatedAt = nameHash("created_at")
    private val hKind = nameHash("kind")
    private val hTags = nameHash("tags")
    private val hContent = nameHash("content")
    private val hSig = nameHash("sig")

    private fun nameHash(name: String): Int {
        val b = "\"$name\":0".encodeToByteArray()
        reader.process(b, b.size)
        reader.getNextToken()
        return reader.fillName()
    }

    private fun parse(input: ByteArray): Event {
        val r = reader
        r.process(input, input.size)
        if (r.getNextToken() != '{'.code.toByte()) throw r.newParseError("expected an object")

        var id = ""
        var pubKey = ""
        var createdAt = 0L
        var kind = 0
        var tagArray = EMPTY_TAGS
        var content = ""
        var sig = ""

        var tok = r.getNextToken()
        while (tok != '}'.code.toByte()) {
            val hash = r.fillName()
            r.getNextToken() // first byte of the value
            when (hash) {
                hId -> id = r.readString()
                hPubKey -> pubKey = r.readString()
                hCreatedAt -> createdAt = NumberConverter.deserializeLong(r)
                hKind -> kind = NumberConverter.deserializeInt(r)
                hTags -> tagArray = readTags(r)
                hContent -> content = r.readString()
                hSig -> sig = r.readString()
                else -> {
                    // skip() leaves the reader on the ',' or '}' after the value.
                    tok = r.skip()
                    if (tok == ','.code.toByte()) tok = r.getNextToken()
                    continue
                }
            }
            tok = r.getNextToken()
            if (tok == ','.code.toByte()) {
                tok = r.getNextToken()
            } else if (tok != '}'.code.toByte()) {
                throw r.newParseError("expected , or }")
            }
        }
        return Event(id, pubKey, createdAt, kind, tagArray, content, sig)
    }

    private fun readTags(r: JsonReader<Any>): Array<Array<String>> {
        if (r.last() != '['.code.toByte()) throw r.newParseError("tags is not an array")
        var tok = r.getNextToken()
        while (tok != ']'.code.toByte()) {
            if (tok != '['.code.toByte()) throw r.newParseError("tag is not an array")
            tags.startTag()
            var inner = r.getNextToken()
            while (inner != ']'.code.toByte()) {
                tags.addValue(r.readString())
                inner = r.getNextToken()
                if (inner == ','.code.toByte()) inner = r.getNextToken()
            }
            tags.endTag()
            tok = r.getNextToken()
            if (tok == ','.code.toByte()) tok = r.getNextToken()
        }
        return tags.finish()
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

/** DSL-JSON's runtime (reflection-analysed, no annotation processor) binding target. */
class DslEvent {
    @JvmField var id: String = ""

    @JvmField var pubkey: String = ""

    // Named like the JSON key: DSL-JSON's runtime analysis ignores @JsonAttribute(name) on
    // a public field (the value silently stayed 0), so the name has to match.
    @Suppress("ktlint:standard:property-naming")
    @JvmField
    var created_at: Long = 0

    @JvmField var kind: Int = 0

    @JvmField var tags: Array<Array<String>> = EMPTY_TAGS

    @JvmField var content: String = ""

    @JvmField var sig: String = ""
}

class DslJsonReflect : BytesInputParser() {
    private val dsl = DslJson(Settings.withRuntime<Any>().includeServiceLoader())

    override fun decodeBytes(input: ByteArray): Event {
        val e = dsl.deserialize(DslEvent::class.java, input, input.size) ?: error("null event")
        return Event(e.id, e.pubkey, e.created_at, e.kind, e.tags, e.content, e.sig)
    }

    override fun parseAll(inputs: Array<String>): Long {
        var sink = 0L
        for (s in inputs) {
            val b = utf8(s)
            val e = dsl.deserialize(DslEvent::class.java, b, b.size)!!
            sink += e.kind + e.tags.size + e.content.length
        }
        return sink
    }
}
