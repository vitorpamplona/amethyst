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
import com.vitorpamplona.quartz.utils.asIntOrNull
import com.vitorpamplona.quartz.utils.asLongOrNull
import com.vitorpamplona.quartz.utils.asTextOrNull

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
                ids = jsonObject.get("ids").mapNotNull { it.asTextOrNull() },
                authors = jsonObject.get("authors").mapNotNull { it.asTextOrNull() },
                kinds = jsonObject.get("kinds").mapNotNull { it.asIntOrNull() },
                tags = tags.associateWith { jsonObject.get(it).mapNotNull { it.asTextOrNull() } },
                since = jsonObject.get("since").asLongOrNull(),
                until = jsonObject.get("until").asLongOrNull(),
                limit = jsonObject.get("limit").asIntOrNull(),
                search = jsonObject.get("search").asTextOrNull(),
            )
        }
    }
}
