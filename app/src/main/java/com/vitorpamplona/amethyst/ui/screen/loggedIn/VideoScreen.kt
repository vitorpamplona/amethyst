package com.vitorpamplona.amethyst.ui.screen.loggedIn

import android.Manifest
import android.net.Uri
import android.os.Build
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.MaterialTheme
import androidx.compose.material.OutlinedButton
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.service.NostrVideoDataSource
import com.vitorpamplona.amethyst.service.model.FileHeaderEvent
import com.vitorpamplona.amethyst.service.model.FileStorageHeaderEvent
import com.vitorpamplona.amethyst.ui.actions.GallerySelect
import com.vitorpamplona.amethyst.ui.actions.NewMediaModel
import com.vitorpamplona.amethyst.ui.actions.NewMediaView
import com.vitorpamplona.amethyst.ui.actions.NewPostView
import com.vitorpamplona.amethyst.ui.components.ObserveDisplayNip05Status
import com.vitorpamplona.amethyst.ui.navigation.Route
import com.vitorpamplona.amethyst.ui.note.FileHeaderDisplay
import com.vitorpamplona.amethyst.ui.note.FileStorageHeaderDisplay
import com.vitorpamplona.amethyst.ui.note.LikeReaction
import com.vitorpamplona.amethyst.ui.note.NoteAuthorPicture
import com.vitorpamplona.amethyst.ui.note.NoteDropDownMenu
import com.vitorpamplona.amethyst.ui.note.NoteUsernameDisplay
import com.vitorpamplona.amethyst.ui.note.RenderRelay
import com.vitorpamplona.amethyst.ui.note.ViewCountReaction
import com.vitorpamplona.amethyst.ui.note.ZapReaction
import com.vitorpamplona.amethyst.ui.screen.FeedEmpty
import com.vitorpamplona.amethyst.ui.screen.FeedError
import com.vitorpamplona.amethyst.ui.screen.FeedState
import com.vitorpamplona.amethyst.ui.screen.FeedViewModel
import com.vitorpamplona.amethyst.ui.screen.LoadingFeed
import com.vitorpamplona.amethyst.ui.screen.NostrVideoFeedViewModel
import com.vitorpamplona.amethyst.ui.screen.ScrollStateKeys
import com.vitorpamplona.amethyst.ui.screen.rememberForeverPagerState
import kotlinx.collections.immutable.ImmutableList
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun VideoScreen(
    videoFeedView: NostrVideoFeedViewModel,
    accountViewModel: AccountViewModel,
    nav: (String) -> Unit
) {
    val lifeCycleOwner = LocalLifecycleOwner.current

    WatchAccountForVideoScreen(videoFeedView = videoFeedView, accountViewModel = accountViewModel)

    DisposableEffect(accountViewModel) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                println("Video Start")
                NostrVideoDataSource.start()
            }
            if (event == Lifecycle.Event.ON_PAUSE) {
                println("Video Stop")
                NostrVideoDataSource.stop()
            }
        }

        lifeCycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifeCycleOwner.lifecycle.removeObserver(observer)
        }
    }

    Column(Modifier.fillMaxHeight()) {
        Column(
            modifier = Modifier.padding(vertical = 0.dp)
        ) {
            SaveableFeedState(videoFeedView, accountViewModel, nav, ScrollStateKeys.VIDEO_SCREEN)
        }
    }
}

