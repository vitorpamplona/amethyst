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
package com.vitorpamplona.amethyst.commons.observer

/**
 * Can the search relay rank for this reader — and if not, which link is missing?
 *
 * A trimmed port of the Observer's `Readiness.assess` (itself a port of
 * vespa-relay's `shared/readiness.js`). Two of its properties are kept because
 * they are the reason it exists:
 *
 *  1. **There is no fallback lens.** `observer:<pk> sort:rank` with a token the
 *     relay cannot resolve does not fail — it silently becomes the anonymous
 *     ranking, which the Observer measured as 209 of 400 posts from one spam
 *     account. So a reader without a working lens gets a reason, not a paper.
 *  2. **The ranked probe is not redundant** with the 10040 check. Cards can be
 *     present and not yet projected; only asking the lensed and anonymous
 *     reads the same question catches that.
 *
 * The COUNT links (cards here vs. upstream, own posts behind) are dropped: the
 * Observer measured COUNTs hanging the check for seconds at a time on this
 * relay, and neither changes whether a paper can be printed.
 */
object ObserverReadiness {
    enum class State {
        /** No kind 10040 on the search relay: the reader never chose who computes their web of trust. */
        NO_SCORE_LIST,

        /** A 10040 exists but names no public `30382:rank` provider with a relay hint. */
        NO_RANK_SERVICE,

        /** The lensed probe is empty while the anonymous one is not: the cards are not projected yet. */
        NOT_PROJECTED,

        /** Neither probe answered with anything — the relay is down, unreachable or refusing us. */
        RELAY_SILENT,

        READY,
    }

    data class Facts(
        /** Whether the search relay holds a kind 10040 for the reader. */
        val scoreListSeen: Boolean,
        /** The `30382:rank` provider's pubkey, from the 10040, when it has one. */
        val rankService: String?,
        /** Rows the lensed and the anonymous ranked probes returned for the same question. */
        val probeLensed: Int,
        val probeAnonymous: Int,
    )

    /** The first unmet link wins; nothing below it is reported as a second failure. */
    fun assess(facts: Facts): State {
        // A 24-hour window of kind 1 is never empty, so two empty probes mean the
        // relay said nothing — and an absent 10040 from a silent relay is not
        // evidence the reader has none.
        if (facts.probeLensed == 0 && facts.probeAnonymous == 0) return State.RELAY_SILENT
        if (!facts.scoreListSeen) return State.NO_SCORE_LIST
        if (facts.rankService.isNullOrBlank()) return State.NO_RANK_SERVICE
        if (facts.probeLensed == 0) return State.NOT_PROJECTED
        return State.READY
    }
}
