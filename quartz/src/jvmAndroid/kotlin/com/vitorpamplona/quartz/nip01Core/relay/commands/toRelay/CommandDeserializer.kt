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
package com.vitorpamplona.quartz.nip01Core.relay.commands.toRelay

import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.core.JsonToken
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.deser.std.StdDeserializer
import com.fasterxml.jackson.databind.node.ObjectNode
import com.vitorpamplona.quartz.nip01Core.jackson.EventManualDeserializer
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.relay.filters.ManualFilterDeserializer
import com.vitorpamplona.quartz.nip42RelayAuth.RelayAuthEvent

class CommandDeserializer : StdDeserializer<Command>(Command::class.java) {
    override fun deserialize(
        jp: JsonParser,
        ctxt: DeserializationContext,
    ): Command? {
        // Expect to start with a JSON array token
        if (jp.currentToken != JsonToken.START_ARRAY) {
            ctxt.reportWrongTokenException(this, JsonToken.START_ARRAY, "Expected START_ARRAY token")
        }

        val type = jp.nextTextValue()
        val message =
            when (type) {
                ReqCmd.LABEL -> {
                    val subId = jp.nextTextValue()
                    val filters = mutableListOf<Filter>()

                    while (jp.nextToken() != JsonToken.END_ARRAY) {
                        val filterObj: ObjectNode = jp.codec.readTree(jp)
                        val filter = ManualFilterDeserializer.fromJson(filterObj)
                        filters.add(filter)
                    }

                    ReqCmd(
                        subId = subId,
                        filters = filters,
                    )
                }

                CountCmd.LABEL -> {
                    val queryId = jp.nextTextValue()
                    val filters = mutableListOf<Filter>()

                    while (jp.nextToken() != JsonToken.END_ARRAY) {
                        val filterObj: ObjectNode = jp.codec.readTree(jp)
                        val filter = ManualFilterDeserializer.fromJson(filterObj)
                        filters.add(filter)
                    }

                    CountCmd(
                        queryId = queryId,
                        filters = filters,
                    )
                }

                EventCmd.LABEL -> {
                    jp.nextToken()
                    val event: JsonNode = jp.codec.readTree(jp)

                    EventCmd(
                        event = EventManualDeserializer.fromJson(event),
                    )
                }

                CloseCmd.LABEL ->
                    CloseCmd(
                        subId = jp.nextTextValue(),
                    )

                AuthCmd.LABEL -> {
                    jp.nextToken()
                    val event: JsonNode = jp.codec.readTree(jp)
                    AuthCmd(
                        event = EventManualDeserializer.fromJson(event) as RelayAuthEvent,
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
