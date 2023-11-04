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
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.service.NostrVideoDataSource
import com.vitorpamplona.amethyst.ui.actions.NewPostView
import com.vitorpamplona.amethyst.ui.components.ObserveDisplayNip05Status
import com.vitorpamplona.amethyst.ui.note.BoostReaction
import com.vitorpamplona.amethyst.ui.note.FileHeaderDisplay
import com.vitorpamplona.amethyst.ui.note.FileStorageHeaderDisplay
import com.vitorpamplona.amethyst.ui.note.HiddenNote
import com.vitorpamplona.amethyst.ui.note.LikeReaction
import com.vitorpamplona.amethyst.ui.note.NoteAuthorPicture
import com.vitorpamplona.amethyst.ui.note.NoteDropDownMenu
import com.vitorpamplona.amethyst.ui.note.NoteUsernameDisplay
import com.vitorpamplona.amethyst.ui.note.RenderRelay
import com.vitorpamplona.amethyst.ui.note.ReplyReaction
import com.vitorpamplona.amethyst.ui.note.ViewCountReaction
import com.vitorpamplona.amethyst.ui.note.WatchForReports
import com.vitorpamplona.amethyst.ui.note.ZapReaction
import com.vitorpamplona.amethyst.ui.note.routeFor
import com.vitorpamplona.amethyst.ui.screen.FeedEmpty
import com.vitorpamplona.amethyst.ui.screen.FeedError
import com.vitorpamplona.amethyst.ui.screen.FeedState
import com.vitorpamplona.amethyst.ui.screen.FeedViewModel
import com.vitorpamplona.amethyst.ui.screen.LoadingFeed
import com.vitorpamplona.amethyst.ui.screen.NostrVideoFeedViewModel
import com.vitorpamplona.amethyst.ui.screen.RefresheableView
import com.vitorpamplona.amethyst.ui.screen.ScrollStateKeys
import com.vitorpamplona.amethyst.ui.screen.rememberForeverPagerState
import com.vitorpamplona.amethyst.ui.theme.Size35dp
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
    nav: (String) -> Unit
) {
    val lifeCycleOwner = LocalLifecycleOwner.current

    WatchAccountForVideoScreen(videoFeedView = videoFeedView, accountViewModel = accountViewModel)

    DisposableEffect(lifeCycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                println("Video Start")
                NostrVideoDataSource.start()
            }
        }

        lifeCycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifeCycleOwner.lifecycle.removeObserver(observer)
        }
    }

    Column(Modifier.fillMaxHeight()) {
        RenderPage(
            videoFeedView = videoFeedView,
            pagerStateKey = ScrollStateKeys.VIDEO_SCREEN,
            accountViewModel = accountViewModel,
            nav = nav
        )
    }
}

