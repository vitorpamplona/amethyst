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
package com.vitorpamplona.quartz.nip59Giftwrap.rumors.kotlinSerialization

import com.vitorpamplona.quartz.nip01Core.kotlinSerialization.TagArrayKSerializer

import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.TagArray
import com.vitorpamplona.quartz.nip59Giftwrap.rumors.Rumor
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.descriptors.element
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlinx.serialization.json.put

object RumorKSerializer : KSerializer<Rumor> {
    override val descriptor: SerialDescriptor =
        buildClassSerialDescriptor("Rumor") {
            element<String>("id")
            element<String>("pubkey")
            element<Long>("created_at")
            element<Int>("kind")
            element("tags", TagArrayKSerializer.descriptor)
            element<String>("content")
        }

    override fun serialize(
        encoder: Encoder,
        value: Rumor,
    ) {
        val jsonEncoder = encoder as JsonEncoder
        val element =
            buildJsonObject {
                value.id?.let { put("id", it) }
                value.pubKey?.let { put("pubkey", it) }
                value.createdAt?.let { put("created_at", it) }
                value.kind?.let { put("kind", it) }
                value.tags?.let { put("tags", TagArrayKSerializer.serializeToElement(it)) }
                value.content?.let { put("content", it) }
            }
        jsonEncoder.encodeJsonElement(element)
    }

    override fun deserialize(decoder: Decoder): Rumor {
        val jsonDecoder = decoder as JsonDecoder
        val jsonObject = jsonDecoder.decodeJsonElement().jsonObject

        var id: HexKey? = null
        var pubKey: HexKey? = null
        var createdAt: Long? = null
        var kind: Int? = null
        var tags: TagArray? = null
        var content: String? = null

        for ((key, value) in jsonObject) {
            when (key) {
                "id" -> id = value.jsonPrimitive.content
                "pubkey" -> pubKey = value.jsonPrimitive.content
                "created_at" -> createdAt = value.jsonPrimitive.long
                "kind" -> kind = value.jsonPrimitive.int
                "tags" -> tags = TagArrayKSerializer.deserializeFromElement(value)
                "content" -> content = value.jsonPrimitive.content
            }
        }

        return Rumor(id, pubKey, createdAt, kind, tags, content)
    }
}
