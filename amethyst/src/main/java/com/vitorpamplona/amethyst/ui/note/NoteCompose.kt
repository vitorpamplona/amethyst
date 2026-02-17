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
package com.vitorpamplona.amethyst.ui.note

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Alignment.Companion.CenterVertically
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.commons.compose.produceCachedStateAsync
import com.vitorpamplona.amethyst.commons.model.nip28PublicChats.PublicChatChannel
import com.vitorpamplona.amethyst.model.AddressableNote
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.service.relayClient.reqCommand.channel.observeChannelPicture
import com.vitorpamplona.amethyst.service.relayClient.reqCommand.event.observeCommunityApprovalNeedStatus
import com.vitorpamplona.amethyst.service.relayClient.reqCommand.event.observeNoteEdits
import com.vitorpamplona.amethyst.service.relayClient.reqCommand.event.observeNoteEvent
import com.vitorpamplona.amethyst.ui.components.GenericLoadable
import com.vitorpamplona.amethyst.ui.components.RobohashFallbackAsyncImage
import com.vitorpamplona.amethyst.ui.layouts.GenericRepostLayout
import com.vitorpamplona.amethyst.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.ui.navigation.routes.routeEditDraftTo
import com.vitorpamplona.amethyst.ui.navigation.routes.routeFor
import com.vitorpamplona.amethyst.ui.note.creators.zapsplits.DisplayZapSplits
import com.vitorpamplona.amethyst.ui.note.elements.BoostedMark
import com.vitorpamplona.amethyst.ui.note.elements.DisplayEditStatus
import com.vitorpamplona.amethyst.ui.note.elements.DisplayFollowingCommunityInPost
import com.vitorpamplona.amethyst.ui.note.elements.DisplayFollowingHashtagsInPost
import com.vitorpamplona.amethyst.ui.note.elements.DisplayLocation
import com.vitorpamplona.amethyst.ui.note.elements.DisplayOts
import com.vitorpamplona.amethyst.ui.note.elements.DisplayPoW
import com.vitorpamplona.amethyst.ui.note.elements.DisplayReward
import com.vitorpamplona.amethyst.ui.note.elements.MoreOptionsButton
import com.vitorpamplona.amethyst.ui.note.elements.Reward
import com.vitorpamplona.amethyst.ui.note.elements.ShowForkInformation
import com.vitorpamplona.amethyst.ui.note.elements.TimeAgo
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
import com.vitorpamplona.amethyst.ui.note.types.EmptyState
import com.vitorpamplona.amethyst.ui.note.types.FileHeaderDisplay
import com.vitorpamplona.amethyst.ui.note.types.FileStorageHeaderDisplay
import com.vitorpamplona.amethyst.ui.note.types.PictureDisplay
import com.vitorpamplona.amethyst.ui.note.types.RenderAppDefinition
import com.vitorpamplona.amethyst.ui.note.types.RenderAudioHeader
import com.vitorpamplona.amethyst.ui.note.types.RenderAudioTrack
import com.vitorpamplona.amethyst.ui.note.types.RenderBadgeAward
import com.vitorpamplona.amethyst.ui.note.types.RenderChannelMessage
import com.vitorpamplona.amethyst.ui.note.types.RenderChatMessage
import com.vitorpamplona.amethyst.ui.note.types.RenderChatMessageEncryptedFile
import com.vitorpamplona.amethyst.ui.note.types.RenderChessGame
import com.vitorpamplona.amethyst.ui.note.types.RenderClassifieds
import com.vitorpamplona.amethyst.ui.note.types.RenderCommunity
import com.vitorpamplona.amethyst.ui.note.types.RenderEmojiPack
import com.vitorpamplona.amethyst.ui.note.types.RenderFhirResource
import com.vitorpamplona.amethyst.ui.note.types.RenderGitIssueEvent
import com.vitorpamplona.amethyst.ui.note.types.RenderGitPatchEvent
import com.vitorpamplona.amethyst.ui.note.types.RenderGitRepositoryEvent
import com.vitorpamplona.amethyst.ui.note.types.RenderHighlight
import com.vitorpamplona.amethyst.ui.note.types.RenderInteractiveStory
import com.vitorpamplona.amethyst.ui.note.types.RenderLiveActivityChatMessage
import com.vitorpamplona.amethyst.ui.note.types.RenderLiveActivityEvent
import com.vitorpamplona.amethyst.ui.note.types.RenderLiveChessChallenge
import com.vitorpamplona.amethyst.ui.note.types.RenderLiveChessGameEnd
import com.vitorpamplona.amethyst.ui.note.types.RenderLongFormContent
import com.vitorpamplona.amethyst.ui.note.types.RenderNIP90ContentDiscoveryResponse
import com.vitorpamplona.amethyst.ui.note.types.RenderNIP90Status
import com.vitorpamplona.amethyst.ui.note.types.RenderNipContent
import com.vitorpamplona.amethyst.ui.note.types.RenderPinListEvent
import com.vitorpamplona.amethyst.ui.note.types.RenderPoll
import com.vitorpamplona.amethyst.ui.note.types.RenderPostApproval
import com.vitorpamplona.amethyst.ui.note.types.RenderPrivateMessage
import com.vitorpamplona.amethyst.ui.note.types.RenderPublicMessage
import com.vitorpamplona.amethyst.ui.note.types.RenderReaction
import com.vitorpamplona.amethyst.ui.note.types.RenderReport
import com.vitorpamplona.amethyst.ui.note.types.RenderTextEvent
import com.vitorpamplona.amethyst.ui.note.types.RenderTextModificationEvent
import com.vitorpamplona.amethyst.ui.note.types.RenderTorrent
import com.vitorpamplona.amethyst.ui.note.types.RenderTorrentComment
import com.vitorpamplona.amethyst.ui.note.types.RenderVoiceTrack
import com.vitorpamplona.amethyst.ui.note.types.RenderWikiContent
import com.vitorpamplona.amethyst.ui.note.types.RenderZapPoll
import com.vitorpamplona.amethyst.ui.note.types.VideoDisplay
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.publicChannels.nip28PublicChat.RenderPublicChatChannelHeader
import com.vitorpamplona.amethyst.ui.stringRes
import com.vitorpamplona.amethyst.ui.theme.DoubleVertSpacer
import com.vitorpamplona.amethyst.ui.theme.Font12SP
import com.vitorpamplona.amethyst.ui.theme.HalfDoubleVertSpacer
import com.vitorpamplona.amethyst.ui.theme.HalfPadding
import com.vitorpamplona.amethyst.ui.theme.HalfStartPadding
import com.vitorpamplona.amethyst.ui.theme.RowColSpacing10dp
import com.vitorpamplona.amethyst.ui.theme.RowColSpacing5dp
import com.vitorpamplona.amethyst.ui.theme.Size10dp
import com.vitorpamplona.amethyst.ui.theme.Size25dp
import com.vitorpamplona.amethyst.ui.theme.Size30dp
import com.vitorpamplona.amethyst.ui.theme.Size34dp
import com.vitorpamplona.amethyst.ui.theme.Size55Modifier
import com.vitorpamplona.amethyst.ui.theme.Size55dp
import com.vitorpamplona.amethyst.ui.theme.StdHorzSpacer
import com.vitorpamplona.amethyst.ui.theme.UserNameMaxRowHeight
import com.vitorpamplona.amethyst.ui.theme.UserNameRowHeight
import com.vitorpamplona.amethyst.ui.theme.WidthAuthorPictureModifier
import com.vitorpamplona.amethyst.ui.theme.boostedNoteModifier
import com.vitorpamplona.amethyst.ui.theme.channelNotePictureModifier
import com.vitorpamplona.amethyst.ui.theme.grayText
import com.vitorpamplona.amethyst.ui.theme.newItemBackgroundColor
import com.vitorpamplona.amethyst.ui.theme.normalWithTopMarginNoteModifier
import com.vitorpamplona.amethyst.ui.theme.placeholderText
import com.vitorpamplona.amethyst.ui.theme.replyModifier
import com.vitorpamplona.quartz.experimental.audio.header.AudioHeaderEvent
import com.vitorpamplona.quartz.experimental.audio.track.AudioTrackEvent
import com.vitorpamplona.quartz.experimental.bounties.bountyBaseReward
import com.vitorpamplona.quartz.experimental.edits.TextNoteModificationEvent
import com.vitorpamplona.quartz.experimental.forks.IForkableEvent
import com.vitorpamplona.quartz.experimental.interactiveStories.InteractiveStoryBaseEvent
import com.vitorpamplona.quartz.experimental.medical.FhirResourceEvent
import com.vitorpamplona.quartz.experimental.nip95.header.FileStorageHeaderEvent
import com.vitorpamplona.quartz.experimental.nipsOnNostr.NipTextEvent
import com.vitorpamplona.quartz.experimental.publicMessages.PublicMessageEvent
import com.vitorpamplona.quartz.experimental.zapPolls.PollNoteEvent
import com.vitorpamplona.quartz.nip01Core.tags.geohash.geoHashOrScope
import com.vitorpamplona.quartz.nip04Dm.messages.PrivateDmEvent
import com.vitorpamplona.quartz.nip10Notes.TextNoteEvent
import com.vitorpamplona.quartz.nip13Pow.strongPoWOrNull
import com.vitorpamplona.quartz.nip17Dm.files.ChatMessageEncryptedFileHeaderEvent
import com.vitorpamplona.quartz.nip17Dm.messages.ChatMessageEvent
import com.vitorpamplona.quartz.nip17Dm.settings.ChatMessageRelayListEvent
import com.vitorpamplona.quartz.nip18Reposts.GenericRepostEvent
import com.vitorpamplona.quartz.nip18Reposts.RepostEvent
import com.vitorpamplona.quartz.nip22Comments.CommentEvent
import com.vitorpamplona.quartz.nip23LongContent.LongTextNoteEvent
import com.vitorpamplona.quartz.nip25Reactions.ReactionEvent
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
import com.vitorpamplona.quartz.nip53LiveActivities.streaming.LiveActivitiesEvent
import com.vitorpamplona.quartz.nip54Wiki.WikiNoteEvent
import com.vitorpamplona.quartz.nip56Reports.ReportEvent
import com.vitorpamplona.quartz.nip57Zaps.splits.hasZapSplitSetup
import com.vitorpamplona.quartz.nip58Badges.BadgeAwardEvent
import com.vitorpamplona.quartz.nip58Badges.BadgeDefinitionEvent
import com.vitorpamplona.quartz.nip64Chess.ChessGameEvent
import com.vitorpamplona.quartz.nip64Chess.LiveChessGameChallengeEvent
import com.vitorpamplona.quartz.nip64Chess.LiveChessGameEndEvent
import com.vitorpamplona.quartz.nip65RelayList.AdvertisedRelayListEvent
import com.vitorpamplona.quartz.nip68Picture.PictureEvent
import com.vitorpamplona.quartz.nip71Video.VideoHorizontalEvent
import com.vitorpamplona.quartz.nip71Video.VideoNormalEvent
import com.vitorpamplona.quartz.nip71Video.VideoShortEvent
import com.vitorpamplona.quartz.nip71Video.VideoVerticalEvent
import com.vitorpamplona.quartz.nip72ModCommunities.approval.CommunityPostApprovalEvent
import com.vitorpamplona.quartz.nip72ModCommunities.communityAddress
import com.vitorpamplona.quartz.nip72ModCommunities.definition.CommunityDefinitionEvent
import com.vitorpamplona.quartz.nip72ModCommunities.isACommunityPost
import com.vitorpamplona.quartz.nip84Highlights.HighlightEvent
import com.vitorpamplona.quartz.nip88Polls.poll.PollEvent
import com.vitorpamplona.quartz.nip89AppHandlers.definition.AppDefinitionEvent
import com.vitorpamplona.quartz.nip90Dvms.NIP90ContentDiscoveryResponseEvent
import com.vitorpamplona.quartz.nip90Dvms.NIP90StatusEvent
import com.vitorpamplona.quartz.nip94FileMetadata.FileHeaderEvent
import com.vitorpamplona.quartz.nip99Classifieds.ClassifiedsEvent
import com.vitorpamplona.quartz.nipA0VoiceMessages.BaseVoiceEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay

