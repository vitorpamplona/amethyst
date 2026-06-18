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
package com.vitorpamplona.amethyst.ui.note.types

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.ui.note.creators.location.LocationPreviewMap
import com.vitorpamplona.amethyst.ui.theme.placeholderText
import com.vitorpamplona.amethyst.ui.theme.replyModifier
import com.vitorpamplona.quartz.experimental.roadstr.confirmation.RoadEventConfirmationEvent
import com.vitorpamplona.quartz.experimental.roadstr.confirmation.tags.RoadEventStatus
import com.vitorpamplona.quartz.experimental.roadstr.report.RoadEventReportEvent
import com.vitorpamplona.quartz.experimental.roadstr.report.tags.RoadEventType
import com.vitorpamplona.quartz.nip01Core.tags.geohash.toGeoHash
import com.vitorpamplona.quartz.utils.TimeUtils

/**
 * Emoji marker for a road event category. Mirrors the icon set of the roadstr
 * reference clients (<https://github.com/jooray/roadstr>) so a report reads the
 * same across implementations; keeps the card icon-light and locale-independent.
 */
private fun RoadEventType.emoji(): String =
    when (this) {
        RoadEventType.POLICE -> "👮"
        RoadEventType.SPEED_CAMERA -> "📷"
        RoadEventType.TRAFFIC_JAM -> "🚗"
        RoadEventType.ACCIDENT -> "💥"
        RoadEventType.ROAD_CLOSURE -> "🚫"
        RoadEventType.CONSTRUCTION -> "🚧"
        RoadEventType.HAZARD -> "⚠️"
        RoadEventType.ROAD_CONDITION -> "🛣️"
        RoadEventType.POTHOLE -> "🕳️"
        RoadEventType.FOG -> "🌫️"
        RoadEventType.ICE -> "🧊"
        RoadEventType.ANIMAL -> "🦌"
        RoadEventType.OTHER -> "ℹ️"
    }

/**
 * Per-category pin color. Uses the exact palette from the roadstr reference
 * clients (<https://github.com/jooray/roadstr>) so a pin's color carries the
 * same meaning across the network.
 */
private fun RoadEventType.color(): Color =
    when (this) {
        RoadEventType.POLICE -> Color(0xFF0000FF)
        RoadEventType.SPEED_CAMERA -> Color(0xFF800080)
        RoadEventType.TRAFFIC_JAM -> Color(0xFFFF8C00)
        RoadEventType.ACCIDENT -> Color(0xFFFF0000)
        RoadEventType.ROAD_CLOSURE -> Color(0xFF8B0000)
        RoadEventType.CONSTRUCTION -> Color(0xFFFFD700)
        RoadEventType.HAZARD -> Color(0xFFFF4500)
        RoadEventType.ROAD_CONDITION -> Color(0xFF4682B4)
        RoadEventType.POTHOLE -> Color(0xFF795548)
        RoadEventType.FOG -> Color(0xFF9E9E9E)
        RoadEventType.ICE -> Color(0xFF00CED1)
        RoadEventType.ANIMAL -> Color(0xFF4CAF50)
        RoadEventType.OTHER -> Color(0xFF808080)
    }

private fun RoadEventType.labelRes(): Int =
    when (this) {
        RoadEventType.POLICE -> R.string.road_event_police
        RoadEventType.SPEED_CAMERA -> R.string.road_event_speed_camera
        RoadEventType.TRAFFIC_JAM -> R.string.road_event_traffic_jam
        RoadEventType.ACCIDENT -> R.string.road_event_accident
        RoadEventType.ROAD_CLOSURE -> R.string.road_event_road_closure
        RoadEventType.CONSTRUCTION -> R.string.road_event_construction
        RoadEventType.HAZARD -> R.string.road_event_hazard
        RoadEventType.ROAD_CONDITION -> R.string.road_event_road_condition
        RoadEventType.POTHOLE -> R.string.road_event_pothole
        RoadEventType.FOG -> R.string.road_event_fog
        RoadEventType.ICE -> R.string.road_event_ice
        RoadEventType.ANIMAL -> R.string.road_event_animal
        RoadEventType.OTHER -> R.string.road_event_other
    }

/**
 * Self-contained card for a Roadstr road event report (kind 1315).
 *
 * A map hero pinned at the event's coordinates, with a floating category pill
 * (colored per [RoadEventType.color]) and the optional free-text comment below.
 * Identical in the feed and the opened thread view, so it takes no `makeItShort`
 * flag — mirroring [RenderBirdex].
 */
@Composable
fun RenderRoadEventReport(baseNote: Note) {
    val noteEvent = baseNote.event as? RoadEventReportEvent ?: return

    val type = remember(noteEvent) { noteEvent.roadEventType() }
    val comment = remember(noteEvent) { noteEvent.content.trim() }
    val point = remember(noteEvent) { noteEvent.roadEventPoint() }
    val freshness = remember(noteEvent) { noteEvent.freshnessAlpha() }

    val color = type?.color() ?: MaterialTheme.colorScheme.outline
    val emoji = type?.emoji() ?: "📍"
    val label = if (type != null) stringResource(type.labelRes()) else stringResource(R.string.road_event_unknown)

    RoadEventCard(
        chipColor = color,
        chipEmoji = emoji,
        chipLabel = label,
        point = point,
        pinAlpha = freshness,
        comment = comment,
    )
}

