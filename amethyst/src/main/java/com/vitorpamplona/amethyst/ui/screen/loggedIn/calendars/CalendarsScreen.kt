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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.calendars

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitorpamplona.amethyst.commons.ui.feeds.FeedContentState
import com.vitorpamplona.amethyst.ui.feeds.WatchLifecycleAndUpdateModel
import com.vitorpamplona.amethyst.ui.layouts.DisappearingScaffold
import com.vitorpamplona.amethyst.ui.navigation.bottombars.AppBottomBar
import com.vitorpamplona.amethyst.ui.navigation.bottombars.FabBottomBarPadded
import com.vitorpamplona.amethyst.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.ui.navigation.routes.Route
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.screen.loggedIn.calendars.datasource.CalendarsFilterAssemblerSubscription

@Composable
fun CalendarsScreen(
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    CalendarsScreen(
        feedState = accountViewModel.feedStates.calendarsFeed,
        collectionsState = accountViewModel.feedStates.calendarCollectionsFeed,
        accountViewModel = accountViewModel,
        nav = nav,
    )
}

@Composable
fun CalendarsScreen(
    feedState: FeedContentState,
    collectionsState: FeedContentState,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    WatchLifecycleAndUpdateModel(feedState)
    WatchLifecycleAndUpdateModel(collectionsState)
    WatchAccountForCalendarsScreen(feedState, collectionsState, accountViewModel)
    CalendarsFilterAssemblerSubscription(accountViewModel)

    var viewMode by rememberSaveable { mutableStateOf(CalendarsViewMode.FEED) }

    DisappearingScaffold(
        isInvertedLayout = false,
        topBar = {
            CalendarsTopBar(
                viewMode = viewMode,
                onViewModeChange = { viewMode = it },
                accountViewModel = accountViewModel,
                nav = nav,
            )
        },
        bottomBar = {
            AppBottomBar(Route.Calendars, nav, accountViewModel) { route ->
                if (route == Route.Calendars) {
                    feedState.sendToTop()
                } else {
                    nav.navBottomBar(route)
                }
            }
        },
        floatingButton = {
            FabBottomBarPadded(nav) {
                NewCalendarButton(nav)
            }
        },
        accountViewModel = accountViewModel,
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.fillMaxSize()) {
                when (viewMode) {
                    CalendarsViewMode.FEED ->
                        CalendarFeedView(feedState, accountViewModel, nav)
                    CalendarsViewMode.MONTH ->
                        CalendarMonthView(feedState, accountViewModel, nav)
                    CalendarsViewMode.WEEK ->
                        CalendarWeekView(feedState, accountViewModel, nav)
                    CalendarsViewMode.DAY ->
                        CalendarDayView(feedState, accountViewModel, nav)
                    CalendarsViewMode.COLLECTIONS ->
                        CalendarCollectionsView(collectionsState, accountViewModel, nav)
                }
            }
        }
    }
}

@Composable
private fun WatchAccountForCalendarsScreen(
    feedState: FeedContentState,
    collectionsState: FeedContentState,
    accountViewModel: AccountViewModel,
) {
    val listState by accountViewModel.account.liveCalendarsFollowLists.collectAsStateWithLifecycle()
    val hiddenUsers by
        accountViewModel.account.hiddenUsers.flow
            .collectAsStateWithLifecycle()

    val rememberedKey = remember(accountViewModel, listState, hiddenUsers) { Any() }

    LaunchedEffect(rememberedKey) {
        feedState.checkKeysInvalidateDataAndSendToTop()
        collectionsState.checkKeysInvalidateDataAndSendToTop()
    }
}
