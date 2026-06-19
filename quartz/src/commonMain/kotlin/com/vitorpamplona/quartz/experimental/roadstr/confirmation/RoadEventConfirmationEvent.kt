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
package com.vitorpamplona.quartz.experimental.roadstr.confirmation

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.experimental.roadstr.confirmation.tags.RoadEventStatus
import com.vitorpamplona.quartz.experimental.roadstr.confirmation.tags.RoadReportTag
import com.vitorpamplona.quartz.experimental.roadstr.report.RoadEventReportEvent
import com.vitorpamplona.quartz.experimental.roadstr.tags.coordinates
import com.vitorpamplona.quartz.experimental.roadstr.tags.latitude
import com.vitorpamplona.quartz.experimental.roadstr.tags.longitude
import com.vitorpamplona.quartz.experimental.roadstr.tags.roadGeohashes
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.TagArrayBuilder
import com.vitorpamplona.quartz.nip01Core.hints.EventHintBundle
import com.vitorpamplona.quartz.nip01Core.hints.EventHintProvider
import com.vitorpamplona.quartz.nip01Core.signers.eventTemplate
import com.vitorpamplona.quartz.nip01Core.tags.geohash.geohashes
import com.vitorpamplona.quartz.nip31Alts.AltTag
import com.vitorpamplona.quartz.nip31Alts.alt
import com.vitorpamplona.quartz.nip40Expiration.expiration
import com.vitorpamplona.quartz.utils.TimeUtils

/**
 * Roadstr road event confirmation/denial (kind 1316).
 *
 * Validates or refutes a previously published [RoadEventReportEvent]. See
 * <https://github.com/jooray/roadstr>.
 *
 * Tags (canonical order):
 * - `e` — the referenced report event id ([RoadReportTag]).
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
) : Event(id, pubKey, createdAt, KIND, tags, content, sig),
    EventHintProvider {
    override fun eventHints() = tags.mapNotNull(RoadReportTag::parseAsHint)

    override fun linkedEventIds() = tags.mapNotNull(RoadReportTag::parseId)

    /** The referenced report event id from the first `e` tag. */
    fun reportId() = tags.roadReport()?.eventId

    fun status() = tags.roadEventStatus()

    fun isConfirmation() = status() == RoadEventStatus.STILL_THERE

    fun isDenial() = status() == RoadEventStatus.NO_LONGER_THERE

    fun geohashes() = tags.geohashes()

    fun latitude() = tags.latitude()

    fun longitude() = tags.longitude()

    fun alt() = tags.firstNotNullOfOrNull(AltTag::parse)

    fun relayExpiration() = tags.expiration()

    companion object {
        const val KIND = 1316

        /** NIP-31 fallback for a confirmation (`still_there`), per the roadstr spec. */
        const val ALT_CONFIRMED = "Roadstr: event confirmed"

        /** NIP-31 fallback for a denial (`no_longer_there`), per the roadstr spec. */
        const val ALT_DENIED = "Roadstr: event denied"

        fun altDescription(status: RoadEventStatus) = if (status == RoadEventStatus.NO_LONGER_THERE) ALT_DENIED else ALT_CONFIRMED

        fun build(
            reportId: HexKey,
            status: RoadEventStatus,
            latitude: Double? = null,
            longitude: Double? = null,
            createdAt: Long = TimeUtils.now(),
            initializer: TagArrayBuilder<RoadEventConfirmationEvent>.() -> Unit = {},
        ) = eventTemplate(KIND, "", createdAt) {
            report(reportId)
            status(status)
            if (latitude != null && longitude != null) {
                roadGeohashes(latitude, longitude)
                coordinates(latitude, longitude)
            }
            expiration(createdAt + RoadEventReportEvent.RELAY_TTL_SECONDS)
            alt(altDescription(status))
            initializer()
        }

        fun build(
            report: EventHintBundle<RoadEventReportEvent>,
            status: RoadEventStatus,
            latitude: Double? = null,
            longitude: Double? = null,
            createdAt: Long = TimeUtils.now(),
            initializer: TagArrayBuilder<RoadEventConfirmationEvent>.() -> Unit = {},
        ) = build(report.event.id, status, latitude, longitude, createdAt) {
            report(report)
            initializer()
        }
    }
}
