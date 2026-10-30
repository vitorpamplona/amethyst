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
package com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.chess

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitorpamplona.amethyst.commons.feeds.FeedContentState
import com.vitorpamplona.amethyst.commons.model.navigation.Route
import com.vitorpamplona.amethyst.commons.nip64Chess.publishChessChallenge
import com.vitorpamplona.amethyst.commons.nip64Chess.ui.NewChessGameButton
import com.vitorpamplona.amethyst.commons.nip64Chess.ui.NewChessGameDialog
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.chess_lobby_section_your_games
import com.vitorpamplona.amethyst.commons.resources.chess_lobby_tab_lobby
import com.vitorpamplona.amethyst.commons.ui.feeds.PagerStateKeys
import com.vitorpamplona.amethyst.commons.ui.feeds.RefresheableBox
import com.vitorpamplona.amethyst.commons.ui.feeds.RenderFeedContentState
import com.vitorpamplona.amethyst.commons.ui.feeds.SaveableFeedContentState
import com.vitorpamplona.amethyst.commons.ui.feeds.ScrollStateKeys
import com.vitorpamplona.amethyst.commons.ui.feeds.WatchLifecycleAndUpdateModel
import com.vitorpamplona.amethyst.commons.ui.feeds.rememberForeverPagerState
import com.vitorpamplona.amethyst.commons.ui.layouts.DisappearingScaffold
import com.vitorpamplona.amethyst.commons.ui.navigation.bottombars.FabBottomBarPadded
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.platform.AppBottomBar
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.chess.datasource.ChessLobbyFilterAssemblerSubscription
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.ui.theme.TabRowHeight
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.StringResource

/**
 * The chess lobby: the user's own games, and the games of the list picked in the top bar (open
 * challenges to take, live games to watch, finished ones). Each game is a note in a feed, loaded
 * like any other feed and drawn by its card.
 */
@Composable
fun ChessLobbyScreen(
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val myGames = accountViewModel.feedStates.chessMyGamesFeed
    val lobby = accountViewModel.feedStates.chessLobbyFeed

    WatchLifecycleAndUpdateModel(myGames)
    WatchLifecycleAndUpdateModel(lobby)
    WatchAccountForChessScreen(myGames, lobby, accountViewModel)
    ChessLobbyFilterAssemblerSubscription(accountViewModel)

    val tabs =
        remember(myGames, lobby) {
            persistentListOf(
                ChessTabItem(Res.string.chess_lobby_section_your_games, myGames, ScrollStateKeys.CHESS_MY_GAMES),
                ChessTabItem(Res.string.chess_lobby_tab_lobby, lobby, ScrollStateKeys.CHESS_LOBBY),
            )
        }
    val pagerState = rememberForeverPagerState(key = PagerStateKeys.CHESS_SCREEN) { tabs.size }

    ChessPages(pagerState, tabs, accountViewModel, nav)
}

@Composable
private fun ChessPages(
    pagerState: PagerState,
    tabs: ImmutableList<ChessTabItem>,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    var showNewGameDialog by remember { mutableStateOf(false) }

    DisappearingScaffold(
        isInvertedLayout = false,
        topBar = {
            Column {
                ChessTopBar(accountViewModel, nav)
                SecondaryTabRow(
                    containerColor = MaterialTheme.colorScheme.background,
                    contentColor = MaterialTheme.colorScheme.onBackground,
                    modifier = TabRowHeight,
                    selectedTabIndex = pagerState.currentPage,
                ) {
                    val coroutineScope = rememberCoroutineScope()
                    tabs.forEachIndexed { index, tab ->
                        Tab(
                            selected = pagerState.currentPage == index,
                            text = { Text(text = stringRes(tab.resource)) },
                            onClick = { coroutineScope.launch { pagerState.animateScrollToPage(index) } },
                        )
                    }
                }
            }
        },
        bottomBar = {
            AppBottomBar(Route.Chess, nav, accountViewModel) { route ->
                if (route == Route.Chess) {
                    tabs[pagerState.currentPage].feedState.sendToTop()
                } else {
                    nav.navBottomBar(route)
                }
            }
        },
        floatingButton = {
            FabBottomBarPadded(nav) {
                NewChessGameButton { showNewGameDialog = true }
            }
        },
        accountViewModel = accountViewModel,
    ) {
        HorizontalPager(
            state = pagerState,
            userScrollEnabled = true,
        ) { page ->
            RefresheableBox(tabs[page].feedState, true) {
                SaveableFeedContentState(tabs[page].feedState, scrollStateKey = tabs[page].scrollStateKey) { listState ->
                    RenderFeedContentState(
                        feedContentState = tabs[page].feedState,
                        accountViewModel = accountViewModel,
                        listState = listState,
                        nav = nav,
                        routeForLastRead = tabs[page].scrollStateKey,
                    )
                }
            }
        }
    }

    if (showNewGameDialog) {
        NewChessGameDialog(
            onDismiss = { showNewGameDialog = false },
            onCreateGame = { opponentPubkey, color ->
                showNewGameDialog = false
                accountViewModel.launchSigner {
                    val challenge = accountViewModel.account.publishChessChallenge(color, opponentPubkey)
                    withContext(Dispatchers.Main) { nav.nav(Route.ChessGame(challenge.id)) }
                }
            },
        )
    }
}

@Composable
private fun WatchAccountForChessScreen(
    myGames: FeedContentState,
    lobby: FeedContentState,
    accountViewModel: AccountViewModel,
) {
    val listState by accountViewModel.account.liveChessFollowLists.collectAsStateWithLifecycle()
    val hiddenUsers by accountViewModel.account.hiddenUsers.flow
        .collectAsStateWithLifecycle()

    LaunchedEffect(accountViewModel, listState, hiddenUsers) {
        myGames.checkKeysInvalidateDataAndSendToTop()
        lobby.checkKeysInvalidateDataAndSendToTop()
    }
}

@Immutable
private class ChessTabItem(
    val resource: StringResource,
    val feedState: FeedContentState,
    val scrollStateKey: String,
)