@Composable
fun WatchAccountForVideoScreen(videoFeedView: NostrVideoFeedViewModel, accountViewModel: AccountViewModel) {
    val accountState by accountViewModel.accountLiveData.observeAsState()
    val account = remember(accountState) { accountState?.account } ?: return

    var firstTime by remember(accountViewModel) { mutableStateOf(true) }

    LaunchedEffect(accountViewModel, account.defaultStoriesFollowList) {
        if (firstTime) {
            firstTime = false
        } else {
            NostrVideoDataSource.resetFilters()
            videoFeedView.invalidateDataAndSendToTop(true)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SaveableFeedState(
    videoFeedView: NostrVideoFeedViewModel,
    accountViewModel: AccountViewModel,
    nav: (String) -> Unit,
    routeForLastRead: String?,
    scrollStateKey: String? = null
) {
    val pagerState = if (scrollStateKey != null) {
        rememberForeverPagerState(scrollStateKey)
    } else {
        remember { PagerState() }
    }

    WatchScrollToTop(videoFeedView, pagerState)

    RenderPage(videoFeedView, accountViewModel, pagerState, nav)
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
public fun WatchScrollToTop(
    viewModel: FeedViewModel,
    pagerState: PagerState
) {
    val scrollToTop by viewModel.scrollToTop.collectAsState()

    LaunchedEffect(scrollToTop) {
        if (scrollToTop > 0 && viewModel.scrolltoTopPending) {
            pagerState.scrollToPage(page = 0)
            viewModel.sentToTop()
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun RenderPage(
    videoFeedView: NostrVideoFeedViewModel,
    accountViewModel: AccountViewModel,
    pagerState: PagerState,
    nav: (String) -> Unit
) {
    val feedState by videoFeedView.feedContent.collectAsState()

    Box() {
        Column {
            Crossfade(
                targetState = feedState,
                animationSpec = tween(durationMillis = 100)
            ) { state ->
                when (state) {
                    is FeedState.Empty -> {
                        FeedEmpty {}
                    }

                    is FeedState.FeedError -> {
                        FeedError(state.errorMessage) {}
                    }

                    is FeedState.Loaded -> {
                        SlidingCarousel(
                            state.feed,
                            pagerState,
                            accountViewModel,
                            nav
                        )
                    }

                    is FeedState.Loading -> {
                        LoadingFeed()
                    }
                }
            }
        }
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
        pageCount = feed.value.size,
        state = pagerState,
        beyondBoundsPageCount = 1,
        modifier = Modifier.fillMaxSize(1f),
        key = { index ->
            feed.value.getOrNull(index)?.idHex ?: "$index"
        }
    ) { index ->
        feed.value.getOrNull(index)?.let { note ->
            RenderVideoOrPictureNote(note, accountViewModel, nav)
        }
    }
}

@Composable
private fun RenderVideoOrPictureNote(
    note: Note,
    accountViewModel: AccountViewModel,
    nav: (String) -> Unit
) {
    Column(remember { Modifier.fillMaxSize(1f) }) {
        Row(remember { Modifier.weight(1f) }, verticalAlignment = Alignment.CenterVertically) {
            val noteEvent = remember { note.event }
            if (noteEvent is FileHeaderEvent) {
                FileHeaderDisplay(note)
            } else if (noteEvent is FileStorageHeaderEvent) {
                FileStorageHeaderDisplay(note)
            }
        }
    }

    Row(verticalAlignment = Alignment.Bottom, modifier = remember { Modifier.fillMaxSize(1f) }) {
        Column(remember { Modifier.weight(1f) }) {
            RenderVideoOrPicture(note, nav, accountViewModel)
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
private fun RenderVideoOrPicture(
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
                    .height(60.dp)
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
                    remember { Modifier.weight(1f) }
                )
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(top = 5.dp)
            ) {
                RelayBadges(baseNote = note)
            }
        }
    }
}

@Composable
private fun VideoUserOptionAction(
    note: Note,
    accountViewModel: AccountViewModel
) {
    var moreActionsExpanded by remember { mutableStateOf(false) }

    IconButton(
        modifier = remember { Modifier.size(24.dp) },
        onClick = { moreActionsExpanded = true }
    ) {
        Icon(
            imageVector = Icons.Default.MoreVert,
            null,
            modifier = remember { Modifier.size(20.dp) },
            tint = MaterialTheme.colors.onSurface.copy(alpha = 0.32f)
        )

        NoteDropDownMenu(
            note,
            moreActionsExpanded,
            { moreActionsExpanded = false },
            accountViewModel
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RelayBadges(baseNote: Note) {
    val noteRelaysState by baseNote.live().relays.observeAsState()
    val noteRelays = remember(noteRelaysState) {
        noteRelaysState?.note?.relays ?: emptySet()
    }

    FlowRow() {
        noteRelays.forEach { dirtyUrl ->
            RenderRelay(dirtyUrl)
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
        NewPostView({ wantsToReplyTo = null }, wantsToReplyTo, null, accountViewModel, nav)
    }

    if (wantsToQuote != null) {
        NewPostView({ wantsToQuote = null }, null, wantsToQuote, accountViewModel, nav)
    }

    Spacer(modifier = Modifier.height(8.dp))

    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(bottom = 75.dp, end = 20.dp)) {
        /*
        ReplyReaction(baseNote, accountViewModel, iconSize = 40.dp) {
            wantsToReplyTo = baseNote
        }
        BoostReaction(baseNote, accountViewModel, iconSize = 40.dp) {
            wantsToQuote = baseNote
        }*/
        LikeReaction(baseNote, grayTint = MaterialTheme.colors.onBackground, accountViewModel, iconSize = 40.dp, heartSize = 35.dp)
        ZapReaction(baseNote, grayTint = MaterialTheme.colors.onBackground, accountViewModel, iconSize = 40.dp, animationSize = 35.dp)
        ViewCountReaction(baseNote.idHex, grayTint = MaterialTheme.colors.onBackground, iconSize = 40.dp, barChartSize = 39.dp)
    }
}

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun NewImageButton(accountViewModel: AccountViewModel, nav: (String) -> Unit) {
    var wantsToPost by remember {
        mutableStateOf(false)
    }

    var pickedURI by remember {
        mutableStateOf<Uri?>(null)
    }

    val scope = rememberCoroutineScope()

    val postViewModel: NewMediaModel = viewModel()
    postViewModel.onceUploaded {
        scope.launch {
            // awaits an refresh on the list
            delay(250)
            val route = Route.Video.route.replace("{scrollToTop}", "true")
            nav(route)
        }
    }

    if (wantsToPost) {
        val cameraPermissionState =
            rememberPermissionState(
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    Manifest.permission.READ_MEDIA_IMAGES
                } else {
                    Manifest.permission.READ_EXTERNAL_STORAGE
                }
            )

        if (cameraPermissionState.status.isGranted) {
            var showGallerySelect by remember { mutableStateOf(false) }
            if (showGallerySelect) {
                GallerySelect(
                    onImageUri = { uri ->
                        wantsToPost = false
                        showGallerySelect = false
                        pickedURI = uri
                    }
                )
            }

            showGallerySelect = true
        } else {
            LaunchedEffect(key1 = accountViewModel) {
                cameraPermissionState.launchPermissionRequest()
            }
        }
    }

    pickedURI?.let {
        NewMediaView(
            uri = it,
            onClose = { pickedURI = null },
            postViewModel = postViewModel,
            accountViewModel = accountViewModel,
            nav = nav
        )
    }

    if (postViewModel.isUploadingImage) {
        ShowProgress(postViewModel)
    } else {
        OutlinedButton(
            onClick = { wantsToPost = true },
            modifier = Modifier.size(55.dp),
            shape = CircleShape,
            colors = ButtonDefaults.outlinedButtonColors(backgroundColor = MaterialTheme.colors.primary),
            contentPadding = PaddingValues(0.dp)
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_compose),
                null,
                modifier = Modifier.size(26.dp),
                tint = Color.White
            )
        }
    }
}

@Composable
private fun ShowProgress(postViewModel: NewMediaModel) {
    Box(Modifier.size(55.dp), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(
            progress = postViewModel.uploadingPercentage.value,
            modifier = Modifier
                .size(55.dp)
                .background(MaterialTheme.colors.background)
                .clip(CircleShape),
            strokeWidth = 5.dp
        )
        postViewModel.uploadingDescription.value?.let {
            Text(
                it,
                color = MaterialTheme.colors.onSurface,
                fontSize = 10.sp,
                textAlign = TextAlign.Center
            )
        }
    }
}
