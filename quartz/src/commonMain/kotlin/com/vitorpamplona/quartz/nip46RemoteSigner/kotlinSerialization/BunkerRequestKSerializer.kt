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

import com.vitorpamplona.quartz.nip46RemoteSigner.BunkerRequest
import com.vitorpamplona.quartz.nip46RemoteSigner.BunkerRequestParser
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.descriptors.element
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

object BunkerRequestKSerializer : KSerializer<BunkerRequest> {
    override val descriptor: SerialDescriptor =
        buildClassSerialDescriptor("BunkerRequest") {
            element<String>("id")
            element<String>("method")
            element<List<String>>("params")
        }

    override fun serialize(
        encoder: Encoder,
        value: BunkerRequest,
    ) {
        val jsonEncoder = encoder as JsonEncoder
        val element =
            buildJsonObject {
                put("id", value.id)
                put("method", value.method)
                put(
                    "params",
                    buildJsonArray {
                        for (p in value.params) {
                            add(JsonPrimitive(p))
                        }
                    },
                )
            }
        jsonEncoder.encodeJsonElement(element)
    }

    override fun deserialize(decoder: Decoder): BunkerRequest {
        val jsonDecoder = decoder as JsonDecoder
        val jsonObject = jsonDecoder.decodeJsonElement().jsonObject
        val id = jsonObject["id"]!!.jsonPrimitive.content
        val method = jsonObject["method"]!!.jsonPrimitive.content
        val params = lenientParams(jsonObject["params"])

        return BunkerRequestParser.parse(id, method, params)
    }

    /**
     * `params` as strings, tolerating a missing or non-array value and non-string
     * elements (kept as their JSON text), so a malformed request still yields its id
     * and method and can be answered with an error.
     */
    fun lenientParams(element: JsonElement?): Array<String> =
        (element as? JsonArray)
            ?.map { (it as? JsonPrimitive)?.content ?: it.toString() }
            ?.toTypedArray()
            ?: emptyArray()
}
