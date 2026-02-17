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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.threadview

import android.annotation.SuppressLint
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.commons.richtext.MediaUrlImage
import com.vitorpamplona.amethyst.commons.ui.feeds.FeedState
import com.vitorpamplona.amethyst.commons.ui.thread.drawReplyLevel
import com.vitorpamplona.amethyst.model.LocalCache
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.service.relayClient.reqCommand.event.observeCommunityApprovalNeedStatus
import com.vitorpamplona.amethyst.ui.components.AutoNonlazyGrid
import com.vitorpamplona.amethyst.ui.components.GenericLoadable
import com.vitorpamplona.amethyst.ui.components.LoadNote
import com.vitorpamplona.amethyst.ui.components.MyAsyncImage
import com.vitorpamplona.amethyst.ui.components.ZoomableContentView
import com.vitorpamplona.amethyst.ui.feeds.RefresheableBox
import com.vitorpamplona.amethyst.ui.navigation.navs.EmptyNav
import com.vitorpamplona.amethyst.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.ui.navigation.routes.routeFor
import com.vitorpamplona.amethyst.ui.navigation.routes.routeToMessage
import com.vitorpamplona.amethyst.ui.note.CheckAndDisplayEditStatus
import com.vitorpamplona.amethyst.ui.note.CheckHiddenFeedWatchBlockAndReport
import com.vitorpamplona.amethyst.ui.note.DisplayDraft
import com.vitorpamplona.amethyst.ui.note.DisplayOtsIfInOriginal
import com.vitorpamplona.amethyst.ui.note.LoadAddressableNote
import com.vitorpamplona.amethyst.ui.note.LongPressToQuickAction
import com.vitorpamplona.amethyst.ui.note.NoteAuthorPicture
import com.vitorpamplona.amethyst.ui.note.NoteCompose
import com.vitorpamplona.amethyst.ui.note.NoteUsernameDisplay
import com.vitorpamplona.amethyst.ui.note.ObserveDisplayNip05Status
import com.vitorpamplona.amethyst.ui.note.ObserveDraftEvent
import com.vitorpamplona.amethyst.ui.note.ReactionsRow
import com.vitorpamplona.amethyst.ui.note.RenderApproveButton
import com.vitorpamplona.amethyst.ui.note.RenderDraft
import com.vitorpamplona.amethyst.ui.note.RenderRepost
import com.vitorpamplona.amethyst.ui.note.WatchNoteEvent
import com.vitorpamplona.amethyst.ui.note.calculateBackgroundColor
import com.vitorpamplona.amethyst.ui.note.creators.zapsplits.DisplayZapSplits
import com.vitorpamplona.amethyst.ui.note.elements.DefaultImageHeader
import com.vitorpamplona.amethyst.ui.note.elements.DefaultImageHeaderBackground
import com.vitorpamplona.amethyst.ui.note.elements.DisplayFollowingCommunityInPost
import com.vitorpamplona.amethyst.ui.note.elements.DisplayFollowingHashtagsInPost
import com.vitorpamplona.amethyst.ui.note.elements.DisplayLocation
import com.vitorpamplona.amethyst.ui.note.elements.DisplayPoW
import com.vitorpamplona.amethyst.ui.note.elements.DisplayReward
import com.vitorpamplona.amethyst.ui.note.elements.MoreOptionsButton
import com.vitorpamplona.amethyst.ui.note.elements.Reward
import com.vitorpamplona.amethyst.ui.note.elements.ShowForkInformation
import com.vitorpamplona.amethyst.ui.note.elements.TimeAgo
import com.vitorpamplona.amethyst.ui.note.observeEdits
import com.vitorpamplona.amethyst.ui.note.showAmount
import com.vitorpamplona.amethyst.ui.note.types.AudioHeader
import com.vitorpamplona.amethyst.ui.note.types.AudioTrackHeader
import com.vitorpamplona.amethyst.ui.note.types.BadgeDisplay
import com.vitorpamplona.amethyst.ui.note.types.DisplayBlockedRelayList
import com.vitorpamplona.amethyst.ui.note.types.DisplayBroadcastRelayList
import com.vitorpamplona.amethyst.ui.note.types.DisplayDMRelayList
import com.vitorpamplona.amethyst.ui.note.types.DisplayFollowList
import com.vitorpamplona.amethyst.ui.note.types.DisplayIndexerRelayList
import com.vitorpamplona.amethyst.ui.note.types.DisplayNIP65RelayList
import com.vitorpamplona.amethyst.ui.note.types.DisplayPeopleList
import com.vitorpamplona.amethyst.ui.note.types.DisplayProxyRelayList
import com.vitorpamplona.amethyst.ui.note.types.DisplayRelaySet
import com.vitorpamplona.amethyst.ui.note.types.DisplaySearchRelayList
import com.vitorpamplona.amethyst.ui.note.types.DisplayTrustedRelayList
import com.vitorpamplona.amethyst.ui.note.types.EditState
import com.vitorpamplona.amethyst.ui.note.types.FileHeaderDisplay
import com.vitorpamplona.amethyst.ui.note.types.FileStorageHeaderDisplay
import com.vitorpamplona.amethyst.ui.note.types.PictureDisplay
import com.vitorpamplona.amethyst.ui.note.types.RenderAppDefinition
import com.vitorpamplona.amethyst.ui.note.types.RenderChannelMessage
import com.vitorpamplona.amethyst.ui.note.types.RenderChatMessageEncryptedFile
import com.vitorpamplona.amethyst.ui.note.types.RenderEmojiPack
import com.vitorpamplona.amethyst.ui.note.types.RenderFhirResource
import com.vitorpamplona.amethyst.ui.note.types.RenderGitIssueEvent
import com.vitorpamplona.amethyst.ui.note.types.RenderGitPatchEvent
import com.vitorpamplona.amethyst.ui.note.types.RenderGitRepositoryEvent
import com.vitorpamplona.amethyst.ui.note.types.RenderHighlight
import com.vitorpamplona.amethyst.ui.note.types.RenderInteractiveStory
import com.vitorpamplona.amethyst.ui.note.types.RenderLiveActivityChatMessage
import com.vitorpamplona.amethyst.ui.note.types.RenderPinListEvent
import com.vitorpamplona.amethyst.ui.note.types.RenderPoll
import com.vitorpamplona.amethyst.ui.note.types.RenderPostApproval
import com.vitorpamplona.amethyst.ui.note.types.RenderPrivateMessage
import com.vitorpamplona.amethyst.ui.note.types.RenderPublicMessage
import com.vitorpamplona.amethyst.ui.note.types.RenderTextEvent
import com.vitorpamplona.amethyst.ui.note.types.RenderTextModificationEvent
import com.vitorpamplona.amethyst.ui.note.types.RenderTorrent
import com.vitorpamplona.amethyst.ui.note.types.RenderTorrentComment
import com.vitorpamplona.amethyst.ui.note.types.RenderZapPoll
import com.vitorpamplona.amethyst.ui.note.types.VideoDisplay
import com.vitorpamplona.amethyst.ui.note.types.VoiceHeader
import com.vitorpamplona.amethyst.ui.painterRes
import com.vitorpamplona.amethyst.ui.screen.RenderFeedState
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.publicChannels.nip28PublicChat.PublicChatChannelHeader
import com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.utils.ThinSendButton
import com.vitorpamplona.amethyst.ui.screen.loggedIn.mockAccountViewModel
import com.vitorpamplona.amethyst.ui.screen.loggedIn.threadview.dal.LevelFeedViewModel
import com.vitorpamplona.amethyst.ui.stringRes
import com.vitorpamplona.amethyst.ui.theme.DividerThickness
import com.vitorpamplona.amethyst.ui.theme.DoubleVertSpacer
import com.vitorpamplona.amethyst.ui.theme.EditFieldBorder
import com.vitorpamplona.amethyst.ui.theme.EditFieldTrailingIconModifier
import com.vitorpamplona.amethyst.ui.theme.FeedPadding
import com.vitorpamplona.amethyst.ui.theme.PaddingHorizontal12Modifier
import com.vitorpamplona.amethyst.ui.theme.Size55dp
import com.vitorpamplona.amethyst.ui.theme.Size5dp
import com.vitorpamplona.amethyst.ui.theme.StdHorzSpacer
import com.vitorpamplona.amethyst.ui.theme.StdVertSpacer
import com.vitorpamplona.amethyst.ui.theme.ThemeComparisonColumn
import com.vitorpamplona.amethyst.ui.theme.grayText
import com.vitorpamplona.amethyst.ui.theme.imageModifier
import com.vitorpamplona.amethyst.ui.theme.lessImportantLink
import com.vitorpamplona.amethyst.ui.theme.placeholderText
import com.vitorpamplona.amethyst.ui.theme.selectedNote
import com.vitorpamplona.quartz.experimental.audio.header.AudioHeaderEvent
import com.vitorpamplona.quartz.experimental.audio.track.AudioTrackEvent
import com.vitorpamplona.quartz.experimental.bounties.bountyBaseReward
import com.vitorpamplona.quartz.experimental.edits.TextNoteModificationEvent
import com.vitorpamplona.quartz.experimental.forks.IForkableEvent
import com.vitorpamplona.quartz.experimental.interactiveStories.InteractiveStoryBaseEvent
import com.vitorpamplona.quartz.experimental.medical.FhirResourceEvent
import com.vitorpamplona.quartz.experimental.nip95.header.FileStorageHeaderEvent
import com.vitorpamplona.quartz.experimental.publicMessages.PublicMessageEvent
import com.vitorpamplona.quartz.experimental.zapPolls.PollNoteEvent
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.tags.geohash.geoHashOrScope
import com.vitorpamplona.quartz.nip04Dm.messages.PrivateDmEvent
import com.vitorpamplona.quartz.nip13Pow.strongPoWOrNull
import com.vitorpamplona.quartz.nip17Dm.files.ChatMessageEncryptedFileHeaderEvent
import com.vitorpamplona.quartz.nip17Dm.settings.ChatMessageRelayListEvent
import com.vitorpamplona.quartz.nip18Reposts.GenericRepostEvent
import com.vitorpamplona.quartz.nip18Reposts.RepostEvent
import com.vitorpamplona.quartz.nip22Comments.CommentEvent
import com.vitorpamplona.quartz.nip23LongContent.LongTextNoteEvent
import com.vitorpamplona.quartz.nip28PublicChat.admin.ChannelCreateEvent
import com.vitorpamplona.quartz.nip28PublicChat.admin.ChannelMetadataEvent
import com.vitorpamplona.quartz.nip28PublicChat.message.ChannelMessageEvent
import com.vitorpamplona.quartz.nip30CustomEmoji.pack.EmojiPackEvent
import com.vitorpamplona.quartz.nip34Git.issue.GitIssueEvent
import com.vitorpamplona.quartz.nip34Git.patch.GitPatchEvent
import com.vitorpamplona.quartz.nip34Git.repository.GitRepositoryEvent
import com.vitorpamplona.quartz.nip35Torrents.TorrentCommentEvent
import com.vitorpamplona.quartz.nip35Torrents.TorrentEvent
import com.vitorpamplona.quartz.nip37Drafts.DraftWrapEvent
import com.vitorpamplona.quartz.nip50Search.SearchRelayListEvent
import com.vitorpamplona.quartz.nip51Lists.PinListEvent
import com.vitorpamplona.quartz.nip51Lists.followList.FollowListEvent
import com.vitorpamplona.quartz.nip51Lists.peopleList.PeopleListEvent
import com.vitorpamplona.quartz.nip51Lists.relayLists.BlockedRelayListEvent
import com.vitorpamplona.quartz.nip51Lists.relayLists.BroadcastRelayListEvent
import com.vitorpamplona.quartz.nip51Lists.relayLists.IndexerRelayListEvent
import com.vitorpamplona.quartz.nip51Lists.relayLists.ProxyRelayListEvent
import com.vitorpamplona.quartz.nip51Lists.relayLists.TrustedRelayListEvent
import com.vitorpamplona.quartz.nip51Lists.relaySets.RelaySetEvent
import com.vitorpamplona.quartz.nip53LiveActivities.chat.LiveActivitiesChatMessageEvent
import com.vitorpamplona.quartz.nip54Wiki.WikiNoteEvent
import com.vitorpamplona.quartz.nip57Zaps.splits.hasZapSplitSetup
import com.vitorpamplona.quartz.nip58Badges.BadgeDefinitionEvent
import com.vitorpamplona.quartz.nip65RelayList.AdvertisedRelayListEvent
import com.vitorpamplona.quartz.nip68Picture.PictureEvent
import com.vitorpamplona.quartz.nip71Video.VideoEvent
import com.vitorpamplona.quartz.nip72ModCommunities.approval.CommunityPostApprovalEvent
import com.vitorpamplona.quartz.nip72ModCommunities.communityAddress
import com.vitorpamplona.quartz.nip72ModCommunities.isACommunityPost
import com.vitorpamplona.quartz.nip84Highlights.HighlightEvent
import com.vitorpamplona.quartz.nip88Polls.poll.PollEvent
import com.vitorpamplona.quartz.nip89AppHandlers.definition.AppDefinitionEvent
import com.vitorpamplona.quartz.nip94FileMetadata.FileHeaderEvent
import com.vitorpamplona.quartz.nip99Classifieds.ClassifiedsEvent
import com.vitorpamplona.quartz.nipA0VoiceMessages.BaseVoiceEvent
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext

@Composable
fun ThreadFeedView(
    noteId: String,
    viewModel: LevelFeedViewModel,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    RefresheableBox(viewModel) {
        RenderFeedState(
            viewModel = viewModel,
            accountViewModel = accountViewModel,
            listState = viewModel.llState,
            nav = nav,
            routeForLastRead = null,
            onLoaded = {
                RenderThreadFeed(noteId, it, viewModel.llState, viewModel, accountViewModel, nav)
            },
        )
    }
}

@Composable
fun RenderThreadFeed(
    noteId: String,
    loaded: FeedState.Loaded,
    listState: LazyListState,
    viewModel: LevelFeedViewModel,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val items by loaded.feed.collectAsStateWithLifecycle()

    val position = items.list.indexOfFirst { it.idHex == noteId }

    LaunchedEffect(noteId, position) {
        // hack to allow multiple scrolls to Item while posts on the screen load.
        // This is important when clicking on a reply of an older thread in Notifications
        // In that case, this screen will open with 0-1 items, and the scrollToItem below
        // will not change the state of the screen (too few items, scroll is not available)
        // as the app loads the reaming of the thread the position of the reply changes
        // and because there wasn't a possibility to scroll before and now there is one,
        // the screen stays at the top. Once the thread has enough replies, the lazy column
        // updates with new items correctly. It just needs a few items to start the scroll.
        //
        // This hack allows the list 1 second to fill up with more
        // records before setting up the position on the feed.
        //
        // It jumps around, but it is the best we can do.

        if (position >= 0 && !viewModel.hasDragged.value) {
            val offset =
                if (position > items.list.size - 3) {
                    0
                } else {
                    -200
                }

            listState.scrollToItem(position, offset)
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = FeedPadding,
        state = listState,
    ) {
        itemsIndexed(items.list, key = { _, item -> item.idHex }) { index, item ->
            val level = viewModel.levelFlowForItem(item).collectAsStateWithLifecycle(0)

            val modifier =
                Modifier
                    .drawReplyLevel(
                        level = level,
                        color = MaterialTheme.colorScheme.placeholderText,
                        selected =
                            if (item.idHex == noteId) {
                                MaterialTheme.colorScheme.lessImportantLink
                            } else {
                                MaterialTheme.colorScheme.placeholderText
                            },
                    )

            if (index == 0) {
                ProvideTextStyle(TextStyle(fontSize = 18.sp, lineHeight = 1.20.em)) {
                    NoteMaster(
                        baseNote = item,
                        modifier = modifier,
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
                    baseNote = item,
                    modifier = modifier,
                    isBoostedNote = false,
                    unPackReply = false,
                    quotesLeft = 3,
                    parentBackgroundColor = background,
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun NoteMaster(
    baseNote: Note,
    modifier: Modifier = Modifier,
    parentBackgroundColor: MutableState<Color>? = null,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    WatchNoteEvent(
        baseNote = baseNote,
        accountViewModel = accountViewModel,
        nav,
        modifier,
    ) {
        CheckHiddenFeedWatchBlockAndReport(
            note = baseNote,
            modifier = modifier,
            ignoreAllBlocksAndReports = false,
            showHiddenWarning = true,
            accountViewModel = accountViewModel,
            nav = nav,
        ) { canPreview ->
            LongPressToQuickAction(baseNote, accountViewModel, nav) { showPopup ->
                FullBleedNoteCompose(
                    baseNote,
                    modifier
                        .combinedClickable(
                            onClick = {},
                            onLongClick = showPopup,
                        ),
                    canPreview,
                    parentBackgroundColor = parentBackgroundColor,
                    accountViewModel,
                    nav,
                )
            }
        }
    }
}

@Composable
private fun FullBleedNoteCompose(
    baseNote: Note,
    modifier: Modifier,
    canPreview: Boolean,
    parentBackgroundColor: MutableState<Color>?,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val noteEvent = baseNote.event ?: return

    val isDraft = baseNote.isDraft()
    val backgroundColor =
        calculateBackgroundColor(
            baseNote.createdAt(),
            null,
            parentBackgroundColor,
            accountViewModel,
        )

    Column(
        Modifier
            .fillMaxWidth()
            .padding(top = 10.dp),
    ) {
        val editState = observeEdits(baseNote = baseNote, accountViewModel = accountViewModel)

        Row(
            modifier =
                Modifier
                    .padding(start = 12.dp, end = 12.dp)
                    .clickable(onClick = { baseNote.author?.let { nav.nav(routeFor(it)) } }),
        ) {
            NoteAuthorPicture(
                baseNote = baseNote,
                size = Size55dp,
                accountViewModel = accountViewModel,
                nav = nav,
            )

            Column(modifier = Modifier.padding(start = 10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    NoteUsernameDisplay(baseNote, Modifier.weight(1f), accountViewModel = accountViewModel)

                    if (isDraft) {
                        ObserveDraftEvent(baseNote, accountViewModel) { draftNote ->
                            val isCommunityPost by remember(draftNote) {
                                derivedStateOf { draftNote.event?.isACommunityPost() == true }
                            }

                            if (isCommunityPost) {
                                DisplayFollowingCommunityInPost(draftNote, accountViewModel, nav)
                            } else {
                                DisplayFollowingHashtagsInPost(draftNote, accountViewModel, nav)
                            }
                        }
                    } else {
                        val isCommunityPost by remember(baseNote) {
                            derivedStateOf { baseNote.event?.isACommunityPost() == true }
                        }

                        if (isCommunityPost) {
                            DisplayFollowingCommunityInPost(baseNote, accountViewModel, nav)
                        } else {
                            DisplayFollowingHashtagsInPost(baseNote, accountViewModel, nav)
                        }
                    }

                    CheckAndDisplayEditStatus(editState)

                    TimeAgo(note = baseNote)

                    MoreOptionsButton(baseNote, editState, accountViewModel, nav)
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(
                        remember { Modifier.weight(1f) },
                    ) {
                        if (noteEvent is IForkableEvent && noteEvent.isAFork()) {
                            ShowForkInformation(noteEvent, Modifier, accountViewModel, nav)
                        } else {
                            ObserveDisplayNip05Status(
                                baseNote,
                                accountViewModel,
                                nav,
                            )
                        }
                    }

                    val geo = remember { noteEvent.geoHashOrScope() }
                    if (geo != null) {
                        DisplayLocation(geo, accountViewModel, nav)
                    }

                    val baseReward = remember { noteEvent.bountyBaseReward()?.let { Reward(it) } }
                    if (baseReward != null) {
                        DisplayReward(baseReward, baseNote, accountViewModel, nav)
                    }

                    val pow = remember(noteEvent) { noteEvent.strongPoWOrNull() }
                    if (pow != null) {
                        DisplayPoW(pow)
                    }

                    if (isDraft) {
                        DisplayDraft()
                    }

                    DisplayOtsIfInOriginal(baseNote, editState, accountViewModel)
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        when (noteEvent) {
            is BadgeDefinitionEvent -> BadgeDisplay(baseNote = baseNote, accountViewModel)
            is LongTextNoteEvent -> RenderLongFormHeaderForThread(noteEvent, baseNote, accountViewModel)
            is WikiNoteEvent -> RenderWikiHeaderForThread(noteEvent, accountViewModel, nav)
            is ClassifiedsEvent -> RenderClassifiedsReaderForThread(noteEvent, baseNote, accountViewModel, nav)
        }

        Row(
            modifier = PaddingHorizontal12Modifier,
        ) {
            Column {
                if (noteEvent is ChannelCreateEvent) {
                    PublicChatChannelHeader(
                        channelHex = noteEvent.id,
                        sendToChannel = true,
                        accountViewModel = accountViewModel,
                        nav = nav,
                    )
                } else if (noteEvent is ChannelMetadataEvent) {
                    noteEvent.channelId()?.let {
                        PublicChatChannelHeader(
                            channelHex = it,
                            sendToChannel = true,
                            accountViewModel = accountViewModel,
                            nav = nav,
                        )
                    }
                } else if (noteEvent is VideoEvent) {
                    VideoDisplay(baseNote, makeItShort = false, canPreview = true, backgroundColor = backgroundColor, ContentScale.FillWidth, accountViewModel = accountViewModel, nav = nav)
                } else if (noteEvent is PictureEvent) {
                    PictureDisplay(baseNote, roundedCorner = true, ContentScale.FillWidth, PaddingValues(vertical = Size5dp), backgroundColor, accountViewModel = accountViewModel, nav)
                } else if (noteEvent is BaseVoiceEvent) {
                    VoiceHeader(noteEvent, baseNote, accountViewModel, nav)
                } else if (noteEvent is FileHeaderEvent) {
                    FileHeaderDisplay(baseNote, roundedCorner = true, ContentScale.FillWidth, accountViewModel = accountViewModel)
                } else if (noteEvent is FileStorageHeaderEvent) {
                    FileStorageHeaderDisplay(baseNote, roundedCorner = true, ContentScale.FillWidth, accountViewModel = accountViewModel)
                } else if (noteEvent is PeopleListEvent) {
                    DisplayPeopleList(baseNote, backgroundColor, accountViewModel, nav)
                } else if (noteEvent is FollowListEvent) {
                    DisplayFollowList(baseNote, false, accountViewModel, nav)
                } else if (noteEvent is AudioTrackEvent) {
                    AudioTrackHeader(noteEvent, baseNote, ContentScale.FillWidth, accountViewModel, nav)
                } else if (noteEvent is AudioHeaderEvent) {
                    AudioHeader(noteEvent, baseNote, ContentScale.FillWidth, accountViewModel, nav)
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
                } else if (noteEvent is ChatMessageRelayListEvent) {
                    DisplayDMRelayList(baseNote, backgroundColor, accountViewModel, nav)
                } else if (noteEvent is ChatMessageEncryptedFileHeaderEvent) {
                    RenderChatMessageEncryptedFile(
                        baseNote,
                        false,
                        canPreview,
                        3,
                        backgroundColor,
                        editState,
                        accountViewModel,
                        nav,
                    )
                } else if (noteEvent is AdvertisedRelayListEvent) {
                    DisplayNIP65RelayList(baseNote, backgroundColor, accountViewModel, nav)
                } else if (noteEvent is SearchRelayListEvent) {
                    DisplaySearchRelayList(baseNote, backgroundColor, accountViewModel, nav)
                } else if (noteEvent is BlockedRelayListEvent) {
                    DisplayBlockedRelayList(baseNote, backgroundColor, accountViewModel, nav)
                } else if (noteEvent is ProxyRelayListEvent) {
                    DisplayProxyRelayList(baseNote, backgroundColor, accountViewModel, nav)
                } else if (noteEvent is TrustedRelayListEvent) {
                    DisplayTrustedRelayList(baseNote, backgroundColor, accountViewModel, nav)
                } else if (noteEvent is IndexerRelayListEvent) {
                    DisplayIndexerRelayList(baseNote, backgroundColor, accountViewModel, nav)
                } else if (noteEvent is BroadcastRelayListEvent) {
                    DisplayBroadcastRelayList(baseNote, backgroundColor, accountViewModel, nav)
                } else if (noteEvent is FhirResourceEvent) {
                    RenderFhirResource(baseNote, accountViewModel, nav)
                } else if (noteEvent is GitRepositoryEvent) {
                    RenderGitRepositoryEvent(baseNote, accountViewModel, nav)
                } else if (noteEvent is InteractiveStoryBaseEvent) {
                    RenderInteractiveStory(
                        baseNote,
                        false,
                        true,
                        3,
                        backgroundColor,
                        accountViewModel,
                        nav,
                    )
                } else if (noteEvent is GitPatchEvent) {
                    RenderGitPatchEvent(baseNote, makeItShort = false, canPreview = true, quotesLeft = 3, backgroundColor = backgroundColor, accountViewModel = accountViewModel, nav = nav)
                } else if (noteEvent is GitIssueEvent) {
                    RenderGitIssueEvent(baseNote, makeItShort = false, canPreview = true, quotesLeft = 3, backgroundColor = backgroundColor, accountViewModel = accountViewModel, nav = nav)
                } else if (noteEvent is AppDefinitionEvent) {
                    RenderAppDefinition(baseNote, accountViewModel, nav)
                } else if (noteEvent is DraftWrapEvent) {
                    RenderDraft(baseNote, 3, true, backgroundColor, accountViewModel, nav)
                } else if (noteEvent is HighlightEvent) {
                    RenderHighlight(baseNote, false, canPreview, quotesLeft = 3, backgroundColor, accountViewModel, nav)
                } else if (noteEvent is PublicMessageEvent) {
                    RenderPublicMessage(baseNote, false, canPreview, quotesLeft = 3, backgroundColor, accountViewModel, nav)
                } else if (noteEvent is CommentEvent) {
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
                    RenderZapPoll(
                        baseNote,
                        false,
                        canPreview,
                        quotesLeft = 3,
                        unPackReply = false,
                        backgroundColor,
                        accountViewModel,
                        nav,
                    )
                } else if (noteEvent is PollEvent) {
                    RenderPoll(
                        note = baseNote,
                        makeItShort = false,
                        canPreview = canPreview,
                        quotesLeft = 3,
                        backgroundColor = backgroundColor,
                        accountViewModel = accountViewModel,
                        nav = nav,
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
                } else if (noteEvent is TorrentEvent) {
                    RenderTorrent(
                        baseNote,
                        backgroundColor,
                        accountViewModel,
                        nav,
                    )
                } else if (noteEvent is TorrentCommentEvent) {
                    RenderTorrentComment(
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

        val zapSplits = remember(noteEvent) { noteEvent.hasZapSplitSetup() }
        if (zapSplits) {
            Spacer(modifier = DoubleVertSpacer)
            Row(PaddingHorizontal12Modifier) {
                DisplayZapSplits(noteEvent, false, accountViewModel, nav)
            }
        }

        RenderApprovalIfNeeded(baseNote, accountViewModel, nav)

        ReactionsRow(baseNote, showReactionDetail = true, addPadding = true, editState = editState, accountViewModel = accountViewModel, nav = nav)
    }
}

@Composable
private fun RenderApprovalIfNeeded(
    baseNote: Note,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    if (baseNote.isNewThread()) {
        val communityAddress =
            remember(baseNote) {
                baseNote.event?.communityAddress()
            }
        communityAddress?.let {
            LoadAddressableNote(it, accountViewModel) { community ->
                if (community != null) {
                    val showApproveButton by observeCommunityApprovalNeedStatus(baseNote, community, accountViewModel)
                    if (showApproveButton == true) {
                        Row(PaddingHorizontal12Modifier) {
                            RenderApproveButton(baseNote, community, accountViewModel)
                        }
                    }
                }
            }
        }
    }
}

@SuppressLint("StateFlowValueCalledInComposition")
@Composable
private fun RenderClassifiedsReaderForThread(
    noteEvent: ClassifiedsEvent,
    note: Note,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val imageSet =
        noteEvent.imageMetas().ifEmpty { null }?.map {
            MediaUrlImage(
                url = it.url,
                description = it.alt,
                hash = it.hash,
                blurhash = it.blurhash,
                dim = it.dimension,
                uri = note.toNostrUri(),
                mimeType = it.mimeType,
            )
        }

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
            if (imageSet != null && imageSet.isNotEmpty()) {
                AutoNonlazyGrid(imageSet.size) {
                    ZoomableContentView(
                        content = imageSet[it],
                        images = imageSet.toImmutableList(),
                        roundedCorner = false,
                        contentScale = ContentScale.Crop,
                        accountViewModel = accountViewModel,
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
                    painter = painterRes(R.drawable.ic_dm, 5),
                    stringRes(R.string.send_a_direct_message),
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )

                Spacer(modifier = StdHorzSpacer)

                Text(stringRes(id = R.string.send_the_seller_a_message))
            }

            Row(
                modifier =
                    Modifier
                        .padding(start = 10.dp, end = 10.dp, bottom = 5.dp, top = 5.dp)
                        .fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val sellerName =
                    note.author
                        ?.metadataOrNull()
                        ?.flow
                        ?.value
                        ?.info
                        ?.bestName()

                val msg =
                    if (sellerName != null) {
                        stringRes(
                            id = R.string.hi_seller_is_this_still_available,
                            sellerName,
                        )
                    } else {
                        stringRes(id = R.string.hi_there_is_this_still_available)
                    }

                var message by remember { mutableStateOf(TextFieldValue(msg)) }

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
                            text = stringRes(R.string.reply_here),
                            color = MaterialTheme.colorScheme.placeholderText,
                        )
                    },
                    textStyle = LocalTextStyle.current.copy(textDirection = TextDirection.Content),
                    trailingIcon = {
                        ThinSendButton(
                            isActive = message.text.isNotBlank(),
                            modifier = EditFieldTrailingIconModifier,
                        ) {
                            note.author?.let {
                                nav.nav {
                                    routeToMessage(
                                        it,
                                        draftMessage = note.toNostrUri() + "\n\n" + msg,
                                        accountViewModel = accountViewModel,
                                    )
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
private fun RenderLongFormHeaderForThread(
    noteEvent: LongTextNoteEvent,
    note: Note,
    accountViewModel: AccountViewModel,
) {
    Column(modifier = Modifier.padding(start = 12.dp, end = 12.dp, bottom = 12.dp)) {
        noteEvent.image()?.let {
            MyAsyncImage(
                imageUrl = it,
                contentDescription =
                    stringRes(
                        R.string.preview_card_image_for,
                        it,
                    ),
                contentScale = ContentScale.FillWidth,
                mainImageModifier = Modifier,
                loadedImageModifier = MaterialTheme.colorScheme.imageModifier,
                accountViewModel = accountViewModel,
                onLoadingBackground = { DefaultImageHeaderBackground(note, accountViewModel) },
                onError = { DefaultImageHeader(note, accountViewModel) },
            )
        } ?: run {
            DefaultImageHeader(note, accountViewModel)
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
                Spacer(modifier = StdVertSpacer)
                Text(
                    text = it,
                    modifier = Modifier.fillMaxWidth(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.grayText,
                )
            }
    }
}

@Preview
@Composable
private fun RenderWikiHeaderForThreadPreview() {
    val event = Event.fromJson("{\"id\":\"277f982a4cd3f67cc47ad9282176acabee1713848f547d6021e0c155572078e1\",\"pubkey\":\"460c25e682fda7832b52d1f22d3d22b3176d972f60dcdc3212ed8c92ef85065c\",\"created_at\":1708695717,\"kind\":30818,\"tags\":[[\"d\",\"amethyst\"],[\"a\",\"30818:f03e7c5262648e0b7823dfb49f8f17309cfec9cb14711413dcabdf3d7fc6369a:amethyst\",\"wss://relay.nostr.band\",\"fork\"],[\"e\",\"ceabc60c8022c472c727aa25ae7691885964366386ce265c47e5a78be6cb00be\",\"wss://relay.nostr.band\",\"fork\"],[\"title\",\"Amethyst\"],[\"published_at\",\"1708707133\"]],\"content\":\"An Android-only app written in Kotlin with support for over 90 event kinds. \\n\\n![](https://play-lh.googleusercontent.com/lvZlAm9dBrpHeOo7sIPKCsiKOLYLhR2b0FiOT4tyiwWO2dvsR2gDS0xk9tOOr9U-6uM=w240-h480-rw)\\n\",\"sig\":\"6748126a909a20dbdb67947a09d64e41d7140a79335a4ad675c6173d7dd5dbcab9c360dec617bd67bbbc20dfad416b15056eda2e20716cd6c425a84301a125a0\"}") as WikiNoteEvent
    val accountViewModel = mockAccountViewModel()

    val editState =
        remember {
            mutableStateOf(GenericLoadable.Empty<EditState>())
        }

    runBlocking {
        withContext(Dispatchers.IO) {
            LocalCache.justConsume(event, null, false)
        }
    }

    val nav = EmptyNav()

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
                    makeItShort = false,
                    canPreview = true,
                    quotesLeft = 3,
                    unPackReply = false,
                    backgroundColor = backgroundColor,
                    editState = editState,
                    accountViewModel = accountViewModel,
                    nav = nav,
                )
            }
        }
    }
}

@Composable
private fun RenderWikiHeaderForThread(
    noteEvent: WikiNoteEvent,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    Row(modifier = Modifier.padding(start = 12.dp, end = 12.dp, bottom = 12.dp)) {
        Column {
            noteEvent.image()?.let {
                AsyncImage(
                    model = it,
                    contentDescription =
                        stringRes(
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
}
