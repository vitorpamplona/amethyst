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
package com.vitorpamplona.quartz.nip46RemoteSigner.jackson

import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.deser.std.StdDeserializer
import com.vitorpamplona.quartz.nip01Core.jackson.toTypedArray
import com.vitorpamplona.quartz.nip46RemoteSigner.BunkerRequest
import com.vitorpamplona.quartz.nip46RemoteSigner.BunkerRequestConnect
import com.vitorpamplona.quartz.nip46RemoteSigner.BunkerRequestGetPublicKey
import com.vitorpamplona.quartz.nip46RemoteSigner.BunkerRequestGetRelays
import com.vitorpamplona.quartz.nip46RemoteSigner.BunkerRequestNip04Decrypt
import com.vitorpamplona.quartz.nip46RemoteSigner.BunkerRequestNip04Encrypt
import com.vitorpamplona.quartz.nip46RemoteSigner.BunkerRequestNip44Decrypt
import com.vitorpamplona.quartz.nip46RemoteSigner.BunkerRequestNip44Encrypt
import com.vitorpamplona.quartz.nip46RemoteSigner.BunkerRequestPing
import com.vitorpamplona.quartz.nip46RemoteSigner.BunkerRequestSign

class BunkerRequestDeserializer : StdDeserializer<BunkerRequest>(BunkerRequest::class.java) {
    override fun deserialize(
        jp: JsonParser,
        ctxt: DeserializationContext,
    ): BunkerRequest {
        val jsonObject: JsonNode = jp.codec.readTree(jp)
        val id = jsonObject.get("id").asText().intern()
        val method = jsonObject.get("method").asText().intern()
        val params = jsonObject.get("params")?.toTypedArray { it.asText().intern() } ?: emptyArray()

        return when (method) {
            BunkerRequestConnect.METHOD_NAME -> BunkerRequestConnect.parse(id, params)
            BunkerRequestGetPublicKey.METHOD_NAME -> BunkerRequestGetPublicKey.parse(id, params)
            BunkerRequestGetRelays.METHOD_NAME -> BunkerRequestGetRelays.parse(id, params)

            BunkerRequestNip04Decrypt.METHOD_NAME -> BunkerRequestNip04Decrypt.parse(id, params)
            BunkerRequestNip04Encrypt.METHOD_NAME -> BunkerRequestNip04Encrypt.parse(id, params)
            BunkerRequestNip44Decrypt.METHOD_NAME -> BunkerRequestNip44Decrypt.parse(id, params)
            BunkerRequestNip44Encrypt.METHOD_NAME -> BunkerRequestNip44Encrypt.parse(id, params)

            BunkerRequestPing.METHOD_NAME -> BunkerRequestPing.parse(id, params)
            BunkerRequestSign.METHOD_NAME -> BunkerRequestSign.parse(id, params)
            else -> BunkerRequest(id, method, params)
        }
    }
}
