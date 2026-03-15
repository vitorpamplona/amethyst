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
package com.vitorpamplona.quartz.nip01Core.kotlinSerialization

import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.Kind
import com.vitorpamplona.quartz.nip01Core.core.TagArray
import com.vitorpamplona.quartz.utils.EventFactory
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.descriptors.element
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlinx.serialization.json.put

object EventKSerializer : KSerializer<Event> {
    private val emptyTagArray = emptyArray<Array<String>>()

    override val descriptor: SerialDescriptor =
        buildClassSerialDescriptor("Event") {
            element<String>("id")
            element<String>("pubkey")
            element<Long>("created_at")
            element<Int>("kind")
            element("tags", TagArrayKSerializer.descriptor)
            element<String>("content")
            element<String>("sig")
        }

    override fun serialize(
        encoder: Encoder,
        value: Event,
    ) {
        val jsonEncoder = encoder as JsonEncoder
        jsonEncoder.encodeJsonElement(serializeToElement(value))
    }

    fun serializeToElement(event: Event): JsonObject =
        buildJsonObject {
            put("id", event.id)
            put("pubkey", event.pubKey)
            put("created_at", event.createdAt)
            put("kind", event.kind)
            put("tags", TagArrayKSerializer.serializeToElement(event.tags))
            put("content", event.content)
            put("sig", event.sig)
        }

    override fun deserialize(decoder: Decoder): Event {
        val jsonDecoder = decoder as JsonDecoder
        return deserializeFromElement(jsonDecoder.decodeJsonElement().jsonObject)
    }

    fun deserializeFromElement(jsonObject: JsonObject): Event {
        var id: HexKey = ""
        var pubKey: HexKey = ""
        var createdAt: Long = 0
        var kind: Kind = 0
        var tags: TagArray = emptyTagArray
        var content = ""
        var sig: HexKey = ""

        for ((key, value) in jsonObject) {
            when (key) {
                "id" -> id = value.jsonPrimitive.content
                "pubkey" -> pubKey = value.jsonPrimitive.content
                "created_at" -> createdAt = value.jsonPrimitive.long
                "kind" -> kind = value.jsonPrimitive.int
                "tags" -> tags = TagArrayKSerializer.deserializeFromElement(value)
                "content" -> content = value.jsonPrimitive.content
                "sig" -> sig = value.jsonPrimitive.content
            }
        }

        if (pubKey.isEmpty()) {
            throw IllegalArgumentException("Event not found")
        }

        return EventFactory.create(id, pubKey, createdAt, kind, tags, content, sig)
    }
}
