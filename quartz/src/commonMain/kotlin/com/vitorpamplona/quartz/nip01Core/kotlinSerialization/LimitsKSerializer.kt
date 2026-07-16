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
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.addJsonArray
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray

/** kotlinx-serialization codec for the `LIMITS` object payload. */
object LimitsKSerializer {
    // Tolerant readers so a malformed/mistyped field degrades to null instead of
    // throwing: this is the iOS/native incoming-parse path, and it must match the
    // leniency of the Jackson path (LimitsDeserializer) used on jvmAndroid.
    private fun JsonObject.bool(key: String): Boolean? = (this[key] as? JsonPrimitive)?.booleanOrNull

    private fun JsonObject.int(key: String): Int? = (this[key] as? JsonPrimitive)?.intOrNull

    private fun JsonObject.long(key: String): Long? = (this[key] as? JsonPrimitive)?.longOrNull

    private fun JsonObject.intList(key: String): List<Int>? = (this[key] as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.intOrNull }

    private fun JsonObject.tagList(key: String): List<List<String>>? =
        (this[key] as? JsonArray)?.map { tag ->
            (tag as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull } ?: emptyList()
        }

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
            canWrite = jsonObject.bool("can_write"),
            canRead = jsonObject.bool("can_read"),
            authForRead = jsonObject.bool("auth_for_read"),
            authForWrite = jsonObject.bool("auth_for_write"),
            acceptedEventKinds = jsonObject.intList("accepted_event_kinds"),
            blockedEventKinds = jsonObject.intList("blocked_event_kinds"),
            minPowDifficulty = jsonObject.int("min_pow_difficulty"),
            maxMessageLength = jsonObject.int("max_message_length"),
            maxSubscriptions = jsonObject.int("max_subscriptions"),
            maxFilters = jsonObject.int("max_filters"),
            maxLimit = jsonObject.int("max_limit"),
            maxEventTags = jsonObject.int("max_event_tags"),
            maxContentLength = jsonObject.int("max_content_length"),
            createdAtMsecsAgo = jsonObject.long("created_at_msecs_ago"),
            createdAtMsecsAhead = jsonObject.long("created_at_msecs_ahead"),
            filterRateLimit = jsonObject.long("filter_rate_limit"),
            publishingRateLimit = jsonObject.long("publishing_rate_limit"),
            requiredTags = jsonObject.tagList("required_tags"),
        )
}
