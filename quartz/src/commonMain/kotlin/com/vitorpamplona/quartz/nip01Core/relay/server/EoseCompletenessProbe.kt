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

import com.vitorpamplona.quartz.nip01Core.relay.commands.toClient.EoseMessage
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter

/**
 * Works out the NIP-67 completeness hint for one REQ's stored replay, only ever
 * claiming what the store's answer actually proves:
 *
 *  - **One filter with `limit = L > 0`**: the store is asked for `L + 1` rows and the
 *    extra (oldest) row is withheld. Seeing it proves the relay holds more (`"more"`);
 *    not seeing it proves the replay was complete (`"finish"`). The client receives
 *    exactly the `L` events it would have without the probe.
 *  - **Every filter unbounded (`limit = null`)**: the store returns every match
 *    (STORE-F12), so the replay is complete (`"finish"`).
 *  - **Several filters, some limited**: limits are per filter and the replay is their
 *    deduped union, so rows cannot be attributed back to a filter without matching
 *    each one. Only the cheap, sound case is claimed: fewer rows than the smallest
 *    limit means no filter reached its limit (`"finish"`). Anything else sends no hint,
 *    which NIP-67 allows (absence is not definitive).
 *
 * `limit = 0` never gets a hint and is never probed: NIP-01 forbids returning stored
 * events for it, so it keeps its exact query.
 *
 * This assumes the backing store honours `limit` exactly and treats `null` as
 * unbounded, which is why [RelaySession] only uses it when the server opts in.
 */
internal class EoseCompletenessProbe private constructor(
    /** The filters to actually query (the limit may be raised by one). */
    val queryFilters: List<Filter>,
    /** Max stored events to forward; the rest are only counted. Null: forward all. */
    private val forwardCap: Int?,
    private val rule: Rule,
) {
    private enum class Rule { PROBE, ALL_UNBOUNDED, UNDER_MIN_LIMIT }

    private val minLimit: Int = if (rule == Rule.UNDER_MIN_LIMIT) queryFilters.minOf { it.limit ?: Int.MAX_VALUE } else 0

    /** Stored events the store produced for this REQ, forwarded or not. */
    private var seen = 0

    /**
     * Counts one stored event and says whether it should be forwarded to the client.
     * Called from the single replay coroutine, before EOSE.
     */
    fun onStored(): Boolean {
        seen++
        return forwardCap == null || seen <= forwardCap
    }

    /** The hints for this replay's EOSE, or null for none. */
    fun hints(): List<String>? =
        when (rule) {
            Rule.PROBE -> if (seen > forwardCap!!) MORE else FINISH
            Rule.ALL_UNBOUNDED -> FINISH
            Rule.UNDER_MIN_LIMIT -> if (seen < minLimit) FINISH else null
        }

    companion object {
        private val FINISH = listOf(EoseMessage.HINT_FINISH)
        private val MORE = listOf(EoseMessage.HINT_MORE)

        fun of(filters: List<Filter>): EoseCompletenessProbe? {
            if (filters.isEmpty()) return null
            if (filters.any { it.limit == 0 }) return null

            if (filters.size == 1) {
                val limit = filters[0].limit
                if (limit != null && limit < Int.MAX_VALUE) {
                    return EoseCompletenessProbe(listOf(filters[0].copy(limit = limit + 1)), limit, Rule.PROBE)
                }
            }

            if (filters.all { it.limit == null }) return EoseCompletenessProbe(filters, null, Rule.ALL_UNBOUNDED)

            return EoseCompletenessProbe(filters, null, Rule.UNDER_MIN_LIMIT)
        }
    }
}
