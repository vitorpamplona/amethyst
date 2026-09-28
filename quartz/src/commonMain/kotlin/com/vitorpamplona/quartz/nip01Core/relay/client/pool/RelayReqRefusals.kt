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
import com.vitorpamplona.quartz.utils.Log
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
        learnDisallowedKinds(relay, reason)
        learnMaxFilters(relay, reason)
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
        // Everything this REQ asks for is a kind the relay refused.
        (filters.isNotEmpty() && narrow(relay, filters).isEmpty()) ||
            when (blocked[relay]) {
                Policy.NO_READS -> true
                Policy.SEARCH_ONLY -> filters.isNotEmpty() && filters.all { it.search.isNullOrEmpty() }
                null -> false
            }

    fun blockedRelays(): Map<NormalizedRelayUrl, Policy> = blocked.snapshot()

    // Kinds a relay has said it will not serve at all ("kind not allowed: 21059").
    private val disallowedKinds = ConcurrentMap<NormalizedRelayUrl, Set<Int>>()

    /**
     * Learn the kinds a relay named as not allowed in a CLOSED reason.
     *
     * A relay with a kind allowlist refuses the WHOLE REQ over one kind it doesn't
     * serve, so the kinds it does serve in that filter are lost with it. The message
     * names the kind, so one refusal is enough to learn it: nothing is guessed, and
     * [narrow] strips exactly that kind for exactly this relay.
     */
    private fun learnDisallowedKinds(
        relay: NormalizedRelayUrl,
        reason: String,
    ) {
        val kinds = parseDisallowedKinds(reason)
        if (kinds.isEmpty()) return
        disallowedKinds.merge(relay, kinds) { old, new -> old + new }
    }

    /**
     * [filters] as [relay] will actually serve them: kinds the relay refused are
     * removed. A filter whose kinds all got removed is dropped, never sent with an
     * empty kind list, which would ask for EVERY kind. A filter with no kind list
     * is left alone.
     */
    fun narrow(
        relay: NormalizedRelayUrl,
        filters: List<Filter>,
    ): List<Filter> {
        val withoutRefusedKinds = stripRefusedKinds(relay, filters)
        val cap = maxFilters[relay] ?: return withoutRefusedKinds
        if (withoutRefusedKinds.size <= cap) return withoutRefusedKinds
        val merged = mergeForCap(withoutRefusedKinds)
        if (merged.size <= cap) return merged
        // Still too many: a partial REQ the relay accepts beats a whole one it refuses.
        val dropped = merged.drop(cap).map { it.kinds }
        if (trimWarned.putIfAbsent("${relay.url} $dropped", Unit) == null) {
            Log.w("RelayReqRefusals") { "${relay.url} caps REQs at $cap filters; sending $cap of ${merged.size}, dropping kinds $dropped" }
        }
        return merged.take(cap)
    }

    private fun stripRefusedKinds(
        relay: NormalizedRelayUrl,
        filters: List<Filter>,
    ): List<Filter> {
        val refused = disallowedKinds[relay] ?: return filters
        return filters.mapNotNull { filter ->
            val kinds = filter.kinds ?: return@mapNotNull filter
            val kept = kinds.filterNot { it in refused }
            when {
                kept.size == kinds.size -> filter
                kept.isEmpty() -> null
                else -> filter.copy(kinds = kept)
            }
        }
    }

    // Shapes already reported as trimmed, so a REQ re-decided on every EOSE warns once.
    private val trimWarned = ConcurrentMap<String, Unit>()

    // The most filters a relay accepts in one REQ, learned from "invalid number of filters: N".
    private val maxFilters = ConcurrentMap<NormalizedRelayUrl, Int>()

    private fun learnMaxFilters(
        relay: NormalizedRelayUrl,
        reason: String,
    ) {
        val cap = parseMaxFilters(reason) ?: return
        maxFilters.merge(relay, cap) { old, new -> minOf(old, new) }
    }

    fun disallowedKinds(relay: NormalizedRelayUrl): Set<Int> = disallowedKinds[relay] ?: emptySet()

    private fun classify(reason: String): Policy? {
        val t = reason.lowercase()
        if (SEARCH_REQUIRED_MARKERS.any { it in t }) return Policy.SEARCH_ONLY
        if (NO_READ_MARKERS.any { it in t }) return Policy.NO_READS
        return null
    }

    companion object {
        // "kind not allowed: 21059", "kinds not allowed: 7374, 30382", "kind 21059 is not allowed"
        private val KINDS_AFTER_MARKER = Regex("""kinds? (?:is |are )?not allowed:?\s*([0-9][0-9,\s]*)""")
        private val KIND_BEFORE_MARKER = Regex("""kind ([0-9]+) (?:is )?not allowed""")

        // "invalid number of filters: 4" (strfry policy) — the relay refused N, so it takes fewer.
        private val INVALID_FILTER_COUNT = Regex("""invalid number of filters:?\s*([0-9]+)""")

        fun parseMaxFilters(reason: String): Int? {
            val refusedCount =
                INVALID_FILTER_COUNT
                    .find(reason.lowercase())
                    ?.groupValues
                    ?.get(1)
                    ?.toIntOrNull() ?: return null
            return (refusedCount - 1).takeIf { it >= 1 }
        }

        /**
         * Merges filters that ask for the same thing except for ONE set of values (the kinds,
         * one tag's values, the authors, or the ids) into a single filter with the union of those values
         * and the earliest `since`. The result matches a superset of what each original
         * matched, so nothing asked for is lost; an event the union adds is one some other
         * filter in the same REQ already wanted, or a harmless duplicate.
         *
         * A filter with a `limit` is never merged: a limit per filter and a limit over the
         * union are different requests.
         */
        fun mergeForCap(filters: List<Filter>): List<Filter> {
            val out = mutableListOf<Filter>()
            val remaining = filters.toMutableList()
            while (remaining.isNotEmpty()) {
                val head = remaining.removeAt(0)
                if (head.limit != null) {
                    out.add(head)
                    continue
                }
                var merged = head
                val iterator = remaining.iterator()
                while (iterator.hasNext()) {
                    val candidate = iterator.next()
                    val union = unionIfMergeable(merged, candidate) ?: continue
                    merged = union
                    iterator.remove()
                }
                out.add(merged)
            }
            return out
        }

        private fun unionIfMergeable(
            a: Filter,
            b: Filter,
        ): Filter? {
            if (b.limit != null) return null
            if (a.until != b.until || a.search != b.search || a.tagsAll != b.tagsAll) return null
            val sameKinds = a.kinds?.toSet() == b.kinds?.toSet()
            val since = if (a.since == null || b.since == null) null else minOf(a.since, b.since)

            val sameIds = a.ids?.toSet() == b.ids?.toSet()
            val sameAuthors = a.authors?.toSet() == b.authors?.toSet()
            val aTags = a.tags ?: emptyMap()
            val bTags = b.tags ?: emptyMap()
            if (aTags.keys != bTags.keys) return null
            val differingTags = aTags.keys.filter { aTags[it]?.toSet() != bTags[it]?.toSet() }

            val differences = (if (sameKinds) 0 else 1) + (if (sameIds) 0 else 1) + (if (sameAuthors) 0 else 1) + differingTags.size
            return when {
                differences == 0 -> a.copy(since = since)
                differences > 1 -> null
                !sameKinds -> if (a.kinds == null || b.kinds == null) null else a.copy(kinds = (a.kinds + b.kinds).distinct(), since = since)
                !sameIds -> if (a.ids == null || b.ids == null) null else a.copy(ids = (a.ids + b.ids).distinct(), since = since)
                !sameAuthors -> if (a.authors == null || b.authors == null) null else a.copy(authors = (a.authors + b.authors).distinct(), since = since)
                else -> {
                    val key = differingTags.single()
                    a.copy(tags = aTags + (key to (aTags.getValue(key) + bTags.getValue(key)).distinct()), since = since)
                }
            }
        }

        fun parseDisallowedKinds(reason: String): Set<Int> {
            val t = reason.lowercase()
            val kinds = mutableSetOf<Int>()
            KINDS_AFTER_MARKER.findAll(t).forEach { m ->
                m.groupValues[1].split(',', ' ').mapNotNullTo(kinds) { it.trim().toIntOrNull() }
            }
            KIND_BEFORE_MARKER.findAll(t).forEach { m -> m.groupValues[1].toIntOrNull()?.let { kinds.add(it) } }
            return kinds
        }

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
