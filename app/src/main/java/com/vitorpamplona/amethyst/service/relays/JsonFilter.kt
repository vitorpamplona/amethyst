package com.vitorpamplona.amethyst.service.relays

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonArray
import com.google.gson.JsonObject

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
        val jsonObject = JsonObject()
        ids?.run {
            jsonObject.add("ids", JsonArray().apply { ids.forEach { add(it) } })
        }
        authors?.run {
            jsonObject.add("authors", JsonArray().apply { authors.forEach { add(it) } })
        }
        kinds?.run {
            jsonObject.add("kinds", JsonArray().apply { kinds.forEach { add(it) } })
        }
        tags?.run {
            entries.forEach { kv ->
                jsonObject.add("#${kv.key}", JsonArray().apply { kv.value.forEach { add(it) } })
            }
        }
        since?.run {
            if (!isEmpty()) {
                if (forRelay != null) {
                    val relaySince = get(forRelay)
                    if (relaySince != null) {
                        jsonObject.addProperty("since", relaySince.time)
                    }
                } else {
                    val jsonObjectSince = JsonObject()
                    entries.forEach { sincePairs ->
                        jsonObjectSince.addProperty(sincePairs.key, "${sincePairs.value}")
                    }
                    jsonObject.add("since", jsonObjectSince)
                }
            }
        }
        until?.run {
            jsonObject.addProperty("until", until)
        }
        limit?.run {
            jsonObject.addProperty("limit", limit)
        }
        search?.run {
            jsonObject.addProperty("search", search)
        }
        return gson.toJson(jsonObject)
    }

    companion object {
        val gson: Gson = GsonBuilder().create()
    }
}
