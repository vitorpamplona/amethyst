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

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
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
 * A top-level content scaffold that caps width and centers its column on wide
 * displays. Each feed / list / profile screen wraps its contents in this so
 * cards maintain a consistent proportion across the whole app.
 *
 * Not used by:
 * - Messages (two-pane layout with its own sizing)
 * - Article Reader (has its own narrower reading-width logic)
 * - Editor / Chess / Relay Dashboard (rely on full width for tools / boards)
 */
@Composable
fun ReadingColumn(
    modifier: Modifier = Modifier,
    maxWidth: Dp = DefaultReadingWidth,
    content: @Composable ColumnScope.() -> Unit,
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(
            modifier = modifier.fillMaxSize().widthIn(max = maxWidth),
            content = content,
        )
    }
}