@Composable
fun NoteCompose(
    baseNote: Note,
    modifier: Modifier = Modifier,
    routeForLastRead: String? = null,
    isBoostedNote: Boolean = false,
    isQuotedNote: Boolean = false,
    unPackReply: Boolean = true,
    makeItShort: Boolean = false,
    isHiddenFeed: Boolean = false,
    quotesLeft: Int,
    parentBackgroundColor: MutableState<Color>? = null,
    accountViewModel: AccountViewModel,
    nav: INav,
    moreOptions: (@Composable () -> Unit)? = null,
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
            ignoreAllBlocksAndReports = isHiddenFeed,
            showHiddenWarning = isQuotedNote || isBoostedNote,
            accountViewModel = accountViewModel,
            nav = nav,
        ) { canPreview ->
            AcceptableNote(
                baseNote = baseNote,
                modifier = modifier,
                routeForLastRead = routeForLastRead,
                isBoostedNote = isBoostedNote,
                isQuotedNote = isQuotedNote,
                unPackReply = unPackReply,
                makeItShort = makeItShort,
                canPreview = canPreview,
                quotesLeft = quotesLeft,
                parentBackgroundColor = parentBackgroundColor,
                accountViewModel = accountViewModel,
                nav = nav,
                moreOptions = moreOptions,
            )
        }
    }
}

