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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.layout.AnimatedPane
import androidx.compose.material3.adaptive.layout.ListDetailPaneScaffoldRole
import androidx.compose.material3.adaptive.navigation.NavigableListDetailPaneScaffold
import androidx.compose.material3.adaptive.navigation.rememberListDetailPaneScaffoldNavigator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.vitorpamplona.amethyst.Amethyst
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.ui.layouts.isCompactWindow
import com.vitorpamplona.amethyst.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.ui.navigation.routes.Route
import com.vitorpamplona.amethyst.ui.navigation.topbars.TopBarWithBackButton
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.stringRes
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@Composable
fun AdaptiveSettingsScreen(
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    if (isCompactWindow()) {
        AllSettingsScreen(accountViewModel, nav)
    } else {
        AdaptiveSettingsListDetail(accountViewModel, nav)
    }
}

@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@Composable
private fun AdaptiveSettingsListDetail(
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val navigator = rememberListDetailPaneScaffoldNavigator<Route>()
    val scope = rememberCoroutineScope()

    BackHandler(navigator.canNavigateBack()) {
        scope.launch {
            navigator.navigateBack()
        }
    }

    Scaffold(
        topBar = {
            TopBarWithBackButton(stringRes(id = R.string.settings), nav::popBack)
        },
    ) { padding ->
        NavigableListDetailPaneScaffold(
            navigator = navigator,
            listPane = {
                AnimatedPane {
                    AllSettingsContent(
                        accountViewModel = accountViewModel,
                        onSettingsNav = { route ->
                            scope.launch {
                                navigator.navigateTo(ListDetailPaneScaffoldRole.Detail, route)
                            }
                        },
                    )
                }
            },
            detailPane = {
                AnimatedPane {
                    val selectedRoute = navigator.currentDestination?.contentKey
                    if (selectedRoute != null) {
                        SettingsDetailContent(
                            route = selectedRoute,
                            accountViewModel = accountViewModel,
                            nav = nav,
                        )
                    } else {
                        SettingsDetailPlaceholder()
                    }
                }
            },
            modifier = Modifier.padding(padding),
        )
    }
}

@Composable
private fun SettingsDetailContent(
    route: Route,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    when (route) {
        is Route.EditRelays -> {
            com.vitorpamplona.amethyst.ui.screen.loggedIn.relays
                .AllRelayListScreen(accountViewModel, nav)
        }

        is Route.EditMediaServers -> {
            com.vitorpamplona.amethyst.ui.actions.mediaServers
                .AllMediaServersScreen(accountViewModel, nav)
        }

        is Route.UpdateReactionType -> {
            com.vitorpamplona.amethyst.ui.note
                .UpdateReactionTypeScreen(accountViewModel, nav)
        }

        is Route.UpdateZapAmount -> {
            UpdateZapAmountScreen(accountViewModel, nav, route.nip47)
        }

        is Route.SecurityFilters -> {
            SecurityFiltersScreen(accountViewModel, nav)
        }

        is Route.UserSettings -> {
            UserSettingsScreen(accountViewModel, nav)
        }

        is Route.AccountBackup -> {
            com.vitorpamplona.amethyst.ui.screen.loggedIn.keyBackup
                .AccountBackupScreen(accountViewModel, nav)
        }

        is Route.PrivacyOptions -> {
            com.vitorpamplona.amethyst.ui.screen.loggedIn.privacy
                .PrivacyOptionsScreen(Amethyst.instance.torPrefs.value, nav)
        }

        is Route.NamecoinSettings -> {
            NamecoinSettingsScreen(Amethyst.instance.namecoinPrefs, nav)
        }

        is Route.Settings -> {
            SettingsScreen(accountViewModel, nav)
        }

        is Route.ReactionsSettings -> {
            ReactionsSettingsScreen(accountViewModel, nav)
        }

        else -> {
            SettingsDetailPlaceholder()
        }
    }
}

@Composable
private fun SettingsDetailPlaceholder() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = stringRes(R.string.settings),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
