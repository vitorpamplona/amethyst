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
import com.vitorpamplona.quartz.experimental.roadstr.tags.RoadEventStatusTag
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.TagArrayBuilder
import com.vitorpamplona.quartz.nip01Core.signers.EventTemplate
import com.vitorpamplona.quartz.nip01Core.signers.eventTemplate
import com.vitorpamplona.quartz.nip01Core.tags.events.ETag
import com.vitorpamplona.quartz.nip01Core.tags.geohash.GeoHash
import com.vitorpamplona.quartz.nip01Core.tags.geohash.GeoHashTag
import com.vitorpamplona.quartz.nip01Core.tags.geohash.geohashes
import com.vitorpamplona.quartz.nip31Alts.AltTag
import com.vitorpamplona.quartz.nip31Alts.alt
import com.vitorpamplona.quartz.nip40Expiration.ExpirationTag
import com.vitorpamplona.quartz.nip40Expiration.expiration
import com.vitorpamplona.quartz.utils.TimeUtils

/**
 * Roadstr road event confirmation/denial (kind 1316).
 *
 * Validates or refutes a previously published [RoadEventReportEvent]. See
 * <https://github.com/jooray/roadstr>.
 *
 * Tags (canonical order):
 * - `e` — the referenced report event id.
 * - `status` — [RoadEventStatus.STILL_THERE] (confirm) or [RoadEventStatus.NO_LONGER_THERE] (deny).
 * - `g` / `lat` / `lon` — optional copy of the report's location, so confirmations
 *   are reachable through the same `#g` geohash queries as reports.
 * - `expiration` — NIP-40 relay garbage-collection window (`created_at + 14 days`).
 * - `alt` — NIP-31 human-readable fallback.
 */
@Immutable
class RoadEventConfirmationEvent(
    id: HexKey,
    pubKey: HexKey,
    createdAt: Long,
    tags: Array<Array<String>>,
    content: String,
    sig: HexKey,
) : Event(id, pubKey, createdAt, KIND, tags, content, sig) {
    /** The referenced report event id from the first `e` tag. */
    fun reportId() = tags.firstNotNullOfOrNull(ETag::parse)?.eventId

    fun status() = tags.firstNotNullOfOrNull(RoadEventStatusTag::parse)

    fun isConfirmation() = status() == RoadEventStatus.STILL_THERE

    fun isDenial() = status() == RoadEventStatus.NO_LONGER_THERE

    fun geohashes() = tags.geohashes()

    fun latitude() = tags.firstNotNullOfOrNull(LatitudeTag::parse)

    fun longitude() = tags.firstNotNullOfOrNull(LongitudeTag::parse)

    fun alt() = tags.firstNotNullOfOrNull(AltTag::parse)

    fun relayExpiration() = tags.firstNotNullOfOrNull(ExpirationTag::parse)

    companion object {
        const val KIND = 1316
        const val ALT_DESCRIPTION = "Roadstr: event confirmation"

        fun build(
            reportId: HexKey,
            status: RoadEventStatus,
            latitude: Double? = null,
            longitude: Double? = null,
            createdAt: Long = TimeUtils.now(),
            initializer: TagArrayBuilder<RoadEventConfirmationEvent>.() -> Unit = {},
        ): EventTemplate<RoadEventConfirmationEvent> =
            eventTemplate(KIND, "", createdAt) {
                add(ETag.assemble(reportId, null, null))
                add(RoadEventStatusTag.assemble(status))
                if (latitude != null && longitude != null) {
                    val geohash6 = GeoHash.encode(latitude, longitude, 6).toString()
                    add(GeoHashTag.assembleSingle(geohash6.substring(0, 4)))
                    add(GeoHashTag.assembleSingle(geohash6.substring(0, 5)))
                    add(GeoHashTag.assembleSingle(geohash6))
                    add(LatitudeTag.assemble(latitude))
                    add(LongitudeTag.assemble(longitude))
                }
                expiration(createdAt + RoadEventReportEvent.RELAY_TTL_SECONDS)
                alt(ALT_DESCRIPTION)
                initializer()
            }
    }
}
