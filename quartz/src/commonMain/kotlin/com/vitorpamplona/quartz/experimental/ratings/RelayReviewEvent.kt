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
package com.vitorpamplona.quartz.experimental.ratings

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.experimental.ratings.tags.CategoryRatingTag
import com.vitorpamplona.quartz.nip01Core.core.BaseAddressableEvent
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.TagArray
import com.vitorpamplona.quartz.nip01Core.core.TagArrayBuilder
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer
import com.vitorpamplona.quartz.nip01Core.signers.EventTemplate
import com.vitorpamplona.quartz.nip01Core.signers.eventTemplate
import com.vitorpamplona.quartz.nip01Core.tags.dTag.dTag
import com.vitorpamplona.quartz.nip50Search.IndexableFieldVisitor
import com.vitorpamplona.quartz.nip50Search.SearchableEvent
import com.vitorpamplona.quartz.utils.TimeUtils

/**
 * A review of a relay (kind 31987).
 *
 * **Not defined by a merged NIP.** The shape is what the clients publishing it emit:
 *
 * - `d` — the relay's URL, which is also the addressable identity, so one review per author per
 *   relay. A `relay` tag is accepted as a fallback for publishers that put the URL there instead.
 * - `rating` — one or more scores on a 0..1 scale. The tag with no third element is the overall
 *   score; one with a third element scores that named aspect (`speed`, `uptime`, ...).
 * - `content` — the written review.
 *
 * Unlike [EntityRatingEvent] there is no scale ambiguity here: every publisher of this kind uses
 * the 0..1 fraction, so a value outside that range is malformed rather than a raw star count.
 */
@Immutable
class RelayReviewEvent(
    id: HexKey,
    pubKey: HexKey,
    createdAt: Long,
    tags: TagArray,
    content: String,
    sig: HexKey,
) : BaseAddressableEvent(id, pubKey, createdAt, KIND, tags, content, sig),
    SearchableEvent {
    override fun indexableContent() = content

    // The read path: the same fields indexableContent() joins, handed over without
    // building the joined string a scan would throw away.
    override fun forEachIndexableField(visitor: IndexableFieldVisitor) {
        visitor.visit(content)
    }

    /** Every `rating` tag, overall and per-category, in event order. */
    fun ratings(): List<CategoryRatingTag> = tags.mapNotNull(CategoryRatingTag::parse)

    /** The overall score — the `rating` tag with no category. */
    fun overallRating(): Double? = ratings().firstOrNull { it.category == null }?.value

    /** The per-aspect scores, in event order. */
    fun categoryRatings(): List<CategoryRatingTag> = ratings().filter { it.category != null }

    /** The overall score as 0..[MAX_STARS] stars, for a shared star row. */
    fun stars(): Double? = overallRating()?.let { it * MAX_STARS }

    /** The reviewed relay's URL as published, from `d` or a `relay` tag. */
    fun relayUrl(): String? =
        dTag().takeIf { it.isNotEmpty() }
            ?: tags.firstNotNullOfOrNull { tag ->
                if (tag.size > 1 && tag[0] == RELAY_TAG && tag[1].isNotEmpty()) tag[1] else null
            }

    /** The reviewed relay, normalized, or null when the URL is missing or unusable. */
    fun relay(): NormalizedRelayUrl? = relayUrl()?.let { RelayUrlNormalizer.normalizeOrNull(it) }

    companion object {
        const val KIND = 31987
        const val RELAY_TAG = "relay"

        /** Relay reviews share the five-star presentation with [EntityRatingEvent]. */
        const val MAX_STARS = EntityRatingEvent.MAX_STARS

        fun build(
            relayUrl: String,
            stars: Int,
            review: String = "",
            categories: Map<String, Int> = emptyMap(),
            createdAt: Long = TimeUtils.now(),
            initializer: TagArrayBuilder<RelayReviewEvent>.() -> Unit = {},
        ): EventTemplate<RelayReviewEvent> =
            eventTemplate(KIND, review, createdAt) {
                dTag(relayUrl)
                add(CategoryRatingTag.assemble(stars.coerceIn(1, MAX_STARS).toDouble() / MAX_STARS))
                categories.forEach { (category, value) ->
                    add(CategoryRatingTag.assemble(value.coerceIn(1, MAX_STARS).toDouble() / MAX_STARS, category))
                }

                initializer()
            }
    }
}
