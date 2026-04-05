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

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.ui.theme.Size55dp

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

/**
 * Custom single-pass layout for note items that replaces nested Row/Column
 * measurements with direct constraint calculation using known fixed dimensions.
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
 * Performance benefits over nested Row/Column:
 * - Eliminates Row's two-pass measurement (measure author, then content with remaining width)
 * - Uses pre-computed pixel dimensions for author column width (55dp) and gaps
 * - Reduces content Column children from ~6 to ~3
 * - Single measure pass positions all sections with known constraints
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
        content = {
            // Slot 0: Author picture (55x55 area)
            Box { authorPicture() }
            // Slot 1: Relay badges (55dp wide, below author)
            Box { relayBadges() }
            // Slot 2: First row (author name, time, options)
            Box { firstRow() }
            // Slot 3: Second row (NIP05, location, PoW)
            Box { secondRow() }
            // Slot 4: Note content (event-specific + zap splits + approval)
            Column(Modifier.fillMaxWidth()) { noteContent() }
            // Slot 5: Reactions row (full width)
            Column(Modifier.fillMaxWidth()) { reactionsRow() }
        },
        modifier = modifier,
    ) { measurables, constraints ->
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

        // Build constraints for each section
        val authorConstraints =
            if (showAuthorColumn) Constraints(maxWidth = px.authorWidth) else ZeroConstraints
        val contentConstraints = Constraints(maxWidth = contentWidth)

        // Measure all children in a single pass with pre-computed constraints
        val authorPlaceable = measurables[0].measure(authorConstraints)
        val relayPlaceable = measurables[1].measure(authorConstraints)
        val firstRowPlaceable = measurables[2].measure(contentConstraints)
        val secondRowPlaceable =
            measurables[3].measure(
                if (showSecondRow) contentConstraints else ZeroConstraints,
            )
        val contentPlaceable = measurables[4].measure(contentConstraints)
        val reactionsPlaceable =
            measurables[5].measure(Constraints(maxWidth = maxWidth))

        // Calculate section heights
        val spacer = if (showContentSpacer) px.contentSpacer else 0

        val contentColumnHeight =
            firstRowPlaceable.height +
                (if (showSecondRow) secondRowPlaceable.height else 0) +
                spacer +
                contentPlaceable.height

        val authorColumnHeight =
            if (showAuthorColumn) {
                authorPlaceable.height + px.authorBadgeGap + relayPlaceable.height
            } else {
                0
            }

        val mainAreaHeight = maxOf(authorColumnHeight, contentColumnHeight)
        val totalHeight = padTop + mainAreaHeight + reactionsPlaceable.height

        layout(maxWidth, totalHeight.coerceAtLeast(constraints.minHeight)) {
            // Place author column (inside padding)
            if (showAuthorColumn) {
                authorPlaceable.placeRelative(padStart, padTop)
                relayPlaceable.placeRelative(
                    padStart,
                    padTop + authorPlaceable.height + px.authorBadgeGap,
                )
            }

            // Place content column (to the right of author, inside padding)
            val contentX = padStart + authorW + gap
            var y = padTop

            firstRowPlaceable.placeRelative(contentX, y)
            y += firstRowPlaceable.height

            if (showSecondRow) {
                secondRowPlaceable.placeRelative(contentX, y)
                y += secondRowPlaceable.height
            }

            y += spacer

            contentPlaceable.placeRelative(contentX, y)

            // Place reactions row at full width, below main area
            reactionsPlaceable.placeRelative(0, padTop + mainAreaHeight)
        }
    }
}
