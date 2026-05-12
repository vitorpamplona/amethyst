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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.notifications

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.model.UiSettingsFlow
import com.vitorpamplona.amethyst.ui.components.SelectNotificationProvider
import com.vitorpamplona.amethyst.ui.feeds.PagerStateKeys
import com.vitorpamplona.amethyst.ui.feeds.RefresheableBox
import com.vitorpamplona.amethyst.ui.feeds.ScrollStateKeys
import com.vitorpamplona.amethyst.ui.feeds.WatchScrollToTop
import com.vitorpamplona.amethyst.ui.feeds.rememberForeverLazyListState
import com.vitorpamplona.amethyst.ui.feeds.rememberForeverPagerState
import com.vitorpamplona.amethyst.ui.layouts.DisappearingScaffold
import com.vitorpamplona.amethyst.ui.navigation.bottombars.AppBottomBar
import com.vitorpamplona.amethyst.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.ui.navigation.routes.Route
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.stringRes
import com.vitorpamplona.amethyst.ui.theme.TabRowHeight
import kotlinx.coroutines.launch

const val NOTIFICATION_LAST_READ_KEY = "Notification"

@Composable
fun NotificationScreen(
    scrollToEventId: String? = null,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    NotificationScreen(
        notifFeedContentState = accountViewModel.feedStates.notifications,
        notifFollowingState = accountViewModel.feedStates.notificationsFollowing,
        notifEveryoneState = accountViewModel.feedStates.notificationsEveryone,
        notifSummaryState = accountViewModel.feedStates.notificationSummary,
        notifPolls = accountViewModel.feedStates.notificationsOpenPolls,
        sharedPrefs = accountViewModel.settings.uiSettingsFlow,
        scrollToEventId = scrollToEventId,
        accountViewModel = accountViewModel,
        nav = nav,
    )
}

@Composable
fun NotificationScreen(
    notifFeedContentState: CardFeedContentState,
    notifFollowingState: CardFeedContentState,
    notifEveryoneState: CardFeedContentState,
    notifSummaryState: NotificationSummaryState,
    notifPolls: OpenPollsState,
    sharedPrefs: UiSettingsFlow,
    scrollToEventId: String? = null,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    SelectNotificationProvider(sharedPrefs)

    val split by accountViewModel.account.settings.splitNotificationsEnabled
        .collectAsStateWithLifecycle()

    if (split) {
        WatchAccountForNotifications(notifFollowingState, accountViewModel)
        WatchAccountForNotifications(notifEveryoneState, accountViewModel)
        SplitNotificationsScaffold(
            notifFollowingState = notifFollowingState,
            notifEveryoneState = notifEveryoneState,
            notifSummaryState = notifSummaryState,
            notifPolls = notifPolls,
            scrollToEventId = scrollToEventId,
            accountViewModel = accountViewModel,
            nav = nav,
        )
    } else {
        WatchAccountForNotifications(notifFeedContentState, accountViewModel)
        SingleNotificationsScaffold(
            notifFeedContentState = notifFeedContentState,
            notifSummaryState = notifSummaryState,
            notifPolls = notifPolls,
            scrollToEventId = scrollToEventId,
            accountViewModel = accountViewModel,
            nav = nav,
        )
    }
}

@Composable
private fun SingleNotificationsScaffold(
    notifFeedContentState: CardFeedContentState,
    notifSummaryState: NotificationSummaryState,
    notifPolls: OpenPollsState,
    scrollToEventId: String?,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    DisappearingScaffold(
        isInvertedLayout = false,
        topBar = {
            Column {
                NotificationTopBar(accountViewModel, nav, showSpinner = true)
                SummaryBar(state = notifSummaryState)
            }
        },
        bottomBar = {
            AppBottomBar(Route.Notification(), nav, accountViewModel) { route ->
                if (route is Route.Notification) {
                    notifFeedContentState.invalidateDataAndSendToTop(true)
                } else {
                    nav.navBottomBar(route)
                }
            }
        },
        accountViewModel = accountViewModel,
    ) {
        SingleNotificationsBody(
            notifFeedContentState = notifFeedContentState,
            notifPolls = notifPolls,
            scrollToEventId = scrollToEventId,
            accountViewModel = accountViewModel,
            nav = nav,
        )
    }
}

