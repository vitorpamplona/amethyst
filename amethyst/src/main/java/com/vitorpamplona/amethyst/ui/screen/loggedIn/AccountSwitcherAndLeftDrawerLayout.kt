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
package com.vitorpamplona.amethyst.ui.screen.loggedIn

import android.content.res.Configuration
import androidx.activity.compose.BackHandler
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.SheetValue
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalConfiguration
import com.vitorpamplona.amethyst.ui.navigation.AccountSwitchBottomSheet
import com.vitorpamplona.amethyst.ui.navigation.DrawerContent
import com.vitorpamplona.amethyst.ui.navigation.INav
import com.vitorpamplona.amethyst.ui.screen.AccountStateViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountSwitcherAndLeftDrawerLayout(
    accountViewModel: AccountViewModel,
    accountStateViewModel: AccountStateViewModel,
    nav: INav,
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

    val orientation = LocalConfiguration.current.orientation
    val currentDrawerState = nav.drawerState.currentValue
    LaunchedEffect(key1 = orientation) {
        if (
            orientation == Configuration.ORIENTATION_LANDSCAPE && currentDrawerState == DrawerValue.Closed
        ) {
            nav.drawerState.close()
        }
    }

    ModalNavigationDrawer(
        drawerState = nav.drawerState,
        drawerContent = {
            DrawerContent(nav, openSheetFunction, accountViewModel)
            BackHandler(enabled = nav.drawerState.isOpen, nav::closeDrawer)
        },
        content = content,
    )

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
                accountStateViewModel = accountStateViewModel,
            )
        }
    }
}
