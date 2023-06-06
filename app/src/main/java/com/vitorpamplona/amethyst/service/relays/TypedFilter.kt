package com.vitorpamplona.amethyst.service.relays

import com.google.gson.GsonBuilder
import com.google.gson.JsonArray
import com.google.gson.JsonObject

class TypedFilter(
    val types: Set<FeedType>,
    val filter: JsonFilter
) {

    fun toJson(): String {
        return GsonBuilder().create().toJson(toJsonObject())
    }

    fun toJsonObject(): JsonObject {
        val jsonObject = JsonObject()
        jsonObject.add("types", typesToJson(types))
        jsonObject.add("filter", filterToJson(filter))
        return jsonObject
    }

    fun typesToJson(types: Set<FeedType>): JsonArray {
        return JsonArray().apply { types.forEach { add(it.name.lowercase()) } }
    }

    fun filterToJson(filter: JsonFilter): JsonObject {
        val jsonObject = JsonObject()
        filter.ids?.run {
            jsonObject.add("ids", JsonArray().apply { filter.ids.forEach { add(it) } })
        }
        filter.authors?.run {
            jsonObject.add("authors", JsonArray().apply { filter.authors.forEach { add(it) } })
        }
        filter.kinds?.run {
            jsonObject.add("kinds", JsonArray().apply { filter.kinds.forEach { add(it) } })
        }
        filter.tags?.run {
            entries.forEach { kv ->
                jsonObject.add("#${kv.key}", JsonArray().apply { kv.value.forEach { add(it) } })
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
        filter.until?.run {
            jsonObject.addProperty("until", filter.until)
        }
        filter.limit?.run {
            jsonObject.addProperty("limit", filter.limit)
        }
        filter.search?.run {
            jsonObject.addProperty("search", filter.search)
        }
        return jsonObject
    }
}
