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
package com.vitorpamplona.quartz.nip01Core.relay.commands.toClient

import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.databind.SerializerProvider
import com.fasterxml.jackson.databind.ser.std.StdSerializer
import com.vitorpamplona.quartz.nip01Core.jackson.EventSerializer
import com.vitorpamplona.quartz.nip77Negentropy.NegErrMessage
import com.vitorpamplona.quartz.nip77Negentropy.NegMsgMessage

class MessageSerializer : StdSerializer<Message>(Message::class.java) {
    val eventSerializer = EventSerializer()
    val countSerializer = CountResultSerializer()

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
                // NIP-01 wire format: ["OK", <event_id>, <true|false>, <message>]
                // The third element is a JSON boolean, not a string.
                gen.writeString(msg.eventId)
                gen.writeBoolean(msg.success)
                gen.writeString(msg.message)
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

            is CountMessage -> {
                // NIP-45 wire format: ["COUNT", <query_id>, <count_result>]
                gen.writeString(msg.queryId)
                countSerializer.serialize(msg.result, gen, provider)
            }

            is EoseMessage -> {
                gen.writeString(msg.subId)
            }

            is LimitsMessage -> {
                // LIMITS wire format: ["LIMITS", { <limit_properties> }]. Only
                // the fields the relay set are emitted; absent limits stay absent.
                gen.writeStartObject()
                msg.canWrite?.let { gen.writeBooleanField("can_write", it) }
                msg.canRead?.let { gen.writeBooleanField("can_read", it) }
                msg.authForRead?.let { gen.writeBooleanField("auth_for_read", it) }
                msg.authForWrite?.let { gen.writeBooleanField("auth_for_write", it) }
                msg.acceptedEventKinds?.let {
                    gen.writeArrayFieldStart("accepted_event_kinds")
                    it.forEach { kind -> gen.writeNumber(kind) }
                    gen.writeEndArray()
                }
                msg.blockedEventKinds?.let {
                    gen.writeArrayFieldStart("blocked_event_kinds")
                    it.forEach { kind -> gen.writeNumber(kind) }
                    gen.writeEndArray()
                }
                msg.minPowDifficulty?.let { gen.writeNumberField("min_pow_difficulty", it) }
                msg.maxMessageLength?.let { gen.writeNumberField("max_message_length", it) }
                msg.maxSubscriptions?.let { gen.writeNumberField("max_subscriptions", it) }
                msg.maxFilters?.let { gen.writeNumberField("max_filters", it) }
                msg.maxLimit?.let { gen.writeNumberField("max_limit", it) }
                msg.maxEventTags?.let { gen.writeNumberField("max_event_tags", it) }
                msg.maxContentLength?.let { gen.writeNumberField("max_content_length", it) }
                msg.createdAtMsecsAgo?.let { gen.writeNumberField("created_at_msecs_ago", it) }
                msg.createdAtMsecsAhead?.let { gen.writeNumberField("created_at_msecs_ahead", it) }
                msg.filterRateLimit?.let { gen.writeNumberField("filter_rate_limit", it) }
                msg.publishingRateLimit?.let { gen.writeNumberField("publishing_rate_limit", it) }
                msg.requiredTags?.let {
                    gen.writeArrayFieldStart("required_tags")
                    it.forEach { tag ->
                        gen.writeStartArray()
                        tag.forEach { part -> gen.writeString(part) }
                        gen.writeEndArray()
                    }
                    gen.writeEndArray()
                }
                gen.writeEndObject()
            }

            is NegMsgMessage -> {
                gen.writeString(msg.subId)
                gen.writeString(msg.message)
            }

            is NegErrMessage -> {
                gen.writeString(msg.subId)
                gen.writeString(msg.reason)
            }
        }

        gen.writeEndArray()
    }
}
