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
package com.vitorpamplona.quartz.nip01Core.signers

import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.databind.SerializerProvider
import com.fasterxml.jackson.databind.ser.std.StdSerializer

class EventTemplateSerializer : StdSerializer<EventTemplate<*>>(EventTemplate::class.java) {
    override fun serialize(
        event: EventTemplate<*>,
        gen: JsonGenerator,
        provider: SerializerProvider,
    ) {
        gen.writeStartObject()
        gen.writeNumberField("created_at", event.createdAt)
        gen.writeNumberField("kind", event.kind)
        gen.writeArrayFieldStart("tags")
        for (i in event.tags.indices) {
            gen.writeStartArray()
            for (j in event.tags[i].indices) {
                gen.writeString(event.tags[i][j])
            }
            gen.writeEndArray()
        }
        gen.writeEndArray()
        gen.writeStringField("content", event.content)
        gen.writeEndObject()
    }
}
