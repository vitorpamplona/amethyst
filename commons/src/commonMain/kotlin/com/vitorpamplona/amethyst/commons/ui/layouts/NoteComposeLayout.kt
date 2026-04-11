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
package com.vitorpamplona.amethyst.commons.ui.layouts

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.Placeable
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.commons.ui.theme.Size55dp

/**
 * Pre-computed pixel dimensions for [NoteComposeLayout], cached via [remember]
 * so dp-to-px conversion only runs once per density change.
 */
@Immutable
private data class NoteLayoutPx(
    /** Author picture column width: 55dp */
    val authorWidth: Int,
    /** Horizontal gap between author column and content column: 10dp */
    val authorContentGap: Int,
    /** Vertical gap between author picture and relay badges: 5dp */
    val authorBadgeGap: Int,
    /** Vertical spacer between header rows and note content: 4dp */
    val contentSpacer: Int,
    /** Left padding for the content area: 12dp */
    val padStart: Int,
    /** Right padding for the content area: 12dp */
    val padEnd: Int,
    /** Top padding for the content area: 10dp */
    val padTop: Int,
)

/**
 * Custom single-pass layout for note feed items that replaces the nested
 * Row/Column structure with direct constraint calculation using known fixed
 * dimensions.
 *
 * @param modifier Applied to the Layout root.
 * @param addPadding Whether to add standard note padding (12dp sides, 10dp top).
 * @param showAuthorColumn Whether to reserve 55dp for the author picture column.
 * @param showSecondRow Whether to measure and place the second header row.
 * @param showContentSpacer Whether to add a 4dp spacer between header rows and content.
 * @param authorPicture Slot for the author's profile picture (55x55dp area).
 * @param relayBadges Slot for relay indicator icons below the author picture.
 * @param firstRow Slot for the primary header: author name, time, more options.
 * @param secondRow Slot for the secondary header: NIP-05, location, PoW, OTS.
 * @param noteContent Slot for the event-specific content.
 * @param reactionsRow Slot for the reaction buttons row.
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
                authorPicture, // 0
                relayBadges, // 1
                firstRow, // 2
                secondRow, // 3
                noteContent, // 4
                reactionsRow, // 5
            ),
        modifier = modifier,
    ) { allMeasurables, constraints ->
        val maxWidth = constraints.maxWidth

        val padStart = if (addPadding) px.padStart else 0
        val padEnd = if (addPadding) px.padEnd else 0
        val padTop = if (addPadding) px.padTop else 0

        val innerWidth = maxWidth - padStart - padEnd
        val authorW = if (showAuthorColumn) px.authorWidth else 0
        val gap = if (showAuthorColumn) px.authorContentGap else 0
        val contentWidth = (innerWidth - authorW - gap).coerceAtLeast(0)

        val authorConstraints = Constraints(maxWidth = px.authorWidth)
        val contentConstraints = Constraints(maxWidth = contentWidth)
        val fullWidthConstraints = Constraints(maxWidth = maxWidth)

        val authorPlaceable = allMeasurables[0].firstOrNull()?.measure(authorConstraints)
        val relayPlaceable = allMeasurables[1].firstOrNull()?.measure(authorConstraints)

        val firstRowPlaceable = allMeasurables[2].firstOrNull()?.measure(contentConstraints)
        val secondRowPlaceable =
            if (showSecondRow) allMeasurables[3].firstOrNull()?.measure(contentConstraints) else null

        val contentMeasurables = allMeasurables[4]
        val contentPlaceables = arrayOfNulls<Placeable>(contentMeasurables.size)
        var contentStackHeight = 0
        for (i in contentMeasurables.indices) {
            val placeable = contentMeasurables[i].measure(contentConstraints)
            contentPlaceables[i] = placeable
            contentStackHeight += placeable.height
        }

        val reactionsMeasurables = allMeasurables[5]
        val reactionsPlaceables = arrayOfNulls<Placeable>(reactionsMeasurables.size)
        var reactionsHeight = 0
        for (i in reactionsMeasurables.indices) {
            val placeable = reactionsMeasurables[i].measure(fullWidthConstraints)
            reactionsPlaceables[i] = placeable
            reactionsHeight += placeable.height
        }

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
            if (showAuthorColumn) {
                authorPlaceable?.placeRelative(padStart, padTop)
                relayPlaceable?.placeRelative(
                    padStart,
                    padTop + (authorPlaceable?.height ?: 0) + px.authorBadgeGap,
                )
            }

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

            var reactionsY = padTop + mainAreaHeight
            for (placeable in reactionsPlaceables) {
                placeable?.placeRelative(0, reactionsY)
                reactionsY += placeable?.height ?: 0
            }
        }
    }
}
