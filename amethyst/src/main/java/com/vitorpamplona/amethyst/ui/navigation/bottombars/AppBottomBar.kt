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

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitorpamplona.amethyst.ui.navigation.routes.Route
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.stringRes
import com.vitorpamplona.amethyst.ui.theme.DividerThickness
import com.vitorpamplona.amethyst.ui.theme.Size0dp
import com.vitorpamplona.amethyst.ui.theme.Size10Modifier
import com.vitorpamplona.amethyst.ui.theme.Size20dp
import com.vitorpamplona.amethyst.ui.theme.Size23dp

@Composable
fun AppBottomBar(
    selectedRoute: Route?,
    accountViewModel: AccountViewModel,
    nav: (Route) -> Unit,
) {
    val items by accountViewModel.settings.uiSettingsFlow.bottomBarItems
        .collectAsStateWithLifecycle()
    if (items.isEmpty()) {
        Spacer(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(windowInsets)
                    .consumeWindowInsets(windowInsets),
        )
        return
    }
    val isKeyboardState by keyboardAsState()
    if (isKeyboardState == KeyboardState.Closed) {
        RenderBottomMenu(items, selectedRoute, accountViewModel, nav)
    }
}

@Composable
private fun RenderBottomMenu(
    items: List<NavBarItem>,
    selectedRoute: Route?,
    accountViewModel: AccountViewModel,
    nav: (Route) -> Unit,
) {
    val defs = remember(items) { items.mapNotNull(NavBarCatalog::get) }

    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.background)
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
            defs.forEach { def ->
                val destination = remember(def, accountViewModel) { def.resolveRoute(accountViewModel) }
                HasNewItemsIcon(destination == selectedRoute, def, destination, accountViewModel, nav)
            }
        }
    }
}

@Composable
private fun RowScope.HasNewItemsIcon(
    selected: Boolean,
    def: NavBarItemDef,
    destination: Route,
    accountViewModel: AccountViewModel,
    nav: (Route) -> Unit,
) {
    NavigationBarItem(
        alwaysShowLabel = false,
        icon = {
            NotifiableIcon(
                selected,
                def,
                destination,
                accountViewModel,
            )
        },
        selected = selected,
        onClick = { nav(destination) },
    )
}

@Composable
private fun NotifiableIcon(
    selected: Boolean,
    def: NavBarItemDef,
    destination: Route,
    accountViewModel: AccountViewModel,
) {
    Box(Modifier.size(Size23dp)) {
        val iconSizeModifier = Modifier.size(Size20dp)
        val description = stringRes(def.labelRes)
        Icon(
            imageVector = def.icon,
            contentDescription = description,
            modifier = iconSizeModifier,
        )

        AddNotifIconIfNeeded(destination, accountViewModel, Modifier.align(Alignment.TopEnd))
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
