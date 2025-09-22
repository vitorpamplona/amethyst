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
package com.vitorpamplona.quartz.nip01Core.relay.filters

import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.databind.SerializerProvider
import com.fasterxml.jackson.databind.ser.std.StdSerializer

class FilterSerializer : StdSerializer<Filter>(Filter::class.java) {
    override fun serialize(
        filter: Filter,
        gen: JsonGenerator,
        provider: SerializerProvider,
    ) {
        gen.writeStartObject()

        filter.ids?.run {
            gen.writeArrayFieldStart("ids")
            for (i in indices) {
                gen.writeString(this[i])
            }
            gen.writeEndArray()
        }

        filter.authors?.run {
            gen.writeArrayFieldStart("authors")
            for (i in indices) {
                gen.writeString(this[i])
            }
            gen.writeEndArray()
        }

        filter.kinds?.run {
            gen.writeArrayFieldStart("kinds")
            for (i in indices) {
                gen.writeNumber(this[i])
            }
            gen.writeEndArray()
        }

        filter.tags?.run {
            entries.forEach { kv ->
                gen.writeArrayFieldStart("#${kv.key}")
                for (i in kv.value.indices) {
                    gen.writeString(kv.value[i])
                }
                gen.writeEndArray()
            }
        }

        filter.since?.run { gen.writeNumberField("since", this) }
        filter.until?.run { gen.writeNumberField("until", this) }
        filter.limit?.run { gen.writeNumberField("limit", this) }
        filter.search?.run { gen.writeStringField("search", this) }

        gen.writeEndObject()
    }
}
