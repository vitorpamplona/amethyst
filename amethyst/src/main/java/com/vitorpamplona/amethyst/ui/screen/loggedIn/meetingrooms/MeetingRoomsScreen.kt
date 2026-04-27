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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.meetingrooms

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
import com.vitorpamplona.amethyst.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.ui.navigation.routes.Route
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.screen.loggedIn.nests.datasource.NestsFilterAssemblerSubscription

/**
 * Drawer screen for NIP-53 kind 30313 (Meeting). Reads from the
 * `meetingRoomsFeed` content state which scans
 * `LocalCache.liveChatChannels` for `MeetingRoomEvent`s; the wire
 * subscription is the same `makeLiveActivitiesFilter` the Nests
 * screen uses (it requests kinds 30311/30312/30313/1311 in one
 * REQ), so mounting the `NestsFilterAssemblerSubscription` here
 * keeps wire traffic identical whichever screen the user lands
 * on first.
 *
 * Read-only list — the app doesn't compose kind-30313 events
 * locally. The per-row CTA (Join, recording link, etc.) lives
 * inside [com.vitorpamplona.amethyst.ui.note.types.RenderMeetingRoomEvent].
 */
@Composable
fun MeetingRoomsScreen(
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    MeetingRoomsScreen(
        meetingRoomsFeedContentState = accountViewModel.feedStates.meetingRoomsFeed,
        accountViewModel = accountViewModel,
        nav = nav,
    )
}

@Composable
fun MeetingRoomsScreen(
    meetingRoomsFeedContentState: FeedContentState,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    WatchLifecycleAndUpdateModel(meetingRoomsFeedContentState)
    WatchAccountForMeetingRoomsScreen(meetingRoomsFeedState = meetingRoomsFeedContentState, accountViewModel = accountViewModel)
    NestsFilterAssemblerSubscription(accountViewModel)

    DisappearingScaffold(
        isInvertedLayout = false,
        topBar = {
            MeetingRoomsTopBar(accountViewModel, nav)
        },
        bottomBar = {
            AppBottomBar(Route.MeetingRooms, accountViewModel) { route ->
                if (route == Route.MeetingRooms) {
                    meetingRoomsFeedContentState.sendToTop()
                } else {
                    nav.navBottomBar(route)
                }
            }
        },
        accountViewModel = accountViewModel,
    ) {
        RefresheableBox(meetingRoomsFeedContentState, true) {
            SaveableFeedContentState(meetingRoomsFeedContentState, scrollStateKey = ScrollStateKeys.MEETING_ROOMS_SCREEN) { listState ->
                RenderFeedContentState(
                    feedContentState = meetingRoomsFeedContentState,
                    accountViewModel = accountViewModel,
                    listState = listState,
                    nav = nav,
                    routeForLastRead = "MeetingRoomsFeed",
                    onLoaded = { loaded ->
                        MeetingRoomsFeedLoaded(
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
fun WatchAccountForMeetingRoomsScreen(
    meetingRoomsFeedState: FeedContentState,
    accountViewModel: AccountViewModel,
) {
    val listState by accountViewModel.account.liveLiveStreamsFollowLists.collectAsStateWithLifecycle()
    // Use `by` to unwrap the StateFlow's value into the LaunchedEffect
    // key — without it, the State<T> object is reference-stable across
    // recompositions and the effect never re-fires when the user
    // mutes someone, leaving the feed stale until a manual refresh.
    val hiddenUsers by accountViewModel.account.hiddenUsers.flow
        .collectAsStateWithLifecycle()

    LaunchedEffect(accountViewModel, listState, hiddenUsers) {
        meetingRoomsFeedState.checkKeysInvalidateDataAndSendToTop()
    }
}