@Composable
fun AcceptableNote(
    baseNote: Note,
    modifier: Modifier = Modifier,
    routeForLastRead: String? = null,
    isBoostedNote: Boolean = false,
    isQuotedNote: Boolean = false,
    unPackReply: Boolean = true,
    makeItShort: Boolean = false,
    canPreview: Boolean = true,
    quotesLeft: Int,
    parentBackgroundColor: MutableState<Color>? = null,
    accountViewModel: AccountViewModel,
    nav: INav,
    moreOptions: (@Composable () -> Unit)?,
) {
    if (isQuotedNote || isBoostedNote) {
        val noteEvent = baseNote.event
        when (noteEvent) {
            is ChannelCreateEvent -> {
                RenderPublicChatChannelHeader(
                    channelId = noteEvent.id,
                    sendToChannel = true,
                    accountViewModel = accountViewModel,
                    nav = nav,
                )
            }

            is ChannelMetadataEvent -> {
                noteEvent.channelId()?.let {
                    RenderPublicChatChannelHeader(
                        channelId = it,
                        sendToChannel = true,
                        accountViewModel = accountViewModel,
                        nav = nav,
                    )
                }
            }

            is CommunityDefinitionEvent -> {
                (baseNote as? AddressableNote)?.let {
                    RenderCommunity(
                        baseNote = it,
                        accountViewModel = accountViewModel,
                        nav = nav,
                    )
                }
            }

            is BadgeDefinitionEvent -> {
                BadgeDisplay(baseNote = baseNote, accountViewModel)
            }

            else -> {
                LongPressToQuickAction(baseNote = baseNote, accountViewModel = accountViewModel, nav) { showPopup ->
                    CheckNewAndRenderNote(
                        baseNote = baseNote,
                        modifier = modifier,
                        routeForLastRead = routeForLastRead,
                        isBoostedNote = isBoostedNote,
                        isQuotedNote = isQuotedNote,
                        unPackReply = unPackReply,
                        makeItShort = makeItShort,
                        canPreview = canPreview,
                        quotesLeft = quotesLeft,
                        parentBackgroundColor = parentBackgroundColor,
                        accountViewModel = accountViewModel,
                        showPopup = showPopup,
                        nav = nav,
                        moreOptions = moreOptions,
                    )
                }
            }
        }
    } else {
        when (val noteEvent = baseNote.event) {
            is ChannelCreateEvent -> {
                RenderPublicChatChannelHeader(
                    channelId = noteEvent.id,
                    sendToChannel = true,
                    accountViewModel = accountViewModel,
                    nav = nav,
                )
            }

            is ChannelMetadataEvent -> {
                noteEvent.channelId()?.let {
                    RenderPublicChatChannelHeader(
                        channelId = it,
                        sendToChannel = true,
                        accountViewModel = accountViewModel,
                        nav = nav,
                    )
                }
            }

            is CommunityDefinitionEvent -> {
                (baseNote as? AddressableNote)?.let {
                    RenderCommunity(
                        baseNote = it,
                        accountViewModel = accountViewModel,
                        nav = nav,
                    )
                }
            }

            is BadgeDefinitionEvent -> {
                BadgeDisplay(baseNote, accountViewModel)
            }

            else -> {
                LongPressToQuickAction(baseNote, accountViewModel, nav) { showPopup ->
                    CheckNewAndRenderNote(
                        baseNote = baseNote,
                        modifier = modifier,
                        routeForLastRead = routeForLastRead,
                        isBoostedNote = isBoostedNote,
                        isQuotedNote = isQuotedNote,
                        unPackReply = unPackReply,
                        makeItShort = makeItShort,
                        canPreview = canPreview,
                        quotesLeft = quotesLeft,
                        parentBackgroundColor = parentBackgroundColor,
                        accountViewModel = accountViewModel,
                        showPopup = showPopup,
                        nav = nav,
                        moreOptions = moreOptions,
                    )
                }
            }
        }
    }
}

