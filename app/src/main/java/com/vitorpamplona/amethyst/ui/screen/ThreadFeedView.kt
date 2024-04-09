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
package com.vitorpamplona.amethyst.ui.screen

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.model.LocalCache
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.ui.components.GenericLoadable
import com.vitorpamplona.amethyst.ui.components.InlineCarrousel
import com.vitorpamplona.amethyst.ui.components.LoadNote
import com.vitorpamplona.amethyst.ui.components.ObserveDisplayNip05Status
import com.vitorpamplona.amethyst.ui.components.mockAccountViewModel
import com.vitorpamplona.amethyst.ui.navigation.routeToMessage
import com.vitorpamplona.amethyst.ui.note.BlankNote
import com.vitorpamplona.amethyst.ui.note.DisplayDraft
import com.vitorpamplona.amethyst.ui.note.DisplayOtsIfInOriginal
import com.vitorpamplona.amethyst.ui.note.HiddenNote
import com.vitorpamplona.amethyst.ui.note.LoadAddressableNote
import com.vitorpamplona.amethyst.ui.note.NoteAuthorPicture
import com.vitorpamplona.amethyst.ui.note.NoteCompose
import com.vitorpamplona.amethyst.ui.note.NoteQuickActionMenu
import com.vitorpamplona.amethyst.ui.note.NoteUsernameDisplay
import com.vitorpamplona.amethyst.ui.note.ReactionsRow
import com.vitorpamplona.amethyst.ui.note.RenderDraft
import com.vitorpamplona.amethyst.ui.note.RenderRepost
import com.vitorpamplona.amethyst.ui.note.elements.DefaultImageHeader
import com.vitorpamplona.amethyst.ui.note.elements.DisplayEditStatus
import com.vitorpamplona.amethyst.ui.note.elements.DisplayFollowingCommunityInPost
import com.vitorpamplona.amethyst.ui.note.elements.DisplayFollowingHashtagsInPost
import com.vitorpamplona.amethyst.ui.note.elements.DisplayLocation
import com.vitorpamplona.amethyst.ui.note.elements.DisplayPoW
import com.vitorpamplona.amethyst.ui.note.elements.DisplayReward
import com.vitorpamplona.amethyst.ui.note.elements.DisplayZapSplits
import com.vitorpamplona.amethyst.ui.note.elements.ForkInformationRow
import com.vitorpamplona.amethyst.ui.note.elements.NoteDropDownMenu
import com.vitorpamplona.amethyst.ui.note.elements.Reward
import com.vitorpamplona.amethyst.ui.note.observeEdits
import com.vitorpamplona.amethyst.ui.note.showAmount
import com.vitorpamplona.amethyst.ui.note.timeAgo
import com.vitorpamplona.amethyst.ui.note.types.AudioHeader
import com.vitorpamplona.amethyst.ui.note.types.AudioTrackHeader
import com.vitorpamplona.amethyst.ui.note.types.BadgeDisplay
import com.vitorpamplona.amethyst.ui.note.types.DisplayHighlight
import com.vitorpamplona.amethyst.ui.note.types.DisplayPeopleList
import com.vitorpamplona.amethyst.ui.note.types.DisplayRelaySet
import com.vitorpamplona.amethyst.ui.note.types.EditState
import com.vitorpamplona.amethyst.ui.note.types.FileHeaderDisplay
import com.vitorpamplona.amethyst.ui.note.types.FileStorageHeaderDisplay
import com.vitorpamplona.amethyst.ui.note.types.RenderAppDefinition
import com.vitorpamplona.amethyst.ui.note.types.RenderChannelMessage
import com.vitorpamplona.amethyst.ui.note.types.RenderEmojiPack
import com.vitorpamplona.amethyst.ui.note.types.RenderFhirResource
import com.vitorpamplona.amethyst.ui.note.types.RenderGitIssueEvent
import com.vitorpamplona.amethyst.ui.note.types.RenderGitPatchEvent
import com.vitorpamplona.amethyst.ui.note.types.RenderGitRepositoryEvent
import com.vitorpamplona.amethyst.ui.note.types.RenderLiveActivityChatMessage
import com.vitorpamplona.amethyst.ui.note.types.RenderPinListEvent
import com.vitorpamplona.amethyst.ui.note.types.RenderPoll
import com.vitorpamplona.amethyst.ui.note.types.RenderPostApproval
import com.vitorpamplona.amethyst.ui.note.types.RenderPrivateMessage
import com.vitorpamplona.amethyst.ui.note.types.RenderTextEvent
import com.vitorpamplona.amethyst.ui.note.types.RenderTextModificationEvent
import com.vitorpamplona.amethyst.ui.note.types.VideoDisplay
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.screen.loggedIn.ChannelHeader
import com.vitorpamplona.amethyst.ui.screen.loggedIn.ThinSendButton
import com.vitorpamplona.amethyst.ui.theme.DividerThickness
import com.vitorpamplona.amethyst.ui.theme.DoubleVertSpacer
import com.vitorpamplona.amethyst.ui.theme.EditFieldBorder
import com.vitorpamplona.amethyst.ui.theme.EditFieldTrailingIconModifier
import com.vitorpamplona.amethyst.ui.theme.FeedPadding
import com.vitorpamplona.amethyst.ui.theme.Size15Modifier
import com.vitorpamplona.amethyst.ui.theme.Size24Modifier
import com.vitorpamplona.amethyst.ui.theme.StdHorzSpacer
import com.vitorpamplona.amethyst.ui.theme.ThemeComparisonColumn
import com.vitorpamplona.amethyst.ui.theme.lessImportantLink
import com.vitorpamplona.amethyst.ui.theme.placeholderText
import com.vitorpamplona.amethyst.ui.theme.selectedNote
import com.vitorpamplona.quartz.events.AppDefinitionEvent
import com.vitorpamplona.quartz.events.AudioHeaderEvent
import com.vitorpamplona.quartz.events.AudioTrackEvent
import com.vitorpamplona.quartz.events.BadgeDefinitionEvent
import com.vitorpamplona.quartz.events.ChannelCreateEvent
import com.vitorpamplona.quartz.events.ChannelMessageEvent
import com.vitorpamplona.quartz.events.ChannelMetadataEvent
import com.vitorpamplona.quartz.events.ClassifiedsEvent
import com.vitorpamplona.quartz.events.CommunityDefinitionEvent
import com.vitorpamplona.quartz.events.CommunityPostApprovalEvent
import com.vitorpamplona.quartz.events.DraftEvent
import com.vitorpamplona.quartz.events.EmojiPackEvent
import com.vitorpamplona.quartz.events.Event
import com.vitorpamplona.quartz.events.FhirResourceEvent
import com.vitorpamplona.quartz.events.FileHeaderEvent
import com.vitorpamplona.quartz.events.FileStorageHeaderEvent
import com.vitorpamplona.quartz.events.GenericRepostEvent
import com.vitorpamplona.quartz.events.GitIssueEvent
import com.vitorpamplona.quartz.events.GitPatchEvent
import com.vitorpamplona.quartz.events.GitRepositoryEvent
import com.vitorpamplona.quartz.events.HighlightEvent
import com.vitorpamplona.quartz.events.LiveActivitiesChatMessageEvent
import com.vitorpamplona.quartz.events.LongTextNoteEvent
import com.vitorpamplona.quartz.events.PeopleListEvent
import com.vitorpamplona.quartz.events.PinListEvent
import com.vitorpamplona.quartz.events.PollNoteEvent
import com.vitorpamplona.quartz.events.PrivateDmEvent
import com.vitorpamplona.quartz.events.RelaySetEvent
import com.vitorpamplona.quartz.events.RepostEvent
import com.vitorpamplona.quartz.events.TextNoteModificationEvent
import com.vitorpamplona.quartz.events.VideoEvent
import com.vitorpamplona.quartz.events.WikiNoteEvent
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toImmutableSet
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext

