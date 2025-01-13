/**
 * Copyright (c) 2024 Vitor Pamplona
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
package com.vitorpamplona.ammolite.relays.filters

import com.fasterxml.jackson.databind.node.JsonNodeFactory
import com.fasterxml.jackson.databind.node.ObjectNode
import com.vitorpamplona.quartz.nip01Core.jackson.EventMapper

object FilterSerializer {
    fun toJsonObject(
        ids: List<String>? = null,
        authors: List<String>? = null,
        kinds: List<Int>? = null,
        tags: Map<String, List<String>>? = null,
        since: Long? = null,
        until: Long? = null,
        limit: Int? = null,
        search: String? = null,
    ): ObjectNode {
        val factory = JsonNodeFactory.instance
        return factory.objectNode().apply {
            ids?.run {
                replace(
                    "ids",
                    factory.arrayNode(ids.size).apply { ids.forEach { add(it) } },
                )
            }
            authors?.run {
                replace(
                    "authors",
                    factory.arrayNode(authors.size).apply { authors.forEach { add(it) } },
                )
            }
            kinds?.run {
                replace(
                    "kinds",
                    factory.arrayNode(kinds.size).apply { kinds.forEach { add(it) } },
                )
            }
            tags?.run {
                entries.forEach { kv ->
                    replace(
                        "#${kv.key}",
                        factory.arrayNode(kv.value.size).apply { kv.value.forEach { add(it) } },
                    )
                }
            }
            since?.run { put("since", since) }
            until?.run { put("until", until) }
            limit?.run { put("limit", limit) }
            search?.run { put("search", search) }
        }
    }

    fun toJson(
        ids: List<String>? = null,
        authors: List<String>? = null,
        kinds: List<Int>? = null,
        tags: Map<String, List<String>>? = null,
        since: Long? = null,
        until: Long? = null,
        limit: Int? = null,
        search: String? = null,
    ): String =
        EventMapper.mapper.writeValueAsString(
            toJsonObject(
                ids,
                authors,
                kinds,
                tags,
                since,
                until,
                limit,
                search,
            ),
        )
}