@Composable
fun calculateBackgroundColor(
    createdAt: Long?,
    routeForLastRead: String? = null,
    parentBackgroundColor: MutableState<Color>? = null,
    accountViewModel: AccountViewModel,
): MutableState<Color> {
    val defaultBackgroundColor = MaterialTheme.colorScheme.background
    val newItemColor = MaterialTheme.colorScheme.newItemBackgroundColor
    val bgColor =
        remember(createdAt) {
            mutableStateOf(
                if (routeForLastRead != null) {
                    val isNew = accountViewModel.loadAndMarkAsRead(routeForLastRead, createdAt)

                    if (isNew) {
                        if (parentBackgroundColor != null) {
                            newItemColor.compositeOver(parentBackgroundColor.value)
                        } else {
                            newItemColor.compositeOver(defaultBackgroundColor)
                        }
                    } else {
                        parentBackgroundColor?.value ?: defaultBackgroundColor.copy(alpha = 0f)
                    }
                } else {
                    parentBackgroundColor?.value ?: defaultBackgroundColor.copy(alpha = 0f)
                },
            )
        }

    LaunchedEffect(createdAt) {
        delay(5000)
        bgColor.value = parentBackgroundColor?.value ?: defaultBackgroundColor.copy(alpha = 0f)
    }

    return bgColor
}

@Composable
private fun CheckNewAndRenderNote(
    baseNote: Note,
    modifier: Modifier = Modifier,
    routeForLastRead: String? = null,
    isBoostedNote: Boolean = false,
    isQuotedNote: Boolean = false,
    unPackReply: Boolean = true,
    makeItShort: Boolean = false,
    canPreview: Boolean = true,
    quotesLeft: Int,
    parentBackgroundColor: MutableState<Color>? = null,
    accountViewModel: AccountViewModel,
    showPopup: () -> Unit,
    nav: INav,
    moreOptions: (@Composable () -> Unit)? = null,
) {
    val backgroundColor =
        calculateBackgroundColor(
            baseNote.createdAt(),
            routeForLastRead,
            parentBackgroundColor,
            accountViewModel,
        )

    ClickableNote(
        baseNote = baseNote,
        backgroundColor = backgroundColor,
        modifier = modifier,
        accountViewModel = accountViewModel,
        showPopup = showPopup,
        nav = nav,
    ) {
        InnerNoteWithReactions(
            baseNote = baseNote,
            backgroundColor = backgroundColor,
            isBoostedNote = isBoostedNote,
            isQuotedNote = isQuotedNote,
            unPackReply = unPackReply,
            makeItShort = makeItShort,
            canPreview = canPreview,
            quotesLeft = quotesLeft,
            accountViewModel = accountViewModel,
            nav = nav,
            moreOptions = moreOptions,
        )
    }
}

@Composable
@OptIn(ExperimentalFoundationApi::class)
fun ClickableNote(
    baseNote: Note,
    modifier: Modifier,
    backgroundColor: MutableState<Color>,
    accountViewModel: AccountViewModel,
    showPopup: () -> Unit,
    nav: INav,
    content: @Composable () -> Unit,
) {
    val updatedModifier =
        remember(baseNote, backgroundColor.value, modifier) {
            modifier
                .combinedClickable(
                    onClick = {
                        val redirectToNote =
                            if (baseNote.event is RepostEvent || baseNote.event is GenericRepostEvent) {
                                baseNote.replyTo?.lastOrNull() ?: baseNote
                            } else {
                                baseNote
                            }

                        nav.nav {
                            if (redirectToNote.event is DraftWrapEvent) {
                                with(Dispatchers.IO) {
                                    routeEditDraftTo(redirectToNote, accountViewModel.account)
                                }
                            } else {
                                routeFor(redirectToNote, accountViewModel.account)
                            }
                        }
                    },
                    onLongClick = showPopup,
                ).background(backgroundColor.value)
        }

    Column(modifier = updatedModifier) { content() }
}

@Composable
fun InnerNoteWithReactions(
    baseNote: Note,
    backgroundColor: MutableState<Color>,
    isBoostedNote: Boolean,
    isQuotedNote: Boolean,
    unPackReply: Boolean,
    makeItShort: Boolean,
    canPreview: Boolean,
    quotesLeft: Int,
    accountViewModel: AccountViewModel,
    nav: INav,
    moreOptions: (@Composable () -> Unit)? = null,
) {
    val notBoostedNorQuote = !isBoostedNote && !isQuotedNote
    val editState = observeEdits(baseNote = baseNote, accountViewModel = accountViewModel)

    Row(
        modifier =
            if (!isBoostedNote) {
                normalWithTopMarginNoteModifier
            } else {
                boostedNoteModifier
            },
        horizontalArrangement = RowColSpacing10dp,
    ) {
        if (notBoostedNorQuote) {
            Column(WidthAuthorPictureModifier, verticalArrangement = RowColSpacing5dp) {
                // Draws the boosted picture outside the boosted card.
                Box(modifier = Size55Modifier, contentAlignment = Alignment.BottomEnd) {
                    RenderAuthorImages(baseNote, nav, accountViewModel)
                }

                BadgeBox(baseNote, accountViewModel, nav)
            }
        }

        Column(Modifier.fillMaxWidth()) {
            val showSecondRow =
                baseNote.event !is RepostEvent &&
                    baseNote.event !is GenericRepostEvent &&
                    !isBoostedNote &&
                    !isQuotedNote &&
                    accountViewModel.settings.isCompleteUIMode()
            NoteBody(
                baseNote = baseNote,
                showAuthorPicture = isQuotedNote,
                unPackReply = unPackReply,
                makeItShort = makeItShort,
                canPreview = canPreview,
                showSecondRow = showSecondRow,
                quotesLeft = quotesLeft,
                backgroundColor = backgroundColor,
                editState = editState,
                accountViewModel = accountViewModel,
                nav = nav,
                moreOptions = moreOptions,
            )

            RenderApprovalIfNeeded(baseNote, accountViewModel, nav)
        }
    }

    val isNotRepost = baseNote.event !is RepostEvent && baseNote.event !is GenericRepostEvent && baseNote.event !is DraftWrapEvent

    if (isNotRepost) {
        if (makeItShort) {
            Spacer(modifier = DoubleVertSpacer)
        } else {
            ReactionsRow(
                baseNote = baseNote,
                showReactionDetail = notBoostedNorQuote,
                addPadding = !isBoostedNote,
                editState = editState,
                accountViewModel = accountViewModel,
                nav = nav,
            )
        }
    } else {
        if (baseNote.event is DraftWrapEvent) {
            Spacer(modifier = DoubleVertSpacer)
        }
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
                        RenderApproveButton(baseNote, community, accountViewModel)
                    }
                }
            }
        }
    }
}

