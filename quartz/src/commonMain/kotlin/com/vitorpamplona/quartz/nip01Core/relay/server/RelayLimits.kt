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
package com.vitorpamplona.quartz.nip01Core.relay.server

import com.vitorpamplona.quartz.nip11RelayInfo.Nip11RelayInformation.RelayInformationLimitation

/**
 * The relay's operational limits — the single source of truth that is both
 * **enforced** and **advertised**.
 *
 * Hand this to [NostrServer] / [EventSourceServer] and it is applied to every
 * connection (per-command checks via [LimitsPolicy], plus the session-level
 * message-size and subscription-count caps in [RelaySession]); call
 * [toNip11Limitation] to advertise the very same numbers in the relay's NIP-11
 * document, so what you enforce and what you publish can never drift.
 *
 * Every field is optional; a null leaves that limit unset (not enforced, not
 * advertised). All fields map 1:1 to NIP-11's `limitation` object.
 *
 * All of these are enforced by a single [LimitsPolicy] in the policy chain — the
 * per-command ones via `accept(...)`, and the per-connection ones
 * ([maxMessageLength], [maxSubscriptions]) via the `acceptMessage` /
 * `acceptSubscription` policy hooks — so limits compose like any other policy.
 *
 * Advertised only (enforced elsewhere or operationally): [minPowDifficulty]
 * (NIP-13), [authRequired] (use a `FullAuthPolicy`), [paymentRequired],
 * [restrictedWrites].
 */
class RelayLimits(
    /** Max size of an incoming message; oversized frames get a NOTICE. Measured in UTF-16 chars. */
    val maxMessageLength: Int? = null,
    /** Max number of concurrent subscriptions a single connection may hold. */
    val maxSubscriptions: Int? = null,
    /** Max number of filters allowed in one REQ/COUNT. */
    val maxFilters: Int? = null,
    /** Upper bound for a filter's `limit`; larger values are clamped down. */
    val maxLimit: Int? = null,
    /** `limit` substituted into a filter that doesn't specify one. */
    val defaultLimit: Int? = null,
    /** Max length of a subscription id (REQ) / query id (COUNT). */
    val maxSubidLength: Int? = null,
    /** Max number of tags on a published event. */
    val maxEventTags: Int? = null,
    /** Max `content` length on a published event. Measured in UTF-16 chars. */
    val maxContentLength: Int? = null,
    /** Minimum NIP-13 PoW difficulty. Advertised only; enforce with a PoW policy. */
    val minPowDifficulty: Int? = null,
    /** Whether the relay requires NIP-42 auth. Advertised only; enforce with `FullAuthPolicy`. */
    val authRequired: Boolean = false,
    /** Whether the relay requires payment. Advertised only. */
    val paymentRequired: Boolean = false,
    /** Whether writes are restricted to certain pubkeys. Advertised only. */
    val restrictedWrites: Boolean = false,
    /** Reject events with `created_at` before this epoch-second. */
    val createdAtLowerLimit: Long? = null,
    /** Reject events with `created_at` after this epoch-second. */
    val createdAtUpperLimit: Long? = null,
) {
    /** Renders these limits as a NIP-11 `limitation` object for the relay info document. */
    fun toNip11Limitation(): RelayInformationLimitation =
        RelayInformationLimitation(
            max_message_length = maxMessageLength,
            max_subscriptions = maxSubscriptions,
            max_filters = maxFilters,
            max_limit = maxLimit,
            default_limit = defaultLimit,
            max_subid_length = maxSubidLength,
            max_event_tags = maxEventTags,
            max_content_length = maxContentLength,
            min_pow_difficulty = minPowDifficulty,
            auth_required = authRequired,
            payment_required = paymentRequired,
            restricted_writes = restrictedWrites,
            // Clamp to Int range (the NIP-11 field is conventionally an int) so a
            // post-2038 epoch second can't silently wrap to a negative timestamp.
            created_at_lower_limit = createdAtLowerLimit?.coerceIn(0L, Int.MAX_VALUE.toLong())?.toInt(),
            created_at_upper_limit = createdAtUpperLimit?.coerceIn(0L, Int.MAX_VALUE.toLong())?.toInt(),
        )
}
