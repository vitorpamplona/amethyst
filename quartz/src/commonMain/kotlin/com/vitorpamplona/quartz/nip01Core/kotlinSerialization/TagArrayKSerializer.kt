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

import com.vitorpamplona.quartz.nip01Core.core.TagArray
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive

object TagArrayKSerializer : KSerializer<TagArray> {
    override val descriptor: SerialDescriptor =
        ListSerializer(ListSerializer(String.serializer())).descriptor

    override fun serialize(
        encoder: Encoder,
        value: TagArray,
    ) {
        val jsonEncoder = encoder as JsonEncoder
        val element =
            buildJsonArray {
                for (tag in value) {
                    add(
                        buildJsonArray {
                            for (s in tag) {
                                add(JsonPrimitive(s))
                            }
                        },
                    )
                }
            }
        jsonEncoder.encodeJsonElement(element)
    }

    override fun deserialize(decoder: Decoder): TagArray {
        val jsonDecoder = decoder as JsonDecoder
        return deserializeFromElement(jsonDecoder.decodeJsonElement())
    }

    fun deserializeFromElement(element: JsonElement): TagArray {
        val array = element.jsonArray
        val outerList = ArrayList<Array<String>>(array.size)
        for (inner in array) {
            val innerArray = inner.jsonArray
            val innerList = ArrayList<String>(innerArray.size.coerceAtLeast(5))
            for (s in innerArray) {
                if (s is JsonNull) {
                    innerList.add("")
                } else {
                    innerList.add(s.jsonPrimitive.content)
                }
            }
            outerList.add(innerList.toTypedArray())
        }
        return outerList.toTypedArray()
    }

    fun serializeToElement(value: TagArray): JsonArray =
        buildJsonArray {
            for (tag in value) {
                add(
                    buildJsonArray {
                        for (s in tag) {
                            add(JsonPrimitive(s))
                        }
                    },
                )
            }
        }
}
