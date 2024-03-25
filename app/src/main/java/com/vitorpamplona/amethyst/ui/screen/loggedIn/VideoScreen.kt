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
package com.vitorpamplona.amethyst.ui.screen.loggedIn

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.model.FeatureSetType
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.service.NostrVideoDataSource
import com.vitorpamplona.amethyst.ui.actions.NewPostView
import com.vitorpamplona.amethyst.ui.components.ObserveDisplayNip05Status
import com.vitorpamplona.amethyst.ui.navigation.routeFor
import com.vitorpamplona.amethyst.ui.note.BoostReaction
import com.vitorpamplona.amethyst.ui.note.HiddenNote
import com.vitorpamplona.amethyst.ui.note.LikeReaction
import com.vitorpamplona.amethyst.ui.note.NoteAuthorPicture
import com.vitorpamplona.amethyst.ui.note.NoteUsernameDisplay
import com.vitorpamplona.amethyst.ui.note.RenderRelay
import com.vitorpamplona.amethyst.ui.note.ReplyReaction
import com.vitorpamplona.amethyst.ui.note.ViewCountReaction
import com.vitorpamplona.amethyst.ui.note.WatchForReports
import com.vitorpamplona.amethyst.ui.note.ZapReaction
import com.vitorpamplona.amethyst.ui.note.elements.NoteDropDownMenu
import com.vitorpamplona.amethyst.ui.note.types.FileHeaderDisplay
import com.vitorpamplona.amethyst.ui.note.types.FileStorageHeaderDisplay
import com.vitorpamplona.amethyst.ui.screen.FeedEmpty
import com.vitorpamplona.amethyst.ui.screen.FeedError
import com.vitorpamplona.amethyst.ui.screen.FeedState
import com.vitorpamplona.amethyst.ui.screen.FeedViewModel
import com.vitorpamplona.amethyst.ui.screen.LoadingFeed
import com.vitorpamplona.amethyst.ui.screen.NostrVideoFeedViewModel
import com.vitorpamplona.amethyst.ui.screen.RefresheableBox
import com.vitorpamplona.amethyst.ui.screen.ScrollStateKeys
import com.vitorpamplona.amethyst.ui.screen.rememberForeverPagerState
import com.vitorpamplona.amethyst.ui.theme.AuthorInfoVideoFeed
import com.vitorpamplona.amethyst.ui.theme.DoubleHorzSpacer
import com.vitorpamplona.amethyst.ui.theme.Size35Modifier
import com.vitorpamplona.amethyst.ui.theme.Size35dp
import com.vitorpamplona.amethyst.ui.theme.Size39Modifier
import com.vitorpamplona.amethyst.ui.theme.Size40Modifier
import com.vitorpamplona.amethyst.ui.theme.Size40dp
import com.vitorpamplona.amethyst.ui.theme.Size55dp
import com.vitorpamplona.amethyst.ui.theme.VideoReactionColumnPadding
import com.vitorpamplona.amethyst.ui.theme.onBackgroundColorFilter
import com.vitorpamplona.amethyst.ui.theme.placeholderText
import com.vitorpamplona.quartz.events.FileHeaderEvent
import com.vitorpamplona.quartz.events.FileStorageHeaderEvent
import kotlinx.collections.immutable.ImmutableList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Composable
fun VideoScreen(
    videoFeedView: NostrVideoFeedViewModel,
    accountViewModel: AccountViewModel,
    nav: (String) -> Unit,
) {
    val lifeCycleOwner = LocalLifecycleOwner.current

    WatchAccountForVideoScreen(videoFeedView = videoFeedView, accountViewModel = accountViewModel)

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

    Column(Modifier.fillMaxHeight()) {
        RenderPage(
            videoFeedView = videoFeedView,
            pagerStateKey = ScrollStateKeys.VIDEO_SCREEN,
            accountViewModel = accountViewModel,
            nav = nav,
        )
    }
}

