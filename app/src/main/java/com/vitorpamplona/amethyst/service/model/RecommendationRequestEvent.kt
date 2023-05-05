package com.vitorpamplona.amethyst.service.model

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.vitorpamplona.amethyst.model.HexKey
import com.vitorpamplona.amethyst.model.toHexKey
import com.vitorpamplona.amethyst.service.relays.JsonFilter
import nostr.postr.Utils
import java.util.Date

class RecommendationRequestEvent(
    id: HexKey,
    pubKey: HexKey,
    createdAt: Long,
    tags: List<List<String>>,
    content: String,
    sig: HexKey
) : Event(id, pubKey, createdAt, kind, tags, content, sig) {

    companion object {
        const val kind = 20020

        fun create(
            users: List<String>,
            filters: RecommendationFilter,
            recommendationServicePubkey: String,
            replyRelay: String?,
            privateKey: ByteArray,
            createdAt: Long = Date().time / 1000
        ): RecommendationRequestEvent {
            val pubKey = Utils.pubkeyCreate(privateKey)
            val content = filters.toJson()

            val tags = mutableListOf<List<String>>()
            tags.add(listOfNotNull("p", recommendationServicePubkey, replyRelay))
            users.forEach {
                tags.add(listOfNotNull("p", "087ec396d3ba5e72664ccaf82ffca23ca4f51c90a01477cb2a33304fc0161c7f", replyRelay))
            }

            val id = generateId(pubKey.toHexKey(), createdAt, kind, tags, content)
            val sig = Utils.sign(id, privateKey)
            return RecommendationRequestEvent(id.toHexKey(), pubKey.toHexKey(), createdAt, tags, content, sig.toHexKey())
        }
    }
}

class RecommendationFilter(val filters: List<JsonFilter>) {
    fun toJson(): String {
        return Event.gson.toJson(toJsonObject())
    }

    fun toJsonObject(): JsonObject {
        val jsonObject = JsonObject()
        jsonObject.add("filters", filterToJson(filters))
        return jsonObject
    }

    fun filterToJson(filters: List<JsonFilter>): JsonArray {
        return JsonArray().apply { filters.forEach { this.add(it.toJsonObject()) } }
    }
}
