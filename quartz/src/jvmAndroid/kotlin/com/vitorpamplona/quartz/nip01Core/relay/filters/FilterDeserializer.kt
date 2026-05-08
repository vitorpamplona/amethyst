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
package com.vitorpamplona.quartz.nip01Core.relay.filters

import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.core.JsonToken
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
    ): Filter = ManualFilterDeserializer.fromJson(jp)
}

class ManualFilterDeserializer {
    companion object {
        /**
         * Streaming filter parser. Reads field-by-field off [jp] without
         * materializing an intermediate `JsonNode` tree — same shape as
         * [com.vitorpamplona.quartz.nip01Core.jackson.EventDeserializer],
         * which is what makes high-fan-out REQ traffic cheap on the
         * relay-inbound path.
         *
         * Caller must position the parser so [jp.currentToken] is the
         * `START_OBJECT` opening the filter. On return, [jp.currentToken]
         * is the matching `END_OBJECT`.
         *
         * Tolerant by design — invalid array entries (wrong type, JSON
         * null) are silently dropped, mirroring the
         * `mapNotNull { it.asTextOrNull() }` behavior of the tree-based
         * overload below. Unknown top-level fields are skipped via
         * [JsonParser.skipChildren].
         */
        fun fromJson(jp: JsonParser): Filter {
            var ids: MutableList<String>? = null
            var authors: MutableList<String>? = null
            var kinds: MutableList<Int>? = null
            var tags: MutableMap<String, List<String>>? = null
            var tagsAll: MutableMap<String, List<String>>? = null
            var since: Long? = null
            var until: Long? = null
            var limit: Int? = null
            var search: String? = null

            while (jp.nextToken() != JsonToken.END_OBJECT) {
                val name = jp.currentName()
                jp.nextToken() // advance to value
                when {
                    name == "ids" -> {
                        ids = readStringArray(jp)
                    }

                    name == "authors" -> {
                        authors = readStringArray(jp)
                    }

                    name == "kinds" -> {
                        kinds = readIntArray(jp)
                    }

                    name == "since" -> {
                        if (jp.currentToken != JsonToken.VALUE_NULL) since = jp.longValue
                    }

                    name == "until" -> {
                        if (jp.currentToken != JsonToken.VALUE_NULL) until = jp.longValue
                    }

                    name == "limit" -> {
                        if (jp.currentToken != JsonToken.VALUE_NULL) limit = jp.intValue
                    }

                    name == "search" -> {
                        if (jp.currentToken != JsonToken.VALUE_NULL) search = jp.text
                    }

                    name.length > 1 && name[0] == '#' -> {
                        val map = tags ?: mutableMapOf<String, List<String>>().also { tags = it }
                        map[name.substring(1)] = readStringArray(jp)
                    }

                    name.length > 1 && name[0] == '&' -> {
                        val map = tagsAll ?: mutableMapOf<String, List<String>>().also { tagsAll = it }
                        map[name.substring(1)] = readStringArray(jp)
                    }

                    else -> {
                        jp.skipChildren()
                    }
                }
            }

            return Filter(
                ids = ids,
                authors = authors,
                kinds = kinds,
                tags = tags,
                tagsAll = tagsAll,
                since = since,
                until = until,
                limit = limit,
                search = search,
            )
        }

        /**
         * Reads a string array off [jp]. Drops non-string entries and
         * JSON nulls — matches the `mapNotNull { it.asTextOrNull() }`
         * tolerance of the tree-based path. Returns an empty list when
         * the value is anything other than a `START_ARRAY` (incl.
         * `null`), so callers don't have to special-case that.
         */
        private fun readStringArray(jp: JsonParser): MutableList<String> {
            val out = mutableListOf<String>()
            if (jp.currentToken == JsonToken.START_ARRAY) {
                while (jp.nextToken() != JsonToken.END_ARRAY) {
                    if (jp.currentToken == JsonToken.VALUE_STRING) {
                        out.add(jp.text)
                    } else if (jp.currentToken == JsonToken.START_OBJECT || jp.currentToken == JsonToken.START_ARRAY) {
                        jp.skipChildren()
                    }
                }
            } else if (jp.currentToken == JsonToken.START_OBJECT) {
                jp.skipChildren()
            }
            return out
        }

        /**
         * Reads an int array off [jp]. Same tolerance rules as
         * [readStringArray] — non-numeric entries are dropped.
         */
        private fun readIntArray(jp: JsonParser): MutableList<Int> {
            val out = mutableListOf<Int>()
            if (jp.currentToken == JsonToken.START_ARRAY) {
                while (jp.nextToken() != JsonToken.END_ARRAY) {
                    when (jp.currentToken) {
                        JsonToken.VALUE_NUMBER_INT, JsonToken.VALUE_NUMBER_FLOAT -> out.add(jp.intValue)
                        JsonToken.START_OBJECT, JsonToken.START_ARRAY -> jp.skipChildren()
                        else -> Unit
                    }
                }
            } else if (jp.currentToken == JsonToken.START_OBJECT) {
                jp.skipChildren()
            }
            return out
        }

        /**
         * Tree-based overload kept for callers that already have an
         * `ObjectNode` in hand (e.g. cross-format adapters). New code on
         * the relay-inbound path should use the streaming overload —
         * this one materializes the full filter tree first.
         */
        fun fromJson(jsonObject: ObjectNode): Filter {
            val tagsIn = mutableListOf<String>()
            jsonObject.fieldNames().forEach {
                if (it.startsWith("#")) {
                    tagsIn.add(it.substring(1))
                }
            }
            val tagsAll = mutableListOf<String>()
            jsonObject.fieldNames().forEach {
                if (it.startsWith("&")) {
                    tagsAll.add(it.substring(1))
                }
            }

            return Filter(
                ids = jsonObject.get("ids")?.mapNotNull { it.asTextOrNull() },
                authors = jsonObject.get("authors")?.mapNotNull { it.asTextOrNull() },
                kinds = jsonObject.get("kinds")?.mapNotNull { it.asIntOrNull() },
                tags = tagsIn.associateWith { jsonObject.get("#$it").mapNotNull { it.asTextOrNull() } },
                tagsAll = tagsAll.associateWith { jsonObject.get("&$it").mapNotNull { it.asTextOrNull() } },
                since = jsonObject.get("since")?.asLongOrNull(),
                until = jsonObject.get("until")?.asLongOrNull(),
                limit = jsonObject.get("limit")?.asIntOrNull(),
                search = jsonObject.get("search")?.asTextOrNull(),
            )
        }
    }
}