@Composable
fun RenderApproveButton(
    post: Note,
    community: AddressableNote,
    accountViewModel: AccountViewModel,
) {
    OutlinedButton(
        modifier = Modifier.fillMaxWidth().defaultMinSize(minWidth = Size10dp, minHeight = Size30dp),
        contentPadding = PaddingValues(0.dp),
        onClick = {
            accountViewModel.approveCommunityPost(post, community)
        },
    ) {
        Text("Approve")
    }
}

@Composable
fun NoteBody(
    baseNote: Note,
    showAuthorPicture: Boolean = false,
    unPackReply: Boolean = true,
    makeItShort: Boolean = false,
    canPreview: Boolean = true,
    showSecondRow: Boolean,
    quotesLeft: Int,
    backgroundColor: MutableState<Color>,
    editState: State<GenericLoadable<EditState>>,
    accountViewModel: AccountViewModel,
    nav: INav,
    moreOptions: (@Composable () -> Unit)? = null,
) {
    FirstUserInfoRow(
        baseNote = baseNote,
        showAuthorPicture = showAuthorPicture,
        editState = editState,
        accountViewModel = accountViewModel,
        nav = nav,
        moreOptions = moreOptions,
    )

    if (showSecondRow) {
        SecondUserInfoRow(
            baseNote,
            editState,
            accountViewModel,
            nav,
        )
    }

    if (baseNote.event !is RepostEvent && baseNote.event !is GenericRepostEvent) {
        Spacer(modifier = Modifier.height(4.dp))
    }

    RenderNoteRow(
        baseNote = baseNote,
        backgroundColor = backgroundColor,
        makeItShort = makeItShort,
        canPreview = canPreview,
        editState = editState,
        quotesLeft = quotesLeft,
        unPackReply = unPackReply,
        accountViewModel = accountViewModel,
        nav = nav,
    )

    if (!makeItShort) {
        val noteEvent = baseNote.event
        val zapSplits = remember(noteEvent) { noteEvent?.hasZapSplitSetup() ?: false }
        if (zapSplits && noteEvent != null) {
            Spacer(modifier = HalfDoubleVertSpacer)
            DisplayZapSplits(noteEvent, false, accountViewModel, nav)
        }
    }
}

