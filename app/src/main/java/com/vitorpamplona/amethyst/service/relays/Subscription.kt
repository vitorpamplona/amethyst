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
import com.vitorpamplona.quartz.events.Event
import java.util.UUID

data class Subscription(
    val id: String = UUID.randomUUID().toString().substring(0, 4),
    val onEOSE: ((Long, String) -> Unit)? = null,
) {
    var typedFilters: List<TypedFilter>? = null // Inactive when null

    fun updateEOSE(
        time: Long,
        relay: String,
    ) {
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
                        filters.forEach { filter -> add(filter.toJsonObject()) }
                    },
                )
            }
        }
    }
}
