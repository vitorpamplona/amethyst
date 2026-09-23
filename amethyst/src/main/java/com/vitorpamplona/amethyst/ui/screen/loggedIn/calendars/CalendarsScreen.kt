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
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vitorpamplona.amethyst.commons.feeds.FeedContentState
import com.vitorpamplona.amethyst.commons.ui.feeds.WatchLifecycleAndUpdateModel
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.navigation.routes.Route
import com.vitorpamplona.amethyst.ui.layouts.DisappearingScaffold
import com.vitorpamplona.amethyst.ui.navigation.bottombars.AppBottomBar
import com.vitorpamplona.amethyst.ui.navigation.bottombars.FabBottomBarPadded
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.screen.loggedIn.calendars.datasource.CalendarsFilterAssemblerSubscription

@Composable
fun CalendarsScreen(
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    CalendarsScreen(
        feedState = accountViewModel.feedStates.calendarAppointmentsFeed,
        accountViewModel = accountViewModel,
        nav = nav,
    )
}

@Composable
fun CalendarsScreen(
    feedState: FeedContentState,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    WatchLifecycleAndUpdateModel(feedState)
    WatchAccountForCalendarsScreen(feedState, accountViewModel)
    CalendarsFilterAssemblerSubscription(accountViewModel)

    // Scoped to this screen's back-stack entry: opening an appointment disposes the composition
    // and switching lenses disposes the branch the previous lens lived in, so none of what the
    // user is looking at can live in `remember`/`rememberSaveable`. It is cleared when the entry
    // itself goes. See [CalendarsViewModel].
    val model: CalendarsViewModel = viewModel()
    model.init(accountViewModel.userProfile().pubkeyHex, feedState)

    val filterDTag by model.filterDTag.collectAsStateWithLifecycle()

    DisappearingScaffold(
        isInvertedLayout = false,
        topBar = {
            CalendarsTopBar(
                viewMode = model.viewMode,
                onViewModeChange = { model.viewMode = it },
                accountViewModel = accountViewModel,
                nav = nav,
                trailing = {
                    CalendarFilterChip(
                        selectedDTag = filterDTag,
                        onSelect = model::selectCalendar,
                        model = model,
                    )
                },
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
                when (model.viewMode) {
                    CalendarsViewMode.FEED -> CalendarFeedView(feedState, model, accountViewModel, nav)
                    CalendarsViewMode.MONTH -> CalendarMonthView(model, accountViewModel, nav)
                    CalendarsViewMode.WEEK -> CalendarWeekView(model, accountViewModel, nav)
                    CalendarsViewMode.DAY -> CalendarDayView(model, accountViewModel, nav)
                }
            }
        }
    }
}

@Composable
private fun WatchAccountForCalendarsScreen(
    feedState: FeedContentState,
    accountViewModel: AccountViewModel,
) {
    val listState by accountViewModel.account.liveCalendarsFollowLists.collectAsStateWithLifecycle()
    val hiddenUsers by
        accountViewModel.account.hiddenUsers.flow
            .collectAsStateWithLifecycle()

    LaunchedEffect(accountViewModel, listState, hiddenUsers) {
        feedState.checkKeysInvalidateDataAndSendToTop()
    }
}
