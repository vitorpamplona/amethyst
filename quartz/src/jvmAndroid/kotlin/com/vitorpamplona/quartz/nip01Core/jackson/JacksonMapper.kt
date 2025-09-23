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
import com.fasterxml.jackson.module.kotlin.readValue
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.OptimizedSerializable
import com.vitorpamplona.quartz.nip01Core.core.TagArray
import com.vitorpamplona.quartz.nip01Core.relay.commands.toClient.Message
import com.vitorpamplona.quartz.nip01Core.relay.commands.toClient.MessageDeserializer
import com.vitorpamplona.quartz.nip01Core.relay.commands.toClient.MessageSerializer
import com.vitorpamplona.quartz.nip01Core.relay.commands.toRelay.Command
import com.vitorpamplona.quartz.nip01Core.relay.commands.toRelay.CommandDeserializer
import com.vitorpamplona.quartz.nip01Core.relay.commands.toRelay.CommandSerializer
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.relay.filters.FilterDeserializer
import com.vitorpamplona.quartz.nip01Core.relay.filters.FilterSerializer
import com.vitorpamplona.quartz.nip01Core.signers.EventTemplate
import com.vitorpamplona.quartz.nip01Core.signers.EventTemplateDeserializer
import com.vitorpamplona.quartz.nip01Core.signers.EventTemplateSerializer
import com.vitorpamplona.quartz.nip46RemoteSigner.BunkerMessage
import com.vitorpamplona.quartz.nip46RemoteSigner.BunkerRequest
import com.vitorpamplona.quartz.nip46RemoteSigner.BunkerResponse
import com.vitorpamplona.quartz.nip46RemoteSigner.jackson.BunkerMessageDeserializer
import com.vitorpamplona.quartz.nip46RemoteSigner.jackson.BunkerRequestDeserializer
import com.vitorpamplona.quartz.nip46RemoteSigner.jackson.BunkerRequestSerializer
import com.vitorpamplona.quartz.nip46RemoteSigner.jackson.BunkerResponseDeserializer
import com.vitorpamplona.quartz.nip46RemoteSigner.jackson.BunkerResponseSerializer
import com.vitorpamplona.quartz.nip47WalletConnect.Request
import com.vitorpamplona.quartz.nip47WalletConnect.Response
import com.vitorpamplona.quartz.nip47WalletConnect.jackson.RequestDeserializer
import com.vitorpamplona.quartz.nip47WalletConnect.jackson.ResponseDeserializer
import com.vitorpamplona.quartz.nip59Giftwrap.rumors.Rumor
import com.vitorpamplona.quartz.nip59Giftwrap.rumors.jackson.RumorDeserializer
import com.vitorpamplona.quartz.nip59Giftwrap.rumors.jackson.RumorSerializer
import java.io.InputStream
import kotlin.jvm.java

class JacksonMapper {
    companion object {
        val defaultPrettyPrinter = InliningTagArrayPrettyPrinter()

        val mapper =
            jacksonObjectMapper()
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
                .enable(JsonReadFeature.ALLOW_UNESCAPED_CONTROL_CHARS.mappedFeature())
                .setDefaultPrettyPrinter(defaultPrettyPrinter)
                .registerModule(
                    SimpleModule()
                        // nip 01
                        .addSerializer(Event::class.java, EventSerializer())
                        .addDeserializer(Event::class.java, EventDeserializer())
                        .addSerializer(Filter::class.java, FilterSerializer())
                        .addDeserializer(Filter::class.java, FilterDeserializer())
                        .addSerializer(Message::class.java, MessageSerializer())
                        .addDeserializer(Message::class.java, MessageDeserializer())
                        .addSerializer(Command::class.java, CommandSerializer())
                        .addDeserializer(Command::class.java, CommandDeserializer())
                        .addDeserializer(TagArray::class.java, TagArrayDeserializer())
                        .addSerializer(TagArray::class.java, TagArraySerializer())
                        .addDeserializer(EventTemplate::class.java, EventTemplateDeserializer())
                        .addSerializer(EventTemplate::class.java, EventTemplateSerializer())
                        // nip 59
                        .addSerializer(Rumor::class.java, RumorSerializer())
                        .addDeserializer(Rumor::class.java, RumorDeserializer())
                        // nip 47
                        .addDeserializer(Response::class.java, ResponseDeserializer())
                        .addDeserializer(Request::class.java, RequestDeserializer())
                        // nip 46
                        .addDeserializer(BunkerMessage::class.java, BunkerMessageDeserializer())
                        .addSerializer(BunkerRequest::class.java, BunkerRequestSerializer())
                        .addDeserializer(BunkerRequest::class.java, BunkerRequestDeserializer())
                        .addSerializer(BunkerResponse::class.java, BunkerResponseSerializer())
                        .addDeserializer(BunkerResponse::class.java, BunkerResponseDeserializer()),
                )

        fun fromJson(json: String): Event = mapper.readValue<Event>(json)

        fun fromJson(json: JsonNode): Event = EventManualDeserializer.fromJson(json)

        fun fromJsonToTagArray(json: String): Array<Array<String>> = mapper.readValue<Array<Array<String>>>(json)

        inline fun <reified T : OptimizedSerializable> fromJsonTo(json: String): T = mapper.readValue<T>(json)

        inline fun <reified T : OptimizedSerializable> fromJsonTo(json: InputStream): T = mapper.readValue<T>(json)

        fun toJson(event: Event): String = EventManualSerializer.toJson(event.id, event.pubKey, event.createdAt, event.kind, event.tags, event.content, event.sig)

        fun toJson(event: ArrayNode): String = mapper.writeValueAsString(event)

        fun toJson(event: ObjectNode?): String = mapper.writeValueAsString(event)

        fun toJson(value: OptimizedSerializable): String = mapper.writeValueAsString(value)

        fun toJson(tags: Array<Array<String>>): String = mapper.writeValueAsString(tags)
    }
}