/**
 * Self-contained card for a Roadstr confirmation/denial (kind 1316).
 *
 * Same hero/pill layout as a report, with a green ✅ "Still there" or red ❌
 * "No longer there" pill and a one-line description of the action.
 */
@Composable
fun RenderRoadEventConfirmation(baseNote: Note) {
    val noteEvent = baseNote.event as? RoadEventConfirmationEvent ?: return

    val status = remember(noteEvent) { noteEvent.status() }
    val point = remember(noteEvent) { noteEvent.roadEventPoint() }

    val denied = status == RoadEventStatus.NO_LONGER_THERE
    val emoji = if (denied) "❌" else "✅"
    val color = if (denied) Color(0xFFF44336) else Color(0xFF4CAF50)

    RoadEventCard(
        chipColor = color,
        chipEmoji = emoji,
        chipLabel = stringResource(if (denied) R.string.road_event_denied else R.string.road_event_confirmed),
        point = point,
        subtitle = stringResource(if (denied) R.string.road_event_denies_report else R.string.road_event_confirms_report),
    )
}

/**
 * Shared road event card chrome: a full-bleed [LocationPreviewMap] hero with a
 * floating [CategoryPill] overlaid on its top-start, and an optional
 * [subtitle] + [comment] block below. When [point] is null the pill stands on
 * its own so the card still reads.
 */
@Composable
private fun RoadEventCard(
    chipColor: Color,
    chipEmoji: String,
    chipLabel: String,
    point: Pair<Double, Double>?,
    pinAlpha: Float = 1f,
    comment: String = "",
    subtitle: String? = null,
) {
    Column(MaterialTheme.colorScheme.replyModifier) {
        if (point != null) {
            Box(Modifier.fillMaxWidth()) {
                LocationPreviewMap(
                    latitude = point.first,
                    longitude = point.second,
                    pinColor = chipColor,
                    pinEmoji = chipEmoji,
                    pinAlpha = pinAlpha,
                )
                CategoryPill(
                    color = chipColor,
                    emoji = chipEmoji,
                    label = chipLabel,
                    modifier = Modifier.align(Alignment.TopStart).padding(10.dp),
                )
            }
        } else {
            CategoryPill(
                color = chipColor,
                emoji = chipEmoji,
                label = chipLabel,
                modifier = Modifier.padding(12.dp),
            )
        }

        if (subtitle != null || comment.isNotEmpty()) {
            Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.placeholderText,
                    )
                }
                if (comment.isNotEmpty()) {
                    if (subtitle != null) Spacer(Modifier.height(4.dp))
                    Text(
                        text = comment,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 4,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

/** A floating rounded pill: [emoji] + [label] on a [color] background, text auto-contrasted. */
@Composable
private fun CategoryPill(
    color: Color,
    emoji: String,
    label: String,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        color = color,
        contentColor = if (color.luminance() > 0.55f) Color.Black else Color.White,
        shape = RoundedCornerShape(50),
        shadowElevation = 3.dp,
    ) {
        Text(
            text = "$emoji  $label",
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

/**
 * Best available coordinate for the card map: the event's explicit `lat`/`lon`
 * tags when present, otherwise the center of the finest published geohash.
 * Returns null when the event carries no location at all.
 */
private fun RoadEventReportEvent.roadEventPoint(): Pair<Double, Double>? = resolveRoadEventPoint(latitude(), longitude(), geohashes())

private fun RoadEventConfirmationEvent.roadEventPoint(): Pair<Double, Double>? = resolveRoadEventPoint(latitude(), longitude(), geohashes())

private fun resolveRoadEventPoint(
    latitude: Double?,
    longitude: Double?,
    geohashes: List<String>,
): Pair<Double, Double>? {
    if (latitude != null && longitude != null) return latitude to longitude

    val finest = geohashes.maxByOrNull { it.length } ?: return null
    val decoded = runCatching { finest.toGeoHash() }.getOrNull() ?: return null
    return decoded.centerLat to decoded.centerLon
}

/**
 * Marker opacity by freshness, matching the roadstr clients: full while the
 * report has plenty of life left, dimmed to 0.6 once under 25% of its effective
 * TTL remains, and faded to 0.4 once effectively expired. Returns 1f when the
 * type (and therefore the TTL) is unknown.
 */
private fun RoadEventReportEvent.freshnessAlpha(now: Long = TimeUtils.now()): Float {
    val expiryAt = effectiveExpirationAt() ?: return 1f
    val total = expiryAt - createdAt
    if (total <= 0L) return 1f

    val fraction = (expiryAt - now).toFloat() / total
    return when {
        fraction <= 0f -> 0.4f
        fraction < 0.25f -> 0.6f
        else -> 1f
    }
}
