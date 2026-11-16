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
package com.vitorpamplona.amethyst.commons.ui.navigation.navs

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import com.vitorpamplona.amethyst.commons.model.navigation.NavStackEntry

/**
 * Keeps the screen being composed open while [held] answers true: its tab's ×, the tab menu's bulk
 * closes, Ctrl/Cmd+W and its window's title bar leave it where it is (see [NavStackEntry.holdOpen]).
 * [held] is asked at the moment of closing, not when this recomposes, so a screen that ends the
 * hold and closes itself in the same step is let go.
 *
 * Only the tab and window layouts close screens this way. The screen still blocks its own Close
 * button and back (`PlatformBackHandler`) itself.
 */
@Composable
fun HoldOpenWhile(held: () -> Boolean) {
    val entry = LocalNavStackEntry.current ?: return
    val current by rememberUpdatedState(held)
    DisposableEffect(entry) {
        val hold = { current() }
        entry.holdOpen = hold
        // A tab moving out to a window composes the screen again there, and the new hold can
        // arrive before this one is disposed: drop only our own.
        onDispose { if (entry.holdOpen === hold) entry.holdOpen = null }
    }
}
