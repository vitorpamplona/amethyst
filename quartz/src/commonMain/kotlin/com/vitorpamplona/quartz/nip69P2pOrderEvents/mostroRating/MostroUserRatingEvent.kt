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
package com.vitorpamplona.quartz.nip69P2pOrderEvents.mostroRating

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.nip01Core.core.BaseAddressableEvent
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.TagArray
import com.vitorpamplona.quartz.nip01Core.core.TagArrayBuilder
import com.vitorpamplona.quartz.nip01Core.core.fastAny
import com.vitorpamplona.quartz.nip01Core.hints.PubKeyHintProvider
import com.vitorpamplona.quartz.nip01Core.hints.types.PubKeyHint
import com.vitorpamplona.quartz.nip01Core.signers.eventTemplate
import com.vitorpamplona.quartz.nip01Core.tags.dTag.dTag
import com.vitorpamplona.quartz.nip40Expiration.expiration
import com.vitorpamplona.quartz.nip69P2pOrderEvents.instanceName
import com.vitorpamplona.quartz.nip69P2pOrderEvents.mostroRating.tags.TotalReviewsTag
import com.vitorpamplona.quartz.nip69P2pOrderEvents.platform
import com.vitorpamplona.quartz.nip69P2pOrderEvents.tags.DocumentTypeTag
import com.vitorpamplona.quartz.utils.Hex
import com.vitorpamplona.quartz.utils.TimeUtils

/**
 * A Mostro instance's reputation snapshot of one user (kind 38384): how many reviews the user's
 * trades earned, the average, the last, best and worst rating, and since when they trade. Spec:
 * Mostro protocol `user_rating.md`, split off kind 38383 by `mostro_separate_kinds.md`. Not a
 * NIP.
 *
 * Signed by the Mostro instance, not by the rater: Mostro aggregates the 1..5 ratings both trade
 * parties send it over encrypted messages and re-publishes this event, addressable on `d` = the
 * rated user's trade pubkey (hex), after each one. It carries a NIP-40 expiration (90 days by
 * default). Content is empty. Ratings are machine data, so this is not a SearchableEvent.
 *
 * **The kind is shared, and the other user outnumbers Mostro.** Paygress, a Cashu-paid compute
 * marketplace, publishes its provider heartbeats on 38384 every minute; in the 2026-10 census
 * they were 350 of 448 sampled events (285 of 300 in a later fetch). Two designs were possible:
 *
 * - one tolerant class whose accessors return null on a heartbeat, or
 * - a tag-based split in `EventFactory`, as kind 38000 does (`MintRecommendationEvent` vs
 *   `UnrecognizedKind38000Event`).
 *
 * This is the split. A tolerant class would still *be* a Mostro rating to every `is` check, feed
 * filter and renderer, and a heartbeat's `d` (`paygress:heartbeat:v1:<provider>:<minute>`) would
 * be one sloppy accessor away from being read as the rated user. With the split, a heartbeat is
 * an [UnrecognizedKind38384Event] (still addressable, never indexed, never drawn), so a Paygress
 * event cannot be presented as a Mostro rating at all. [isMostroRating] decides.
 */
@Immutable
class MostroUserRatingEvent(
    id: HexKey,
    pubKey: HexKey,
    createdAt: Long,
    tags: TagArray,
    content: String,
    sig: HexKey,
) : BaseAddressableEvent(id, pubKey, createdAt, KIND, tags, content, sig),
    PubKeyHintProvider {
    /** No tag here carries a relay hint. */
    override fun pubKeyHints() = emptyList<PubKeyHint>()

    /**
     * `RATED`: the rated user's trade pubkey, from `d`. The one case where this event's `d` is a
     * reference: it names the subject, as a NIP-85 user assertion's `d` does (`SUBJECT`), rather
     * than restating the event's own address. The signer is the instance (`AUTHOR`), not a rater.
     */
    override fun linkedPubKeys() = listOfNotNull(ratedPubKey())

    /** The rated user's trade pubkey (`d`), when it is a 64-hex key. */
    fun ratedPubKey(): HexKey? = dTag().takeIf { it.length == 64 && Hex.isHex64(it) }

    fun platform() = tags.platform()

    fun instanceName() = tags.instanceName()

    fun totalReviews() = tags.totalReviews()

    /** The average rating on the 1..5 scale. */
    fun totalRating() = tags.totalRating()

    fun lastRating() = tags.lastRating()

    fun maxRate() = tags.maxRate()

    fun minRate() = tags.minRate()

    /** Start of the UTC day of the user's first trade. Newer daemons only; see [firstTradeDay]. */
    fun since() = tags.since()

    /** DEPRECATED by the spec: days since the first trade, as of [createdAt]. */
    fun days() = tags.days()

    /**
     * Start of the UTC day of the user's first trade: `since`, or, from daemons that predate it,
     * the deprecated `days` counted back from [createdAt] (user_rating.md: clients "MUST read
     * `since` when present and MAY fall back to `days`").
     */
    fun firstTradeDay(): Long? =
        since() ?: days()?.let {
            val instant = createdAt - it * TimeUtils.ONE_DAY
            instant - instant % TimeUtils.ONE_DAY
        }

    companion object {
        const val KIND = 38384

        /**
         * True for a Mostro rating shape: a `z` of `rating`, which every daemon since the kind
         * split publishes, or, failing that, a `total_reviews` tag. A Paygress heartbeat (`t`,
         * `d`, `v`, `p`, JSON content) carries neither.
         */
        fun isMostroRating(tags: TagArray): Boolean =
            DocumentTypeTag.isType(tags, DocumentTypeTag.RATING) ||
                tags.fastAny { it.size > 1 && it[0] == TotalReviewsTag.TAG_NAME && it[1].isNotEmpty() }

        fun build(
            ratedPubKey: HexKey,
            totalReviews: Long,
            totalRating: Double,
            lastRating: Long,
            maxRate: Long,
            minRate: Long,
            since: Long? = null,
            expiration: Long? = null,
            instanceName: String? = null,
            createdAt: Long = TimeUtils.now(),
            initializer: TagArrayBuilder<MostroUserRatingEvent>.() -> Unit = {},
        ) = eventTemplate(KIND, "", createdAt) {
            dTag(ratedPubKey)
            expiration?.let { expiration(it) }
            totalReviews(totalReviews)
            totalRating(totalRating)
            lastRating(lastRating)
            maxRate(maxRate)
            minRate(minRate)
            since?.let { since(it) }
            platform(instanceName)
            documentType()
            initializer()
        }
    }
}
