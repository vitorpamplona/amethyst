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
package com.vitorpamplona.quartz.nip01Core.relay.commands.toClient

import com.fasterxml.jackson.databind.JsonNode

class LimitsDeserializer {
    companion object {
        // Type-checked readers: a missing field, an explicit JSON null, or a
        // wrong-typed value all yield null ("unspecified — keep the previous
        // value") rather than coercing to false/0. Keeps this in step with the
        // kotlinx path (LimitsKSerializer) used on iOS/native.
        private fun JsonNode.bool(field: String): Boolean? = get(field)?.takeIf { it.isBoolean }?.booleanValue()

        private fun JsonNode.int(field: String): Int? = get(field)?.takeIf { it.isNumber }?.intValue()

        private fun JsonNode.long(field: String): Long? = get(field)?.takeIf { it.isNumber }?.longValue()

        private fun JsonNode.intList(field: String): List<Int>? = get(field)?.takeIf { it.isArray }?.mapNotNull { it.takeIf { n -> n.isNumber }?.intValue() }

        private fun JsonNode.requiredTags(field: String): List<List<String>>? =
            get(field)?.takeIf { it.isArray }?.map { tag ->
                if (tag.isArray) tag.mapNotNull { it.takeIf { n -> n.isValueNode }?.asText() } else emptyList()
            }

        fun fromJson(jsonObject: JsonNode): LimitsMessage =
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
                requiredTags = jsonObject.requiredTags("required_tags"),
            )
    }
}
