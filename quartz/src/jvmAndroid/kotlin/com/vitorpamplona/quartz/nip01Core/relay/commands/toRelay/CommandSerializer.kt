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
package com.vitorpamplona.quartz.nip01Core.relay.commands.toRelay

import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.databind.SerializerProvider
import com.fasterxml.jackson.databind.ser.std.StdSerializer
import com.vitorpamplona.quartz.nip01Core.jackson.EventSerializer
import com.vitorpamplona.quartz.nip01Core.relay.filters.FilterSerializer

class CommandSerializer : StdSerializer<Command>(Command::class.java) {
    val eventSerializer = EventSerializer()
    val filterSerializer = FilterSerializer()

    override fun serialize(
        cmd: Command,
        gen: JsonGenerator,
        provider: SerializerProvider,
    ) {
        gen.writeStartArray()
        gen.writeString(cmd.label())

        when (cmd) {
            is ReqCmd -> {
                gen.writeString(cmd.subId)
                cmd.filters.forEach {
                    filterSerializer.serialize(it, gen, provider)
                }
            }

            is EventCmd -> {
                eventSerializer.serialize(cmd.event, gen, provider)
            }

            is CloseCmd -> {
                gen.writeString(cmd.subId)
            }

            is AuthCmd -> {
                eventSerializer.serialize(cmd.event, gen, provider)
            }

            is CountCmd -> {
                gen.writeString(cmd.queryId)
                cmd.filters.forEach {
                    filterSerializer.serialize(it, gen, provider)
                }
            }

            else -> {
                null
            }
        }

        gen.writeEndArray()
    }
}
