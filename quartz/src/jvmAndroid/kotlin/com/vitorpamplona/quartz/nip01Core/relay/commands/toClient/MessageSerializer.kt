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
package com.vitorpamplona.quartz.nip01Core.relay.commands.toClient

import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.databind.SerializerProvider
import com.fasterxml.jackson.databind.ser.std.StdSerializer
import com.vitorpamplona.quartz.nip01Core.jackson.EventSerializer

class MessageSerializer : StdSerializer<Message>(Message::class.java) {
    val eventSerializer = EventSerializer()

    override fun serialize(
        msg: Message,
        gen: JsonGenerator,
        provider: SerializerProvider,
    ) {
        gen.writeStartArray()
        gen.writeString(msg.label())

        when (msg) {
            is EventMessage -> {
                gen.writeString(msg.subId)
                eventSerializer.serialize(msg.event, gen, provider)
            }

            is NoticeMessage -> {
                gen.writeString(msg.message)
            }

            is OkMessage -> {
                gen.writeString(msg.eventId)
                gen.writeString(msg.success.toString())
                if (msg.message.isNotBlank()) {
                    gen.writeString(msg.message)
                }
            }

            is AuthMessage -> {
                gen.writeString(msg.challenge)
            }

            is NotifyMessage -> {
                gen.writeString(msg.message)
            }

            is ClosedMessage -> {
                gen.writeString(msg.subId)
                gen.writeString(msg.message)
            }

            else -> null
        }

        gen.writeEndArray()
    }
}
