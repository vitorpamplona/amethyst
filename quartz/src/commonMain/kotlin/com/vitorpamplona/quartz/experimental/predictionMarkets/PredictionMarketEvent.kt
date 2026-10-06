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
package com.vitorpamplona.quartz.experimental.predictionMarkets

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.nip01Core.core.BaseAddressableEvent
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.TagArray
import com.vitorpamplona.quartz.nip01Core.core.fastForEach
import com.vitorpamplona.quartz.nip50Search.IndexableFieldVisitor
import com.vitorpamplona.quartz.nip50Search.SearchableEvent
import com.vitorpamplona.quartz.utils.TimeUtils

/**
 * A prediction market (kind 38000), as published by BAO Markets.
 *
 * App-specific and **not** defined by any NIP: kind 38000 is NIP-87's mint recommendation, and
 * BAO Markets reuses the number. [com.vitorpamplona.quartz.utils.EventFactory] tells the two
 * apart by tags ([isPredictionMarket]). Amethyst only reads these — it never publishes them.
 *
 * The schema is derived from events seen in the wild, in three shapes:
 *
 * - **Current**: everything in tags — `market`, `status` (also `state`, `s`), `end`, `network`/`n`
 *   ("demo" is play money), `category`/`c`, repeated `outcome`, `resolution`, `resolution_source`,
 *   `min_bet`/`max_bet` (sats), `fee_percent`, `oracle`, `cancel_reason`, `client`, and a `data`
 *   tag holding JSON `{title, description, outcomes, …}`. `content` is a human-readable social
 *   post of the same words.
 * - **BAO Fund**: a `title` tag, `c` = bao-fund, `n`, `pool_model`, `end`, `["outcome", id,
 *   label]` tags, and JSON `content` `{title, description, outcomes: [{id, label}], …}`.
 * - **First**: `type`, `category`, `oracle`, `end`, `state`, `platform_fee` tags and JSON
 *   `content` `{title, description, outcomes: [{id, label, probability}], …}`.
 *
 * Tags win over JSON wherever both carry a field. The JSON is parsed at most once per event,
 * lazily, and a malformed blob just reads as absent.
 */
