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

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.ui.components.ClickableUrl
import com.vitorpamplona.amethyst.ui.theme.placeholderText
import com.vitorpamplona.amethyst.ui.theme.replyModifier
import com.vitorpamplona.quartz.experimental.birdstar.BirdDetectionEvent
import com.vitorpamplona.quartz.experimental.birdstar.BirdexEvent

/** How many species names to list before collapsing into a "+N more" suffix. */
private const val SPECIES_PREVIEW_LIMIT = 6

/** Bird emoji prefix shared by both Birdstar card titles. */
private const val BIRD_PREFIX = "\uD83D\uDC26 "

/**
 * Minimal, fixed-size summary card for a Birdstar "Birdex" (kind 12473).
 *
 * The event has no body and no images, only a species list. To keep the card
 * bounded regardless of how many species a Birdex holds, we show the count and a
 * short preview of scientific names with a "+N more" suffix — no images, no
 * expansion, no network calls. The card is identical in the feed and the opened
 * view, so it takes no makeItShort flag.
 */
@Composable
fun RenderBirdex(baseNote: Note) {
    val noteEvent = baseNote.event as? BirdexEvent ?: return

    val names = remember(noteEvent) { noteEvent.speciesNames() }
    val preview = remember(names) { names.take(SPECIES_PREVIEW_LIMIT) }
    val remaining = names.size - preview.size
    val joined = remember(preview) { preview.joinToString(", ") }

    Column(MaterialTheme.colorScheme.replyModifier.padding(10.dp)) {
        Text(
            text = BIRD_PREFIX + pluralStringResource(R.plurals.birdex_species_count, names.size, names.size),
            style = MaterialTheme.typography.titleMedium,
        )

        if (preview.isNotEmpty()) {
            Spacer(Modifier.height(6.dp))
            Text(
                text =
                    if (remaining > 0) {
                        pluralStringResource(R.plurals.birdex_species_preview_more, remaining, joined, remaining)
                    } else {
                        joined
                    },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.placeholderText,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * Minimal card for a single Birdstar bird detection (kind 2473).
 *
 * The event has no body and no images. The title is the common name parsed from
 * the publisher's `alt` tag (a generic label when absent), and the scientific
 * name renders as an italic link to the Wikidata species entry from the `i` tag
 * when one is present. The `g` geohash is surfaced by the generic note-location
 * UI, not here.
 */
@Composable
fun RenderBirdDetection(baseNote: Note) {
    val noteEvent = baseNote.event as? BirdDetectionEvent ?: return

    val detection =
        remember(noteEvent) {
            BirdDetectionInfo(
                commonName = noteEvent.commonName(),
                species = noteEvent.speciesName(),
                reference = noteEvent.speciesReference(),
            )
        }

    Column(MaterialTheme.colorScheme.replyModifier.padding(10.dp)) {
        Text(
            text = BIRD_PREFIX + (detection.commonName ?: stringResource(R.string.bird_detection_title)),
            style = MaterialTheme.typography.titleMedium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )

        if (detection.species != null) {
            Spacer(Modifier.height(6.dp))
            if (detection.reference != null) {
                val typography = MaterialTheme.typography
                val speciesStyle = remember(typography) { typography.bodyMedium.copy(fontStyle = FontStyle.Italic) }
                ClickableUrl(
                    urlText = detection.species,
                    url = detection.reference,
                    style = speciesStyle,
                )
            } else {
                Text(
                    text = detection.species,
                    style = MaterialTheme.typography.bodyMedium,
                    fontStyle = FontStyle.Italic,
                    color = MaterialTheme.colorScheme.placeholderText,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** Tag values parsed once per event for the detection card. */
private class BirdDetectionInfo(
    val commonName: String?,
    val species: String?,
    val reference: String?,
)
