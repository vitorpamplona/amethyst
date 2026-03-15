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

import com.vitorpamplona.quartz.nip01Core.relay.commands.toClient.AuthMessage
import com.vitorpamplona.quartz.nip01Core.relay.commands.toClient.ClosedMessage
import com.vitorpamplona.quartz.nip01Core.relay.commands.toClient.CountMessage
import com.vitorpamplona.quartz.nip01Core.relay.commands.toClient.EoseMessage
import com.vitorpamplona.quartz.nip01Core.relay.commands.toClient.EventMessage
import com.vitorpamplona.quartz.nip01Core.relay.commands.toClient.Message
import com.vitorpamplona.quartz.nip01Core.relay.commands.toClient.NoticeMessage
import com.vitorpamplona.quartz.nip01Core.relay.commands.toClient.NotifyMessage
import com.vitorpamplona.quartz.nip01Core.relay.commands.toClient.OkMessage
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

object MessageKSerializer : KSerializer<Message> {
    override val descriptor: SerialDescriptor =
        buildClassSerialDescriptor("Message")

    override fun serialize(
        encoder: Encoder,
        value: Message,
    ) {
        val jsonEncoder = encoder as JsonEncoder
        val element =
            buildJsonArray {
                add(JsonPrimitive(value.label()))
                when (value) {
                    is EventMessage -> {
                        add(JsonPrimitive(value.subId))
                        add(EventKSerializer.serializeToElement(value.event))
                    }
                    is NoticeMessage -> {
                        add(JsonPrimitive(value.message))
                    }
                    is OkMessage -> {
                        add(JsonPrimitive(value.eventId))
                        // Jackson writes success as a string, not boolean
                        add(JsonPrimitive(value.success.toString()))
                        if (value.message.isNotBlank()) {
                            add(JsonPrimitive(value.message))
                        }
                    }
                    is AuthMessage -> {
                        add(JsonPrimitive(value.challenge))
                    }
                    is NotifyMessage -> {
                        add(JsonPrimitive(value.message))
                    }
                    is ClosedMessage -> {
                        add(JsonPrimitive(value.subId))
                        add(JsonPrimitive(value.message))
                    }
                    is CountMessage -> {
                        add(CountResultKSerializer.serializeToElement(value.result))
                    }
                }
            }
        jsonEncoder.encodeJsonElement(element)
    }

    override fun deserialize(decoder: Decoder): Message {
        val jsonDecoder = decoder as JsonDecoder
        val array = jsonDecoder.decodeJsonElement().jsonArray
        val type = array[0].jsonPrimitive.content

        return when (type) {
            EventMessage.LABEL -> {
                val subId = array[1].jsonPrimitive.content
                val event = EventKSerializer.deserializeFromElement(array[2].jsonObject)
                EventMessage(subId, event)
            }
            EoseMessage.LABEL -> {
                EoseMessage(array[1].jsonPrimitive.content)
            }
            NoticeMessage.LABEL -> {
                NoticeMessage(array[1].jsonPrimitive.content)
            }
            OkMessage.LABEL -> {
                OkMessage(
                    eventId = array[1].jsonPrimitive.content,
                    success = array[2].jsonPrimitive.boolean,
                    message = if (array.size > 3) array[3].jsonPrimitive.content else "",
                )
            }
            AuthMessage.LABEL -> {
                AuthMessage(array[1].jsonPrimitive.content)
            }
            NotifyMessage.LABEL -> {
                NotifyMessage(array[1].jsonPrimitive.content)
            }
            ClosedMessage.LABEL -> {
                ClosedMessage(
                    subId = array[1].jsonPrimitive.content,
                    message = if (array.size > 2) array[2].jsonPrimitive.content else "",
                )
            }
            CountMessage.LABEL -> {
                val queryId = array[1].jsonPrimitive.content
                val result = CountResultKSerializer.deserializeFromElement(array[2].jsonObject)
                CountMessage(queryId, result)
            }
            else -> throw IllegalArgumentException("Message $type is not supported")
        }
    }
}
