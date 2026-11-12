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
package com.vitorpamplona.amethyst.commons.relayClient.eoseManagers

import com.vitorpamplona.amethyst.commons.util.KmpLock
import com.vitorpamplona.amethyst.commons.util.withLock
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl

/**
 * The kinds each relay refused (a CLOSED "kind not allowed"), learned from what its answers report.
 *
 * The pool strips those kinds from what it sends that relay and from the filters its EOSE reports,
 * so a cursor records a shape without them. Planning the full request again would then never find
 * it covered, and the relay would be asked from scratch on every pass. Planning ignores these kinds
 * on that relay: it can never send them anyway.
 *
 * Learned by comparing a reported filter with the one the subscription asked for: the same in every
 * field and window but with fewer kinds means the pool took those out. A kind counts as refused
 * only after [CONFIRMATIONS] such answers, so one answer to an older request (asked before a kind
 * was added) does not make the relay skip it, and an answer that carries the kind again forgets it.
 * The pool's own list (quartz `RelayReqRefusals`) is private; exposing it would replace this.
 */
class RefusedKinds {
    private val lock = KmpLock()
    private val seenStripped = mutableMapOf<NormalizedRelayUrl, MutableMap<Int, Int>>()

    private fun refused(relay: NormalizedRelayUrl): Set<Int> = lock.withLock { seenStripped[relay]?.filterValues { it >= CONFIRMATIONS }?.keys }.orEmpty()

    /** [requested] without the kinds [relay] refused; unchanged when it would ask for none. */
    fun askable(
        relay: NormalizedRelayUrl,
        requested: FilterShape,
    ): FilterShape {
        val kinds = requested.kinds ?: return requested
        val refused = refused(relay)
        if (refused.isEmpty()) return requested
        val kept = kinds - refused
        return if (kept.isEmpty() || kept.size == kinds.size) requested else requested.copy(kinds = kept)
    }

    /** Learns the kinds [relay]'s pool stripped from [asked] to send [reported]. */
    fun learn(
        relay: NormalizedRelayUrl,
        asked: List<Filter>?,
        reported: List<Filter>?,
    ) {
        if (asked.isNullOrEmpty() || reported.isNullOrEmpty()) return
        val stripped = mutableSetOf<Int>()
        val sent = mutableSetOf<Int>()
        reported.forEach { filter ->
            val sentKinds = filter.kinds ?: return@forEach
            sent.addAll(sentKinds)
            val original =
                asked.firstOrNull { it !== filter && it.since == filter.since && it.until == filter.until && it.limit == filter.limit && sameButKinds(it, filter) }
                    ?: return@forEach
            val askedKinds = original.kinds ?: return@forEach
            if (askedKinds.containsAll(sentKinds)) stripped.addAll(askedKinds - sentKinds.toSet())
        }
        if (stripped.isEmpty() && sent.isEmpty()) return
        lock.withLock {
            val counts = seenStripped.getOrPut(relay) { mutableMapOf() }
            // Sent again: whatever refused it no longer applies.
            sent.forEach { counts.remove(it) }
            stripped.forEach { counts[it] = (counts[it] ?: 0) + 1 }
            if (counts.isEmpty()) seenStripped.remove(relay)
        }
    }

    private fun sameButKinds(
        a: Filter,
        b: Filter,
    ) = a.ids == b.ids && a.authors == b.authors && a.tags == b.tags && a.tagsAll == b.tagsAll && a.search == b.search

    companion object {
        /** Answers that must agree before a kind counts as refused. */
        const val CONFIRMATIONS = 2

        /** One per app: refusals are the relay's, whoever asked. */
        val shared = RefusedKinds()
    }
}
