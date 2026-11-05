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
package com.vitorpamplona.amethyst.desktop.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.KeyShortcut
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.window.FrameWindowScope
import androidx.compose.ui.window.MenuBar
import androidx.compose.ui.window.MenuScope
import androidx.compose.ui.window.WindowPlacement
import com.vitorpamplona.amethyst.commons.model.NavigationLayoutType
import com.vitorpamplona.amethyst.commons.model.navigation.NavBackStacks
import com.vitorpamplona.amethyst.commons.model.navigation.Route
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.deck_add_column
import com.vitorpamplona.amethyst.commons.resources.deck_close_column
import com.vitorpamplona.amethyst.commons.resources.deck_focus_column
import com.vitorpamplona.amethyst.commons.resources.deck_mode
import com.vitorpamplona.amethyst.commons.resources.deck_move_left
import com.vitorpamplona.amethyst.commons.resources.deck_move_right
import com.vitorpamplona.amethyst.commons.resources.deck_save_workspace
import com.vitorpamplona.amethyst.commons.resources.desktop_menu_help
import com.vitorpamplona.amethyst.commons.resources.desktop_menu_minimize
import com.vitorpamplona.amethyst.commons.resources.desktop_menu_report_issue
import com.vitorpamplona.amethyst.commons.resources.desktop_menu_website
import com.vitorpamplona.amethyst.commons.resources.desktop_menu_window
import com.vitorpamplona.amethyst.commons.resources.desktop_menu_zoom
import com.vitorpamplona.amethyst.commons.resources.navigation_close_tab
import com.vitorpamplona.amethyst.commons.resources.navigation_new_tab
import com.vitorpamplona.amethyst.commons.resources.navigation_next_tab
import com.vitorpamplona.amethyst.commons.resources.navigation_previous_tab
import com.vitorpamplona.amethyst.commons.resources.navigation_reopen_closed
import com.vitorpamplona.amethyst.commons.resources.navigation_window_menu_bring_all
import com.vitorpamplona.amethyst.commons.resources.navigation_window_menu_close_all
import com.vitorpamplona.amethyst.commons.resources.navigation_window_menu_reopen
import com.vitorpamplona.amethyst.commons.resources.navigation_window_menu_show_main
import com.vitorpamplona.amethyst.commons.resources.navigation_window_menu_to_tab
import com.vitorpamplona.amethyst.commons.ui.navigation.deck.DeckCommand
import com.vitorpamplona.amethyst.commons.ui.navigation.deck.DeckCommandBus
import com.vitorpamplona.amethyst.commons.ui.navigation.drawer.DrawerSectionsNavigateFirst
import com.vitorpamplona.amethyst.commons.ui.navigation.host.NavDestinations
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.Nav
import com.vitorpamplona.amethyst.commons.ui.navigation.tabs.routeTitle
import com.vitorpamplona.amethyst.commons.ui.settings.SettingsCategory
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.amethyst.desktop.platform.Platform
import com.vitorpamplona.amethyst.desktop.platform.PlatformInfo
import com.vitorpamplona.quartz.utils.Log
import java.awt.Desktop
import java.awt.desktop.QuitStrategy
import javax.swing.JFrame

/**
 * The window's navigator, once a logged-in shell exists: [DesktopAppRoot] sets it from inside the
 * navigation host, so the menu bar (which lives outside the shared app) can drive it.
 */
class DesktopNavigator {
    var nav by mutableStateOf<INav?>(null)
    var userPubKeyHex by mutableStateOf<String?>(null)

    /** What the menu bar mirrors the drawer from: the shell's account and the screens it has. */
    var accountViewModel by mutableStateOf<AccountViewModel?>(null)
    var destinations by mutableStateOf<NavDestinations?>(null)

    /** The Settings screen's categories, built inside the shell where its links and platform live. */
    var settingsCatalog by mutableStateOf<List<SettingsCategory>>(emptyList())

    // The last shortcut run and when it last fired, to tell a held key's auto-repeat from a press.
    internal var lastShortcut: Key? = null
    internal var lastShortcutAtMillis = 0L

    /** The open screens, once the shell runs: the tabs and screen windows the Window menu lists. */
    val stacks: NavBackStacks? get() = (nav as? Nav)?.stacks

    /** Whether a tab or screen window holds each screen, so Cmd/Ctrl+W and +T act on those, not on the deck. */
    val singleScreen: Boolean get() = stacks?.isSingleScreen == true

    /** The app's main window, raised by Show Main Window and when a section is asked for from a screen window. */
    var mainFrame: JFrame? = null

