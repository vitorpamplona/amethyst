/*
 * Copyright (c) 2025 Vitor Pamplona
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
package com.vitorpamplona.quartz.nip01Core.kotlinSerialization

import com.vitorpamplona.quartz.nip01Core.relay.commands.toClient.LimitsMessage
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.addJsonArray
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray

/** kotlinx-serialization codec for the NIP-22 `LIMITS` object payload. */
object LimitsKSerializer {
    fun serializeToElement(value: LimitsMessage): JsonObject =
        buildJsonObject {
            // Only emit the fields the relay actually set; absent limits stay absent.
            value.canWrite?.let { put("can_write", it) }
            value.canRead?.let { put("can_read", it) }
            value.authForRead?.let { put("auth_for_read", it) }
            value.authForWrite?.let { put("auth_for_write", it) }
            value.acceptedEventKinds?.let { kinds ->
                putJsonArray("accepted_event_kinds") { kinds.forEach { add(it) } }
            }
            value.blockedEventKinds?.let { kinds ->
                putJsonArray("blocked_event_kinds") { kinds.forEach { add(it) } }
            }
            value.minPowDifficulty?.let { put("min_pow_difficulty", it) }
            value.maxMessageLength?.let { put("max_message_length", it) }
            value.maxSubscriptions?.let { put("max_subscriptions", it) }
            value.maxFilters?.let { put("max_filters", it) }
            value.maxLimit?.let { put("max_limit", it) }
            value.maxEventTags?.let { put("max_event_tags", it) }
            value.maxContentLength?.let { put("max_content_length", it) }
            value.createdAtMsecsAgo?.let { put("created_at_msecs_ago", it) }
            value.createdAtMsecsAhead?.let { put("created_at_msecs_ahead", it) }
            value.filterRateLimit?.let { put("filter_rate_limit", it) }
            value.publishingRateLimit?.let { put("publishing_rate_limit", it) }
            value.requiredTags?.let { tags ->
                putJsonArray("required_tags") {
                    tags.forEach { tag ->
                        addJsonArray { tag.forEach { part -> add(part) } }
                    }
                }
            }
        }

    fun deserializeFromElement(jsonObject: JsonObject): LimitsMessage =
        LimitsMessage(
            canWrite = jsonObject["can_write"]?.jsonPrimitive?.boolean,
            canRead = jsonObject["can_read"]?.jsonPrimitive?.boolean,
            authForRead = jsonObject["auth_for_read"]?.jsonPrimitive?.boolean,
            authForWrite = jsonObject["auth_for_write"]?.jsonPrimitive?.boolean,
            acceptedEventKinds = jsonObject["accepted_event_kinds"]?.jsonArray?.map { it.jsonPrimitive.int },
            blockedEventKinds = jsonObject["blocked_event_kinds"]?.jsonArray?.map { it.jsonPrimitive.int },
            minPowDifficulty = jsonObject["min_pow_difficulty"]?.jsonPrimitive?.int,
            maxMessageLength = jsonObject["max_message_length"]?.jsonPrimitive?.int,
            maxSubscriptions = jsonObject["max_subscriptions"]?.jsonPrimitive?.int,
            maxFilters = jsonObject["max_filters"]?.jsonPrimitive?.int,
            maxLimit = jsonObject["max_limit"]?.jsonPrimitive?.int,
            maxEventTags = jsonObject["max_event_tags"]?.jsonPrimitive?.int,
            maxContentLength = jsonObject["max_content_length"]?.jsonPrimitive?.int,
            createdAtMsecsAgo = jsonObject["created_at_msecs_ago"]?.jsonPrimitive?.long,
            createdAtMsecsAhead = jsonObject["created_at_msecs_ahead"]?.jsonPrimitive?.long,
            filterRateLimit = jsonObject["filter_rate_limit"]?.jsonPrimitive?.long,
            publishingRateLimit = jsonObject["publishing_rate_limit"]?.jsonPrimitive?.long,
            requiredTags =
                jsonObject["required_tags"]?.jsonArray?.map { tag ->
                    (tag as JsonArray).map { it.jsonPrimitive.content }
                },
        )
}
