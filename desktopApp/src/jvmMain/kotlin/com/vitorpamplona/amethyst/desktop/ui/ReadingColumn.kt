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
package com.vitorpamplona.amethyst.desktop.ui

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Maximum reading width for single-pane content screens. Matches the
 * comfortable column width used by Twitter / Mastodon / Threads on desktop —
 * wider than a book column (which tops out around 600 dp), narrower than a
 * full-window feed, so cards don't stretch disproportionately on 4K displays.
 */
val DefaultReadingWidth: Dp = 720.dp

/**
 * Side padding the current screen should apply to its scrollable list /
 * headers to keep content centered at [DefaultReadingWidth] within the
 * window. `0.dp` outside of a [ReadingColumn].
 *
 * Screens use this to widen the gutter on wide displays (`horizontal =
 * readingSidePadding + 12.dp`) while keeping the scrollable area itself at
 * full window width — so the mouse wheel scrolls the feed wherever it
 * hovers, not only inside the 720 dp column.
 */
val LocalReadingSidePadding = compositionLocalOf { 0.dp }

/**
 * Convenience: reads the current reading-column side padding and adds the
 * standard 12.dp screen-edge gutter. Use this inside composable bodies when
 * applying horizontal padding to header rows or `contentPadding` on
 * LazyColumns so items stay centered at [DefaultReadingWidth] while the
 * scrollable surface still spans the full window width.
 */
@Composable
fun readingHorizontalPadding(): Dp = LocalReadingSidePadding.current + 12.dp

/**
 * Top-level scaffold for single-pane content screens. Measures the window
 * width and computes the side padding that centers a [maxWidth]-wide column
 * within it. The actual content (header + LazyColumn) fills the window — the
 * centering is done via [LocalReadingSidePadding] applied to inner modifiers
 * (`horizontal` padding on a header Row, `contentPadding` on a LazyColumn).
 * This keeps scroll events live across the full window, not just the center
 * column.
 *
 * Not used by:
 * - Messages (two-pane layout with its own sizing)
 * - Article Reader (has its own narrower reading-width logic)
 * - Editor / Chess / Relay Dashboard (tools that want full width)
 */
@Composable
fun ReadingColumn(
    maxWidth: Dp = DefaultReadingWidth,
    content: @Composable ColumnScope.() -> Unit,
) {
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val sidePadding = ((this.maxWidth - maxWidth) / 2).coerceAtLeast(0.dp)
        CompositionLocalProvider(LocalReadingSidePadding provides sidePadding) {
            Column(modifier = Modifier.fillMaxSize(), content = content)
        }
    }
}
