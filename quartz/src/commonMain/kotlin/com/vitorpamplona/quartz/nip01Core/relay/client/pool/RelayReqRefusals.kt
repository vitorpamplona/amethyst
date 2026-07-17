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
package com.vitorpamplona.quartz.nip01Core.relay.client.pool

import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.utils.concurrent.ConcurrentMap
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

/**
 * Per-relay REQ suppression for **relay-wide capability refusals** — a relay that
 * won't serve a whole *class* of REQs no matter which subscription sends them.
 *
 * This is the sibling of the per-subscription refusal memory in
 * [com.vitorpamplona.quartz.nip01Core.relay.client.reqs.RequestSubscriptionState]:
 * that one stops replaying *one refused filter* across reconnects; this one stops a
 * relay being hammered by *many different* subscriptions it structurally can't answer.
 * The motivating case: a NIP-50 search-only relay pulled into the general read set
 * CLOSES every feed REQ with `error: search filter is required` — 13 CLOSEDs across 6
 * subscriptions on a *single* connection, where per-filter memory can't help because
 * every filter differs.
 *
 * Two capability classes are recognized from the CLOSED/NOTICE text (substring match,
 * mirroring [com.vitorpamplona.quartz.nip01Core.relay.client.accessories.AdaptiveRelayLimiter]'s
 * marker approach — the discriminating detail is in the human message, not the prefix):
 *
 *  - [Policy.SEARCH_ONLY] — the relay requires a NIP-50 `search` field. Only REQs whose
 *    every filter lacks `search` are suppressed; a genuine search REQ still goes through,
 *    so this block is safe to keep for the whole session.
 *  - [Policy.NO_READS] — the relay serves no REQs at all (write-only / "queries not
 *    allowed"). Every REQ is suppressed.
 *
 * A relay is only blocked after [threshold] matching refusals, so a single fluke never
 * silences it. The block is session-scoped (the lifetime of the owning
 * [com.vitorpamplona.quartz.nip01Core.relay.client.pool.PoolRequests]); these are stable
 * relay properties, and both policies leave the REQs the relay *does* serve untouched,
 * so there is deliberately no auto-clear on the per-event hot path.
 *
 * Fed from the relay socket-reader threads (via CLOSED frames) and read from the send
 * threads, so all state is in [ConcurrentMap]s.
 */
class RelayReqRefusals(
    private val threshold: Int = 2,
) {
    enum class Policy {
        /** The relay only answers REQs that carry a NIP-50 `search` term. */
        SEARCH_ONLY,

        /** The relay answers no REQs at all. */
        NO_READS,
    }

    // How far each relay has progressed toward a block: the candidate class and its count.
    private val progress = ConcurrentMap<NormalizedRelayUrl, Progress>()

    // Relays that have reached [threshold] refusals of one class; NO_READS wins over SEARCH_ONLY.
    private val blocked = ConcurrentMap<NormalizedRelayUrl, Policy>()

    private val blockedSet = MutableStateFlow<Set<NormalizedRelayUrl>>(emptySet())

    /**
     * The relays currently blocked (any class), as a reactive set. The app subtracts this
     * from a feed's read-relay set so a proven-useless relay is dropped entirely — closing
     * the idle socket — not merely having its REQs suppressed. Monotonic within a session.
     */
    val blockedFlow: StateFlow<Set<NormalizedRelayUrl>> = blockedSet

    private class Progress(
        val candidate: Policy,
        val hits: Int,
    )

    /**
     * Feed a CLOSED (or NOTICE) reason for [relay]; escalates to a block once the class
     * repeats. Returns true only when this call *newly* blocked the relay, so the caller can
     * drop it from the desired-relay set (closing the socket) exactly once.
     */
    fun onRefused(
        relay: NormalizedRelayUrl,
        reason: String,
    ): Boolean {
        val candidate = classify(reason) ?: return false
        // NO_READS is the strictest verdict; once reached, nothing softens it.
        if (blocked[relay] == Policy.NO_READS) return false

        val next =
            progress.merge(relay, Progress(candidate, 1)) { old, _ ->
                if (old.candidate == candidate) Progress(candidate, old.hits + 1) else Progress(candidate, 1)
            }
        if (next.hits >= threshold) {
            val wasBlocked = blocked[relay] != null
            blocked[relay] = candidate
            if (!wasBlocked) {
                blockedSet.update { it + relay }
                return true
            }
        }
        return false
    }

    /**
     * True when [relay] has been shown to refuse the class of REQ that [filters] represents.
     * [Policy.SEARCH_ONLY] suppresses only when every filter lacks a `search` term.
     */
    fun shouldSuppress(
        relay: NormalizedRelayUrl,
        filters: List<Filter>,
    ): Boolean =
        when (blocked[relay]) {
            Policy.NO_READS -> true
            Policy.SEARCH_ONLY -> filters.isNotEmpty() && filters.all { it.search.isNullOrEmpty() }
            null -> false
        }

    fun blockedRelays(): Map<NormalizedRelayUrl, Policy> = blocked.snapshot()

    private fun classify(reason: String): Policy? {
        val t = reason.lowercase()
        if (SEARCH_REQUIRED_MARKERS.any { it in t }) return Policy.SEARCH_ONLY
        if (NO_READ_MARKERS.any { it in t }) return Policy.NO_READS
        return null
    }

    companion object {
        // The relay only serves NIP-50 search REQs (a plain feed REQ is refused).
        private val SEARCH_REQUIRED_MARKERS =
            listOf(
                "search filter is required",
                "search filter required",
                "requires a search",
                "search query is required",
                "search term is required",
            )

        // The relay answers no REQs at all (write-only / queries disabled). Kept narrow
        // and unconditional so it never catches an auth-gated "authenticate first" message,
        // which is resolved by the auth subsystem, not by giving up on the relay.
        private val NO_READ_MARKERS =
            listOf(
                "does not accept req",
                "not accepting req",
                "queries not allowed",
                "queries are not allowed",
                "does not allow queries",
                "reqs are not allowed",
            )
    }
}
