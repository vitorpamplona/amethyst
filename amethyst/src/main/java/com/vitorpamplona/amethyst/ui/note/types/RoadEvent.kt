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

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.ui.note.creators.location.LoadCityName
import com.vitorpamplona.amethyst.ui.theme.placeholderText
import com.vitorpamplona.amethyst.ui.theme.replyModifier
import com.vitorpamplona.quartz.experimental.roadstr.RoadEventConfirmationEvent
import com.vitorpamplona.quartz.experimental.roadstr.RoadEventReportEvent
import com.vitorpamplona.quartz.experimental.roadstr.RoadEventStatus
import com.vitorpamplona.quartz.experimental.roadstr.RoadEventType

/** Emoji marker for a road event category — keeps the card icon-light and locale-independent. */
private fun RoadEventType.emoji(): String =
    when (this) {
        RoadEventType.POLICE -> "👮"
        RoadEventType.SPEED_CAMERA -> "📷"
        RoadEventType.TRAFFIC_JAM -> "🚗"
        RoadEventType.ACCIDENT -> "💥"
        RoadEventType.ROAD_CLOSURE -> "⛔"
        RoadEventType.CONSTRUCTION -> "🚧"
        RoadEventType.HAZARD -> "⚠️"
        RoadEventType.ROAD_CONDITION -> "🛣️"
        RoadEventType.POTHOLE -> "🕳️"
        RoadEventType.FOG -> "🌫️"
        RoadEventType.ICE -> "🧊"
        RoadEventType.ANIMAL -> "🦌"
        RoadEventType.OTHER -> "📍"
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
 * Shows the category (emoji + localized label), the optional free-text comment,
 * and the location resolved from the finest published geohash. Identical in the
 * feed and the opened thread view, so it takes no `makeItShort` flag — mirroring
 * [RenderBirdex].
 */
@Composable
fun RenderRoadEventReport(baseNote: Note) {
    val noteEvent = baseNote.event as? RoadEventReportEvent ?: return

    val type = remember(noteEvent) { noteEvent.roadEventType() }
    val comment = remember(noteEvent) { noteEvent.content.trim() }
    val geohash = remember(noteEvent) { noteEvent.geohashes().maxByOrNull { it.length } }

    Column(MaterialTheme.colorScheme.replyModifier.padding(10.dp)) {
        val title =
            if (type != null) {
                "${type.emoji()} ${stringResource(type.labelRes())}"
            } else {
                stringResource(R.string.road_event_unknown)
            }

        Text(text = title, style = MaterialTheme.typography.titleMedium)

        if (comment.isNotEmpty()) {
            Spacer(Modifier.height(6.dp))
            Text(
                text = comment,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis,
            )
        }

        if (geohash != null) {
            Spacer(Modifier.height(6.dp))
            RoadEventLocation(geohash)
        }
    }
}

/**
 * Self-contained card for a Roadstr confirmation/denial (kind 1316).
 *
 * Shows whether the referenced report was confirmed (still there) or denied
 * (no longer there), plus a one-line description of the action.
 */
@Composable
fun RenderRoadEventConfirmation(baseNote: Note) {
    val noteEvent = baseNote.event as? RoadEventConfirmationEvent ?: return

    val status = remember(noteEvent) { noteEvent.status() }
    val geohash = remember(noteEvent) { noteEvent.geohashes().maxByOrNull { it.length } }

    Column(MaterialTheme.colorScheme.replyModifier.padding(10.dp)) {
        val emoji = if (status == RoadEventStatus.NO_LONGER_THERE) "❌" else "✅"
        val titleRes =
            if (status == RoadEventStatus.NO_LONGER_THERE) {
                R.string.road_event_denied
            } else {
                R.string.road_event_confirmed
            }

        Text(text = "$emoji ${stringResource(titleRes)}", style = MaterialTheme.typography.titleMedium)

        Spacer(Modifier.height(4.dp))
        Text(
            text =
                stringResource(
                    if (status == RoadEventStatus.NO_LONGER_THERE) {
                        R.string.road_event_denies_report
                    } else {
                        R.string.road_event_confirms_report
                    },
                ),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.placeholderText,
        )

        if (geohash != null) {
            Spacer(Modifier.height(6.dp))
            RoadEventLocation(geohash)
        }
    }
}

/** A single "📍 City" line that reverse-geocodes [geohash], falling back to the raw geohash. */
@Composable
private fun RoadEventLocation(geohash: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(text = "📍", style = MaterialTheme.typography.bodyMedium)
        LoadCityName(geohashStr = geohash) { cityName ->
            Text(
                text = cityName,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.placeholderText,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
