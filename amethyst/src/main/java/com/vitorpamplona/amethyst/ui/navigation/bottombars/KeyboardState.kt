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
package com.vitorpamplona.amethyst.ui.navigation.bottombars

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalDensity

enum class KeyboardState {
    Opened,
    Closed,
}

/**
 * Whether the soft keyboard is currently on screen, derived from [WindowInsets.ime].
 *
 * This intentionally reads the same animated IME inset that drives `Modifier.imePadding()`
 * everywhere else in the app, so the two can never disagree. The previous implementation
 * measured `View.getWindowVisibleDisplayFrame` from a `ViewTreeObserver` global-layout
 * listener — a pre-edge-to-edge heuristic. Under `enableEdgeToEdge()`
 * (`decorFitsSystemWindows = false`) the window content no longer resizes for the IME, so
 * that listener fired when the keyboard appeared but frequently never fired again when it
 * closed, latching the state at [Opened] even though the keyboard was gone (leaving the
 * bottom navigation bar hidden and a stranded gap at the bottom). The IME inset always
 * animates back to zero on the persistent root view, so this reading can't get stuck.
 */
@Composable
fun keyboardAsState(): State<KeyboardState> {
    val density = LocalDensity.current
    val imeInsets = WindowInsets.ime
    return remember(density, imeInsets) {
        derivedStateOf {
            if (imeInsets.getBottom(density) > 0) KeyboardState.Opened else KeyboardState.Closed
        }
    }
}
