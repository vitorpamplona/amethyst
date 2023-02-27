package com.vitorpamplona.amethyst.ui.screen

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.Divider
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.collectAsState
import androidx.navigation.NavController
import com.google.accompanist.swiperefresh.SwipeRefresh
import com.google.accompanist.swiperefresh.rememberSwipeRefreshState
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.ui.components.TranslateableRichTextViewer
import com.vitorpamplona.amethyst.ui.note.BlankNote
import com.vitorpamplona.amethyst.ui.note.HiddenNote
import com.vitorpamplona.amethyst.ui.note.NoteAuthorPicture
import com.vitorpamplona.amethyst.ui.note.NoteCompose
import com.vitorpamplona.amethyst.ui.note.NoteDropDownMenu
import com.vitorpamplona.amethyst.ui.note.NoteUsernameDisplay
import com.vitorpamplona.amethyst.ui.note.ReactionsRow
import com.vitorpamplona.amethyst.ui.note.timeAgo
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.components.ObserveDisplayNip05Status

@Composable
fun ThreadFeedView(noteId: String, viewModel: FeedViewModel, accountViewModel: AccountViewModel, navController: NavController) {
    val feedState by viewModel.feedContent.collectAsState()

    var isRefreshing by remember { mutableStateOf(false) }
    val swipeRefreshState = rememberSwipeRefreshState(isRefreshing)

    val listState = rememberLazyListState()

    LaunchedEffect(isRefreshing) {
        if (isRefreshing) {
            viewModel.refresh()
            isRefreshing = false
        }
    }

    SwipeRefresh(
        state = swipeRefreshState,
        onRefresh = {
            isRefreshing = true
        },
    ) {
        Column() {
            Crossfade(targetState = feedState, animationSpec = tween(durationMillis = 100)) { state ->
                when (state) {
                    is FeedState.Empty -> {
                        FeedEmpty {
                            isRefreshing = true
                        }
                    }
                    is FeedState.FeedError -> {
                        FeedError(state.errorMessage) {
                            isRefreshing = true
                        }
                    }
                    is FeedState.Loaded -> {
                        LaunchedEffect(noteId) {
                            val noteForPosition = state.feed.value.filter { it.idHex == noteId}.firstOrNull()
                            val position = state.feed.value.indexOf(noteForPosition)
                            if (position > 0)
                                listState.animateScrollToItem(position, 0)
                        }

                        LazyColumn(
                            contentPadding = PaddingValues(
                                top = 10.dp,
                                bottom = 10.dp
                            ),
                            state = listState
                        ) {
                            itemsIndexed(state.feed.value, key = { _, item -> item.idHex }) { index, item ->
                                if (index == 0)
                                    NoteMaster(item,
                                        modifier = Modifier.drawReplyLevel(
                                            item.replyLevel(),
                                            MaterialTheme.colors.onSurface.copy(alpha = 0.32f),
                                            if (item.idHex == noteId) MaterialTheme.colors.primary.copy(alpha = 0.52f) else MaterialTheme.colors.onSurface.copy(alpha = 0.32f)
                                        ),
                                        accountViewModel = accountViewModel,
                                        navController = navController
                                    )
                                else {
                                    Column() {
                                        Row() {
                                            NoteCompose(
                                                item,
                                                modifier = Modifier.drawReplyLevel(
                                                    item.replyLevel(),
                                                    MaterialTheme.colors.onSurface.copy(alpha = 0.32f),
                                                    if (item.idHex == noteId) MaterialTheme.colors.primary.copy(alpha = 0.52f) else MaterialTheme.colors.onSurface.copy(alpha = 0.32f)
                                                ),
                                                isBoostedNote = false,
                                                accountViewModel = accountViewModel,
                                                navController = navController,
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                    FeedState.Loading -> {
                        LoadingFeed()
                    }
                }
            }
        }
    }
}

// Creates a Zebra pattern where each bar is a reply level.
fun Modifier.drawReplyLevel(level: Int, color: Color, selected: Color): Modifier = this
    .drawBehind {
        val paddingDp = 2
        val strokeWidthDp = 2
        val levelWidthDp = strokeWidthDp + 1

        val padding = paddingDp.dp.toPx()
        val strokeWidth = strokeWidthDp.dp.toPx()
        val levelWidth = levelWidthDp.dp.toPx()

        repeat(level) {
            this.drawLine(
                if (it == level-1) selected else color,
                Offset(padding + it * levelWidth, 0f),
                Offset(padding + it * levelWidth, size.height),
                strokeWidth = strokeWidth
            )
        }

        return@drawBehind
    }
    .padding(start = (2 + (level * 3)).dp)

@Composable
fun NoteMaster(baseNote: Note,
               modifier: Modifier = Modifier,
               accountViewModel: AccountViewModel,
               navController: NavController
) {
    val noteState by baseNote.live().metadata.observeAsState()
    val note = noteState?.note

    val noteReportsState by baseNote.live().reports.observeAsState()
    val noteForReports = noteReportsState?.note ?: return

    val accountState by accountViewModel.accountLiveData.observeAsState()
    val account = accountState?.account ?: return

    var showHiddenNote by remember { mutableStateOf(false) }

    var moreActionsExpanded by remember { mutableStateOf(false) }

    val noteEvent = note?.event

    if (noteEvent == null) {
        BlankNote()
    } else if (!account.isAcceptable(noteForReports) && !showHiddenNote) {
        HiddenNote(
            account.getRelevantReports(noteForReports),
            account.userProfile(),
            Modifier,
            false,
            navController,
            onClick = { showHiddenNote = true }
        )
    } else {
        Column(
            modifier
                .fillMaxWidth()
                .padding(top = 10.dp)) {
            Row(modifier = Modifier
                .padding(start = 12.dp, end = 12.dp)
                .clickable(onClick = {
                    note.author?.let {
                        navController.navigate("User/${it.pubkeyHex}")
                    }
                })
            ) {
                NoteAuthorPicture(
                    note = baseNote,
                    navController = navController,
                    userAccount = account.userProfile(),
                    size = 55.dp
                )

                Column(modifier = Modifier.padding(start = 10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        NoteUsernameDisplay(baseNote, Modifier.weight(1f))

                        Text(
                            timeAgo(noteEvent.createdAt),
                            color = MaterialTheme.colors.onSurface.copy(alpha = 0.32f),
                            maxLines = 1
                        )

                        IconButton(
                            modifier = Modifier.then(Modifier.size(24.dp)),
                            onClick = { moreActionsExpanded = true }
                        ) {
                            Icon(
                                imageVector = Icons.Default.MoreVert,
                                null,
                                modifier = Modifier.size(15.dp),
                                tint = MaterialTheme.colors.onSurface.copy(alpha = 0.32f),
                            )

                            NoteDropDownMenu(baseNote, moreActionsExpanded, { moreActionsExpanded = false }, accountViewModel)
                        }
                    }

                    ObserveDisplayNip05Status(baseNote)
                }
            }

            Row(modifier = Modifier.padding(horizontal = 12.dp)) {
                Column() {
                    val eventContent = note.event?.content

                    val canPreview = note.author == account.userProfile()
                      || (note.author?.let { account.userProfile().isFollowing(it) } ?: true )
                      || !noteForReports.hasAnyReports()

                    if (eventContent != null) {
                        TranslateableRichTextViewer(
                            eventContent,
                            canPreview,
                            Modifier.fillMaxWidth(),
                            note.event?.tags,
                            MaterialTheme.colors.background,
                            accountViewModel,
                            navController
                        )
                    }

                    ReactionsRow(note, accountViewModel)

                    Divider(
                        modifier = Modifier.padding(top = 10.dp),
                        thickness = 0.25.dp
                    )
                }
            }
        }
    }
}