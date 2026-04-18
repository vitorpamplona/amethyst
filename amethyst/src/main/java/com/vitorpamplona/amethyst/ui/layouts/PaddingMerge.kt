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

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalLayoutDirection

/**
 * Merges two [PaddingValues] component-wise, resolving start/end against the current
 * [LocalLayoutDirection]. Used to combine the scaffold's bar padding with an inner
 * list's own padding (e.g. FeedPadding) into a single `contentPadding` value for a
 * LazyColumn / LazyVerticalGrid.
 */
@Composable
fun rememberMergedPadding(
    outer: PaddingValues,
    inner: PaddingValues,
): PaddingValues {
    val layoutDirection = LocalLayoutDirection.current
    return remember(outer, inner, layoutDirection) {
        PaddingValues(
            start = outer.calculateStartPadding(layoutDirection) + inner.calculateStartPadding(layoutDirection),
            top = outer.calculateTopPadding() + inner.calculateTopPadding(),
            end = outer.calculateEndPadding(layoutDirection) + inner.calculateEndPadding(layoutDirection),
            bottom = outer.calculateBottomPadding() + inner.calculateBottomPadding(),
        )
    }
}
