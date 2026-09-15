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
package com.vitorpamplona.amethyst.service.resourceusage

import com.vitorpamplona.amethyst.commons.feeds.FeedUpdateMeter
import com.vitorpamplona.amethyst.commons.feeds.FeedUpdateOutcome

/**
 * Books the feed fan-out into the resource-usage ledger.
 *
 * Every event bundle that arrives from the relays is handed to every
 * [com.vitorpamplona.amethyst.commons.feeds.FeedContentState] the account owns
 * — around 48 of them — regardless of which feed is on screen, or whether the
 * app is on screen at all. This meter is what turns that suspicion into
 * numbers: how often the fan-out runs, how long it takes, how much of it
 * happens backgrounded, and what fraction of the ~48 per-feed updates actually
 * change something a user could see.
 *
 * See hypothesis H1 in `amethyst/plans/2026-09-14-cpu-battery-diagnosis.md`.
 * If `feeds.skipped.*` and `feeds.unchanged.*` dwarf `feeds.changed.*`, the
 * answer is to stop fanning out to feeds nobody is collecting, not to make
 * each feed faster.
 *
 * Cost: [onFeedUpdate] is one map lookup and one atomic increment per feed per
 * bundle — the same hot path [RelayUsageListener] already runs per relay frame.
 * Nothing allocates: the outcome-to-key table is built once at class-init,
 * because `enum.name.lowercase()` on this path would be ~48 throwaway strings
 * per bundle.
 */
class FeedUsageMeter(
    private val accountant: ResourceUsageAccountant,
    private val isForeground: () -> Boolean,
) : FeedUpdateMeter {
    override fun onFeedUpdate(outcome: FeedUpdateOutcome) {
        accountant.add(keyFor(outcome, isForeground()), 1)
    }

    override fun onBundleFanOut(
        noteCount: Int,
        elapsedNanos: Long,
    ) {
        val visibility = if (isForeground()) UsageKeys.FG else UsageKeys.BG
        accountant.add(UsageKeys.ingestBundles(visibility), 1)
        accountant.add(UsageKeys.ingestNotes(visibility), noteCount.toLong())
        accountant.add(UsageKeys.feedsFanoutCount(visibility), 1)
        accountant.add(UsageKeys.feedsFanoutUs(visibility), elapsedNanos / 1_000)
    }

    /** Installs this as the process-wide meter. Idempotent. */
    fun install() {
        FeedUpdateMeter.instance = this
    }

    companion object {
        /** Key segment per outcome — fixed strings, never a runtime-derived name. */
        private val OUTCOME_SEGMENTS =
            mapOf(
                FeedUpdateOutcome.SKIPPED to "skipped",
                FeedUpdateOutcome.CHANGED to "changed",
                FeedUpdateOutcome.UNCHANGED to "unchanged",
                FeedUpdateOutcome.REBUILT to "rebuilt",
            )

        /** Every key this meter can emit, indexed by outcome then foreground-ness. */
        private val KEYS: Map<FeedUpdateOutcome, Array<String>> =
            FeedUpdateOutcome.entries.associateWith { outcome ->
                val segment = OUTCOME_SEGMENTS.getValue(outcome)
                arrayOf(
                    UsageKeys.feedOutcome(segment, UsageKeys.BG),
                    UsageKeys.feedOutcome(segment, UsageKeys.FG),
                )
            }

        fun keyFor(
            outcome: FeedUpdateOutcome,
            foreground: Boolean,
        ): String = KEYS.getValue(outcome)[if (foreground) 1 else 0]

        /** The outcome key segments, for tests and the report. */
        val outcomeSegments: Collection<String> get() = OUTCOME_SEGMENTS.values
    }
}
