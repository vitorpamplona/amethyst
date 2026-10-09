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
package com.vitorpamplona.amethyst.commons.ui.navigation.shell

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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.movableContentOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.commons.model.navigation.Route
import com.vitorpamplona.amethyst.commons.ui.components.PlatformBackHandler
import com.vitorpamplona.amethyst.commons.ui.components.rememberModalSheetState
import com.vitorpamplona.amethyst.commons.ui.layouts.LocalScreenLayout
import com.vitorpamplona.amethyst.commons.ui.layouts.NavigationStyle
import com.vitorpamplona.amethyst.commons.ui.navigation.bottombars.AppNavigationRail
import com.vitorpamplona.amethyst.commons.ui.navigation.deck.DeckFocus
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.Nav
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.notifications.NotificationSidePanel
import com.vitorpamplona.amethyst.commons.ui.theme.DividerThickness
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import kotlinx.coroutines.launch

/**
 * The navigation shell around [content] at every window size: the modal drawer (phones), the rail
 * (medium windows), or the permanently docked drawer (wide, landscape, tall windows), plus the
 * docked notification feed when the window has room for it and the account-switcher sheet.
 *
 * What the drawers and the sheet show is still the front end's: [drawerContent] and
 * [permanentDrawerContent] draw the modal and the docked drawer (both get the callback that opens
 * the account switcher), [accountSwitcherContent] fills the switcher sheet. [suspendEdgeSwipe] is
 * read while composing the modal drawer; returning true turns its left-edge swipe off, for a front
 * end with its own gesture near that edge (Android's embedded-tab selection handles).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppShellLayout(
    accountViewModel: AccountViewModel,
    nav: Nav,
    drawerContent: @Composable (openAccountSwitcher: () -> Unit) -> Unit,
    permanentDrawerContent: @Composable (openAccountSwitcher: () -> Unit) -> Unit,
    accountSwitcherContent: @Composable () -> Unit,
    suspendEdgeSwipe: () -> Boolean = { false },
    /** The deck's columns, beside the main screen in place of the notification panel; null when the deck is off. */
    deck: (@Composable (Modifier) -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var openAccountSwitcherBottomSheet by rememberSaveable { mutableStateOf(false) }

    val sheetState =
        rememberModalSheetState(
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
    // content lets the whole navigation subtree MOVE between those positions instead of being
    // disposed and rebuilt, preserving every screen's remember/rememberSaveable state.
    val currentContent by rememberUpdatedState(content)
    val movableContent = remember { movableContentOf { currentContent() } }

    // The deck moves between the same shells, and its columns' histories and ViewModels must move
    // with it, not be rebuilt when the window crosses a tier.
    val currentDeck by rememberUpdatedState(deck)
    val movableDeck = remember { movableContentOf { modifier: Modifier -> currentDeck?.invoke(modifier) } }
    val deckSlot: (@Composable (Modifier) -> Unit)? = if (deck != null) movableDeck else null

    val docked = LocalScreenLayout.current.navigationStyle == NavigationStyle.PERMANENT_DRAWER

    // Publish docked-ness on the Nav so drawer consumers (openDrawer, edge swipes, the
    // status editor) can behave correctly without each re-deriving the layout tier.
    LaunchedEffect(nav, docked) {
        nav.isDrawerDocked = docked
        // Entering the permanent tier with the modal drawer still Open would otherwise
        // carry the stale Open value back to the modal tier and pop the drawer uninvited.
        if (docked && !nav.drawerState.isClosed) {
            nav.drawerState.snapTo(DrawerValue.Closed)
        }
    }

    if (docked) {
        PermanentDrawerShell(accountViewModel, nav, { permanentDrawerContent(openSheetFunction) }, deckSlot, movableContent)
    } else {
        ModalDrawerShell(accountViewModel, nav, { drawerContent(openSheetFunction) }, suspendEdgeSwipe, deckSlot, movableContent)
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
            accountSwitcherContent()
        }
    }
}

/**
 * Every window that does not dock the drawer: the drawer slides in as a modal sheet. On the
 * rail tier an [AppNavigationRail] sits at the left edge in place of the phone bottom bar and
 * the shell becomes multi-pane — a wide portrait window rails and is still wide enough for
 * the notification panel.
 */
@Composable
private fun ModalDrawerShell(
    accountViewModel: AccountViewModel,
    nav: Nav,
    drawer: @Composable () -> Unit,
    suspendEdgeSwipe: () -> Boolean,
    deck: (@Composable (Modifier) -> Unit)?,
    content: @Composable () -> Unit,
) {
    // Derived, so a window being resized (a desktop drag, a foldable mid-unfold) recomposes this shell
    // only when the orientation actually flips, not on every size change along the way.
    val windowInfo = LocalWindowInfo.current
    val isLandscape by remember(windowInfo) {
        derivedStateOf { windowInfo.containerSize.let { it.width > it.height } }
    }
    LaunchedEffect(key1 = isLandscape) {
        // Dismiss an open drawer when the device rotates to landscape; the layout
        // underneath changes too much for the sheet to stay meaningful.
        if (isLandscape) {
            nav.drawerState.close()
        }
    }

    val isTabPagerRoute by remember(nav) {
        derivedStateOf { nav.currentRoute.let { it is Route.Home || it is Route.Message } }
    }
    val drawerGesturesEnabled =
        (
            !isTabPagerRoute ||
                nav.drawerState.isOpen ||
                nav.drawerState.targetValue != nav.drawerState.currentValue
        ) &&
            // The front end can take the left edge for a gesture of its own (on Android, a selection handle
            // dragged over an embedded surface), so a drag there doesn't open the drawer.
            !suspendEdgeSwipe()

    val showRail = LocalScreenLayout.current.navigationStyle == NavigationStyle.NAV_RAIL

    ModalNavigationDrawer(
        drawerState = nav.drawerState,
        gesturesEnabled = drawerGesturesEnabled,
        drawerContent = {
            drawer()
            PlatformBackHandler(enabled = nav.drawerState.isOpen, nav::closeDrawer)
        },
        content = {
            if (showRail) {
                MultiPaneShell(
                    accountViewModel = accountViewModel,
                    nav = nav,
                    leading = { AppNavigationRail(nav, accountViewModel) },
                    deck = deck,
                    content = content,
                )
            } else {
                content()
            }
        },
    )
}

