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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
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
import com.vitorpamplona.amethyst.commons.model.navigation.Route
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.bookmarks
import com.vitorpamplona.amethyst.commons.resources.deck_add_column
import com.vitorpamplona.amethyst.commons.resources.deck_close_column
import com.vitorpamplona.amethyst.commons.resources.deck_focus_column
import com.vitorpamplona.amethyst.commons.resources.deck_mode
import com.vitorpamplona.amethyst.commons.resources.deck_move_left
import com.vitorpamplona.amethyst.commons.resources.deck_move_right
import com.vitorpamplona.amethyst.commons.resources.deck_save_workspace
import com.vitorpamplona.amethyst.commons.resources.desktop_menu_about
import com.vitorpamplona.amethyst.commons.resources.desktop_menu_file
import com.vitorpamplona.amethyst.commons.resources.desktop_menu_go
import com.vitorpamplona.amethyst.commons.resources.desktop_menu_help
import com.vitorpamplona.amethyst.commons.resources.desktop_menu_home
import com.vitorpamplona.amethyst.commons.resources.desktop_menu_messages
import com.vitorpamplona.amethyst.commons.resources.desktop_menu_notifications
import com.vitorpamplona.amethyst.commons.resources.desktop_menu_quit
import com.vitorpamplona.amethyst.commons.resources.desktop_menu_report_issue
import com.vitorpamplona.amethyst.commons.resources.drafts
import com.vitorpamplona.amethyst.commons.resources.new_post
import com.vitorpamplona.amethyst.commons.resources.profile
import com.vitorpamplona.amethyst.commons.resources.screen_search_title
import com.vitorpamplona.amethyst.commons.resources.settings
import com.vitorpamplona.amethyst.commons.ui.navigation.deck.DeckCommand
import com.vitorpamplona.amethyst.commons.ui.navigation.deck.DeckCommandBus
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.desktop.platform.Platform
import com.vitorpamplona.amethyst.desktop.platform.PlatformInfo

/**
 * The window's navigator, once a logged-in shell exists: [DesktopAppRoot] sets it from inside the
 * navigation host, so the menu bar (which lives outside the shared app) can drive it.
 */
class DesktopNavigator {
    var nav by mutableStateOf<INav?>(null)
    var userPubKeyHex by mutableStateOf<String?>(null)

    // The last shortcut run and when it last fired, to tell a held key's auto-repeat from a press.
    internal var lastShortcut: Key? = null
    internal var lastShortcutAtMillis = 0L

    fun go(route: Route) {
        nav?.nav(route)
    }

    /** A top-level destination: switches to it the way the bottom bar does, instead of stacking it. */
    fun goTop(route: Route) {
        nav?.navBottomBar(route)
    }
}

/**
 * What the menu bar and the keyboard shortcuts do: Cmd+key on macOS, Ctrl+key elsewhere. Everything
 * but quitting needs a logged-in shell.
 */
private enum class Command(
    val key: Key,
    val shift: Boolean = false,
    val alt: Boolean = false,
    val needsLogin: Boolean = true,
    /** For the deck's focus commands, which column (0-based). */
    val column: Int = -1,
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
        get() = if (isMac) KeyShortcut(key, meta = true, shift = shift, alt = alt) else KeyShortcut(key, ctrl = true, shift = shift, alt = alt)

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
    val command = Command.entries.firstOrNull { it.key == event.key && it.shift == event.isShiftPressed && it.alt == event.isAltPressed } ?: return false
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
 */
@Composable
fun FrameWindowScope.DesktopMenuBar(
    navigator: DesktopNavigator,
    onQuit: () -> Unit,
) {
    if (!isMac) return

    val loggedIn = navigator.nav != null

    @Composable
    fun MenuScope.CommandItem(
        label: String,
        command: Command,
    ) = Item(label, enabled = loggedIn || !command.needsLogin, shortcut = command.shortcut) { navigator.run(command, onQuit) }

    MenuBar {
        Menu(stringRes(Res.string.desktop_menu_file)) {
            CommandItem(stringRes(Res.string.new_post), Command.NEW_POST)
            CommandItem(stringRes(Res.string.settings), Command.SETTINGS)
            Separator()
            CommandItem(stringRes(Res.string.desktop_menu_quit), Command.QUIT)
        }
        Menu(stringRes(Res.string.desktop_menu_go)) {
            CommandItem(stringRes(Res.string.desktop_menu_home), Command.HOME)
            CommandItem(stringRes(Res.string.desktop_menu_messages), Command.MESSAGES)
            CommandItem(stringRes(Res.string.desktop_menu_notifications), Command.NOTIFICATIONS)
            CommandItem(stringRes(Res.string.screen_search_title), Command.SEARCH)
            Separator()
            CommandItem(stringRes(Res.string.profile), Command.PROFILE)
            CommandItem(stringRes(Res.string.bookmarks), Command.BOOKMARKS)
            CommandItem(stringRes(Res.string.drafts), Command.DRAFTS)
        }
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
        Menu(stringRes(Res.string.desktop_menu_help)) {
            Item(stringRes(Res.string.desktop_menu_about)) { DesktopBrowser.open(PROJECT_URL) }
            Item(stringRes(Res.string.desktop_menu_report_issue)) { DesktopBrowser.open("$PROJECT_URL/issues") }
        }
    }
}

private const val AUTO_REPEAT_WINDOW_MS = 700L

private const val PROJECT_URL = "https://github.com/vitorpamplona/amethyst"
