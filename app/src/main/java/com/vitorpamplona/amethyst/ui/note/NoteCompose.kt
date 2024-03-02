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
package com.vitorpamplona.amethyst.ui.note

import android.graphics.Bitmap
import android.util.Log
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.foundation.text.ClickableText
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Divider
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Alignment.Companion.CenterVertically
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import androidx.core.graphics.get
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.distinctUntilChanged
import androidx.lifecycle.map
import coil.compose.AsyncImage
import coil.compose.AsyncImagePainter
import coil.request.SuccessResult
import com.fonfon.kgeohash.toGeoHash
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.commons.BaseMediaContent
import com.vitorpamplona.amethyst.commons.MediaLocalImage
import com.vitorpamplona.amethyst.commons.MediaLocalVideo
import com.vitorpamplona.amethyst.commons.MediaUrlImage
import com.vitorpamplona.amethyst.commons.MediaUrlVideo
import com.vitorpamplona.amethyst.commons.RichTextParser
import com.vitorpamplona.amethyst.model.AddressableNote
import com.vitorpamplona.amethyst.model.Channel
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.model.RelayBriefInfoCache
import com.vitorpamplona.amethyst.model.User
import com.vitorpamplona.amethyst.service.CachedGeoLocations
import com.vitorpamplona.amethyst.ui.actions.EditPostView
import com.vitorpamplona.amethyst.ui.actions.NewRelayListView
import com.vitorpamplona.amethyst.ui.components.ClickableUrl
import com.vitorpamplona.amethyst.ui.components.CreateClickableTextWithEmoji
import com.vitorpamplona.amethyst.ui.components.CreateTextWithEmoji
import com.vitorpamplona.amethyst.ui.components.GenericLoadable
import com.vitorpamplona.amethyst.ui.components.LoadNote
import com.vitorpamplona.amethyst.ui.components.LoadThumbAndThenVideoView
import com.vitorpamplona.amethyst.ui.components.ObserveDisplayNip05Status
import com.vitorpamplona.amethyst.ui.components.RobohashFallbackAsyncImage
import com.vitorpamplona.amethyst.ui.components.SensitivityWarning
import com.vitorpamplona.amethyst.ui.components.ShowMoreButton
import com.vitorpamplona.amethyst.ui.components.TranslatableRichTextViewer
import com.vitorpamplona.amethyst.ui.components.VideoView
import com.vitorpamplona.amethyst.ui.components.ZoomableContentView
import com.vitorpamplona.amethyst.ui.components.ZoomableImageDialog
import com.vitorpamplona.amethyst.ui.components.measureSpaceWidth
import com.vitorpamplona.amethyst.ui.elements.AddButton
import com.vitorpamplona.amethyst.ui.elements.DisplayFollowingCommunityInPost
import com.vitorpamplona.amethyst.ui.elements.DisplayFollowingHashtagsInPost
import com.vitorpamplona.amethyst.ui.elements.DisplayPoW
import com.vitorpamplona.amethyst.ui.elements.DisplayReward
import com.vitorpamplona.amethyst.ui.elements.DisplayUncitedHashtags
import com.vitorpamplona.amethyst.ui.elements.DisplayZapSplits
import com.vitorpamplona.amethyst.ui.elements.RemoveButton
import com.vitorpamplona.amethyst.ui.elements.Reward
import com.vitorpamplona.amethyst.ui.layouts.GenericRepostLayout
import com.vitorpamplona.amethyst.ui.navigation.routeFor
import com.vitorpamplona.amethyst.ui.screen.equalImmutableLists
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.screen.loggedIn.ChannelHeader
import com.vitorpamplona.amethyst.ui.screen.loggedIn.CheckIfUrlIsOnline
import com.vitorpamplona.amethyst.ui.screen.loggedIn.CrossfadeCheckIfUrlIsOnline
import com.vitorpamplona.amethyst.ui.screen.loggedIn.JoinCommunityButton
import com.vitorpamplona.amethyst.ui.screen.loggedIn.LeaveCommunityButton
import com.vitorpamplona.amethyst.ui.screen.loggedIn.LiveFlag
import com.vitorpamplona.amethyst.ui.screen.loggedIn.NormalTimeAgo
import com.vitorpamplona.amethyst.ui.screen.loggedIn.ScheduledFlag
import com.vitorpamplona.amethyst.ui.theme.DividerThickness
import com.vitorpamplona.amethyst.ui.theme.DoubleHorzSpacer
import com.vitorpamplona.amethyst.ui.theme.DoubleVertSpacer
import com.vitorpamplona.amethyst.ui.theme.Font14SP
import com.vitorpamplona.amethyst.ui.theme.HalfDoubleVertSpacer
import com.vitorpamplona.amethyst.ui.theme.HalfPadding
import com.vitorpamplona.amethyst.ui.theme.HalfStartPadding
import com.vitorpamplona.amethyst.ui.theme.HeaderPictureModifier
import com.vitorpamplona.amethyst.ui.theme.QuoteBorder
import com.vitorpamplona.amethyst.ui.theme.Size10dp
import com.vitorpamplona.amethyst.ui.theme.Size15Modifier
import com.vitorpamplona.amethyst.ui.theme.Size16Modifier
import com.vitorpamplona.amethyst.ui.theme.Size24Modifier
import com.vitorpamplona.amethyst.ui.theme.Size25dp
import com.vitorpamplona.amethyst.ui.theme.Size30Modifier
import com.vitorpamplona.amethyst.ui.theme.Size34dp
import com.vitorpamplona.amethyst.ui.theme.Size35Modifier
import com.vitorpamplona.amethyst.ui.theme.Size35dp
import com.vitorpamplona.amethyst.ui.theme.Size55Modifier
import com.vitorpamplona.amethyst.ui.theme.Size55dp
import com.vitorpamplona.amethyst.ui.theme.Size5dp
import com.vitorpamplona.amethyst.ui.theme.SmallBorder
import com.vitorpamplona.amethyst.ui.theme.StdHorzSpacer
import com.vitorpamplona.amethyst.ui.theme.StdPadding
import com.vitorpamplona.amethyst.ui.theme.StdVertSpacer
import com.vitorpamplona.amethyst.ui.theme.UserNameMaxRowHeight
import com.vitorpamplona.amethyst.ui.theme.UserNameRowHeight
import com.vitorpamplona.amethyst.ui.theme.WidthAuthorPictureModifier
import com.vitorpamplona.amethyst.ui.theme.boostedNoteModifier
import com.vitorpamplona.amethyst.ui.theme.channelNotePictureModifier
import com.vitorpamplona.amethyst.ui.theme.grayText
import com.vitorpamplona.amethyst.ui.theme.imageModifier
import com.vitorpamplona.amethyst.ui.theme.innerPostModifier
import com.vitorpamplona.amethyst.ui.theme.lessImportantLink
import com.vitorpamplona.amethyst.ui.theme.mediumImportanceLink
import com.vitorpamplona.amethyst.ui.theme.newItemBackgroundColor
import com.vitorpamplona.amethyst.ui.theme.nip05
import com.vitorpamplona.amethyst.ui.theme.normalNoteModifier
import com.vitorpamplona.amethyst.ui.theme.normalWithTopMarginNoteModifier
import com.vitorpamplona.amethyst.ui.theme.placeholderText
import com.vitorpamplona.amethyst.ui.theme.replyBackground
import com.vitorpamplona.amethyst.ui.theme.replyModifier
import com.vitorpamplona.amethyst.ui.theme.subtleBorder
import com.vitorpamplona.quartz.encoders.ATag
import com.vitorpamplona.quartz.encoders.toNpub
import com.vitorpamplona.quartz.events.AppDefinitionEvent
import com.vitorpamplona.quartz.events.AudioHeaderEvent
import com.vitorpamplona.quartz.events.AudioTrackEvent
import com.vitorpamplona.quartz.events.BadgeAwardEvent
import com.vitorpamplona.quartz.events.BadgeDefinitionEvent
import com.vitorpamplona.quartz.events.BaseTextNoteEvent
import com.vitorpamplona.quartz.events.ChannelCreateEvent
import com.vitorpamplona.quartz.events.ChannelMessageEvent
import com.vitorpamplona.quartz.events.ChannelMetadataEvent
import com.vitorpamplona.quartz.events.ClassifiedsEvent
import com.vitorpamplona.quartz.events.CommunityDefinitionEvent
import com.vitorpamplona.quartz.events.CommunityPostApprovalEvent
import com.vitorpamplona.quartz.events.EmojiPackEvent
import com.vitorpamplona.quartz.events.EmojiPackSelectionEvent
import com.vitorpamplona.quartz.events.EmojiUrl
import com.vitorpamplona.quartz.events.EmptyTagList
import com.vitorpamplona.quartz.events.Event
import com.vitorpamplona.quartz.events.FhirResourceEvent
import com.vitorpamplona.quartz.events.FileHeaderEvent
import com.vitorpamplona.quartz.events.FileStorageHeaderEvent
import com.vitorpamplona.quartz.events.GenericRepostEvent
import com.vitorpamplona.quartz.events.GitPatchEvent
import com.vitorpamplona.quartz.events.GitRepositoryEvent
import com.vitorpamplona.quartz.events.HighlightEvent
import com.vitorpamplona.quartz.events.LiveActivitiesChatMessageEvent
import com.vitorpamplona.quartz.events.LiveActivitiesEvent
import com.vitorpamplona.quartz.events.LiveActivitiesEvent.Companion.STATUS_ENDED
import com.vitorpamplona.quartz.events.LiveActivitiesEvent.Companion.STATUS_LIVE
import com.vitorpamplona.quartz.events.LiveActivitiesEvent.Companion.STATUS_PLANNED
import com.vitorpamplona.quartz.events.LongTextNoteEvent
import com.vitorpamplona.quartz.events.Participant
import com.vitorpamplona.quartz.events.PeopleListEvent
import com.vitorpamplona.quartz.events.PinListEvent
import com.vitorpamplona.quartz.events.PollNoteEvent
import com.vitorpamplona.quartz.events.PrivateDmEvent
import com.vitorpamplona.quartz.events.ReactionEvent
import com.vitorpamplona.quartz.events.RelaySetEvent
import com.vitorpamplona.quartz.events.ReportEvent
import com.vitorpamplona.quartz.events.RepostEvent
import com.vitorpamplona.quartz.events.TextNoteEvent
import com.vitorpamplona.quartz.events.TextNoteModificationEvent
import com.vitorpamplona.quartz.events.UserMetadata
import com.vitorpamplona.quartz.events.VideoEvent
import com.vitorpamplona.quartz.events.VideoHorizontalEvent
import com.vitorpamplona.quartz.events.VideoVerticalEvent
import com.vitorpamplona.quartz.events.WikiNoteEvent
import com.vitorpamplona.quartz.events.toImmutableListOfLists
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.net.URL
import java.text.DecimalFormat
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun NoteCompose(
    baseNote: Note,
    routeForLastRead: String? = null,
    modifier: Modifier = Modifier,
    isBoostedNote: Boolean = false,
    isQuotedNote: Boolean = false,
    unPackReply: Boolean = true,
    makeItShort: Boolean = false,
    addMarginTop: Boolean = true,
    showHidden: Boolean = false,
    parentBackgroundColor: MutableState<Color>? = null,
    accountViewModel: AccountViewModel,
    nav: (String) -> Unit,
) {
    val hasEvent by baseNote.live().hasEvent.observeAsState(baseNote.event != null)

    Crossfade(targetState = hasEvent, label = "Event presence") {
        if (it) {
            CheckHiddenNoteCompose(
                note = baseNote,
                routeForLastRead = routeForLastRead,
                modifier = modifier,
                isBoostedNote = isBoostedNote,
                isQuotedNote = isQuotedNote,
                unPackReply = unPackReply,
                makeItShort = makeItShort,
                addMarginTop = addMarginTop,
                showHidden = showHidden,
                parentBackgroundColor = parentBackgroundColor,
                accountViewModel = accountViewModel,
                nav = nav,
            )
        } else {
            LongPressToQuickAction(baseNote = baseNote, accountViewModel = accountViewModel) { showPopup ->
                BlankNote(
                    remember {
                        modifier.combinedClickable(
                            onClick = {},
                            onLongClick = showPopup,
                        )
                    },
                    isBoostedNote || isQuotedNote,
                )
            }
        }
    }
}

@Composable
fun CheckHiddenNoteCompose(
    note: Note,
    routeForLastRead: String? = null,
    modifier: Modifier = Modifier,
    isBoostedNote: Boolean = false,
    isQuotedNote: Boolean = false,
    unPackReply: Boolean = true,
    makeItShort: Boolean = false,
    addMarginTop: Boolean = true,
    showHidden: Boolean = false,
    parentBackgroundColor: MutableState<Color>? = null,
    accountViewModel: AccountViewModel,
    nav: (String) -> Unit,
) {
    if (showHidden) {
        // Ignores reports as well
        val state by
            remember(note) {
                mutableStateOf(
                    AccountViewModel.NoteComposeReportState(),
                )
            }

        RenderReportState(
            state = state,
            note = note,
            routeForLastRead = routeForLastRead,
            modifier = modifier,
            isBoostedNote = isBoostedNote,
            isQuotedNote = isQuotedNote,
            unPackReply = unPackReply,
            makeItShort = makeItShort,
            addMarginTop = addMarginTop,
            parentBackgroundColor = parentBackgroundColor,
            accountViewModel = accountViewModel,
            nav = nav,
        )
    } else {
        val isHidden by
            remember(note) {
                accountViewModel.account.liveHiddenUsers
                    .map { note.isHiddenFor(it) }
                    .distinctUntilChanged()
            }
                .observeAsState(accountViewModel.isNoteHidden(note))

        Crossfade(targetState = isHidden, label = "CheckHiddenNoteCompose") {
            if (!it) {
                LoadedNoteCompose(
                    note = note,
                    routeForLastRead = routeForLastRead,
                    modifier = modifier,
                    isBoostedNote = isBoostedNote,
                    isQuotedNote = isQuotedNote,
                    unPackReply = unPackReply,
                    makeItShort = makeItShort,
                    addMarginTop = addMarginTop,
                    parentBackgroundColor = parentBackgroundColor,
                    accountViewModel = accountViewModel,
                    nav = nav,
                )
            }
        }
    }
}

@Composable
fun LoadedNoteCompose(
    note: Note,
    routeForLastRead: String? = null,
    modifier: Modifier = Modifier,
    isBoostedNote: Boolean = false,
    isQuotedNote: Boolean = false,
    unPackReply: Boolean = true,
    makeItShort: Boolean = false,
    addMarginTop: Boolean = true,
    parentBackgroundColor: MutableState<Color>? = null,
    accountViewModel: AccountViewModel,
    nav: (String) -> Unit,
) {
    var state by
        remember(note) {
            mutableStateOf(
                AccountViewModel.NoteComposeReportState(),
            )
        }

    WatchForReports(note, accountViewModel) { newState ->
        if (state != newState) {
            state = newState
        }
    }

    Crossfade(targetState = state, label = "LoadedNoteCompose") {
        RenderReportState(
            it,
            note,
            routeForLastRead,
            modifier,
            isBoostedNote,
            isQuotedNote,
            unPackReply,
            makeItShort,
            addMarginTop,
            parentBackgroundColor,
            accountViewModel,
            nav,
        )
    }
}

@Composable
fun RenderReportState(
    state: AccountViewModel.NoteComposeReportState,
    note: Note,
    routeForLastRead: String? = null,
    modifier: Modifier = Modifier,
    isBoostedNote: Boolean = false,
    isQuotedNote: Boolean = false,
    unPackReply: Boolean = true,
    makeItShort: Boolean = false,
    addMarginTop: Boolean = true,
    parentBackgroundColor: MutableState<Color>? = null,
    accountViewModel: AccountViewModel,
    nav: (String) -> Unit,
) {
    var showReportedNote by remember(note) { mutableStateOf(false) }

    Crossfade(targetState = !state.isAcceptable && !showReportedNote, label = "RenderReportState") {
            showHiddenNote ->
        if (showHiddenNote) {
            HiddenNote(
                state.relevantReports,
                state.isHiddenAuthor,
                accountViewModel,
                modifier,
                isBoostedNote,
                nav,
                onClick = { showReportedNote = true },
            )
        } else {
            val canPreview = (!state.isAcceptable && showReportedNote) || state.canPreview

            NormalNote(
                baseNote = note,
                routeForLastRead = routeForLastRead,
                modifier = modifier,
                isBoostedNote = isBoostedNote,
                isQuotedNote = isQuotedNote,
                unPackReply = unPackReply,
                makeItShort = makeItShort,
                addMarginTop = addMarginTop,
                canPreview = canPreview,
                parentBackgroundColor = parentBackgroundColor,
                accountViewModel = accountViewModel,
                nav = nav,
            )
        }
    }
}

