package com.vitorpamplona.amethyst.ui.screen

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Text
import androidx.compose.material3.pullrefresh.PullRefreshIndicator
import androidx.compose.material3.pullrefresh.pullRefresh
import androidx.compose.material3.pullrefresh.rememberPullRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.ui.components.ObserveDisplayNip05Status
import com.vitorpamplona.amethyst.ui.note.AudioHeader
import com.vitorpamplona.amethyst.ui.note.AudioTrackHeader
import com.vitorpamplona.amethyst.ui.note.BadgeDisplay
import com.vitorpamplona.amethyst.ui.note.BlankNote
import com.vitorpamplona.amethyst.ui.note.CreateImageHeader
import com.vitorpamplona.amethyst.ui.note.DisplayFollowingCommunityInPost
import com.vitorpamplona.amethyst.ui.note.DisplayFollowingHashtagsInPost
import com.vitorpamplona.amethyst.ui.note.DisplayHighlight
import com.vitorpamplona.amethyst.ui.note.DisplayLocation
import com.vitorpamplona.amethyst.ui.note.DisplayPeopleList
import com.vitorpamplona.amethyst.ui.note.DisplayPoW
import com.vitorpamplona.amethyst.ui.note.DisplayRelaySet
import com.vitorpamplona.amethyst.ui.note.DisplayReward
import com.vitorpamplona.amethyst.ui.note.DisplayZapSplits
import com.vitorpamplona.amethyst.ui.note.FileHeaderDisplay
import com.vitorpamplona.amethyst.ui.note.FileStorageHeaderDisplay
import com.vitorpamplona.amethyst.ui.note.HiddenNote
import com.vitorpamplona.amethyst.ui.note.NoteAuthorPicture
import com.vitorpamplona.amethyst.ui.note.NoteCompose
import com.vitorpamplona.amethyst.ui.note.NoteDropDownMenu
import com.vitorpamplona.amethyst.ui.note.NoteQuickActionMenu
import com.vitorpamplona.amethyst.ui.note.NoteUsernameDisplay
import com.vitorpamplona.amethyst.ui.note.ReactionsRow
import com.vitorpamplona.amethyst.ui.note.RenderAppDefinition
import com.vitorpamplona.amethyst.ui.note.RenderEmojiPack
import com.vitorpamplona.amethyst.ui.note.RenderPinListEvent
import com.vitorpamplona.amethyst.ui.note.RenderPoll
import com.vitorpamplona.amethyst.ui.note.RenderPostApproval
import com.vitorpamplona.amethyst.ui.note.RenderRepost
import com.vitorpamplona.amethyst.ui.note.RenderTextEvent
import com.vitorpamplona.amethyst.ui.note.Reward
import com.vitorpamplona.amethyst.ui.note.timeAgo
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.screen.loggedIn.ChannelHeader
import com.vitorpamplona.amethyst.ui.theme.DividerThickness
import com.vitorpamplona.amethyst.ui.theme.DoubleVertSpacer
import com.vitorpamplona.amethyst.ui.theme.FeedPadding
import com.vitorpamplona.amethyst.ui.theme.SmallBorder
import com.vitorpamplona.amethyst.ui.theme.lessImportantLink
import com.vitorpamplona.amethyst.ui.theme.placeholderText
import com.vitorpamplona.amethyst.ui.theme.selectedNote
import com.vitorpamplona.quartz.events.AppDefinitionEvent
import com.vitorpamplona.quartz.events.AudioHeaderEvent
import com.vitorpamplona.quartz.events.AudioTrackEvent
import com.vitorpamplona.quartz.events.BadgeDefinitionEvent
import com.vitorpamplona.quartz.events.ChannelCreateEvent
import com.vitorpamplona.quartz.events.ChannelMetadataEvent
import com.vitorpamplona.quartz.events.ClassifiedsEvent
import com.vitorpamplona.quartz.events.CommunityDefinitionEvent
import com.vitorpamplona.quartz.events.CommunityPostApprovalEvent
import com.vitorpamplona.quartz.events.EmojiPackEvent
import com.vitorpamplona.quartz.events.FileHeaderEvent
import com.vitorpamplona.quartz.events.FileStorageHeaderEvent
import com.vitorpamplona.quartz.events.GenericRepostEvent
import com.vitorpamplona.quartz.events.HighlightEvent
import com.vitorpamplona.quartz.events.LongTextNoteEvent
import com.vitorpamplona.quartz.events.PeopleListEvent
import com.vitorpamplona.quartz.events.PinListEvent
import com.vitorpamplona.quartz.events.PollNoteEvent
import com.vitorpamplona.quartz.events.RelaySetEvent
import com.vitorpamplona.quartz.events.RepostEvent
import kotlinx.collections.immutable.toImmutableSet
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun ThreadFeedView(noteId: String, viewModel: FeedViewModel, accountViewModel: AccountViewModel, nav: (String) -> Unit) {
    val feedState by viewModel.feedContent.collectAsStateWithLifecycle()

    val listState = rememberLazyListState()

    var refreshing by remember { mutableStateOf(false) }
    val refresh = { refreshing = true; viewModel.invalidateData(); refreshing = false }
    val pullRefreshState = rememberPullRefreshState(refreshing, onRefresh = refresh)

    Box(Modifier.pullRefresh(pullRefreshState)) {
        Column() {
            Crossfade(
                targetState = feedState,
                animationSpec = tween(durationMillis = 100),
                label = "ThreadViewMainState"
            ) { state ->
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
                            launch(Dispatchers.IO) {
                                // waits to load the thread to scroll to item.
                                delay(100)
                                val noteForPosition = state.feed.value.filter { it.idHex == noteId }.firstOrNull()
                                var position = state.feed.value.indexOf(noteForPosition)

                                if (position >= 0) {
                                    if (position >= 1 && position < state.feed.value.size - 1) {
                                        position-- // show the replying note
                                    }

                                    withContext(Dispatchers.Main) {
                                        listState.scrollToItem(position)
                                    }
                                }
                            }
                        }

                        LazyColumn(
                            contentPadding = FeedPadding,
                            state = listState
                        ) {
                            itemsIndexed(state.feed.value, key = { _, item -> item.idHex }) { index, item ->
                                if (index == 0) {
                                    ProvideTextStyle(TextStyle(fontSize = 18.sp, lineHeight = 1.20.em)) {
                                        NoteMaster(
                                            item,
                                            modifier = Modifier.drawReplyLevel(
                                                item.replyLevel(),
                                                MaterialTheme.colorScheme.placeholderText,
                                                if (item.idHex == noteId) MaterialTheme.colorScheme.lessImportantLink else MaterialTheme.colorScheme.placeholderText
                                            ),
                                            accountViewModel = accountViewModel,
                                            nav = nav
                                        )
                                    }
                                } else {
                                    Column() {
                                        Row() {
                                            val selectedNoteColor = MaterialTheme.colorScheme.selectedNote
                                            val background = remember {
                                                if (item.idHex == noteId) mutableStateOf(selectedNoteColor) else null
                                            }

                                            NoteCompose(
                                                item,
                                                modifier = Modifier.drawReplyLevel(
                                                    item.replyLevel(),
                                                    MaterialTheme.colorScheme.placeholderText,
                                                    if (item.idHex == noteId) MaterialTheme.colorScheme.lessImportantLink else MaterialTheme.colorScheme.placeholderText
                                                ),
                                                parentBackgroundColor = background,
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

    val moreActionsExpanded = remember { mutableStateOf(false) }
    val enablePopup = remember {
        { moreActionsExpanded.value = true }
    }

    val noteEvent = note?.event

    var popupExpanded by remember { mutableStateOf(false) }

    val defaultBackgroundColor = MaterialTheme.colorScheme.background
    val backgroundColor = remember { mutableStateOf<Color>(defaultBackgroundColor) }

    if (noteEvent == null) {
        BlankNote()
    } else if (!account.isAcceptable(noteForReports) && !showHiddenNote) {
        val reports = remember {
            account.getRelevantReports(noteForReports).toImmutableSet()
        }

        HiddenNote(
            reports,
            note.author?.let { account.isHidden(it) } ?: false,
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

                        val isCommunityPost by remember(baseNote) {
                            derivedStateOf {
                                baseNote.event?.isTaggedAddressableKind(CommunityDefinitionEvent.kind) == true
                            }
                        }

                        if (isCommunityPost) {
                            DisplayFollowingCommunityInPost(baseNote, accountViewModel, nav)
                        } else {
                            DisplayFollowingHashtagsInPost(baseNote, accountViewModel, nav)
                        }

                        Text(
                            timeAgo(note.createdAt(), context = context),
                            color = MaterialTheme.colorScheme.placeholderText,
                            maxLines = 1
                        )

                        IconButton(
                            modifier = Modifier.then(Modifier.size(24.dp)),
                            onClick = enablePopup
                        ) {
                            Icon(
                                imageVector = Icons.Default.MoreVert,
                                null,
                                modifier = Modifier.size(15.dp),
                                tint = MaterialTheme.colorScheme.placeholderText
                            )

                            NoteDropDownMenu(baseNote, moreActionsExpanded, accountViewModel)
                        }
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        ObserveDisplayNip05Status(baseNote, remember { Modifier.weight(1f) }, accountViewModel, nav)

                        val geo = remember { noteEvent.getGeoHash() }
                        if (geo != null) {
                            DisplayLocation(geo, nav)
                        }

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
                RenderLongFormHeaderForThread(noteEvent)
            } else if (noteEvent is ClassifiedsEvent) {
                RenderClassifiedsReaderForThread(noteEvent, note, accountViewModel)
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
                    if ((noteEvent is ChannelCreateEvent || noteEvent is ChannelMetadataEvent) && note.channelHex() != null) {
                        ChannelHeader(
                            channelHex = note.channelHex()!!,
                            showVideo = true,
                            showBottomDiviser = false,
                            sendToChannel = true,
                            accountViewModel = accountViewModel,
                            nav = nav
                        )
                    } else if (noteEvent is FileHeaderEvent) {
                        FileHeaderDisplay(baseNote, true, accountViewModel)
                    } else if (noteEvent is FileStorageHeaderEvent) {
                        FileStorageHeaderDisplay(baseNote, true, accountViewModel)
                    } else if (noteEvent is PeopleListEvent) {
                        DisplayPeopleList(baseNote, backgroundColor, accountViewModel, nav)
                    } else if (noteEvent is AudioTrackEvent) {
                        AudioTrackHeader(noteEvent, baseNote, accountViewModel, nav)
                    } else if (noteEvent is AudioHeaderEvent) {
                        AudioHeader(noteEvent, baseNote, accountViewModel, nav)
                    } else if (noteEvent is CommunityPostApprovalEvent) {
                        RenderPostApproval(
                            baseNote,
                            false,
                            true,
                            backgroundColor,
                            accountViewModel,
                            nav
                        )
                    } else if (noteEvent is PinListEvent) {
                        RenderPinListEvent(
                            baseNote,
                            backgroundColor,
                            accountViewModel,
                            nav
                        )
                    } else if (noteEvent is EmojiPackEvent) {
                        RenderEmojiPack(
                            baseNote,
                            true,
                            backgroundColor,
                            accountViewModel
                        )
                    } else if (noteEvent is RelaySetEvent) {
                        DisplayRelaySet(
                            baseNote,
                            backgroundColor,
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
                            noteEvent.inPost(),
                            false,
                            true,
                            backgroundColor,
                            accountViewModel,
                            nav
                        )
                    } else if (noteEvent is RepostEvent || noteEvent is GenericRepostEvent) {
                        RenderRepost(baseNote, backgroundColor, accountViewModel, nav)
                    } else if (noteEvent is PollNoteEvent) {
                        val canPreview = note.author == account.userProfile() ||
                            (note.author?.let { account.userProfile().isFollowingCached(it) } ?: true) ||
                            !noteForReports.hasAnyReports()

                        RenderPoll(
                            baseNote,
                            false,
                            canPreview,
                            backgroundColor,
                            accountViewModel,
                            nav
                        )
                    } else {
                        val canPreview = note.author == account.userProfile() ||
                            (note.author?.let { account.userProfile().isFollowingCached(it) } ?: true) ||
                            !noteForReports.hasAnyReports()

                        RenderTextEvent(
                            baseNote,
                            false,
                            canPreview,
                            backgroundColor,
                            accountViewModel,
                            nav
                        )
                    }
                }
            }

            val noteEvent = baseNote.event
            val zapSplits = remember(noteEvent) { noteEvent?.hasZapSplitSetup() ?: false }
            if (zapSplits && noteEvent != null) {
                Spacer(modifier = DoubleVertSpacer)
                DisplayZapSplits(noteEvent, accountViewModel, nav)
            }

            ReactionsRow(note, true, accountViewModel, nav)

            Divider(
                thickness = DividerThickness
            )
        }

        NoteQuickActionMenu(note, popupExpanded, { popupExpanded = false }, accountViewModel)
    }
}

@Composable
private fun RenderClassifiedsReaderForThread(
    noteEvent: ClassifiedsEvent,
    note: Note,
    accountViewModel: AccountViewModel
) {
    val image = remember(noteEvent) { noteEvent.image() }
    val title = remember(noteEvent) { noteEvent.title() }
    val summary =
        remember(noteEvent) { noteEvent.summary() ?: noteEvent.content.take(200).ifBlank { null } }
    val price = remember(noteEvent) { noteEvent.price() }
    val location = remember(noteEvent) { noteEvent.location() }

    Row(modifier = Modifier.padding(start = 12.dp, end = 12.dp, bottom = 12.dp)) {
        Column {
            Row() {
                image?.let {
                    AsyncImage(
                        model = it,
                        contentDescription = stringResource(
                            R.string.preview_card_image_for,
                            it
                        ),
                        contentScale = ContentScale.FillWidth,
                        modifier = Modifier.fillMaxWidth()
                    )
                } ?: CreateImageHeader(note, accountViewModel)
            }

            Row(
                Modifier.padding(start = 10.dp, end = 10.dp, top = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                title?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodyLarge,
                        maxLines = 1,
                        modifier = Modifier.weight(1f)
                    )
                }

                price?.let {
                    val priceTag = remember(noteEvent) {
                        if (price.frequency != null && price.currency != null) {
                            "${price.amount} ${price.currency}/${price.frequency}"
                        } else if (price.currency != null) {
                            "${price.amount} ${price.currency}"
                        } else {
                            price.amount
                        }
                    }

                    Text(
                        text = priceTag,
                        maxLines = 1,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                        modifier = remember {
                            Modifier
                                .clip(SmallBorder)
                                .background(Color.Black)
                                .padding(start = 5.dp)
                        }
                    )
                }
            }

            summary?.let {
                Row(
                    Modifier.padding(start = 10.dp, end = 10.dp, top = 5.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.weight(1f),
                        color = Color.Gray,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            location?.let {
                Row(
                    Modifier.padding(start = 10.dp, end = 10.dp, top = 5.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Gray,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun RenderLongFormHeaderForThread(noteEvent: LongTextNoteEvent) {
    Row(modifier = Modifier.padding(start = 12.dp, end = 12.dp, bottom = 12.dp)) {
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
                Spacer(modifier = DoubleVertSpacer)
                Text(
                    text = it,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            noteEvent.summary()?.ifBlank { null }?.let {
                Spacer(modifier = DoubleVertSpacer)
                Text(
                    text = it,
                    modifier = Modifier.fillMaxWidth(),
                    color = Color.Gray
                )
            }
        }
    }
}
