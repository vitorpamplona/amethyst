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
package com.vitorpamplona.amethyst.desktop.platform

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.FrameWindowScope

/**
 * Wraps content in a [MaterialTheme] tuned for the host OS: native fonts, accent
 * color, surface tones, and shape rounding all match what the user sees in their
 * other apps.
 *
 * Pair this with [applyNativeWindowChrome] inside the [androidx.compose.ui.window.Window]
 * scope to also get native title-bar treatment (transparent title bar with
 * traffic-light inset on macOS).
 */
@Composable
fun PlatformMaterialTheme(
    isDark: Boolean,
    content: @Composable () -> Unit,
) {
    val accent = remember { PlatformAccent.systemAccent() }
    val colorScheme = remember(isDark, accent) { PlatformColorScheme.resolve(isDark, accent) }
    MaterialTheme(
        colorScheme = colorScheme,
        typography = PlatformTypography.current,
        shapes = PlatformShapes.current,
        content = content,
    )
}

/**
 * Inset to apply at the top of window content so it doesn't underlap the macOS
 * traffic lights once `applyNativeWindowChrome` has made the title bar transparent
 * + full-content. ~28 dp clears the buttons with comfortable padding. On non-macOS
 * platforms returns 0 dp so layouts stay flush.
 */
val titleBarInsetTop: Dp
    get() = if (PlatformInfo.isMacOS) 28.dp else 0.dp

/**
 * Applies platform-specific native window chrome.
 *
 * On macOS:
 *  - `apple.awt.transparentTitleBar = true` removes the title bar tint so app
 *    content shows through.
 *  - `apple.awt.fullWindowContent = true` lets content extend under the title bar
 *    while the traffic lights stay drawn on top.
 *  - `apple.awt.windowTitleVisible = false` hides the "Amethyst" string so the
 *    bar is clean.
 *
 * On other platforms this is a no-op — the OS chrome stays as-is, which is
 * already what users expect (custom Compose-drawn title bars on Linux/Windows
 * mostly look worse than the system's, especially for window snapping & a11y).
 */
@Composable
fun FrameWindowScope.applyNativeWindowChrome() {
    if (!PlatformInfo.isMacOS) return
    val rootPane = window.rootPane
    rootPane.putClientProperty("apple.awt.transparentTitleBar", true)
    rootPane.putClientProperty("apple.awt.fullWindowContent", true)
    rootPane.putClientProperty("apple.awt.windowTitleVisible", false)
}
