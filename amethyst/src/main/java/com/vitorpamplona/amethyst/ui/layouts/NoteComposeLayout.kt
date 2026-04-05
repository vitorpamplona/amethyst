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
package com.vitorpamplona.amethyst.ui.layouts

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.Placeable
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vitorpamplona.amethyst.ui.theme.Size55dp
import com.vitorpamplona.amethyst.ui.theme.ThemeComparisonColumn

private val ZeroConstraints = Constraints.fixed(0, 0)

@Immutable
private data class NoteLayoutPx(
    val authorWidth: Int,
    val authorContentGap: Int,
    val authorBadgeGap: Int,
    val contentSpacer: Int,
    val padStart: Int,
    val padEnd: Int,
    val padTop: Int,
)

@Composable
@Preview
private fun NoteComposeLayoutPreview() {
    ThemeComparisonColumn {
        NoteComposeLayoutPreviewCard()
    }
}

@Composable
private fun NoteComposeLayoutPreviewCard() {
    NoteComposeLayout(
        modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface),
        showSecondRow = true,
        authorPicture = {
            Box(
                Modifier
                    .size(55.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF9575CD)),
            )
        },
        relayBadges = {
            Text("R1  R2  R3", fontSize = 10.sp, color = Color.Gray)
        },
        firstRow = {
            Text(
                "Alice  @alice  2h  ...",
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        secondRow = {
            Text(
                "alice@nostr.com",
                fontSize = 12.sp,
                color = Color.Gray,
                maxLines = 1,
            )
        },
        noteContent = {
            Text(
                "This is a sample note content that demonstrates the custom " +
                    "NoteComposeLayout with all slots populated. The layout handles " +
                    "author picture, relay badges, header rows, content, and reactions.",
            )
        },
        reactionsRow = {
            Text("  Reply 3     Boost 5     Like 12     Zap 1.2k", fontSize = 12.sp, color = Color.Gray)
        },
    )
}

@Composable
@Preview
private fun NoteComposeLayoutBoostedPreview() {
    ThemeComparisonColumn {
        NoteComposeLayout(
            modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface),
            addPadding = false,
            showAuthorColumn = false,
            authorPicture = {},
            relayBadges = {},
            firstRow = {
                Text("Bob boosted  2h  ...")
            },
            secondRow = {},
            noteContent = {
                Text("Boosted note content without author column or padding.")
            },
            reactionsRow = {
                Text("  Reply     Boost     Like     Zap", fontSize = 12.sp, color = Color.Gray)
            },
        )
    }
}

/**
 * Custom zero-overhead layout for note items. Uses the multi-content [Layout]
 * overload to eliminate ALL intermediate wrapper nodes (Box/Column). Each slot's
 * composables become direct measurables measured with pre-computed constraints
 * in a single pass.
 *
 * Layout structure (when [showAuthorColumn] is true):
 * ```
 * ┌──────────────────────────────────────────────┐
 * │ padding (start=12, end=12, top=10)           │
 * │ ┌──────┐ 10dp ┌────────────────────────────┐ │
 * │ │author│  gap  │ firstRow                   │ │
 * │ │ 55dp │       │ secondRow (optional)       │ │
 * │ │      │       │ 4dp spacer (optional)      │ │
 * │ ├──────┤ 5dp   │ noteContent                │ │
 * │ │relay │  gap  │                            │ │
 * │ │badges│       │                            │ │
 * │ └──────┘       └────────────────────────────┘ │
 * ├───────────────────────────────────────────────┤
 * │ reactionsRow (full width, no side padding)    │
 * └───────────────────────────────────────────────┘
 * ```
 *
 * Performance characteristics:
 * - Zero intermediate layout nodes (no Box/Column wrappers)
 * - Pre-computed pixel dimensions cached across recompositions
 * - Single measure pass with known constraints for all 6 slots
 * - Multi-child slots (noteContent, reactionsRow) stacked inline
 * - Hidden slots skip measurement entirely (empty measurable lists)
 *
 * @param addPadding Whether to add standard note padding (12dp sides, 10dp top)
 * @param showAuthorColumn Whether to reserve space for the author picture column
 * @param showSecondRow Whether to measure and place the second info row
 * @param showContentSpacer Whether to add a 4dp spacer between header rows and content
 */
