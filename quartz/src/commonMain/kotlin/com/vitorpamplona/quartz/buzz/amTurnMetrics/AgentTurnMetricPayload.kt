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
package com.vitorpamplona.quartz.buzz.amTurnMetrics

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * The decrypted payload of a Buzz Agent Turn Metric (NIP-AM, `kind:44200`) — per-turn
 * token usage and cost for one AI-agent turn.
 *
 * Field names, types, and nullability mirror `AgentTurnMetricPayload` in Buzz's
 * `buzz-core/src/agent_turn_metric.rs` (camelCase on the wire). [harness] and
 * [timestamp] are required; every other field is optional. Token fields are nullable
 * on purpose — `null` means "the harness did not report this", not zero. Unknown JSON
 * fields are ignored for forward compatibility, and an unrecognized [stopReason] is
 * kept verbatim (map it with [stopReasonOrUnknown]) rather than dropping the payload.
 *
 * [pricingIdentity] is optional but not nullable on the wire: it is present only when the
 * publisher could prove the billing authority and the actually-requested model, and its absence
 * means "price unknown" — never infer a price from [model], which stays the configured/session
 * model.
 */
@Serializable
data class AgentTurnMetricPayload(
    val harness: String,
    val timestamp: String,
    val model: String? = null,
    val channelId: String? = null,
    val sessionId: String? = null,
    val turnId: String? = null,
    val turnSeq: Long? = null,
    val turn: TokenCounts? = null,
    val cumulative: TokenCounts? = null,
    val deltaReliable: Boolean = true,
    val stopReason: String? = null,
    val pricingIdentity: PricingIdentity? = null,
) {
    /** Maps [stopReason] to a [StopReason], treating any unrecognized value as [StopReason.UNKNOWN]. */
    fun stopReasonOrUnknown(): StopReason? = stopReason?.let { StopReason.fromWire(it) }

    /**
     * NIP-AM numeric validity: any present `costUsd` must be finite and non-negative.
     * (Token counts are non-negative by type.)
     */
    fun isValid(): Boolean = turn.isCostValid() && cumulative.isCostValid()

    fun encodeToJson(): String = JSON.encodeToString(this)

    companion object {
        val JSON =
            Json {
                ignoreUnknownKeys = true
                explicitNulls = false
                encodeDefaults = true
            }

        fun decodeFromJson(json: String): AgentTurnMetricPayload = JSON.decodeFromString(json)

        private fun TokenCounts?.isCostValid(): Boolean {
            val cost = this?.costUsd ?: return true
            return cost.isFinite() && cost >= 0.0
        }
    }
}

/**
 * The billing identity of a turn (NIP-AM `pricingIdentity`), mirroring `PricingIdentity` in
 * `agent_turn_metric.rs`. When present, [authority] and [model] are required strings; [cacheClass]
 * is omitted (never null) when not applicable.
 *
 * Pricing lookup is an exact string match on `(authority, model)`. The Rust parser does not
 * restrict [authority] to the registered set, so neither does this one — check
 * [isRegisteredAuthority] before trusting it for a price.
 */
@Serializable
data class PricingIdentity(
    /** The billing namespace: an exact lowercase hostname such as `api.anthropic.com` (not the transport provider). */
    val authority: String,
    /** The actually-requested billable model id, as resolved at request time. */
    val model: String,
    /** The cache-write class (e.g. `ephemeral`), when applicable. */
    val cacheClass: String? = null,
) {
    /** True when [authority] is one of the NIP's registered billing namespaces. */
    fun isRegisteredAuthority(): Boolean = authority in REGISTERED_AUTHORITIES

    companion object {
        const val AUTHORITY_ANTHROPIC = "api.anthropic.com"
        const val AUTHORITY_OPENAI = "api.openai.com"
        const val AUTHORITY_OPENROUTER = "openrouter.ai"

        /** The registered `authority` values; the set only grows by amendment to NIP-AM. */
        val REGISTERED_AUTHORITIES = setOf(AUTHORITY_ANTHROPIC, AUTHORITY_OPENAI, AUTHORITY_OPENROUTER)
    }
}

/**
 * Token usage counts. All fields nullable — `null` distinguishes "not reported" from
 * zero. Mirrors `TokenCounts` in `agent_turn_metric.rs`.
 *
 * The cache components ([cacheReadTokens], [cacheWriteTokens]) are informational subsets of
 * [inputTokens]. NIP-AM has publishers keep an explicit zero and *omit* a component they cannot
 * see, so an absent cache field is "unknown" — never sum it as zero.
 */
@Serializable
data class TokenCounts(
    val inputTokens: Long? = null,
    val outputTokens: Long? = null,
    val totalTokens: Long? = null,
    val costUsd: Double? = null,
    val cacheReadTokens: Long? = null,
    val cacheWriteTokens: Long? = null,
)

/** Why an agent turn ended. Wire values are snake_case; unknown maps to [UNKNOWN]. */
@Serializable
enum class StopReason {
    @SerialName("end_turn")
    END_TURN,

    @SerialName("max_tokens")
    MAX_TOKENS,

    @SerialName("cancelled")
    CANCELLED,

    @SerialName("error")
    ERROR,

    @SerialName("unknown")
    UNKNOWN,
    ;

    companion object {
        fun fromWire(value: String): StopReason =
            when (value) {
                "end_turn" -> END_TURN
                "max_tokens" -> MAX_TOKENS
                "cancelled" -> CANCELLED
                "error" -> ERROR
                else -> UNKNOWN
            }
    }
}
