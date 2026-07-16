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
        private fun JsonNode.intList(field: String): List<Int>? = get(field)?.takeIf { it.isArray }?.map { it.asInt() }

        private fun JsonNode.requiredTags(field: String): List<List<String>>? =
            get(field)?.takeIf { it.isArray }?.map { tag ->
                tag.map { it.asText() }
            }

        fun fromJson(jsonObject: JsonNode): LimitsMessage =
            LimitsMessage(
                canWrite = jsonObject.get("can_write")?.asBoolean(),
                canRead = jsonObject.get("can_read")?.asBoolean(),
                authForRead = jsonObject.get("auth_for_read")?.asBoolean(),
                authForWrite = jsonObject.get("auth_for_write")?.asBoolean(),
                acceptedEventKinds = jsonObject.intList("accepted_event_kinds"),
                blockedEventKinds = jsonObject.intList("blocked_event_kinds"),
                minPowDifficulty = jsonObject.get("min_pow_difficulty")?.asInt(),
                maxMessageLength = jsonObject.get("max_message_length")?.asInt(),
                maxSubscriptions = jsonObject.get("max_subscriptions")?.asInt(),
                maxFilters = jsonObject.get("max_filters")?.asInt(),
                maxLimit = jsonObject.get("max_limit")?.asInt(),
                maxEventTags = jsonObject.get("max_event_tags")?.asInt(),
                maxContentLength = jsonObject.get("max_content_length")?.asInt(),
                createdAtMsecsAgo = jsonObject.get("created_at_msecs_ago")?.asLong(),
                createdAtMsecsAhead = jsonObject.get("created_at_msecs_ahead")?.asLong(),
                filterRateLimit = jsonObject.get("filter_rate_limit")?.asLong(),
                publishingRateLimit = jsonObject.get("publishing_rate_limit")?.asLong(),
                requiredTags = jsonObject.requiredTags("required_tags"),
            )
    }
}
