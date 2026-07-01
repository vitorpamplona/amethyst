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
package com.vitorpamplona.quartz.experimental.roadstr.report

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.experimental.roadstr.report.tags.RoadEventType
import com.vitorpamplona.quartz.experimental.roadstr.tags.coordinates
import com.vitorpamplona.quartz.experimental.roadstr.tags.latitude
import com.vitorpamplona.quartz.experimental.roadstr.tags.longitude
import com.vitorpamplona.quartz.experimental.roadstr.tags.roadGeohashes
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.TagArrayBuilder
import com.vitorpamplona.quartz.nip01Core.signers.eventTemplate
import com.vitorpamplona.quartz.nip01Core.tags.geohash.geohashes
import com.vitorpamplona.quartz.nip31Alts.AltTag
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
    /** The parsed [RoadEventType], or null when the `t` tag is missing/unknown. */
    fun roadEventType() = tags.roadEventType()

    /** The raw event-type code from the first `t` tag (may be outside [RoadEventType]). */
    fun roadEventTypeCode() = tags.roadEventTypeCode()

    /** All `g` geohash tags, coarse-to-fine as published. */
    fun geohashes() = tags.geohashes()

    fun latitude() = tags.latitude()

    fun longitude() = tags.longitude()

    fun alt() = tags.firstNotNullOfOrNull(AltTag::parse)

    fun relayExpiration() = tags.expiration()

    /** Client-side effective expiry (`created_at + ` the type's TTL), or null if the type is unknown. */
    fun effectiveExpirationAt(): Long? = roadEventType()?.let { createdAt + it.effectiveTtlSeconds }

    /** Whether the report should no longer be displayed at [now], per its client-side TTL. */
    fun isEffectivelyExpired(now: Long = TimeUtils.now()): Boolean = effectiveExpirationAt()?.let { now > it } ?: false

    companion object {
        const val KIND = 1315

        /** Fixed relay-side NIP-40 window (14 days) for both report and confirmation events. */
        const val RELAY_TTL_SECONDS = 1_209_600L

        fun build(
            type: RoadEventType,
            latitude: Double,
            longitude: Double,
            comment: String = "",
            createdAt: Long = TimeUtils.now(),
            initializer: TagArrayBuilder<RoadEventReportEvent>.() -> Unit = {},
        ) = eventTemplate(KIND, comment, createdAt) {
            roadEventType(type)
            roadGeohashes(latitude, longitude)
            coordinates(latitude, longitude)
            expiration(createdAt + RELAY_TTL_SECONDS)
            initializer()
        }
    }
}