    /** False while the main window is closed with screen windows still open: the app keeps running. */
    var mainWindowVisible by mutableStateOf(true)

    /** Every open screen window, by its entry. */
    val screenFrames = mutableStateMapOf<Long, JFrame>()

    /** The screen window with the focus, if one has it: Cmd/Ctrl+W closes it. */
    var focusedScreenWindow by mutableStateOf<Long?>(null)

    /** Brings the main window back, and to the front. */
    fun showMainWindow() {
        mainWindowVisible = true
        mainFrame?.let {
            it.toFront()
            it.requestFocus()
        }
    }

    /** Raises every Amethyst window, the main one last so it lands on top. */
    fun bringAllToFront() {
        screenFrames.values.forEach { it.toFront() }
        showMainWindow()
    }

    /** Closes the focused screen window, or else the active tab. */
    fun closeCurrentScreen() {
        val stacks = stacks ?: return
        val window = focusedScreenWindow
        if (window != null && stacks.windows().any { it.id == window }) {
            stacks.close(window)
        } else if (stacks.mode == NavigationLayoutType.TABS) {
            stacks.close(stacks.top.id)
        }
    }

    fun go(route: Route) {
        nav?.nav(route)
    }

    /** A top-level destination: switches to it the way the bottom bar does, instead of stacking it. */
    fun goTop(route: Route) {
        nav?.navBottomBar(route)
    }

    /** A destination picked from the drawer, opened the way its drawer row opens it. */
    fun goDrawer(route: () -> Route) {
        nav?.run {
            closeDrawer()
            navDrawer { route() }
        }
    }
}

/**
 * What the menu bar and the keyboard shortcuts do: Cmd+key on macOS, Ctrl+key elsewhere. Everything
 * but quitting needs a logged-in shell.
 */
internal enum class Command(
    val key: Key,
    val shift: Boolean = false,
    val alt: Boolean = false,
    val needsLogin: Boolean = true,
    /** For the deck's focus commands, which column (0-based). */
    val column: Int = -1,
    /** Acts on tabs and screen windows; takes its keys from the deck's while each screen has its own. */
    val forScreens: Boolean = false,
    /** Ctrl on every platform, as Ctrl+Tab is everywhere (Cmd+Tab switches apps on macOS). */
    val ctrlEverywhere: Boolean = false,
) {
    NEW_POST(Key.N),
    SETTINGS(Key.Comma),
    QUIT(Key.Q, needsLogin = false),
    HOME(Key.One),
    MESSAGES(Key.Two),
    NOTIFICATIONS(Key.Three),
    SEARCH(Key.F),
    PROFILE(Key.P, shift = true),
    BOOKMARKS(Key.B, shift = true),
    DRAFTS(Key.D, shift = true),

    // The deck's, when it is on: they do nothing otherwise.
    ADD_COLUMN(Key.T),
    CLOSE_COLUMN(Key.W),

    // Page Up/Down as browsers move tabs: Shift+arrows select words in every text field.
    MOVE_COLUMN_LEFT(Key.PageUp, shift = true),
    MOVE_COLUMN_RIGHT(Key.PageDown, shift = true),
    SAVE_WORKSPACE(Key.S, shift = true),

    // The tabs and windows layouts': they take Cmd/Ctrl+T and +W from the deck while those run.
    NEW_TAB(Key.T, forScreens = true),
    CLOSE_TAB(Key.W, forScreens = true),
    REOPEN_CLOSED(Key.T, shift = true, forScreens = true),
    NEXT_TAB(Key.Tab, forScreens = true, ctrlEverywhere = true),
    PREVIOUS_TAB(Key.Tab, shift = true, forScreens = true, ctrlEverywhere = true),
    FOCUS_COLUMN_1(Key.One, alt = true, column = 0),
    FOCUS_COLUMN_2(Key.Two, alt = true, column = 1),
    FOCUS_COLUMN_3(Key.Three, alt = true, column = 2),
    FOCUS_COLUMN_4(Key.Four, alt = true, column = 3),
    FOCUS_COLUMN_5(Key.Five, alt = true, column = 4),
    FOCUS_COLUMN_6(Key.Six, alt = true, column = 5),
    FOCUS_COLUMN_7(Key.Seven, alt = true, column = 6),
    FOCUS_COLUMN_8(Key.Eight, alt = true, column = 7),
    FOCUS_COLUMN_9(Key.Nine, alt = true, column = 8),
    ;

    val shortcut: KeyShortcut
        get() = if (isMac && !ctrlEverywhere) KeyShortcut(key, meta = true, shift = shift, alt = alt) else KeyShortcut(key, ctrl = true, shift = shift, alt = alt)

    /** The deck's commands, which only apply while a deck is on screen. */
    val forDeck: Boolean get() = column >= 0 || this in DECK_COMMANDS
}

