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

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.SheetValue
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.movableContentOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitorpamplona.amethyst.commons.model.dock.DockLayout
import com.vitorpamplona.amethyst.commons.model.dock.DockView
import com.vitorpamplona.amethyst.commons.model.dock.PanelId
import com.vitorpamplona.amethyst.commons.model.navigation.Route
import com.vitorpamplona.amethyst.commons.ui.components.PlatformBackHandler
import com.vitorpamplona.amethyst.commons.ui.components.rememberModalSheetState
import com.vitorpamplona.amethyst.commons.ui.layouts.DockStripWidth
import com.vitorpamplona.amethyst.commons.ui.layouts.DrawerWidthRange
import com.vitorpamplona.amethyst.commons.ui.layouts.LocalScreenLayout
import com.vitorpamplona.amethyst.commons.ui.layouts.LocalTitleBarOverlay
import com.vitorpamplona.amethyst.commons.ui.layouts.MIN_CENTER_PANE_DP
import com.vitorpamplona.amethyst.commons.ui.layouts.NavigationStyle
import com.vitorpamplona.amethyst.commons.ui.layouts.NotificationPanelWidth
import com.vitorpamplona.amethyst.commons.ui.layouts.PaneFit
import com.vitorpamplona.amethyst.commons.ui.layouts.PaneSplitter
import com.vitorpamplona.amethyst.commons.ui.layouts.PermanentDrawerWidth
import com.vitorpamplona.amethyst.commons.ui.layouts.fitPanes
import com.vitorpamplona.amethyst.commons.ui.navigation.bottombars.AppNavigationRail
import com.vitorpamplona.amethyst.commons.ui.navigation.deck.DeckChromeWidth
import com.vitorpamplona.amethyst.commons.ui.navigation.deck.DeckFocus
import com.vitorpamplona.amethyst.commons.ui.navigation.dock.DockColumns
import com.vitorpamplona.amethyst.commons.ui.navigation.dock.DockController
import com.vitorpamplona.amethyst.commons.ui.navigation.dock.DockDragOverlay
import com.vitorpamplona.amethyst.commons.ui.navigation.dock.DockLayoutNameDialog
import com.vitorpamplona.amethyst.commons.ui.navigation.dock.DockOverlayLayer
import com.vitorpamplona.amethyst.commons.ui.navigation.dock.DockPanelNav
import com.vitorpamplona.amethyst.commons.ui.navigation.dock.DockPanelSpec
import com.vitorpamplona.amethyst.commons.ui.navigation.dock.DockStrip
import com.vitorpamplona.amethyst.commons.ui.navigation.dock.LocalDockController
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.Nav
import com.vitorpamplona.amethyst.commons.ui.platform.LocalAppServices
import com.vitorpamplona.amethyst.commons.ui.theme.DividerThickness
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlin.reflect.KClass
import com.vitorpamplona.amethyst.commons.model.deck.DeckLayout as DeckColumnsLayout

