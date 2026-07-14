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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.ui.components.getActivity

/** How the app shell presents its top-level navigation for the current window size. */
enum class NavigationStyle {
    /** Compact windows (phones): bottom navigation bar + modal drawer. */
    BOTTOM_BAR,

    /**
     * Medium windows (portrait tablets, unfolded foldables): a left navigation rail
     * replaces the bottom bar; the drawer stays modal behind the rail's avatar button.
     */
    NAV_RAIL,

    /** Expanded windows (landscape tablets, desktop windows): the drawer docks permanently on the left. */
    PERMANENT_DRAWER,
}

/**
 * The shell layout decisions for the current window, published once per window size change
 * through [LocalScreenLayout] so every screen, bar and panel agrees on the same tier.
 */
@Immutable
data class ScreenLayoutSpec(
    val navigationStyle: NavigationStyle,
    val showsNotificationPanel: Boolean,
) {
    /**
     * True on the rail and permanent-drawer tiers. Large screens hide the bottom bar and pin
     * the top/bottom chrome (no disappearing bars on scroll).
     */
    val isLargeScreen: Boolean get() = navigationStyle != NavigationStyle.BOTTOM_BAR

    companion object {
        val Phone = ScreenLayoutSpec(NavigationStyle.BOTTOM_BAR, showsNotificationPanel = false)
    }
}

val LocalScreenLayout = compositionLocalOf { ScreenLayoutSpec.Phone }

/**
 * Minimum window width for the docked notification panel: the permanent drawer
 * ([PermanentDrawerWidth]) + a readable center pane + the panel ([NotificationPanelWidth])
 * only coexist comfortably from a landscape-tablet-sized window up.
 */
private const val NOTIFICATION_PANEL_MIN_WINDOW_DP = 1200

val PermanentDrawerWidth = 300.dp

val NotificationPanelWidth = 360.dp

/**
 * Maximum width of a screen's content column inside a wide center pane. Every NavHost
 * destination is wrapped in [CappedScreenContent] (via the builders in NavigationEffects),
 * so the whole screen — top bar, tabs, feed, settings rows — shares one centered reading
 * column instead of stretching across the pane. Screens that genuinely need the full pane
 * (Messages' two-pane split, the embedded browser surfaces) opt out at registration.
 */
val FeedContentMaxWidth = 600.dp

/**
 * Centers a destination's content at [FeedContentMaxWidth]. The outer box paints the theme
 * background so the gutters match the screens' own surfaces; on Compact windows the cap is
 * wider than the pane and this is a visual no-op.
 */
@Composable
fun CappedScreenContent(content: @Composable () -> Unit) {
    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.TopCenter,
    ) {
        Box(
            Modifier
                .widthIn(max = FeedContentMaxWidth)
                .fillMaxSize(),
        ) {
            content()
        }
    }
}

@OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
@Composable
fun rememberScreenLayoutSpec(): ScreenLayoutSpec {
    val widthSizeClass = calculateWindowSizeClass(getActivity()).widthSizeClass
    val windowWidthDp = LocalConfiguration.current.screenWidthDp
    return remember(widthSizeClass, windowWidthDp) {
        val style =
            when (widthSizeClass) {
                WindowWidthSizeClass.Expanded -> NavigationStyle.PERMANENT_DRAWER
                WindowWidthSizeClass.Medium -> NavigationStyle.NAV_RAIL
                else -> NavigationStyle.BOTTOM_BAR
            }
        ScreenLayoutSpec(
            navigationStyle = style,
            showsNotificationPanel =
                style == NavigationStyle.PERMANENT_DRAWER &&
                    windowWidthDp >= NOTIFICATION_PANEL_MIN_WINDOW_DP,
        )
    }
}