@Composable
private fun RenderNoteRow(
    baseNote: Note,
    backgroundColor: MutableState<Color>,
    makeItShort: Boolean,
    canPreview: Boolean,
    quotesLeft: Int,
    unPackReply: Boolean,
    editState: State<GenericLoadable<EditState>>,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    when (val noteEvent = baseNote.event) {
        is AppDefinitionEvent -> {
            RenderAppDefinition(baseNote, accountViewModel, nav)
        }

        is AudioTrackEvent -> {
            RenderAudioTrack(baseNote, ContentScale.FillWidth, accountViewModel, nav)
        }

        is AudioHeaderEvent -> {
            RenderAudioHeader(baseNote, ContentScale.FillWidth, accountViewModel, nav)
        }

        is DraftWrapEvent -> {
            RenderDraft(baseNote, quotesLeft, unPackReply, backgroundColor, accountViewModel, nav)
        }

        is ReactionEvent -> {
            RenderReaction(baseNote, quotesLeft, backgroundColor, accountViewModel, nav)
        }

        is RepostEvent -> {
            RenderRepost(baseNote, quotesLeft, backgroundColor, accountViewModel, nav)
        }

        is GenericRepostEvent -> {
            RenderRepost(baseNote, quotesLeft, backgroundColor, accountViewModel, nav)
        }

        is ReportEvent -> {
            RenderReport(baseNote, quotesLeft, backgroundColor, accountViewModel, nav)
        }

        is LongTextNoteEvent -> {
            RenderLongFormContent(baseNote, accountViewModel, nav)
        }

        is WikiNoteEvent -> {
            RenderWikiContent(baseNote, accountViewModel, nav)
        }

        is NipTextEvent -> {
            RenderNipContent(baseNote, accountViewModel, nav)
        }

        is BadgeAwardEvent -> {
            RenderBadgeAward(baseNote, backgroundColor, accountViewModel, nav)
        }

        is FhirResourceEvent -> {
            RenderFhirResource(baseNote, accountViewModel, nav)
        }

        is PeopleListEvent -> {
            DisplayPeopleList(baseNote, backgroundColor, accountViewModel, nav)
        }

        is FollowListEvent -> {
            DisplayFollowList(baseNote, true, accountViewModel, nav)
        }

        is RelaySetEvent -> {
            DisplayRelaySet(baseNote, backgroundColor, accountViewModel, nav)
        }

        is ChatMessageRelayListEvent -> {
            DisplayDMRelayList(baseNote, backgroundColor, accountViewModel, nav)
        }

        is AdvertisedRelayListEvent -> {
            DisplayNIP65RelayList(baseNote, backgroundColor, accountViewModel, nav)
        }

        is SearchRelayListEvent -> {
            DisplaySearchRelayList(baseNote, backgroundColor, accountViewModel, nav)
        }

        is BlockedRelayListEvent -> {
            DisplayBlockedRelayList(baseNote, backgroundColor, accountViewModel, nav)
        }

        is TrustedRelayListEvent -> {
            DisplayTrustedRelayList(baseNote, backgroundColor, accountViewModel, nav)
        }

        is IndexerRelayListEvent -> {
            DisplayIndexerRelayList(baseNote, backgroundColor, accountViewModel, nav)
        }

        is ProxyRelayListEvent -> {
            DisplayProxyRelayList(baseNote, backgroundColor, accountViewModel, nav)
        }

        is BroadcastRelayListEvent -> {
            DisplayBroadcastRelayList(baseNote, backgroundColor, accountViewModel, nav)
        }

        is PinListEvent -> {
            RenderPinListEvent(baseNote, backgroundColor, accountViewModel, nav)
        }

        is EmojiPackEvent -> {
            RenderEmojiPack(baseNote, true, backgroundColor, accountViewModel)
        }

        is LiveActivitiesEvent -> {
            RenderLiveActivityEvent(baseNote, accountViewModel, nav)
        }

        is GitRepositoryEvent -> {
            RenderGitRepositoryEvent(baseNote, accountViewModel, nav)
        }

        is GitPatchEvent -> {
            RenderGitPatchEvent(
                baseNote,
                makeItShort,
                canPreview,
                quotesLeft,
                backgroundColor,
                accountViewModel,
                nav,
            )
        }

        is GitIssueEvent -> {
            RenderGitIssueEvent(
                baseNote,
                makeItShort,
                canPreview,
                quotesLeft,
                backgroundColor,
                accountViewModel,
                nav,
            )
        }

        is PrivateDmEvent -> {
            RenderPrivateMessage(
                baseNote,
                makeItShort,
                canPreview,
                quotesLeft,
                backgroundColor,
                accountViewModel,
                nav,
            )
        }

        is ChatMessageEvent -> {
            RenderChatMessage(
                baseNote,
                makeItShort,
                canPreview,
                quotesLeft,
                backgroundColor,
                editState,
                accountViewModel,
                nav,
            )
        }

        is ChatMessageEncryptedFileHeaderEvent -> {
            RenderChatMessageEncryptedFile(
                baseNote,
                makeItShort,
                canPreview,
                quotesLeft,
                backgroundColor,
                editState,
                accountViewModel,
                nav,
            )
        }

        is ChessGameEvent -> {
            RenderChessGame(
                baseNote,
                backgroundColor,
                accountViewModel,
                nav,
            )
        }

        is LiveChessGameChallengeEvent -> {
            RenderLiveChessChallenge(
                baseNote,
                backgroundColor,
                accountViewModel,
                nav,
            )
        }

        is LiveChessGameEndEvent -> {
            RenderLiveChessGameEnd(
                baseNote,
                backgroundColor,
                accountViewModel,
                nav,
            )
        }

        is ClassifiedsEvent -> {
            RenderClassifieds(
                noteEvent,
                baseNote,
                accountViewModel,
                nav,
            )
        }

        is HighlightEvent -> {
            RenderHighlight(
                baseNote,
                makeItShort,
                canPreview,
                quotesLeft,
                backgroundColor,
                accountViewModel,
                nav,
            )
        }

        is CommentEvent -> {
            RenderTextEvent(
                baseNote,
                makeItShort,
                canPreview,
                quotesLeft,
                unPackReply,
                backgroundColor,
                editState,
                accountViewModel,
                nav,
            )
        }

        is NIP90ContentDiscoveryResponseEvent -> {
            RenderNIP90ContentDiscoveryResponse(
                baseNote,
                makeItShort,
                canPreview,
                quotesLeft,
                backgroundColor,
                accountViewModel,
                nav,
            )
        }

        is NIP90StatusEvent -> {
            RenderNIP90Status(
                baseNote,
                accountViewModel,
                nav,
            )
        }

        is PollNoteEvent -> {
            RenderZapPoll(
                baseNote,
                makeItShort,
                canPreview,
                quotesLeft,
                unPackReply,
                backgroundColor,
                accountViewModel,
                nav,
            )
        }

        is PollEvent -> {
            RenderPoll(
                baseNote,
                makeItShort,
                canPreview,
                quotesLeft,
                backgroundColor,
                accountViewModel,
                nav,
            )
        }

        is FileHeaderEvent -> {
            FileHeaderDisplay(baseNote, true, ContentScale.FillWidth, accountViewModel)
        }

        is VideoHorizontalEvent -> {
            VideoDisplay(baseNote, makeItShort, canPreview, backgroundColor, ContentScale.FillWidth, accountViewModel, nav)
        }

        is VideoVerticalEvent -> {
            VideoDisplay(baseNote, makeItShort, canPreview, backgroundColor, ContentScale.FillWidth, accountViewModel, nav)
        }

        is VideoNormalEvent -> {
            VideoDisplay(baseNote, makeItShort, canPreview, backgroundColor, ContentScale.FillWidth, accountViewModel, nav)
        }

        is VideoShortEvent -> {
            VideoDisplay(baseNote, makeItShort, canPreview, backgroundColor, ContentScale.FillWidth, accountViewModel, nav)
        }

        is PictureEvent -> {
            PictureDisplay(baseNote, true, ContentScale.FillWidth, PaddingValues(vertical = 5.dp), backgroundColor, accountViewModel, nav)
        }

        is BaseVoiceEvent -> {
            RenderVoiceTrack(baseNote, accountViewModel, nav)
        }

        is FileStorageHeaderEvent -> {
            FileStorageHeaderDisplay(baseNote, true, ContentScale.FillWidth, accountViewModel)
        }

        is CommunityPostApprovalEvent -> {
            RenderPostApproval(
                baseNote,
                quotesLeft,
                backgroundColor,
                accountViewModel,
                nav,
            )
        }

        is TextNoteModificationEvent -> {
            RenderTextModificationEvent(
                baseNote,
                makeItShort,
                canPreview,
                quotesLeft,
                backgroundColor,
                accountViewModel,
                nav,
            )
        }

        is ChannelMessageEvent -> {
            RenderChannelMessage(
                baseNote,
                makeItShort,
                canPreview,
                quotesLeft,
                backgroundColor,
                editState,
                accountViewModel,
                nav,
            )
        }

        is LiveActivitiesChatMessageEvent -> {
            RenderLiveActivityChatMessage(
                baseNote,
                makeItShort,
                canPreview,
                quotesLeft,
                backgroundColor,
                editState,
                accountViewModel,
                nav,
            )
        }

        is TorrentEvent -> {
            RenderTorrent(
                baseNote,
                backgroundColor,
                accountViewModel,
                nav,
            )
        }

        is TorrentCommentEvent -> {
            RenderTorrentComment(
                baseNote,
                makeItShort,
                canPreview,
                quotesLeft,
                unPackReply,
                backgroundColor,
                editState,
                accountViewModel,
                nav,
            )
        }

        is InteractiveStoryBaseEvent -> {
            RenderInteractiveStory(
                baseNote,
                makeItShort,
                canPreview,
                quotesLeft,
                backgroundColor,
                accountViewModel,
                nav,
            )
        }

        is PublicMessageEvent -> {
            RenderPublicMessage(
                baseNote,
                makeItShort,
                canPreview,
                quotesLeft,
                backgroundColor,
                accountViewModel,
                nav,
            )
        }

        else -> {
            RenderTextEvent(
                baseNote,
                makeItShort,
                canPreview,
                quotesLeft,
                unPackReply,
                backgroundColor,
                editState,
                accountViewModel,
                nav,
            )
        }
    }
}

