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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.rooms

import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.vitorpamplona.amethyst.ui.components.getActivity
import com.vitorpamplona.amethyst.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.rooms.singlepane.MessagesSinglePane
import com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.rooms.twopane.MessagesTwoPane

@OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
@Composable
fun MessagesScreen(
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val act = LocalContext.current.getActivity()
    val windowSizeClass = calculateWindowSizeClass(act)

    val twoPane by remember(windowSizeClass.widthSizeClass) {
        derivedStateOf {
            when (windowSizeClass.widthSizeClass) {
                WindowWidthSizeClass.Compact -> false
                WindowWidthSizeClass.Expanded,
                WindowWidthSizeClass.Medium,
                -> true
                else -> false
            }
        }
    }

    if (twoPane) {
        MessagesTwoPane(
            knownFeedContentState = accountViewModel.feedStates.dmKnown,
            newFeedContentState = accountViewModel.feedStates.dmNew,
            widthSizeClass = windowSizeClass.widthSizeClass,
            accountViewModel = accountViewModel,
            nav = nav,
        )
    } else {
        MessagesSinglePane(
            knownFeedContentState = accountViewModel.feedStates.dmKnown,
            newFeedContentState = accountViewModel.feedStates.dmNew,
            accountViewModel = accountViewModel,
            nav = nav,
        )
    }
}
