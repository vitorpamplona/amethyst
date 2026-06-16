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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.workouts

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitorpamplona.amethyst.commons.ui.feeds.FeedContentState
import com.vitorpamplona.amethyst.ui.feeds.RefresheableBox
import com.vitorpamplona.amethyst.ui.feeds.RenderFeedContentState
import com.vitorpamplona.amethyst.ui.feeds.SaveableFeedContentState
import com.vitorpamplona.amethyst.ui.feeds.ScrollStateKeys
import com.vitorpamplona.amethyst.ui.feeds.WatchLifecycleAndUpdateModel
import com.vitorpamplona.amethyst.ui.layouts.DisappearingScaffold
import com.vitorpamplona.amethyst.ui.layouts.LocalDisappearingScaffoldPadding
import com.vitorpamplona.amethyst.ui.navigation.bottombars.AppBottomBar
import com.vitorpamplona.amethyst.ui.navigation.bottombars.FabBottomBarPadded
import com.vitorpamplona.amethyst.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.ui.navigation.routes.Route
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.screen.loggedIn.workouts.datasource.WorkoutsFilterAssemblerSubscription
import com.vitorpamplona.amethyst.ui.screen.loggedIn.workouts.suggestion.WorkoutSuggestions

@Composable
fun WorkoutsScreen(
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    WorkoutsScreen(
        workoutsFeedContentState = accountViewModel.feedStates.workoutsFeed,
        accountViewModel = accountViewModel,
        nav = nav,
    )
}

@Composable
fun WorkoutsScreen(
    workoutsFeedContentState: FeedContentState,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    WatchLifecycleAndUpdateModel(workoutsFeedContentState)
    WatchAccountForWorkoutsScreen(workoutsFeedContentState = workoutsFeedContentState, accountViewModel = accountViewModel)
    WorkoutsFilterAssemblerSubscription(accountViewModel)

    DisappearingScaffold(
        isInvertedLayout = false,
        topBar = {
            WorkoutsTopBar(accountViewModel, nav)
        },
        bottomBar = {
            AppBottomBar(Route.Workouts, nav, accountViewModel) { route ->
                if (route == Route.Workouts) {
                    workoutsFeedContentState.sendToTop()
                } else {
                    nav.navBottomBar(route)
                }
            }
        },
        floatingButton = {
            FabBottomBarPadded(nav) {
                NewWorkoutButton(nav)
            }
        },
        accountViewModel = accountViewModel,
    ) {
        Box(Modifier.fillMaxSize()) {
            RefresheableBox(workoutsFeedContentState, true) {
                SaveableFeedContentState(workoutsFeedContentState, scrollStateKey = ScrollStateKeys.WORKOUTS_SCREEN) { listState ->
                    RenderFeedContentState(
                        feedContentState = workoutsFeedContentState,
                        accountViewModel = accountViewModel,
                        listState = listState,
                        nav = nav,
                        routeForLastRead = "WorkoutsFeed",
                    )
                }
            }

            // Health Connect detection banner: invites connect or offers detected workouts as kind 1301 posts.
            WorkoutSuggestions(
                accountViewModel = accountViewModel,
                nav = nav,
                modifier =
                    Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = LocalDisappearingScaffoldPadding.current.calculateTopPadding()),
            )
        }
    }
}

@Composable
fun WatchAccountForWorkoutsScreen(
    workoutsFeedContentState: FeedContentState,
    accountViewModel: AccountViewModel,
) {
    val listState by accountViewModel.account.liveWorkoutsFollowLists.collectAsStateWithLifecycle()
    val hiddenUsers =
        accountViewModel.account.hiddenUsers.flow
            .collectAsStateWithLifecycle()

    LaunchedEffect(accountViewModel, listState, hiddenUsers) {
        workoutsFeedContentState.checkKeysInvalidateDataAndSendToTop()
    }
}
