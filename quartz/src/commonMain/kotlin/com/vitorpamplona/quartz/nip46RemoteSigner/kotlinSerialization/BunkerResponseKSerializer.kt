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
package com.vitorpamplona.quartz.nip46RemoteSigner.kotlinSerialization

import com.vitorpamplona.quartz.nip46RemoteSigner.BunkerResponse
import com.vitorpamplona.quartz.nip46RemoteSigner.BunkerResponseAck
import com.vitorpamplona.quartz.nip46RemoteSigner.BunkerResponseError
import com.vitorpamplona.quartz.nip46RemoteSigner.BunkerResponseEvent
import com.vitorpamplona.quartz.nip46RemoteSigner.BunkerResponseGetRelays
import com.vitorpamplona.quartz.nip46RemoteSigner.BunkerResponsePong
import com.vitorpamplona.quartz.nip46RemoteSigner.BunkerResponsePublicKey
import com.vitorpamplona.quartz.utils.Hex
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.descriptors.element
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

object BunkerResponseKSerializer : KSerializer<BunkerResponse> {
    override val descriptor: SerialDescriptor =
        buildClassSerialDescriptor("BunkerResponse") {
            element<String>("id")
            element<String?>("result")
            element<String?>("error")
        }

    override fun serialize(
        encoder: Encoder,
        value: BunkerResponse,
    ) {
        val jsonEncoder = encoder as JsonEncoder
        val element =
            buildJsonObject {
                put("id", value.id)
                value.result?.let { put("result", it) }
                value.error?.let { put("error", it) }
            }
        jsonEncoder.encodeJsonElement(element)
    }

    override fun deserialize(decoder: Decoder): BunkerResponse {
        val jsonDecoder = decoder as JsonDecoder
        return deserializeFromElement(jsonDecoder.decodeJsonElement().jsonObject)
    }

    fun deserializeFromElement(jsonObject: JsonObject): BunkerResponse {
        val id = jsonObject["id"]!!.jsonPrimitive.content
        val result = jsonObject["result"]?.let { if (it is JsonNull) null else it.jsonPrimitive.content }
        val error = jsonObject["error"]?.let { if (it is JsonNull) null else it.jsonPrimitive.content }

        if (error != null) {
            return BunkerResponseError.parse(id, result, error)
        }

        if (result != null) {
            when (result) {
                BunkerResponseAck.RESULT -> {
                    return BunkerResponseAck.parse(id, result, error)
                }

                BunkerResponsePong.RESULT -> {
                    return BunkerResponsePong.parse(id, result, error)
                }

                else -> {
                    if (result.length == 64 && Hex.isHex(result)) {
                        return BunkerResponsePublicKey.parse(id, result)
                    }

                    if (result.isNotEmpty() && result[0] == '{') {
                        try {
                            return BunkerResponseEvent.parse(id, result)
                        } catch (_: Exception) {
                        }

                        try {
                            return BunkerResponseGetRelays.parse(id, result)
                        } catch (_: Exception) {
                        }
                    }

                    return BunkerResponse(id, result, error)
                }
            }
        }

        return BunkerResponse(id, result, error)
    }
}