@Composable
fun WatchForReports(
    note: Note,
    accountViewModel: AccountViewModel,
    onChange: (AccountViewModel.NoteComposeReportState) -> Unit,
) {
    val userFollowsState by accountViewModel.userFollows.observeAsState()
    val noteReportsState by note.live().reports.observeAsState()
    val userBlocks by accountViewModel.account.flowHiddenUsers.collectAsStateWithLifecycle()

    LaunchedEffect(key1 = noteReportsState, key2 = userFollowsState, userBlocks) {
        accountViewModel.isNoteAcceptable(note, onChange)
    }
}

@Composable
fun NormalNote(
    baseNote: Note,
    routeForLastRead: String? = null,
    modifier: Modifier = Modifier,
    isBoostedNote: Boolean = false,
    isQuotedNote: Boolean = false,
    unPackReply: Boolean = true,
    makeItShort: Boolean = false,
    addMarginTop: Boolean = true,
    canPreview: Boolean = true,
    parentBackgroundColor: MutableState<Color>? = null,
    accountViewModel: AccountViewModel,
    nav: (String) -> Unit,
) {
    if (isQuotedNote || isBoostedNote) {
        when (baseNote.event) {
            is ChannelCreateEvent,
            is ChannelMetadataEvent,
            ->
                ChannelHeader(
                    channelNote = baseNote,
                    showVideo = !makeItShort,
                    showBottomDiviser = true,
                    sendToChannel = true,
                    accountViewModel = accountViewModel,
                    nav = nav,
                )
            is CommunityDefinitionEvent ->
                (baseNote as? AddressableNote)?.let {
                    CommunityHeader(
                        baseNote = it,
                        showBottomDiviser = true,
                        sendToCommunity = true,
                        accountViewModel = accountViewModel,
                        nav = nav,
                    )
                }
            is BadgeDefinitionEvent -> BadgeDisplay(baseNote = baseNote)
            else ->
                LongPressToQuickAction(baseNote = baseNote, accountViewModel = accountViewModel) {
                        showPopup,
                    ->
                    CheckNewAndRenderNote(
                        baseNote,
                        routeForLastRead,
                        modifier,
                        isBoostedNote,
                        isQuotedNote,
                        unPackReply,
                        makeItShort,
                        addMarginTop,
                        canPreview,
                        parentBackgroundColor,
                        accountViewModel,
                        showPopup,
                        nav,
                    )
                }
        }
    } else {
        when (baseNote.event) {
            is ChannelCreateEvent,
            is ChannelMetadataEvent,
            ->
                ChannelHeader(
                    channelNote = baseNote,
                    showVideo = !makeItShort,
                    showBottomDiviser = true,
                    sendToChannel = true,
                    accountViewModel = accountViewModel,
                    nav = nav,
                )
            is CommunityDefinitionEvent ->
                (baseNote as? AddressableNote)?.let {
                    CommunityHeader(
                        baseNote = it,
                        showBottomDiviser = true,
                        sendToCommunity = true,
                        accountViewModel = accountViewModel,
                        nav = nav,
                    )
                }
            is BadgeDefinitionEvent -> BadgeDisplay(baseNote = baseNote)
            is FileHeaderEvent -> FileHeaderDisplay(baseNote, false, accountViewModel)
            is FileStorageHeaderEvent -> FileStorageHeaderDisplay(baseNote, false, accountViewModel)
            else ->
                LongPressToQuickAction(baseNote = baseNote, accountViewModel = accountViewModel) {
                        showPopup,
                    ->
                    CheckNewAndRenderNote(
                        baseNote = baseNote,
                        routeForLastRead = routeForLastRead,
                        modifier = modifier,
                        isBoostedNote = isBoostedNote,
                        isQuotedNote = isQuotedNote,
                        unPackReply = unPackReply,
                        makeItShort = makeItShort,
                        addMarginTop = addMarginTop,
                        canPreview = canPreview,
                        parentBackgroundColor = parentBackgroundColor,
                        accountViewModel = accountViewModel,
                        showPopup = showPopup,
                        nav = nav,
                    )
                }
        }
    }
}

@Composable
fun CommunityHeader(
    baseNote: AddressableNote,
    showBottomDiviser: Boolean,
    sendToCommunity: Boolean,
    modifier: Modifier = StdPadding,
    accountViewModel: AccountViewModel,
    nav: (String) -> Unit,
) {
    val expanded = remember { mutableStateOf(false) }

    Column(Modifier.fillMaxWidth()) {
        Column(
            verticalArrangement = Arrangement.Center,
            modifier =
                Modifier.clickable {
                    if (sendToCommunity) {
                        routeFor(baseNote, accountViewModel.userProfile())?.let { nav(it) }
                    } else {
                        expanded.value = !expanded.value
                    }
                },
        ) {
            ShortCommunityHeader(
                baseNote = baseNote,
                accountViewModel = accountViewModel,
                nav = nav,
            )

            if (expanded.value) {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    LongCommunityHeader(
                        baseNote = baseNote,
                        lineModifier = modifier,
                        accountViewModel = accountViewModel,
                        nav = nav,
                    )
                }
            }
        }

        if (showBottomDiviser) {
            Divider(
                thickness = DividerThickness,
            )
        }
    }
}

@Composable
fun LongCommunityHeader(
    baseNote: AddressableNote,
    lineModifier: Modifier = Modifier.padding(horizontal = Size10dp, vertical = Size5dp),
    accountViewModel: AccountViewModel,
    nav: (String) -> Unit,
) {
    val noteState by baseNote.live().metadata.observeAsState()
    val noteEvent =
        remember(noteState) { noteState?.note?.event as? CommunityDefinitionEvent } ?: return

    Row(
        lineModifier,
    ) {
        val rulesLabel = stringResource(id = R.string.rules)
        val summary =
            remember(noteState) {
                val subject = noteEvent.subject()?.ifEmpty { null }
                val body = noteEvent.description()?.ifBlank { null }
                val rules = noteEvent.rules()?.ifBlank { null }

                if (!subject.isNullOrBlank() && body?.split("\n")?.get(0)?.contains(subject) == false) {
                    if (rules == null) {
                        "### $subject\n$body"
                    } else {
                        "### $subject\n$body\n\n### $rulesLabel\n\n$rules"
                    }
                } else {
                    if (rules == null) {
                        body
                    } else {
                        "$body\n\n$rulesLabel\n$rules"
                    }
                }
            }

        Column(
            Modifier.weight(1f),
        ) {
            Row(verticalAlignment = CenterVertically) {
                val defaultBackground = MaterialTheme.colorScheme.background
                val background = remember { mutableStateOf(defaultBackground) }

                TranslatableRichTextViewer(
                    content = summary ?: stringResource(id = R.string.community_no_descriptor),
                    canPreview = false,
                    tags = EmptyTagList,
                    backgroundColor = background,
                    accountViewModel = accountViewModel,
                    nav = nav,
                )
            }

            if (summary != null && noteEvent.hasHashtags()) {
                DisplayUncitedHashtags(
                    remember(noteEvent) { noteEvent.hashtags().toImmutableList() },
                    summary ?: "",
                    nav,
                )
            }
        }

        Column {
            Row {
                Spacer(DoubleHorzSpacer)
                LongCommunityActionOptions(baseNote, accountViewModel, nav)
            }
        }
    }

    Row(
        lineModifier,
        verticalAlignment = CenterVertically,
    ) {
        Text(
            text = stringResource(id = R.string.owner),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.width(75.dp),
        )
        Spacer(DoubleHorzSpacer)
        NoteAuthorPicture(baseNote, nav, accountViewModel, Size25dp)
        Spacer(DoubleHorzSpacer)
        NoteUsernameDisplay(baseNote, remember { Modifier.weight(1f) })
    }

    var participantUsers by
        remember(baseNote) {
            mutableStateOf<ImmutableList<Pair<Participant, User>>>(
                persistentListOf(),
            )
        }

    LaunchedEffect(key1 = noteState) {
        val participants = (noteState?.note?.event as? CommunityDefinitionEvent)?.moderators()

        if (participants != null) {
            accountViewModel.loadParticipants(participants) { newParticipantUsers ->
                if (
                    newParticipantUsers != null && !equalImmutableLists(newParticipantUsers, participantUsers)
                ) {
                    participantUsers = newParticipantUsers
                }
            }
        }
    }

    participantUsers.forEach {
        Row(
            lineModifier.clickable { nav("User/${it.second.pubkeyHex}") },
            verticalAlignment = CenterVertically,
        ) {
            it.first.role?.let { it1 ->
                Text(
                    text = it1.capitalize(Locale.ROOT),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.width(75.dp),
                )
            }
            Spacer(DoubleHorzSpacer)
            ClickableUserPicture(it.second, Size25dp, accountViewModel)
            Spacer(DoubleHorzSpacer)
            UsernameDisplay(it.second, remember { Modifier.weight(1f) })
        }
    }

    Row(
        lineModifier,
        verticalAlignment = CenterVertically,
    ) {
        Text(
            text = stringResource(id = R.string.created_at),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.width(75.dp),
        )
        Spacer(DoubleHorzSpacer)
        NormalTimeAgo(baseNote = baseNote, Modifier.weight(1f))
        MoreOptionsButton(baseNote, null, accountViewModel, nav)
    }
}