private val DECK_COMMANDS = setOf(Command.ADD_COLUMN, Command.CLOSE_COLUMN, Command.MOVE_COLUMN_LEFT, Command.MOVE_COLUMN_RIGHT, Command.SAVE_WORKSPACE)

// The real host, not the theming preview: the system menu bar and the Cmd key only exist on a Mac.
private val isMac = PlatformInfo.host == Platform.MACOS

private fun DesktopNavigator.run(
    command: Command,
    onQuit: () -> Unit,
) {
    when (command) {
        Command.NEW_POST -> go(Route.NewShortNote())
        Command.SETTINGS -> go(Route.AllSettings)
        Command.QUIT -> onQuit()
        Command.HOME -> goTop(Route.Home)
        Command.MESSAGES -> goTop(Route.Message)
        Command.NOTIFICATIONS -> goTop(Route.Notification())
        Command.SEARCH -> goTop(Route.Search())
        Command.PROFILE -> userPubKeyHex?.let { go(Route.Profile(it)) }
        Command.BOOKMARKS -> go(Route.Bookmarks)
        Command.DRAFTS -> go(Route.Drafts)
        Command.ADD_COLUMN -> DeckCommandBus.send(DeckCommand.AddColumn)
        Command.CLOSE_COLUMN -> DeckCommandBus.send(DeckCommand.CloseColumn)
        Command.MOVE_COLUMN_LEFT -> DeckCommandBus.send(DeckCommand.MoveColumn(-1))
        Command.MOVE_COLUMN_RIGHT -> DeckCommandBus.send(DeckCommand.MoveColumn(1))
        Command.SAVE_WORKSPACE -> DeckCommandBus.send(DeckCommand.SaveWorkspace)
        Command.FOCUS_COLUMN_1, Command.FOCUS_COLUMN_2, Command.FOCUS_COLUMN_3,
        Command.FOCUS_COLUMN_4, Command.FOCUS_COLUMN_5, Command.FOCUS_COLUMN_6,
        Command.FOCUS_COLUMN_7, Command.FOCUS_COLUMN_8, Command.FOCUS_COLUMN_9,
        -> DeckCommandBus.send(DeckCommand.FocusColumn(command.column))
        Command.NEW_TAB -> stacks?.open(Route.Search(), forceNew = true)
        Command.CLOSE_TAB -> closeCurrentScreen()
        Command.REOPEN_CLOSED -> stacks?.reopenClosed()
        Command.NEXT_TAB -> stacks?.cycle(1)
        Command.PREVIOUS_TAB -> stacks?.cycle(-1)
    }
}

/**
 * The window's keyboard shortcuts where there is no menu bar to carry them (Linux, Windows): pass it
 * to the window's `onPreviewKeyEvent`. On macOS the menu bar handles them and this does nothing.
 */
fun DesktopNavigator.handleShortcut(
    event: KeyEvent,
    onQuit: () -> Unit,
): Boolean {
    if (isMac || event.type != KeyEventType.KeyDown) return false
    if (!event.isCtrlPressed || event.isMetaPressed) return false
    val matching = Command.entries.filter { it.key == event.key && it.shift == event.isShiftPressed && it.alt == event.isAltPressed }
    // Cmd/Ctrl+T and +W belong to the tabs and windows while each screen has its own, to the deck otherwise.
    val command = (if (singleScreen) matching.firstOrNull { it.forScreens } else null) ?: matching.firstOrNull { !it.forScreens } ?: return false
    if (command.needsLogin && nav == null) return false
    // With no deck showing, the keys stay with whatever has focus (Ctrl+W in a text field, say).
    if (command.forDeck && !DeckCommandBus.active) return false
    // Holding the chord repeats its key-down every few tens of ms: one press, one command (one
    // New Post, not a stack of them). Timed rather than tracked to the key-up, which a focus
    // change can swallow.
    val now = System.currentTimeMillis()
    val repeat = event.key == lastShortcut && now - lastShortcutAtMillis < AUTO_REPEAT_WINDOW_MS
    lastShortcut = event.key
    lastShortcutAtMillis = now
    if (!repeat) run(command, onQuit)
    return true
}