@Composable
fun ObserveDraftEvent(
    note: Note,
    accountViewModel: AccountViewModel,
    render: @Composable (Note) -> Unit,
) {
    val noteEvent by observeNoteEvent<DraftWrapEvent>(note, accountViewModel)

    noteEvent?.let {
        val innerNote by produceCachedStateAsync(cache = accountViewModel.draftNoteCache, key = it)

        innerNote?.let {
            render(it)
        }
    }
}

@Composable
fun RenderDraft(
    note: Note,
    quotesLeft: Int,
    unPackReply: Boolean,
    backgroundColor: MutableState<Color>,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    ObserveDraftEvent(note, accountViewModel) {
        val edits = remember { mutableStateOf(GenericLoadable.Empty<EditState>()) }

        RenderNoteRow(
            baseNote = it,
            backgroundColor = backgroundColor,
            makeItShort = false,
            canPreview = true,
            editState = edits,
            quotesLeft = quotesLeft,
            unPackReply = unPackReply,
            accountViewModel = accountViewModel,
            nav = nav,
        )

        val zapSplits = remember(it.event) { it.event?.hasZapSplitSetup() }
        if (zapSplits == true) {
            Spacer(modifier = HalfDoubleVertSpacer)
            DisplayZapSplits(it.event!!, false, accountViewModel, nav)
        }
    }
}

@Composable
fun RenderRepost(
    note: Note,
    quotesLeft: Int,
    backgroundColor: MutableState<Color>,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    note.replyTo?.lastOrNull { it.event !is CommunityDefinitionEvent }?.let {
        NoteCompose(
            it,
            modifier = Modifier,
            isBoostedNote = true,
            unPackReply = false,
            quotesLeft = quotesLeft - 1,
            parentBackgroundColor = backgroundColor,
            accountViewModel = accountViewModel,
            nav = nav,
        )
    }
}

fun getGradient(backgroundColor: MutableState<Color>): Brush =
    Brush.verticalGradient(
        colors =
            listOf(
                backgroundColor.value.copy(alpha = 0f),
                backgroundColor.value.copy(alpha = 1f),
            ),
    )

@Composable
fun ReplyNoteComposition(
    replyingDirectlyTo: Note,
    backgroundColor: MutableState<Color>,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    NoteCompose(
        baseNote = replyingDirectlyTo,
        modifier = MaterialTheme.colorScheme.replyModifier,
        isQuotedNote = true,
        unPackReply = false,
        makeItShort = true,
        quotesLeft = 0,
        parentBackgroundColor = backgroundColor,
        accountViewModel = accountViewModel,
        nav = nav,
    )
}

@Composable
fun SecondUserInfoRow(
    note: Note,
    editState: State<GenericLoadable<EditState>>,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val noteEvent = note.event ?: return
    val noteAuthor = note.author ?: return

    Row(
        verticalAlignment = CenterVertically,
        modifier = UserNameMaxRowHeight,
    ) {
        Column(modifier = remember(noteEvent) { Modifier.weight(1f) }) {
            if (noteEvent is IForkableEvent && noteEvent.isAFork()) {
                ShowForkInformation(noteEvent, Modifier, accountViewModel, nav)
            } else {
                ObserveDisplayNip05Status(noteAuthor, accountViewModel, nav)
            }
        }

        val geo = remember(noteEvent) { noteEvent.geoHashOrScope() }
        if (geo != null) {
            Spacer(StdHorzSpacer)
            DisplayLocation(geo, accountViewModel, nav)
        }

        val baseReward = remember(noteEvent) { noteEvent.bountyBaseReward()?.let { Reward(it) } }
        if (baseReward != null) {
            Spacer(StdHorzSpacer)
            DisplayReward(baseReward, note, accountViewModel, nav)
        }

        val pow = remember(noteEvent) { noteEvent.strongPoWOrNull() }
        if (pow != null) {
            Spacer(StdHorzSpacer)
            DisplayPoW(pow)
        }

        DisplayOtsIfInOriginal(note, editState, accountViewModel)
    }
}

@Composable
fun DisplayOtsIfInOriginal(
    note: Note,
    editState: State<GenericLoadable<EditState>>,
    accountViewModel: AccountViewModel,
) {
    val editNote = (editState.value as? GenericLoadable.Loaded<EditState>)?.loaded?.modificationToShow?.value

    if (editNote == null) {
        DisplayOts(note = note, accountViewModel = accountViewModel)
    } else {
        DisplayOts(note = editNote, accountViewModel = accountViewModel)
    }
}

@Composable
fun DisplayDraft() {
    Text(
        "Draft",
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.placeholderText,
        maxLines = 1,
        modifier = HalfStartPadding,
    )
}

