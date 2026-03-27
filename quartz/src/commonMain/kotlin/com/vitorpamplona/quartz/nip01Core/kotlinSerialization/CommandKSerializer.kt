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

import com.vitorpamplona.quartz.nip01Core.relay.commands.toRelay.AuthCmd
import com.vitorpamplona.quartz.nip01Core.relay.commands.toRelay.CloseCmd
import com.vitorpamplona.quartz.nip01Core.relay.commands.toRelay.Command
import com.vitorpamplona.quartz.nip01Core.relay.commands.toRelay.CountCmd
import com.vitorpamplona.quartz.nip01Core.relay.commands.toRelay.EventCmd
import com.vitorpamplona.quartz.nip01Core.relay.commands.toRelay.ReqCmd
import com.vitorpamplona.quartz.nip42RelayAuth.RelayAuthEvent
import com.vitorpamplona.quartz.nip77Negentropy.NegCloseCmd
import com.vitorpamplona.quartz.nip77Negentropy.NegMsgCmd
import com.vitorpamplona.quartz.nip77Negentropy.NegOpenCmd
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

object CommandKSerializer : KSerializer<Command> {
    override val descriptor: SerialDescriptor =
        buildClassSerialDescriptor("Command")

    override fun serialize(
        encoder: Encoder,
        value: Command,
    ) {
        val jsonEncoder = encoder as JsonEncoder
        val element =
            buildJsonArray {
                add(JsonPrimitive(value.label()))
                when (value) {
                    is ReqCmd -> {
                        add(JsonPrimitive(value.subId))
                        for (filter in value.filters) {
                            add(FilterKSerializer.serializeToElement(filter))
                        }
                    }

                    is EventCmd -> {
                        add(EventKSerializer.serializeToElement(value.event))
                    }

                    is CloseCmd -> {
                        add(JsonPrimitive(value.subId))
                    }

                    is AuthCmd -> {
                        add(EventKSerializer.serializeToElement(value.event))
                    }

                    is CountCmd -> {
                        add(JsonPrimitive(value.queryId))
                        for (filter in value.filters) {
                            add(FilterKSerializer.serializeToElement(filter))
                        }
                    }

                    is NegOpenCmd -> {
                        add(JsonPrimitive(value.subId))
                        add(FilterKSerializer.serializeToElement(value.filter))
                        add(JsonPrimitive(value.initialMessage))
                    }

                    is NegMsgCmd -> {
                        add(JsonPrimitive(value.subId))
                        add(JsonPrimitive(value.message))
                    }

                    is NegCloseCmd -> {
                        add(JsonPrimitive(value.subId))
                    }
                }
            }
        jsonEncoder.encodeJsonElement(element)
    }

    override fun deserialize(decoder: Decoder): Command {
        val jsonDecoder = decoder as JsonDecoder
        val array = jsonDecoder.decodeJsonElement().jsonArray
        val type = array[0].jsonPrimitive.content

        return when (type) {
            ReqCmd.LABEL -> {
                val subId = array[1].jsonPrimitive.content
                val filters =
                    (2 until array.size).map { i ->
                        FilterKSerializer.deserializeFromElement(array[i].jsonObject)
                    }
                ReqCmd(subId, filters)
            }

            CountCmd.LABEL -> {
                val queryId = array[1].jsonPrimitive.content
                val filters =
                    (2 until array.size).map { i ->
                        FilterKSerializer.deserializeFromElement(array[i].jsonObject)
                    }
                CountCmd(queryId, filters)
            }

            EventCmd.LABEL -> {
                EventCmd(EventKSerializer.deserializeFromElement(array[1].jsonObject))
            }

            CloseCmd.LABEL -> {
                CloseCmd(array[1].jsonPrimitive.content)
            }

            AuthCmd.LABEL -> {
                AuthCmd(EventKSerializer.deserializeFromElement(array[1].jsonObject) as RelayAuthEvent)
            }

            NegOpenCmd.LABEL -> {
                NegOpenCmd(
                    subId = array[1].jsonPrimitive.content,
                    filter = FilterKSerializer.deserializeFromElement(array[2].jsonObject),
                    initialMessage = array[3].jsonPrimitive.content,
                )
            }

            NegMsgCmd.LABEL -> {
                NegMsgCmd(
                    subId = array[1].jsonPrimitive.content,
                    message = array[2].jsonPrimitive.content,
                )
            }

            NegCloseCmd.LABEL -> {
                NegCloseCmd(
                    subId = array[1].jsonPrimitive.content,
                )
            }

            else -> {
                throw IllegalArgumentException("Message $type is not supported")
            }
        }
    }
}
