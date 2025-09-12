/**
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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.profile

import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntSize
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.model.LocalCache
import com.vitorpamplona.amethyst.model.User
import com.vitorpamplona.amethyst.service.relayClient.reqCommand.account.observeAccountIsHiddenUser
import com.vitorpamplona.amethyst.ui.feeds.WatchLifecycleAndUpdateModel
import com.vitorpamplona.amethyst.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.screen.loggedIn.lists.NostrUserListFeedViewModel
import com.vitorpamplona.amethyst.ui.screen.loggedIn.profile.bookmarks.BookmarkTabHeader
import com.vitorpamplona.amethyst.ui.screen.loggedIn.profile.bookmarks.TabBookmarks
import com.vitorpamplona.amethyst.ui.screen.loggedIn.profile.bookmarks.dal.UserProfileBookmarksFeedViewModel
import com.vitorpamplona.amethyst.ui.screen.loggedIn.profile.conversations.TabNotesConversations
import com.vitorpamplona.amethyst.ui.screen.loggedIn.profile.conversations.dal.UserProfileConversationsFeedViewModel
import com.vitorpamplona.amethyst.ui.screen.loggedIn.profile.datasource.UserProfileFilterAssemblerSubscription
import com.vitorpamplona.amethyst.ui.screen.loggedIn.profile.followers.FollowersTabHeader
import com.vitorpamplona.amethyst.ui.screen.loggedIn.profile.followers.TabFollowers
import com.vitorpamplona.amethyst.ui.screen.loggedIn.profile.followers.dal.UserProfileFollowersUserFeedViewModel
import com.vitorpamplona.amethyst.ui.screen.loggedIn.profile.follows.FollowTabHeader
import com.vitorpamplona.amethyst.ui.screen.loggedIn.profile.follows.TabFollows
import com.vitorpamplona.amethyst.ui.screen.loggedIn.profile.follows.dal.UserProfileFollowsUserFeedViewModel
import com.vitorpamplona.amethyst.ui.screen.loggedIn.profile.gallery.TabGallery
import com.vitorpamplona.amethyst.ui.screen.loggedIn.profile.gallery.dal.UserProfileGalleryFeedViewModel
import com.vitorpamplona.amethyst.ui.screen.loggedIn.profile.hashtags.FollowedTagsTabHeader
import com.vitorpamplona.amethyst.ui.screen.loggedIn.profile.hashtags.TabFollowedTags
import com.vitorpamplona.amethyst.ui.screen.loggedIn.profile.header.ProfileHeader
import com.vitorpamplona.amethyst.ui.screen.loggedIn.profile.header.apps.UserAppRecommendationsFeedViewModel
import com.vitorpamplona.amethyst.ui.screen.loggedIn.profile.mutual.TabMutualConversations
import com.vitorpamplona.amethyst.ui.screen.loggedIn.profile.mutual.dal.UserProfileMutualFeedViewModel
import com.vitorpamplona.amethyst.ui.screen.loggedIn.profile.newthreads.TabNotesNewThreads
import com.vitorpamplona.amethyst.ui.screen.loggedIn.profile.newthreads.dal.UserProfileNewThreadsFeedViewModel
import com.vitorpamplona.amethyst.ui.screen.loggedIn.profile.relays.RelaysTabHeader
import com.vitorpamplona.amethyst.ui.screen.loggedIn.profile.relays.TabRelays
import com.vitorpamplona.amethyst.ui.screen.loggedIn.profile.reports.ReportsTabHeader
import com.vitorpamplona.amethyst.ui.screen.loggedIn.profile.reports.TabReports
import com.vitorpamplona.amethyst.ui.screen.loggedIn.profile.reports.dal.UserProfileReportFeedViewModel
import com.vitorpamplona.amethyst.ui.screen.loggedIn.profile.zaps.TabReceivedZaps
import com.vitorpamplona.amethyst.ui.screen.loggedIn.profile.zaps.ZapTabHeader
import com.vitorpamplona.amethyst.ui.screen.loggedIn.profile.zaps.dal.UserProfileZapsFeedViewModel
import com.vitorpamplona.amethyst.ui.stringRes
import com.vitorpamplona.amethyst.ui.theme.DividerThickness
import com.vitorpamplona.amethyst.ui.theme.Size8dp
import kotlinx.coroutines.launch

@Composable
fun ProfileScreen(
    userId: String?,
    accountViewModel: AccountViewModel,
    nostrListsViewModel: NostrUserListFeedViewModel,
    nav: INav,
) {
    if (userId == null) return

    var userBase by remember { mutableStateOf(LocalCache.getUserIfExists(userId)) }

    if (userBase == null) {
        LaunchedEffect(userId) {
            val newUserBase = LocalCache.checkGetOrCreateUser(userId)
            if (newUserBase != userBase) {
                userBase = newUserBase
            }
        }
    }

    userBase?.let {
        PrepareViewModels(
            baseUser = it,
            accountViewModel = accountViewModel,
            nostrListsViewModel = nostrListsViewModel,
            nav = nav,
        )
    }
}

@Composable
fun PrepareViewModels(
    baseUser: User,
    accountViewModel: AccountViewModel,
    nostrListsViewModel: NostrUserListFeedViewModel,
    nav: INav,
) {
    val followsFeedViewModel: UserProfileFollowsUserFeedViewModel =
        viewModel(
            key = baseUser.pubkeyHex + "UserProfileFollowsUserFeedViewModel",
            factory =
                UserProfileFollowsUserFeedViewModel.Factory(
                    baseUser,
                    accountViewModel.account,
                ),
        )

    val galleryFeedViewModel: UserProfileGalleryFeedViewModel =
        viewModel(
            key = baseUser.pubkeyHex + "UserGalleryFeedViewModel",
            factory =
                UserProfileGalleryFeedViewModel.Factory(
                    baseUser,
                    accountViewModel.account,
                ),
        )

    val followersFeedViewModel: UserProfileFollowersUserFeedViewModel =
        viewModel(
            key = baseUser.pubkeyHex + "UserProfileFollowersUserFeedViewModel",
            factory =
                UserProfileFollowersUserFeedViewModel.Factory(
                    baseUser,
                    accountViewModel.account,
                ),
        )

    val appRecommendations: UserAppRecommendationsFeedViewModel =
        viewModel(
            key = baseUser.pubkeyHex + "UserAppRecommendationsFeedViewModel",
            factory = UserAppRecommendationsFeedViewModel.Factory(baseUser),
        )

    val zapFeedViewModel: UserProfileZapsFeedViewModel =
        viewModel(
            key = baseUser.pubkeyHex + "UserProfileZapsFeedViewModel",
            factory =
                UserProfileZapsFeedViewModel.Factory(
                    baseUser,
                ),
        )

    val threadsViewModel: UserProfileNewThreadsFeedViewModel =
        viewModel(
            key = baseUser.pubkeyHex + "UserProfileNewThreadsFeedViewModel",
            factory =
                UserProfileNewThreadsFeedViewModel.Factory(
                    baseUser,
                    accountViewModel.account,
                ),
        )

    val repliesViewModel: UserProfileConversationsFeedViewModel =
        viewModel(
            key = baseUser.pubkeyHex + "UserProfileConversationsFeedViewModel",
            factory =
                UserProfileConversationsFeedViewModel.Factory(
                    baseUser,
                    accountViewModel.account,
                ),
        )

    val mutualViewModel: UserProfileMutualFeedViewModel =
        viewModel(
            key = baseUser.pubkeyHex + "UserProfileMutualFeedViewModel",
            factory =
                UserProfileMutualFeedViewModel.Factory(
                    baseUser,
                    accountViewModel.account,
                ),
        )

    val bookmarksFeedViewModel: UserProfileBookmarksFeedViewModel =
        viewModel(
            key = baseUser.pubkeyHex + "UserProfileBookmarksFeedViewModel",
            factory =
                UserProfileBookmarksFeedViewModel.Factory(
                    baseUser,
                    accountViewModel.account,
                ),
        )

    val reportsFeedViewModel: UserProfileReportFeedViewModel =
        viewModel(
            key = baseUser.pubkeyHex + "UserProfileReportFeedViewModel",
            factory =
                UserProfileReportFeedViewModel.Factory(
                    baseUser,
                ),
        )

    ProfileScreen(
        baseUser = baseUser,
        threadsViewModel,
        repliesViewModel,
        mutualViewModel,
        followsFeedViewModel,
        followersFeedViewModel,
        appRecommendations,
        zapFeedViewModel,
        bookmarksFeedViewModel,
        galleryFeedViewModel,
        reportsFeedViewModel,
        nostrListsViewModel,
        accountViewModel = accountViewModel,
        nav = nav,
    )
}

@Composable
fun ProfileScreen(
    baseUser: User,
    threadsViewModel: UserProfileNewThreadsFeedViewModel,
    repliesViewModel: UserProfileConversationsFeedViewModel,
    mutualViewModel: UserProfileMutualFeedViewModel,
    followsFeedViewModel: UserProfileFollowsUserFeedViewModel,
    followersFeedViewModel: UserProfileFollowersUserFeedViewModel,
    appRecommendations: UserAppRecommendationsFeedViewModel,
    zapFeedViewModel: UserProfileZapsFeedViewModel,
    bookmarksFeedViewModel: UserProfileBookmarksFeedViewModel,
    galleryFeedViewModel: UserProfileGalleryFeedViewModel,
    reportsFeedViewModel: UserProfileReportFeedViewModel,
    followSetsViewModel: NostrUserListFeedViewModel,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    WatchLifecycleAndUpdateModel(threadsViewModel)
    WatchLifecycleAndUpdateModel(repliesViewModel)
    WatchLifecycleAndUpdateModel(mutualViewModel)
    WatchLifecycleAndUpdateModel(followsFeedViewModel)
    WatchLifecycleAndUpdateModel(followersFeedViewModel)
    WatchLifecycleAndUpdateModel(appRecommendations)
    WatchLifecycleAndUpdateModel(bookmarksFeedViewModel)
    WatchLifecycleAndUpdateModel(galleryFeedViewModel)
    WatchLifecycleAndUpdateModel(reportsFeedViewModel)
    WatchLifecycleAndUpdateModel(followSetsViewModel)

    UserProfileFilterAssemblerSubscription(baseUser, accountViewModel.dataSources().profile)

    RenderSurface { tabRowModifier: Modifier, pagerModifier: Modifier ->
        RenderScreen(
            baseUser,
            tabRowModifier,
            pagerModifier,
            threadsViewModel,
            repliesViewModel,
            mutualViewModel,
            appRecommendations,
            followsFeedViewModel,
            followersFeedViewModel,
            zapFeedViewModel,
            bookmarksFeedViewModel,
            galleryFeedViewModel,
            reportsFeedViewModel,
            followSetsViewModel,
            accountViewModel,
            nav,
        )
    }
}

@Composable
private fun RenderSurface(content: @Composable (tabRowModifier: Modifier, pagerModifier: Modifier) -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.background,
    ) {
        var columnSize by remember { mutableStateOf(IntSize.Zero) }
        var tabsSize by remember { mutableStateOf(IntSize.Zero) }

        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .onSizeChanged { columnSize = it },
        ) {
            val coroutineScope = rememberCoroutineScope()
            val scrollState = rememberScrollState()

            val tabRowModifier = remember { Modifier.onSizeChanged { tabsSize = it } }

            val pagerModifier =
                with(LocalDensity.current) { Modifier.height((columnSize.height - tabsSize.height).toDp()) }

            val starting =
                with(LocalDensity.current) { WindowInsets.statusBars.getTop(this) }

            Box(
                modifier =
                    remember {
                        Modifier
                            .verticalScroll(scrollState)
                            .nestedScroll(
                                object : NestedScrollConnection {
                                    override fun onPreScroll(
                                        available: Offset,
                                        source: NestedScrollSource,
                                    ): Offset {
                                        val borderLimit = scrollState.maxValue - starting
                                        val finalValue = scrollState.value - available.y

                                        return if (available.y >= 0) {
                                            Offset.Zero
                                        } else {
                                            // When scrolling vertically, scroll the container first.

                                            // if it doesn't go over the max
                                            if (finalValue < borderLimit) {
                                                coroutineScope.launch { scrollState.scrollBy(-available.y) }
                                                Offset(0f, available.y)
                                            } else {
                                                // if it's already over the max
                                                if (scrollState.value >= borderLimit) {
                                                    Offset.Zero
                                                } else {
                                                    // move to the max
                                                    val newY = (borderLimit - scrollState.value).toFloat()
                                                    coroutineScope.launch { scrollState.scrollBy(newY) }
                                                    Offset(0f, -newY)
                                                }
                                            }
                                        }
                                    }
                                },
                            ).fillMaxHeight()
                    },
            ) {
                content(tabRowModifier, pagerModifier)
            }
        }
    }
}

@Composable
private fun RenderScreen(
    baseUser: User,
    tabRowModifier: Modifier,
    pagerModifier: Modifier,
    threadsViewModel: UserProfileNewThreadsFeedViewModel,
    repliesViewModel: UserProfileConversationsFeedViewModel,
    mutualViewModel: UserProfileMutualFeedViewModel,
    appRecommendations: UserAppRecommendationsFeedViewModel,
    followsFeedViewModel: UserProfileFollowsUserFeedViewModel,
    followersFeedViewModel: UserProfileFollowersUserFeedViewModel,
    zapFeedViewModel: UserProfileZapsFeedViewModel,
    bookmarksFeedViewModel: UserProfileBookmarksFeedViewModel,
    galleryFeedViewModel: UserProfileGalleryFeedViewModel,
    reportsFeedViewModel: UserProfileReportFeedViewModel,
    followSetsViewModel: NostrUserListFeedViewModel,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val pagerState = rememberPagerState { 11 }

    Column {
        ProfileHeader(baseUser, appRecommendations, followSetsViewModel, nav, accountViewModel)
        ScrollableTabRow(
            containerColor = Color.Transparent,
            contentColor = MaterialTheme.colorScheme.onBackground,
            selectedTabIndex = pagerState.currentPage,
            edgePadding = Size8dp,
            modifier = tabRowModifier,
            divider = { HorizontalDivider(thickness = DividerThickness) },
        ) {
            CreateAndRenderTabs(
                baseUser,
                pagerState,
                accountViewModel,
            )
        }
        HorizontalPager(
            state = pagerState,
            modifier = pagerModifier,
        ) { page ->
            CreateAndRenderPages(
                page,
                baseUser,
                threadsViewModel,
                repliesViewModel,
                mutualViewModel,
                followsFeedViewModel,
                followersFeedViewModel,
                zapFeedViewModel,
                bookmarksFeedViewModel,
                galleryFeedViewModel,
                reportsFeedViewModel,
                accountViewModel,
                nav,
            )
        }
    }
}

@Composable
private fun CreateAndRenderPages(
    page: Int,
    baseUser: User,
    threadsViewModel: UserProfileNewThreadsFeedViewModel,
    repliesViewModel: UserProfileConversationsFeedViewModel,
    mutualViewModel: UserProfileMutualFeedViewModel,
    followsFeedViewModel: UserProfileFollowsUserFeedViewModel,
    followersFeedViewModel: UserProfileFollowersUserFeedViewModel,
    zapFeedViewModel: UserProfileZapsFeedViewModel,
    bookmarksFeedViewModel: UserProfileBookmarksFeedViewModel,
    galleryFeedViewModel: UserProfileGalleryFeedViewModel,
    reportsFeedViewModel: UserProfileReportFeedViewModel,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    UpdateThreadsAndRepliesWhenBlockUnblock(
        baseUser,
        threadsViewModel,
        repliesViewModel,
        accountViewModel,
    )

    when (page) {
        0 -> TabNotesNewThreads(threadsViewModel, accountViewModel, nav)
        1 -> TabNotesConversations(repliesViewModel, accountViewModel, nav)
        2 -> TabMutualConversations(mutualViewModel, accountViewModel, nav)
        3 -> TabGallery(galleryFeedViewModel, accountViewModel, nav)
        4 -> TabFollows(baseUser, followsFeedViewModel, accountViewModel, nav)
        5 -> TabFollowers(baseUser, followersFeedViewModel, accountViewModel, nav)
        6 -> TabReceivedZaps(baseUser, zapFeedViewModel, accountViewModel, nav)
        7 -> TabBookmarks(bookmarksFeedViewModel, accountViewModel, nav)
        8 -> TabFollowedTags(baseUser, accountViewModel, nav)
        9 -> TabReports(baseUser, reportsFeedViewModel, accountViewModel, nav)
        10 -> TabRelays(baseUser, accountViewModel, nav)
    }
}

@Composable
fun UpdateThreadsAndRepliesWhenBlockUnblock(
    baseUser: User,
    threadsViewModel: UserProfileNewThreadsFeedViewModel,
    repliesViewModel: UserProfileConversationsFeedViewModel,
    accountViewModel: AccountViewModel,
) {
    val isHidden by observeAccountIsHiddenUser(accountViewModel.account, baseUser)

    LaunchedEffect(key1 = isHidden) {
        threadsViewModel.invalidateData()
        repliesViewModel.invalidateData()
    }
}

@Composable
private fun CreateAndRenderTabs(
    baseUser: User,
    pagerState: PagerState,
    accountViewModel: AccountViewModel,
) {
    val coroutineScope = rememberCoroutineScope()

    val tabs =
        listOf<@Composable (() -> Unit)?>(
            { Text(text = stringRes(R.string.notes)) },
            { Text(text = stringRes(R.string.replies)) },
            { Text(text = stringRes(R.string.mutual)) },
            { Text(text = stringRes(R.string.gallery)) },
            { FollowTabHeader(baseUser, accountViewModel) },
            { FollowersTabHeader(baseUser, accountViewModel) },
            { ZapTabHeader(baseUser, accountViewModel) },
            { BookmarkTabHeader(baseUser, accountViewModel) },
            { FollowedTagsTabHeader(baseUser, accountViewModel) },
            { ReportsTabHeader(baseUser, accountViewModel) },
            { RelaysTabHeader(baseUser, accountViewModel) },
        )

    tabs.forEachIndexed { index, function ->
        Tab(
            selected = pagerState.currentPage == index,
            onClick = { coroutineScope.launch { pagerState.animateScrollToPage(index) } },
            text = function,
        )
    }
}
