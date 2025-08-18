/**
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
package com.vitorpamplona.quartz.experimental.limits

import com.fasterxml.jackson.annotation.JsonProperty

class Limits(
    @field:JsonProperty("can_write") val canWrite: Boolean?,
    @field:JsonProperty("can_read") val canRead: Boolean?,
    @field:JsonProperty("accepted_event_kinds") val acceptedEventKinds: Set<Int>?,
    @field:JsonProperty("blocked_event_kinds") val blockedEventKinds: Set<Int>?,
    @field:JsonProperty("min_pow_difficulty") val minPoW: Int?,
    @field:JsonProperty("max_message_length") val maxMessageLength: Int?,
    @field:JsonProperty("max_subscriptions") val maxSubscriptions: Int?,
    @field:JsonProperty("max_filters") val maxFilters: Int?,
    @field:JsonProperty("max_limit") val maxLimit: Int?,
    @field:JsonProperty("max_event_tags") val maxEventTags: Int?,
    @field:JsonProperty("max_content_length") val maxContentLength: Int?,
    @field:JsonProperty("created_at_msecs_ago") val createdAtMillisecsAgo: Long?,
    @field:JsonProperty("created_at_msecs_ahead") val createdAtMillisecsAhead: Long?,
    @field:JsonProperty("filter_rate_limit") val filterRateLimit: Long?,
    @field:JsonProperty("publishing_rate_limit") val publishingRateLimit: Long?,
    @field:JsonProperty("required_tags") val requiredTags: Array<Array<String>>?,
)
