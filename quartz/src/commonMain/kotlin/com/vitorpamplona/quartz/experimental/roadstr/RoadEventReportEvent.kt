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
package com.vitorpamplona.quartz.experimental.roadstr

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.experimental.roadstr.tags.LatitudeTag
import com.vitorpamplona.quartz.experimental.roadstr.tags.LongitudeTag
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.TagArrayBuilder
import com.vitorpamplona.quartz.nip01Core.signers.EventTemplate
import com.vitorpamplona.quartz.nip01Core.signers.eventTemplate
import com.vitorpamplona.quartz.nip01Core.tags.geohash.GeoHash
import com.vitorpamplona.quartz.nip01Core.tags.geohash.GeoHashTag
import com.vitorpamplona.quartz.nip01Core.tags.geohash.geohashes
import com.vitorpamplona.quartz.nip01Core.tags.hashtags.HashtagTag
import com.vitorpamplona.quartz.nip31Alts.AltTag
import com.vitorpamplona.quartz.nip31Alts.alt
import com.vitorpamplona.quartz.nip40Expiration.ExpirationTag
import com.vitorpamplona.quartz.nip40Expiration.expiration
import com.vitorpamplona.quartz.utils.TimeUtils

/**
 * Roadstr road event report (kind 1315).
 *
 * A driver-published report of a notable road condition (police, accident,
 * hazard, …) at a specific location. See <https://github.com/jooray/roadstr>.
 *
 * Tags (canonical order):
 * - `t` — the [RoadEventType] code.
 * - `g` — geohash at precision 4, 5 and 6 (for multi-resolution spatial queries).
 * - `lat` / `lon` — coordinates as decimal strings with 7 fractional digits.
 * - `expiration` — NIP-40 relay garbage-collection window, always `created_at + 14 days`.
 * - `alt` — NIP-31 human-readable fallback.
 *
 * The relay-side `expiration` is a fixed 14-day window; clients should instead use
 * [effectiveExpirationAt] (derived from the event type's TTL) to decide when to
 * stop displaying a report. The `content` field holds an optional free-text comment.
 */
@Immutable
class RoadEventReportEvent(
    id: HexKey,
    pubKey: HexKey,
    createdAt: Long,
    tags: Array<Array<String>>,
    content: String,
    sig: HexKey,
) : Event(id, pubKey, createdAt, KIND, tags, content, sig) {
    /** The raw event-type code from the first `t` tag (may be a value outside [RoadEventType]). */
    fun roadEventTypeCode() = tags.firstNotNullOfOrNull(HashtagTag::parse)

    /** The parsed [RoadEventType], or null when the `t` tag is missing/unknown. */
    fun roadEventType() = RoadEventType.fromCode(roadEventTypeCode())

    /** All `g` geohash tags, finest-to-coarsest as published. */
    fun geohashes() = tags.geohashes()

    fun latitude() = tags.firstNotNullOfOrNull(LatitudeTag::parse)

    fun longitude() = tags.firstNotNullOfOrNull(LongitudeTag::parse)

    fun alt() = tags.firstNotNullOfOrNull(AltTag::parse)

    fun relayExpiration() = tags.firstNotNullOfOrNull(ExpirationTag::parse)

    /** Client-side effective expiry (`created_at + ` the type's TTL), or null if the type is unknown. */
    fun effectiveExpirationAt(): Long? = roadEventType()?.let { createdAt + it.effectiveTtlSeconds }

    /** Whether the report should no longer be displayed at [now], per its client-side TTL. */
    fun isEffectivelyExpired(now: Long = TimeUtils.now()): Boolean = effectiveExpirationAt()?.let { now > it } ?: false

    companion object {
        const val KIND = 1315
        const val ALT_PREFIX = "Roadstr: "

        /** Fixed relay-side NIP-40 window (14 days) for both report and confirmation events. */
        const val RELAY_TTL_SECONDS = 1_209_600L

        fun altDescription(type: RoadEventType) = "$ALT_PREFIX${type.code} report"

        fun build(
            type: RoadEventType,
            latitude: Double,
            longitude: Double,
            comment: String = "",
            createdAt: Long = TimeUtils.now(),
            initializer: TagArrayBuilder<RoadEventReportEvent>.() -> Unit = {},
        ): EventTemplate<RoadEventReportEvent> {
            val geohash6 = GeoHash.encode(latitude, longitude, 6).toString()
            return eventTemplate(KIND, comment, createdAt) {
                add(HashtagTag.assemble(type.code))
                add(GeoHashTag.assembleSingle(geohash6.substring(0, 4)))
                add(GeoHashTag.assembleSingle(geohash6.substring(0, 5)))
                add(GeoHashTag.assembleSingle(geohash6))
                add(LatitudeTag.assemble(latitude))
                add(LongitudeTag.assemble(longitude))
                expiration(createdAt + RELAY_TTL_SECONDS)
                alt(altDescription(type))
                initializer()
            }
        }
    }
}
