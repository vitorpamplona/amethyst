/**
 * Copyright (c) 2024 Vitor Pamplona
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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.model.FeatureSetType
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.service.NostrVideoDataSource
import com.vitorpamplona.amethyst.ui.actions.CrossfadeIfEnabled
import com.vitorpamplona.amethyst.ui.actions.NewPostView
import com.vitorpamplona.amethyst.ui.components.ClickableBox
import com.vitorpamplona.amethyst.ui.components.ObserveDisplayNip05Status
import com.vitorpamplona.amethyst.ui.feeds.FeedContentState
import com.vitorpamplona.amethyst.ui.feeds.FeedEmpty
import com.vitorpamplona.amethyst.ui.feeds.FeedError
import com.vitorpamplona.amethyst.ui.feeds.FeedState
import com.vitorpamplona.amethyst.ui.feeds.LoadingFeed
import com.vitorpamplona.amethyst.ui.feeds.RefresheableBox
import com.vitorpamplona.amethyst.ui.feeds.ScrollStateKeys
import com.vitorpamplona.amethyst.ui.feeds.WatchScrollToTop
import com.vitorpamplona.amethyst.ui.navigation.AppBottomBar
import com.vitorpamplona.amethyst.ui.navigation.INav
import com.vitorpamplona.amethyst.ui.navigation.Route
import com.vitorpamplona.amethyst.ui.navigation.routeFor
import com.vitorpamplona.amethyst.ui.note.BoostReaction
import com.vitorpamplona.amethyst.ui.note.CheckHiddenFeedWatchBlockAndReport
import com.vitorpamplona.amethyst.ui.note.LikeReaction
import com.vitorpamplona.amethyst.ui.note.NoteAuthorPicture
import com.vitorpamplona.amethyst.ui.note.NoteUsernameDisplay
import com.vitorpamplona.amethyst.ui.note.RenderAllRelayList
import com.vitorpamplona.amethyst.ui.note.ReplyReaction
import com.vitorpamplona.amethyst.ui.note.ZapReaction
import com.vitorpamplona.amethyst.ui.note.elements.NoteDropDownMenu
import com.vitorpamplona.amethyst.ui.note.types.FileHeaderDisplay
import com.vitorpamplona.amethyst.ui.note.types.FileStorageHeaderDisplay
import com.vitorpamplona.amethyst.ui.note.types.JustVideoDisplay
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.screen.loggedIn.DisappearingScaffold
import com.vitorpamplona.amethyst.ui.stringRes
import com.vitorpamplona.amethyst.ui.theme.AuthorInfoVideoFeed
import com.vitorpamplona.amethyst.ui.theme.DoubleHorzSpacer
import com.vitorpamplona.amethyst.ui.theme.Size20Modifier
import com.vitorpamplona.amethyst.ui.theme.Size22Modifier
import com.vitorpamplona.amethyst.ui.theme.Size35Modifier
import com.vitorpamplona.amethyst.ui.theme.Size35dp
import com.vitorpamplona.amethyst.ui.theme.Size40Modifier
import com.vitorpamplona.amethyst.ui.theme.Size40dp
import com.vitorpamplona.amethyst.ui.theme.Size55dp
import com.vitorpamplona.amethyst.ui.theme.VideoReactionColumnPadding
import com.vitorpamplona.amethyst.ui.theme.placeholderText
import com.vitorpamplona.quartz.events.FileHeaderEvent
import com.vitorpamplona.quartz.events.FileStorageHeaderEvent
import com.vitorpamplona.quartz.events.VideoEvent

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
    val lifeCycleOwner = LocalLifecycleOwner.current

    WatchAccountForVideoScreen(videoFeedContentState = videoFeedContentState, accountViewModel = accountViewModel)

    DisposableEffect(lifeCycleOwner) {
        val observer =
            LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_RESUME) {
                    println("Video Start")
                    NostrVideoDataSource.start()
                }
            }

        lifeCycleOwner.lifecycle.addObserver(observer)
        onDispose { lifeCycleOwner.lifecycle.removeObserver(observer) }
    }

    DisappearingScaffold(
        isInvertedLayout = false,
        topBar = {
            StoriesTopBar(accountViewModel, nav)
        },
        bottomBar = {
            AppBottomBar(Route.Video, accountViewModel) { route, _ ->
                if (route == Route.Video) {
                    videoFeedContentState.sendToTop()
                } else {
                    nav.newStack(route.base)
                }
            }
        },
        floatingButton = {
            NewImageButton(accountViewModel, nav, videoFeedContentState::sendToTop)
        },
        accountViewModel = accountViewModel,
    ) {
        Column(
            modifier = Modifier.padding(it).consumeWindowInsets(it),
        ) {
            RenderPage(
                videoFeedContentState = videoFeedContentState,
                pagerStateKey = ScrollStateKeys.VIDEO_SCREEN,
                accountViewModel = accountViewModel,
                nav = nav,
            )
        }
    }
}

@Composable
fun WatchAccountForVideoScreen(
    videoFeedContentState: FeedContentState,
    accountViewModel: AccountViewModel,
) {
    val listState by accountViewModel.account.liveStoriesFollowLists.collectAsStateWithLifecycle()
    val hiddenUsers = accountViewModel.account.flowHiddenUsers.collectAsStateWithLifecycle()

    LaunchedEffect(accountViewModel, listState, hiddenUsers) {
        NostrVideoDataSource.resetFilters()
        videoFeedContentState.checkKeysInvalidateDataAndSendToTop()
    }
}

@Composable
fun RenderPage(
    videoFeedContentState: FeedContentState,
    pagerStateKey: String?,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val feedState by videoFeedContentState.feedContent.collectAsStateWithLifecycle()

    CrossfadeIfEnabled(
        targetState = feedState,
        animationSpec = tween(durationMillis = 100),
        label = "RenderPage",
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
                LoadedState(state, pagerStateKey, videoFeedContentState, accountViewModel, nav)
            }
            is FeedState.Loading -> {
                LoadingFeed()
            }
        }
    }
}

@Composable
private fun LoadedState(
    loaded: FeedState.Loaded,
    pagerStateKey: String?,
    videoFeedContentState: FeedContentState,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    RefresheableBox(invalidateableContent = videoFeedContentState) {
        SlidingCarousel(
            loaded,
            pagerStateKey,
            videoFeedContentState,
            accountViewModel,
            nav,
        )
    }
}

@Composable
fun SlidingCarousel(
    loaded: FeedState.Loaded,
    pagerStateKey: String?,
    videoFeedContentState: FeedContentState,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val items by loaded.feed.collectAsStateWithLifecycle()

    val pagerState =
        if (pagerStateKey != null) {
            myRememberForeverPagerState(pagerStateKey, items.list.size) { items.list.size }
        } else {
            myRememberPagerState(items.list.size) { items.list.size }
        }

    WatchScrollToTop(videoFeedContentState, pagerState)

    VerticalPager(
        state = pagerState,
        beyondViewportPageCount = 1,
        modifier = Modifier.fillMaxSize(),
        key = { index -> items.list.getOrNull(index)?.idHex ?: "$index" },
    ) { index ->
        items.list.getOrNull(index)?.let { note ->
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CheckHiddenFeedWatchBlockAndReport(
                    note = note,
                    modifier = Modifier.fillMaxWidth(),
                    showHiddenWarning = true,
                    ignoreAllBlocksAndReports = items.showHidden,
                    accountViewModel = accountViewModel,
                    nav = nav,
                ) {
                    RenderVideoOrPictureNote(
                        note,
                        accountViewModel,
                        nav,
                    )
                }
            }
        }
    }
}

@Composable
private fun RenderVideoOrPictureNote(
    note: Note,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    Column(Modifier.fillMaxSize(1f), verticalArrangement = Arrangement.Center) {
        Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
            val noteEvent = remember { note.event }
            if (noteEvent is FileHeaderEvent) {
                FileHeaderDisplay(note, false, true, accountViewModel)
            } else if (noteEvent is FileStorageHeaderEvent) {
                FileStorageHeaderDisplay(note, false, true, accountViewModel)
            } else if (noteEvent is VideoEvent) {
                JustVideoDisplay(note, false, true, accountViewModel)
            }
        }
    }

    Row(modifier = Modifier.fillMaxSize(1f), verticalAlignment = Alignment.Bottom) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.Center) {
            RenderAuthorInformation(note, nav, accountViewModel)
        }

        Column(
            modifier = AuthorInfoVideoFeed,
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            ReactionsColumn(note, accountViewModel, nav)
        }
    }
}

@Composable
private fun RenderAuthorInformation(
    note: Note,
    nav: INav,
    accountViewModel: AccountViewModel,
) {
    Row(modifier = Modifier.padding(start = 10.dp, end = 10.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        NoteAuthorPicture(note, nav, accountViewModel, Size55dp)

        Spacer(modifier = DoubleHorzSpacer)

        Column(
            Modifier
                .height(65.dp)
                .weight(1f),
            verticalArrangement = Arrangement.Center,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                NoteUsernameDisplay(note, Modifier.weight(1f), accountViewModel = accountViewModel)
                VideoUserOptionAction(note, accountViewModel, nav)
            }
            if (accountViewModel.settings.featureSet == FeatureSetType.COMPLETE) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ObserveDisplayNip05Status(
                        note.author!!,
                        Modifier.weight(1f),
                        accountViewModel,
                        nav = nav,
                    )
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(top = 2.dp),
                ) {
                    RenderAllRelayList(baseNote = note, accountViewModel = accountViewModel, nav = nav)
                }
            }
        }
    }
}

@Composable
private fun VideoUserOptionAction(
    note: Note,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val popupExpanded = remember { mutableStateOf(false) }

    ClickableBox(
        modifier = Size22Modifier,
        onClick = { popupExpanded.value = true },
    ) {
        Icon(
            imageVector = Icons.Default.MoreVert,
            contentDescription = stringRes(id = R.string.more_options),
            modifier = Size20Modifier,
            tint = MaterialTheme.colorScheme.placeholderText,
        )

        if (popupExpanded.value) {
            NoteDropDownMenu(
                note,
                { popupExpanded.value = false },
                null,
                accountViewModel,
                nav,
            )
        }
    }
}

@Composable
fun ReactionsColumn(
    baseNote: Note,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    var wantsToReplyTo by remember { mutableStateOf<Note?>(null) }

    var wantsToQuote by remember { mutableStateOf<Note?>(null) }

    if (wantsToReplyTo != null) {
        NewPostView(
            onClose = { wantsToReplyTo = null },
            baseReplyTo = wantsToReplyTo,
            quote = null,
            accountViewModel = accountViewModel,
            nav = nav,
        )
    }

    if (wantsToQuote != null) {
        NewPostView(
            onClose = { wantsToQuote = null },
            baseReplyTo = null,
            quote = wantsToQuote,
            accountViewModel = accountViewModel,
            nav = nav,
        )
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = VideoReactionColumnPadding,
    ) {
        ReplyReaction(
            baseNote = baseNote,
            grayTint = MaterialTheme.colorScheme.onBackground,
            accountViewModel = accountViewModel,
            iconSizeModifier = Size40Modifier,
        ) {
            routeFor(
                baseNote,
                accountViewModel.userProfile(),
            )?.let { nav.nav(it) }
        }
        BoostReaction(
            baseNote = baseNote,
            grayTint = MaterialTheme.colorScheme.onBackground,
            accountViewModel = accountViewModel,
            iconSizeModifier = Size40Modifier,
            iconSize = Size40dp,
            onQuotePress = {
                wantsToQuote = baseNote
            },
            onForkPress = {
            },
        )
        LikeReaction(
            baseNote = baseNote,
            grayTint = MaterialTheme.colorScheme.onBackground,
            accountViewModel = accountViewModel,
            nav = nav,
            iconSize = Size40dp,
            heartSizeModifier = Size35Modifier,
            iconFontSize = 28.sp,
        )
        ZapReaction(
            baseNote = baseNote,
            grayTint = MaterialTheme.colorScheme.onBackground,
            accountViewModel = accountViewModel,
            iconSize = Size40dp,
            iconSizeModifier = Size40Modifier,
            animationSize = Size35dp,
            nav = nav,
        )
    }
}
