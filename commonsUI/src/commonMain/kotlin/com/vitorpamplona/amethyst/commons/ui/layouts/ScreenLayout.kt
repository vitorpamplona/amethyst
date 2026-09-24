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

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.compositionLocalOf

/** How the app shell presents its top-level navigation for the current window size. */
enum class NavigationStyle {
    /** Compact windows (phones): bottom navigation bar + modal drawer. */
    BOTTOM_BAR,

    /**
     * Every non-Compact window that does not dock — portrait tablets and unfolded foldables at
     * any width, plus short landscape windows: a left navigation rail replaces the bottom bar
     * and the drawer stays modal behind the rail's avatar button.
     */
    NAV_RAIL,

    /** Wide, landscape, tall windows (landscape tablets, desktop): the drawer docks permanently on the left. */
    PERMANENT_DRAWER,
}

/**
 * The shell layout decisions for the current window, published once per window size change
 * through [LocalScreenLayout] so every screen, bar and panel agrees on the same tier.
 */
@Immutable
data class ScreenLayoutSpec(
    val navigationStyle: NavigationStyle,
    val hasRoomForNotificationPanel: Boolean,
) {
    /**
     * True on the rail and permanent-drawer tiers. Large screens hide the bottom bar and pin
     * the top/bottom chrome (no disappearing bars on scroll).
     */
    val isLargeScreen: Boolean get() = navigationStyle != NavigationStyle.BOTTOM_BAR

    companion object {
        val Phone = ScreenLayoutSpec(NavigationStyle.BOTTOM_BAR, hasRoomForNotificationPanel = false)
    }
}

val LocalScreenLayout = compositionLocalOf { ScreenLayoutSpec.Phone }
