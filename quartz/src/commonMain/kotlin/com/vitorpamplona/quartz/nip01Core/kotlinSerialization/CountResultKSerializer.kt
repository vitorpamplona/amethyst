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

import com.vitorpamplona.quartz.nip01Core.relay.commands.toClient.CountResult
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.descriptors.element
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

object CountResultKSerializer : KSerializer<CountResult> {
    override val descriptor: SerialDescriptor =
        buildClassSerialDescriptor("CountResult") {
            element<Int>("count")
            element<Boolean>("pubkey")
        }

    override fun serialize(
        encoder: Encoder,
        value: CountResult,
    ) {
        val jsonEncoder = encoder as JsonEncoder
        jsonEncoder.encodeJsonElement(serializeToElement(value))
    }

    fun serializeToElement(value: CountResult): JsonObject =
        buildJsonObject {
            put("count", value.count)
            // Matches Jackson's CountResultSerializer which writes "pubkey" for approximate
            put("pubkey", value.approximate)
        }

    override fun deserialize(decoder: Decoder): CountResult {
        val jsonDecoder = decoder as JsonDecoder
        return deserializeFromElement(jsonDecoder.decodeJsonElement().jsonObject)
    }

    fun deserializeFromElement(jsonObject: JsonObject): CountResult =
        CountResult(
            count = jsonObject["count"]!!.jsonPrimitive.int,
            approximate = jsonObject["approximate"]?.jsonPrimitive?.boolean ?: false,
        )
}