@Composable
fun WatchAccountForVideoScreen(videoFeedView: NostrVideoFeedViewModel, accountViewModel: AccountViewModel) {
    val accountState by accountViewModel.accountLiveData.observeAsState()

    LaunchedEffect(accountViewModel, accountState?.account?.defaultStoriesFollowList) {
        NostrVideoDataSource.resetFilters()
        videoFeedView.checkKeysInvalidateDataAndSendToTop()
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
public fun WatchScrollToTop(
    viewModel: FeedViewModel,
    pagerState: PagerState
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
    nav: (String) -> Unit
) {
    val feedState by videoFeedView.feedContent.collectAsStateWithLifecycle()

    Crossfade(
        targetState = feedState,
        animationSpec = tween(durationMillis = 100),
        label = "RenderPage"
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
    nav: (String) -> Unit
) {
    val pagerState = if (pagerStateKey != null) {
        rememberForeverPagerState(pagerStateKey) { state.feed.value.size }
    } else {
        rememberPagerState { state.feed.value.size }
    }

    WatchScrollToTop(videoFeedView, pagerState)

    RefresheableView(viewModel = videoFeedView) {
        SlidingCarousel(
            state.feed,
            pagerState,
            accountViewModel,
            nav
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SlidingCarousel(
    feed: MutableState<ImmutableList<Note>>,
    pagerState: PagerState,
    accountViewModel: AccountViewModel,
    nav: (String) -> Unit
) {
    VerticalPager(
        state = pagerState,
        beyondBoundsPageCount = 1,
        modifier = Modifier.fillMaxSize(),
        key = { index ->
            feed.value.getOrNull(index)?.idHex ?: "$index"
        }
    ) { index ->
        feed.value.getOrNull(index)?.let { note ->
            LoadedVideoCompose(note, accountViewModel, nav)
        }
    }
}

@Composable
fun LoadedVideoCompose(
    note: Note,
    accountViewModel: AccountViewModel,
    nav: (String) -> Unit
) {
    var state by remember(note) {
        mutableStateOf(
            AccountViewModel.NoteComposeReportState()
        )
    }

    val scope = rememberCoroutineScope()

    WatchForReports(note, accountViewModel) { newState ->
        if (state != newState) {
            scope.launch(Dispatchers.Main) {
                state = newState
            }
        }
    }

    Crossfade(targetState = state) {
        RenderReportState(
            it,
            note,
            accountViewModel,
            nav
        )
    }
}

@Composable
fun RenderReportState(
    state: AccountViewModel.NoteComposeReportState,
    note: Note,
    accountViewModel: AccountViewModel,
    nav: (String) -> Unit
) {
    var showReportedNote by remember { mutableStateOf(false) }

    Crossfade(targetState = !state.isAcceptable && !showReportedNote) { showHiddenNote ->
        if (showHiddenNote) {
            Column(remember { Modifier.fillMaxSize() }, verticalArrangement = Arrangement.Center) {
                HiddenNote(
                    state.relevantReports,
                    state.isHiddenAuthor,
                    accountViewModel,
                    Modifier.fillMaxWidth(),
                    false,
                    nav,
                    onClick = { showReportedNote = true }
                )
            }
        } else {
            RenderVideoOrPictureNote(
                note,
                accountViewModel,
                nav
            )
        }
    }
}

@Composable
private fun RenderVideoOrPictureNote(
    note: Note,
    accountViewModel: AccountViewModel,
    nav: (String) -> Unit
) {
    Column(remember { Modifier.fillMaxSize(1f) }, verticalArrangement = Arrangement.Center) {
        Row(remember { Modifier.weight(1f) }, verticalAlignment = Alignment.CenterVertically) {
            val noteEvent = remember { note.event }
            if (noteEvent is FileHeaderEvent) {
                FileHeaderDisplay(note, false, accountViewModel)
            } else if (noteEvent is FileStorageHeaderEvent) {
                FileStorageHeaderDisplay(note, false, accountViewModel)
            }
        }
    }

    Row(verticalAlignment = Alignment.Bottom, modifier = remember { Modifier.fillMaxSize(1f) }) {
        Column(remember { Modifier.weight(1f) }) {
            RenderAuthorInformation(note, nav, accountViewModel)
        }

        Column(
            remember {
                Modifier
                    .width(65.dp)
                    .padding(bottom = 10.dp)
            },
            verticalArrangement = Arrangement.Center
        ) {
            Row(horizontalArrangement = Arrangement.Center) {
                ReactionsColumn(note, accountViewModel, nav)
            }
        }
    }
}

@Composable
private fun RenderAuthorInformation(
    note: Note,
    nav: (String) -> Unit,
    accountViewModel: AccountViewModel
) {
    Row(remember { Modifier.padding(10.dp) }, verticalAlignment = Alignment.Bottom) {
        Column(remember { Modifier.size(55.dp) }, verticalArrangement = Arrangement.Center) {
            NoteAuthorPicture(note, nav, accountViewModel, 55.dp)
        }

        Column(
            remember {
                Modifier
                    .padding(start = 10.dp, end = 10.dp)
                    .height(65.dp)
                    .weight(1f)
            },
            verticalArrangement = Arrangement.Center
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                NoteUsernameDisplay(note, remember { Modifier.weight(1f) })
                VideoUserOptionAction(note, accountViewModel)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                ObserveDisplayNip05Status(
                    remember { note.author!! },
                    remember { Modifier.weight(1f) },
                    accountViewModel,
                    nav = nav
                )
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(top = 2.dp)
            ) {
                RelayBadges(baseNote = note, accountViewModel, nav)
            }
        }
    }
}

@Composable
private fun VideoUserOptionAction(
    note: Note,
    accountViewModel: AccountViewModel
) {
    val popupExpanded = remember { mutableStateOf(false) }
    val enablePopup = remember {
        { popupExpanded.value = true }
    }

    IconButton(
        modifier = remember { Modifier.size(22.dp) },
        onClick = enablePopup
    ) {
        Icon(
            imageVector = Icons.Default.MoreVert,
            null,
            modifier = remember { Modifier.size(20.dp) },
            tint = MaterialTheme.colorScheme.placeholderText
        )

        NoteDropDownMenu(
            note,
            popupExpanded,
            accountViewModel
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RelayBadges(baseNote: Note, accountViewModel: AccountViewModel, nav: (String) -> Unit) {
    val noteRelays by baseNote.live().relayInfo.observeAsState()

    FlowRow() {
        noteRelays?.forEach { relayInfo ->
            RenderRelay(relayInfo, accountViewModel, nav)
        }
    }
}

@Composable
fun ReactionsColumn(baseNote: Note, accountViewModel: AccountViewModel, nav: (String) -> Unit) {
    var wantsToReplyTo by remember {
        mutableStateOf<Note?>(null)
    }

    var wantsToQuote by remember {
        mutableStateOf<Note?>(null)
    }

    if (wantsToReplyTo != null) {
        NewPostView(onClose = { wantsToReplyTo = null }, baseReplyTo = wantsToReplyTo, quote = null, accountViewModel = accountViewModel, nav = nav)
    }

    if (wantsToQuote != null) {
        NewPostView(onClose = { wantsToQuote = null }, baseReplyTo = null, quote = wantsToQuote, accountViewModel = accountViewModel, nav = nav)
    }

    Spacer(modifier = Modifier.height(8.dp))

    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(bottom = 75.dp, end = 20.dp)) {
        ReplyReaction(baseNote, grayTint = MaterialTheme.colorScheme.onBackground, accountViewModel, iconSize = 40.dp) {
            routeFor(
                baseNote,
                accountViewModel.userProfile()
            )?.let { nav(it) }
        }
        BoostReaction(baseNote, grayTint = MaterialTheme.colorScheme.onBackground, accountViewModel, iconSize = 40.dp) {
            wantsToQuote = baseNote
        }
        LikeReaction(baseNote, grayTint = MaterialTheme.colorScheme.onBackground, accountViewModel, nav, iconSize = 40.dp, heartSize = Size35dp, 28.sp)
        ZapReaction(baseNote, grayTint = MaterialTheme.colorScheme.onBackground, accountViewModel, iconSize = 40.dp, animationSize = Size35dp, nav = nav)
        ViewCountReaction(baseNote, grayTint = MaterialTheme.colorScheme.onBackground, barChartSize = 39.dp, viewCountColorFilter = MaterialTheme.colorScheme.onBackgroundColorFilter)
    }
}
