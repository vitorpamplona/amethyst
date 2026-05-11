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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.video

import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitorpamplona.amethyst.commons.ui.feeds.FeedContentState
import com.vitorpamplona.amethyst.commons.ui.feeds.FeedState
import com.vitorpamplona.amethyst.ui.actions.CrossfadeIfEnabled
import com.vitorpamplona.amethyst.ui.feeds.FeedEmpty
import com.vitorpamplona.amethyst.ui.feeds.FeedError
import com.vitorpamplona.amethyst.ui.feeds.LoadingFeed
import com.vitorpamplona.amethyst.ui.feeds.RefresheableBox
import com.vitorpamplona.amethyst.ui.feeds.ScrollStateKeys
import com.vitorpamplona.amethyst.ui.feeds.WatchLifecycleAndUpdateModel
import com.vitorpamplona.amethyst.ui.feeds.WatchScrollToTop
import com.vitorpamplona.amethyst.ui.feeds.rememberForeverPagerState
import com.vitorpamplona.amethyst.ui.layouts.DisappearingScaffold
import com.vitorpamplona.amethyst.ui.navigation.bottombars.AppBottomBar
import com.vitorpamplona.amethyst.ui.navigation.bottombars.FabBottomBarPadded
import com.vitorpamplona.amethyst.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.ui.navigation.routes.Route
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.screen.loggedIn.pictures.PictureCardCompose
import com.vitorpamplona.amethyst.ui.screen.loggedIn.shorts.VideoCardCompose
import com.vitorpamplona.amethyst.ui.screen.loggedIn.video.datasource.VideoFilterAssemblerSubscription
import com.vitorpamplona.quartz.nip68Picture.PictureEvent
import com.vitorpamplona.quartz.nip71Video.VideoEvent
import com.vitorpamplona.quartz.nip94FileMetadata.FileHeaderEvent

@Composable
fun VideoScreen(
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    VideoScreen(
        accountViewModel.feedStates.videoFeed,
        accountViewModel,
        nav,
    )
}

@Composable
fun VideoScreen(
    videoFeedContentState: FeedContentState,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    WatchLifecycleAndUpdateModel(videoFeedContentState)
    WatchAccountForVideoScreen(videoFeedContentState = videoFeedContentState, accountViewModel = accountViewModel)
    VideoFilterAssemblerSubscription(accountViewModel)

    DisappearingScaffold(
        isInvertedLayout = false,
        topBar = {
            StoriesTopBar(
                accountViewModel = accountViewModel,
                nav = nav,
                colors = transparentTopBarColors(),
            )
        },
        bottomBar = {
            AppBottomBar(Route.Video, nav, accountViewModel) { route ->
                if (route == Route.Video) {
                    videoFeedContentState.sendToTop()
                } else {
                    nav.navBottomBar(route)
                }
            }
        },
        floatingButton = {
            // Bottom padding shifts the FAB above the reactions overlay so the row at the
            // very bottom stays unobstructed.
            Box(modifier = Modifier.padding(bottom = 80.dp)) {
                FabBottomBarPadded(nav) {
                    NewImageButton(accountViewModel, nav, videoFeedContentState::sendToTop)
                }
            }
        },
        accountViewModel = accountViewModel,
    ) { padding ->
        RenderFeed(
            videoFeedContentState = videoFeedContentState,
            scrollKey = ScrollStateKeys.VIDEO_SCREEN,
            padding = padding,
            accountViewModel = accountViewModel,
            nav = nav,
        )
    }
}

@Composable
fun WatchAccountForVideoScreen(
    videoFeedContentState: FeedContentState,
    accountViewModel: AccountViewModel,
) {
    val listState by accountViewModel.account.liveStoriesFollowLists.collectAsStateWithLifecycle()
    val hiddenUsers =
        accountViewModel.account.hiddenUsers.flow
            .collectAsStateWithLifecycle()

    LaunchedEffect(accountViewModel, listState, hiddenUsers) {
        videoFeedContentState.checkKeysInvalidateDataAndSendToTop()
    }
}

@Composable
private fun RenderFeed(
    videoFeedContentState: FeedContentState,
    scrollKey: String?,
    padding: PaddingValues,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    RefresheableBox(invalidateableContent = videoFeedContentState) {
        val feedState by videoFeedContentState.feedContent.collectAsStateWithLifecycle()

        CrossfadeIfEnabled(
            targetState = feedState,
            animationSpec = tween(durationMillis = 100),
            accountViewModel = accountViewModel,
        ) { state ->
            when (state) {
                is FeedState.Empty -> {
                    FeedEmpty(videoFeedContentState::invalidateData)
                }

                is FeedState.FeedError -> {
                    FeedError(state.errorMessage, videoFeedContentState::invalidateData)
                }

                is FeedState.Loaded -> {
                    VideoFeedLoaded(
                        loaded = state,
                        scrollKey = scrollKey,
                        padding = padding,
                        videoFeedContentState = videoFeedContentState,
                        accountViewModel = accountViewModel,
                        nav = nav,
                    )
                }

                is FeedState.Loading -> {
                    LoadingFeed()
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun transparentTopBarColors() =
    TopAppBarDefaults.topAppBarColors(
        containerColor = Color.Transparent,
        scrolledContainerColor = Color.Transparent,
    )

@Composable
fun VideoFeedLoaded(
    loaded: FeedState.Loaded,
    scrollKey: String?,
    padding: PaddingValues,
    videoFeedContentState: FeedContentState,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val items by loaded.feed.collectAsStateWithLifecycle()
    val pageCount = items.list.size

    val pagerState =
        if (scrollKey != null) {
            rememberForeverPagerState(scrollKey) { pageCount }
        } else {
            rememberPagerState { pageCount }
        }

    WatchScrollToTop(videoFeedContentState, pagerState)

    VerticalPager(
        state = pagerState,
        modifier = Modifier.fillMaxSize(),
        key = { idx -> items.list.getOrNull(idx)?.idHex ?: idx },
    ) { page ->
        val item = items.list.getOrNull(page) ?: return@VerticalPager

        VideoPagerPage(baseNote = item, padding = padding, accountViewModel = accountViewModel, nav = nav) {
            Box(modifier = Modifier.fillMaxSize()) {
                when (item.event) {
                    is PictureEvent -> PictureCardCompose(baseNote = item, accountViewModel = accountViewModel, nav = nav, showReactions = false)
                    is VideoEvent -> VideoCardCompose(item, accountViewModel, nav, showReactions = false)
                    is FileHeaderEvent -> FileHeaderCardCompose(item, accountViewModel, nav, showReactions = false)
                    else -> Unit
                }
            }
        }
    }
}
