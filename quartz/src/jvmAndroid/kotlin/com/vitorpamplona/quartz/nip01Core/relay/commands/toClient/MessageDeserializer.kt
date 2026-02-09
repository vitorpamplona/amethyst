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

import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.core.JsonToken
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.deser.std.StdDeserializer
import com.vitorpamplona.quartz.nip01Core.jackson.EventDeserializer

class MessageDeserializer : StdDeserializer<Message>(Message::class.java) {
    val eventDeserializer = EventDeserializer()

    override fun deserialize(
        jp: JsonParser,
        ctxt: DeserializationContext,
    ): Message {
        // Expect to start with a JSON array token
        if (jp.currentToken != JsonToken.START_ARRAY) {
            ctxt.reportWrongTokenException(this, JsonToken.START_ARRAY, "Expected START_ARRAY token")
        }

        val message =
            when (val type = jp.nextTextValue()) {
                EventMessage.LABEL -> {
                    val subId = jp.nextTextValue()
                    jp.nextToken()

                    EventMessage(
                        subId = subId,
                        event = eventDeserializer.deserialize(jp, ctxt),
                    )
                }

                EoseMessage.LABEL -> {
                    EoseMessage(
                        subId = jp.nextTextValue(),
                    )
                }

                NoticeMessage.LABEL -> {
                    NoticeMessage(
                        message = jp.nextTextValue(),
                    )
                }

                OkMessage.LABEL -> {
                    OkMessage(
                        eventId = jp.nextTextValue(),
                        success = jp.nextBooleanValue(),
                        message = jp.nextTextValue() ?: "",
                    )
                }

                AuthMessage.LABEL -> {
                    AuthMessage(
                        challenge = jp.nextTextValue(),
                    )
                }

                NotifyMessage.LABEL -> {
                    NotifyMessage(
                        message = jp.nextTextValue(),
                    )
                }

                ClosedMessage.LABEL -> {
                    ClosedMessage(
                        subId = jp.nextTextValue(),
                        message = jp.nextTextValue() ?: "",
                    )
                }

                CountMessage.LABEL -> {
                    val queryId = jp.nextTextValue()
                    jp.nextToken()
                    val result: JsonNode = jp.codec.readTree(jp)

                    CountMessage(
                        queryId = queryId,
                        result = CountResultDeserializer.fromJson(result),
                    )
                }

                else -> {
                    throw IllegalArgumentException("Message $type is not supported")
                }
            }

        if (jp.currentToken != JsonToken.END_ARRAY) {
            // cleaning out other array elements
            while (jp.nextToken() != JsonToken.END_ARRAY) {
                // clears out
            }
        }

        return message
    }
}