@Composable
private fun SplitNotificationsScaffold(
    notifFollowingState: CardFeedContentState,
    notifEveryoneState: CardFeedContentState,
    notifSummaryState: NotificationSummaryState,
    notifPolls: OpenPollsState,
    scrollToEventId: String?,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val pagerState = rememberForeverPagerState(key = PagerStateKeys.NOTIFICATION_SCREEN) { 2 }
    val coroutineScope = rememberCoroutineScope()

    DisappearingScaffold(
        isInvertedLayout = false,
        topBar = {
            Column {
                NotificationTopBar(accountViewModel, nav, showSpinner = false)
                SummaryBar(state = notifSummaryState)
                SecondaryTabRow(
                    containerColor = MaterialTheme.colorScheme.background,
                    contentColor = MaterialTheme.colorScheme.onBackground,
                    modifier = TabRowHeight,
                    selectedTabIndex = pagerState.currentPage,
                ) {
                    Tab(
                        selected = pagerState.currentPage == 0,
                        text = { Text(stringRes(R.string.notification_tab_following)) },
                        onClick = { coroutineScope.launch { pagerState.animateScrollToPage(0) } },
                    )
                    Tab(
                        selected = pagerState.currentPage == 1,
                        text = { Text(stringRes(R.string.notification_tab_everyone)) },
                        onClick = { coroutineScope.launch { pagerState.animateScrollToPage(1) } },
                    )
                }
            }
        },
        bottomBar = {
            AppBottomBar(Route.Notification(), nav, accountViewModel) { route ->
                if (route is Route.Notification) {
                    val active =
                        if (pagerState.currentPage == 0) notifFollowingState else notifEveryoneState
                    active.invalidateDataAndSendToTop(true)
                } else {
                    nav.navBottomBar(route)
                }
            }
        },
        accountViewModel = accountViewModel,
    ) {
        SplitNotificationsBody(
            pagerState = pagerState,
            notifFollowingState = notifFollowingState,
            notifEveryoneState = notifEveryoneState,
            notifPolls = notifPolls,
            scrollToEventId = scrollToEventId,
            accountViewModel = accountViewModel,
            nav = nav,
        )
    }
}

@Composable
private fun SingleNotificationsBody(
    notifFeedContentState: CardFeedContentState,
    notifPolls: OpenPollsState,
    scrollToEventId: String?,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    RefresheableBox(notifFeedContentState, true) {
        val listState = rememberForeverLazyListState(ScrollStateKeys.NOTIFICATION_SCREEN)

        WatchScrollToTop(notifFeedContentState, listState)

        RenderCardFeed(
            feedContent = notifFeedContentState,
            pollContent = notifPolls,
            accountViewModel = accountViewModel,
            listState = listState,
            nav = nav,
            routeForLastRead = NOTIFICATION_LAST_READ_KEY,
            scrollToEventId = scrollToEventId,
            headerContent = { ObserveInboxRelayListAndDisplayIfNotFound(accountViewModel, nav) },
        )
    }
}

@Composable
private fun SplitNotificationsBody(
    pagerState: PagerState,
    notifFollowingState: CardFeedContentState,
    notifEveryoneState: CardFeedContentState,
    notifPolls: OpenPollsState,
    scrollToEventId: String?,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    HorizontalPager(state = pagerState) { page ->
        when (page) {
            0 -> {
                NotificationPagerPage(
                    state = notifFollowingState,
                    pollContent = notifPolls,
                    scrollStateKey = ScrollStateKeys.NOTIFICATION_FOLLOWING,
                    scrollToEventId = scrollToEventId,
                    accountViewModel = accountViewModel,
                    nav = nav,
                )
            }

            1 -> {
                NotificationPagerPage(
                    state = notifEveryoneState,
                    pollContent = notifPolls,
                    scrollStateKey = ScrollStateKeys.NOTIFICATION_EVERYONE,
                    // Only the Following tab honors the deep-link scroll target so users
                    // aren't bounced when they swipe across to Everyone.
                    scrollToEventId = null,
                    accountViewModel = accountViewModel,
                    nav = nav,
                )
            }
        }
    }
}

@Composable
private fun NotificationPagerPage(
    state: CardFeedContentState,
    pollContent: OpenPollsState,
    scrollStateKey: String,
    scrollToEventId: String?,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    RefresheableBox(state, true) {
        val listState = rememberForeverLazyListState(scrollStateKey)

        WatchScrollToTop(state, listState)

        RenderCardFeed(
            feedContent = state,
            pollContent = pollContent,
            accountViewModel = accountViewModel,
            listState = listState,
            nav = nav,
            routeForLastRead = NOTIFICATION_LAST_READ_KEY,
            scrollToEventId = scrollToEventId,
            headerContent = { ObserveInboxRelayListAndDisplayIfNotFound(accountViewModel, nav) },
        )
    }
}

@Composable
fun WatchAccountForNotifications(
    notifFeedContentState: CardFeedContentState,
    accountViewModel: AccountViewModel,
) {
    val listState by
        accountViewModel.account.liveNotificationFollowLists.collectAsStateWithLifecycle()

    LaunchedEffect(accountViewModel, listState) {
        notifFeedContentState.checkKeysInvalidateDataAndSendToTop()
    }
}
