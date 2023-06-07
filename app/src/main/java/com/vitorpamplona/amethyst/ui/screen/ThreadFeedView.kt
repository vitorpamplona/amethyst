package com.vitorpamplona.amethyst.ui.screen

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.Divider
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.MaterialTheme
import androidx.compose.material.SnackbarDefaults.backgroundColor
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.pullrefresh.PullRefreshIndicator
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
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
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.service.model.AppDefinitionEvent
import com.vitorpamplona.amethyst.service.model.AudioTrackEvent
import com.vitorpamplona.amethyst.service.model.BadgeDefinitionEvent
import com.vitorpamplona.amethyst.service.model.HighlightEvent
import com.vitorpamplona.amethyst.service.model.LongTextNoteEvent
import com.vitorpamplona.amethyst.service.model.PeopleListEvent
import com.vitorpamplona.amethyst.service.model.PinListEvent
import com.vitorpamplona.amethyst.service.model.PollNoteEvent
import com.vitorpamplona.amethyst.service.model.RelaySetEvent
import com.vitorpamplona.amethyst.ui.actions.ImmutableListOfLists
import com.vitorpamplona.amethyst.ui.actions.toImmutableListOfLists
import com.vitorpamplona.amethyst.ui.components.ObserveDisplayNip05Status
import com.vitorpamplona.amethyst.ui.components.SensitivityWarning
import com.vitorpamplona.amethyst.ui.components.TranslatableRichTextViewer
import com.vitorpamplona.amethyst.ui.note.*
import com.vitorpamplona.amethyst.ui.note.BadgeDisplay
import com.vitorpamplona.amethyst.ui.note.BlankNote
import com.vitorpamplona.amethyst.ui.note.DisplayFollowingHashtagsInPost
import com.vitorpamplona.amethyst.ui.note.DisplayPoW
import com.vitorpamplona.amethyst.ui.note.DisplayReward
import com.vitorpamplona.amethyst.ui.note.DisplayUncitedHashtags
import com.vitorpamplona.amethyst.ui.note.HiddenNote
import com.vitorpamplona.amethyst.ui.note.NoteAuthorPicture
import com.vitorpamplona.amethyst.ui.note.NoteCompose
import com.vitorpamplona.amethyst.ui.note.NoteDropDownMenu
import com.vitorpamplona.amethyst.ui.note.NoteQuickActionMenu
import com.vitorpamplona.amethyst.ui.note.NoteUsernameDisplay
import com.vitorpamplona.amethyst.ui.note.ReactionsRow
import com.vitorpamplona.amethyst.ui.note.timeAgo
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.theme.newItemBackgroundColor
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toImmutableSet
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterialApi::class)
@Composable
fun ThreadFeedView(noteId: String, viewModel: FeedViewModel, accountViewModel: AccountViewModel, nav: (String) -> Unit) {
    val feedState by viewModel.feedContent.collectAsState()

    val listState = rememberLazyListState()

    var refreshing by remember { mutableStateOf(false) }
    val refresh = { refreshing = true; viewModel.invalidateData(); refreshing = false }
    val pullRefreshState = rememberPullRefreshState(refreshing, onRefresh = refresh)

    Box(Modifier.pullRefresh(pullRefreshState)) {
        Column() {
            Crossfade(targetState = feedState, animationSpec = tween(durationMillis = 100)) { state ->
                when (state) {
                    is FeedState.Empty -> {
                        FeedEmpty {
                            refreshing = true
                        }
                    }
                    is FeedState.FeedError -> {
                        FeedError(state.errorMessage) {
                            refreshing = true
                        }
                    }
                    is FeedState.Loaded -> {
                        refreshing = false
                        LaunchedEffect(noteId) {
                            // waits to load the thread to scroll to item.
                            delay(100)
                            val noteForPosition = state.feed.value.filter { it.idHex == noteId }.firstOrNull()
                            var position = state.feed.value.indexOf(noteForPosition)

                            if (position >= 0) {
                                if (position >= 1 && position < state.feed.value.size - 1) {
                                    position-- // show the replying note
                                }

                                listState.animateScrollToItem(position)
                            }
                        }

                        LazyColumn(
                            contentPadding = PaddingValues(
                                top = 10.dp,
                                bottom = 10.dp
                            ),
                            state = listState
                        ) {
                            itemsIndexed(state.feed.value, key = { _, item -> item.idHex }) { index, item ->
                                if (index == 0) {
                                    NoteMaster(
                                        item,
                                        modifier = Modifier.drawReplyLevel(
                                            item.replyLevel(),
                                            MaterialTheme.colors.onSurface.copy(alpha = 0.32f),
                                            if (item.idHex == noteId) MaterialTheme.colors.primary.copy(alpha = 0.52f) else MaterialTheme.colors.onSurface.copy(alpha = 0.32f)
                                        ),
                                        accountViewModel = accountViewModel,
                                        nav = nav
                                    )
                                } else {
                                    Column() {
                                        Row() {
                                            NoteCompose(
                                                item,
                                                modifier = Modifier.drawReplyLevel(
                                                    item.replyLevel(),
                                                    MaterialTheme.colors.onSurface.copy(alpha = 0.32f),
                                                    if (item.idHex == noteId) MaterialTheme.colors.primary.copy(alpha = 0.52f) else MaterialTheme.colors.onSurface.copy(alpha = 0.32f)
                                                ),
                                                parentBackgroundColor = if (item.idHex == noteId) MaterialTheme.colors.newItemBackgroundColor.compositeOver(MaterialTheme.colors.background) else null,
                                                isBoostedNote = false,
                                                unPackReply = false,
                                                accountViewModel = accountViewModel,
                                                nav = nav
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

        PullRefreshIndicator(refreshing, pullRefreshState, Modifier.align(Alignment.TopCenter))
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
                if (it == level - 1) selected else color,
                Offset(padding + it * levelWidth, 0f),
                Offset(padding + it * levelWidth, size.height),
                strokeWidth = strokeWidth
            )
        }

        return@drawBehind
    }
    .padding(start = (2 + (level * 3)).dp)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun NoteMaster(
    baseNote: Note,
    modifier: Modifier = Modifier,
    accountViewModel: AccountViewModel,
    nav: (String) -> Unit
) {
    val noteState by baseNote.live().metadata.observeAsState()
    val note = noteState?.note

    val noteReportsState by baseNote.live().reports.observeAsState()
    val noteForReports = noteReportsState?.note ?: return

    val accountState by accountViewModel.accountLiveData.observeAsState()
    val account = accountState?.account ?: return

    var showHiddenNote by remember { mutableStateOf(false) }

    val context = LocalContext.current

    var moreActionsExpanded by remember { mutableStateOf(false) }

    val noteEvent = note?.event

    var popupExpanded by remember { mutableStateOf(false) }

    if (noteEvent == null) {
        BlankNote()
    } else if (!account.isAcceptable(noteForReports) && !showHiddenNote) {
        val reports = remember {
            account.getRelevantReports(noteForReports).toImmutableSet()
        }

        HiddenNote(
            reports,
            accountViewModel,
            Modifier,
            false,
            nav,
            onClick = { showHiddenNote = true }
        )
    } else {
        Column(
            modifier
                .fillMaxWidth()
                .padding(top = 10.dp)
        ) {
            Row(
                modifier = Modifier
                    .padding(start = 12.dp, end = 12.dp)
                    .clickable(onClick = {
                        note.author?.let {
                            nav("User/${it.pubkeyHex}")
                        }
                    })
            ) {
                NoteAuthorPicture(
                    baseNote = baseNote,
                    nav = nav,
                    accountViewModel = accountViewModel,
                    size = 55.dp
                )

                Column(modifier = Modifier.padding(start = 10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        NoteUsernameDisplay(baseNote, Modifier.weight(1f))

                        DisplayFollowingHashtagsInPost(noteEvent, accountViewModel, nav)

                        Text(
                            timeAgo(note.createdAt(), context = context),
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
                                tint = MaterialTheme.colors.onSurface.copy(alpha = 0.32f)
                            )

                            NoteDropDownMenu(baseNote, moreActionsExpanded, { moreActionsExpanded = false }, accountViewModel)
                        }
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        ObserveDisplayNip05Status(baseNote, remember { Modifier.weight(1f) })

                        val baseReward = remember { noteEvent.getReward()?.let { Reward(it) } }
                        if (baseReward != null) {
                            DisplayReward(baseReward, baseNote, accountViewModel, nav)
                        }

                        val pow = remember { noteEvent.getPoWRank() }
                        if (pow > 20) {
                            DisplayPoW(pow)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            if (noteEvent is BadgeDefinitionEvent) {
                BadgeDisplay(baseNote = note)
            } else if (noteEvent is LongTextNoteEvent) {
                Row(modifier = Modifier.padding(start = 12.dp, end = 12.dp, top = 10.dp, bottom = 12.dp)) {
                    Column {
                        noteEvent.image()?.let {
                            AsyncImage(
                                model = it,
                                contentDescription = stringResource(
                                    R.string.preview_card_image_for,
                                    it
                                ),
                                contentScale = ContentScale.FillWidth,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        noteEvent.title()?.let {
                            Text(
                                text = it,
                                fontSize = 30.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 10.dp)
                            )
                        }

                        noteEvent.summary()?.let {
                            Text(
                                text = it,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 10.dp),
                                color = Color.Gray
                            )
                        }
                    }
                }
            }

            Row(
                modifier = Modifier
                    .padding(horizontal = 12.dp)
                    .combinedClickable(
                        onClick = { },
                        onLongClick = { popupExpanded = true }
                    )
            ) {
                Column() {
                    if (noteEvent is PeopleListEvent) {
                        DisplayPeopleList(baseNote, MaterialTheme.colors.background, accountViewModel, nav)
                    } else if (noteEvent is AudioTrackEvent) {
                        AudioTrackHeader(noteEvent, accountViewModel, nav)
                    } else if (noteEvent is PinListEvent) {
                        PinListHeader(
                            baseNote,
                            MaterialTheme.colors.background,
                            accountViewModel,
                            nav
                        )
                    } else if (noteEvent is RelaySetEvent) {
                        DisplayRelaySet(
                            baseNote,
                            MaterialTheme.colors.background,
                            accountViewModel,
                            nav
                        )
                    } else if (noteEvent is AppDefinitionEvent) {
                        RenderAppDefinition(baseNote, accountViewModel, nav)
                    } else if (noteEvent is HighlightEvent) {
                        DisplayHighlight(
                            noteEvent.quote(),
                            noteEvent.author(),
                            noteEvent.inUrl(),
                            false,
                            true,
                            backgroundColor,
                            accountViewModel,
                            nav
                        )
                    } else {
                        val eventContent = note.event?.content()

                        val canPreview = note.author == account.userProfile() ||
                            (note.author?.let { account.userProfile().isFollowingCached(it) } ?: true) ||
                            !noteForReports.hasAnyReports()

                        if (eventContent != null) {
                            val hasSensitiveContent = remember(note.event) { note.event?.isSensitive() ?: false }
                            val tags = remember(note) { note.event?.tags()?.toImmutableListOfLists() ?: ImmutableListOfLists() }

                            SensitivityWarning(
                                hasSensitiveContent = hasSensitiveContent,
                                accountViewModel = accountViewModel
                            ) {
                                TranslatableRichTextViewer(
                                    eventContent,
                                    canPreview,
                                    remember { Modifier.fillMaxWidth() },
                                    tags,
                                    MaterialTheme.colors.background,
                                    accountViewModel,
                                    nav
                                )
                            }

                            val hashtags = remember {
                                noteEvent.hashtags().toImmutableList()
                            }
                            DisplayUncitedHashtags(hashtags, eventContent, nav)

                            if (noteEvent is PollNoteEvent) {
                                PollNote(
                                    note,
                                    canPreview,
                                    backgroundColor,
                                    accountViewModel,
                                    nav
                                )
                            }
                        }
                    }
                }
            }

            ReactionsRow(note, true, accountViewModel, nav)

            Divider(
                modifier = Modifier.padding(top = 10.dp),
                thickness = 0.25.dp
            )
        }

        NoteQuickActionMenu(note, popupExpanded, { popupExpanded = false }, accountViewModel)
    }
}
