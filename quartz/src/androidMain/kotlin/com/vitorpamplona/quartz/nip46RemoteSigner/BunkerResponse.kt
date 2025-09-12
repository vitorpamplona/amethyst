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
package com.vitorpamplona.quartz.nip46RemoteSigner

import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.SerializerProvider
import com.fasterxml.jackson.databind.deser.std.StdDeserializer
import com.fasterxml.jackson.databind.ser.std.StdSerializer
import com.vitorpamplona.quartz.utils.Hex
import com.vitorpamplona.quartz.utils.bytesUsedInMemory
import com.vitorpamplona.quartz.utils.pointerSizeInBytes

open class BunkerResponse(
    val id: String,
    val result: String?,
    val error: String?,
) : BunkerMessage() {
    override fun countMemory(): Int =
        3 * pointerSizeInBytes + // 3 fields, 4 bytes each reference (32bit)
            id.bytesUsedInMemory() +
            (result?.bytesUsedInMemory() ?: 0) +
            (error?.bytesUsedInMemory() ?: 0)

    class BunkerResponseDeserializer : StdDeserializer<BunkerResponse>(BunkerResponse::class.java) {
        override fun deserialize(
            jp: JsonParser,
            ctxt: DeserializationContext,
        ): BunkerResponse {
            val jsonObject: JsonNode = jp.codec.readTree(jp)

            val id = jsonObject.get("id").asText().intern()
            val result = jsonObject.get("result")?.asText()
            val error = jsonObject.get("error")?.asText()

            if (error != null) {
                return com.vitorpamplona.quartz.nip46RemoteSigner.BunkerResponseError
                    .parse(id, result, error)
            }

            if (result != null) {
                when (result) {
                    BunkerResponseAck.RESULT -> return BunkerResponseAck.parse(id, result, error)
                    BunkerResponsePong.RESULT -> return BunkerResponsePong.parse(id, result, error)
                    else -> {
                        if (result.length == 64 && Hex.isHex(result)) {
                            return BunkerResponsePublicKey.parse(id, result)
                        }

                        if (result.get(0) == '{') {
                            try {
                                return com.vitorpamplona.quartz.nip46RemoteSigner.BunkerResponseEvent
                                    .parse(id, result)
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

    class BunkerResponseSerializer : StdSerializer<BunkerResponse>(BunkerResponse::class.java) {
        override fun serialize(
            value: BunkerResponse,
            gen: JsonGenerator,
            provider: SerializerProvider,
        ) {
            gen.writeStartObject()
            gen.writeStringField("id", value.id)
            value.result?.let { gen.writeStringField("result", value.result) }
            value.error?.let { gen.writeStringField("error", it) }
            gen.writeEndObject()
        }
    }
}
