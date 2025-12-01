/**
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
package com.vitorpamplona.quartz.nip11RelayInfo

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonPrimitive

object FlexibleIntListSerializer : KSerializer<List<Int>?> {
    private val listSerializer = ListSerializer(Int.serializer())

    override val descriptor: SerialDescriptor = listSerializer.descriptor

    override fun deserialize(decoder: Decoder): List<Int>? {
        require(decoder is JsonDecoder) { "This serializer can only be used with Json format" }

        val element = decoder.decodeJsonElement()

        return when {
            // Handle JSON null
            element is JsonNull -> null

            // Handle array format (spec-compliant): [1, 2, 3]
            element is JsonArray -> {
                element.mapNotNull { arrayElement ->
                    try {
                        arrayElement.jsonPrimitive.int
                    } catch (e: Exception) {
                        // Skip elements that aren't valid integers (strings, booleans, floats, etc.)
                        null
                    }
                }
            }

            // Handle single integer format (malformed but found in the wild): 1
            element is JsonPrimitive && !element.isString -> {
                try {
                    listOf(element.int)
                } catch (e: Exception) {
                    // Can't parse as integer (e.g., float, boolean), treat as missing data
                    null
                }
            }

            // Unsupported format (strings, objects, etc.)
            else -> null
        }
    }

    @OptIn(ExperimentalSerializationApi::class)
    override fun serialize(
        encoder: Encoder,
        value: List<Int>?,
    ) {
        if (value == null) {
            encoder.encodeNull()
        } else {
            listSerializer.serialize(encoder, value)
        }
    }
}
