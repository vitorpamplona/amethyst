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

import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.deser.std.StdDeserializer
import com.fasterxml.jackson.databind.node.ObjectNode

class FilterDeserializer : StdDeserializer<Filter>(Filter::class.java) {
    override fun deserialize(
        jp: JsonParser,
        ctxt: DeserializationContext,
    ): Filter = ManualFilterDeserializer.fromJson(jp.codec.readTree(jp))
}

class ManualFilterDeserializer {
    companion object {
        fun fromJson(jsonObject: ObjectNode): Filter {
            val tags = mutableListOf<String>()
            jsonObject.fieldNames().forEach {
                if (it.startsWith("#")) {
                    tags.add(it.substring(1))
                }
            }

            return Filter(
                ids = jsonObject.get("ids").map { it.asText() },
                authors = jsonObject.get("authors").map { it.asText() },
                kinds = jsonObject.get("kinds").map { it.asInt() },
                tags = tags.associateWith { jsonObject.get(it).map { it.asText() } },
                since = jsonObject.get("since").asLong(),
                until = jsonObject.get("until").asLong(),
                limit = jsonObject.get("limit").asInt(),
                search = jsonObject.get("search").asText(),
            )
        }
    }
}
