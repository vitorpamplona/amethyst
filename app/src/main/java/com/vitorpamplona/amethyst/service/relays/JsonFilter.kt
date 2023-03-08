package com.vitorpamplona.amethyst.service.relays

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.vitorpamplona.amethyst.service.model.Event
import java.io.Serializable
import java.util.*

interface Filter {
    fun match(event: Event): Boolean
    fun toShortString(): String
}

class JsonFilter(
    val ids: List<String>? = null,
    val authors: List<String>? = null,
    val kinds: List<Int>? = null,
    val tags: Map<String, List<String>>? = null,
    val since: Long? = null,
    val until: Long? = null,
    val limit: Int? = null,
    val search: String? = null
) : Filter, Serializable {
    fun toJson(): String {
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
            jsonObject.addProperty("since", since)
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

    override fun match(event: Event): Boolean {
        if (ids?.any { event.id == it } == false) return false
        if (kinds?.any { event.kind == it } == false) return false
        if (authors?.any { event.pubKey == it } == false) return false
        tags?.forEach { tag ->
            if (!event.tags.any { it.first() == tag.key && it[1] in tag.value }) return false
        }
        if (event.createdAt !in (since ?: Long.MIN_VALUE)..(until ?: Long.MAX_VALUE)) {
            return false
        }
        return true
    }

    override fun toString(): String = "JsonFilter${toJson()}"

    override fun toShortString(): String {
        val list = ArrayList<String>()
        ids?.run {
            list.add("ids")
        }
        authors?.run {
            list.add("authors")
        }
        kinds?.run {
            list.add("kinds[${kinds.joinToString()}]")
        }
        tags?.run {
            list.add("tags")
        }
        since?.run {
            list.add("since")
        }
        until?.run {
            list.add("until")
        }
        limit?.run {
            list.add("limit")
        }
        search?.run {
            list.add("search")
        }
        return list.joinToString()
    }

    companion object {
        val gson: Gson = GsonBuilder().create()

        fun fromJson(json: String): JsonFilter {
            val jsonFilter = gson.fromJson(json, JsonObject::class.java)
            return fromJson(jsonFilter)
        }

        val declaredFields = JsonFilter::class.java.declaredFields.map { it.name }
        fun fromJson(json: JsonObject): JsonFilter {
            // sanity check
            if (json.keySet().any { !(it.startsWith("#") || it in declaredFields) }) {
                println("Filter $json contains unknown parameters.")
            }
            return JsonFilter(
                ids = if (json.has("ids")) json.getAsJsonArray("ids").map { it.asString } else null,
                authors = if (json.has("authors")) {
                    json.getAsJsonArray("authors")
                        .map { it.asString }
                } else {
                    null
                },
                kinds = if (json.has("kinds")) {
                    json.getAsJsonArray("kinds")
                        .map { it.asInt }
                } else {
                    null
                },
                tags = json
                    .entrySet()
                    .filter { it.key.startsWith("#") }
                    .associate {
                        it.key.substring(1) to it.value.asJsonArray.map { it.asString }
                    }
                    .ifEmpty { null },
                since = if (json.has("since")) json.get("since").asLong else null,
                until = if (json.has("until")) json.get("until").asLong else null,
                limit = if (json.has("limit")) json.get("limit").asInt else null,
                search = if (json.has("search")) json.get("search").asString else null
            )
        }
    }
}