@Composable
fun ThreadFeedView(
    noteId: String,
    viewModel: FeedViewModel,
    accountViewModel: AccountViewModel,
    nav: (String) -> Unit,
) {
    val listState = rememberLazyListState()

    RefresheableBox(viewModel) {
        RenderFeedState(
            viewModel = viewModel,
            accountViewModel = accountViewModel,
            listState = listState,
            nav = nav,
            routeForLastRead = null,
            onLoaded = {
                RenderThreadFeed(noteId, it, listState, accountViewModel, nav)
            },
        )
    }
}

@Composable
fun RenderThreadFeed(
    noteId: String,
    state: FeedState.Loaded,
    listState: LazyListState,
    accountViewModel: AccountViewModel,
    nav: (String) -> Unit,
) {
    LaunchedEffect(noteId) {
        // waits to load the thread to scroll to item.
        delay(100)
        val noteForPosition = state.feed.value.filter { it.idHex == noteId }.firstOrNull()
        var position = state.feed.value.indexOf(noteForPosition)

        if (position >= 0) {
            if (position >= 1 && position < state.feed.value.size - 1) {
                position-- // show the replying note
            }

            listState.scrollToItem(position)
        }
    }

    LazyColumn(
        contentPadding = FeedPadding,
        state = listState,
    ) {
        itemsIndexed(state.feed.value, key = { _, item -> item.idHex }) { index, item ->
            if (index == 0) {
                ProvideTextStyle(TextStyle(fontSize = 18.sp, lineHeight = 1.20.em)) {
                    NoteMaster(
                        item,
                        modifier =
                            Modifier.drawReplyLevel(
                                item.replyLevel(),
                                MaterialTheme.colorScheme.placeholderText,
                                if (item.idHex == noteId) {
                                    MaterialTheme.colorScheme.lessImportantLink
                                } else {
                                    MaterialTheme.colorScheme.placeholderText
                                },
                            ),
                        accountViewModel = accountViewModel,
                        nav = nav,
                    )
                }
            } else {
                val selectedNoteColor = MaterialTheme.colorScheme.selectedNote
                val background =
                    remember {
                        if (item.idHex == noteId) mutableStateOf(selectedNoteColor) else null
                    }

                NoteCompose(
                    item,
                    modifier =
                        Modifier.drawReplyLevel(
                            item.replyLevel(),
                            MaterialTheme.colorScheme.placeholderText,
                            if (item.idHex == noteId) {
                                MaterialTheme.colorScheme.lessImportantLink
                            } else {
                                MaterialTheme.colorScheme.placeholderText
                            },
                        ),
                    parentBackgroundColor = background,
                    isBoostedNote = false,
                    unPackReply = false,
                    quotesLeft = 3,
                    accountViewModel = accountViewModel,
                    nav = nav,
                )
            }

            HorizontalDivider(
                thickness = DividerThickness,
            )
        }
    }
}