@Composable
fun WatchAccountForVideoScreen(
    videoFeedView: NostrVideoFeedViewModel,
    accountViewModel: AccountViewModel,
) {
    val listState by accountViewModel.account.liveStoriesFollowLists.collectAsStateWithLifecycle()
    val hiddenUsers = accountViewModel.account.flowHiddenUsers.collectAsStateWithLifecycle()

    LaunchedEffect(accountViewModel, listState, hiddenUsers) {
        NostrVideoDataSource.resetFilters()
        videoFeedView.checkKeysInvalidateDataAndSendToTop()
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
public fun WatchScrollToTop(
    viewModel: FeedViewModel,
    pagerState: PagerState,
) {
    val scrollToTop by viewModel.scrollToTop.collectAsStateWithLifecycle()

    LaunchedEffect(scrollToTop) {
        if (scrollToTop > 0 && viewModel.scrolltoTopPending) {
            pagerState.scrollToPage(page = 0)
            viewModel.sentToTop()
        }
    }
}

@Composable
fun RenderPage(
    videoFeedView: NostrVideoFeedViewModel,
    pagerStateKey: String?,
    accountViewModel: AccountViewModel,
    nav: (String) -> Unit,
) {
    val feedState by videoFeedView.feedContent.collectAsStateWithLifecycle()

    Crossfade(
        targetState = feedState,
        animationSpec = tween(durationMillis = 100),
        label = "RenderPage",
    ) { state ->
        when (state) {
            is FeedState.Empty -> {
                FeedEmpty {}
            }
            is FeedState.FeedError -> {
                FeedError(state.errorMessage) {}
            }
            is FeedState.Loaded -> {
                LoadedState(state, pagerStateKey, videoFeedView, accountViewModel, nav)
            }
            is FeedState.Loading -> {
                LoadingFeed()
            }
        }
    }
}

@Composable
@OptIn(ExperimentalFoundationApi::class)
private fun LoadedState(
    state: FeedState.Loaded,
    pagerStateKey: String?,
    videoFeedView: NostrVideoFeedViewModel,
    accountViewModel: AccountViewModel,
    nav: (String) -> Unit,
) {
    val pagerState =
        if (pagerStateKey != null) {
            rememberForeverPagerState(pagerStateKey) { state.feed.value.size }
        } else {
            rememberPagerState { state.feed.value.size }
        }

    WatchScrollToTop(videoFeedView, pagerState)

    RefresheableBox(viewModel = videoFeedView) {
        SlidingCarousel(
            state.feed,
            pagerState,
            state.showHidden.value,
            accountViewModel,
            nav,
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SlidingCarousel(
    feed: MutableState<ImmutableList<Note>>,
    pagerState: PagerState,
    showHidden: Boolean,
    accountViewModel: AccountViewModel,
    nav: (String) -> Unit,
) {
    VerticalPager(
        state = pagerState,
        beyondBoundsPageCount = 1,
        modifier = Modifier.fillMaxSize(),
        key = { index -> feed.value.getOrNull(index)?.idHex ?: "$index" },
    ) { index ->
        feed.value.getOrNull(index)?.let { note ->
            LoadedVideoCompose(note, showHidden, accountViewModel, nav)
        }
    }
}

@Composable
fun LoadedVideoCompose(
    note: Note,
    showHidden: Boolean,
    accountViewModel: AccountViewModel,
    nav: (String) -> Unit,
) {
    var state by
        remember(note) {
            mutableStateOf(
                AccountViewModel.NoteComposeReportState(),
            )
        }

    if (!showHidden) {
        val scope = rememberCoroutineScope()

        WatchForReports(note, accountViewModel) { newState ->
            if (state != newState) {
                scope.launch(Dispatchers.Main) { state = newState }
            }
        }
    }

    Crossfade(targetState = state, label = "LoadedVideoCompose") {
        RenderReportState(
            it,
            note,
            accountViewModel,
            nav,
        )
    }
}

@Composable
fun RenderReportState(
    state: AccountViewModel.NoteComposeReportState,
    note: Note,
    accountViewModel: AccountViewModel,
    nav: (String) -> Unit,
) {
    var showReportedNote by remember { mutableStateOf(false) }

    Crossfade(targetState = (!state.isAcceptable || state.isHiddenAuthor) && !showReportedNote) {
            showHiddenNote ->
        if (showHiddenNote) {
            Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center) {
                HiddenNote(
                    state.relevantReports,
                    state.isHiddenAuthor,
                    accountViewModel,
                    Modifier.fillMaxWidth(),
                    nav,
                    onClick = { showReportedNote = true },
                )
            }
        } else {
            RenderVideoOrPictureNote(
                note,
                accountViewModel,
                nav,
            )
        }
    }
}

@Composable
private fun RenderVideoOrPictureNote(
    note: Note,
    accountViewModel: AccountViewModel,
    nav: (String) -> Unit,
) {
    Column(Modifier.fillMaxSize(1f), verticalArrangement = Arrangement.Center) {
        Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
            val noteEvent = remember { note.event }
            if (noteEvent is FileHeaderEvent) {
                FileHeaderDisplay(note, false, accountViewModel)
            } else if (noteEvent is FileStorageHeaderEvent) {
                FileStorageHeaderDisplay(note, false, accountViewModel)
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
    nav: (String) -> Unit,
    accountViewModel: AccountViewModel,
) {
    Row(modifier = Modifier.padding(start = 10.dp, end = 10.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        NoteAuthorPicture(note, nav, accountViewModel, Size55dp)

        Spacer(modifier = DoubleHorzSpacer)

        Column(
            Modifier.height(65.dp).weight(1f),
            verticalArrangement = Arrangement.Center,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                NoteUsernameDisplay(note, remember { Modifier.weight(1f) })
                VideoUserOptionAction(note, accountViewModel, nav)
            }
            if (accountViewModel.settings.featureSet != FeatureSetType.SIMPLIFIED) {
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
                    RelayBadges(baseNote = note, accountViewModel, nav)
                }
            }
        }
    }
}

@Composable
private fun VideoUserOptionAction(
    note: Note,
    accountViewModel: AccountViewModel,
    nav: (String) -> Unit,
) {
    val popupExpanded = remember { mutableStateOf(false) }
    val enablePopup = remember { { popupExpanded.value = true } }

    IconButton(
        modifier = remember { Modifier.size(22.dp) },
        onClick = enablePopup,
    ) {
        Icon(
            imageVector = Icons.Default.MoreVert,
            contentDescription = stringResource(id = R.string.more_options),
            modifier = remember { Modifier.size(20.dp) },
            tint = MaterialTheme.colorScheme.placeholderText,
        )

        NoteDropDownMenu(
            note,
            popupExpanded,
            null,
            accountViewModel,
            nav,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RelayBadges(
    baseNote: Note,
    accountViewModel: AccountViewModel,
    nav: (String) -> Unit,
) {
    val noteRelays by baseNote.live().relayInfo.observeAsState(baseNote.relays)

    FlowRow { noteRelays?.forEach { relayInfo -> RenderRelay(relayInfo, accountViewModel, nav) } }
}

@Composable
fun ReactionsColumn(
    baseNote: Note,
    accountViewModel: AccountViewModel,
    nav: (String) -> Unit,
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
            )
                ?.let { nav(it) }
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
        ViewCountReaction(
            note = baseNote,
            grayTint = MaterialTheme.colorScheme.onBackground,
            barChartModifier = Size39Modifier,
            viewCountColorFilter = MaterialTheme.colorScheme.onBackgroundColorFilter,
        )
    }
}
