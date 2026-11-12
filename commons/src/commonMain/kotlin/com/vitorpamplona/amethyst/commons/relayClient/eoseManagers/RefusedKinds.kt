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
 * field and window but with fewer kinds means the pool took those out.
 */
class RefusedKinds {
    private val lock = KmpLock()
    private val byRelay = mutableMapOf<NormalizedRelayUrl, Set<Int>>()

    /** [requested] without the kinds [relay] refused; unchanged when it would ask for none. */
    fun askable(
        relay: NormalizedRelayUrl,
        requested: FilterShape,
    ): FilterShape {
        val kinds = requested.kinds ?: return requested
        val refused = lock.withLock { byRelay[relay] } ?: return requested
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
        reported.forEach { sent ->
            val sentKinds = sent.kinds ?: return@forEach
            val original =
                asked.firstOrNull { it !== sent && it.since == sent.since && it.until == sent.until && it.limit == sent.limit && sameButKinds(it, sent) }
                    ?: return@forEach
            val askedKinds = original.kinds ?: return@forEach
            if (askedKinds.containsAll(sentKinds)) stripped.addAll(askedKinds - sentKinds.toSet())
        }
        if (stripped.isEmpty()) return
        lock.withLock { byRelay[relay] = byRelay[relay].orEmpty() + stripped }
    }

    private fun sameButKinds(
        a: Filter,
        b: Filter,
    ) = a.ids == b.ids && a.authors == b.authors && a.tags == b.tags && a.tagsAll == b.tagsAll && a.search == b.search

    companion object {
        /** One per app: refusals are the relay's, whoever asked. */
        val shared = RefusedKinds()
    }
}
