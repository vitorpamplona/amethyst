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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.nests

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.ui.feeds.FeedContentState
import com.vitorpamplona.amethyst.ui.feeds.RefresheableBox
import com.vitorpamplona.amethyst.ui.feeds.RenderFeedContentState
import com.vitorpamplona.amethyst.ui.feeds.SaveableFeedContentState
import com.vitorpamplona.amethyst.ui.feeds.ScrollStateKeys
import com.vitorpamplona.amethyst.ui.feeds.WatchLifecycleAndUpdateModel
import com.vitorpamplona.amethyst.ui.layouts.DisappearingScaffold
import com.vitorpamplona.amethyst.ui.navigation.bottombars.AppBottomBar
import com.vitorpamplona.amethyst.ui.navigation.bottombars.FabBottomBarPadded
import com.vitorpamplona.amethyst.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.ui.navigation.routes.Route
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.screen.loggedIn.nests.create.CreateNestSheet
import com.vitorpamplona.amethyst.ui.screen.loggedIn.nests.create.CreateNestViewModel
import com.vitorpamplona.amethyst.ui.screen.loggedIn.nests.datasource.NestsFilterAssemblerSubscription
import com.vitorpamplona.amethyst.ui.stringRes

@Composable
fun NestsScreen(
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    NestsScreen(
        nestsFeedContentState = accountViewModel.feedStates.nestsFeed,
        accountViewModel = accountViewModel,
        nav = nav,
    )
}

@Composable
fun NestsScreen(
    nestsFeedContentState: FeedContentState,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    WatchLifecycleAndUpdateModel(nestsFeedContentState)
    WatchAccountForNestsScreen(nestsFeedState = nestsFeedContentState, accountViewModel = accountViewModel)
    NestsFilterAssemblerSubscription(accountViewModel)

    val nestsServers by accountViewModel.account.nestsServers.flow
        .collectAsStateWithLifecycle()
    var showCreateSheet by remember { mutableStateOf(false) }
    var showSetupDialog by remember { mutableStateOf(false) }

    DisappearingScaffold(
        isInvertedLayout = false,
        topBar = {
            NestsTopBar(accountViewModel, nav)
        },
        bottomBar = {
            AppBottomBar(Route.Nests, nav, accountViewModel) { route ->
                if (route == Route.Nests) {
                    nestsFeedContentState.sendToTop()
                } else {
                    nav.navBottomBar(route)
                }
            }
        },
        floatingButton = {
            FabBottomBarPadded(nav) {
                FloatingActionButton(
                    onClick = {
                        if (nestsServers.any { it.startsWith("http") }) {
                            showCreateSheet = true
                        } else {
                            showSetupDialog = true
                        }
                    },
                    shape = CircleShape,
                ) {
                    Icon(
                        symbol = MaterialSymbols.Add,
                        contentDescription = stringRes(R.string.nest_create_fab),
                    )
                }
            }
        },
        accountViewModel = accountViewModel,
    ) {
        RefresheableBox(nestsFeedContentState, true) {
            SaveableFeedContentState(nestsFeedContentState, scrollStateKey = ScrollStateKeys.NESTS_SCREEN) { listState ->
                RenderFeedContentState(
                    feedContentState = nestsFeedContentState,
                    accountViewModel = accountViewModel,
                    listState = listState,
                    nav = nav,
                    routeForLastRead = "NestsFeed",
                    onLoaded = { loaded ->
                        NestsFeedLoaded(
                            loaded = loaded,
                            listState = listState,
                            accountViewModel = accountViewModel,
                            nav = nav,
                        )
                    },
                )
            }
        }
    }

    if (showSetupDialog) {
        SetUpAudioServerDialog(
            defaultUrl = CreateNestViewModel.DEFAULT_SERVICE_URL,
            onDismiss = { showSetupDialog = false },
            onConfirm = {
                showSetupDialog = false
                accountViewModel.launchSigner {
                    try {
                        accountViewModel.account.sendNestsServersList(
                            listOf(CreateNestViewModel.DEFAULT_SERVICE_URL),
                        )
                        showCreateSheet = true
                    } catch (_: Throwable) {
                        accountViewModel.toastManager.toast(
                            R.string.nests,
                            R.string.nest_no_server_save_failed,
                        )
                    }
                }
            },
        )
    }

    if (showCreateSheet) {
        CreateNestSheet(
            accountViewModel = accountViewModel,
            onDismiss = { showCreateSheet = false },
        )
    }
}

@Composable
private fun SetUpAudioServerDialog(
    defaultUrl: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringRes(R.string.nest_no_server_title)) },
        text = { Text(stringRes(R.string.nest_no_server_body, defaultUrl)) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringRes(R.string.nest_no_server_use_default))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringRes(R.string.nest_no_server_cancel))
            }
        },
    )
}

@Composable
fun WatchAccountForNestsScreen(
    nestsFeedState: FeedContentState,
    accountViewModel: AccountViewModel,
) {
    val listState by accountViewModel.account.liveLiveStreamsFollowLists.collectAsStateWithLifecycle()
    // Use `by` to unwrap the StateFlow's value into the LaunchedEffect
    // key — without it, the State<T> object is reference-stable across
    // recompositions and the effect never re-fires when the user
    // mutes someone, leaving the rooms feed stale until a manual
    // refresh.
    val hiddenUsers by accountViewModel.account.hiddenUsers.flow
        .collectAsStateWithLifecycle()

    LaunchedEffect(accountViewModel, listState, hiddenUsers) {
        nestsFeedState.checkKeysInvalidateDataAndSendToTop()
    }
}