/**
 * The navigation shell around [content] at every window size: the modal drawer (phones), the rail
 * (medium windows), or the permanently docked drawer (wide, landscape, tall windows), plus the
 * docked notification feed when the window has room for it and the account-switcher sheet.
 *
 * What the drawers and the sheet show is still the front end's: [drawerContent] and
 * [permanentDrawerContent] draw the modal and the docked drawer (both get the callback that opens
 * the account switcher), [accountSwitcherContent] fills the switcher sheet. [suspendEdgeSwipe] is
 * read while composing the modal drawer; returning true turns its left-edge swipe off, for a front
 * end with its own gesture near that edge (Android's embedded-tab selection handles). [chrome] is
 * what the navigation layout adds around the main screen; its screen windows are declared by the caller.
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
    /**
     * What the navigation layout adds: the deck's columns, beside the main screen in place of the
     * notification panel, and a strip across the top of everything right of the docked drawer (the
     * tabs), so the headers below it stay level.
     */
    chrome: LayoutChrome = LayoutChrome.None,
    content: @Composable () -> Unit,
) {
    val deck = chrome.deck
    val topStrip = chrome.topStrip
    val scope = rememberCoroutineScope()
    var openAccountSwitcherBottomSheet by rememberSaveable { mutableStateOf(false) }

    val sheetState =
        rememberModalSheetState(
            skipPartiallyExpanded = true,
            confirmValueChange = { it != SheetValue.PartiallyExpanded },
        )

    // Only composes the sheet: ModalBottomSheet shows itself (LaunchedEffect(sheetState) { show() }).
    // Calling show() here as well moved the sheet's target to shown before its first frame, so the
    // scrim's fade, keyed on that target, started fully dark: on iOS the screen blinked to dark
    // before the sheet slid up.
    val openSheetFunction = remember { { openAccountSwitcherBottomSheet = true } }

    // The layout tier can change while the app runs (fold/unfold, rotation, window resize)
    // and the shells below place `content` at different composition positions. Movable
    // content lets the whole navigation subtree MOVE between those positions instead of being
    // disposed and rebuilt, preserving every screen's remember/rememberSaveable state.
    val currentContent by rememberUpdatedState(content)
    // In a multi-pane window (docked drawer or rail) the content sits right of the leading pane, so
    // it takes back the top inset of window controls drawn over the app (macOS's traffic lights;
    // 0 on Android). Done inside the movable content: a consumption on the pane around it stayed
    // applied after the content moved to a single-pane window, leaving its top bar under them.
    val movableContent =
        remember {
            movableContentOf {
                val multiPane = LocalScreenLayout.current.navigationStyle != NavigationStyle.BOTTOM_BAR
                val overlay = if (multiPane) LocalTitleBarOverlay.current else 0.dp
                Box(Modifier.consumeWindowInsets(PaddingValues(top = overlay))) {
                    currentContent()
                }
            }
        }

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

    val dock = LocalDockController.current
    val density = LocalDensity.current
    val windowInfo = LocalWindowInfo.current
    if (dock != null) {
        // The drag tests the pointer against the window's bounds; followed off composition, so a
        // window being resized doesn't recompose the shell for it.
        LaunchedEffect(dock, windowInfo, density) {
            dock.drag.edgePx = with(density) { DockDropEdge.toPx() }
            snapshotFlow { windowInfo.containerSize }.collect { dock.drag.window = Size(it.width.toFloat(), it.height.toFloat()) }
        }
    }

    Box(Modifier.fillMaxSize()) {
        if (docked) {
            PermanentDrawerShell(accountViewModel, nav, { permanentDrawerContent(openSheetFunction) }, deckSlot, topStrip, movableContent)
        } else {
            ModalDrawerShell(accountViewModel, nav, { drawerContent(openSheetFunction) }, suspendEdgeSwipe, deckSlot, movableContent)
        }
        // Over everything while a panel is dragged: where it would land.
        if (dock != null) {
            DockDragOverlay(dock)
            DockLayoutNameDialog(dock)
        }
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
                    resizableLeading = false,
                    leading = { AppNavigationRail(nav, accountViewModel) },
                    deck = deck,
                    topStrip = null,
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
 * content, and the dock on the right: its columns of panels (the notification feed, the
 * assistant, the devices: [DockController]) and, at the window's edge, the strip of their icons.
 * Shared because a wide portrait window rails rather than docks and is still wide enough for the
 * dock — the two shells differ only in which navigation pane leads.
 *
 * The docked drawer ([resizableLeading]) and each dock column sit behind [PaneSplitter]s the user
 * drags to resize them; the widths are kept per device and fitted to the window by [fitPanes], so
 * the centre pane keeps its room: the outer column narrows first, and a column that cannot fit even
 * at its narrowest folds to the strip until the window widens. Where panels cannot dock (a window
 * too narrow, a screen that wants the whole width) they show over the main screen instead. Beside
 * the deck the dock keeps the main screen and one deck column their room.
 */
@Composable
private fun MultiPaneShell(
    accountViewModel: AccountViewModel,
    nav: Nav,
    resizableLeading: Boolean,
    leading: @Composable () -> Unit,
    deck: (@Composable (Modifier) -> Unit)?,
    topStrip: (@Composable () -> Unit)?,
    content: @Composable () -> Unit,
) {
    val paneWidthPrefs = LocalAppServices.current.paneWidthPrefs
    val widths by paneWidthPrefs.flow.collectAsStateWithLifecycle()
    val dock = LocalDockController.current
    val roomForDock = LocalScreenLayout.current.hasRoomForNotificationPanel
    val wantsWidth = fullWidthHere(nav)

    val saved by (dock?.layoutFlow ?: EmptyDockFlow).collectAsStateWithLifecycle()
    val layout = remember(saved, dock) { if (dock != null) saved.ensure(dock.ids) else saved }
    // The panels the current screen leaves in the dock: a panel steps out on the screen it duplicates.
    val hiddenHere = panelsHiddenHere(nav, dock)
    // A deck that already has a Notifications column has no use for the notification panel too.
    val deckNeeds = if (deck != null) deckNeeds(accountViewModel) else null
    val deckNotifications = deckNeeds?.showsNotifications == true
    val available =
        remember(dock, layout, hiddenHere, deckNotifications) {
            dock?.allIds(layout)?.filter { it !in hiddenHere && !(deckNotifications && it == PanelId.NOTIFICATIONS) }.orEmpty()
        }
    // A screen that wants the width keeps only the panels made to sit beside it ([DockPanelSpec.besideFullWidth]).
    val dockOnly =
        remember(dock, available, wantsWidth) {
            if (wantsWidth && dock != null) available.filter { dock.spec(it)?.besideFullWidth == true }.toSet() else null
        }
    // Beside the deck too: its columns and the dock share the room, the dock giving way first.
    val canDock = dock != null && roomForDock && dockOnly?.isNotEmpty() != false
    val overlay = dock?.overlay
    val maximized = dock?.maximized
    val view =
        remember(layout, available, canDock, dockOnly, overlay, maximized) {
            if (canDock) layout.view((dockOnly ?: available.toSet()) - setOfNotNull(overlay, maximized)) else DockView.Empty
        }
    // The strip shows wherever the dock does, and on a docked-drawer window too narrow for it (its
    // panels then open over the screen). With only the notification feed, as on a phone build, it
    // shows only while the feed is folded: the panel and its bell are the same thing.
    val stripShown =
        dock != null && available.isNotEmpty() &&
            (roomForDock || resizableLeading) &&
            (available.size > 1 || view.isEmpty)
    if (dock != null) {
        SideEffect {
            dock.canDock = canDock
            dock.dockOnly = dockOnly
            dock.stripShown = stripShown
        }
        // A phone-sized window has no dock: a panel asked for there would open nowhere.
        DisposableEffect(dock) {
            onDispose {
                dock.canDock = false
                dock.dockOnly = null
                dock.stripShown = false
            }
        }
    }

    val windowWidth =
        with(LocalDensity.current) {
            LocalWindowInfo.current.containerSize.width
                .toDp()
                .value
        }
    // The outer column gives up its room first, then the inner ones, then the drawer.
    val columns = view.columns
    val panes =
        columns.reversed().map { column ->
            val first = column.slots.first().active
            PaneFit(column.width, dock?.spec(first)?.defaultWidth ?: NotificationPanelWidth.value, DockLayout.COLUMN_WIDTH_RANGE, droppable = true)
        } + PaneFit(widths.drawer, PermanentDrawerWidth.value, DrawerWidthRange, droppable = false, shown = resizableLeading)
    // Beside the deck the main screen keeps its own minimum plus one deck column's.
    // Beside the deck, the dock leaves the main screen its minimum and one whole deck column.
    val centerMin = if (deckNeeds != null) DeckMainPaneMinWidth.value + DeckChromeWidth.value + deckNeeds.firstColumnWidth else MIN_CENTER_PANE_DP
    val fitted = fitPanes(windowWidth, panes, reserved = if (stripShown) DockStripWidth.value else 0f, centerMin = centerMin)
    val fittedColumns = fitted.take(columns.size).reversed()
    // A column the window cannot hold right now folds to the strip until it widens: out of what is
    // drawn, and out of what the strip lights as shown.
    val shownView = DockView(columns.filterIndexed { i, _ -> fittedColumns[i] > 0f })
    val columnWidths = fittedColumns.filter { it > 0f }
    val drawerWidth = fitted.last()
    val foldedByWidth = columns.filterIndexed { i, _ -> fittedColumns[i] <= 0f }.flatMap { c -> c.slots.flatMap { it.tabs } }.toSet()
    if (dock != null) SideEffect { dock.foldedByWidth = foldedByWidth }
    val panelNav = remember(nav) { DockPanelNav(nav) }

    Row(Modifier.fillMaxSize()) {
        if (resizableLeading) {
            Box(Modifier.width(drawerWidth.dp).fillMaxHeight()) { leading() }
            PaneSplitter(onDrag = { delta -> paneWidthPrefs.setDrawer((drawerWidth + delta).coerceIn(DrawerWidthRange)) })
        } else {
            leading()
            VerticalDivider(thickness = DividerThickness)
        }

        Column(Modifier.weight(1f).fillMaxHeight()) {
            if (topStrip != null) {
                // Right of the drawer only, which is what sits under macOS's traffic lights.
                Box(Modifier.consumeWindowInsets(PaddingValues(top = LocalTitleBarOverlay.current))) { topStrip() }
            }
            Row(Modifier.weight(1f).fillMaxWidth()) {
                val center: @Composable () -> Unit = {
                    Box(Modifier.fillMaxSize().then(if (dock != null) Modifier.dockCenterZone(dock) else Modifier)) {
                        content()
                        if (dock != null) DockOverlayLayer(dock, layout, accountViewModel, panelNav)
                    }
                }
                if (deck != null) {
                    // The deck beside the main screen, the dock beyond it. Like the dock, its columns take back the
                    // window-controls inset that only the leading pane sits under.
                    DeckSplit(
                        Modifier.weight(1f).fillMaxHeight(),
                        { CenterPane(Modifier.unfocusDeckOnPress(), center) },
                        { Box(Modifier.consumeWindowInsets(PaddingValues(top = LocalTitleBarOverlay.current))) { deck(Modifier) } },
                    )
                } else {
                    // The content takes back the window-controls inset itself (see AppShellLayout).
                    CenterPane(Modifier.weight(1f), center)
                }

                // Only the leading pane sits under window controls drawn over the app (macOS's traffic
                // lights): the dock to the right takes that inset back, so its headers reach the
                // window's edge instead of growing a band under an empty title bar. The theme
                // background is the dock's only fill: its panels paint none of their own.
                if (dock != null && (!shownView.isEmpty || stripShown)) {
                    Row(
                        Modifier
                            .fillMaxHeight()
                            .background(MaterialTheme.colorScheme.background)
                            .consumeWindowInsets(PaddingValues(top = LocalTitleBarOverlay.current)),
                    ) {
                        // No Surface here (the Row paints the only fill): text and icons take the theme's
                        // color from here, or they fall back to black on the dark theme.
                        CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onBackground) {
                            if (!shownView.isEmpty) DockColumns(dock, shownView, columnWidths, accountViewModel, panelNav)
                            if (stripShown) {
                                VerticalDivider(thickness = DividerThickness)
                                DockStrip(dock, available, shownView, accountViewModel, onPinScreen = { dock.pinScreen(nav.currentRoute) })
                            }
                        }
                    }
                }
            }
        }
    }
}

/** How close to a dock column's edge a dragged panel must be to open a new column there. */
private val DockDropEdge = 40.dp

/** No dock (no logged-in shell provides one): an empty layout to collect. */
private val EmptyDockFlow = MutableStateFlow(DockLayout())

/** The main screen's bounds, for a panel dragged to its right edge (a first dock column). */
@Composable
private fun Modifier.dockCenterZone(dock: DockController): Modifier {
    DisposableEffect(dock) { onDispose { dock.drag.center = null } }
    return onGloballyPositioned { dock.drag.center = it.boundsInRoot() }
}

/** What the deck's workspace on screen asks of the dock beside it. */
private class DeckNeeds(
    /** It has a Notifications column, so the notification panel would repeat it. */
    val showsNotifications: Boolean,
    /** The width of its first column, which the dock leaves it whole. */
    val firstColumnWidth: Float,
)

@Composable
private fun deckNeeds(accountViewModel: AccountViewModel): DeckNeeds {
    val deck by accountViewModel.account.settings.deck
        .collectAsStateWithLifecycle()
    return remember(deck) {
        val columns = deck.current.columns
        DeckNeeds(
            showsNotifications = columns.any { it.root is Route.Notification },
            firstColumnWidth = columns.firstOrNull()?.width ?: DeckColumnsLayout.MIN_COLUMN_WIDTH,
        )
    }
}

/** The panels that step out of the dock on the current screen, the one each duplicates. */
@Composable
private fun panelsHiddenHere(
    nav: Nav,
    dock: DockController?,
): Set<PanelId> {
    if (dock == null) return emptySet()
    val hidden by remember(nav, dock) {
        derivedStateOf {
            val here = nav.currentRoute::class
            dock.panels
                .filter { here in it.hidesOn }
                .map { it.id }
                .toSet()
        }
    }
    return hidden
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
 * Whether the current screen wants the whole width ([FullWidthRoutes]): there the dock keeps only
 * its strip, and its panels open over the screen. Only asked where the dock has room.
 *
 * The current route is read here, through [derivedStateOf], rather than in the shells, so a
 * navigation recomposes only when it enters or leaves one of those screens.
 */
@Composable
private fun fullWidthHere(nav: Nav): Boolean {
    val wants by remember(nav) { derivedStateOf { nav.currentRoute::class in FullWidthRoutes } }
    return wants
}

/**
 * Screens that want the whole pane (the paper and the nSite/napplet browser, coding sessions): the
 * dock folds to its strip there. A new full-width screen is one more entry here, plus
 * `capWidth = false` where it is registered. A panel that duplicates a screen names it in its own
 * [DockPanelSpec.hidesOn] instead.
 */
val FullWidthRoutes: Set<KClass<out Route>> =
    setOf(
        Route.FrontPage::class,
        Route.NostrApp::class,
        Route.Browser::class,
        // Coding sessions want the width: the list beside the chat, and long diffs.
        Route.ClaudeCode::class,
        Route.ClaudeCodeChat::class,
        Route.ClaudeCodeChanges::class,
        Route.ClaudeCodePropose::class,
        Route.ClaudeCodePullRequests::class,
        Route.Devices::class,
    )

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
    topStrip: (@Composable () -> Unit)?,
    content: @Composable () -> Unit,
) {
    MultiPaneShell(
        accountViewModel = accountViewModel,
        nav = nav,
        resizableLeading = true,
        leading = drawer,
        deck = deck,
        topStrip = topStrip,
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
