/**
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
package com.vitorpamplona.amethyst.ui.navigation

import android.graphics.Rect
import android.view.View
import android.view.ViewTreeObserver
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.BottomAppBarDefaults.windowInsets
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.ui.painterRes
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.stringRes
import com.vitorpamplona.amethyst.ui.theme.DividerThickness
import com.vitorpamplona.amethyst.ui.theme.Size0dp
import com.vitorpamplona.amethyst.ui.theme.Size10Modifier
import com.vitorpamplona.amethyst.ui.theme.Size24dp
import com.vitorpamplona.amethyst.ui.theme.Size25dp
import kotlinx.collections.immutable.persistentListOf

val bottomNavigationItems =
    persistentListOf(
        BottomBarRoute(Route.Home, R.drawable.ic_home, R.string.route_home, Modifier.size(Size25dp), Modifier.size(Size24dp)),
        BottomBarRoute(Route.Message, R.drawable.ic_dm, R.string.route_messages),
        BottomBarRoute(Route.Video, R.drawable.ic_video, R.string.route_video),
        BottomBarRoute(Route.Discover, R.drawable.ic_sensors, R.string.route_discover),
        BottomBarRoute(Route.Notification, R.drawable.ic_notifications, R.string.route_notifications),
    )

enum class Keyboard {
    Opened,
    Closed,
}

fun isKeyboardOpen(view: View): Keyboard {
    val rect = Rect()
    view.getWindowVisibleDisplayFrame(rect)
    val screenHeight = view.rootView.height
    val keypadHeight = screenHeight - rect.bottom

    return if (keypadHeight > screenHeight * 0.15) {
        Keyboard.Opened
    } else {
        Keyboard.Closed
    }
}

@Composable
fun keyboardAsState(): State<Keyboard> {
    val view = LocalView.current

    val keyboardState = remember(view) { mutableStateOf(isKeyboardOpen(view)) }

    DisposableEffect(view) {
        val onGlobalListener =
            ViewTreeObserver.OnGlobalLayoutListener {
                val newKeyboardValue = isKeyboardOpen(view)

                if (newKeyboardValue != keyboardState.value) {
                    keyboardState.value = newKeyboardValue
                }
            }
        view.viewTreeObserver.addOnGlobalLayoutListener(onGlobalListener)
        onDispose { view.viewTreeObserver.removeOnGlobalLayoutListener(onGlobalListener) }
    }

    return keyboardState
}

@Composable
fun IfKeyboardClosed(inner: @Composable () -> Unit) {
    val isKeyboardState by keyboardAsState()
    if (isKeyboardState == Keyboard.Closed) {
        inner()
    }
}

@Composable
fun AppBottomBar(
    selectedRoute: Route?,
    accountViewModel: AccountViewModel,
    nav: (Route) -> Unit,
) {
    IfKeyboardClosed { RenderBottomMenu(selectedRoute, accountViewModel, nav) }
}

@Composable
private fun RenderBottomMenu(
    selectedRoute: Route?,
    accountViewModel: AccountViewModel,
    nav: (Route) -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .windowInsetsPadding(windowInsets)
                .consumeWindowInsets(windowInsets)
                .height(50.dp),
    ) {
        HorizontalDivider(
            thickness = DividerThickness,
        )
        NavigationBar(
            containerColor = MaterialTheme.colorScheme.background,
            tonalElevation = Size0dp,
        ) {
            bottomNavigationItems.forEach { item ->
                HasNewItemsIcon(item.route == selectedRoute, item, accountViewModel, nav)
            }
        }
    }
}

@Composable
private fun RowScope.HasNewItemsIcon(
    selected: Boolean,
    bottomNav: BottomBarRoute,
    accountViewModel: AccountViewModel,
    nav: (Route) -> Unit,
) {
    NavigationBarItem(
        alwaysShowLabel = false,
        icon = {
            NotifiableIcon(
                selected,
                bottomNav,
                accountViewModel,
            )
        },
        selected = selected,
        onClick = { nav(bottomNav.route) },
    )
}

@Composable
private fun NotifiableIcon(
    selected: Boolean,
    route: BottomBarRoute,
    accountViewModel: AccountViewModel,
) {
    Box(route.notifSize) {
        Icon(
            painter = painterRes(resourceId = route.icon, 0),
            contentDescription = stringRes(route.contentDescriptor),
            modifier = route.iconSize,
            tint = if (selected) MaterialTheme.colorScheme.primary else Color.Unspecified,
        )

        AddNotifIconIfNeeded(route.route, accountViewModel, Modifier.align(Alignment.TopEnd))
    }
}

@Composable
fun AddNotifIconIfNeeded(
    route: Route,
    accountViewModel: AccountViewModel,
    modifier: Modifier = Modifier,
) {
    val flow = accountViewModel.hasNewItems[route] ?: return
    val hasNewItems by flow.collectAsStateWithLifecycle()
    if (hasNewItems) {
        NotificationDotIcon(modifier)
    }
}

@Composable
private fun NotificationDotIcon(modifier: Modifier) {
    val color = MaterialTheme.colorScheme.primary
    Canvas(modifier = Size10Modifier.then(modifier), onDraw = {
        drawCircle(color = color)
    })
}
