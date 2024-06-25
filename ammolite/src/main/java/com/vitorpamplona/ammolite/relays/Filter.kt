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
package com.vitorpamplona.ammolite.relays

import com.vitorpamplona.quartz.events.Event

class Filter(
    val ids: List<String>? = null,
    val authors: List<String>? = null,
    val kinds: List<Int>? = null,
    val tags: Map<String, List<String>>? = null,
    val since: Map<String, EOSETime>? = null,
    val until: Long? = null,
    val limit: Int? = null,
    val search: String? = null,
) {
    fun toJson(forRelay: String? = null): String {
        val factory = Event.mapper.nodeFactory
        val filter =
            factory.objectNode().apply {
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
                since?.run {
                    if (!isEmpty()) {
                        if (forRelay != null) {
                            val relaySince = get(forRelay)
                            if (relaySince != null) {
                                put("since", relaySince.time)
                            }
                        } else {
                            val jsonObjectSince = factory.objectNode()
                            entries.forEach { sincePairs ->
                                jsonObjectSince.put(sincePairs.key, "${sincePairs.value}")
                            }
                            put("since", jsonObjectSince)
                        }
                    }
                }
                until?.run { put("until", until) }
                limit?.run { put("limit", limit) }
                search?.run { put("search", search) }
            }
        return Event.mapper.writeValueAsString(filter)
    }

    fun match(
        event: Event,
        forRelay: String? = null,
    ): Boolean {
        if (ids?.any { event.id == it } == false) return false
        if (kinds?.any { event.kind == it } == false) return false
        if (authors?.any { event.pubKey == it } == false) return false
        tags?.forEach { tag ->
            if (!event.tags.any { it.first() == tag.key && it[1] in tag.value }) return false
        }
        if (event.createdAt !in (since?.get(forRelay)?.time ?: Long.MIN_VALUE)..(until ?: Long.MAX_VALUE)) {
            return false
        }
        return true
    }
}
