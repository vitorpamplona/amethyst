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

import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalPlatformWindowInsets
import androidx.compose.ui.platform.PlatformInsets
import androidx.compose.ui.platform.PlatformWindowInsets
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.isSpecified
import androidx.compose.ui.unit.sp
import com.vitorpamplona.amethyst.commons.ui.layouts.LocalTitleBarOverlay
import com.vitorpamplona.amethyst.commons.ui.theme.DefaultTypography

/**
 * The shared type scale sized for a desktop screen. Android's post text is 16 sp, where desktop
 * apps read at 13–15 (macOS body text is 13 pt): the larger styles step down, while the small gray
 * ones (`bodySmall`, labels) keep their size, as they already sit near the OS's secondary text.
 */
val DesktopTypography: Typography =
    DefaultTypography.run {
        copy(
            displayLarge = displayLarge.resized(48),
            displayMedium = displayMedium.resized(40),
            displaySmall = displaySmall.resized(32),
            headlineLarge = headlineLarge.resized(28),
            headlineMedium = headlineMedium.resized(24),
            headlineSmall = headlineSmall.resized(21),
            titleLarge = titleLarge.resized(20),
            titleMedium = titleMedium.resized(15),
            titleSmall = titleSmall.resized(13),
            bodyLarge = bodyLarge.resized(14),
            bodyMedium = bodyMedium.resized(13),
        )
    }

/** [size] in sp, with the line height scaled along when the style sets one. */
private fun TextStyle.resized(size: Int): TextStyle {
    val lineHeight = if (lineHeight.isSpecified && fontSize.isSpecified) (lineHeight.value * size / fontSize.value).sp else lineHeight
    return copy(fontSize = size.sp, lineHeight = lineHeight)
}

/**
 * Reports macOS's transparent title bar to the shared UI as the window's caption bar, so every
 * screen that pads its top with `WindowInsets.systemBars` (Material's top bars, the scaffolds)
 * draws its own background up to the window's edge and keeps its content below the traffic lights,
 * as Android screens do under the status bar. In a multi-pane window only the leading pane keeps
 * it: the shell reads [LocalTitleBarOverlay] to take it back elsewhere. [top] of 0 provides nothing.
 *
 * Compose Desktop reads all window insets from [LocalPlatformWindowInsets] (internal, see CMP-9379),
 * which reports none for a full-content window; this wraps the platform's values and only raises
 * the caption bar and the system bars.
 */
@OptIn(InternalComposeUiApi::class)
@Composable
fun ProvideTitleBarInsets(
    top: Dp,
    content: @Composable () -> Unit,
) {
    val base = LocalPlatformWindowInsets.current
    val topPx = with(LocalDensity.current) { top.roundToPx() }
    if (topPx <= 0) {
        content()
        return
    }
    val insets = remember(base, topPx) { TitleBarWindowInsets(base, topPx) }
    CompositionLocalProvider(
        LocalPlatformWindowInsets provides insets,
        // Lets the shell take the inset back in the panes right of the leading one.
        LocalTitleBarOverlay provides top,
        content = content,
    )
}

@OptIn(InternalComposeUiApi::class)
private class TitleBarWindowInsets(
    private val base: PlatformWindowInsets,
    private val topPx: Int,
) : PlatformWindowInsets by base {
    override val captionBar: PlatformInsets = base.captionBar.withTopAtLeast(topPx)
    override val systemBars: PlatformInsets = base.systemBars.withTopAtLeast(topPx)

    // Popups and dialogs drop the safe insets; then the title bar goes with them.
    override fun excluding(
        safeInsets: Boolean,
        ime: Boolean,
    ): PlatformWindowInsets = if (safeInsets) base.excluding(safeInsets, ime) else TitleBarWindowInsets(base.excluding(safeInsets, ime), topPx)
}

@OptIn(InternalComposeUiApi::class)
private fun PlatformInsets.withTopAtLeast(topPx: Int): PlatformInsets =
    PlatformInsets(
        getLeft = { left },
        getTop = { maxOf(top, topPx) },
        getRight = { right },
        getBottom = { bottom },
    )
