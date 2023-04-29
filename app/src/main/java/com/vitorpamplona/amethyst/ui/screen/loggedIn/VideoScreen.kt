package com.vitorpamplona.amethyst.ui.screen.loggedIn

import android.Manifest
import android.net.Uri
import android.os.Build
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.MaterialTheme
import androidx.compose.material.OutlinedButton
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.navigation.NavController
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.service.NostrVideoDataSource
import com.vitorpamplona.amethyst.service.model.FileHeaderEvent
import com.vitorpamplona.amethyst.service.model.FileStorageHeaderEvent
import com.vitorpamplona.amethyst.ui.actions.GallerySelect
import com.vitorpamplona.amethyst.ui.actions.NewMediaView
import com.vitorpamplona.amethyst.ui.actions.NewPostView
import com.vitorpamplona.amethyst.ui.components.ObserveDisplayNip05Status
import com.vitorpamplona.amethyst.ui.dal.VideoFeedFilter
import com.vitorpamplona.amethyst.ui.note.FileHeaderDisplay
import com.vitorpamplona.amethyst.ui.note.FileStorageHeaderDisplay
import com.vitorpamplona.amethyst.ui.note.LikeReaction
import com.vitorpamplona.amethyst.ui.note.NoteAuthorPicture
import com.vitorpamplona.amethyst.ui.note.NoteDropDownMenu
import com.vitorpamplona.amethyst.ui.note.NoteUsernameDisplay
import com.vitorpamplona.amethyst.ui.note.ViewCountReaction
import com.vitorpamplona.amethyst.ui.note.ZapReaction
import com.vitorpamplona.amethyst.ui.screen.FeedEmpty
import com.vitorpamplona.amethyst.ui.screen.FeedError
import com.vitorpamplona.amethyst.ui.screen.FeedState
import com.vitorpamplona.amethyst.ui.screen.LoadingFeed
import com.vitorpamplona.amethyst.ui.screen.NostrVideoFeedViewModel

@Composable
fun VideoScreen(
    videoFeedView: NostrVideoFeedViewModel,
    accountViewModel: AccountViewModel,
    navController: NavController,
    scrollToTop: Boolean = false
) {
    val lifeCycleOwner = LocalLifecycleOwner.current
    val account = accountViewModel.accountLiveData.value?.account ?: return

    VideoFeedFilter.account = account

    LaunchedEffect(accountViewModel) {
        VideoFeedFilter.account = account
        NostrVideoDataSource.resetFilters()
        videoFeedView.invalidateData()
    }

    DisposableEffect(accountViewModel) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                println("Video Start")
                VideoFeedFilter.account = account
                NostrVideoDataSource.start()
                videoFeedView.invalidateData()
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
            FeedView(videoFeedView, accountViewModel, navController)
        }
    }
}

@Composable
fun FeedView(
    videoFeedView: NostrVideoFeedViewModel,
    accountViewModel: AccountViewModel,
    navController: NavController
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
                            accountViewModel,
                            navController
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
    feed: MutableState<List<Note>>,
    accountViewModel: AccountViewModel,
    navController: NavController
) {
    val pagerState: PagerState = remember { PagerState() }

    VerticalPager(
        pageCount = feed.value.size,
        state = pagerState,
        beyondBoundsPageCount = 1,
        modifier = Modifier.fillMaxSize(1f)
    ) { index ->
        feed.value.getOrNull(index)?.let { note ->
            RenderVideoOrPictureNote(note, accountViewModel, navController)
        }
    }
}

@Composable
private fun RenderVideoOrPictureNote(
    note: Note,
    accountViewModel: AccountViewModel,
    navController: NavController
) {
    val noteEvent = note.event

    val accountState by accountViewModel.accountLiveData.observeAsState()
    val account = accountState?.account ?: return
    val loggedIn = account.userProfile()

    var moreActionsExpanded by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize(1f)) {
        Row(Modifier.weight(1f, true), verticalAlignment = Alignment.CenterVertically) {
            if (noteEvent is FileHeaderEvent) {
                FileHeaderDisplay(note)
            } else if (noteEvent is FileStorageHeaderEvent) {
                FileStorageHeaderDisplay(note)
            }
        }
    }

    Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.fillMaxSize(1f)) {
        Column(Modifier.weight(1f)) {
            Row(Modifier.padding(10.dp), verticalAlignment = Alignment.Bottom) {
                Column(Modifier.size(45.dp), verticalArrangement = Arrangement.Center) {
                    NoteAuthorPicture(note, navController, loggedIn, 45.dp)
                }

                Column(
                    Modifier
                        .padding(start = 10.dp, end = 10.dp)
                        .height(45.dp)
                        .weight(1f),
                    verticalArrangement = Arrangement.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        NoteUsernameDisplay(note, Modifier.weight(1f))

                        IconButton(
                            modifier = Modifier.size(24.dp),
                            onClick = { moreActionsExpanded = true }
                        ) {
                            Icon(
                                imageVector = Icons.Default.MoreVert,
                                null,
                                modifier = Modifier.size(15.dp),
                                tint = MaterialTheme.colors.onSurface.copy(alpha = 0.32f)
                            )

                            NoteDropDownMenu(note, moreActionsExpanded, { moreActionsExpanded = false }, accountViewModel)
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        ObserveDisplayNip05Status(note.author!!, Modifier.weight(1f))
                    }
                }
            }
        }

        Column(
            Modifier
                .width(65.dp)
                .padding(bottom = 10.dp),
            verticalArrangement = Arrangement.Center
        ) {
            Row(horizontalArrangement = Arrangement.Center) {
                ReactionsColumn(note, accountViewModel, navController)
            }
        }
    }
}

@Composable
fun ReactionsColumn(baseNote: Note, accountViewModel: AccountViewModel, navController: NavController) {
    val accountState by accountViewModel.accountLiveData.observeAsState()
    val account = accountState?.account ?: return

    var wantsToReplyTo by remember {
        mutableStateOf<Note?>(null)
    }

    var wantsToQuote by remember {
        mutableStateOf<Note?>(null)
    }

    if (wantsToReplyTo != null) {
        NewPostView({ wantsToReplyTo = null }, wantsToReplyTo, null, account, accountViewModel, navController)
    }

    if (wantsToQuote != null) {
        NewPostView({ wantsToQuote = null }, null, wantsToQuote, account, accountViewModel, navController)
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
        LikeReaction(baseNote, accountViewModel, iconSize = 40.dp, heartSize = 35.dp)
        ZapReaction(baseNote, accountViewModel, iconSize = 40.dp, animationSize = 35.dp)
        ViewCountReaction(baseNote.idHex, iconSize = 40.dp, barChartSize = 39.dp)
    }
}

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun NewImageButton(accountViewModel: AccountViewModel, navController: NavController) {
    var wantsToPost by remember {
        mutableStateOf(false)
    }

    var pickedURI by remember {
        mutableStateOf<Uri?>(null)
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
        NewMediaView(it, onClose = { pickedURI = null }, accountViewModel = accountViewModel, navController = navController)
    }

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