@Composable
fun NoteComposeLayout(
    modifier: Modifier = Modifier,
    addPadding: Boolean = true,
    showAuthorColumn: Boolean = true,
    showSecondRow: Boolean = false,
    showContentSpacer: Boolean = true,
    authorPicture: @Composable () -> Unit,
    relayBadges: @Composable () -> Unit,
    firstRow: @Composable () -> Unit,
    secondRow: @Composable () -> Unit,
    noteContent: @Composable () -> Unit,
    reactionsRow: @Composable () -> Unit,
) {
    val density = LocalDensity.current
    val px =
        remember(density) {
            with(density) {
                NoteLayoutPx(
                    authorWidth = Size55dp.roundToPx(),
                    authorContentGap = 10.dp.roundToPx(),
                    authorBadgeGap = 5.dp.roundToPx(),
                    contentSpacer = 4.dp.roundToPx(),
                    padStart = 12.dp.roundToPx(),
                    padEnd = 12.dp.roundToPx(),
                    padTop = 10.dp.roundToPx(),
                )
            }
        }

    Layout(
        contents =
            listOf(
                authorPicture,
                relayBadges,
                firstRow,
                secondRow,
                noteContent,
                reactionsRow,
            ),
        modifier = modifier,
    ) { allMeasurables, constraints ->
        val maxWidth = constraints.maxWidth

        // Compute padding offsets
        val padStart = if (addPadding) px.padStart else 0
        val padEnd = if (addPadding) px.padEnd else 0
        val padTop = if (addPadding) px.padTop else 0

        // Compute column widths from known dimensions
        val innerWidth = maxWidth - padStart - padEnd
        val authorW = if (showAuthorColumn) px.authorWidth else 0
        val gap = if (showAuthorColumn) px.authorContentGap else 0
        val contentWidth = (innerWidth - authorW - gap).coerceAtLeast(0)

        // Pre-compute constraint objects once
        val authorConstraints = Constraints(maxWidth = px.authorWidth)
        val contentConstraints = Constraints(maxWidth = contentWidth)
        val fullWidthConstraints = Constraints(maxWidth = maxWidth)

        // Measure author column (single child per slot, skip if empty)
        val authorPlaceable = allMeasurables[0].firstOrNull()?.measure(authorConstraints)
        val relayPlaceable = allMeasurables[1].firstOrNull()?.measure(authorConstraints)

        // Measure header rows
        val firstRowPlaceable = allMeasurables[2].firstOrNull()?.measure(contentConstraints)
        val secondRowPlaceable =
            if (showSecondRow) allMeasurables[3].firstOrNull()?.measure(contentConstraints) else null

        // Measure note content children directly (no Column wrapper)
        val contentMeasurables = allMeasurables[4]
        val contentPlaceables = arrayOfNulls<Placeable>(contentMeasurables.size)
        var contentStackHeight = 0
        for (i in contentMeasurables.indices) {
            val placeable = contentMeasurables[i].measure(contentConstraints)
            contentPlaceables[i] = placeable
            contentStackHeight += placeable.height
        }

        // Measure reactions row children directly (no Column wrapper)
        val reactionsMeasurables = allMeasurables[5]
        val reactionsPlaceables = arrayOfNulls<Placeable>(reactionsMeasurables.size)
        var reactionsHeight = 0
        for (i in reactionsMeasurables.indices) {
            val placeable = reactionsMeasurables[i].measure(fullWidthConstraints)
            reactionsPlaceables[i] = placeable
            reactionsHeight += placeable.height
        }

        // Calculate section heights
        val spacer = if (showContentSpacer) px.contentSpacer else 0

        val contentColumnHeight =
            (firstRowPlaceable?.height ?: 0) +
                (secondRowPlaceable?.height ?: 0) +
                spacer +
                contentStackHeight

        val authorColumnHeight =
            if (showAuthorColumn && authorPlaceable != null) {
                authorPlaceable.height + px.authorBadgeGap + (relayPlaceable?.height ?: 0)
            } else {
                0
            }

        val mainAreaHeight = maxOf(authorColumnHeight, contentColumnHeight)
        val totalHeight = padTop + mainAreaHeight + reactionsHeight

        layout(maxWidth, totalHeight.coerceAtLeast(constraints.minHeight)) {
            // Place author column
            if (showAuthorColumn) {
                authorPlaceable?.placeRelative(padStart, padTop)
                relayPlaceable?.placeRelative(
                    padStart,
                    padTop + (authorPlaceable?.height ?: 0) + px.authorBadgeGap,
                )
            }

            // Place content column
            val contentX = padStart + authorW + gap
            var y = padTop

            firstRowPlaceable?.placeRelative(contentX, y)
            y += firstRowPlaceable?.height ?: 0

            if (showSecondRow) {
                secondRowPlaceable?.placeRelative(contentX, y)
                y += secondRowPlaceable?.height ?: 0
            }

            y += spacer

            for (placeable in contentPlaceables) {
                placeable?.placeRelative(contentX, y)
                y += placeable?.height ?: 0
            }

            // Place reactions row at full width, below main area
            var reactionsY = padTop + mainAreaHeight
            for (placeable in reactionsPlaceables) {
                placeable?.placeRelative(0, reactionsY)
                reactionsY += placeable?.height ?: 0
            }
        }
    }
}
