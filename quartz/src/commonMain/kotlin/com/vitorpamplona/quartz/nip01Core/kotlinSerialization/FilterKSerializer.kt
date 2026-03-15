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

import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put

object FilterKSerializer : KSerializer<Filter> {
    override val descriptor: SerialDescriptor =
        buildClassSerialDescriptor("Filter")

    override fun serialize(
        encoder: Encoder,
        value: Filter,
    ) {
        val jsonEncoder = encoder as JsonEncoder
        jsonEncoder.encodeJsonElement(serializeToElement(value))
    }

    fun serializeToElement(filter: Filter): JsonObject =
        buildJsonObject {
            filter.kinds?.let { kinds ->
                put(
                    "kinds",
                    buildJsonArray {
                        for (k in kinds) add(JsonPrimitive(k))
                    },
                )
            }
            filter.ids?.let { ids ->
                put(
                    "ids",
                    buildJsonArray {
                        for (id in ids) add(JsonPrimitive(id))
                    },
                )
            }
            filter.authors?.let { authors ->
                put(
                    "authors",
                    buildJsonArray {
                        for (a in authors) add(JsonPrimitive(a))
                    },
                )
            }
            filter.tags?.let { tags ->
                for ((key, values) in tags) {
                    put(
                        "#$key",
                        buildJsonArray {
                            for (v in values) add(JsonPrimitive(v))
                        },
                    )
                }
            }
            filter.tagsAll?.let { tagsAll ->
                for ((key, values) in tagsAll) {
                    put(
                        "&$key",
                        buildJsonArray {
                            for (v in values) add(JsonPrimitive(v))
                        },
                    )
                }
            }
            filter.since?.let { put("since", it) }
            filter.until?.let { put("until", it) }
            filter.limit?.let { put("limit", it) }
            filter.search?.let { put("search", it) }
        }

    override fun deserialize(decoder: Decoder): Filter {
        val jsonDecoder = decoder as JsonDecoder
        return deserializeFromElement(jsonDecoder.decodeJsonElement().jsonObject)
    }

    fun deserializeFromElement(jsonObject: JsonObject): Filter {
        val tags = mutableMapOf<String, List<String>>()
        val tagsAll = mutableMapOf<String, List<String>>()

        for ((key, value) in jsonObject) {
            when {
                key.startsWith("#") -> {
                    tags[key.substring(1)] = value.jsonArray.mapNotNull { it.jsonPrimitive.content }
                }
                key.startsWith("&") -> {
                    tagsAll[key.substring(1)] = value.jsonArray.mapNotNull { it.jsonPrimitive.content }
                }
            }
        }

        return Filter(
            ids = jsonObject["ids"]?.jsonArray?.mapNotNull { it.jsonPrimitive.content },
            authors = jsonObject["authors"]?.jsonArray?.mapNotNull { it.jsonPrimitive.content },
            kinds = jsonObject["kinds"]?.jsonArray?.mapNotNull { it.jsonPrimitive.intOrNull },
            tags = tags.ifEmpty { null },
            tagsAll = tagsAll.ifEmpty { null },
            since = jsonObject["since"]?.jsonPrimitive?.longOrNull,
            until = jsonObject["until"]?.jsonPrimitive?.longOrNull,
            limit = jsonObject["limit"]?.jsonPrimitive?.intOrNull,
            search = jsonObject["search"]?.jsonPrimitive?.content,
        )
    }
}