// Creates a Zebra pattern where each bar is a reply level.
fun Modifier.drawReplyLevel(
    level: Int,
    color: Color,
    selected: Color,
): Modifier =
    this
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
                    strokeWidth = strokeWidth,
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
    nav: (String) -> Unit,
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
    val enablePopup = remember { { moreActionsExpanded.value = true } }

    val noteEvent = note?.event

    var popupExpanded by remember { mutableStateOf(false) }

    val defaultBackgroundColor = MaterialTheme.colorScheme.background
    val backgroundColor = remember { mutableStateOf<Color>(defaultBackgroundColor) }

    if (noteEvent == null) {
        BlankNote()
    } else if (!account.isAcceptable(noteForReports) && !showHiddenNote) {
        val reports = remember { account.getRelevantReports(noteForReports).toImmutableSet() }

        HiddenNote(
            reports,
            note.author?.let { account.isHidden(it) } ?: false,
            accountViewModel,
            Modifier.fillMaxWidth(),
            nav,
            onClick = { showHiddenNote = true },
        )
    } else {
        Column(
            modifier
                .fillMaxWidth()
                .padding(top = 10.dp),
        ) {
            val editState = observeEdits(baseNote = baseNote, accountViewModel = accountViewModel)

            Row(
                modifier =
                    Modifier
                        .padding(start = 12.dp, end = 12.dp)
                        .clickable(onClick = { note.author?.let { nav("User/${it.pubkeyHex}") } }),
            ) {
                NoteAuthorPicture(
                    baseNote = baseNote,
                    nav = nav,
                    accountViewModel = accountViewModel,
                    size = 55.dp,
                )

                Column(modifier = Modifier.padding(start = 10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        NoteUsernameDisplay(baseNote, Modifier.weight(1f))

                        val isCommunityPost by
                            remember(baseNote) {
                                derivedStateOf {
                                    baseNote.event?.isTaggedAddressableKind(CommunityDefinitionEvent.KIND) == true
                                }
                            }

                        if (isCommunityPost) {
                            DisplayFollowingCommunityInPost(baseNote, accountViewModel, nav)
                        } else {
                            DisplayFollowingHashtagsInPost(baseNote, accountViewModel, nav)
                        }

                        if (editState.value is GenericLoadable.Loaded) {
                            (editState.value as? GenericLoadable.Loaded<EditState>)?.loaded?.let {
                                DisplayEditStatus(it)
                            }
                        }

                        Text(
                            timeAgo(note.createdAt(), context = context),
                            color = MaterialTheme.colorScheme.placeholderText,
                            maxLines = 1,
                        )

                        IconButton(
                            modifier = Size24Modifier,
                            onClick = enablePopup,
                        ) {
                            Icon(
                                imageVector = Icons.Default.MoreVert,
                                contentDescription = stringResource(id = R.string.more_options),
                                modifier = Size15Modifier,
                                tint = MaterialTheme.colorScheme.placeholderText,
                            )

                            NoteDropDownMenu(baseNote, moreActionsExpanded, editState, accountViewModel, nav)
                        }
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        ObserveDisplayNip05Status(
                            baseNote,
                            remember { Modifier.weight(1f) },
                            accountViewModel,
                            nav,
                        )

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

                        if (note.isDraft()) {
                            DisplayDraft()
                        }

                        DisplayOtsIfInOriginal(note, editState, accountViewModel)
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            if (noteEvent is BadgeDefinitionEvent) {
                BadgeDisplay(baseNote = note)
            } else if (noteEvent is LongTextNoteEvent) {
                RenderLongFormHeaderForThread(noteEvent)
            } else if (noteEvent is WikiNoteEvent) {
                RenderWikiHeaderForThread(noteEvent, accountViewModel, nav)
            } else if (noteEvent is ClassifiedsEvent) {
                RenderClassifiedsReaderForThread(noteEvent, note, accountViewModel, nav)
            }

            Row(
                modifier =
                    Modifier
                        .padding(horizontal = 12.dp)
                        .combinedClickable(
                            onClick = {},
                            onLongClick = { popupExpanded = true },
                        ),
            ) {
                Column {
                    val canPreview =
                        note.author == account.userProfile() ||
                            (note.author?.let { account.userProfile().isFollowingCached(it) } ?: true) ||
                            !noteForReports.hasAnyReports()

                    if (
                        (noteEvent is ChannelCreateEvent || noteEvent is ChannelMetadataEvent) &&
                        note.channelHex() != null
                    ) {
                        ChannelHeader(
                            channelHex = note.channelHex()!!,
                            showVideo = true,
                            sendToChannel = true,
                            accountViewModel = accountViewModel,
                            nav = nav,
                        )
                    } else if (noteEvent is VideoEvent) {
                        VideoDisplay(baseNote, false, true, backgroundColor, accountViewModel, nav)
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
                            quotesLeft = 3,
                            backgroundColor,
                            accountViewModel,
                            nav,
                        )
                    } else if (noteEvent is PinListEvent) {
                        RenderPinListEvent(
                            baseNote,
                            backgroundColor,
                            accountViewModel,
                            nav,
                        )
                    } else if (noteEvent is EmojiPackEvent) {
                        RenderEmojiPack(
                            baseNote,
                            true,
                            backgroundColor,
                            accountViewModel,
                        )
                    } else if (noteEvent is RelaySetEvent) {
                        DisplayRelaySet(
                            baseNote,
                            backgroundColor,
                            accountViewModel,
                            nav,
                        )
                    } else if (noteEvent is FhirResourceEvent) {
                        RenderFhirResource(baseNote, accountViewModel, nav)
                    } else if (noteEvent is GitRepositoryEvent) {
                        RenderGitRepositoryEvent(baseNote, accountViewModel, nav)
                    } else if (noteEvent is GitPatchEvent) {
                        RenderGitPatchEvent(baseNote, false, true, quotesLeft = 3, backgroundColor, accountViewModel, nav)
                    } else if (noteEvent is GitIssueEvent) {
                        RenderGitIssueEvent(baseNote, false, true, quotesLeft = 3, backgroundColor, accountViewModel, nav)
                    } else if (noteEvent is AppDefinitionEvent) {
                        RenderAppDefinition(baseNote, accountViewModel, nav)
                    } else if (noteEvent is DraftEvent) {
                        RenderDraft(baseNote, 3, backgroundColor, accountViewModel, nav)
                    } else if (noteEvent is HighlightEvent) {
                        DisplayHighlight(
                            noteEvent.quote(),
                            noteEvent.author(),
                            noteEvent.inUrl(),
                            noteEvent.inPost(),
                            false,
                            true,
                            quotesLeft = 3,
                            backgroundColor,
                            accountViewModel,
                            nav,
                        )
                    } else if (noteEvent is RepostEvent || noteEvent is GenericRepostEvent) {
                        RenderRepost(baseNote, quotesLeft = 3, backgroundColor, accountViewModel, nav)
                    } else if (noteEvent is TextNoteModificationEvent) {
                        RenderTextModificationEvent(
                            note = baseNote,
                            makeItShort = false,
                            canPreview = true,
                            quotesLeft = 3,
                            backgroundColor,
                            accountViewModel,
                            nav,
                        )
                    } else if (noteEvent is PollNoteEvent) {
                        RenderPoll(
                            baseNote,
                            false,
                            canPreview,
                            quotesLeft = 3,
                            unPackReply = false,
                            backgroundColor,
                            accountViewModel,
                            nav,
                        )
                    } else if (noteEvent is PrivateDmEvent) {
                        RenderPrivateMessage(
                            baseNote,
                            false,
                            canPreview,
                            3,
                            backgroundColor,
                            accountViewModel,
                            nav,
                        )
                    } else if (noteEvent is ChannelMessageEvent) {
                        RenderChannelMessage(
                            baseNote,
                            false,
                            canPreview,
                            3,
                            backgroundColor,
                            editState,
                            accountViewModel,
                            nav,
                        )
                    } else if (noteEvent is LiveActivitiesChatMessageEvent) {
                        RenderLiveActivityChatMessage(
                            baseNote,
                            false,
                            canPreview,
                            3,
                            backgroundColor,
                            editState,
                            accountViewModel,
                            nav,
                        )
                    } else {
                        RenderTextEvent(
                            baseNote,
                            false,
                            canPreview,
                            quotesLeft = 3,
                            unPackReply = false,
                            backgroundColor,
                            editState,
                            accountViewModel,
                            nav,
                        )
                    }
                }
            }

            val noteEvent = baseNote.event
            val zapSplits = remember(noteEvent) { noteEvent?.hasZapSplitSetup() ?: false }
            if (zapSplits && noteEvent != null) {
                Spacer(modifier = DoubleVertSpacer)
                DisplayZapSplits(noteEvent, false, accountViewModel, nav)
            }

            ReactionsRow(note, true, editState, accountViewModel, nav)
        }

        NoteQuickActionMenu(
            note = note,
            popupExpanded = popupExpanded,
            onDismiss = { popupExpanded = false },
            onWantsToEditDraft = { },
            accountViewModel = accountViewModel,
            nav = nav,
        )
    }
}

@Composable
private fun RenderClassifiedsReaderForThread(
    noteEvent: ClassifiedsEvent,
    note: Note,
    accountViewModel: AccountViewModel,
    nav: (String) -> Unit,
) {
    val images = remember(noteEvent) { noteEvent.images().toImmutableList() }
    val title = remember(noteEvent) { noteEvent.title() }
    val summary =
        remember(noteEvent) {
            val sum = noteEvent.summary()
            if (sum != noteEvent.content) {
                sum
            } else {
                null
            }
        }
    val price = remember(noteEvent) { noteEvent.price() }
    val location = remember(noteEvent) { noteEvent.location() }

    Row(modifier = Modifier.padding(start = 12.dp, end = 12.dp, bottom = 12.dp)) {
        Column {
            if (images.isNotEmpty()) {
                Row {
                    InlineCarrousel(
                        images,
                        images.first(),
                    )
                }
            } else {
                DefaultImageHeader(note, accountViewModel)
            }

            Row(
                Modifier.padding(top = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                title?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.headlineSmall,
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            price?.let {
                Row(
                    Modifier.padding(top = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val newAmount = price.amount.toBigDecimalOrNull()?.let { showAmount(it) } ?: price.amount

                    val priceTag =
                        remember(noteEvent) {
                            if (price.frequency != null && price.currency != null) {
                                "$newAmount ${price.currency}/${price.frequency}"
                            } else if (price.currency != null) {
                                "$newAmount ${price.currency}"
                            } else {
                                newAmount
                            }
                        }

                    Text(
                        text = priceTag,
                        maxLines = 1,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f),
                    )

                    location?.let {
                        Text(
                            text = it,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }

            summary?.let {
                Row(
                    Modifier.padding(top = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.weight(1f),
                        color = Color.Gray,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            Row(
                Modifier
                    .padding(start = 20.dp, end = 20.dp, bottom = 5.dp, top = 15.dp)
                    .fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_dm),
                    stringResource(R.string.send_a_direct_message),
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )

                Spacer(modifier = StdHorzSpacer)

                Text(stringResource(id = R.string.send_the_seller_a_message))
            }

            Row(
                modifier =
                    Modifier
                        .padding(start = 10.dp, end = 10.dp, bottom = 5.dp, top = 5.dp)
                        .fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val sellerName = note.author?.info?.bestName()

                val msg =
                    if (sellerName != null) {
                        stringResource(
                            id = R.string.hi_seller_is_this_still_available,
                            sellerName,
                        )
                    } else {
                        stringResource(id = R.string.hi_there_is_this_still_available)
                    }

                var message by remember { mutableStateOf(TextFieldValue(msg)) }
                val scope = rememberCoroutineScope()

                TextField(
                    value = message,
                    onValueChange = { message = it },
                    keyboardOptions =
                        KeyboardOptions.Default.copy(
                            capitalization = KeyboardCapitalization.Sentences,
                        ),
                    shape = EditFieldBorder,
                    modifier = Modifier.weight(1f, true),
                    placeholder = {
                        Text(
                            text = stringResource(R.string.reply_here),
                            color = MaterialTheme.colorScheme.placeholderText,
                        )
                    },
                    textStyle = LocalTextStyle.current.copy(textDirection = TextDirection.Content),
                    trailingIcon = {
                        ThinSendButton(
                            isActive = message.text.isNotBlank(),
                            modifier = EditFieldTrailingIconModifier,
                        ) {
                            scope.launch(Dispatchers.IO) {
                                note.author?.let {
                                    nav(routeToMessage(it, note.toNostrUri() + "\n\n" + msg, accountViewModel))
                                }
                            }
                        }
                    },
                    colors =
                        TextFieldDefaults.colors(
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                        ),
                )
            }
        }
    }
}

@Composable
private fun RenderLongFormHeaderForThread(noteEvent: LongTextNoteEvent) {
    Column(modifier = Modifier.padding(start = 12.dp, end = 12.dp, bottom = 12.dp)) {
        noteEvent.image()?.let {
            AsyncImage(
                model = it,
                contentDescription =
                    stringResource(
                        R.string.preview_card_image_for,
                        it,
                    ),
                contentScale = ContentScale.FillWidth,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        noteEvent.title()?.let {
            Spacer(modifier = DoubleVertSpacer)
            Text(
                text = it,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        noteEvent
            .summary()
            ?.ifBlank { null }
            ?.let {
                Spacer(modifier = DoubleVertSpacer)
                Text(
                    text = it,
                    modifier = Modifier.fillMaxWidth(),
                    color = Color.Gray,
                )
            }
    }
}

@Preview
@Composable
private fun RenderWikiHeaderForThreadPreview() {
    val event = Event.fromJson("{\"id\":\"277f982a4cd3f67cc47ad9282176acabee1713848f547d6021e0c155572078e1\",\"pubkey\":\"460c25e682fda7832b52d1f22d3d22b3176d972f60dcdc3212ed8c92ef85065c\",\"created_at\":1708695717,\"kind\":30818,\"tags\":[[\"d\",\"amethyst\"],[\"a\",\"30818:f03e7c5262648e0b7823dfb49f8f17309cfec9cb14711413dcabdf3d7fc6369a:amethyst\",\"wss://relay.nostr.band\",\"fork\"],[\"e\",\"ceabc60c8022c472c727aa25ae7691885964366386ce265c47e5a78be6cb00be\",\"wss://relay.nostr.band\",\"fork\"],[\"title\",\"Amethyst\"],[\"published_at\",\"1708707133\"]],\"content\":\"An Android-only app written in Kotlin with support for over 90 event kinds. \\n\\n![](https://play-lh.googleusercontent.com/lvZlAm9dBrpHeOo7sIPKCsiKOLYLhR2b0FiOT4tyiwWO2dvsR2gDS0xk9tOOr9U-6uM=w240-h480-rw)\\n\",\"sig\":\"6748126a909a20dbdb67947a09d64e41d7140a79335a4ad675c6173d7dd5dbcab9c360dec617bd67bbbc20dfad416b15056eda2e20716cd6c425a84301a125a0\"}") as WikiNoteEvent
    val accountViewModel = mockAccountViewModel()
    val nav: (String) -> Unit = {}

    val editState =
        remember {
            mutableStateOf(GenericLoadable.Empty<EditState>())
        }

    runBlocking {
        withContext(Dispatchers.IO) {
            LocalCache.justConsume(event, null)
        }
    }

    LoadNote(baseNoteHex = "277f982a4cd3f67cc47ad9282176acabee1713848f547d6021e0c155572078e1", accountViewModel = accountViewModel) { baseNote ->
        ThemeComparisonColumn {
            val bg = MaterialTheme.colorScheme.background
            val backgroundColor =
                remember {
                    mutableStateOf(bg)
                }

            Column {
                RenderWikiHeaderForThread(noteEvent = event, accountViewModel = accountViewModel, nav)
                RenderTextEvent(
                    baseNote!!,
                    false,
                    true,
                    quotesLeft = 3,
                    unPackReply = false,
                    backgroundColor,
                    editState,
                    accountViewModel,
                    nav,
                )
            }
        }
    }
}

@Composable
private fun RenderWikiHeaderForThread(
    noteEvent: WikiNoteEvent,
    accountViewModel: AccountViewModel,
    nav: (String) -> Unit,
) {
    val forkedAddress = remember(noteEvent) { noteEvent.forkFromAddress() }

    Row(modifier = Modifier.padding(start = 12.dp, end = 12.dp, bottom = 12.dp)) {
        Column {
            noteEvent.image()?.let {
                AsyncImage(
                    model = it,
                    contentDescription =
                        stringResource(
                            R.string.preview_card_image_for,
                            it,
                        ),
                    contentScale = ContentScale.FillWidth,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            noteEvent.title()?.let {
                Spacer(modifier = DoubleVertSpacer)
                Text(
                    text = it,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            forkedAddress?.let {
                LoadAddressableNote(aTag = it, accountViewModel = accountViewModel) { originalVersion ->
                    if (originalVersion != null) {
                        ForkInformationRow(originalVersion, Modifier.fillMaxWidth(), accountViewModel, nav)
                    }
                }
            }

            noteEvent
                .summary()
                ?.ifBlank { null }
                ?.let {
                    Spacer(modifier = DoubleVertSpacer)
                    Text(
                        text = it,
                        modifier = Modifier.fillMaxWidth(),
                        color = Color.Gray,
                    )
                }
        }
    }
}
