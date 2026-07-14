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
package com.vitorpamplona.amethyst.ui.screen.loggedIn

import android.content.res.Configuration
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.SheetValue
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.movableContentOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.compose.currentBackStackEntryAsState
import com.vitorpamplona.amethyst.ui.layouts.LocalScreenLayout
import com.vitorpamplona.amethyst.ui.layouts.NavigationStyle
import com.vitorpamplona.amethyst.ui.navigation.bottombars.AppNavigationRail
import com.vitorpamplona.amethyst.ui.navigation.drawer.AccountSwitchBottomSheet
import com.vitorpamplona.amethyst.ui.navigation.drawer.DrawerContent
import com.vitorpamplona.amethyst.ui.navigation.drawer.PermanentDrawerContent
import com.vitorpamplona.amethyst.ui.navigation.navs.Nav
import com.vitorpamplona.amethyst.ui.navigation.routes.Route
import com.vitorpamplona.amethyst.ui.screen.AccountSessionManager
import com.vitorpamplona.amethyst.ui.screen.loggedIn.embed.EmbeddedSelectionDrag
import com.vitorpamplona.amethyst.ui.screen.loggedIn.notifications.NotificationSidePanel
import com.vitorpamplona.amethyst.ui.theme.DividerThickness
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountSwitcherAndLeftDrawerLayout(
    accountViewModel: AccountViewModel,
    accountSessionManager: AccountSessionManager,
    nav: Nav,
    content: @Composable () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var openAccountSwitcherBottomSheet by rememberSaveable { mutableStateOf(false) }

    val sheetState =
        rememberModalBottomSheetState(
            skipPartiallyExpanded = true,
            confirmValueChange = { it != SheetValue.PartiallyExpanded },
        )

    val openSheetFunction =
        remember {
            {
                scope.launch {
                    openAccountSwitcherBottomSheet = true
                    sheetState.show()
                }
                Unit
            }
        }

    // The layout tier can change while the app runs (fold/unfold, rotation, window resize)
    // and the shells below place `content` at different composition positions. Movable
    // content lets the whole NavHost subtree MOVE between those positions instead of being
    // disposed and rebuilt, preserving every screen's remember/rememberSaveable state.
    val currentContent by rememberUpdatedState(content)
    val movableContent = remember { movableContentOf { currentContent() } }

    val docked = LocalScreenLayout.current.navigationStyle == NavigationStyle.PERMANENT_DRAWER

    // Publish docked-ness on the Nav so drawer consumers (openDrawer, edge swipes, the
    // status editor) can behave correctly without each re-deriving the layout tier.
    LaunchedEffect(docked) {
        nav.isDrawerDocked = docked
        // Entering the permanent tier with the modal drawer still Open would otherwise
        // carry the stale Open value back to the modal tier and pop the drawer uninvited.
        if (docked && !nav.drawerState.isClosed) {
            nav.drawerState.snapTo(DrawerValue.Closed)
        }
    }

    if (docked) {
        PermanentDrawerShell(accountViewModel, nav, openSheetFunction, movableContent)
    } else {
        ModalDrawerShell(accountViewModel, nav, openSheetFunction, movableContent)
    }

    // Sheet content
    if (openAccountSwitcherBottomSheet) {
        ModalBottomSheet(
            onDismissRequest = {
                scope
                    .launch { sheetState.hide() }
                    .invokeOnCompletion {
                        if (!sheetState.isVisible) {
                            openAccountSwitcherBottomSheet = false
                        }
                    }
            },
            sheetState = sheetState,
        ) {
            AccountSwitchBottomSheet(
                accountViewModel = accountViewModel,
                accountSessionManager = accountSessionManager,
            )
        }
    }
}

/**
 * Compact and Medium windows: the drawer slides in as a modal sheet. On Medium an
 * [AppNavigationRail] sits at the left edge in place of the phone bottom bar.
 */
@Composable
private fun ModalDrawerShell(
    accountViewModel: AccountViewModel,
    nav: Nav,
    openSheet: () -> Unit,
    content: @Composable () -> Unit,
) {
    val orientation = LocalConfiguration.current.orientation
    LaunchedEffect(key1 = orientation) {
        // Dismiss an open drawer when the device rotates to landscape; the layout
        // underneath changes too much for the sheet to stay meaningful.
        if (orientation == Configuration.ORIENTATION_LANDSCAPE) {
            nav.drawerState.close()
        }
    }

    val navBackStackEntry by nav.controller.currentBackStackEntryAsState()
    val isTabPagerRoute =
        navBackStackEntry?.destination?.let { dest ->
            dest.hasRoute<Route.Home>() || dest.hasRoute<Route.Message>()
        } ?: false
    val drawerGesturesEnabled =
        (
            !isTabPagerRoute ||
                nav.drawerState.isOpen ||
                nav.drawerState.targetValue != nav.drawerState.currentValue
        ) &&
            // Suspend the left-edge swipe while a selection/caret handle is dragged over an embedded surface,
            // so a handle drag near the left edge (or the auto-scroll edge drag) doesn't open the drawer.
            !EmbeddedSelectionDrag.dragging

    val showRail = LocalScreenLayout.current.navigationStyle == NavigationStyle.NAV_RAIL

    ModalNavigationDrawer(
        drawerState = nav.drawerState,
        gesturesEnabled = drawerGesturesEnabled,
        drawerContent = {
            DrawerContent(nav, openSheet, accountViewModel)
            BackHandler(enabled = nav.drawerState.isOpen, nav::closeDrawer)
        },
        content = {
            if (showRail) {
                Row(Modifier.fillMaxSize()) {
                    AppNavigationRail(nav, accountViewModel)
                    VerticalDivider(thickness = DividerThickness)
                    CenterPane(Modifier.weight(1f), content)
                }
            } else {
                content()
            }
        },
    )
}

/**
 * Expanded windows: the drawer is permanently docked on the left, the bottom bar disappears,
 * and — when the window is wide enough — the notification feed docks on the right.
 */
@Composable
private fun PermanentDrawerShell(
    accountViewModel: AccountViewModel,
    nav: Nav,
    openSheet: () -> Unit,
    content: @Composable () -> Unit,
) {
    val navBackStackEntry by nav.controller.currentBackStackEntryAsState()
    // The panel duplicates the Notifications screen, so it steps aside while the user is there.
    val showPanel =
        LocalScreenLayout.current.showsNotificationPanel &&
            navBackStackEntry?.destination?.hasRoute<Route.Notification>() != true

    Row(Modifier.fillMaxSize()) {
        PermanentDrawerContent(nav, openSheet, accountViewModel)

        VerticalDivider(thickness = DividerThickness)

        CenterPane(Modifier.weight(1f), content)

        if (showPanel) {
            VerticalDivider(thickness = DividerThickness)
            NotificationSidePanel(accountViewModel, nav)
        }
    }
}

/**
 * Hosts the navigation content. Screen width capping happens per NavHost destination
 * ([com.vitorpamplona.amethyst.ui.layouts.CappedScreenContent] via the NavigationEffects
 * builders), so this pane just claims the leftover row width.
 */
@Composable
private fun CenterPane(
    modifier: Modifier,
    content: @Composable () -> Unit,
) {
    Box(modifier.fillMaxHeight()) {
        content()
    }
}
