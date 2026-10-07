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

import androidx.compose.runtime.Composable
import com.vitorpamplona.amethyst.commons.ui.navigation.drawer.DrawerContent
import com.vitorpamplona.amethyst.commons.ui.navigation.drawer.PermanentDrawerContent
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.Nav
import com.vitorpamplona.amethyst.commons.ui.navigation.shell.AppShellLayout
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.amethyst.ui.navigation.drawer.AccountSwitchBottomSheet
import com.vitorpamplona.amethyst.ui.screen.AccountSessionManager
import com.vitorpamplona.amethyst.ui.screen.loggedIn.embed.EmbeddedSelectionDrag

/**
 * Android's fill of the shared [AppShellLayout]: the app's drawer contents and account switcher,
 * and the left-edge swipe suspended while an embedded surface's selection handle is dragged.
 */
@Composable
fun AccountSwitcherAndLeftDrawerLayout(
    accountViewModel: AccountViewModel,
    accountSessionManager: AccountSessionManager,
    nav: Nav,
    content: @Composable () -> Unit,
) {
    AppShellLayout(
        accountViewModel = accountViewModel,
        nav = nav,
        drawerContent = { openAccountSwitcher -> DrawerContent(nav, openAccountSwitcher, accountViewModel) },
        permanentDrawerContent = { openAccountSwitcher -> PermanentDrawerContent(nav, openAccountSwitcher, accountViewModel) },
        accountSwitcherContent = { AccountSwitchBottomSheet(accountViewModel, accountSessionManager) },
        suspendEdgeSwipe = { EmbeddedSelectionDrag.dragging },
        content = content,
    )
}
