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

import androidx.compose.runtime.Immutable

/**
 * `LIMITS` message: the relay advertises the current rights and limits
 * for this connection. Sent upon connection and again at any point during the
 * connection to reflect the client's changing rights (e.g. after a NIP-42 AUTH).
 *
 * Wire format: `["LIMITS", { <limit_properties> }]`.
 *
 * Every field is optional: a relay only sends the limits it enforces, and a
 * missing field means "unspecified / no advertised limit" — clients should keep
 * any previously cached value and not assume a default. Clients cache this
 * payload on the relay connection and apply it when sending `EVENT`s and `REQ`s.
 */
@Immutable
class LimitsMessage(
    /** Whether clients may publish events to this relay. */
    val canWrite: Boolean? = null,
    /** Whether clients may send `REQ` commands to this relay. */
    val canRead: Boolean? = null,
    /** Whether the client must authenticate (NIP-42) before reading. */
    val authForRead: Boolean? = null,
    /** Whether the client must authenticate (NIP-42) before writing. */
    val authForWrite: Boolean? = null,
    /** Allowlist of event kinds the relay accepts for publishing. */
    val acceptedEventKinds: List<Int>? = null,
    /** Denylist of event kinds the relay rejects for publishing. */
    val blockedEventKinds: List<Int>? = null,
    /** Minimum proof-of-work (NIP-13) difficulty in bits required to publish. */
    val minPowDifficulty: Int? = null,
    /** Maximum byte length for published events and `REQ` filters. */
    val maxMessageLength: Int? = null,
    /** Maximum number of concurrent subscriptions allowed. */
    val maxSubscriptions: Int? = null,
    /** Maximum number of filters allowed per `REQ` command. */
    val maxFilters: Int? = null,
    /** Maximum value the relay honors for a filter's `limit` (use pagination for more). */
    val maxLimit: Int? = null,
    /** Maximum number of tags allowed in a published event. */
    val maxEventTags: Int? = null,
    /** Maximum length of the `content` field of a published event. */
    val maxContentLength: Int? = null,
    /** Minimum event `created_at` recency, in milliseconds before now. */
    val createdAtMsecsAgo: Long? = null,
    /** Maximum event `created_at` in the future, in milliseconds ahead of now. */
    val createdAtMsecsAhead: Long? = null,
    /** Minimum debounce interval, in milliseconds, between filter changes. */
    val filterRateLimit: Long? = null,
    /** Minimum debounce interval, in milliseconds, between publishes. */
    val publishingRateLimit: Long? = null,
    /** Tags that must be present on published events, as `[key, optional value]` pairs. */
    val requiredTags: List<List<String>>? = null,
) : Message {
    override fun label() = LABEL

    companion object {
        const val LABEL = "LIMITS"
    }
}
