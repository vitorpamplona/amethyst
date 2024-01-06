/**
 * Copyright (c) 2023 Vitor Pamplona
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
package com.vitorpamplona.amethyst.service.relays

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.ArrayNode
import com.vitorpamplona.quartz.events.Event

class TypedFilter(
    val types: Set<FeedType>,
    val filter: JsonFilter,
) {
    fun toJson(): String {
        return Event.mapper.writeValueAsString(toJsonObject())
    }

    fun toJsonObject(): JsonNode {
        val factory = Event.mapper.nodeFactory

        return factory.objectNode().apply {
            put("types", typesToJson(types))
            put("filter", filterToJson(filter))
        }
    }

    fun typesToJson(types: Set<FeedType>): ArrayNode {
        val factory = Event.mapper.nodeFactory
        return factory.arrayNode(types.size).apply { types.forEach { add(it.name.lowercase()) } }
    }

    fun filterToJson(filter: JsonFilter): JsonNode {
        val factory = Event.mapper.nodeFactory
        return factory.objectNode().apply {
            filter.ids?.run {
                put(
                    "ids",
                    factory.arrayNode(filter.ids.size).apply { filter.ids.forEach { add(it) } },
                )
            }
            filter.authors?.run {
                put(
                    "authors",
                    factory.arrayNode(filter.authors.size).apply { filter.authors.forEach { add(it) } },
                )
            }
            filter.kinds?.run {
                put(
                    "kinds",
                    factory.arrayNode(filter.kinds.size).apply { filter.kinds.forEach { add(it) } },
                )
            }
            filter.tags?.run {
                entries.forEach { kv ->
                    put(
                        "#${kv.key}",
                        factory.arrayNode(kv.value.size).apply { kv.value.forEach { add(it) } },
                    )
                }
            }
      /*
      Does not include since in the json comparison
      filter.since?.run {
          val jsonObjectSince = JsonObject()
          entries.forEach { sincePairs ->
              jsonObjectSince.addProperty(sincePairs.key, "${sincePairs.value}")
          }
          jsonObject.add("since", jsonObjectSince)
      }*/
            filter.until?.run { put("until", filter.until) }
            filter.limit?.run { put("limit", filter.limit) }
            filter.search?.run { put("search", filter.search) }
        }
    }
}
