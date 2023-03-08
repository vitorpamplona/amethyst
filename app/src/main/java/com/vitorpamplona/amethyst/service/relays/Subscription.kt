package com.vitorpamplona.amethyst.service.relays

import com.google.gson.GsonBuilder
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import java.util.UUID

data class Subscription(
    val id: String = UUID.randomUUID().toString().substring(0, 4),
    val onEOSE: ((Long) -> Unit)? = null
) {
    var typedFilters: List<TypedFilter>? = null // Inactive when null

    fun updateEOSE(l: Long) {
        onEOSE?.let { it(l) }
    }

    fun toJson(): String {
        return GsonBuilder().create().toJson(toJsonObject())
    }

    fun toJsonObject(): JsonObject {
        val jsonObject = JsonObject()
        jsonObject.addProperty("id", id)
        typedFilters?.run {
            jsonObject.add("typedFilters", JsonArray().apply { typedFilters?.forEach { add(it.toJsonObject()) } })
        }
        return jsonObject
    }
}
