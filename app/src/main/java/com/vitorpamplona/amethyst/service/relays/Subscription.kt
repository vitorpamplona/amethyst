package com.vitorpamplona.amethyst.service.relays

import com.fasterxml.jackson.databind.JsonNode
import com.vitorpamplona.quartz.events.Event
import java.util.UUID

data class Subscription(
    val id: String = UUID.randomUUID().toString().substring(0, 4),
    val onEOSE: ((Long, String) -> Unit)? = null
) {
    var typedFilters: List<TypedFilter>? = null // Inactive when null

    fun updateEOSE(time: Long, relay: String) {
        onEOSE?.let { it(time, relay) }
    }

    fun toJson(): String {
        return Event.mapper.writeValueAsString(toJsonObject())
    }

    fun toJsonObject(): JsonNode {
        val factory = Event.mapper.nodeFactory

        return factory.objectNode().apply {
            put("id", id)
            typedFilters?.also { filters ->
                put(
                    "typedFilters",
                    factory.arrayNode(filters.size).apply {
                        filters.forEach { filter ->
                            add(filter.toJsonObject())
                        }
                    }
                )
            }
        }
    }
}