/**
 * The macOS menu bar, at the top of the screen. Linux and Windows get none: Swing would draw it
 * inside the window, in a look neither OS uses (GNOME apps have no menu bar), and everything in it
 * is in the drawer; their shortcuts go through [handleShortcut].
 *
 * Once logged in, each drawer section is a menu ([drawerMenus]), with the settings categories as
 * submenus of System. Settings… and Quit are the app menu's own (see [installAppMenuHandlers]).
 */
@Composable
fun FrameWindowScope.DesktopMenuBar(
    navigator: DesktopNavigator,
    onQuit: () -> Unit,
    /** The main window installs the app menu's handlers; a screen window's menu bar leaves them be. */
    installAppMenu: Boolean = true,
) {
    if (!isMac) return

    if (installAppMenu) {
        DisposableEffect(navigator) {
            installAppMenuHandlers(navigator, onQuit)
            onDispose { removeAppMenuHandlers() }
        }
    }

    val accountViewModel = navigator.accountViewModel
    val drawer =
        if (accountViewModel != null) {
            val hidden by remember(accountViewModel) { accountViewModel.hiddenDrawerItemsFlow() }.collectAsState()
            val destinations = navigator.destinations
            val settings = navigator.settingsCatalog
            remember(accountViewModel, hidden, destinations, settings, navigator) {
                drawerMenus(
                    sections = DrawerSectionsNavigateFirst,
                    hidden = hidden,
                    settings = settings,
                    canOpen = { route -> destinations?.has(route) ?: true },
                    resolveRoute = { def -> def.resolveRoute(accountViewModel) },
                    actions = navigator.menuActions(onQuit),
                )
            }
        } else {
            emptyList()
        }

    val deckListeners by DeckCommandBus.listeners.collectAsState()
    val deckEnabled = navigator.nav != null && deckListeners > 0

    val singleScreen = navigator.singleScreen

    // While each screen has its own tab or window, Cmd+T and Cmd+W are theirs, not the deck's.
    @Composable
    fun MenuScope.CommandItem(
        label: String,
        command: Command,
    ) = Item(
        label,
        enabled = deckEnabled || !command.forDeck,
        shortcut = command.shortcut.takeUnless { command.forDeck && singleScreen && Command.entries.any { it.forScreens && it.key == command.key && it.shift == command.shift } },
    ) { navigator.run(command, onQuit) }

    MenuBar {
        drawer.forEach { menu -> Menu(stringRes(menu.label)) { MenuEntries(menu.entries) } }
        if (drawer.isNotEmpty()) {
            Menu(stringRes(Res.string.deck_mode)) {
                CommandItem(stringRes(Res.string.deck_add_column), Command.ADD_COLUMN)
                CommandItem(stringRes(Res.string.deck_close_column), Command.CLOSE_COLUMN)
                CommandItem(stringRes(Res.string.deck_move_left), Command.MOVE_COLUMN_LEFT)
                CommandItem(stringRes(Res.string.deck_move_right), Command.MOVE_COLUMN_RIGHT)
                Separator()
                CommandItem(stringRes(Res.string.deck_save_workspace), Command.SAVE_WORKSPACE)
                Separator()
                Command.entries.filter { it.column >= 0 }.forEach { command ->
                    CommandItem(stringRes(Res.string.deck_focus_column, command.column + 1), command)
                }
            }
        }
        Menu(stringRes(Res.string.desktop_menu_window)) {
            Item(stringRes(Res.string.desktop_menu_minimize), shortcut = KeyShortcut(Key.M, meta = true)) { window.isMinimized = true }
            Item(stringRes(Res.string.desktop_menu_zoom)) {
                window.placement = if (window.placement == WindowPlacement.Maximized) WindowPlacement.Floating else WindowPlacement.Maximized
            }
            if (singleScreen) ScreenWindowItems(navigator, onQuit)
        }
        Menu(stringRes(Res.string.desktop_menu_help)) {
            Item(stringRes(Res.string.desktop_menu_website)) { DesktopBrowser.open(PROJECT_URL) }
            Item(stringRes(Res.string.desktop_menu_report_issue)) { DesktopBrowser.open("$PROJECT_URL/issues") }
        }
    }
}

/** Renders a menu's [entries], submenus included. */
@Composable
private fun MenuScope.MenuEntries(entries: List<MenuEntry>) {
    entries.forEach { entry ->
        when (entry) {
            is MenuEntry.Action -> Item(stringRes(entry.label), shortcut = entry.command?.shortcut, onClick = entry.onClick)
            is MenuEntry.Submenu -> Menu(stringRes(entry.label)) { MenuEntries(entry.entries) }
            MenuEntry.Separator -> Separator()
        }
    }
}

