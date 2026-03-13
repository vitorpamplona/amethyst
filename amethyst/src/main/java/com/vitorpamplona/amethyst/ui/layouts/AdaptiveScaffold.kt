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

import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteType
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.window.core.layout.WindowWidthSizeClass
import com.vitorpamplona.amethyst.ui.navigation.bottombars.BottomBarRoute
import com.vitorpamplona.amethyst.ui.navigation.bottombars.bottomNavigationItems
import com.vitorpamplona.amethyst.ui.navigation.routes.Route
import com.vitorpamplona.amethyst.ui.painterRes
import com.vitorpamplona.amethyst.ui.stringRes

val LocalWindowSizeClass = compositionLocalOf { WindowWidthSizeClass.COMPACT }

@Composable
fun isCompactWindow(): Boolean = LocalWindowSizeClass.current == WindowWidthSizeClass.COMPACT

@Composable
fun AdaptiveScaffold(
    selectedRoute: Route?,
    onNavigate: (Route) -> Unit,
    content: @Composable () -> Unit,
) {
    val adaptiveInfo = currentWindowAdaptiveInfo()
    val windowSizeClass = adaptiveInfo.windowSizeClass.windowWidthSizeClass

    CompositionLocalProvider(LocalWindowSizeClass provides windowSizeClass) {
        NavigationSuiteScaffold(
            navigationSuiteItems = {
                bottomNavigationItems.forEach { navItem ->
                    val selected = navItem.route == selectedRoute
                    item(
                        selected = selected,
                        onClick = { onNavigate(navItem.route) },
                        icon = {
                            AdaptiveNavIcon(navItem, selected)
                        },
                    )
                }
            },
        ) {
            content()
        }
    }
}

@Composable
private fun AdaptiveNavIcon(
    route: BottomBarRoute,
    selected: Boolean,
) {
    Icon(
        painter = painterRes(resourceId = route.icon, 0),
        contentDescription = stringRes(route.contentDescriptor),
        modifier = route.iconSize,
        tint = if (selected) MaterialTheme.colorScheme.primary else Color.Unspecified,
    )
}
