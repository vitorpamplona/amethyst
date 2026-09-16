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

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.workouts_tab_following
import com.vitorpamplona.amethyst.commons.resources.workouts_tab_mine
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
import com.vitorpamplona.amethyst.ui.screen.loggedIn.workouts.datasource.WorkoutsFilterAssemblerSubscription
import com.vitorpamplona.amethyst.ui.screen.loggedIn.workouts.fitness.MyFitnessContent
import com.vitorpamplona.amethyst.ui.stringRes
import com.vitorpamplona.amethyst.ui.theme.TabRowHeight
import kotlinx.coroutines.launch

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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkoutsScreen(
    workoutsFeedContentState: FeedContentState,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    WatchLifecycleAndUpdateModel(workoutsFeedContentState)
    WatchAccountForWorkoutsScreen(workoutsFeedContentState = workoutsFeedContentState, accountViewModel = accountViewModel)
    WorkoutsFilterAssemblerSubscription(accountViewModel)

    // Mine first. The section is about the user's own training; other people's workouts are the
    // second tab, not the landing page.
    val pagerState = rememberPagerState { 2 }
    val onFollowingTab = pagerState.currentPage == FOLLOWING_TAB

    DisappearingScaffold(
        isInvertedLayout = false,
        topBar = {
            Column {
                // The follow-list spinner filters the Following feed, so it only belongs on that
                // tab; the Mine tab shows the plain title instead.
                WorkoutsTopBar(accountViewModel, nav, showFeedFilter = onFollowingTab)
                WorkoutsTabs(pagerState)
            }
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
        HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
            when (page) {
                MINE_TAB -> MyFitnessContent(accountViewModel, nav)

                else ->
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
            }
        }
    }
}

private const val MINE_TAB = 0
private const val FOLLOWING_TAB = 1

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WorkoutsTabs(pagerState: PagerState) {
    val scope = rememberCoroutineScope()

    SecondaryTabRow(
        containerColor = MaterialTheme.colorScheme.background,
        contentColor = MaterialTheme.colorScheme.onBackground,
        selectedTabIndex = pagerState.currentPage,
        modifier = TabRowHeight,
    ) {
        Tab(
            selected = pagerState.currentPage == MINE_TAB,
            onClick = { scope.launch { pagerState.animateScrollToPage(MINE_TAB) } },
            text = { Text(stringRes(Res.string.workouts_tab_mine)) },
        )
        Tab(
            selected = pagerState.currentPage == FOLLOWING_TAB,
            onClick = { scope.launch { pagerState.animateScrollToPage(FOLLOWING_TAB) } },
            text = { Text(stringRes(Res.string.workouts_tab_following)) },
        )
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
