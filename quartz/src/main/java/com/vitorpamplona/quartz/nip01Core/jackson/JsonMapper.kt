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
package com.vitorpamplona.quartz.nip01Core.jackson

import com.fasterxml.jackson.core.json.JsonReadFeature
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.module.SimpleModule
import com.fasterxml.jackson.databind.node.ArrayNode
import com.fasterxml.jackson.databind.node.ObjectNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip46RemoteSigner.BunkerMessage
import com.vitorpamplona.quartz.nip46RemoteSigner.BunkerRequest
import com.vitorpamplona.quartz.nip46RemoteSigner.BunkerResponse
import com.vitorpamplona.quartz.nip47WalletConnect.Request
import com.vitorpamplona.quartz.nip47WalletConnect.RequestDeserializer
import com.vitorpamplona.quartz.nip47WalletConnect.Response
import com.vitorpamplona.quartz.nip47WalletConnect.ResponseDeserializer
import com.vitorpamplona.quartz.nip55AndroidSigner.api.foreground.intents.results.IntentResult
import com.vitorpamplona.quartz.nip55AndroidSigner.api.foreground.intents.results.IntentResultJsonDeserializer
import com.vitorpamplona.quartz.nip55AndroidSigner.api.foreground.intents.results.IntentResultJsonSerializer
import com.vitorpamplona.quartz.nip55AndroidSigner.api.permission.Permission
import com.vitorpamplona.quartz.nip55AndroidSigner.api.permission.PermissionDeserializer
import com.vitorpamplona.quartz.nip55AndroidSigner.api.permission.PermissionSerializer
import com.vitorpamplona.quartz.nip59Giftwrap.rumors.Rumor
import com.vitorpamplona.quartz.nip59Giftwrap.rumors.RumorDeserializer
import com.vitorpamplona.quartz.nip59Giftwrap.rumors.RumorSerializer

class JsonMapper {
    companion object {
        val defaultPrettyPrinter = InliningTagArrayPrettyPrinter()

        val mapper =
            jacksonObjectMapper()
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
                .enable(JsonReadFeature.ALLOW_UNESCAPED_CONTROL_CHARS.mappedFeature())
                .setDefaultPrettyPrinter(defaultPrettyPrinter)
                .registerModule(
                    SimpleModule()
                        .addSerializer(Event::class.java, EventSerializer())
                        .addDeserializer(Event::class.java, EventDeserializer())
                        .addSerializer(Rumor::class.java, RumorSerializer())
                        .addDeserializer(Rumor::class.java, RumorDeserializer())
                        .addDeserializer(Response::class.java, ResponseDeserializer())
                        .addDeserializer(Request::class.java, RequestDeserializer())
                        .addDeserializer(BunkerMessage::class.java, BunkerMessage.BunkerMessageDeserializer())
                        .addSerializer(BunkerRequest::class.java, BunkerRequest.BunkerRequestSerializer())
                        .addDeserializer(BunkerRequest::class.java, BunkerRequest.BunkerRequestDeserializer())
                        .addSerializer(BunkerResponse::class.java, BunkerResponse.BunkerResponseSerializer())
                        .addDeserializer(BunkerResponse::class.java, BunkerResponse.BunkerResponseDeserializer())
                        .addDeserializer(Permission::class.java, PermissionDeserializer())
                        .addSerializer(Permission::class.java, PermissionSerializer())
                        .addDeserializer(IntentResult::class.java, IntentResultJsonDeserializer())
                        .addSerializer(IntentResult::class.java, IntentResultJsonSerializer())
                        .addDeserializer(Array<Array<String>>::class.java, TagArrayDeserializer()),
                )

        fun fromJson(json: String): Event = mapper.readValue(json, Event::class.java)

        fun fromJson(json: JsonNode): Event = EventManualDeserializer.fromJson(json)

        fun toJson(event: Event): String = mapper.writeValueAsString(event)

        fun toJson(event: ArrayNode?): String = mapper.writeValueAsString(event)

        fun toJson(event: ObjectNode?): String = mapper.writeValueAsString(event)
    }
}
