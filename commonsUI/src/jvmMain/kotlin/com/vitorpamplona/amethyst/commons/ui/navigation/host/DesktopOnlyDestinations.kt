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
package com.vitorpamplona.amethyst.commons.ui.navigation.host

import com.vitorpamplona.amethyst.commons.model.navigation.Route
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.claudecode.ClaudeCodeChangesScreen
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.claudecode.ClaudeCodeChatScreen
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.claudecode.ClaudeCodeProposeScreen
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.claudecode.ClaudeCodeSessionsScreen
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.claudecode.ClaudeCodeStartScreen
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel

/**
 * Shared screens only a desktop computer can back: Claude Code runs the user's installed `claude`
 * program, which an Android app can't start. The desktop app registers these next to
 * [jvmDestinations]; elsewhere every way in (the drawer row, "Work on this with Claude" on the git
 * screens) hides, since [canOpen] finds no screen for the route.
 */
fun NavDestinations.desktopOnlyDestinations(accountViewModel: AccountViewModel) {
    composableFromEnd<Route.ClaudeCode> { ClaudeCodeSessionsScreen(nav) }
    composableFromEndArgs<Route.ClaudeCodeChat>(capWidth = false) { ClaudeCodeChatScreen(it.key, accountViewModel, nav) }
    composableFromEndArgs<Route.ClaudeCodeChanges>(capWidth = false) { ClaudeCodeChangesScreen(it.key, nav) }
    composableFromEndArgs<Route.ClaudeCodePropose>(capWidth = false) { ClaudeCodeProposeScreen(it.key, accountViewModel, nav) }
    composableFromEndArgs<Route.ClaudeCodeStart>(capWidth = false) { ClaudeCodeStartScreen(it, accountViewModel, nav) }
}
