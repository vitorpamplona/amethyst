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
import androidx.compose.ui.input.key.KeyShortcut
import androidx.compose.ui.window.FrameWindowScope
import androidx.compose.ui.window.MenuBar
import com.vitorpamplona.amethyst.commons.model.navigation.Route
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.bookmarks
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
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.stringRes

/**
 * The window's navigator, once a logged-in shell exists: [DesktopAppRoot] sets it from inside the
 * navigation host, so the menu bar (which lives outside the shared app) can drive it.
 */
class DesktopNavigator {
    var nav by mutableStateOf<INav?>(null)
    var userPubKeyHex by mutableStateOf<String?>(null)

    fun go(route: Route) {
        nav?.nav(route)
    }

    /** A top-level destination: switches to it the way the bottom bar does, instead of stacking it. */
    fun goTop(route: Route) {
        nav?.navBottomBar(route)
    }
}

private val isMac =
    System
        .getProperty("os.name")
        .orEmpty()
        .lowercase()
        .contains("mac")

private fun shortcut(
    key: Key,
    shift: Boolean = false,
) = if (isMac) KeyShortcut(key, meta = true, shift = shift) else KeyShortcut(key, ctrl = true, shift = shift)

/** The desktop menu bar: the system one on macOS, the window's own elsewhere. */
@Composable
fun FrameWindowScope.DesktopMenuBar(
    navigator: DesktopNavigator,
    onQuit: () -> Unit,
) {
    val loggedIn = navigator.nav != null

    MenuBar {
        Menu(stringRes(Res.string.desktop_menu_file)) {
            Item(stringRes(Res.string.new_post), enabled = loggedIn, shortcut = shortcut(Key.N)) { navigator.go(Route.NewShortNote()) }
            Item(stringRes(Res.string.settings), enabled = loggedIn, shortcut = shortcut(Key.Comma)) { navigator.go(Route.AllSettings) }
            Separator()
            Item(stringRes(Res.string.desktop_menu_quit), shortcut = shortcut(Key.Q), onClick = onQuit)
        }
        Menu(stringRes(Res.string.desktop_menu_go)) {
            Item(stringRes(Res.string.desktop_menu_home), enabled = loggedIn, shortcut = shortcut(Key.One)) { navigator.goTop(Route.Home) }
            Item(stringRes(Res.string.desktop_menu_messages), enabled = loggedIn, shortcut = shortcut(Key.Two)) { navigator.goTop(Route.Message) }
            Item(stringRes(Res.string.desktop_menu_notifications), enabled = loggedIn, shortcut = shortcut(Key.Three)) { navigator.goTop(Route.Notification()) }
            Item(stringRes(Res.string.screen_search_title), enabled = loggedIn, shortcut = shortcut(Key.F)) { navigator.goTop(Route.Search()) }
            Separator()
            Item(stringRes(Res.string.profile), enabled = loggedIn, shortcut = shortcut(Key.P, shift = true)) {
                navigator.userPubKeyHex?.let { navigator.go(Route.Profile(it)) }
            }
            Item(stringRes(Res.string.bookmarks), enabled = loggedIn, shortcut = shortcut(Key.B, shift = true)) { navigator.go(Route.Bookmarks) }
            Item(stringRes(Res.string.drafts), enabled = loggedIn, shortcut = shortcut(Key.D, shift = true)) { navigator.go(Route.Drafts) }
        }
        Menu(stringRes(Res.string.desktop_menu_help)) {
            Item(stringRes(Res.string.desktop_menu_about)) { DesktopBrowser.open(PROJECT_URL) }
            Item(stringRes(Res.string.desktop_menu_report_issue)) { DesktopBrowser.open("$PROJECT_URL/issues") }
        }
    }
}

private const val PROJECT_URL = "https://github.com/vitorpamplona/amethyst"