@Composable
fun DisplayDraftChat() {
    Text(
        "Draft",
        color = MaterialTheme.colorScheme.placeholderText,
        modifier = Modifier,
        fontWeight = FontWeight.Bold,
        fontSize = Font12SP,
        maxLines = 1,
    )
}

@Composable
fun FirstUserInfoRow(
    baseNote: Note,
    showAuthorPicture: Boolean,
    editState: State<GenericLoadable<EditState>>,
    accountViewModel: AccountViewModel,
    nav: INav,
    moreOptions: (@Composable () -> Unit)? = null,
) {
    Row(verticalAlignment = CenterVertically, modifier = UserNameRowHeight) {
        val isRepost = baseNote.event is RepostEvent || baseNote.event is GenericRepostEvent
        val isDraft = baseNote.isDraft()
        val textColor = if (isRepost) MaterialTheme.colorScheme.grayText else Color.Unspecified

        if (showAuthorPicture) {
            NoteAuthorPicture(baseNote, Size25dp, accountViewModel = accountViewModel, nav = nav)
            Spacer(HalfPadding)
            NoteUsernameDisplay(baseNote, Modifier.weight(1f), textColor = textColor, accountViewModel = accountViewModel)
        } else {
            NoteUsernameDisplay(baseNote, Modifier.weight(1f), textColor = textColor, accountViewModel = accountViewModel)
        }

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

        if (isRepost) {
            BoostedMark()
        }

        CheckAndDisplayEditStatus(editState)

        if (isDraft) {
            DisplayDraft()
        }

        TimeAgo(baseNote)

        if (moreOptions == null) {
            MoreOptionsButton(baseNote, editState, accountViewModel, nav)
        } else {
            moreOptions()
        }
    }
}

@Composable
fun CheckAndDisplayEditStatus(editState: State<GenericLoadable<EditState>>) {
    if (editState.value is GenericLoadable.Loaded) {
        (editState.value as? GenericLoadable.Loaded<EditState>)?.loaded?.let {
            DisplayEditStatus(it)
        }
    }
}

@Composable
fun observeEdits(
    baseNote: Note,
    accountViewModel: AccountViewModel,
): State<GenericLoadable<EditState>> {
    if (baseNote.event !is TextNoteEvent) {
        return EmptyState
    }

    val editState =
        remember(baseNote.idHex) {
            val cached = accountViewModel.cachedModificationEventsForNote(baseNote)
            mutableStateOf(
                if (cached != null) {
                    if (cached.isEmpty()) {
                        GenericLoadable.Empty()
                    } else {
                        val state = EditState()
                        state.updateModifications(cached)
                        GenericLoadable.Loaded(state)
                    }
                } else {
                    GenericLoadable.Loading()
                },
            )
        }

    val updatedNote by observeNoteEdits(baseNote, accountViewModel)

    LaunchedEffect(key1 = updatedNote) {
        updatedNote?.note?.let {
            val newModifications = accountViewModel.findModificationEventsForNote(it)
            if (newModifications.isEmpty()) {
                if (editState.value !is GenericLoadable.Empty) {
                    editState.value = GenericLoadable.Empty()
                }
            } else {
                if (editState.value is GenericLoadable.Loaded) {
                    (editState.value as? GenericLoadable.Loaded<EditState>)?.loaded?.updateModifications(newModifications)
                } else {
                    val state = EditState()
                    state.updateModifications(newModifications)
                    editState.value = GenericLoadable.Loaded(state)
                }
            }
        }
    }

    return editState
}

@Composable
fun BadgeBox(
    baseNote: Note,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    if (accountViewModel.settings.isCompleteUIMode()) {
        if (baseNote.event is RepostEvent || baseNote.event is GenericRepostEvent) {
            baseNote.replyTo?.lastOrNull()?.let { RelayBadges(it, accountViewModel, nav) }
        } else {
            RelayBadges(baseNote, accountViewModel, nav)
        }
    }
}

@Composable
fun RenderAuthorImages(
    baseNote: Note,
    nav: INav,
    accountViewModel: AccountViewModel,
) {
    val noteEvent = baseNote.event
    if (noteEvent is RepostEvent || noteEvent is GenericRepostEvent) {
        val baseRepost = baseNote.replyTo?.lastOrNull()
        if (baseRepost != null) {
            RepostNoteAuthorPicture(baseNote, baseRepost, accountViewModel, nav)
        } else {
            NoteAuthorPicture(baseNote, Size55dp, accountViewModel = accountViewModel, nav = nav)
        }
    } else {
        NoteAuthorPicture(baseNote, Size55dp, accountViewModel = accountViewModel, nav = nav)
    }

    if (noteEvent is ChannelMessageEvent) {
        val baseChannelHex = noteEvent.channelId()
        if (baseChannelHex != null) {
            LoadPublicChatChannel(baseChannelHex, accountViewModel) { channel ->
                ChannelNotePicture(
                    channel,
                    accountViewModel,
                )
            }
        }
    }
}

@Composable
private fun ChannelNotePicture(
    baseChannel: PublicChatChannel,
    accountViewModel: AccountViewModel,
) {
    val model by observeChannelPicture(baseChannel, accountViewModel)

    RobohashFallbackAsyncImage(
        robot = baseChannel.idHex,
        model = model,
        contentDescription = stringRes(R.string.group_picture),
        modifier = MaterialTheme.colorScheme.channelNotePictureModifier,
        loadProfilePicture = accountViewModel.settings.showProfilePictures(),
        loadRobohash = accountViewModel.settings.isNotPerformanceMode(),
    )
}

@Composable
private fun RepostNoteAuthorPicture(
    baseNote: Note,
    baseRepost: Note,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    GenericRepostLayout(
        baseAuthorPicture = {
            NoteAuthorPicture(
                baseNote = baseNote,
                size = Size34dp,
                accountViewModel = accountViewModel,
                nav = nav,
            )
        },
        repostAuthorPicture = {
            NoteAuthorPicture(
                baseNote = baseRepost,
                size = Size34dp,
                accountViewModel = accountViewModel,
                nav = nav,
            )
        },
    )
}