@Immutable
class PredictionMarketEvent(
    id: HexKey,
    pubKey: HexKey,
    createdAt: Long,
    tags: Array<Array<String>>,
    content: String,
    sig: HexKey,
) : BaseAddressableEvent(id, pubKey, createdAt, KIND, tags, content, sig),
    SearchableEvent {
    private val dataDetails by lazy { PredictionMarketDetails.parse(tags.marketData()) }

    private val contentDetails by lazy { PredictionMarketDetails.parse(content) }

    // The question and its description, then each outcome label (poll-option style), the
    // resolution and the cancel reason. The social post in `content` is the fallback body only:
    // in the current shape it restates title + description word for word.
    override fun indexableContent() = (listOfNotNull(title(), description()) + outcomes() + listOfNotNull(resolution(), cancelReason(), socialPost())).joinToString("\n")

    // The read path: the same fields indexableContent() joins, handed over without
    // building the joined string a scan would throw away.
    override fun forEachIndexableField(visitor: IndexableFieldVisitor) {
        if (!visitor.visit(title())) return
        if (!visitor.visit(description())) return
        // outcomeOptions() is memoized, so the per-keystroke walk re-parses no tags or JSON.
        val outcomes = outcomeOptions()
        for (i in outcomes.indices) {
            if (!visitor.visit(outcomes[i].label)) return
        }
        if (!visitor.visit(resolution())) return
        if (!visitor.visit(cancelReason())) return
        visitor.visit(socialPost())
    }

    /**
     * The human-readable post in `content`, when that is what `content` holds (not one of the
     * JSON shapes) and the event has no [description] it would merely repeat.
     */
    fun socialPost(): String? {
        if (description() != null || contentDetails != null) return null
        val trimmed = content.trim()
        if (trimmed.isEmpty() || trimmed == "null" || trimmed.startsWith("{") || trimmed.startsWith("[")) return null
        return content
    }

    fun marketId() = tags.marketId()

    /** The question being predicted. */
    fun title() = dataDetails?.title ?: tags.marketTitle() ?: contentDetails?.title

    fun description() = dataDetails?.description ?: contentDetails?.description

    /** The outcomes one can bet on, id and label: the `outcome` tags, else the JSON `outcomes`. */
    fun outcomeOptions(): List<PredictionMarketOutcome> = outcomeOptionsValue

    // Memoized like the details above: search reads the outcomes per event per keystroke, and
    // each read would otherwise re-scan the tags and allocate a fresh list.
    private val outcomeOptionsValue by lazy {
        tags.marketOutcomes().ifEmpty {
            dataDetails?.outcomes?.ifEmpty { null } ?: contentDetails?.outcomes ?: emptyList()
        }
    }

    /** The outcomes as a reader sees them, by label. */
    fun outcomes(): List<String> = outcomeOptions().map { it.label }

    /** The winning outcome, once resolved. */
    fun resolution() = tags.marketResolution() ?: dataDetails?.resolution ?: contentDetails?.resolution

    /** When betting closes, unix seconds. */
    fun endsAt() = tags.marketEnd() ?: dataDetails?.endDate ?: contentDetails?.endDate

    fun category() = tags.marketCategory() ?: dataDetails?.category ?: contentDetails?.category

    /** `demo` (play money, most markets), `mainnet`, or whatever else the publisher names. */
    fun network() = tags.marketNetwork()

    /** True for play-money markets: nothing real is at stake. */
    fun isDemo() = network().equals(DEMO_NETWORK, ignoreCase = true)

    fun resolutionSource() = tags.marketResolutionSource() ?: dataDetails?.resolutionSource ?: contentDetails?.resolutionSource

    fun minBetSats() = tags.marketMinBet() ?: dataDetails?.minBetSats ?: contentDetails?.minBetSats

    fun maxBetSats() = tags.marketMaxBet() ?: dataDetails?.maxBetSats ?: contentDetails?.maxBetSats

    fun feePercent() = tags.marketFeePercent() ?: dataDetails?.feePercent ?: contentDetails?.feePercent

    fun cancelReason() = tags.marketCancelReason() ?: contentDetails?.reason

    fun oracle() = tags.marketOracle()

    fun client() = tags.marketClient()

    /**
     * The market's status at [now] (unix seconds), or null when nothing says.
     *
     * The declared `status`/`state`/`s` word decides when it is one we know; a declared RESOLVED
     * or CANCELLED is final. Otherwise the event's own evidence refines it: a `resolution` means
     * RESOLVED, a `cancel_reason` on an undeclared market means CANCELLED, and an open market
     * whose `end` has passed is CLOSED — publishers leave "active" in place long after betting
     * stops.
     */
    fun status(now: Long = TimeUtils.now()): PredictionMarketStatus? {
        val declared = declaredStatus()
        if (declared == PredictionMarketStatus.RESOLVED || declared == PredictionMarketStatus.CANCELLED) return declared
        if (resolution() != null) return PredictionMarketStatus.RESOLVED
        if (declared == PredictionMarketStatus.CLOSED) return declared
        if (declared == null && cancelReason() != null) return PredictionMarketStatus.CANCELLED

        val end = endsAt()
        return when {
            end != null -> if (end > now) PredictionMarketStatus.OPEN else PredictionMarketStatus.CLOSED
            else -> declared
        }
    }

    private fun declaredStatus(): PredictionMarketStatus? =
        tags.marketStatusValues().firstNotNullOfOrNull(PredictionMarketStatus::parse)
            ?: contentDetails?.status?.let(PredictionMarketStatus::parse)

    companion object {
        const val KIND = 38000
        const val DEMO_NETWORK = "demo"

        /**
         * True when a kind-38000 event is shaped like a prediction market: it names a `market`,
         * offers at least two `outcome`s, or declares a market `type` with an `end`. Checked
         * after [com.vitorpamplona.quartz.nip87Ecash.recommendation.MintRecommendationEvent.isMintRecommendation]
         * and [com.vitorpamplona.quartz.experimental.ballots.BallotEvent.isBallot].
         */
        fun isPredictionMarket(tags: TagArray): Boolean {
            var outcomes = 0
            var hasType = false
            var hasEnd = false
            tags.fastForEach {
                if (it.size > 1) {
                    when (it[0]) {
                        "market" -> if (it[1].isNotBlank()) return true
                        "outcome" -> outcomes++
                        "type" -> hasType = true
                        "end" -> hasEnd = true
                    }
                }
            }
            return outcomes >= 2 || (hasType && hasEnd)
        }
    }
}