/**
 * The wide-window arrangement both shells render: a [leading] navigation pane, the centre
 * content, and the notification feed when the window has room. Shared because a wide portrait
 * window now rails rather than docks and is still wide enough for the panel — the two shells
 * differ only in which navigation pane leads.
 */
@Composable
private fun MultiPaneShell(
    accountViewModel: AccountViewModel,
    nav: Nav,
    leading: @Composable () -> Unit,
    deck: (@Composable (Modifier) -> Unit)?,
    content: @Composable () -> Unit,
) {
    Row(Modifier.fillMaxSize()) {
        leading()

        VerticalDivider(thickness = DividerThickness)

        if (deck != null) {
            DeckSplit(Modifier.weight(1f).fillMaxHeight(), { CenterPane(Modifier.unfocusDeckOnPress(), content) }, { deck(Modifier) })
        } else {
            CenterPane(Modifier.weight(1f), content)

            NotificationSidePanelSlot(accountViewModel, nav)
        }
    }
}

/** A press in the main screen takes the focus back from the deck's columns (see [DeckFocus]). */
private fun Modifier.unfocusDeckOnPress() =
    pointerInput(Unit) {
        awaitPointerEventScope {
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                if (event.type == PointerEventType.Press) DeckFocus.columnId = null
            }
        }
    }

/** How narrow the deck may squeeze the main screen; past this, the columns scroll. */
private val DeckMainPaneMinWidth = 420.dp

/**
 * The main screen and the deck side by side: the deck takes what it needs up to all but
 * [DeckMainPaneMinWidth], the main screen the rest. A plain layout rather than BoxWithConstraints,
 * whose subcomposition the main screen's movable content would have to move into and out of.
 */
@Composable
private fun DeckSplit(
    modifier: Modifier,
    center: @Composable () -> Unit,
    deck: @Composable () -> Unit,
) {
    Layout(contents = listOf(center, deck), modifier = modifier) { (centerMeasurables, deckMeasurables), constraints ->
        val height = constraints.maxHeight
        val deckMax = (constraints.maxWidth - DeckMainPaneMinWidth.roundToPx()).coerceAtLeast(0)
        val deckPlaceables = deckMeasurables.map { it.measure(Constraints(maxWidth = deckMax, minHeight = height, maxHeight = height)) }
        val centerWidth = constraints.maxWidth - (deckPlaceables.maxOfOrNull { it.width } ?: 0)
        val centerPlaceables = centerMeasurables.map { it.measure(Constraints.fixed(centerWidth, height)) }
        layout(constraints.maxWidth, height) {
            centerPlaceables.forEach { it.place(0, 0) }
            deckPlaceables.forEach { it.place(centerWidth, 0) }
        }
    }
}

/**
 * The docked notification feed, when there is room for it. The panel duplicates the
 * Notifications screen, so it steps aside while the user is there.
 *
 * The current route is read here, through [derivedStateOf], rather than in the shells, so
 * windows too narrow for the panel never observe it and a navigation recomposes this slot only
 * when it enters or leaves Notifications.
 */
@Composable
private fun NotificationSidePanelSlot(
    accountViewModel: AccountViewModel,
    nav: Nav,
) {
    if (!LocalScreenLayout.current.hasRoomForNotificationPanel) return

    val onNotifications by remember(nav) { derivedStateOf { nav.currentRoute is Route.Notification } }

    if (!onNotifications) {
        VerticalDivider(thickness = DividerThickness)
        NotificationSidePanel(accountViewModel, nav)
    }
}

/**
 * Wide, landscape, tall windows: the drawer is permanently docked on the left, the bottom bar
 * disappears, and — when the window is wide enough — the notification feed docks on the right.
 */
@Composable
private fun PermanentDrawerShell(
    accountViewModel: AccountViewModel,
    nav: Nav,
    drawer: @Composable () -> Unit,
    deck: (@Composable (Modifier) -> Unit)?,
    content: @Composable () -> Unit,
) {
    MultiPaneShell(
        accountViewModel = accountViewModel,
        nav = nav,
        leading = drawer,
        deck = deck,
        content = content,
    )
}

/**
 * Hosts the navigation content. Screen width capping happens per destination
 * ([com.vitorpamplona.amethyst.commons.ui.layouts.CappedScreenContent] via the
 * [com.vitorpamplona.amethyst.commons.ui.navigation.host.NavDestinations] builders), so this pane
 * just claims the leftover row width.
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
