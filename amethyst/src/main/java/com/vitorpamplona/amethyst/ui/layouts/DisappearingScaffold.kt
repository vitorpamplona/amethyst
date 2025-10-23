/**
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
package com.vitorpamplona.amethyst.ui.layouts

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.imePadding
import androidx.compose.material3.BottomAppBarDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.theme.DividerThickness
import com.vitorpamplona.myapplication.DisappearingBottomBar
import com.vitorpamplona.myapplication.DisappearingFloatingButton
import com.vitorpamplona.myapplication.DisappearingTopBar
import com.vitorpamplona.myapplication.enterAlwaysScrollBehavior

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DisappearingScaffold(
    isInvertedLayout: Boolean,
    topBar: (@Composable () -> Unit)? = null,
    bottomBar: (@Composable () -> Unit)? = null,
    floatingButton: (@Composable () -> Unit)? = null,
    accountViewModel: AccountViewModel,
    isActive: () -> Boolean = { true },
    mainContent: @Composable (padding: PaddingValues) -> Unit,
) {
    val topBehavior =
        enterAlwaysScrollBehavior(
            canScroll = {
                topBar != null && isActive() && accountViewModel.settings.isImmersiveScrollingActive()
            },
            reverseLayout = isInvertedLayout,
        )
    val bottomBehavior =
        BottomAppBarDefaults.exitAlwaysScrollBehavior(
            canScroll = {
                (bottomBar != null || floatingButton != null) && isActive() && accountViewModel.settings.isImmersiveScrollingActive()
            },
        )

    Scaffold(
        modifier =
            Modifier
                .imePadding()
                .nestedScroll(topBehavior.nestedScrollConnection)
                .nestedScroll(bottomBehavior.nestedScrollConnection),
        bottomBar = {
            bottomBar?.let {
                DisappearingBottomBar(bottomBehavior) {
                    it()
                }
            }
        },
        topBar = {
            topBar?.let {
                DisappearingTopBar(topBehavior) {
                    Column {
                        it()
                        HorizontalDivider(thickness = DividerThickness)
                    }
                }
            }
        },
        floatingActionButton = {
            floatingButton?.let {
                DisappearingFloatingButton(bottomBehavior) {
                    it()
                }
            }
        },
        content = mainContent,
    )
}