private fun DesktopNavigator.menuActions(onQuit: () -> Unit) =
    object : DrawerMenuActions {
        override fun openItem(route: () -> Route) = goDrawer(route)

        override fun run(command: Command) = this@menuActions.run(command, onQuit)

        override fun open(route: Route) = go(route)
    }

/**
 * The app menu (the one under the app's name) is AppKit's, not Swing's: its Settings… item stays
 * greyed out until a handler is set, and its Quit (Cmd+Q, and the dock's Quit) ends the JVM with
 * `System.exit` unless told otherwise. Closing the windows instead runs the window's
 * `onCloseRequest`, which quits the way the updater does.
 */
private fun installAppMenuHandlers(
    navigator: DesktopNavigator,
    onQuit: () -> Unit,
) {
    try {
        if (!Desktop.isDesktopSupported()) return
        val desktop = Desktop.getDesktop()
        if (desktop.isSupported(Desktop.Action.APP_PREFERENCES)) {
            desktop.setPreferencesHandler { navigator.run(Command.SETTINGS, onQuit) }
        }
        if (desktop.isSupported(Desktop.Action.APP_QUIT_STRATEGY)) {
            desktop.setQuitStrategy(QuitStrategy.CLOSE_ALL_WINDOWS)
        }
    } catch (e: Exception) {
        Log.w("DesktopMenuBar", "Could not set up the app menu", e)
    }
}

private fun removeAppMenuHandlers() {
    try {
        if (!Desktop.isDesktopSupported()) return
        val desktop = Desktop.getDesktop()
        if (desktop.isSupported(Desktop.Action.APP_PREFERENCES)) desktop.setPreferencesHandler(null)
    } catch (e: Exception) {
        Log.w("DesktopMenuBar", "Could not reset the app menu", e)
    }
}

private const val AUTO_REPEAT_WINDOW_MS = 700L

private const val PROJECT_URL = "https://github.com/vitorpamplona/amethyst"

/**
 * The Window menu's part for the tabs and windows layouts: the tab and window commands, the main
 * window, and every open screen window, a click raising it.
 */
@Composable
private fun MenuScope.ScreenWindowItems(
    navigator: DesktopNavigator,
    onQuit: () -> Unit,
) {
    val stacks = navigator.stacks ?: return
    val accountViewModel = navigator.accountViewModel ?: return
    val tabs = stacks.mode == NavigationLayoutType.TABS
    Separator()
    Item(stringRes(Res.string.navigation_new_tab), shortcut = Command.NEW_TAB.shortcut) { navigator.run(Command.NEW_TAB, onQuit) }
    Item(stringRes(Res.string.navigation_close_tab), shortcut = Command.CLOSE_TAB.shortcut) { navigator.run(Command.CLOSE_TAB, onQuit) }
    Item(
        stringRes(if (tabs) Res.string.navigation_reopen_closed else Res.string.navigation_window_menu_reopen),
        enabled = stacks.canReopen,
        shortcut = Command.REOPEN_CLOSED.shortcut,
    ) { navigator.run(Command.REOPEN_CLOSED, onQuit) }
    if (tabs) {
        Item(stringRes(Res.string.navigation_next_tab), shortcut = Command.NEXT_TAB.shortcut) { navigator.run(Command.NEXT_TAB, onQuit) }
        Item(stringRes(Res.string.navigation_previous_tab), shortcut = Command.PREVIOUS_TAB.shortcut) { navigator.run(Command.PREVIOUS_TAB, onQuit) }
    }
    val focused = navigator.focusedScreenWindow
    if (focused != null && stacks.windows().any { it.id == focused && it.detached }) {
        Item(stringRes(Res.string.navigation_window_menu_to_tab)) { stacks.setDetached(focused, false) }
    }
    Separator()
    Item(stringRes(Res.string.navigation_window_menu_show_main)) { navigator.showMainWindow() }
    Item(stringRes(Res.string.navigation_window_menu_bring_all)) { navigator.bringAllToFront() }
    val windows = stacks.windows()
    if (windows.isNotEmpty()) {
        Item(stringRes(Res.string.navigation_window_menu_close_all)) { windows.forEach { stacks.close(it.id) } }
        Separator()
        windows.forEach { entry ->
            Item(routeTitle(entry.route, accountViewModel)) { stacks.activate(entry.id) }
        }
    }
}
