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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.music

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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

@Composable
fun MusicTracksScreen(
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    MusicTracksScreen(
        musicTracksFeedContentState = accountViewModel.feedStates.musicTracksFeed,
        accountViewModel = accountViewModel,
        nav = nav,
    )
}

@Composable
fun MusicTracksScreen(
    musicTracksFeedContentState: FeedContentState,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    WatchLifecycleAndUpdateModel(musicTracksFeedContentState)
    WatchAccountForMusicTracksScreen(
        musicFeedState = musicTracksFeedContentState,
        accountViewModel = accountViewModel,
    )

    DisappearingScaffold(
        isInvertedLayout = false,
        topBar = {
            MusicTracksTopBar(accountViewModel, nav)
        },
        bottomBar = {
            AppBottomBar(Route.MusicTracks, nav, accountViewModel) { route ->
                if (route == Route.MusicTracks) {
                    musicTracksFeedContentState.sendToTop()
                } else {
                    nav.navBottomBar(route)
                }
            }
        },
        floatingButton = {
            FabBottomBarPadded(nav) {
                NewMusicTrackButton(accountViewModel, nav)
            }
        },
        accountViewModel = accountViewModel,
    ) {
        RefresheableBox(musicTracksFeedContentState, true) {
            SaveableFeedContentState(musicTracksFeedContentState, scrollStateKey = ScrollStateKeys.MUSIC_TRACKS_SCREEN) { listState ->
                RenderFeedContentState(
                    feedContentState = musicTracksFeedContentState,
                    accountViewModel = accountViewModel,
                    listState = listState,
                    nav = nav,
                    routeForLastRead = "MusicTracksFeed",
                    onLoaded = { loaded ->
                        MusicTracksFeedLoaded(
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
}

@Composable
fun WatchAccountForMusicTracksScreen(
    musicFeedState: FeedContentState,
    accountViewModel: AccountViewModel,
) {
    val listState by accountViewModel.account.liveHomeFollowLists.collectAsStateWithLifecycle()
    val hiddenUsers =
        accountViewModel.account.hiddenUsers.flow
            .collectAsStateWithLifecycle()

    LaunchedEffect(accountViewModel, listState, hiddenUsers) {
        musicFeedState.checkKeysInvalidateDataAndSendToTop()
    }
}
