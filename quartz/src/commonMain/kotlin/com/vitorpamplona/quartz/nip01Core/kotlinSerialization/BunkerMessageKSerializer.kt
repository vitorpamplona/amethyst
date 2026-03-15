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

import com.vitorpamplona.quartz.nip46RemoteSigner.BunkerMessage
import com.vitorpamplona.quartz.nip46RemoteSigner.BunkerRequest
import com.vitorpamplona.quartz.nip46RemoteSigner.BunkerResponse
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

object BunkerMessageKSerializer : KSerializer<BunkerMessage> {
    override val descriptor: SerialDescriptor =
        buildClassSerialDescriptor("BunkerMessage")

    override fun serialize(
        encoder: Encoder,
        value: BunkerMessage,
    ) {
        val jsonEncoder = encoder as JsonEncoder
        when (value) {
            is BunkerRequest -> BunkerRequestKSerializer.serialize(jsonEncoder, value)
            is BunkerResponse -> BunkerResponseKSerializer.serialize(jsonEncoder, value)
            else -> throw IllegalArgumentException("Unknown BunkerMessage type")
        }
    }

    override fun deserialize(decoder: Decoder): BunkerMessage {
        val jsonDecoder = decoder as JsonDecoder
        val jsonObject = jsonDecoder.decodeJsonElement().jsonObject
        val isRequest = jsonObject.containsKey("method")

        return if (isRequest) {
            val id = jsonObject["id"]!!.jsonPrimitive.content
            val method = jsonObject["method"]!!.jsonPrimitive.content
            val params =
                jsonObject["params"]?.jsonArray?.map { it.jsonPrimitive.content }?.toTypedArray()
                    ?: emptyArray()
            dispatchBunkerRequest(id, method, params)
        } else {
            BunkerResponseKSerializer.deserializeFromElement(jsonObject)
        }
    }

    private fun dispatchBunkerRequest(
        id: String,
        method: String,
        params: Array<String>,
    ): BunkerRequest {
        return when (method) {
            com.vitorpamplona.quartz.nip46RemoteSigner.BunkerRequestConnect.METHOD_NAME ->
                com.vitorpamplona.quartz.nip46RemoteSigner.BunkerRequestConnect.parse(id, params)
            com.vitorpamplona.quartz.nip46RemoteSigner.BunkerRequestGetPublicKey.METHOD_NAME ->
                com.vitorpamplona.quartz.nip46RemoteSigner.BunkerRequestGetPublicKey.parse(id, params)
            com.vitorpamplona.quartz.nip46RemoteSigner.BunkerRequestGetRelays.METHOD_NAME ->
                com.vitorpamplona.quartz.nip46RemoteSigner.BunkerRequestGetRelays.parse(id, params)
            com.vitorpamplona.quartz.nip46RemoteSigner.BunkerRequestNip04Decrypt.METHOD_NAME ->
                com.vitorpamplona.quartz.nip46RemoteSigner.BunkerRequestNip04Decrypt.parse(id, params)
            com.vitorpamplona.quartz.nip46RemoteSigner.BunkerRequestNip04Encrypt.METHOD_NAME ->
                com.vitorpamplona.quartz.nip46RemoteSigner.BunkerRequestNip04Encrypt.parse(id, params)
            com.vitorpamplona.quartz.nip46RemoteSigner.BunkerRequestNip44Decrypt.METHOD_NAME ->
                com.vitorpamplona.quartz.nip46RemoteSigner.BunkerRequestNip44Decrypt.parse(id, params)
            com.vitorpamplona.quartz.nip46RemoteSigner.BunkerRequestNip44Encrypt.METHOD_NAME ->
                com.vitorpamplona.quartz.nip46RemoteSigner.BunkerRequestNip44Encrypt.parse(id, params)
            com.vitorpamplona.quartz.nip46RemoteSigner.BunkerRequestPing.METHOD_NAME ->
                com.vitorpamplona.quartz.nip46RemoteSigner.BunkerRequestPing.parse(id, params)
            com.vitorpamplona.quartz.nip46RemoteSigner.BunkerRequestSign.METHOD_NAME ->
                com.vitorpamplona.quartz.nip46RemoteSigner.BunkerRequestSign.parse(id, params)
            else -> BunkerRequest(id, method, params)
        }
    }
}
