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

import com.vitorpamplona.jsonbench.BytesInputParser
import com.vitorpamplona.jsonbench.Event
import com.vitorpamplona.jsonbench.StringInputParser
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import java.io.ByteArrayInputStream

/** The plugin-generated serializer's model: what a kotlinx user would write by default. */
@Serializable
class KxEvent(
    val id: String,
    val pubkey: String,
    @SerialName("created_at") val createdAt: Long,
    val kind: Int,
    val tags: List<List<String>>,
    val content: String,
    val sig: String,
) {
    fun toBench() = Event(id, pubkey, createdAt, kind, Array(tags.size) { tags[it].toTypedArray() }, content, sig)
}

private val kxJson = Json { ignoreUnknownKeys = true }

class KotlinxTyped : StringInputParser() {
    override fun decode(input: String) = kxJson.decodeFromString(KxEvent.serializer(), input).toBench()

    override fun parseAll(inputs: Array<String>): Long {
        var sink = 0L
        for (s in inputs) {
            val e = kxJson.decodeFromString(KxEvent.serializer(), s)
            sink += e.kind + e.tags.size + e.content.length
        }
        return sink
    }
}

@OptIn(ExperimentalSerializationApi::class)
class KotlinxStream : BytesInputParser() {
    override fun decodeBytes(input: ByteArray) = kxJson.decodeFromStream(KxEvent.serializer(), ByteArrayInputStream(input)).toBench()

    override fun parseAll(inputs: Array<String>): Long {
        var sink = 0L
        for (s in inputs) {
            val b = utf8(s)
            val e = kxJson.decodeFromStream(KxEvent.serializer(), ByteArrayInputStream(b))
            sink += e.kind + e.tags.size + e.content.length
        }
        return sink
    }
}

class KotlinxElement : StringInputParser() {
    override fun keep(input: String): Any = kxJson.parseToJsonElement(input)

    override fun decode(input: String): Event {
        val o = kxJson.parseToJsonElement(input).jsonObject
        val tags = o.getValue("tags").jsonArray
        return Event(
            o.getValue("id").jsonPrimitive.content,
            o.getValue("pubkey").jsonPrimitive.content,
            o.getValue("created_at").jsonPrimitive.long,
            o.getValue("kind").jsonPrimitive.int,
            Array(tags.size) { i ->
                val tag = tags[i].jsonArray
                Array(tag.size) { j -> tag[j].jsonPrimitive.content }
            },
            o.getValue("content").jsonPrimitive.content,
            o.getValue("sig").jsonPrimitive.content,
        )
    }

    override fun parseAll(inputs: Array<String>): Long {
        var sink = 0L
        for (s in inputs) {
            val o = kxJson.parseToJsonElement(s).jsonObject
            sink += o.getValue("kind").jsonPrimitive.int + o.getValue("tags").jsonArray.size
        }
        return sink
    }
}
