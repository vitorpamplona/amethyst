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

import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.OptimizedSerializable
import com.vitorpamplona.quartz.nip01Core.core.TagArray
import com.vitorpamplona.quartz.nip01Core.relay.commands.toClient.Message
import com.vitorpamplona.quartz.nip01Core.relay.commands.toRelay.Command
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.signers.EventTemplate
import com.vitorpamplona.quartz.nip46RemoteSigner.BunkerMessage
import com.vitorpamplona.quartz.nip46RemoteSigner.BunkerRequest
import com.vitorpamplona.quartz.nip46RemoteSigner.BunkerResponse
import com.vitorpamplona.quartz.nip46RemoteSigner.kotlinSerialization.BunkerMessageKSerializer
import com.vitorpamplona.quartz.nip46RemoteSigner.kotlinSerialization.BunkerRequestKSerializer
import com.vitorpamplona.quartz.nip46RemoteSigner.kotlinSerialization.BunkerResponseKSerializer
import com.vitorpamplona.quartz.nip47WalletConnect.Notification
import com.vitorpamplona.quartz.nip47WalletConnect.Request
import com.vitorpamplona.quartz.nip47WalletConnect.Response
import com.vitorpamplona.quartz.nip47WalletConnect.kotlinSerialization.Nip47NotificationKSerializer
import com.vitorpamplona.quartz.nip47WalletConnect.kotlinSerialization.Nip47RequestKSerializer
import com.vitorpamplona.quartz.nip47WalletConnect.kotlinSerialization.Nip47ResponseKSerializer
import com.vitorpamplona.quartz.nip59Giftwrap.rumors.Rumor
import com.vitorpamplona.quartz.nip59Giftwrap.rumors.kotlinSerialization.RumorKSerializer
import kotlinx.serialization.json.Json

class KotlinSerializationMapper {
    companion object {
        val json =
            Json {
                ignoreUnknownKeys = true
                isLenient = true
                encodeDefaults = false
            }

        fun fromJson(jsonStr: String): Event = json.decodeFromString(EventKSerializer, jsonStr)

        fun fromJsonToMessage(jsonStr: String): Message = json.decodeFromString(MessageKSerializer, jsonStr)

        fun fromJsonToCommand(jsonStr: String): Command = json.decodeFromString(CommandKSerializer, jsonStr)

        fun fromJsonToTagArray(jsonStr: String): TagArray = json.decodeFromString(TagArrayKSerializer, jsonStr)

        fun fromJsonToRumor(jsonStr: String): Rumor = json.decodeFromString(RumorKSerializer, jsonStr)

        fun fromJsonToEventTemplate(jsonStr: String): EventTemplate<Event> = json.decodeFromString(EventTemplateKSerializer, jsonStr)

        fun toJson(event: Event): String = json.encodeToString(EventKSerializer, event)

        fun toJson(tags: TagArray): String = json.encodeToString(TagArrayKSerializer, tags)

        fun toJson(value: OptimizedSerializable): String =
            when (value) {
                is Event -> json.encodeToString(EventKSerializer, value)
                is Filter -> json.encodeToString(FilterKSerializer, value)
                is Rumor -> json.encodeToString(RumorKSerializer, value)
                is EventTemplate<*> -> {
                    @Suppress("UNCHECKED_CAST")
                    json.encodeToString(EventTemplateKSerializer, value as EventTemplate<Event>)
                }
                is Message -> json.encodeToString(MessageKSerializer, value)
                is Command -> json.encodeToString(CommandKSerializer, value)
                is BunkerRequest -> json.encodeToString(BunkerRequestKSerializer, value)
                is BunkerResponse -> json.encodeToString(BunkerResponseKSerializer, value)
                is BunkerMessage -> json.encodeToString(BunkerMessageKSerializer, value)
                else -> throw IllegalArgumentException("Unsupported type: ${value::class}")
            }

        inline fun <reified T : OptimizedSerializable> fromJsonTo(jsonStr: String): T {
            val result: Any =
                when (T::class) {
                    Event::class -> fromJson(jsonStr)
                    Filter::class -> json.decodeFromString(FilterKSerializer, jsonStr)
                    Rumor::class -> fromJsonToRumor(jsonStr)
                    EventTemplate::class -> fromJsonToEventTemplate(jsonStr)
                    Message::class -> fromJsonToMessage(jsonStr)
                    Command::class -> fromJsonToCommand(jsonStr)
                    BunkerRequest::class -> json.decodeFromString(BunkerRequestKSerializer, jsonStr)
                    BunkerResponse::class -> json.decodeFromString(BunkerResponseKSerializer, jsonStr)
                    BunkerMessage::class -> json.decodeFromString(BunkerMessageKSerializer, jsonStr)
                    Response::class -> json.decodeFromString(Nip47ResponseKSerializer, jsonStr)
                    Request::class -> json.decodeFromString(Nip47RequestKSerializer, jsonStr)
                    Notification::class -> json.decodeFromString(Nip47NotificationKSerializer, jsonStr)
                    else -> throw IllegalArgumentException("Unsupported type: ${T::class}")
                }
            @Suppress("UNCHECKED_CAST")
            return result as T
        }
    }
}
