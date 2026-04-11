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

import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.ic_dm
import com.vitorpamplona.amethyst.commons.resources.ic_home
import com.vitorpamplona.amethyst.commons.resources.ic_notifications
import com.vitorpamplona.amethyst.commons.resources.ic_sensors
import com.vitorpamplona.amethyst.commons.resources.ic_video
import com.vitorpamplona.amethyst.ui.navigation.routes.Route
import com.vitorpamplona.amethyst.ui.theme.Size20dp
import com.vitorpamplona.amethyst.ui.theme.Size23dp
import com.vitorpamplona.amethyst.ui.theme.Size24dp
import com.vitorpamplona.amethyst.ui.theme.Size25dp
import kotlinx.collections.immutable.persistentListOf
import org.jetbrains.compose.resources.DrawableResource

class BottomBarRoute(
    val route: Route,
    val icon: DrawableResource,
    val contentDescriptor: Int,
    val notifSize: Modifier = Modifier.size(Size23dp),
    val iconSize: Modifier = Modifier.size(Size20dp),
)

val bottomNavigationItems =
    persistentListOf(
        BottomBarRoute(Route.Home, Res.drawable.ic_home, R.string.route_home, Modifier.size(Size25dp), Modifier.size(Size24dp)),
        BottomBarRoute(Route.Message, Res.drawable.ic_dm, R.string.route_messages),
        BottomBarRoute(Route.Video, Res.drawable.ic_video, R.string.route_video),
        BottomBarRoute(Route.Discover, Res.drawable.ic_sensors, R.string.route_discover),
        BottomBarRoute(Route.Notification(), Res.drawable.ic_notifications, R.string.route_notifications),
    )
