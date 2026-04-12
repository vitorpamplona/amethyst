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
 * Custom single-pass layout for note feed items that replaces the nested
 * Row/Column structure with direct constraint calculation using known fixed
 * dimensions.
 *
 * ## Layout structure
 *
 * When [showAuthorColumn] is true (normal notes):
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
 * When [showAuthorColumn] is false (boosted/quoted notes):
 * ```
 * ┌───────────────────────────────────────────────┐
 * │ firstRow (full width)                         │
 * │ noteContent                                   │
 * ├───────────────────────────────────────────────┤
 * │ reactionsRow (full width)                     │
 * └───────────────────────────────────────────────┘
 * ```
 *
 * ## Design choices
 *
 * **Multi-content Layout instead of layoutId**: Uses [Layout] with `contents: List`
 * so each slot's composables become direct measurables without Box/Column wrappers.
 * This eliminates 6 intermediate layout nodes per note compared to the layoutId
 * approach. Empty slot lambdas produce empty measurable lists, which are skipped
 * entirely (no [Constraints.fixed] zero-size measurement needed).
 *
 * **Padding in measure policy instead of Modifier.padding()**: The reactions row
 * spans the full parent width without side padding, while the author + content area
 * has 12dp horizontal padding. A single Modifier.padding() can't express different
 * padding for different children, so padding offsets are applied during placement.
 *
 * **placeRelative instead of place**: Ensures correct positioning in RTL layouts
 * by automatically mirroring x coordinates.
 *
 * **arrayOfNulls instead of map**: For multi-child slots (noteContent, reactionsRow),
 * uses pre-sized arrays with indexed iteration to avoid List allocation overhead in
 * the measure pass hot path.
 *
 * ## Slot ordering contract
 *
 * The `contents` list and `allMeasurables` indices are:
 * - 0: [authorPicture] - 0 or 1 measurables
 * - 1: [relayBadges] - 0 or 1 measurables
 * - 2: [firstRow] - exactly 1 measurable
 * - 3: [secondRow] - 0 or 1 measurables
 * - 4: [noteContent] - 1+ measurables (stacked vertically)
 * - 5: [reactionsRow] - 0+ measurables (stacked vertically)
 *
 * ## Performance
 *
 * Compared to the previous nested Row > Column > Column structure:
 * - Eliminates 3 layout node levels (Row, author Column, content Column)
 * - Eliminates Row's two-pass measurement (measure author first, then content)
 * - Pre-computes all pixel dimensions once via [remember], cached across recompositions
 * - Remaining per-frame allocations: ~48 bytes for `listOf(6 lambdas)` +
 *   ~24 bytes per `arrayOfNulls` for multi-child slots
 *
 * The caller should use `drawBehind { drawRect(color) }` instead of
 * `background(color)` on the [modifier] to avoid recomposition when the
 * background color state changes (e.g. "new item" highlight fade).
 *
 * @param modifier Applied to the Layout root. Typically includes combinedClickable
 *   for note navigation, drawBehind for background color, and fillMaxWidth.
 * @param addPadding Whether to add standard note padding (12dp sides, 10dp top)
 *   to the author + content area. False for boosted notes.
 * @param showAuthorColumn Whether to reserve 55dp for the author picture column
 *   with a 10dp gap. False for boosted/quoted notes.
 * @param showSecondRow Whether to measure and place the second header row
 *   (NIP-05 status, location, PoW). Requires complete UI mode.
 * @param showContentSpacer Whether to add a 4dp spacer between header rows and
 *   note content. False for repost events.
 * @param authorPicture Slot for the author's profile picture (55x55dp area).
 *   Should emit nothing when [showAuthorColumn] is false to skip composition.
 * @param relayBadges Slot for relay indicator icons below the author picture.
 *   Should emit nothing when [showAuthorColumn] is false.
 * @param firstRow Slot for the primary header: author name, time, more options.
 *   Always present.
 * @param secondRow Slot for the secondary header: NIP-05, location, PoW, OTS.
 *   Should emit nothing when [showSecondRow] is false.
 * @param noteContent Slot for the event-specific content. May emit multiple
 *   children (content + zap splits + approval button) which are stacked vertically.
 * @param reactionsRow Slot for the reaction buttons row. May emit a ReactionsRow,
 *   a Spacer, or nothing depending on event type.
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

    // Slot order must match the indices used in the measure policy below.
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

        // Padding is applied in the measure policy (not via Modifier.padding) because
        // the reactions row needs full width while the content area is inset.
        val padStart = if (addPadding) px.padStart else 0
        val padEnd = if (addPadding) px.padEnd else 0
        val padTop = if (addPadding) px.padTop else 0

        // Column widths are computed from known fixed dimensions, eliminating
        // the Row's two-pass measurement (measure author → compute remaining → measure content).
        val innerWidth = maxWidth - padStart - padEnd
        val authorW = if (showAuthorColumn) px.authorWidth else 0
        val gap = if (showAuthorColumn) px.authorContentGap else 0
        val contentWidth = (innerWidth - authorW - gap).coerceAtLeast(0)

        // Constraints is a value class (inline Long) so these don't allocate.
        val authorConstraints = Constraints(maxWidth = px.authorWidth)
        val contentConstraints = Constraints(maxWidth = contentWidth)
        val fullWidthConstraints = Constraints(maxWidth = maxWidth)

        // Single-child slots: firstOrNull() returns null for empty slots (hidden),
        // skipping measurement entirely.
        val authorPlaceable = allMeasurables[0].firstOrNull()?.measure(authorConstraints)
        val relayPlaceable = allMeasurables[1].firstOrNull()?.measure(authorConstraints)

        val firstRowPlaceable = allMeasurables[2].firstOrNull()?.measure(contentConstraints)
        val secondRowPlaceable =
            if (showSecondRow) allMeasurables[3].firstOrNull()?.measure(contentConstraints) else null

        // Multi-child slots: measured into pre-sized arrays to avoid List allocation.
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

        // Height calculation
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

        // placeRelative handles RTL by mirroring x coordinates automatically.
        layout(maxWidth, totalHeight.coerceAtLeast(constraints.minHeight)) {
            // Author column (inside padding area)
            if (showAuthorColumn) {
                authorPlaceable?.placeRelative(padStart, padTop)
                relayPlaceable?.placeRelative(
                    padStart,
                    padTop + (authorPlaceable?.height ?: 0) + px.authorBadgeGap,
                )
            }

            // Content column (to the right of author, inside padding area)
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

            // Reactions row (full width, no side padding, below main area)
            var reactionsY = padTop + mainAreaHeight
            for (placeable in reactionsPlaceables) {
                placeable?.placeRelative(0, reactionsY)
                reactionsY += placeable?.height ?: 0
            }
        }
    }
}
