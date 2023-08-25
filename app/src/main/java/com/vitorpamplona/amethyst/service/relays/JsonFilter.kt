package com.vitorpamplona.amethyst.service.relays

import com.vitorpamplona.quartz.events.Event

class JsonFilter(
    val ids: List<String>? = null,
    val authors: List<String>? = null,
    val kinds: List<Int>? = null,
    val tags: Map<String, List<String>>? = null,
    val since: Map<String, EOSETime>? = null,
    val until: Long? = null,
    val limit: Int? = null,
    val search: String? = null
) {

    fun toJson(forRelay: String? = null): String {
        val factory = Event.mapper.nodeFactory
        val filter = factory.objectNode().apply {
            ids?.run {
                put(
                    "ids",
                    factory.arrayNode(ids.size).apply {
                        ids.forEach { add(it) }
                    }
                )
            }
            authors?.run {
                put(
                    "authors",
                    factory.arrayNode(authors.size).apply {
                        authors.forEach { add(it) }
                    }
                )
            }
            kinds?.run {
                put(
                    "kinds",
                    factory.arrayNode(kinds.size).apply {
                        kinds.forEach { add(it) }
                    }
                )
            }
            tags?.run {
                entries.forEach { kv ->
                    put(
                        "#${kv.key}",
                        factory.arrayNode(kv.value.size).apply {
                            kv.value.forEach { add(it) }
                        }
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
}