@Composable
fun ShortCommunityHeader(
    baseNote: AddressableNote,
    accountViewModel: AccountViewModel,
    nav: (String) -> Unit,
) {
    val noteState by baseNote.live().metadata.observeAsState()
    val noteEvent =
        remember(noteState) { noteState?.note?.event as? CommunityDefinitionEvent } ?: return

    val automaticallyShowProfilePicture =
        remember {
            accountViewModel.settings.showProfilePictures.value
        }

    Row(verticalAlignment = CenterVertically) {
        noteEvent.image()?.let {
            RobohashFallbackAsyncImage(
                robot = baseNote.idHex,
                model = it,
                contentDescription = stringResource(R.string.profile_image),
                contentScale = ContentScale.Crop,
                modifier = HeaderPictureModifier,
                loadProfilePicture = automaticallyShowProfilePicture,
            )
        }

        Column(
            modifier =
                Modifier
                    .padding(start = 10.dp)
                    .height(Size35dp)
                    .weight(1f),
            verticalArrangement = Arrangement.Center,
        ) {
            Row(verticalAlignment = CenterVertically) {
                Text(
                    text = remember(noteState) { noteEvent.dTag() },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        Row(
            modifier =
                Modifier
                    .height(Size35dp)
                    .padding(start = 5.dp),
            verticalAlignment = CenterVertically,
        ) {
            ShortCommunityActionOptions(baseNote, accountViewModel, nav)
        }
    }
}

@Composable
private fun ShortCommunityActionOptions(
    note: AddressableNote,
    accountViewModel: AccountViewModel,
    nav: (String) -> Unit,
) {
    Spacer(modifier = StdHorzSpacer)
    LikeReaction(
        baseNote = note,
        grayTint = MaterialTheme.colorScheme.onSurface,
        accountViewModel = accountViewModel,
        nav = nav,
    )
    Spacer(modifier = StdHorzSpacer)
    ZapReaction(
        baseNote = note,
        grayTint = MaterialTheme.colorScheme.onSurface,
        accountViewModel = accountViewModel,
        nav = nav,
    )

    WatchAddressableNoteFollows(note, accountViewModel) { isFollowing ->
        if (!isFollowing) {
            Spacer(modifier = StdHorzSpacer)
            JoinCommunityButton(accountViewModel, note, nav)
        }
    }
}

@Composable
fun WatchAddressableNoteFollows(
    note: AddressableNote,
    accountViewModel: AccountViewModel,
    onFollowChanges: @Composable (Boolean) -> Unit,
) {
    val showFollowingMark by
        remember {
            accountViewModel.userFollows
                .map { it.user.latestContactList?.isTaggedAddressableNote(note.idHex) ?: false }
                .distinctUntilChanged()
        }
            .observeAsState(false)

    onFollowChanges(showFollowingMark)
}

@Composable
private fun LongCommunityActionOptions(
    note: AddressableNote,
    accountViewModel: AccountViewModel,
    nav: (String) -> Unit,
) {
    WatchAddressableNoteFollows(note, accountViewModel) { isFollowing ->
        if (isFollowing) {
            LeaveCommunityButton(accountViewModel, note, nav)
        }
    }
}

@Composable
private fun CheckNewAndRenderNote(
    baseNote: Note,
    routeForLastRead: String? = null,
    modifier: Modifier = Modifier,
    isBoostedNote: Boolean = false,
    isQuotedNote: Boolean = false,
    unPackReply: Boolean = true,
    makeItShort: Boolean = false,
    addMarginTop: Boolean = true,
    canPreview: Boolean = true,
    parentBackgroundColor: MutableState<Color>? = null,
    accountViewModel: AccountViewModel,
    showPopup: () -> Unit,
    nav: (String) -> Unit,
) {
    val newItemColor = MaterialTheme.colorScheme.newItemBackgroundColor
    val defaultBackgroundColor = MaterialTheme.colorScheme.background
    val backgroundColor =
        remember(baseNote) {
            mutableStateOf<Color>(parentBackgroundColor?.value ?: defaultBackgroundColor)
        }

    LaunchedEffect(key1 = routeForLastRead, key2 = parentBackgroundColor?.value) {
        routeForLastRead?.let {
            accountViewModel.loadAndMarkAsRead(it, baseNote.createdAt()) { isNew ->
                val newBackgroundColor =
                    if (isNew) {
                        if (parentBackgroundColor != null) {
                            newItemColor.compositeOver(parentBackgroundColor.value)
                        } else {
                            newItemColor.compositeOver(defaultBackgroundColor)
                        }
                    } else {
                        parentBackgroundColor?.value ?: defaultBackgroundColor
                    }

                if (newBackgroundColor != backgroundColor.value) {
                    launch(Dispatchers.Main) { backgroundColor.value = newBackgroundColor }
                }
            }
        }
            ?: run {
                val newBackgroundColor = parentBackgroundColor?.value ?: defaultBackgroundColor

                if (newBackgroundColor != backgroundColor.value) {
                    launch(Dispatchers.Main) { backgroundColor.value = newBackgroundColor }
                }
            }
    }

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
            addMarginTop = addMarginTop,
            unPackReply = unPackReply,
            makeItShort = makeItShort,
            canPreview = canPreview,
            accountViewModel = accountViewModel,
            nav = nav,
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
    nav: (String) -> Unit,
    content: @Composable () -> Unit,
) {
    val scope = rememberCoroutineScope()

    val updatedModifier =
        remember(baseNote, backgroundColor.value) {
            modifier
                .combinedClickable(
                    onClick = {
                        scope.launch {
                            val redirectToNote =
                                if (baseNote.event is RepostEvent || baseNote.event is GenericRepostEvent) {
                                    baseNote.replyTo?.lastOrNull() ?: baseNote
                                } else {
                                    baseNote
                                }
                            routeFor(redirectToNote, accountViewModel.userProfile())?.let { nav(it) }
                        }
                    },
                    onLongClick = showPopup,
                )
                .background(backgroundColor.value)
        }

    Column(modifier = updatedModifier) { content() }
}

@Composable
fun InnerNoteWithReactions(
    baseNote: Note,
    backgroundColor: MutableState<Color>,
    isBoostedNote: Boolean,
    isQuotedNote: Boolean,
    addMarginTop: Boolean,
    unPackReply: Boolean,
    makeItShort: Boolean,
    canPreview: Boolean,
    accountViewModel: AccountViewModel,
    nav: (String) -> Unit,
) {
    val notBoostedNorQuote = !isBoostedNote && !isQuotedNote
    val editState = observeEdits(baseNote = baseNote, accountViewModel = accountViewModel)

    Row(
        modifier =
            if (!isBoostedNote && addMarginTop) {
                normalWithTopMarginNoteModifier
            } else if (!isBoostedNote) {
                normalNoteModifier
            } else {
                boostedNoteModifier
            },
    ) {
        if (notBoostedNorQuote) {
            Column(WidthAuthorPictureModifier) {
                AuthorAndRelayInformation(baseNote, accountViewModel, nav)
            }
            Spacer(modifier = DoubleHorzSpacer)
        }

        Column(Modifier.fillMaxWidth()) {
            val showSecondRow =
                baseNote.event !is RepostEvent &&
                    baseNote.event !is GenericRepostEvent &&
                    !isBoostedNote &&
                    !isQuotedNote
            NoteBody(
                baseNote = baseNote,
                showAuthorPicture = isQuotedNote,
                unPackReply = unPackReply,
                makeItShort = makeItShort,
                canPreview = canPreview,
                showSecondRow = showSecondRow,
                backgroundColor = backgroundColor,
                editState = editState,
                accountViewModel = accountViewModel,
                nav = nav,
            )
        }
    }

    val isNotRepost = baseNote.event !is RepostEvent && baseNote.event !is GenericRepostEvent

    if (isNotRepost) {
        if (makeItShort) {
            if (isBoostedNote) {
            } else {
                Spacer(modifier = DoubleVertSpacer)
            }
        } else {
            ReactionsRow(
                baseNote = baseNote,
                showReactionDetail = notBoostedNorQuote,
                editState = editState,
                accountViewModel = accountViewModel,
                nav = nav,
            )
        }
    }

    if (notBoostedNorQuote) {
        Divider(
            thickness = DividerThickness,
        )
    }
}

@Stable
class EditState() {
    private var modificationsList: List<Note> = persistentListOf()
    private var modificationToShowIndex: Int = -1

    val modificationToShow: MutableState<Note?> = mutableStateOf(null)
    val showingVersion: MutableState<Int> = mutableStateOf(0)

    fun hasModificationsToShow(): Boolean = modificationsList.isNotEmpty()

    fun isOriginal(): Boolean = modificationToShowIndex < 0

    fun isLatest(): Boolean = modificationToShowIndex == modificationsList.lastIndex

    fun originalVersionId() = 0

    fun lastVersionId() = modificationsList.size

    fun versionId() = modificationToShowIndex + 1

    fun latest() = modificationsList.lastOrNull()

    fun nextModification() {
        if (modificationToShowIndex < 0) {
            modificationToShowIndex = 0
            modificationToShow.value = modificationsList.getOrNull(0)
        } else {
            modificationToShowIndex++
            if (modificationToShowIndex >= modificationsList.size) {
                modificationToShowIndex = -1
                modificationToShow.value = null
            } else {
                modificationToShow.value = modificationsList.getOrNull(modificationToShowIndex)
            }
        }

        showingVersion.value = versionId()
    }

    fun updateModifications(newModifications: List<Note>) {
        if (modificationsList != newModifications) {
            modificationsList = newModifications

            if (newModifications.isEmpty()) {
                modificationToShow.value = null
                modificationToShowIndex = -1
            } else {
                modificationToShowIndex = newModifications.lastIndex
                modificationToShow.value = newModifications.last()
            }
        }

        showingVersion.value = versionId()
    }
}

@Composable
private fun NoteBody(
    baseNote: Note,
    showAuthorPicture: Boolean = false,
    unPackReply: Boolean = true,
    makeItShort: Boolean = false,
    canPreview: Boolean = true,
    showSecondRow: Boolean,
    backgroundColor: MutableState<Color>,
    editState: State<GenericLoadable<EditState>>,
    accountViewModel: AccountViewModel,
    nav: (String) -> Unit,
) {
    FirstUserInfoRow(
        baseNote = baseNote,
        showAuthorPicture = showAuthorPicture,
        editState = editState,
        accountViewModel = accountViewModel,
        nav = nav,
    )

    if (showSecondRow) {
        SecondUserInfoRow(
            baseNote,
            accountViewModel,
            nav,
        )
    }

    if (baseNote.event !is RepostEvent && baseNote.event !is GenericRepostEvent) {
        Spacer(modifier = Modifier.height(3.dp))
    }

    if (!makeItShort) {
        ReplyRow(
            baseNote,
            unPackReply,
            backgroundColor,
            accountViewModel,
            nav,
        )
    }

    RenderNoteRow(
        baseNote = baseNote,
        backgroundColor = backgroundColor,
        makeItShort = makeItShort,
        canPreview = canPreview,
        editState = editState,
        accountViewModel = accountViewModel,
        nav = nav,
    )

    val noteEvent = baseNote.event
    val zapSplits = remember(noteEvent) { noteEvent?.hasZapSplitSetup() ?: false }
    if (zapSplits && noteEvent != null) {
        Spacer(modifier = HalfDoubleVertSpacer)
        DisplayZapSplits(noteEvent, false, accountViewModel, nav)
    }
}

@Composable
private fun RenderNoteRow(
    baseNote: Note,
    backgroundColor: MutableState<Color>,
    makeItShort: Boolean,
    canPreview: Boolean,
    editState: State<GenericLoadable<EditState>>,
    accountViewModel: AccountViewModel,
    nav: (String) -> Unit,
) {
    val noteEvent = baseNote.event
    when (noteEvent) {
        is AppDefinitionEvent -> {
            RenderAppDefinition(baseNote, accountViewModel, nav)
        }
        is AudioTrackEvent -> {
            RenderAudioTrack(baseNote, accountViewModel, nav)
        }
        is AudioHeaderEvent -> {
            RenderAudioHeader(baseNote, accountViewModel, nav)
        }
        is ReactionEvent -> {
            RenderReaction(baseNote, backgroundColor, accountViewModel, nav)
        }
        is RepostEvent -> {
            RenderRepost(baseNote, backgroundColor, accountViewModel, nav)
        }
        is GenericRepostEvent -> {
            RenderRepost(baseNote, backgroundColor, accountViewModel, nav)
        }
        is ReportEvent -> {
            RenderReport(baseNote, backgroundColor, accountViewModel, nav)
        }
        is LongTextNoteEvent -> {
            RenderLongFormContent(baseNote, accountViewModel, nav)
        }
        is WikiNoteEvent -> {
            RenderWikiContent(baseNote, accountViewModel, nav)
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
        is RelaySetEvent -> {
            DisplayRelaySet(baseNote, backgroundColor, accountViewModel, nav)
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
                backgroundColor,
                accountViewModel,
                nav,
            )
        }
        is PollNoteEvent -> {
            RenderPoll(
                baseNote,
                makeItShort,
                canPreview,
                backgroundColor,
                accountViewModel,
                nav,
            )
        }
        is FileHeaderEvent -> {
            FileHeaderDisplay(baseNote, true, accountViewModel)
        }
        is VideoHorizontalEvent -> {
            VideoDisplay(baseNote, makeItShort, canPreview, backgroundColor, accountViewModel, nav)
        }
        is VideoVerticalEvent -> {
            VideoDisplay(baseNote, makeItShort, canPreview, backgroundColor, accountViewModel, nav)
        }
        is FileStorageHeaderEvent -> {
            FileStorageHeaderDisplay(baseNote, true, accountViewModel)
        }
        is CommunityPostApprovalEvent -> {
            RenderPostApproval(
                baseNote,
                makeItShort,
                canPreview,
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
                backgroundColor,
                editState,
                accountViewModel,
                nav,
            )
        }
        else -> {
            RenderTextEvent(
                baseNote,
                makeItShort,
                canPreview,
                backgroundColor,
                editState,
                accountViewModel,
                nav,
            )
        }
    }
}

@Composable
fun LoadDecryptedContent(
    note: Note,
    accountViewModel: AccountViewModel,
    inner: @Composable (String) -> Unit,
) {
    var decryptedContent by
        remember(note.event) {
            mutableStateOf(
                accountViewModel.cachedDecrypt(note),
            )
        }

    decryptedContent?.let { inner(it) }
        ?: run {
            LaunchedEffect(key1 = decryptedContent) {
                accountViewModel.decrypt(note) { decryptedContent = it }
            }
        }
}

@Composable
fun LoadDecryptedContentOrNull(
    note: Note,
    accountViewModel: AccountViewModel,
    inner: @Composable (String?) -> Unit,
) {
    var decryptedContent by
        remember(note.event) {
            mutableStateOf(
                accountViewModel.cachedDecrypt(note),
            )
        }

    if (decryptedContent == null) {
        LaunchedEffect(key1 = decryptedContent) {
            accountViewModel.decrypt(note) { decryptedContent = it }
        }
    }

    inner(decryptedContent)
}

@Composable
fun RenderTextEvent(
    note: Note,
    makeItShort: Boolean,
    canPreview: Boolean,
    backgroundColor: MutableState<Color>,
    editState: State<GenericLoadable<EditState>>,
    accountViewModel: AccountViewModel,
    nav: (String) -> Unit,
) {
    LoadDecryptedContent(
        note,
        accountViewModel,
    ) { body ->
        val eventContent by
            remember(note.event) {
                derivedStateOf {
                    val subject = (note.event as? TextNoteEvent)?.subject()?.ifEmpty { null }
                    val newBody =
                        if (editState.value is GenericLoadable.Loaded) {
                            val state = (editState.value as? GenericLoadable.Loaded)?.loaded?.modificationToShow
                            state?.value?.event?.content() ?: body
                        } else {
                            body
                        }

                    if (!subject.isNullOrBlank() && !newBody.split("\n")[0].contains(subject)) {
                        "### $subject\n$newBody"
                    } else {
                        newBody
                    }
                }
            }

        val isAuthorTheLoggedUser = remember(note.event) { accountViewModel.isLoggedUser(note.author) }

        if (makeItShort && isAuthorTheLoggedUser) {
            Text(
                text = eventContent,
                color = MaterialTheme.colorScheme.placeholderText,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        } else {
            SensitivityWarning(
                note = note,
                accountViewModel = accountViewModel,
            ) {
                val modifier = remember(note) { Modifier.fillMaxWidth() }
                val tags = remember(note) { note.event?.tags()?.toImmutableListOfLists() ?: EmptyTagList }

                TranslatableRichTextViewer(
                    content = eventContent,
                    canPreview = canPreview && !makeItShort,
                    modifier = modifier,
                    tags = tags,
                    backgroundColor = backgroundColor,
                    accountViewModel = accountViewModel,
                    nav = nav,
                )
            }

            if (note.event?.hasHashtags() == true) {
                val hashtags =
                    remember(note.event) { note.event?.hashtags()?.toImmutableList() ?: persistentListOf() }
                DisplayUncitedHashtags(hashtags, eventContent, nav)
            }
        }
    }
}

@Composable
fun RenderTextModificationEvent(
    note: Note,
    makeItShort: Boolean,
    canPreview: Boolean,
    backgroundColor: MutableState<Color>,
    editStateByAuthor: State<GenericLoadable<EditState>>,
    accountViewModel: AccountViewModel,
    nav: (String) -> Unit,
) {
    val noteEvent = note.event as? TextNoteModificationEvent ?: return
    val noteAuthor = note.author ?: return

    val isAuthorTheLoggedUser = remember(note.event) { accountViewModel.isLoggedUser(note.author) }

    val editState =
        remember {
            derivedStateOf {
                val loadable = editStateByAuthor.value as? GenericLoadable.Loaded<EditState>

                val state = EditState()

                val latestChangeByAuthor =
                    if (loadable != null && loadable.loaded.hasModificationsToShow()) {
                        loadable.loaded.latest()
                    } else {
                        null
                    }

                state.updateModifications(listOfNotNull(latestChangeByAuthor, note))

                GenericLoadable.Loaded(state)
            }
        }

    val wantsToEditPost =
        remember {
            mutableStateOf(false)
        }

    Card(
        modifier = MaterialTheme.colorScheme.imageModifier,
    ) {
        Column(Modifier.fillMaxWidth().padding(Size10dp)) {
            Text(
                text = stringResource(id = R.string.proposal_to_edit),
                style =
                    TextStyle(
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                    ),
            )

            Spacer(modifier = StdVertSpacer)

            noteEvent.summary()?.let {
                TranslatableRichTextViewer(
                    content = it,
                    canPreview = canPreview && !makeItShort,
                    modifier = Modifier.fillMaxWidth(),
                    tags = EmptyTagList,
                    backgroundColor = backgroundColor,
                    accountViewModel = accountViewModel,
                    nav = nav,
                )
                Spacer(modifier = StdVertSpacer)
            }

            noteEvent.editedNote()?.let {
                LoadNote(baseNoteHex = it, accountViewModel = accountViewModel) { baseNote ->
                    baseNote?.let {
                        Column(
                            modifier =
                                MaterialTheme.colorScheme.innerPostModifier.padding(Size10dp).clickable {
                                    routeFor(baseNote, accountViewModel.userProfile())?.let { nav(it) }
                                },
                        ) {
                            NoteBody(
                                baseNote = baseNote,
                                showAuthorPicture = true,
                                unPackReply = false,
                                makeItShort = false,
                                canPreview = true,
                                showSecondRow = false,
                                backgroundColor = backgroundColor,
                                editState = editState,
                                accountViewModel = accountViewModel,
                                nav = nav,
                            )

                            if (wantsToEditPost.value) {
                                EditPostView(
                                    onClose = {
                                        wantsToEditPost.value = false
                                    },
                                    edit = baseNote,
                                    versionLookingAt = note,
                                    accountViewModel = accountViewModel,
                                    nav = nav,
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = StdVertSpacer)

            Button(
                onClick = { wantsToEditPost.value = true },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(text = stringResource(id = R.string.accept_the_suggestion))
            }
        }
    }
}

@Composable
fun RenderPoll(
    note: Note,
    makeItShort: Boolean,
    canPreview: Boolean,
    backgroundColor: MutableState<Color>,
    accountViewModel: AccountViewModel,
    nav: (String) -> Unit,
) {
    val noteEvent = note.event as? PollNoteEvent ?: return
    val eventContent = noteEvent.content()

    if (makeItShort && accountViewModel.isLoggedUser(note.author)) {
        Text(
            text = eventContent,
            color = MaterialTheme.colorScheme.placeholderText,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    } else {
        val tags = remember(note) { note.event?.tags()?.toImmutableListOfLists() ?: EmptyTagList }

        SensitivityWarning(
            note = note,
            accountViewModel = accountViewModel,
        ) {
            TranslatableRichTextViewer(
                content = eventContent,
                canPreview = canPreview && !makeItShort,
                modifier = remember { Modifier.fillMaxWidth() },
                tags = tags,
                backgroundColor = backgroundColor,
                accountViewModel = accountViewModel,
                nav = nav,
            )

            PollNote(
                note,
                canPreview = canPreview && !makeItShort,
                backgroundColor,
                accountViewModel,
                nav,
            )
        }

        if (noteEvent.hasHashtags()) {
            val hashtags = remember { noteEvent.hashtags().toImmutableList() }
            DisplayUncitedHashtags(hashtags, eventContent, nav)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun RenderAppDefinition(
    note: Note,
    accountViewModel: AccountViewModel,
    nav: (String) -> Unit,
) {
    val noteEvent = note.event as? AppDefinitionEvent ?: return

    var metadata by remember { mutableStateOf<UserMetadata?>(null) }

    LaunchedEffect(key1 = noteEvent) {
        launch(Dispatchers.Default) { metadata = noteEvent.appMetaData() }
    }

    metadata?.let {
        Box {
            val clipboardManager = LocalClipboardManager.current
            val uri = LocalUriHandler.current

            if (!it.banner.isNullOrBlank()) {
                var zoomImageDialogOpen by remember { mutableStateOf(false) }

                AsyncImage(
                    model = it.banner,
                    contentDescription = stringResource(id = R.string.profile_image),
                    contentScale = ContentScale.FillWidth,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(125.dp)
                            .combinedClickable(
                                onClick = {},
                                onLongClick = { clipboardManager.setText(AnnotatedString(it.banner!!)) },
                            ),
                )

                if (zoomImageDialogOpen) {
                    ZoomableImageDialog(
                        imageUrl = RichTextParser.parseImageOrVideo(it.banner!!),
                        onDismiss = { zoomImageDialogOpen = false },
                        accountViewModel = accountViewModel,
                    )
                }
            } else {
                Image(
                    painter = painterResource(R.drawable.profile_banner),
                    contentDescription = stringResource(id = R.string.profile_banner),
                    contentScale = ContentScale.FillWidth,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(125.dp),
                )
            }

            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp)
                        .padding(top = 75.dp),
            ) {
                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Bottom,
                ) {
                    var zoomImageDialogOpen by remember { mutableStateOf(false) }

                    Box(Modifier.size(100.dp)) {
                        it.picture?.let {
                            AsyncImage(
                                model = it,
                                contentDescription = null,
                                contentScale = ContentScale.FillWidth,
                                modifier =
                                    Modifier
                                        .border(
                                            3.dp,
                                            MaterialTheme.colorScheme.background,
                                            CircleShape,
                                        )
                                        .clip(shape = CircleShape)
                                        .fillMaxSize()
                                        .background(MaterialTheme.colorScheme.background)
                                        .combinedClickable(
                                            onClick = { zoomImageDialogOpen = true },
                                            onLongClick = { clipboardManager.setText(AnnotatedString(it)) },
                                        ),
                            )
                        }
                    }

                    if (zoomImageDialogOpen) {
                        ZoomableImageDialog(
                            imageUrl = RichTextParser.parseImageOrVideo(it.banner!!),
                            onDismiss = { zoomImageDialogOpen = false },
                            accountViewModel = accountViewModel,
                        )
                    }

                    Spacer(Modifier.weight(1f))

                    Row(
                        modifier =
                            Modifier
                                .height(Size35dp)
                                .padding(bottom = 3.dp),
                    ) {}
                }

                val name = remember(it) { it.anyName() }
                name?.let {
                    Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.padding(top = 7.dp)) {
                        CreateTextWithEmoji(
                            text = it,
                            tags = remember { (note.event?.tags() ?: emptyArray()).toImmutableListOfLists() },
                            fontWeight = FontWeight.Bold,
                            fontSize = 25.sp,
                        )
                    }
                }

                val website = remember(it) { it.website }
                if (!website.isNullOrEmpty()) {
                    Row(verticalAlignment = CenterVertically) {
                        LinkIcon(Size16Modifier, MaterialTheme.colorScheme.placeholderText)

                        ClickableText(
                            text = AnnotatedString(website.removePrefix("https://")),
                            onClick = { website.let { runCatching { uri.openUri(it) } } },
                            style = LocalTextStyle.current.copy(color = MaterialTheme.colorScheme.primary),
                            modifier = Modifier.padding(top = 1.dp, bottom = 1.dp, start = 5.dp),
                        )
                    }
                }

                it.about?.let {
                    Row(
                        modifier = Modifier.padding(top = 5.dp, bottom = 5.dp),
                    ) {
                        val tags =
                            remember(note) { note.event?.tags()?.toImmutableListOfLists() ?: EmptyTagList }
                        val bgColor = MaterialTheme.colorScheme.background
                        val backgroundColor = remember { mutableStateOf(bgColor) }
                        TranslatableRichTextViewer(
                            content = it,
                            canPreview = false,
                            tags = tags,
                            backgroundColor = backgroundColor,
                            accountViewModel = accountViewModel,
                            nav = nav,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RenderHighlight(
    note: Note,
    makeItShort: Boolean,
    canPreview: Boolean,
    backgroundColor: MutableState<Color>,
    accountViewModel: AccountViewModel,
    nav: (String) -> Unit,
) {
    val quote = remember { (note.event as? HighlightEvent)?.quote() ?: "" }
    val author = remember { (note.event as? HighlightEvent)?.author() }
    val url = remember { (note.event as? HighlightEvent)?.inUrl() }
    val postHex = remember { (note.event as? HighlightEvent)?.taggedAddresses()?.firstOrNull() }

    DisplayHighlight(
        highlight = quote,
        authorHex = author,
        url = url,
        postAddress = postHex,
        makeItShort = makeItShort,
        canPreview = canPreview,
        backgroundColor = backgroundColor,
        accountViewModel = accountViewModel,
        nav = nav,
    )
}

@Composable
private fun RenderPrivateMessage(
    note: Note,
    makeItShort: Boolean,
    canPreview: Boolean,
    backgroundColor: MutableState<Color>,
    accountViewModel: AccountViewModel,
    nav: (String) -> Unit,
) {
    val noteEvent = note.event as? PrivateDmEvent ?: return

    val withMe = remember { noteEvent.with(accountViewModel.userProfile().pubkeyHex) }
    if (withMe) {
        LoadDecryptedContent(note, accountViewModel) { eventContent ->
            val modifier = remember(note.event?.id()) { Modifier.fillMaxWidth() }
            val isAuthorTheLoggedUser =
                remember(note.event?.id()) { accountViewModel.isLoggedUser(note.author) }

            val tags = remember(note) { note.event?.tags()?.toImmutableListOfLists() ?: EmptyTagList }

            if (makeItShort && isAuthorTheLoggedUser) {
                Text(
                    text = eventContent,
                    color = MaterialTheme.colorScheme.placeholderText,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            } else {
                SensitivityWarning(
                    note = note,
                    accountViewModel = accountViewModel,
                ) {
                    TranslatableRichTextViewer(
                        content = eventContent,
                        canPreview = canPreview && !makeItShort,
                        modifier = modifier,
                        tags = tags,
                        backgroundColor = backgroundColor,
                        accountViewModel = accountViewModel,
                        nav = nav,
                    )
                }

                if (noteEvent.hasHashtags()) {
                    val hashtags =
                        remember(note.event?.id()) {
                            note.event?.hashtags()?.toImmutableList() ?: persistentListOf()
                        }
                    DisplayUncitedHashtags(hashtags, eventContent, nav)
                }
            }
        }
    } else {
        val recipient = noteEvent.recipientPubKeyBytes()?.toNpub() ?: "Someone"

        TranslatableRichTextViewer(
            stringResource(
                id = R.string.private_conversation_notification,
                "@${note.author?.pubkeyNpub()}",
                "@$recipient",
            ),
            canPreview = !makeItShort,
            Modifier.fillMaxWidth(),
            EmptyTagList,
            backgroundColor,
            accountViewModel,
            nav,
        )
    }
}

@Composable
fun DisplayRelaySet(
    baseNote: Note,
    backgroundColor: MutableState<Color>,
    accountViewModel: AccountViewModel,
    nav: (String) -> Unit,
) {
    val noteEvent = baseNote.event as? RelaySetEvent ?: return

    val relays by
        remember(baseNote) {
            mutableStateOf(
                noteEvent.relays().map { RelayBriefInfoCache.RelayBriefInfo(it) }.toImmutableList(),
            )
        }

    var expanded by remember { mutableStateOf(false) }

    val toMembersShow =
        if (expanded) {
            relays
        } else {
            relays.take(3)
        }

    val relayListName by remember { derivedStateOf { "#${noteEvent.dTag()}" } }

    val relayDescription by remember { derivedStateOf { noteEvent.description() } }

    Text(
        text = relayListName,
        fontWeight = FontWeight.Bold,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(5.dp),
        textAlign = TextAlign.Center,
    )

    relayDescription?.let {
        Text(
            text = it,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(5.dp),
            textAlign = TextAlign.Center,
            color = Color.Gray,
        )
    }

    Box {
        Column(modifier = Modifier.padding(top = 5.dp)) {
            toMembersShow.forEach { relay ->
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = CenterVertically) {
                    Text(
                        text = relay.displayUrl,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier =
                            Modifier
                                .padding(start = 10.dp, bottom = 5.dp)
                                .weight(1f),
                    )

                    Column(modifier = Modifier.padding(start = 10.dp)) {
                        RelayOptionsAction(relay.url, accountViewModel, nav)
                    }
                }
            }
        }

        if (relays.size > 3 && !expanded) {
            Row(
                verticalAlignment = CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier =
                    Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .background(getGradient(backgroundColor)),
            ) {
                ShowMoreButton { expanded = !expanded }
            }
        }
    }
}

@Composable
private fun RelayOptionsAction(
    relay: String,
    accountViewModel: AccountViewModel,
    nav: (String) -> Unit,
) {
    val userStateRelayInfo by accountViewModel.account.userProfile().live().relayInfo.observeAsState()
    val isCurrentlyOnTheUsersList by
        remember(userStateRelayInfo) {
            derivedStateOf {
                userStateRelayInfo?.user?.latestContactList?.relays()?.none { it.key == relay } == true
            }
        }

    var wantsToAddRelay by remember { mutableStateOf("") }

    if (wantsToAddRelay.isNotEmpty()) {
        NewRelayListView({ wantsToAddRelay = "" }, accountViewModel, wantsToAddRelay, nav = nav)
    }

    if (isCurrentlyOnTheUsersList) {
        AddRelayButton { wantsToAddRelay = relay }
    } else {
        RemoveRelayButton { wantsToAddRelay = relay }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DisplayPeopleList(
    baseNote: Note,
    backgroundColor: MutableState<Color>,
    accountViewModel: AccountViewModel,
    nav: (String) -> Unit,
) {
    val noteEvent = baseNote.event as? PeopleListEvent ?: return

    var members by remember { mutableStateOf<ImmutableList<User>>(persistentListOf()) }

    var expanded by remember { mutableStateOf(false) }

    val toMembersShow =
        if (expanded) {
            members
        } else {
            members.take(3)
        }

    val name by remember { derivedStateOf { "#${noteEvent.dTag()}" } }

    Text(
        text = name,
        fontWeight = FontWeight.Bold,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(5.dp),
        textAlign = TextAlign.Center,
    )

    LaunchedEffect(Unit) { accountViewModel.loadUsers(noteEvent.bookmarkedPeople()) { members = it } }

    Box {
        FlowRow(modifier = Modifier.padding(top = 5.dp)) {
            toMembersShow.forEach { user ->
                Row(modifier = Modifier.fillMaxWidth()) {
                    UserCompose(
                        user,
                        overallModifier = Modifier,
                        accountViewModel = accountViewModel,
                        nav = nav,
                    )
                }
            }
        }

        if (members.size > 3 && !expanded) {
            Row(
                verticalAlignment = CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier =
                    Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .background(getGradient(backgroundColor)),
            ) {
                ShowMoreButton { expanded = !expanded }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RenderBadgeAward(
    note: Note,
    backgroundColor: MutableState<Color>,
    accountViewModel: AccountViewModel,
    nav: (String) -> Unit,
) {
    if (note.replyTo.isNullOrEmpty()) return

    val noteEvent = note.event as? BadgeAwardEvent ?: return
    var awardees by remember { mutableStateOf<List<User>>(listOf()) }

    Text(text = stringResource(R.string.award_granted_to))

    LaunchedEffect(key1 = note) { accountViewModel.loadUsers(noteEvent.awardees()) { awardees = it } }

    FlowRow(modifier = Modifier.padding(top = 5.dp)) {
        awardees.take(100).forEach { user ->
            Row(
                modifier =
                    Modifier
                        .size(size = Size35dp)
                        .clickable { nav("User/${user.pubkeyHex}") },
                verticalAlignment = CenterVertically,
            ) {
                ClickableUserPicture(
                    baseUser = user,
                    accountViewModel = accountViewModel,
                    size = Size35dp,
                )
            }
        }

        if (awardees.size > 100) {
            Text(" and ${awardees.size - 100} others", maxLines = 1)
        }
    }

    note.replyTo?.firstOrNull()?.let {
        NoteCompose(
            it,
            modifier = Modifier,
            isBoostedNote = false,
            isQuotedNote = true,
            unPackReply = false,
            parentBackgroundColor = backgroundColor,
            accountViewModel = accountViewModel,
            nav = nav,
        )
    }
}

@Composable
private fun RenderReaction(
    note: Note,
    backgroundColor: MutableState<Color>,
    accountViewModel: AccountViewModel,
    nav: (String) -> Unit,
) {
    note.replyTo?.lastOrNull()?.let {
        NoteCompose(
            it,
            modifier = Modifier,
            isBoostedNote = true,
            unPackReply = false,
            parentBackgroundColor = backgroundColor,
            accountViewModel = accountViewModel,
            nav = nav,
        )
    }

    // Reposts have trash in their contents.
    val refactorReactionText = if (note.event?.content() == "+") "❤" else note.event?.content() ?: ""

    Text(
        text = refactorReactionText,
        maxLines = 1,
    )
}

@Composable
fun RenderRepost(
    note: Note,
    backgroundColor: MutableState<Color>,
    accountViewModel: AccountViewModel,
    nav: (String) -> Unit,
) {
    val boostedNote = remember { note.replyTo?.lastOrNull() }

    boostedNote?.let {
        NoteCompose(
            it,
            modifier = Modifier,
            isBoostedNote = true,
            unPackReply = false,
            parentBackgroundColor = backgroundColor,
            accountViewModel = accountViewModel,
            nav = nav,
        )
    }
}

@Composable
fun RenderPostApproval(
    note: Note,
    makeItShort: Boolean,
    canPreview: Boolean,
    backgroundColor: MutableState<Color>,
    accountViewModel: AccountViewModel,
    nav: (String) -> Unit,
) {
    if (note.replyTo.isNullOrEmpty()) return

    val noteEvent = note.event as? CommunityPostApprovalEvent ?: return

    Column(Modifier.fillMaxWidth()) {
        noteEvent.communities().forEach {
            LoadAddressableNote(it, accountViewModel) {
                it?.let {
                    NoteCompose(
                        it,
                        parentBackgroundColor = backgroundColor,
                        accountViewModel = accountViewModel,
                        nav = nav,
                    )
                }
            }
        }

        Text(
            text = stringResource(id = R.string.community_approved_posts),
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(5.dp),
            textAlign = TextAlign.Center,
        )

        note.replyTo?.forEach {
            NoteCompose(
                it,
                modifier = MaterialTheme.colorScheme.replyModifier,
                unPackReply = false,
                makeItShort = true,
                isQuotedNote = true,
                parentBackgroundColor = backgroundColor,
                accountViewModel = accountViewModel,
                nav = nav,
            )
        }
    }
}

@Composable
fun LoadAddressableNote(
    aTagHex: String,
    accountViewModel: AccountViewModel,
    content: @Composable (AddressableNote?) -> Unit,
) {
    var note by
        remember(aTagHex) {
            mutableStateOf<AddressableNote?>(accountViewModel.getAddressableNoteIfExists(aTagHex))
        }

    if (note == null) {
        LaunchedEffect(key1 = aTagHex) {
            accountViewModel.checkGetOrCreateAddressableNote(aTagHex) { newNote ->
                if (newNote != note) {
                    note = newNote
                }
            }
        }
    }

    content(note)
}

@Composable
fun LoadAddressableNote(
    aTag: ATag,
    accountViewModel: AccountViewModel,
    content: @Composable (AddressableNote?) -> Unit,
) {
    var note by
        remember(aTag) {
            mutableStateOf<AddressableNote?>(accountViewModel.getAddressableNoteIfExists(aTag.toTag()))
        }

    if (note == null) {
        LaunchedEffect(key1 = aTag) {
            accountViewModel.getOrCreateAddressableNote(aTag) { newNote ->
                if (newNote != note) {
                    note = newNote
                }
            }
        }
    }

    content(note)
}

@Composable
public fun RenderEmojiPack(
    baseNote: Note,
    actionable: Boolean,
    backgroundColor: MutableState<Color>,
    accountViewModel: AccountViewModel,
    onClick: ((EmojiUrl) -> Unit)? = null,
) {
    val noteEvent by
        baseNote
            .live()
            .metadata
            .map { it.note.event }
            .distinctUntilChanged()
            .observeAsState(baseNote.event)

    if (noteEvent == null || noteEvent !is EmojiPackEvent) return

    (noteEvent as? EmojiPackEvent)?.let {
        RenderEmojiPack(
            noteEvent = it,
            baseNote = baseNote,
            actionable = actionable,
            backgroundColor = backgroundColor,
            accountViewModel = accountViewModel,
            onClick = onClick,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
public fun RenderEmojiPack(
    noteEvent: EmojiPackEvent,
    baseNote: Note,
    actionable: Boolean,
    backgroundColor: MutableState<Color>,
    accountViewModel: AccountViewModel,
    onClick: ((EmojiUrl) -> Unit)? = null,
) {
    var expanded by remember { mutableStateOf(false) }

    val allEmojis = remember(noteEvent) { noteEvent.taggedEmojis() }

    val emojisToShow =
        if (expanded) {
            allEmojis
        } else {
            allEmojis.take(60)
        }

    Row(verticalAlignment = CenterVertically) {
        Text(
            text = remember(noteEvent) { "#${noteEvent.dTag()}" },
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier =
                Modifier
                    .weight(1F)
                    .padding(5.dp),
            textAlign = TextAlign.Center,
        )

        if (actionable) {
            EmojiListOptions(accountViewModel, baseNote)
        }
    }

    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
        FlowRow(modifier = Modifier.padding(top = 5.dp)) {
            emojisToShow.forEach { emoji ->
                if (onClick != null) {
                    IconButton(onClick = { onClick(emoji) }, modifier = Size35Modifier) {
                        AsyncImage(
                            model = emoji.url,
                            contentDescription = null,
                            modifier = Size35Modifier,
                        )
                    }
                } else {
                    Box(
                        modifier = Size35Modifier,
                        contentAlignment = Alignment.Center,
                    ) {
                        AsyncImage(
                            model = emoji.url,
                            contentDescription = null,
                            modifier = Size35Modifier,
                        )
                    }
                }
            }
        }

        if (allEmojis.size > 60 && !expanded) {
            Row(
                verticalAlignment = CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier =
                    Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .background(getGradient(backgroundColor)),
            ) {
                ShowMoreButton { expanded = !expanded }
            }
        }
    }
}

@Composable
private fun EmojiListOptions(
    accountViewModel: AccountViewModel,
    emojiPackNote: Note,
) {
    LoadAddressableNote(
        aTag =
            ATag(
                EmojiPackSelectionEvent.KIND,
                accountViewModel.userProfile().pubkeyHex,
                "",
                null,
            ),
        accountViewModel,
    ) {
        it?.let { usersEmojiList ->
            val hasAddedThis by
                remember {
                    usersEmojiList
                        .live()
                        .metadata
                        .map { usersEmojiList.event?.isTaggedAddressableNote(emojiPackNote.idHex) }
                        .distinctUntilChanged()
                }
                    .observeAsState()

            Crossfade(targetState = hasAddedThis, label = "EmojiListOptions") {
                if (it != true) {
                    AddButton { accountViewModel.addEmojiPack(usersEmojiList, emojiPackNote) }
                } else {
                    RemoveButton { accountViewModel.removeEmojiPack(usersEmojiList, emojiPackNote) }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RenderPinListEvent(
    baseNote: Note,
    backgroundColor: MutableState<Color>,
    accountViewModel: AccountViewModel,
    nav: (String) -> Unit,
) {
    val noteEvent = baseNote.event as? PinListEvent ?: return

    val pins by remember { mutableStateOf(noteEvent.pins()) }

    var expanded by remember { mutableStateOf(false) }

    val pinsToShow =
        if (expanded) {
            pins
        } else {
            pins.take(3)
        }

    Text(
        text = "#${noteEvent.dTag()}",
        fontWeight = FontWeight.Bold,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(5.dp),
        textAlign = TextAlign.Center,
    )

    Box {
        FlowRow(modifier = Modifier.padding(top = 5.dp)) {
            pinsToShow.forEach { pin ->
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = CenterVertically) {
                    PinIcon(
                        modifier = Size15Modifier,
                        tint = MaterialTheme.colorScheme.onBackground.copy(0.32f),
                    )

                    Spacer(modifier = Modifier.width(5.dp))

                    TranslatableRichTextViewer(
                        content = pin,
                        canPreview = true,
                        tags = EmptyTagList,
                        backgroundColor = backgroundColor,
                        accountViewModel = accountViewModel,
                        nav = nav,
                    )
                }
            }
        }

        if (pins.size > 3 && !expanded) {
            Row(
                verticalAlignment = CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier =
                    Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .background(getGradient(backgroundColor)),
            ) {
                ShowMoreButton { expanded = !expanded }
            }
        }
    }
}

fun getGradient(backgroundColor: MutableState<Color>): Brush {
    return Brush.verticalGradient(
        colors =
            listOf(
                backgroundColor.value.copy(alpha = 0f),
                backgroundColor.value,
            ),
    )
}

@Composable
private fun RenderAudioTrack(
    note: Note,
    accountViewModel: AccountViewModel,
    nav: (String) -> Unit,
) {
    val noteEvent = note.event as? AudioTrackEvent ?: return

    AudioTrackHeader(noteEvent, note, accountViewModel, nav)
}

@Composable
private fun RenderAudioHeader(
    note: Note,
    accountViewModel: AccountViewModel,
    nav: (String) -> Unit,
) {
    val noteEvent = note.event as? AudioHeaderEvent ?: return

    AudioHeader(noteEvent, note, accountViewModel, nav)
}

@Composable
private fun RenderLongFormContent(
    note: Note,
    accountViewModel: AccountViewModel,
    nav: (String) -> Unit,
) {
    val noteEvent = note.event as? LongTextNoteEvent ?: return

    LongFormHeader(noteEvent, note, accountViewModel)
}

@Composable
private fun RenderReport(
    note: Note,
    backgroundColor: MutableState<Color>,
    accountViewModel: AccountViewModel,
    nav: (String) -> Unit,
) {
    val noteEvent = note.event as? ReportEvent ?: return

    val base = remember { (noteEvent.reportedPost() + noteEvent.reportedAuthor()) }

    val reportType =
        base
            .map {
                when (it.reportType) {
                    ReportEvent.ReportType.EXPLICIT -> stringResource(R.string.explicit_content)
                    ReportEvent.ReportType.NUDITY -> stringResource(R.string.nudity)
                    ReportEvent.ReportType.PROFANITY -> stringResource(R.string.profanity_hateful_speech)
                    ReportEvent.ReportType.SPAM -> stringResource(R.string.spam)
                    ReportEvent.ReportType.IMPERSONATION -> stringResource(R.string.impersonation)
                    ReportEvent.ReportType.ILLEGAL -> stringResource(R.string.illegal_behavior)
                    ReportEvent.ReportType.OTHER -> stringResource(R.string.other)
                }
            }
            .toSet()
            .joinToString(", ")

    val content =
        remember {
            reportType + (note.event?.content()?.ifBlank { null }?.let { ": $it" } ?: "")
        }

    TranslatableRichTextViewer(
        content = content,
        canPreview = true,
        modifier = Modifier,
        tags = EmptyTagList,
        backgroundColor = backgroundColor,
        accountViewModel = accountViewModel,
        nav = nav,
    )

    note.replyTo?.lastOrNull()?.let {
        NoteCompose(
            baseNote = it,
            isQuotedNote = true,
            modifier =
                Modifier
                    .padding(top = 5.dp)
                    .fillMaxWidth()
                    .clip(shape = QuoteBorder)
                    .border(
                        1.dp,
                        MaterialTheme.colorScheme.subtleBorder,
                        QuoteBorder,
                    ),
            unPackReply = false,
            makeItShort = true,
            parentBackgroundColor = backgroundColor,
            accountViewModel = accountViewModel,
            nav = nav,
        )
    }
}

@Composable
private fun ReplyRow(
    note: Note,
    unPackReply: Boolean,
    backgroundColor: MutableState<Color>,
    accountViewModel: AccountViewModel,
    nav: (String) -> Unit,
) {
    val noteEvent = note.event

    val showReply by
        remember(note) {
            derivedStateOf {
                noteEvent is BaseTextNoteEvent && (note.replyTo != null || noteEvent.hasAnyTaggedUser())
            }
        }

    val showChannelInfo by
        remember(note) {
            derivedStateOf {
                if (noteEvent is ChannelMessageEvent || noteEvent is LiveActivitiesChatMessageEvent) {
                    note.channelHex()
                } else {
                    null
                }
            }
        }

    showChannelInfo?.let {
        ChannelHeader(
            channelHex = it,
            showVideo = false,
            showBottomDiviser = false,
            sendToChannel = true,
            modifier = MaterialTheme.colorScheme.replyModifier.padding(10.dp),
            accountViewModel = accountViewModel,
            nav = nav,
        )
    }

    if (showReply) {
        val replyingDirectlyTo =
            remember(note) {
                if (noteEvent is BaseTextNoteEvent) {
                    val replyingTo = noteEvent.replyingTo()
                    if (replyingTo != null) {
                        note.replyTo?.firstOrNull {
                            // important to test both ids in case it's a replaceable event.
                            it.idHex == replyingTo || it.event?.id() == replyingTo
                        }
                    } else {
                        note.replyTo?.lastOrNull { it.event?.kind() != CommunityDefinitionEvent.KIND }
                    }
                } else {
                    note.replyTo?.lastOrNull { it.event?.kind() != CommunityDefinitionEvent.KIND }
                }
            }
        if (replyingDirectlyTo != null && unPackReply) {
            ReplyNoteComposition(replyingDirectlyTo, backgroundColor, accountViewModel, nav)
            Spacer(modifier = StdVertSpacer)
        } else if (showChannelInfo != null) {
            val replies = remember { note.replyTo?.toImmutableList() }
            val mentions =
                remember {
                    (note.event as? BaseTextNoteEvent)?.mentions()?.toImmutableList() ?: persistentListOf()
                }

            ReplyInformationChannel(replies, mentions, accountViewModel, nav)
        }
    }
}

@Composable
private fun ReplyNoteComposition(
    replyingDirectlyTo: Note,
    backgroundColor: MutableState<Color>,
    accountViewModel: AccountViewModel,
    nav: (String) -> Unit,
) {
    val replyBackgroundColor = remember { mutableStateOf(backgroundColor.value) }
    val defaultReplyBackground = MaterialTheme.colorScheme.replyBackground

    LaunchedEffect(key1 = backgroundColor.value, key2 = defaultReplyBackground) {
        launch(Dispatchers.Default) {
            val newReplyBackgroundColor = defaultReplyBackground.compositeOver(backgroundColor.value)
            if (replyBackgroundColor.value != newReplyBackgroundColor) {
                replyBackgroundColor.value = newReplyBackgroundColor
            }
        }
    }

    NoteCompose(
        baseNote = replyingDirectlyTo,
        isQuotedNote = true,
        modifier = MaterialTheme.colorScheme.replyModifier,
        unPackReply = false,
        makeItShort = true,
        parentBackgroundColor = replyBackgroundColor,
        accountViewModel = accountViewModel,
        nav = nav,
    )
}

@Composable
fun SecondUserInfoRow(
    note: Note,
    accountViewModel: AccountViewModel,
    nav: (String) -> Unit,
) {
    val noteEvent = note.event ?: return
    val noteAuthor = note.author ?: return

    Row(
        verticalAlignment = CenterVertically,
        modifier = UserNameMaxRowHeight,
    ) {
        if (noteEvent is BaseTextNoteEvent && noteEvent.isAFork()) {
            ShowForkInformation(noteEvent, remember(noteEvent) { Modifier.weight(1f) }, accountViewModel, nav)
        } else {
            ObserveDisplayNip05Status(noteAuthor, remember(noteEvent) { Modifier.weight(1f) }, accountViewModel, nav)
        }

        val geo = remember(noteEvent) { noteEvent.getGeoHash() }
        if (geo != null) {
            Spacer(StdHorzSpacer)
            DisplayLocation(geo, nav)
        }

        val baseReward = remember(noteEvent) { noteEvent.getReward()?.let { Reward(it) } }
        if (baseReward != null) {
            Spacer(StdHorzSpacer)
            DisplayReward(baseReward, note, accountViewModel, nav)
        }

        val pow = remember(noteEvent) { noteEvent.getPoWRank() }
        if (pow > 20) {
            Spacer(StdHorzSpacer)
            DisplayPoW(pow)
        }

        DisplayOts(note, accountViewModel)
    }
}

@Composable
fun DisplayOts(
    note: Note,
    accountViewModel: AccountViewModel,
) {
    LoadOts(
        note,
        accountViewModel,
        whenConfirmed = { unixtimestamp ->
            val context = LocalContext.current
            val timeStr by remember(unixtimestamp) { mutableStateOf(timeAgoNoDot(unixtimestamp, context = context)) }

            ClickableText(
                text = buildAnnotatedString { append(stringResource(id = R.string.existed_since, timeStr)) },
                onClick = {
                    val fullDateTime =
                        SimpleDateFormat.getDateTimeInstance().format(Date(unixtimestamp * 1000))

                    accountViewModel.toast(
                        context.getString(R.string.ots_info_title),
                        context.getString(R.string.ots_info_description, fullDateTime),
                    )
                },
                style =
                    LocalTextStyle.current.copy(
                        color = MaterialTheme.colorScheme.lessImportantLink,
                        fontSize = Font14SP,
                        fontWeight = FontWeight.Bold,
                    ),
                maxLines = 1,
            )
        },
        whenPending = {
            Text(
                stringResource(id = R.string.timestamp_pending_short),
                color = MaterialTheme.colorScheme.lessImportantLink,
                fontSize = Font14SP,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
            )
        },
    )
}

@Composable
private fun ShowForkInformation(
    noteEvent: BaseTextNoteEvent,
    modifier: Modifier,
    accountViewModel: AccountViewModel,
    nav: (String) -> Unit,
) {
    val forkedAddress = remember(noteEvent) { noteEvent.forkFromAddress() }
    val forkedEvent = remember(noteEvent) { noteEvent.forkFromVersion() }
    if (forkedAddress != null) {
        LoadAddressableNote(aTag = forkedAddress, accountViewModel = accountViewModel) { addressableNote ->
            if (addressableNote != null) {
                ForkInformationRowLightColor(addressableNote, modifier, accountViewModel, nav)
            }
        }
    } else if (forkedEvent != null) {
        LoadNote(forkedEvent, accountViewModel = accountViewModel) { event ->
            if (event != null) {
                ForkInformationRowLightColor(event, modifier, accountViewModel, nav)
            }
        }
    }
}

@Composable
fun ForkInformationRowLightColor(
    originalVersion: Note,
    modifier: Modifier = Modifier,
    accountViewModel: AccountViewModel,
    nav: (String) -> Unit,
) {
    val noteState by originalVersion.live().metadata.observeAsState()
    val note = noteState?.note ?: return
    val author = note.author ?: return
    val route = remember(note) { routeFor(note, accountViewModel.userProfile()) }

    if (route != null) {
        Row(modifier) {
            ClickableText(
                text =
                    buildAnnotatedString {
                        append(stringResource(id = R.string.forked_from))
                        append(" ")
                    },
                onClick = { nav(route) },
                style = LocalTextStyle.current.copy(color = MaterialTheme.colorScheme.nip05, fontSize = Font14SP),
                maxLines = 1,
                overflow = TextOverflow.Visible,
            )

            val userState by author.live().metadata.observeAsState()
            val userDisplayName = remember(userState) { userState?.user?.toBestDisplayName() }
            val userTags =
                remember(userState) { userState?.user?.info?.latestMetadata?.tags?.toImmutableListOfLists() }

            if (userDisplayName != null) {
                CreateClickableTextWithEmoji(
                    clickablePart = userDisplayName,
                    maxLines = 1,
                    route = route,
                    overrideColor = MaterialTheme.colorScheme.nip05,
                    fontSize = Font14SP,
                    nav = nav,
                    tags = userTags,
                )
            }
        }
    }
}

@Composable
fun LoadStatuses(
    user: User,
    accountViewModel: AccountViewModel,
    content: @Composable (ImmutableList<AddressableNote>) -> Unit,
) {
    var statuses: ImmutableList<AddressableNote> by remember { mutableStateOf(persistentListOf()) }

    val userStatus by user.live().statuses.observeAsState()

    LaunchedEffect(key1 = userStatus) {
        accountViewModel.findStatusesForUser(userStatus?.user ?: user) { newStatuses ->
            if (!equalImmutableLists(statuses, newStatuses)) {
                statuses = newStatuses
            }
        }
    }

    content(statuses)
}

@Composable
fun LoadOts(
    note: Note,
    accountViewModel: AccountViewModel,
    whenConfirmed: @Composable (Long) -> Unit,
    whenPending: @Composable () -> Unit,
) {
    var earliestDate: GenericLoadable<Long> by remember { mutableStateOf(GenericLoadable.Loading()) }

    val noteStatus by note.live().innerOts.observeAsState()

    LaunchedEffect(key1 = noteStatus) {
        accountViewModel.findOtsEventsForNote(noteStatus?.note ?: note) { newOts ->
            earliestDate =
                if (newOts == null) {
                    GenericLoadable.Empty()
                } else {
                    GenericLoadable.Loaded(newOts)
                }
        }
    }

    (earliestDate as? GenericLoadable.Loaded)?.let {
        whenConfirmed(it.loaded)
    } ?: run {
        val account = accountViewModel.account.saveable.observeAsState()
        if (account.value?.account?.hasPendingAttestations(note) == true) {
            whenPending()
        }
    }
}

@Composable
fun LoadCityName(
    geohashStr: String,
    onLoading: (@Composable () -> Unit)? = null,
    content: @Composable (String) -> Unit,
) {
    var cityName by remember(geohashStr) { mutableStateOf(CachedGeoLocations.cached(geohashStr)) }

    if (cityName == null) {
        if (onLoading != null) {
            onLoading()
        }

        val context = LocalContext.current

        LaunchedEffect(key1 = geohashStr, context) {
            launch(Dispatchers.IO) {
                val geoHash = runCatching { geohashStr.toGeoHash() }.getOrNull()
                if (geoHash != null) {
                    val newCityName =
                        CachedGeoLocations.geoLocate(geohashStr, geoHash.toLocation(), context)?.ifBlank { null }
                    if (newCityName != null && newCityName != cityName) {
                        cityName = newCityName
                    }
                }
            }
        }
    } else {
        cityName?.let { content(it) }
    }
}

@Composable
fun DisplayLocation(
    geohashStr: String,
    nav: (String) -> Unit,
) {
    LoadCityName(geohashStr) { cityName ->
        ClickableText(
            text = AnnotatedString(cityName),
            onClick = { nav("Geohash/$geohashStr") },
            style =
                LocalTextStyle.current.copy(
                    color =
                        MaterialTheme.colorScheme.primary.copy(
                            alpha = 0.52f,
                        ),
                    fontSize = Font14SP,
                    fontWeight = FontWeight.Bold,
                ),
            maxLines = 1,
        )
    }
}

@Composable
fun FirstUserInfoRow(
    baseNote: Note,
    showAuthorPicture: Boolean,
    editState: State<GenericLoadable<EditState>>,
    accountViewModel: AccountViewModel,
    nav: (String) -> Unit,
) {
    Row(verticalAlignment = CenterVertically, modifier = remember { UserNameRowHeight }) {
        val isRepost by
            remember(baseNote) {
                derivedStateOf { baseNote.event is RepostEvent || baseNote.event is GenericRepostEvent }
            }

        val isCommunityPost by
            remember(baseNote) {
                derivedStateOf {
                    baseNote.event?.isTaggedAddressableKind(CommunityDefinitionEvent.KIND) == true
                }
            }

        val textColor = if (isRepost) MaterialTheme.colorScheme.grayText else Color.Unspecified

        if (showAuthorPicture) {
            NoteAuthorPicture(baseNote, nav, accountViewModel, Size25dp)
            Spacer(HalfPadding)
            NoteUsernameDisplay(baseNote, remember { Modifier.weight(1f) }, textColor = textColor)
        } else {
            NoteUsernameDisplay(baseNote, remember { Modifier.weight(1f) }, textColor = textColor)
        }

        if (isRepost) {
            BoostedMark()
        } else if (isCommunityPost) {
            DisplayFollowingCommunityInPost(baseNote, accountViewModel, nav)
        } else {
            DisplayFollowingHashtagsInPost(baseNote, accountViewModel, nav)
        }

        if (editState.value is GenericLoadable.Loaded) {
            (editState.value as? GenericLoadable.Loaded<EditState>)?.loaded?.let {
                DisplayEditStatus(it)
            }
        }

        TimeAgo(baseNote)

        MoreOptionsButton(baseNote, editState, accountViewModel, nav)
    }
}

@Composable
fun observeEdits(
    baseNote: Note,
    accountViewModel: AccountViewModel,
): State<GenericLoadable<EditState>> {
    if (baseNote.event !is TextNoteEvent) {
        return remember { mutableStateOf(GenericLoadable.Empty<EditState>()) }
    }

    val editState =
        remember(baseNote.idHex) {
            val cached = accountViewModel.cachedModificationEventsForNote(baseNote)
            mutableStateOf(
                if (cached != null) {
                    if (cached.isEmpty()) {
                        GenericLoadable.Empty<EditState>()
                    } else {
                        val state = EditState()
                        state.updateModifications(cached)
                        GenericLoadable.Loaded<EditState>(state)
                    }
                } else {
                    GenericLoadable.Loading<EditState>()
                },
            )
        }

    val updatedNote by baseNote.live().innerModifications.observeAsState()

    LaunchedEffect(key1 = updatedNote) {
        updatedNote?.note?.let {
            accountViewModel.findModificationEventsForNote(it) { newModifications ->
                if (newModifications.isEmpty()) {
                    if (editState.value !is GenericLoadable.Empty) {
                        editState.value = GenericLoadable.Empty<EditState>()
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
    }

    return editState
}

@Composable
fun DisplayEditStatus(editState: EditState) {
    ClickableText(
        text =
            buildAnnotatedString {
                if (editState.showingVersion.value == editState.originalVersionId()) {
                    append(stringResource(id = R.string.original))
                } else if (editState.showingVersion.value == editState.lastVersionId()) {
                    append(stringResource(id = R.string.edited))
                } else {
                    append(stringResource(id = R.string.edited_number, editState.versionId()))
                }
            },
        onClick = {
            editState.nextModification()
        },
        style =
            LocalTextStyle.current.copy(
                color = MaterialTheme.colorScheme.placeholderText,
                fontWeight = FontWeight.Bold,
            ),
        maxLines = 1,
        modifier = HalfStartPadding,
    )
}

@Composable
private fun BoostedMark() {
    Text(
        stringResource(id = R.string.boosted),
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.placeholderText,
        maxLines = 1,
        modifier = HalfStartPadding,
    )
}

@Composable
fun MoreOptionsButton(
    baseNote: Note,
    editState: State<GenericLoadable<EditState>>? = null,
    accountViewModel: AccountViewModel,
    nav: (String) -> Unit,
) {
    val popupExpanded = remember { mutableStateOf(false) }
    val enablePopup = remember { { popupExpanded.value = true } }

    IconButton(
        modifier = Size24Modifier,
        onClick = enablePopup,
    ) {
        VerticalDotsIcon(R.string.note_options)

        NoteDropDownMenu(
            baseNote,
            popupExpanded,
            editState,
            accountViewModel,
            nav,
        )
    }
}

@Composable
fun TimeAgo(note: Note) {
    val time = remember(note) { note.createdAt() } ?: return
    TimeAgo(time)
}

@Composable
fun TimeAgo(time: Long) {
    val context = LocalContext.current
    val timeStr by remember(time) { mutableStateOf(timeAgo(time, context = context)) }

    Text(
        text = timeStr,
        color = MaterialTheme.colorScheme.placeholderText,
        maxLines = 1,
    )
}

@Composable
private fun AuthorAndRelayInformation(
    baseNote: Note,
    accountViewModel: AccountViewModel,
    nav: (String) -> Unit,
) {
    // Draws the boosted picture outside the boosted card.
    Box(modifier = Size55Modifier, contentAlignment = Alignment.BottomEnd) {
        RenderAuthorImages(baseNote, nav, accountViewModel)
    }

    BadgeBox(baseNote, accountViewModel, nav)
}

@Composable
private fun BadgeBox(
    baseNote: Note,
    accountViewModel: AccountViewModel,
    nav: (String) -> Unit,
) {
    if (baseNote.event is RepostEvent || baseNote.event is GenericRepostEvent) {
        baseNote.replyTo?.lastOrNull()?.let { RelayBadges(it, accountViewModel, nav) }
    } else {
        RelayBadges(baseNote, accountViewModel, nav)
    }
}

@Composable
private fun RenderAuthorImages(
    baseNote: Note,
    nav: (String) -> Unit,
    accountViewModel: AccountViewModel,
) {
    val isRepost = baseNote.event is RepostEvent || baseNote.event is GenericRepostEvent

    if (isRepost) {
        val baseRepost = baseNote.replyTo?.lastOrNull()
        if (baseRepost != null) {
            RepostNoteAuthorPicture(baseNote, baseRepost, accountViewModel, nav)
        } else {
            NoteAuthorPicture(baseNote, nav, accountViewModel, Size55dp)
        }
    } else {
        NoteAuthorPicture(baseNote, nav, accountViewModel, Size55dp)
    }

    if (baseNote.event is ChannelMessageEvent) {
        val baseChannelHex = remember(baseNote) { baseNote.channelHex() }
        if (baseChannelHex != null) {
            LoadChannel(baseChannelHex, accountViewModel) { channel ->
                ChannelNotePicture(
                    channel,
                    loadProfilePicture = accountViewModel.settings.showProfilePictures.value,
                )
            }
        }
    }
}

@Composable
fun LoadChannel(
    baseChannelHex: String,
    accountViewModel: AccountViewModel,
    content: @Composable (Channel) -> Unit,
) {
    var channel by
        remember(baseChannelHex) {
            mutableStateOf<Channel?>(accountViewModel.getChannelIfExists(baseChannelHex))
        }

    if (channel == null) {
        LaunchedEffect(key1 = baseChannelHex) {
            accountViewModel.checkGetOrCreateChannel(baseChannelHex) { newChannel ->
                launch(Dispatchers.Main) { channel = newChannel }
            }
        }
    }

    channel?.let { content(it) }
}

@Composable
private fun ChannelNotePicture(
    baseChannel: Channel,
    loadProfilePicture: Boolean,
) {
    val model by
        baseChannel.live.map { it.channel.profilePicture() }.distinctUntilChanged().observeAsState()

    Box(Size30Modifier) {
        RobohashFallbackAsyncImage(
            robot = baseChannel.idHex,
            model = model,
            contentDescription = stringResource(R.string.group_picture),
            modifier = MaterialTheme.colorScheme.channelNotePictureModifier,
            loadProfilePicture = loadProfilePicture,
        )
    }
}

@Composable
private fun RepostNoteAuthorPicture(
    baseNote: Note,
    baseRepost: Note,
    accountViewModel: AccountViewModel,
    nav: (String) -> Unit,
) {
    GenericRepostLayout(
        baseAuthorPicture = {
            NoteAuthorPicture(
                baseNote = baseNote,
                nav = nav,
                accountViewModel = accountViewModel,
                size = Size34dp,
            )
        },
        repostAuthorPicture = {
            NoteAuthorPicture(
                baseNote = baseRepost,
                nav = nav,
                accountViewModel = accountViewModel,
                size = Size34dp,
            )
        },
    )
}

@Composable
fun DisplayHighlight(
    highlight: String,
    authorHex: String?,
    url: String?,
    postAddress: ATag?,
    makeItShort: Boolean,
    canPreview: Boolean,
    backgroundColor: MutableState<Color>,
    accountViewModel: AccountViewModel,
    nav: (String) -> Unit,
) {
    val quote =
        remember {
            highlight.split("\n").joinToString("\n") { "> *${it.removeSuffix(" ")}*" }
        }

    TranslatableRichTextViewer(
        quote,
        canPreview = canPreview && !makeItShort,
        remember { Modifier.fillMaxWidth() },
        EmptyTagList,
        backgroundColor,
        accountViewModel,
        nav,
    )

    DisplayQuoteAuthor(authorHex ?: "", url, postAddress, accountViewModel, nav)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DisplayQuoteAuthor(
    authorHex: String,
    url: String?,
    postAddress: ATag?,
    accountViewModel: AccountViewModel,
    nav: (String) -> Unit,
) {
    var userBase by remember { mutableStateOf<User?>(accountViewModel.getUserIfExists(authorHex)) }

    if (userBase == null) {
        LaunchedEffect(Unit) {
            accountViewModel.checkGetOrCreateUser(authorHex) { newUserBase -> userBase = newUserBase }
        }
    }

    val spaceWidth = measureSpaceWidth(textStyle = LocalTextStyle.current)

    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(spaceWidth),
        verticalArrangement = Arrangement.Center,
    ) {
        userBase?.let { userBase -> LoadAndDisplayUser(userBase, nav) }

        url?.let { url -> LoadAndDisplayUrl(url) }

        postAddress?.let { address -> LoadAndDisplayPost(address, accountViewModel, nav) }
    }
}

@Composable
private fun LoadAndDisplayPost(
    postAddress: ATag,
    accountViewModel: AccountViewModel,
    nav: (String) -> Unit,
) {
    LoadAddressableNote(aTag = postAddress, accountViewModel) {
        it?.let { note ->
            val noteEvent by
                note.live().metadata.map { it.note.event }.distinctUntilChanged().observeAsState(note.event)

            val title = remember(noteEvent) { (noteEvent as? LongTextNoteEvent)?.title() }

            title?.let {
                Text(remember { "-" }, maxLines = 1)
                ClickableText(
                    text = AnnotatedString(title),
                    onClick = { routeFor(note, accountViewModel.userProfile())?.let { nav(it) } },
                    style = LocalTextStyle.current.copy(color = MaterialTheme.colorScheme.primary),
                )
            }
        }
    }
}

@Composable
private fun LoadAndDisplayUrl(url: String) {
    val validatedUrl =
        remember {
            try {
                URL(url)
            } catch (e: Exception) {
                Log.w("Note Compose", "Invalid URI: $url")
                null
            }
        }

    validatedUrl?.host?.let { host ->
        Text(remember { "-" }, maxLines = 1)
        ClickableUrl(urlText = host, url = url)
    }
}

@Composable
fun LoadAndDisplayUser(
    userBase: User,
    nav: (String) -> Unit,
) {
    LoadAndDisplayUser(userBase, "User/${userBase.pubkeyHex}", nav)
}

@Composable
fun LoadAndDisplayUser(
    userBase: User,
    route: String,
    nav: (String) -> Unit,
) {
    val userState by userBase.live().metadata.observeAsState()
    val userDisplayName = remember(userState) { userState?.user?.toBestDisplayName() }
    val userTags =
        remember(userState) { userState?.user?.info?.latestMetadata?.tags?.toImmutableListOfLists() }

    if (userDisplayName != null) {
        CreateClickableTextWithEmoji(
            clickablePart = userDisplayName,
            maxLines = 1,
            route = route,
            nav = nav,
            tags = userTags,
        )
    }
}

@Composable
fun BadgeDisplay(baseNote: Note) {
    val background = MaterialTheme.colorScheme.background
    val badgeData = baseNote.event as? BadgeDefinitionEvent ?: return

    val image = remember { badgeData.thumb()?.ifBlank { null } ?: badgeData.image() }
    val name = remember { badgeData.name() }
    val description = remember { badgeData.description() }

    var backgroundFromImage by remember { mutableStateOf(Pair(background, background)) }
    var imageResult by remember { mutableStateOf<SuccessResult?>(null) }

    LaunchedEffect(key1 = imageResult) {
        launch(Dispatchers.IO) {
            imageResult?.let {
                val backgroundColor =
                    it.drawable.toBitmap(200, 200).copy(Bitmap.Config.ARGB_8888, false).get(0, 199)
                val colorFromImage = Color(backgroundColor)
                val textBackground =
                    if (colorFromImage.luminance() > 0.5) {
                        lightColorScheme().onBackground
                    } else {
                        darkColorScheme().onBackground
                    }

                launch(Dispatchers.Main) { backgroundFromImage = Pair(colorFromImage, textBackground) }
            }
        }
    }

    Row(
        modifier =
            Modifier
                .padding(10.dp)
                .clip(shape = CutCornerShape(20, 20, 20, 20))
                .border(
                    5.dp,
                    MaterialTheme.colorScheme.mediumImportanceLink,
                    CutCornerShape(20),
                )
                .background(backgroundFromImage.first),
    ) {
        RenderBadge(
            image,
            name,
            backgroundFromImage.second,
            description,
        ) {
            if (imageResult == null) {
                imageResult = it.result
            }
        }
    }
}

@Composable
private fun RenderBadge(
    image: String?,
    name: String?,
    backgroundFromImage: Color,
    description: String?,
    onSuccess: (AsyncImagePainter.State.Success) -> Unit,
) {
    Column {
        image.let {
            AsyncImage(
                model = it,
                contentDescription =
                    stringResource(
                        R.string.badge_award_image_for,
                        name ?: "",
                    ),
                contentScale = ContentScale.FillWidth,
                modifier = Modifier.fillMaxWidth(),
                onSuccess = onSuccess,
            )
        }

        name?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(start = 10.dp, end = 10.dp),
                color = backgroundFromImage,
            )
        }

        description?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(start = 10.dp, end = 10.dp, bottom = 10.dp),
                color = Color.Gray,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
fun FileHeaderDisplay(
    note: Note,
    roundedCorner: Boolean,
    accountViewModel: AccountViewModel,
) {
    val event = (note.event as? FileHeaderEvent) ?: return
    val fullUrl = event.url() ?: return

    val content by
        remember(note) {
            val blurHash = event.blurhash()
            val hash = event.hash()
            val dimensions = event.dimensions()
            val description = event.alt() ?: event.content
            val isImage = RichTextParser.isImageUrl(fullUrl)
            val uri = note.toNostrUri()

            mutableStateOf<BaseMediaContent>(
                if (isImage) {
                    MediaUrlImage(
                        url = fullUrl,
                        description = description,
                        hash = hash,
                        blurhash = blurHash,
                        dim = dimensions,
                        uri = uri,
                    )
                } else {
                    MediaUrlVideo(
                        url = fullUrl,
                        description = description,
                        hash = hash,
                        dim = dimensions,
                        uri = uri,
                        authorName = note.author?.toBestDisplayName(),
                    )
                },
            )
        }

    SensitivityWarning(note = note, accountViewModel = accountViewModel) {
        ZoomableContentView(
            content = content,
            roundedCorner = roundedCorner,
            accountViewModel = accountViewModel,
        )
    }
}

@Composable
fun VideoDisplay(
    note: Note,
    makeItShort: Boolean,
    canPreview: Boolean,
    backgroundColor: MutableState<Color>,
    accountViewModel: AccountViewModel,
    nav: (String) -> Unit,
) {
    val event = (note.event as? VideoEvent) ?: return
    val fullUrl = event.url() ?: return

    val title = event.title()
    val summary = event.content.ifBlank { null }?.takeIf { title != it }
    val image = event.thumb() ?: event.image()
    val isYouTube = fullUrl.contains("youtube.com") || fullUrl.contains("youtu.be")
    val tags = remember(note) { note.event?.tags()?.toImmutableListOfLists() ?: EmptyTagList }

    val content by
        remember(note) {
            val blurHash = event.blurhash()
            val hash = event.hash()
            val dimensions = event.dimensions()
            val description = event.alt() ?: event.content
            val isImage = RichTextParser.isImageUrl(fullUrl)
            val uri = note.toNostrUri()

            mutableStateOf<BaseMediaContent>(
                if (isImage) {
                    MediaUrlImage(
                        url = fullUrl,
                        description = description,
                        hash = hash,
                        blurhash = blurHash,
                        dim = dimensions,
                        uri = uri,
                    )
                } else {
                    MediaUrlVideo(
                        url = fullUrl,
                        description = description,
                        hash = hash,
                        dim = dimensions,
                        uri = uri,
                        authorName = note.author?.toBestDisplayName(),
                        artworkUri = event.thumb() ?: event.image(),
                    )
                },
            )
        }

    SensitivityWarning(note = note, accountViewModel = accountViewModel) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(top = 5.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (isYouTube) {
                val uri = LocalUriHandler.current
                Row(
                    modifier = Modifier.clickable { runCatching { uri.openUri(fullUrl) } },
                ) {
                    image?.let {
                        AsyncImage(
                            model = it,
                            contentDescription =
                                stringResource(
                                    R.string.preview_card_image_for,
                                    it,
                                ),
                            contentScale = ContentScale.FillWidth,
                            modifier = MaterialTheme.colorScheme.imageModifier,
                        )
                    }
                        ?: CreateImageHeader(note, accountViewModel)
                }
            } else {
                ZoomableContentView(
                    content = content,
                    roundedCorner = true,
                    accountViewModel = accountViewModel,
                )
            }

            title?.let {
                Text(
                    text = it,
                    fontWeight = FontWeight.Bold,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(top = 5.dp),
                )
            }

            summary?.let {
                TranslatableRichTextViewer(
                    content = it,
                    canPreview = canPreview && !makeItShort,
                    modifier = Modifier.fillMaxWidth(),
                    tags = tags,
                    backgroundColor = backgroundColor,
                    accountViewModel = accountViewModel,
                    nav = nav,
                )
            }

            if (event.hasHashtags()) {
                Row(
                    Modifier.fillMaxWidth(),
                ) {
                    DisplayUncitedHashtags(
                        remember(event) { event.hashtags().toImmutableList() },
                        summary ?: "",
                        nav,
                    )
                }
            }
        }
    }
}

@Composable
fun FileStorageHeaderDisplay(
    baseNote: Note,
    roundedCorner: Boolean,
    accountViewModel: AccountViewModel,
) {
    val eventHeader = (baseNote.event as? FileStorageHeaderEvent) ?: return
    val dataEventId = eventHeader.dataEventId() ?: return

    LoadNote(baseNoteHex = dataEventId, accountViewModel) { contentNote ->
        if (contentNote != null) {
            ObserverAndRenderNIP95(baseNote, contentNote, roundedCorner, accountViewModel)
        }
    }
}

@Composable
private fun ObserverAndRenderNIP95(
    header: Note,
    content: Note,
    roundedCorner: Boolean,
    accountViewModel: AccountViewModel,
) {
    val eventHeader = (header.event as? FileStorageHeaderEvent) ?: return

    val appContext = LocalContext.current.applicationContext

    val noteState by content.live().metadata.observeAsState()

    val content by
        remember(noteState) {
            // Creates a new object when the event arrives to force an update of the image.
            val note = noteState?.note
            val uri = header.toNostrUri()
            val localDir = note?.idHex?.let { File(File(appContext.cacheDir, "NIP95"), it) }
            val blurHash = eventHeader.blurhash()
            val dimensions = eventHeader.dimensions()
            val description = eventHeader.alt() ?: eventHeader.content
            val mimeType = eventHeader.mimeType()

            val newContent =
                if (mimeType?.startsWith("image") == true) {
                    MediaLocalImage(
                        localFile = localDir,
                        mimeType = mimeType,
                        description = description,
                        dim = dimensions,
                        blurhash = blurHash,
                        isVerified = true,
                        uri = uri,
                    )
                } else {
                    MediaLocalVideo(
                        localFile = localDir,
                        mimeType = mimeType,
                        description = description,
                        dim = dimensions,
                        isVerified = true,
                        uri = uri,
                        authorName = header.author?.toBestDisplayName(),
                    )
                }

            mutableStateOf<BaseMediaContent?>(newContent)
        }

    Crossfade(targetState = content) {
        if (it != null) {
            SensitivityWarning(note = header, accountViewModel = accountViewModel) {
                ZoomableContentView(
                    content = it,
                    roundedCorner = roundedCorner,
                    accountViewModel = accountViewModel,
                )
            }
        }
    }
}

@Composable
fun AudioTrackHeader(
    noteEvent: AudioTrackEvent,
    note: Note,
    accountViewModel: AccountViewModel,
    nav: (String) -> Unit,
) {
    val media = remember { noteEvent.media() }
    val cover = remember { noteEvent.cover() }
    val subject = remember { noteEvent.subject() }
    val content = remember { noteEvent.content() }
    val participants = remember { noteEvent.participants() }

    var participantUsers by remember { mutableStateOf<List<Pair<Participant, User>>>(emptyList()) }

    LaunchedEffect(key1 = participants) {
        accountViewModel.loadParticipants(participants) { participantUsers = it }
    }

    Row(modifier = Modifier.padding(top = 5.dp)) {
        Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Row {
                subject?.let {
                    Row(
                        verticalAlignment = CenterVertically,
                        modifier = Modifier.padding(top = 5.dp, bottom = 5.dp),
                    ) {
                        Text(
                            text = it,
                            fontWeight = FontWeight.Bold,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }

            participantUsers.forEach {
                Row(
                    verticalAlignment = CenterVertically,
                    modifier =
                        Modifier
                            .padding(top = 5.dp, start = 10.dp, end = 10.dp)
                            .clickable {
                                nav("User/${it.second.pubkeyHex}")
                            },
                ) {
                    ClickableUserPicture(it.second, 25.dp, accountViewModel)
                    Spacer(Modifier.width(5.dp))
                    UsernameDisplay(it.second, Modifier.weight(1f))
                    Spacer(Modifier.width(5.dp))
                    it.first.role?.let {
                        Text(
                            text = it.capitalize(Locale.ROOT),
                            color = MaterialTheme.colorScheme.placeholderText,
                            maxLines = 1,
                        )
                    }
                }
            }

            media?.let { media ->
                Row(
                    verticalAlignment = CenterVertically,
                ) {
                    cover?.let { cover ->
                        LoadThumbAndThenVideoView(
                            videoUri = media,
                            title = noteEvent.subject(),
                            thumbUri = cover,
                            authorName = note.author?.toBestDisplayName(),
                            roundedCorner = true,
                            nostrUriCallback = "nostr:${note.toNEvent()}",
                            accountViewModel = accountViewModel,
                        )
                    }
                        ?: VideoView(
                            videoUri = media,
                            title = noteEvent.subject(),
                            authorName = note.author?.toBestDisplayName(),
                            roundedCorner = true,
                            accountViewModel = accountViewModel,
                        )
                }
            }
        }
    }
}

@Composable
fun AudioHeader(
    noteEvent: AudioHeaderEvent,
    note: Note,
    accountViewModel: AccountViewModel,
    nav: (String) -> Unit,
) {
    val media = remember { noteEvent.stream() ?: noteEvent.download() }
    val waveform = remember { noteEvent.wavefrom()?.toImmutableList()?.ifEmpty { null } }
    val content = remember { noteEvent.content().ifBlank { null } }

    val defaultBackground = MaterialTheme.colorScheme.background
    val background = remember { mutableStateOf(defaultBackground) }
    val tags = remember(noteEvent) { noteEvent.tags()?.toImmutableListOfLists() ?: EmptyTagList }

    Row(modifier = Modifier.padding(top = 5.dp)) {
        Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            media?.let { media ->
                Row(
                    verticalAlignment = CenterVertically,
                ) {
                    VideoView(
                        videoUri = media,
                        waveform = waveform,
                        title = noteEvent.subject(),
                        authorName = note.author?.toBestDisplayName(),
                        roundedCorner = true,
                        accountViewModel = accountViewModel,
                        nostrUriCallback = note.toNostrUri(),
                    )
                }
            }

            content?.let {
                Row(
                    verticalAlignment = CenterVertically,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(top = 5.dp),
                ) {
                    TranslatableRichTextViewer(
                        content = it,
                        canPreview = true,
                        tags = tags,
                        backgroundColor = background,
                        accountViewModel = accountViewModel,
                        nav = nav,
                    )
                }
            }

            if (noteEvent.hasHashtags()) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = CenterVertically) {
                    val hashtags = remember(noteEvent) { noteEvent.hashtags().toImmutableList() }
                    DisplayUncitedHashtags(hashtags, content ?: "", nav)
                }
            }
        }
    }
}

@Composable
fun RenderGitPatchEvent(
    baseNote: Note,
    makeItShort: Boolean,
    canPreview: Boolean,
    backgroundColor: MutableState<Color>,
    accountViewModel: AccountViewModel,
    nav: (String) -> Unit,
) {
    val event = baseNote.event as? GitPatchEvent ?: return

    RenderGitPatchEvent(event, baseNote, makeItShort, canPreview, backgroundColor, accountViewModel, nav)
}

@Composable
private fun RenderShortRepositoryHeader(
    baseNote: AddressableNote,
    accountViewModel: AccountViewModel,
    nav: (String) -> Unit,
) {
    val noteState by baseNote.live().metadata.observeAsState()
    val noteEvent = noteState?.note?.event as? GitRepositoryEvent ?: return

    Column(
        modifier = MaterialTheme.colorScheme.replyModifier.padding(10.dp),
    ) {
        val title = remember(noteEvent) { noteEvent.name() ?: noteEvent.dTag() }
        Text(
            text = stringResource(id = R.string.git_repository, title),
            style = MaterialTheme.typography.titleLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth(),
        )

        noteEvent.description()?.let {
            Spacer(modifier = DoubleVertSpacer)
            Text(
                text = it,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun RenderGitPatchEvent(
    noteEvent: GitPatchEvent,
    note: Note,
    makeItShort: Boolean,
    canPreview: Boolean,
    backgroundColor: MutableState<Color>,
    accountViewModel: AccountViewModel,
    nav: (String) -> Unit,
) {
    val repository = remember(noteEvent) { noteEvent.repository() }

    if (repository != null) {
        LoadAddressableNote(aTag = repository, accountViewModel = accountViewModel) {
            if (it != null) {
                RenderShortRepositoryHeader(it, accountViewModel, nav)
                Spacer(modifier = DoubleVertSpacer)
            }
        }
    }

    LoadDecryptedContent(note, accountViewModel) { body ->
        val eventContent by
            remember(note.event) {
                derivedStateOf {
                    val subject = (note.event as? TextNoteEvent)?.subject()?.ifEmpty { null }

                    if (!subject.isNullOrBlank() && !body.split("\n")[0].contains(subject)) {
                        "### $subject\n$body"
                    } else {
                        body
                    }
                }
            }

        val isAuthorTheLoggedUser = remember(note.event) { accountViewModel.isLoggedUser(note.author) }

        if (makeItShort && isAuthorTheLoggedUser) {
            Text(
                text = eventContent,
                color = MaterialTheme.colorScheme.placeholderText,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        } else {
            SensitivityWarning(
                note = note,
                accountViewModel = accountViewModel,
            ) {
                val modifier = remember(note) { Modifier.fillMaxWidth() }
                val tags = remember(note) { note.event?.tags()?.toImmutableListOfLists() ?: EmptyTagList }

                TranslatableRichTextViewer(
                    content = eventContent,
                    canPreview = canPreview && !makeItShort,
                    modifier = modifier,
                    tags = tags,
                    backgroundColor = backgroundColor,
                    accountViewModel = accountViewModel,
                    nav = nav,
                )
            }

            if (note.event?.hasHashtags() == true) {
                val hashtags =
                    remember(note.event) { note.event?.hashtags()?.toImmutableList() ?: persistentListOf() }
                DisplayUncitedHashtags(hashtags, eventContent, nav)
            }
        }
    }
}

@Composable
fun RenderGitRepositoryEvent(
    baseNote: Note,
    accountViewModel: AccountViewModel,
    nav: (String) -> Unit,
) {
    val event = baseNote.event as? GitRepositoryEvent ?: return

    RenderGitRepositoryEvent(event, baseNote, accountViewModel, nav)
}

@Composable
private fun RenderGitRepositoryEvent(
    noteEvent: GitRepositoryEvent,
    note: Note,
    accountViewModel: AccountViewModel,
    nav: (String) -> Unit,
) {
    val title = remember(noteEvent) { noteEvent.name() ?: noteEvent.dTag() }
    val summary = remember(noteEvent) { noteEvent.description() }
    val web = remember(noteEvent) { noteEvent.web() }
    val clone = remember(noteEvent) { noteEvent.clone() }

    Row(
        modifier =
            Modifier
                .clip(shape = QuoteBorder)
                .border(
                    1.dp,
                    MaterialTheme.colorScheme.subtleBorder,
                    QuoteBorder,
                ).padding(Size10dp),
    ) {
        Column {
            Text(
                text = stringResource(id = R.string.git_repository, title),
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth(),
            )

            summary?.let {
                Text(
                    text = it,
                    modifier = Modifier.fillMaxWidth().padding(vertical = Size5dp),
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            HorizontalDivider(thickness = DividerThickness)

            web?.let {
                Row(Modifier.fillMaxWidth().padding(top = Size5dp)) {
                    Text(
                        text = stringResource(id = R.string.git_web_address),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(modifier = StdHorzSpacer)
                    ClickableUrl(
                        url = it,
                        urlText = it.removePrefix("https://").removePrefix("http://"),
                    )
                }
            }

            clone?.let {
                Row(Modifier.fillMaxWidth().padding(top = Size5dp)) {
                    Text(
                        text = stringResource(id = R.string.git_clone_address),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(modifier = StdHorzSpacer)
                    ClickableUrl(
                        url = it,
                        urlText = it.removePrefix("https://").removePrefix("http://"),
                    )
                }
            }
        }
    }
}

@Composable
fun RenderLiveActivityEvent(
    baseNote: Note,
    accountViewModel: AccountViewModel,
    nav: (String) -> Unit,
) {
    Row(modifier = Modifier.padding(top = 5.dp)) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            RenderLiveActivityEventInner(baseNote = baseNote, accountViewModel, nav)
        }
    }
}

@Composable
fun RenderLiveActivityEventInner(
    baseNote: Note,
    accountViewModel: AccountViewModel,
    nav: (String) -> Unit,
) {
    val noteEvent = baseNote.event as? LiveActivitiesEvent ?: return

    val eventUpdates by baseNote.live().metadata.observeAsState()

    val media = remember(eventUpdates) { noteEvent.streaming() }
    val cover = remember(eventUpdates) { noteEvent.image() }
    val subject = remember(eventUpdates) { noteEvent.title() }
    val content = remember(eventUpdates) { noteEvent.summary() }
    val participants = remember(eventUpdates) { noteEvent.participants() }
    val status = remember(eventUpdates) { noteEvent.status() }
    val starts = remember(eventUpdates) { noteEvent.starts() }

    Row(
        verticalAlignment = CenterVertically,
        modifier =
            Modifier
                .padding(vertical = 5.dp)
                .fillMaxWidth(),
    ) {
        subject?.let {
            Text(
                text = it,
                fontWeight = FontWeight.Bold,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
        }

        Spacer(modifier = StdHorzSpacer)

        Crossfade(targetState = status, label = "RenderLiveActivityEventInner") {
            when (it) {
                STATUS_LIVE -> {
                    media?.let { CrossfadeCheckIfUrlIsOnline(it, accountViewModel) { LiveFlag() } }
                }
                STATUS_PLANNED -> {
                    ScheduledFlag(starts)
                }
            }
        }
    }

    var participantUsers by remember {
        mutableStateOf<ImmutableList<Pair<Participant, User>>>(
            persistentListOf(),
        )
    }

    LaunchedEffect(key1 = eventUpdates) {
        accountViewModel.loadParticipants(participants) { newParticipantUsers ->
            if (!equalImmutableLists(newParticipantUsers, participantUsers)) {
                participantUsers = newParticipantUsers
            }
        }
    }

    media?.let { media ->
        if (status == STATUS_LIVE) {
            CheckIfUrlIsOnline(media, accountViewModel) { isOnline ->
                if (isOnline) {
                    Row(
                        verticalAlignment = CenterVertically,
                    ) {
                        VideoView(
                            videoUri = media,
                            title = subject,
                            artworkUri = cover,
                            authorName = baseNote.author?.toBestDisplayName(),
                            roundedCorner = true,
                            accountViewModel = accountViewModel,
                            nostrUriCallback = "nostr:${baseNote.toNEvent()}",
                        )
                    }
                } else {
                    Row(
                        verticalAlignment = CenterVertically,
                        modifier =
                            Modifier
                                .padding(10.dp)
                                .height(100.dp),
                    ) {
                        Text(
                            text = stringResource(id = R.string.live_stream_is_offline),
                            color = MaterialTheme.colorScheme.onBackground,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
        } else if (status == STATUS_ENDED) {
            Row(
                verticalAlignment = CenterVertically,
                modifier =
                    Modifier
                        .padding(10.dp)
                        .height(100.dp),
            ) {
                Text(
                    text = stringResource(id = R.string.live_stream_has_ended),
                    color = MaterialTheme.colorScheme.onBackground,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }

    participantUsers.forEach {
        Row(
            verticalAlignment = CenterVertically,
            modifier =
                Modifier
                    .padding(vertical = 5.dp)
                    .clickable { nav("User/${it.second.pubkeyHex}") },
        ) {
            ClickableUserPicture(it.second, 25.dp, accountViewModel)
            Spacer(StdHorzSpacer)
            UsernameDisplay(it.second, Modifier.weight(1f))
            Spacer(StdHorzSpacer)
            it.first.role?.let {
                Text(
                    text = it.capitalize(Locale.ROOT),
                    color = MaterialTheme.colorScheme.placeholderText,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun LongFormHeader(
    noteEvent: LongTextNoteEvent,
    note: Note,
    accountViewModel: AccountViewModel,
) {
    val image = remember(noteEvent) { noteEvent.image() }
    val title = remember(noteEvent) { noteEvent.title() }
    val summary =
        remember(noteEvent) {
            noteEvent.summary()?.ifBlank { null } ?: noteEvent.content.take(200).ifBlank { null }
        }

    Row(
        modifier =
            Modifier
                .padding(top = Size5dp)
                .clip(shape = QuoteBorder)
                .border(
                    1.dp,
                    MaterialTheme.colorScheme.subtleBorder,
                    QuoteBorder,
                ),
    ) {
        Column {
            val automaticallyShowUrlPreview = remember { accountViewModel.settings.showUrlPreview.value }

            if (automaticallyShowUrlPreview) {
                image?.let {
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
                    ?: CreateImageHeader(note, accountViewModel)
            }

            title?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(start = 10.dp, end = 10.dp, top = 10.dp),
                )
            }

            summary?.let {
                Spacer(modifier = StdVertSpacer)
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(start = 10.dp, end = 10.dp, bottom = 10.dp),
                    color = Color.Gray,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun RenderWikiContent(
    note: Note,
    accountViewModel: AccountViewModel,
    nav: (String) -> Unit,
) {
    val noteEvent = note.event as? WikiNoteEvent ?: return

    WikiNoteHeader(noteEvent, note, accountViewModel, nav)
}

@Composable
private fun WikiNoteHeader(
    noteEvent: WikiNoteEvent,
    note: Note,
    accountViewModel: AccountViewModel,
    nav: (String) -> Unit,
) {
    val title = remember(noteEvent) { noteEvent.title() }
    val summary =
        remember(noteEvent) {
            noteEvent.summary()?.ifBlank { null } ?: noteEvent.content.take(200).ifBlank { null }
        }
    val image = remember(noteEvent) { noteEvent.image() }

    Row(
        modifier =
            Modifier
                .padding(top = Size5dp)
                .clip(shape = QuoteBorder)
                .border(
                    1.dp,
                    MaterialTheme.colorScheme.subtleBorder,
                    QuoteBorder,
                ),
    ) {
        Column {
            val automaticallyShowUrlPreview = remember { accountViewModel.settings.showUrlPreview.value }

            if (automaticallyShowUrlPreview) {
                image?.let {
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
                    ?: CreateImageHeader(note, accountViewModel)
            }

            title?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(start = 10.dp, end = 10.dp, top = 10.dp),
                )
            }

            summary?.let {
                Spacer(modifier = StdVertSpacer)
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(start = 10.dp, end = 10.dp, bottom = 10.dp),
                    color = Color.Gray,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun RenderClassifieds(
    noteEvent: ClassifiedsEvent,
    note: Note,
    accountViewModel: AccountViewModel,
    nav: (String) -> Unit,
) {
    val image = remember(noteEvent) { noteEvent.image() }
    val title = remember(noteEvent) { noteEvent.title() }
    val summary =
        remember(noteEvent) { noteEvent.summary() ?: noteEvent.content.take(200).ifBlank { null } }
    val price = remember(noteEvent) { noteEvent.price() }
    val location = remember(noteEvent) { noteEvent.location() }

    Row(
        modifier =
            Modifier
                .clip(shape = QuoteBorder)
                .border(
                    1.dp,
                    MaterialTheme.colorScheme.subtleBorder,
                    QuoteBorder,
                ),
    ) {
        Column {
            Row {
                image?.let {
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
                    ?: CreateImageHeader(note, accountViewModel)
            }

            Row(
                Modifier.padding(start = 10.dp, end = 10.dp, top = 10.dp),
                verticalAlignment = CenterVertically,
            ) {
                title?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodyLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                }

                price?.let {
                    val priceTag =
                        remember(noteEvent) {
                            val newAmount =
                                price.amount.toBigDecimalOrNull()?.let { showAmount(it) } ?: price.amount

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
                        modifier =
                            remember {
                                Modifier
                                    .clip(SmallBorder)
                                    .padding(start = 5.dp)
                            },
                    )
                }
            }

            if (summary != null || location != null) {
                Row(
                    Modifier.padding(start = 10.dp, end = 10.dp, top = 5.dp),
                    verticalAlignment = CenterVertically,
                ) {
                    summary?.let {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.weight(1f),
                            color = Color.Gray,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }

          /*
          Column {
              location?.let {
                  Text(
                      text = it,
                      style = MaterialTheme.typography.bodySmall,
                      color = Color.Gray,
                      maxLines = 1,
                      overflow = TextOverflow.Ellipsis,
                      modifier = Modifier.padding(start = 5.dp)
                  )
              }

              Button(
                  modifier = Modifier
                      .padding(horizontal = 3.dp)
                      .width(50.dp),
                  onClick = {
                      note.author?.let {
                          accountViewModel.createChatRoomFor(it) {
                              nav("Room/$it")
                          }
                      }
                  },
                  contentPadding = ZeroPadding,
                  colors = ButtonDefaults
                      .buttonColors(
                          containerColor = MaterialTheme.colorScheme.primary
                      )
              ) {
                  Icon(
                      painter = painterResource(R.drawable.ic_dm),
                      stringResource(R.string.send_a_direct_message),
                      modifier = Modifier.size(20.dp),
                      tint = Color.White
                  )
              }
          }

           */
                }
            }

            Spacer(modifier = DoubleVertSpacer)
        }
    }
}

@Composable
fun CreateImageHeader(
    note: Note,
    accountViewModel: AccountViewModel,
) {
    val banner = remember(note.author?.info) { note.author?.info?.banner }

    Box {
        banner?.let {
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
            ?: Image(
                painter = painterResource(R.drawable.profile_banner),
                contentDescription = stringResource(R.string.profile_banner),
                contentScale = ContentScale.FillWidth,
                modifier =
                    remember {
                        Modifier
                            .fillMaxWidth()
                            .height(150.dp)
                    },
            )

        Box(
            remember {
                Modifier
                    .width(75.dp)
                    .height(75.dp)
                    .padding(10.dp)
                    .align(Alignment.BottomStart)
            },
        ) {
            NoteAuthorPicture(baseNote = note, accountViewModel = accountViewModel, size = Size55dp)
        }
    }
}

@Preview
@Composable
fun RenderEyeGlassesPrescriptionPreview() {
    val prescriptionEvent = Event.fromJson("{\"id\":\"0c15d2bc6f7dcc42fa4426d35d30d09840c9afa5b46d100415006e41d6471416\",\"pubkey\":\"bcd4715cc34f98dce7b52fddaf1d826e5ce0263479b7e110a5bd3c3789486ca8\",\"created_at\":1709074097,\"kind\":82,\"tags\":[],\"content\":\"{\\\"resourceType\\\":\\\"Bundle\\\",\\\"id\\\":\\\"bundle-vision-test\\\",\\\"type\\\":\\\"document\\\",\\\"entry\\\":[{\\\"resourceType\\\":\\\"Practitioner\\\",\\\"id\\\":\\\"2\\\",\\\"active\\\":true,\\\"name\\\":[{\\\"use\\\":\\\"official\\\",\\\"family\\\":\\\"Careful\\\",\\\"given\\\":[\\\"Adam\\\"]}],\\\"gender\\\":\\\"male\\\"},{\\\"resourceType\\\":\\\"Patient\\\",\\\"id\\\":\\\"1\\\",\\\"active\\\":true,\\\"name\\\":[{\\\"use\\\":\\\"official\\\",\\\"family\\\":\\\"Duck\\\",\\\"given\\\":[\\\"Donald\\\"]}],\\\"gender\\\":\\\"male\\\"},{\\\"resourceType\\\":\\\"VisionPrescription\\\",\\\"status\\\":\\\"active\\\",\\\"created\\\":\\\"2014-06-15\\\",\\\"patient\\\":{\\\"reference\\\":\\\"#1\\\"},\\\"dateWritten\\\":\\\"2014-06-15\\\",\\\"prescriber\\\":{\\\"reference\\\":\\\"#2\\\"},\\\"lensSpecification\\\":[{\\\"eye\\\":\\\"right\\\",\\\"sphere\\\":-2,\\\"prism\\\":[{\\\"amount\\\":0.5,\\\"base\\\":\\\"down\\\"}],\\\"add\\\":2},{\\\"eye\\\":\\\"left\\\",\\\"sphere\\\":-1,\\\"cylinder\\\":-0.5,\\\"axis\\\":180,\\\"prism\\\":[{\\\"amount\\\":0.5,\\\"base\\\":\\\"up\\\"}],\\\"add\\\":2}]}]}\",\"sig\":\"dc58f6109111ca06920c0c711aeaf8e2ee84975afa60d939828d4e01e2edea738f735fb5b1fcadf6d5496e36ac429abf7020a55fd1e4ed215738afc8d07cb950\"}") as FhirResourceEvent

    RenderFhirResource(prescriptionEvent)
}

@Composable
fun RenderFhirResource(
    baseNote: Note,
    accountViewModel: AccountViewModel,
    nav: (String) -> Unit,
) {
    val event = baseNote.event as? FhirResourceEvent ?: return

    RenderFhirResource(event)
}

@Composable
fun RenderFhirResource(event: FhirResourceEvent) {
    val state by produceState(initialValue = FhirElementDatabase(), key1 = event) {
        withContext(Dispatchers.Default) {
            parseResourceBundleOrNull(event.content)?.let {
                value = it
            }
        }
    }

    state.baseResource?.let { resource ->
        when (resource) {
            is Bundle -> {
                val vision = resource.entry.filterIsInstance(VisionPrescription::class.java)

                vision.firstOrNull()?.let {
                    RenderEyeGlassesPrescription(it, state.localDb)
                }
            }
            is VisionPrescription -> {
                RenderEyeGlassesPrescription(resource, state.localDb)
            }
            else -> {
            }
        }
    }
}

@Composable
fun RenderEyeGlassesPrescription(
    visionPrescription: VisionPrescription,
    db: ImmutableMap<String, Resource>,
) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = Size10dp),
    ) {
        val rightEye = visionPrescription.lensSpecification.firstOrNull { it.eye == "right" }
        val leftEye = visionPrescription.lensSpecification.firstOrNull { it.eye == "left" }

        Text(
            "Eyeglasses Prescription",
            modifier = Modifier.padding(4.dp).fillMaxWidth(),
            textAlign = TextAlign.Center,
        )

        Spacer(StdVertSpacer)

        visionPrescription.patient?.reference?.let {
            val patient = findReferenceInDb(it, db) as? Patient

            patient?.name?.firstOrNull()?.assembleName()?.let {
                Text(
                    text = "Patient: $it",
                    modifier = Modifier.padding(4.dp).fillMaxWidth(),
                )
            }
        }
        visionPrescription.status?.let {
            Text(
                text = "Status: ${it.capitalize()}",
                modifier = Modifier.padding(4.dp).fillMaxWidth(),
            )
        }

        Spacer(DoubleVertSpacer)

        RenderEyeGlassesPrescriptionHeaderRow()
        HorizontalDivider(thickness = DividerThickness)

        rightEye?.let {
            RenderEyeGlassesPrescriptionRow(data = it)
            HorizontalDivider(thickness = DividerThickness)
        }

        leftEye?.let {
            RenderEyeGlassesPrescriptionRow(data = it)
            HorizontalDivider(thickness = DividerThickness)
        }

        visionPrescription.prescriber?.reference?.let {
            val practitioner = findReferenceInDb(it, db) as? Practitioner

            practitioner?.name?.firstOrNull()?.assembleName()?.let {
                Spacer(DoubleVertSpacer)
                Text(
                    text = "Signed by: $it",
                    modifier = Modifier.padding(4.dp).fillMaxWidth(),
                    textAlign = TextAlign.Right,
                )
            }
        }
    }
}

@Composable
fun RenderEyeGlassesPrescriptionHeaderRow() {
    Row(
        modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = "Eye",
            modifier = Modifier.padding(4.dp).weight(1f),
        )
        VerticalDivider(thickness = DividerThickness)
        Text(
            text = "Sph",
            textAlign = TextAlign.Right,
            modifier = Modifier.padding(4.dp).weight(1f),
        )
        Text(
            text = "Cyl",
            textAlign = TextAlign.Right,
            modifier = Modifier.padding(4.dp).weight(1f),
        )
        Text(
            text = "Axis",
            textAlign = TextAlign.Right,
            modifier = Modifier.padding(4.dp).weight(1f),
        )
        VerticalDivider(thickness = DividerThickness)
        Text(
            text = "Add",
            textAlign = TextAlign.Right,
            modifier = Modifier.padding(4.dp).weight(1f),
        )
    }
}

@Composable
fun RenderEyeGlassesPrescriptionRow(data: LensSpecification) {
    Row(
        modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val numberFormat = DecimalFormat("##.00")
        val integerFormat = DecimalFormat("###")

        Text(
            text = data.eye?.capitalize() ?: "Unknown",
            modifier = Modifier.padding(4.dp).weight(1f),
        )
        VerticalDivider(thickness = DividerThickness)
        Text(
            text = formatOrBlank(data.sphere, numberFormat),
            textAlign = TextAlign.Right,
            modifier = Modifier.padding(4.dp).weight(1f),
        )
        Text(
            text = formatOrBlank(data.cylinder, numberFormat),
            textAlign = TextAlign.Right,
            modifier = Modifier.padding(4.dp).weight(1f),
        )
        Text(
            text = formatOrBlank(data.axis, integerFormat),
            textAlign = TextAlign.Right,
            modifier = Modifier.padding(4.dp).weight(1f),
        )
        VerticalDivider(thickness = DividerThickness)
        Text(
            text = formatOrBlank(data.add, numberFormat),
            textAlign = TextAlign.Right,
            modifier = Modifier.padding(4.dp).weight(1f),
        )
    }
}

fun formatOrBlank(
    amount: Double?,
    numberFormat: NumberFormat,
): String {
    if (amount == null) return ""
    if (Math.abs(amount) < 0.01) return ""
    return numberFormat.format(amount)
}
