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
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowForwardIos
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Alignment.Companion.BottomEnd
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import androidx.core.graphics.get
import androidx.lifecycle.distinctUntilChanged
import androidx.lifecycle.map
import coil.compose.AsyncImage
import coil.compose.AsyncImagePainter
import coil.request.SuccessResult
import com.fonfon.kgeohash.GeoHash
import com.fonfon.kgeohash.toGeoHash
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.model.AddressableNote
import com.vitorpamplona.amethyst.model.Channel
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.model.RelayBriefInfo
import com.vitorpamplona.amethyst.model.User
import com.vitorpamplona.amethyst.service.ReverseGeoLocationUtil
import com.vitorpamplona.amethyst.ui.actions.NewRelayListView
import com.vitorpamplona.amethyst.ui.components.ClickableUrl
import com.vitorpamplona.amethyst.ui.components.CreateClickableTextWithEmoji
import com.vitorpamplona.amethyst.ui.components.CreateTextWithEmoji
import com.vitorpamplona.amethyst.ui.components.LoadNote
import com.vitorpamplona.amethyst.ui.components.LoadThumbAndThenVideoView
import com.vitorpamplona.amethyst.ui.components.MeasureSpaceWidth
import com.vitorpamplona.amethyst.ui.components.ObserveDisplayNip05Status
import com.vitorpamplona.amethyst.ui.components.RobohashAsyncImageProxy
import com.vitorpamplona.amethyst.ui.components.SensitivityWarning
import com.vitorpamplona.amethyst.ui.components.ShowMoreButton
import com.vitorpamplona.amethyst.ui.components.TranslatableRichTextViewer
import com.vitorpamplona.amethyst.ui.components.VideoView
import com.vitorpamplona.amethyst.ui.components.ZoomableContent
import com.vitorpamplona.amethyst.ui.components.ZoomableContentView
import com.vitorpamplona.amethyst.ui.components.ZoomableImageDialog
import com.vitorpamplona.amethyst.ui.components.ZoomableLocalImage
import com.vitorpamplona.amethyst.ui.components.ZoomableLocalVideo
import com.vitorpamplona.amethyst.ui.components.ZoomableUrlImage
import com.vitorpamplona.amethyst.ui.components.ZoomableUrlVideo
import com.vitorpamplona.amethyst.ui.components.figureOutMimeType
import com.vitorpamplona.amethyst.ui.components.imageExtensions
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
import com.vitorpamplona.amethyst.ui.theme.BitcoinOrange
import com.vitorpamplona.amethyst.ui.theme.ButtonBorder
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
import com.vitorpamplona.amethyst.ui.theme.Size18Modifier
import com.vitorpamplona.amethyst.ui.theme.Size20Modifier
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
import com.vitorpamplona.amethyst.ui.theme.grayText
import com.vitorpamplona.amethyst.ui.theme.lessImportantLink
import com.vitorpamplona.amethyst.ui.theme.mediumImportanceLink
import com.vitorpamplona.amethyst.ui.theme.newItemBackgroundColor
import com.vitorpamplona.amethyst.ui.theme.placeholderText
import com.vitorpamplona.amethyst.ui.theme.replyBackground
import com.vitorpamplona.amethyst.ui.theme.replyModifier
import com.vitorpamplona.amethyst.ui.theme.subtleBorder
import com.vitorpamplona.quartz.encoders.ATag
import com.vitorpamplona.quartz.encoders.HexKey
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
import com.vitorpamplona.quartz.events.ChatroomKey
import com.vitorpamplona.quartz.events.ChatroomKeyable
import com.vitorpamplona.quartz.events.ClassifiedsEvent
import com.vitorpamplona.quartz.events.CommunityDefinitionEvent
import com.vitorpamplona.quartz.events.CommunityPostApprovalEvent
import com.vitorpamplona.quartz.events.EmojiPackEvent
import com.vitorpamplona.quartz.events.EmojiPackSelectionEvent
import com.vitorpamplona.quartz.events.EmojiUrl
import com.vitorpamplona.quartz.events.EmptyTagList
import com.vitorpamplona.quartz.events.EventInterface
import com.vitorpamplona.quartz.events.FileHeaderEvent
import com.vitorpamplona.quartz.events.FileStorageHeaderEvent
import com.vitorpamplona.quartz.events.GenericRepostEvent
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
import com.vitorpamplona.quartz.events.UserMetadata
import com.vitorpamplona.quartz.events.toImmutableListOfLists
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import java.math.BigDecimal
import java.net.URL
import java.net.URLEncoder
import java.util.Locale
import kotlin.time.measureTimedValue

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
    nav: (String) -> Unit
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
                nav = nav
            )
        } else {
            LongPressToQuickAction(baseNote = baseNote, accountViewModel = accountViewModel) { showPopup ->
                BlankNote(
                    remember {
                        modifier.combinedClickable(
                            onClick = { },
                            onLongClick = showPopup
                        )
                    },
                    isBoostedNote || isQuotedNote
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
    nav: (String) -> Unit
) {
    if (showHidden) {
        // Ignores reports as well
        val state by remember {
            mutableStateOf(
                AccountViewModel.NoteComposeReportState()
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
            nav = nav
        )
    } else {
        val isHidden by remember(note) {
            accountViewModel.account.liveHiddenUsers.map {
                note.isHiddenFor(it)
            }.distinctUntilChanged()
        }.observeAsState(accountViewModel.isNoteHidden(note))

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
                    nav = nav
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
    nav: (String) -> Unit
) {
    var state by remember {
        mutableStateOf(
            AccountViewModel.NoteComposeReportState()
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
            nav
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
    nav: (String) -> Unit
) {
    var showReportedNote by remember { mutableStateOf(false) }

    Crossfade(targetState = !state.isAcceptable && !showReportedNote, label = "RenderReportState") { showHiddenNote ->
        if (showHiddenNote) {
            HiddenNote(
                state.relevantReports,
                state.isHiddenAuthor,
                accountViewModel,
                modifier,
                isBoostedNote,
                nav,
                onClick = { showReportedNote = true }
            )
        } else {
            val canPreview = (!state.isAcceptable && showReportedNote) || state.canPreview

            NormalNote(
                note,
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
                nav
            )
        }
    }
}

@Composable
fun WatchForReports(
    note: Note,
    accountViewModel: AccountViewModel,
    onChange: (AccountViewModel.NoteComposeReportState) -> Unit
) {
    val userFollowsState by accountViewModel.userFollows.observeAsState()
    val noteReportsState by note.live().reports.observeAsState()

    LaunchedEffect(key1 = noteReportsState, key2 = userFollowsState) {
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
    nav: (String) -> Unit
) {
    if (isQuotedNote || isBoostedNote) {
        when (baseNote.event) {
            is ChannelCreateEvent, is ChannelMetadataEvent -> ChannelHeader(
                channelNote = baseNote,
                showVideo = !makeItShort,
                showBottomDiviser = true,
                sendToChannel = true,
                accountViewModel = accountViewModel,
                nav = nav
            )
            is CommunityDefinitionEvent -> (baseNote as? AddressableNote)?.let {
                CommunityHeader(
                    baseNote = it,
                    showBottomDiviser = true,
                    sendToCommunity = true,
                    accountViewModel = accountViewModel,
                    nav = nav
                )
            }
            is BadgeDefinitionEvent -> BadgeDisplay(baseNote = baseNote)
            else ->
                LongPressToQuickAction(baseNote = baseNote, accountViewModel = accountViewModel) { showPopup ->
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
                        nav
                    )
                }
        }
    } else {
        when (baseNote.event) {
            is ChannelCreateEvent, is ChannelMetadataEvent -> ChannelHeader(
                channelNote = baseNote,
                showVideo = !makeItShort,
                showBottomDiviser = true,
                sendToChannel = true,
                accountViewModel = accountViewModel,
                nav = nav
            )
            is CommunityDefinitionEvent -> (baseNote as? AddressableNote)?.let {
                CommunityHeader(
                    baseNote = it,
                    showBottomDiviser = true,
                    sendToCommunity = true,
                    accountViewModel = accountViewModel,
                    nav = nav
                )
            }
            is BadgeDefinitionEvent -> BadgeDisplay(baseNote = baseNote)
            is FileHeaderEvent -> FileHeaderDisplay(baseNote, false, accountViewModel)
            is FileStorageHeaderEvent -> FileStorageHeaderDisplay(baseNote, false, accountViewModel)
            else ->
                LongPressToQuickAction(baseNote = baseNote, accountViewModel = accountViewModel) { showPopup ->
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
                        nav
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
    nav: (String) -> Unit
) {
    val expanded = remember { mutableStateOf(false) }

    Column(Modifier.fillMaxWidth()) {
        Column(
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.clickable {
                if (sendToCommunity) {
                    routeFor(baseNote, accountViewModel.userProfile())?.let {
                        nav(it)
                    }
                } else {
                    expanded.value = !expanded.value
                }
            }
        ) {
            ShortCommunityHeader(
                baseNote = baseNote,
                accountViewModel = accountViewModel,
                nav = nav
            )

            if (expanded.value) {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    LongCommunityHeader(
                        baseNote = baseNote,
                        lineModifier = modifier,
                        accountViewModel = accountViewModel,
                        nav = nav
                    )
                }
            }
        }

        if (showBottomDiviser) {
            Divider(
                thickness = DividerThickness
            )
        }
    }
}

@Composable
fun LongCommunityHeader(
    baseNote: AddressableNote,
    lineModifier: Modifier = Modifier.padding(horizontal = Size10dp, vertical = Size5dp),
    accountViewModel: AccountViewModel,
    nav: (String) -> Unit
) {
    val noteState by baseNote.live().metadata.observeAsState()
    val noteEvent = remember(noteState) { noteState?.note?.event as? CommunityDefinitionEvent } ?: return

    Row(
        lineModifier
    ) {
        val rulesLabel = stringResource(id = R.string.rules)
        val summary = remember(noteState) {
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
            Modifier.weight(1f)
        ) {
            Row(verticalAlignment = CenterVertically) {
                val defaultBackground = MaterialTheme.colorScheme.background
                val background = remember {
                    mutableStateOf(defaultBackground)
                }

                TranslatableRichTextViewer(
                    content = summary ?: stringResource(id = R.string.community_no_descriptor),
                    canPreview = false,
                    tags = EmptyTagList,
                    backgroundColor = background,
                    accountViewModel = accountViewModel,
                    nav = nav
                )
            }

            val hashtags = remember(noteEvent) { noteEvent.hashtags().toImmutableList() }
            DisplayUncitedHashtags(hashtags, summary ?: "", nav)
        }

        Column() {
            Row() {
                Spacer(DoubleHorzSpacer)
                LongCommunityActionOptions(baseNote, accountViewModel, nav)
            }
        }
    }

    Row(
        lineModifier,
        verticalAlignment = CenterVertically
    ) {
        Text(
            text = stringResource(id = R.string.owner),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.width(75.dp)
        )
        Spacer(DoubleHorzSpacer)
        NoteAuthorPicture(baseNote, nav, accountViewModel, Size25dp)
        Spacer(DoubleHorzSpacer)
        NoteUsernameDisplay(baseNote, remember { Modifier.weight(1f) })
    }

    var participantUsers by remember(baseNote) {
        mutableStateOf<ImmutableList<Pair<Participant, User>>>(
            persistentListOf()
        )
    }

    LaunchedEffect(key1 = noteState) {
        val participants = (noteState?.note?.event as? CommunityDefinitionEvent)?.moderators()

        if (participants != null) {
            accountViewModel.loadParticipants(participants) { newParticipantUsers ->
                if (newParticipantUsers != null && !equalImmutableLists(newParticipantUsers, participantUsers)) {
                    participantUsers = newParticipantUsers
                }
            }
        }
    }

    participantUsers.forEach {
        Row(
            lineModifier.clickable {
                nav("User/${it.second.pubkeyHex}")
            },
            verticalAlignment = CenterVertically
        ) {
            it.first.role?.let { it1 ->
                Text(
                    text = it1.capitalize(Locale.ROOT),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.width(75.dp)
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
        verticalAlignment = CenterVertically
    ) {
        Text(
            text = stringResource(id = R.string.created_at),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.width(75.dp)
        )
        Spacer(DoubleHorzSpacer)
        NormalTimeAgo(baseNote = baseNote, Modifier.weight(1f))
        MoreOptionsButton(baseNote, accountViewModel)
    }
}

@Composable
fun ShortCommunityHeader(baseNote: AddressableNote, accountViewModel: AccountViewModel, nav: (String) -> Unit) {
    val noteState by baseNote.live().metadata.observeAsState()
    val noteEvent = remember(noteState) { noteState?.note?.event as? CommunityDefinitionEvent } ?: return

    val automaticallyShowProfilePicture = remember {
        accountViewModel.settings.showProfilePictures.value
    }

    Row(verticalAlignment = CenterVertically) {
        noteEvent.image()?.let {
            RobohashAsyncImageProxy(
                robot = baseNote.idHex,
                model = it,
                contentDescription = stringResource(R.string.profile_image),
                contentScale = ContentScale.Crop,
                modifier = HeaderPictureModifier,
                loadProfilePicture = automaticallyShowProfilePicture
            )
        }

        Column(
            modifier = Modifier
                .padding(start = 10.dp)
                .height(Size35dp)
                .weight(1f),
            verticalArrangement = Arrangement.Center
        ) {
            Row(verticalAlignment = CenterVertically) {
                Text(
                    text = remember(noteState) { noteEvent.dTag() },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        Row(
            modifier = Modifier
                .height(Size35dp)
                .padding(start = 5.dp),
            verticalAlignment = CenterVertically
        ) {
            ShortCommunityActionOptions(baseNote, accountViewModel, nav)
        }
    }
}

@Composable
private fun ShortCommunityActionOptions(
    note: AddressableNote,
    accountViewModel: AccountViewModel,
    nav: (String) -> Unit
) {
    Spacer(modifier = StdHorzSpacer)
    LikeReaction(baseNote = note, grayTint = MaterialTheme.colorScheme.onSurface, accountViewModel = accountViewModel, nav = nav)
    Spacer(modifier = StdHorzSpacer)
    ZapReaction(baseNote = note, grayTint = MaterialTheme.colorScheme.onSurface, accountViewModel = accountViewModel, nav = nav)

    WatchAddressableNoteFollows(note, accountViewModel) { isFollowing ->
        if (!isFollowing) {
            Spacer(modifier = StdHorzSpacer)
            JoinCommunityButton(accountViewModel, note, nav)
        }
    }
}

@Composable
fun WatchAddressableNoteFollows(note: AddressableNote, accountViewModel: AccountViewModel, onFollowChanges: @Composable (Boolean) -> Unit) {
    val showFollowingMark by remember {
        accountViewModel.userFollows.map {
            it.user.latestContactList?.isTaggedAddressableNote(note.idHex) ?: false
        }.distinctUntilChanged()
    }.observeAsState(false)

    onFollowChanges(showFollowingMark)
}

@Composable
private fun LongCommunityActionOptions(
    note: AddressableNote,
    accountViewModel: AccountViewModel,
    nav: (String) -> Unit
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
    nav: (String) -> Unit
) {
    val newItemColor = MaterialTheme.colorScheme.newItemBackgroundColor
    val defaultBackgroundColor = MaterialTheme.colorScheme.background
    val backgroundColor = remember(baseNote) { mutableStateOf<Color>(parentBackgroundColor?.value ?: defaultBackgroundColor) }

    LaunchedEffect(key1 = routeForLastRead, key2 = parentBackgroundColor?.value) {
        routeForLastRead?.let {
            accountViewModel.loadAndMarkAsRead(it, baseNote.createdAt()) { isNew ->
                val newBackgroundColor = if (isNew) {
                    if (parentBackgroundColor != null) {
                        newItemColor.compositeOver(parentBackgroundColor.value)
                    } else {
                        newItemColor.compositeOver(defaultBackgroundColor)
                    }
                } else {
                    parentBackgroundColor?.value ?: defaultBackgroundColor
                }

                if (newBackgroundColor != backgroundColor.value) {
                    launch(Dispatchers.Main) {
                        backgroundColor.value = newBackgroundColor
                    }
                }
            }
        } ?: run {
            val newBackgroundColor = parentBackgroundColor?.value ?: defaultBackgroundColor

            if (newBackgroundColor != backgroundColor.value) {
                launch(Dispatchers.Main) {
                    backgroundColor.value = newBackgroundColor
                }
            }
        }
    }

    ClickableNote(
        baseNote = baseNote,
        backgroundColor = backgroundColor,
        modifier = modifier,
        accountViewModel = accountViewModel,
        showPopup = showPopup,
        nav = nav
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
            nav = nav
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
    content: @Composable () -> Unit
) {
    val scope = rememberCoroutineScope()

    val updatedModifier = remember(baseNote, backgroundColor.value) {
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
                        routeFor(redirectToNote, accountViewModel.userProfile())?.let {
                            nav(it)
                        }
                    }
                },
                onLongClick = showPopup
            )
            .background(backgroundColor.value)
    }

    Column(modifier = updatedModifier) {
        content()
    }
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
    nav: (String) -> Unit
) {
    val notBoostedNorQuote = !isBoostedNote && !isQuotedNote

    Row(
        modifier = remember {
            Modifier
                .fillMaxWidth()
                .padding(
                    start = if (!isBoostedNote) 12.dp else 0.dp,
                    end = if (!isBoostedNote) 12.dp else 0.dp,
                    top = if (addMarginTop && !isBoostedNote) 10.dp else 0.dp
                    // Don't add margin to the bottom because of the Divider down below
                )
        }
    ) {
        if (notBoostedNorQuote) {
            Column(WidthAuthorPictureModifier) {
                val (value, elapsed) = measureTimedValue {
                    AuthorAndRelayInformation(baseNote, accountViewModel, nav)
                }
                Log.d("Rendering Metrics", "Author:   ${baseNote.event?.content()?.split("\n")?.getOrNull(0)?.take(15)}.. $elapsed")
            }
            Spacer(modifier = DoubleHorzSpacer)
        }

        Column(Modifier.fillMaxWidth()) {
            val showSecondRow = baseNote.event !is RepostEvent && baseNote.event !is GenericRepostEvent && !isBoostedNote && !isQuotedNote
            val (value, elapsed) = measureTimedValue {
                NoteBody(
                    baseNote = baseNote,
                    showAuthorPicture = isQuotedNote,
                    unPackReply = unPackReply,
                    makeItShort = makeItShort,
                    canPreview = canPreview,
                    showSecondRow = showSecondRow,
                    backgroundColor = backgroundColor,
                    accountViewModel = accountViewModel,
                    nav = nav
                )
            }
            Log.d("Rendering Metrics", "TextBody: ${baseNote.event?.content()?.split("\n")?.getOrNull(0)?.take(15)}.. $elapsed")
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
            val (value, elapsed) = measureTimedValue {
                ReactionsRow(
                    baseNote = baseNote,
                    showReactionDetail = notBoostedNorQuote,
                    accountViewModel = accountViewModel,
                    nav = nav
                )
            }
            Log.d("Rendering Metrics", "Reaction: ${baseNote.event?.content()?.split("\n")?.getOrNull(0)?.take(15)}.. $elapsed")
        }
    }

    if (notBoostedNorQuote) {
        Divider(
            thickness = DividerThickness
        )
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
    accountViewModel: AccountViewModel,
    nav: (String) -> Unit
) {
    FirstUserInfoRow(
        baseNote = baseNote,
        showAuthorPicture = showAuthorPicture,
        accountViewModel = accountViewModel,
        nav = nav
    )

    if (showSecondRow) {
        SecondUserInfoRow(
            baseNote,
            accountViewModel,
            nav
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
            nav
        )
    }

    RenderNoteRow(
        baseNote,
        backgroundColor,
        makeItShort,
        canPreview,
        accountViewModel,
        nav
    )

    val noteEvent = baseNote.event
    val zapSplits = remember(noteEvent) { noteEvent?.hasZapSplitSetup() ?: false }
    if (zapSplits && noteEvent != null) {
        Spacer(modifier = HalfDoubleVertSpacer)
        DisplayZapSplits(noteEvent, accountViewModel, nav)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DisplayZapSplits(noteEvent: EventInterface, accountViewModel: AccountViewModel, nav: (String) -> Unit) {
    val list = remember(noteEvent) { noteEvent.zapSplitSetup() }

    if (list.isEmpty()) return

    Row(verticalAlignment = CenterVertically) {
        Box(
            Modifier
                .height(20.dp)
                .width(25.dp)
        ) {
            Icon(
                imageVector = Icons.Outlined.Bolt,
                contentDescription = stringResource(id = R.string.zaps),
                modifier = Modifier
                    .size(20.dp)
                    .align(Alignment.CenterStart),
                tint = BitcoinOrange
            )
            Icon(
                imageVector = Icons.Outlined.ArrowForwardIos,
                contentDescription = stringResource(id = R.string.zaps),
                modifier = Modifier
                    .size(13.dp)
                    .align(Alignment.CenterEnd),
                tint = BitcoinOrange
            )
        }

        Spacer(modifier = StdHorzSpacer)

        FlowRow {
            list.forEach {
                if (it.isLnAddress) {
                    ClickableText(
                        text = AnnotatedString(it.lnAddressOrPubKeyHex),
                        onClick = { },
                        style = LocalTextStyle.current.copy(color = MaterialTheme.colorScheme.primary)
                    )
                } else {
                    UserPicture(
                        userHex = it.lnAddressOrPubKeyHex,
                        size = Size25dp,
                        accountViewModel = accountViewModel,
                        nav = nav
                    )
                }
            }
        }
    }
}

@Composable
private fun RenderNoteRow(
    baseNote: Note,
    backgroundColor: MutableState<Color>,
    makeItShort: Boolean,
    canPreview: Boolean,
    accountViewModel: AccountViewModel,
    nav: (String) -> Unit
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

        is BadgeAwardEvent -> {
            RenderBadgeAward(baseNote, backgroundColor, accountViewModel, nav)
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

        is PrivateDmEvent -> {
            RenderPrivateMessage(
                baseNote,
                makeItShort,
                canPreview,
                backgroundColor,
                accountViewModel,
                nav
            )
        }

        is ClassifiedsEvent -> {
            RenderClassifieds(
                noteEvent,
                baseNote,
                accountViewModel
            )
        }

        is HighlightEvent -> {
            RenderHighlight(
                baseNote,
                makeItShort,
                canPreview,
                backgroundColor,
                accountViewModel,
                nav
            )
        }

        is PollNoteEvent -> {
            RenderPoll(
                baseNote,
                makeItShort,
                canPreview,
                backgroundColor,
                accountViewModel,
                nav
            )
        }

        is FileHeaderEvent -> {
            FileHeaderDisplay(baseNote, true, accountViewModel)
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
                nav
            )
        }

        else -> {
            RenderTextEvent(
                baseNote,
                makeItShort,
                canPreview,
                backgroundColor,
                accountViewModel,
                nav
            )
        }
    }
}

fun routeFor(note: Note, loggedIn: User): String? {
    val noteEvent = note.event

    if (noteEvent is ChannelMessageEvent || noteEvent is ChannelCreateEvent || noteEvent is ChannelMetadataEvent) {
        note.channelHex()?.let {
            return "Channel/$it"
        }
    } else if (noteEvent is LiveActivitiesEvent || noteEvent is LiveActivitiesChatMessageEvent) {
        note.channelHex()?.let {
            return "Channel/${URLEncoder.encode(it, "utf-8")}"
        }
    } else if (noteEvent is ChatroomKeyable) {
        val room = noteEvent.chatroomKey(loggedIn.pubkeyHex)
        loggedIn.createChatroom(room)
        return "Room/${room.hashCode()}"
    } else if (noteEvent is CommunityDefinitionEvent) {
        return "Community/${URLEncoder.encode(note.idHex, "utf-8")}"
    } else {
        return "Note/${URLEncoder.encode(note.idHex, "utf-8")}"
    }

    return null
}

fun routeToMessage(user: HexKey, draftMessage: String?, accountViewModel: AccountViewModel): String {
    val withKey = ChatroomKey(persistentSetOf(user))
    accountViewModel.account.userProfile().createChatroom(withKey)
    return if (draftMessage != null) {
        "Room/${withKey.hashCode()}?message=$draftMessage"
    } else {
        "Room/${withKey.hashCode()}"
    }
}

fun routeToMessage(user: User, draftMessage: String?, accountViewModel: AccountViewModel): String {
    return routeToMessage(user.pubkeyHex, draftMessage, accountViewModel)
}

fun routeFor(note: Channel): String {
    return "Channel/${note.idHex}"
}

fun routeFor(user: User): String {
    return "User/${user.pubkeyHex}"
}

fun authorRouteFor(note: Note): String {
    return "User/${note.author?.pubkeyHex}"
}

@Composable
fun LoadDecryptedContent(
    note: Note,
    accountViewModel: AccountViewModel,
    inner: @Composable (String) -> Unit
) {
    var decryptedContent by remember(note.event) {
        mutableStateOf(
            accountViewModel.cachedDecrypt(note)
        )
    }

    decryptedContent?.let {
        inner(it)
    } ?: run {
        LaunchedEffect(key1 = decryptedContent) {
            accountViewModel.decrypt(note) {
                decryptedContent = it
            }
        }
    }
}

@Composable
fun LoadDecryptedContentOrNull(
    note: Note,
    accountViewModel: AccountViewModel,
    inner: @Composable (String?) -> Unit
) {
    var decryptedContent by remember(note.event) {
        mutableStateOf(
            accountViewModel.cachedDecrypt(note)
        )
    }

    if (decryptedContent == null) {
        LaunchedEffect(key1 = decryptedContent) {
            accountViewModel.decrypt(note) {
                decryptedContent = it
            }
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
    accountViewModel: AccountViewModel,
    nav: (String) -> Unit
) {
    LoadDecryptedContent(note, accountViewModel) { body ->
        val eventContent by remember(note.event) {
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
                overflow = TextOverflow.Ellipsis
            )
        } else {
            SensitivityWarning(
                note = note,
                accountViewModel = accountViewModel
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
                    nav = nav
                )
            }

            val hashtags = remember(note.event) { note.event?.hashtags()?.toImmutableList() ?: persistentListOf() }
            DisplayUncitedHashtags(hashtags, eventContent, nav)
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
    nav: (String) -> Unit
) {
    val noteEvent = note.event as? PollNoteEvent ?: return
    val eventContent = remember(note) { noteEvent.content() }

    if (makeItShort && accountViewModel.isLoggedUser(note.author)) {
        Text(
            text = eventContent,
            color = MaterialTheme.colorScheme.placeholderText,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    } else {
        val tags = remember(note) { note.event?.tags()?.toImmutableListOfLists() ?: EmptyTagList }

        SensitivityWarning(
            note = note,
            accountViewModel = accountViewModel
        ) {
            TranslatableRichTextViewer(
                content = eventContent,
                canPreview = canPreview && !makeItShort,
                modifier = remember { Modifier.fillMaxWidth() },
                tags = tags,
                backgroundColor = backgroundColor,
                accountViewModel = accountViewModel,
                nav = nav
            )

            PollNote(
                note,
                canPreview = canPreview && !makeItShort,
                backgroundColor,
                accountViewModel,
                nav
            )
        }

        val hashtags = remember { noteEvent.hashtags().toImmutableList() }
        DisplayUncitedHashtags(hashtags, eventContent, nav)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun RenderAppDefinition(
    note: Note,
    accountViewModel: AccountViewModel,
    nav: (String) -> Unit
) {
    val noteEvent = note.event as? AppDefinitionEvent ?: return

    var metadata by remember {
        mutableStateOf<UserMetadata?>(null)
    }

    LaunchedEffect(key1 = noteEvent) {
        launch(Dispatchers.Default) {
            metadata = noteEvent.appMetaData()
        }
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
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(125.dp)
                        .combinedClickable(
                            onClick = {},
                            onLongClick = {
                                clipboardManager.setText(AnnotatedString(it.banner!!))
                            }
                        )
                )

                if (zoomImageDialogOpen) {
                    ZoomableImageDialog(
                        imageUrl = figureOutMimeType(it.banner!!),
                        onDismiss = { zoomImageDialogOpen = false },
                        accountViewModel = accountViewModel
                    )
                }
            } else {
                Image(
                    painter = painterResource(R.drawable.profile_banner),
                    contentDescription = stringResource(id = R.string.profile_banner),
                    contentScale = ContentScale.FillWidth,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(125.dp)
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp)
                    .padding(top = 75.dp)
            ) {
                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Bottom
                ) {
                    var zoomImageDialogOpen by remember { mutableStateOf(false) }

                    Box(Modifier.size(100.dp)) {
                        it.picture?.let {
                            AsyncImage(
                                model = it,
                                contentDescription = null,
                                contentScale = ContentScale.FillWidth,
                                modifier = Modifier
                                    .border(
                                        3.dp,
                                        MaterialTheme.colorScheme.background,
                                        CircleShape
                                    )
                                    .clip(shape = CircleShape)
                                    .fillMaxSize()
                                    .background(MaterialTheme.colorScheme.background)
                                    .combinedClickable(
                                        onClick = { zoomImageDialogOpen = true },
                                        onLongClick = {
                                            clipboardManager.setText(AnnotatedString(it))
                                        }
                                    )
                            )
                        }
                    }

                    if (zoomImageDialogOpen) {
                        ZoomableImageDialog(
                            imageUrl = figureOutMimeType(it.banner!!),
                            onDismiss = { zoomImageDialogOpen = false },
                            accountViewModel = accountViewModel
                        )
                    }

                    Spacer(Modifier.weight(1f))

                    Row(
                        modifier = Modifier
                            .height(Size35dp)
                            .padding(bottom = 3.dp)
                    ) {
                    }
                }

                val name = remember(it) { it.anyName() }
                name?.let {
                    Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.padding(top = 7.dp)) {
                        CreateTextWithEmoji(
                            text = it,
                            tags = remember { (note.event?.tags() ?: emptyList()).toImmutableListOfLists() },
                            fontWeight = FontWeight.Bold,
                            fontSize = 25.sp
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
                            modifier = Modifier.padding(top = 1.dp, bottom = 1.dp, start = 5.dp)
                        )
                    }
                }

                it.about?.let {
                    Row(
                        modifier = Modifier.padding(top = 5.dp, bottom = 5.dp)
                    ) {
                        val tags = remember(note) { note.event?.tags()?.toImmutableListOfLists() ?: EmptyTagList }
                        val bgColor = MaterialTheme.colorScheme.background
                        val backgroundColor = remember {
                            mutableStateOf(bgColor)
                        }
                        TranslatableRichTextViewer(
                            content = it,
                            canPreview = false,
                            tags = tags,
                            backgroundColor = backgroundColor,
                            accountViewModel = accountViewModel,
                            nav = nav
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
    nav: (String) -> Unit
) {
    val quote = remember {
        (note.event as? HighlightEvent)?.quote() ?: ""
    }
    val author = remember() {
        (note.event as? HighlightEvent)?.author()
    }
    val url = remember() {
        (note.event as? HighlightEvent)?.inUrl()
    }
    val postHex = remember() {
        (note.event as? HighlightEvent)?.taggedAddresses()?.firstOrNull()
    }

    DisplayHighlight(
        highlight = quote,
        authorHex = author,
        url = url,
        postAddress = postHex,
        makeItShort = makeItShort,
        canPreview = canPreview,
        backgroundColor = backgroundColor,
        accountViewModel = accountViewModel,
        nav = nav
    )
}

@Composable
private fun RenderPrivateMessage(
    note: Note,
    makeItShort: Boolean,
    canPreview: Boolean,
    backgroundColor: MutableState<Color>,
    accountViewModel: AccountViewModel,
    nav: (String) -> Unit
) {
    val noteEvent = note.event as? PrivateDmEvent ?: return

    val withMe = remember { noteEvent.with(accountViewModel.userProfile().pubkeyHex) }
    if (withMe) {
        LoadDecryptedContent(note, accountViewModel) { eventContent ->
            val hashtags = remember(note.event?.id()) { note.event?.hashtags()?.toImmutableList() ?: persistentListOf() }
            val modifier = remember(note.event?.id()) { Modifier.fillMaxWidth() }
            val isAuthorTheLoggedUser = remember(note.event?.id()) { accountViewModel.isLoggedUser(note.author) }

            val tags = remember(note) { note.event?.tags()?.toImmutableListOfLists() ?: EmptyTagList }

            if (makeItShort && isAuthorTheLoggedUser) {
                Text(
                    text = eventContent,
                    color = MaterialTheme.colorScheme.placeholderText,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            } else {
                SensitivityWarning(
                    note = note,
                    accountViewModel = accountViewModel
                ) {
                    TranslatableRichTextViewer(
                        content = eventContent,
                        canPreview = canPreview && !makeItShort,
                        modifier = modifier,
                        tags = tags,
                        backgroundColor = backgroundColor,
                        accountViewModel = accountViewModel,
                        nav = nav
                    )
                }

                DisplayUncitedHashtags(hashtags, eventContent, nav)
            }
        }
    } else {
        val recipient = noteEvent.recipientPubKeyBytes()?.toNpub() ?: "Someone"

        TranslatableRichTextViewer(
            stringResource(
                id = R.string.private_conversation_notification,
                "@${note.author?.pubkeyNpub()}",
                "@$recipient"
            ),
            canPreview = !makeItShort,
            Modifier.fillMaxWidth(),
            EmptyTagList,
            backgroundColor,
            accountViewModel,
            nav
        )
    }
}

@Composable
fun DisplayRelaySet(
    baseNote: Note,
    backgroundColor: MutableState<Color>,
    accountViewModel: AccountViewModel,
    nav: (String) -> Unit
) {
    val noteEvent = baseNote.event as? RelaySetEvent ?: return

    val relays by remember(baseNote) {
        mutableStateOf(
            noteEvent.relays().map { RelayBriefInfo(it) }.toImmutableList()
        )
    }

    var expanded by remember {
        mutableStateOf(false)
    }

    val toMembersShow = if (expanded) {
        relays
    } else {
        relays.take(3)
    }

    val relayListName by remember {
        derivedStateOf {
            "#${noteEvent.dTag()}"
        }
    }

    val relayDescription by remember {
        derivedStateOf {
            noteEvent.description()
        }
    }

    Text(
        text = relayListName,
        fontWeight = FontWeight.Bold,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier
            .fillMaxWidth()
            .padding(5.dp),
        textAlign = TextAlign.Center
    )

    relayDescription?.let {
        Text(
            text = it,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .fillMaxWidth()
                .padding(5.dp),
            textAlign = TextAlign.Center,
            color = Color.Gray
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
                        modifier = Modifier
                            .padding(start = 10.dp, bottom = 5.dp)
                            .weight(1f)
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
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(getGradient(backgroundColor))
            ) {
                ShowMoreButton {
                    expanded = !expanded
                }
            }
        }
    }
}

@Composable
private fun RelayOptionsAction(
    relay: String,
    accountViewModel: AccountViewModel,
    nav: (String) -> Unit
) {
    val userStateRelayInfo by accountViewModel.account.userProfile().live().relayInfo.observeAsState()
    val isCurrentlyOnTheUsersList by remember(userStateRelayInfo) {
        derivedStateOf {
            userStateRelayInfo?.user?.latestContactList?.relays()?.none { it.key == relay } == true
        }
    }

    var wantsToAddRelay by remember {
        mutableStateOf("")
    }

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
    nav: (String) -> Unit
) {
    val noteEvent = baseNote.event as? PeopleListEvent ?: return

    var members by remember { mutableStateOf<ImmutableList<User>>(persistentListOf()) }

    var expanded by remember {
        mutableStateOf(false)
    }

    val toMembersShow = if (expanded) {
        members
    } else {
        members.take(3)
    }

    val name by remember {
        derivedStateOf {
            "#${noteEvent.dTag()}"
        }
    }

    Text(
        text = name,
        fontWeight = FontWeight.Bold,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier
            .fillMaxWidth()
            .padding(5.dp),
        textAlign = TextAlign.Center
    )

    LaunchedEffect(Unit) {
        accountViewModel.loadUsers(noteEvent.bookmarkedPeople()) {
            members = it
        }
    }

    Box {
        FlowRow(modifier = Modifier.padding(top = 5.dp)) {
            toMembersShow.forEach { user ->
                Row(modifier = Modifier.fillMaxWidth()) {
                    UserCompose(
                        user,
                        overallModifier = Modifier,
                        accountViewModel = accountViewModel,
                        nav = nav
                    )
                }
            }
        }

        if (members.size > 3 && !expanded) {
            Row(
                verticalAlignment = CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(getGradient(backgroundColor))
            ) {
                ShowMoreButton {
                    expanded = !expanded
                }
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
    nav: (String) -> Unit
) {
    if (note.replyTo.isNullOrEmpty()) return

    val noteEvent = note.event as? BadgeAwardEvent ?: return
    var awardees by remember { mutableStateOf<List<User>>(listOf()) }

    Text(text = stringResource(R.string.award_granted_to))

    LaunchedEffect(key1 = note) {
        accountViewModel.loadUsers(noteEvent.awardees()) {
            awardees = it
        }
    }

    FlowRow(modifier = Modifier.padding(top = 5.dp)) {
        awardees.take(100).forEach { user ->
            Row(
                modifier = Modifier
                    .size(size = Size35dp)
                    .clickable {
                        nav("User/${user.pubkeyHex}")
                    },
                verticalAlignment = CenterVertically
            ) {
                ClickableUserPicture(
                    baseUser = user,
                    accountViewModel = accountViewModel,
                    size = Size35dp
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
            nav = nav
        )
    }
}

@Composable
private fun RenderReaction(
    note: Note,
    backgroundColor: MutableState<Color>,
    accountViewModel: AccountViewModel,
    nav: (String) -> Unit
) {
    note.replyTo?.lastOrNull()?.let {
        NoteCompose(
            it,
            modifier = Modifier,
            isBoostedNote = true,
            unPackReply = false,
            parentBackgroundColor = backgroundColor,
            accountViewModel = accountViewModel,
            nav = nav
        )
    }

    // Reposts have trash in their contents.
    val refactorReactionText =
        if (note.event?.content() == "+") "❤" else note.event?.content() ?: ""

    Text(
        text = refactorReactionText,
        maxLines = 1
    )
}

@Composable
fun RenderRepost(
    note: Note,
    backgroundColor: MutableState<Color>,
    accountViewModel: AccountViewModel,
    nav: (String) -> Unit
) {
    val boostedNote = remember {
        note.replyTo?.lastOrNull()
    }

    boostedNote?.let {
        NoteCompose(
            it,
            modifier = Modifier,
            isBoostedNote = true,
            unPackReply = false,
            parentBackgroundColor = backgroundColor,
            accountViewModel = accountViewModel,
            nav = nav
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
    nav: (String) -> Unit
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
                        nav = nav
                    )
                }
            }
        }

        Text(
            text = stringResource(id = R.string.community_approved_posts),
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .fillMaxWidth()
                .padding(5.dp),
            textAlign = TextAlign.Center
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
                nav = nav
            )
        }
    }
}

@Composable
fun LoadAddressableNote(aTagHex: String, accountViewModel: AccountViewModel, content: @Composable (AddressableNote?) -> Unit) {
    var note by remember(aTagHex) {
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
fun LoadAddressableNote(aTag: ATag, accountViewModel: AccountViewModel, content: @Composable (AddressableNote?) -> Unit) {
    var note by remember(aTag) {
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
    onClick: ((EmojiUrl) -> Unit)? = null
) {
    val noteEvent by baseNote.live().metadata.map {
        it.note.event
    }.distinctUntilChanged().observeAsState(baseNote.event)

    if (noteEvent == null || noteEvent !is EmojiPackEvent) return

    (noteEvent as? EmojiPackEvent)?.let {
        RenderEmojiPack(
            noteEvent = it,
            baseNote = baseNote,
            actionable = actionable,
            backgroundColor = backgroundColor,
            accountViewModel = accountViewModel,
            onClick = onClick
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
    onClick: ((EmojiUrl) -> Unit)? = null
) {
    var expanded by remember {
        mutableStateOf(false)
    }

    val allEmojis = remember(noteEvent) {
        noteEvent.taggedEmojis()
    }

    val emojisToShow = if (expanded) {
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
            modifier = Modifier
                .weight(1F)
                .padding(5.dp),
            textAlign = TextAlign.Center
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
                            modifier = Size35Modifier
                        )
                    }
                } else {
                    Box(
                        modifier = Size35Modifier,
                        contentAlignment = Alignment.Center
                    ) {
                        AsyncImage(
                            model = emoji.url,
                            contentDescription = null,
                            modifier = Size35Modifier
                        )
                    }
                }
            }
        }

        if (allEmojis.size > 60 && !expanded) {
            Row(
                verticalAlignment = CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(getGradient(backgroundColor))
            ) {
                ShowMoreButton {
                    expanded = !expanded
                }
            }
        }
    }
}

@Composable
private fun EmojiListOptions(
    accountViewModel: AccountViewModel,
    emojiPackNote: Note
) {
    LoadAddressableNote(
        aTag = ATag(
            EmojiPackSelectionEvent.kind,
            accountViewModel.userProfile().pubkeyHex,
            "",
            null
        ),
        accountViewModel
    ) {
        it?.let { usersEmojiList ->
            val hasAddedThis by remember {
                usersEmojiList.live().metadata.map {
                    usersEmojiList.event?.isTaggedAddressableNote(emojiPackNote.idHex)
                }.distinctUntilChanged()
            }.observeAsState()

            Crossfade(targetState = hasAddedThis, label = "EmojiListOptions") {
                if (it != true) {
                    AddButton() {
                        accountViewModel.addEmojiPack(usersEmojiList, emojiPackNote)
                    }
                } else {
                    RemoveButton {
                        accountViewModel.removeEmojiPack(usersEmojiList, emojiPackNote)
                    }
                }
            }
        }
    }
}

@Composable
fun RemoveButton(onClick: () -> Unit) {
    Button(
        modifier = Modifier.padding(start = 3.dp),
        onClick = onClick,
        shape = ButtonBorder,
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary
        ),
        contentPadding = PaddingValues(vertical = 0.dp, horizontal = 16.dp)
    ) {
        Text(text = stringResource(R.string.remove), color = Color.White)
    }
}

@Composable
fun AddButton(
    text: Int = R.string.add,
    isActive: Boolean = true,
    modifier: Modifier = Modifier.padding(start = 3.dp),
    onClick: () -> Unit
) {
    Button(
        modifier = modifier,
        onClick = {
            if (isActive) {
                onClick()
            }
        },
        shape = ButtonBorder,
        colors = ButtonDefaults.buttonColors(
            containerColor = if (isActive) MaterialTheme.colorScheme.primary else Color.Gray
        ),
        contentPadding = PaddingValues(vertical = 0.dp, horizontal = 16.dp)
    ) {
        Text(text = stringResource(text), color = Color.White, textAlign = TextAlign.Center)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RenderPinListEvent(
    baseNote: Note,
    backgroundColor: MutableState<Color>,
    accountViewModel: AccountViewModel,
    nav: (String) -> Unit
) {
    val noteEvent = baseNote.event as? PinListEvent ?: return

    val pins by remember { mutableStateOf(noteEvent.pins()) }

    var expanded by remember {
        mutableStateOf(false)
    }

    val pinsToShow = if (expanded) {
        pins
    } else {
        pins.take(3)
    }

    Text(
        text = "#${noteEvent.dTag()}",
        fontWeight = FontWeight.Bold,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier
            .fillMaxWidth()
            .padding(5.dp),
        textAlign = TextAlign.Center
    )

    Box {
        FlowRow(modifier = Modifier.padding(top = 5.dp)) {
            pinsToShow.forEach { pin ->
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = CenterVertically) {
                    PinIcon(modifier = Size15Modifier, tint = MaterialTheme.colorScheme.onBackground.copy(0.32f))

                    Spacer(modifier = Modifier.width(5.dp))

                    TranslatableRichTextViewer(
                        content = pin,
                        canPreview = true,
                        tags = EmptyTagList,
                        backgroundColor = backgroundColor,
                        accountViewModel = accountViewModel,
                        nav = nav
                    )
                }
            }
        }

        if (pins.size > 3 && !expanded) {
            Row(
                verticalAlignment = CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(getGradient(backgroundColor))
            ) {
                ShowMoreButton {
                    expanded = !expanded
                }
            }
        }
    }
}

fun getGradient(backgroundColor: MutableState<Color>): Brush {
    return Brush.verticalGradient(
        colors = listOf(
            backgroundColor.value.copy(alpha = 0f),
            backgroundColor.value
        )
    )
}

@Composable
private fun RenderAudioTrack(
    note: Note,
    accountViewModel: AccountViewModel,
    nav: (String) -> Unit
) {
    val noteEvent = note.event as? AudioTrackEvent ?: return

    AudioTrackHeader(noteEvent, note, accountViewModel, nav)
}

@Composable
private fun RenderAudioHeader(
    note: Note,
    accountViewModel: AccountViewModel,
    nav: (String) -> Unit
) {
    val noteEvent = note.event as? AudioHeaderEvent ?: return

    AudioHeader(noteEvent, note, accountViewModel, nav)
}

@Composable
private fun RenderLongFormContent(
    note: Note,
    accountViewModel: AccountViewModel,
    nav: (String) -> Unit
) {
    val noteEvent = note.event as? LongTextNoteEvent ?: return

    LongFormHeader(noteEvent, note, accountViewModel)
}

@Composable
private fun RenderReport(
    note: Note,
    backgroundColor: MutableState<Color>,
    accountViewModel: AccountViewModel,
    nav: (String) -> Unit
) {
    val noteEvent = note.event as? ReportEvent ?: return

    val base = remember {
        (noteEvent.reportedPost() + noteEvent.reportedAuthor())
    }

    val reportType = base.map {
        when (it.reportType) {
            ReportEvent.ReportType.EXPLICIT -> stringResource(R.string.explicit_content)
            ReportEvent.ReportType.NUDITY -> stringResource(R.string.nudity)
            ReportEvent.ReportType.PROFANITY -> stringResource(R.string.profanity_hateful_speech)
            ReportEvent.ReportType.SPAM -> stringResource(R.string.spam)
            ReportEvent.ReportType.IMPERSONATION -> stringResource(R.string.impersonation)
            ReportEvent.ReportType.ILLEGAL -> stringResource(R.string.illegal_behavior)
        }
    }.toSet().joinToString(", ")

    val content = remember {
        reportType + (note.event?.content()?.ifBlank { null }?.let { ": $it" } ?: "")
    }

    TranslatableRichTextViewer(
        content = content,
        canPreview = true,
        modifier = Modifier,
        tags = EmptyTagList,
        backgroundColor = backgroundColor,
        accountViewModel = accountViewModel,
        nav = nav
    )

    note.replyTo?.lastOrNull()?.let {
        NoteCompose(
            baseNote = it,
            isQuotedNote = true,
            modifier = Modifier
                .padding(top = 5.dp)
                .fillMaxWidth()
                .clip(shape = QuoteBorder)
                .border(
                    1.dp,
                    MaterialTheme.colorScheme.subtleBorder,
                    QuoteBorder
                ),
            unPackReply = false,
            makeItShort = true,
            parentBackgroundColor = backgroundColor,
            accountViewModel = accountViewModel,
            nav = nav
        )
    }
}

@Composable
private fun ReplyRow(
    note: Note,
    unPackReply: Boolean,
    backgroundColor: MutableState<Color>,
    accountViewModel: AccountViewModel,
    nav: (String) -> Unit
) {
    val noteEvent = note.event

    val showReply by remember(note) {
        derivedStateOf {
            noteEvent is BaseTextNoteEvent && (note.replyTo != null || noteEvent.hasAnyTaggedUser())
        }
    }

    val showChannelInfo by remember(note) {
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
            nav = nav
        )
    }

    if (showReply) {
        val replyingDirectlyTo = remember { note.replyTo?.lastOrNull { it.event?.kind() != CommunityDefinitionEvent.kind } }
        if (replyingDirectlyTo != null && unPackReply) {
            ReplyNoteComposition(replyingDirectlyTo, backgroundColor, accountViewModel, nav)
            Spacer(modifier = StdVertSpacer)
        } else if (showChannelInfo != null) {
            val replies = remember { note.replyTo?.toImmutableList() }
            val mentions = remember {
                (note.event as? BaseTextNoteEvent)?.mentions()?.toImmutableList()
                    ?: persistentListOf()
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
    nav: (String) -> Unit
) {
    val replyBackgroundColor = remember {
        mutableStateOf(backgroundColor.value)
    }
    val defaultReplyBackground = MaterialTheme.colorScheme.replyBackground

    LaunchedEffect(key1 = backgroundColor.value, key2 = defaultReplyBackground) {
        launch(Dispatchers.Default) {
            val newReplyBackgroundColor =
                defaultReplyBackground.compositeOver(backgroundColor.value)
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
        nav = nav
    )
}

@Composable
fun SecondUserInfoRow(
    note: Note,
    accountViewModel: AccountViewModel,
    nav: (String) -> Unit
) {
    val noteEvent = remember { note.event } ?: return
    val noteAuthor = remember { note.author } ?: return

    Row(
        verticalAlignment = CenterVertically,
        modifier = UserNameMaxRowHeight
    ) {
        ObserveDisplayNip05Status(noteAuthor, remember { Modifier.weight(1f) }, accountViewModel, nav)

        val geo = remember { noteEvent.getGeoHash() }
        if (geo != null) {
            Spacer(StdHorzSpacer)
            DisplayLocation(geo, nav)
        }

        val baseReward = remember { noteEvent.getReward()?.let { Reward(it) } }
        if (baseReward != null) {
            Spacer(StdHorzSpacer)
            DisplayReward(baseReward, note, accountViewModel, nav)
        }

        val pow = remember { noteEvent.getPoWRank() }
        if (pow > 20) {
            Spacer(StdHorzSpacer)
            DisplayPoW(pow)
        }
    }
}

@Composable
fun LoadStatuses(
    user: User,
    accountViewModel: AccountViewModel,
    content: @Composable (ImmutableList<AddressableNote>) -> Unit
) {
    var statuses: ImmutableList<AddressableNote> by remember {
        mutableStateOf(persistentListOf())
    }

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
fun LoadCityName(geohash: GeoHash, content: @Composable (String) -> Unit) {
    val context = LocalContext.current
    var cityName by remember(geohash) {
        mutableStateOf<String>(geohash.toString())
    }

    LaunchedEffect(key1 = geohash) {
        launch(Dispatchers.IO) {
            val newCityName = ReverseGeoLocationUtil().execute(geohash.toLocation(), context)?.ifBlank { null }
            if (newCityName != null && newCityName != cityName) {
                cityName = newCityName
            }
        }
    }

    content(cityName)
}

@Composable
fun DisplayLocation(geohashStr: String, nav: (String) -> Unit) {
    val geoHash = runCatching { geohashStr.toGeoHash() }.getOrNull()
    if (geoHash != null) {
        LoadCityName(geoHash) { cityName ->
            ClickableText(
                text = AnnotatedString(cityName),
                onClick = { nav("Geohash/$geoHash") },
                style = LocalTextStyle.current.copy(
                    color = MaterialTheme.colorScheme.primary.copy(
                        alpha = 0.52f
                    ),
                    fontSize = Font14SP,
                    fontWeight = FontWeight.Bold
                ),
                maxLines = 1
            )
        }
    }
}

@Composable
fun FirstUserInfoRow(
    baseNote: Note,
    showAuthorPicture: Boolean,
    accountViewModel: AccountViewModel,
    nav: (String) -> Unit
) {
    Row(verticalAlignment = CenterVertically, modifier = remember { UserNameRowHeight }) {
        val isRepost by remember(baseNote) {
            derivedStateOf {
                baseNote.event is RepostEvent || baseNote.event is GenericRepostEvent
            }
        }

        val isCommunityPost by remember(baseNote) {
            derivedStateOf {
                baseNote.event?.isTaggedAddressableKind(CommunityDefinitionEvent.kind) == true
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

        TimeAgo(baseNote)

        MoreOptionsButton(baseNote, accountViewModel)
    }
}

@Composable
private fun BoostedMark() {
    Text(
        stringResource(id = R.string.boosted),
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.placeholderText,
        maxLines = 1,
        modifier = HalfStartPadding
    )
}

@Composable
fun MoreOptionsButton(
    baseNote: Note,
    accountViewModel: AccountViewModel
) {
    val popupExpanded = remember { mutableStateOf(false) }
    val enablePopup = remember {
        { popupExpanded.value = true }
    }

    IconButton(
        modifier = Size24Modifier,
        onClick = enablePopup
    ) {
        VerticalDotsIcon()

        NoteDropDownMenu(
            baseNote,
            popupExpanded,
            accountViewModel
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
        maxLines = 1
    )
}

@Composable
private fun AuthorAndRelayInformation(baseNote: Note, accountViewModel: AccountViewModel, nav: (String) -> Unit) {
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
    nav: (String) -> Unit
) {
    val isRepost by remember(baseNote) {
        derivedStateOf {
            baseNote.event is RepostEvent || baseNote.event is GenericRepostEvent
        }
    }

    if (isRepost) {
        val baseReply by remember(baseNote) {
            derivedStateOf {
                baseNote.replyTo?.lastOrNull()
            }
        }
        baseReply?.let {
            RelayBadges(it, accountViewModel, nav)
        }
    } else {
        RelayBadges(baseNote, accountViewModel, nav)
    }
}

@Composable
private fun RenderAuthorImages(
    baseNote: Note,
    nav: (String) -> Unit,
    accountViewModel: AccountViewModel
) {
    val baseRepost by remember {
        derivedStateOf {
            baseNote.replyTo?.lastOrNull()
        }
    }

    val isRepost = baseNote.event is RepostEvent || baseNote.event is GenericRepostEvent

    if (isRepost && baseRepost != null) {
        RepostNoteAuthorPicture(baseNote, baseRepost!!, accountViewModel, nav)
    } else {
        NoteAuthorPicture(baseNote, nav, accountViewModel, Size55dp)
    }

    val isChannel = baseNote.event is ChannelMessageEvent && baseNote.channelHex() != null

    val automaticallyShowProfilePicture = remember {
        accountViewModel.settings.showProfilePictures.value
    }

    if (isChannel) {
        val baseChannelHex = remember { baseNote.channelHex() }
        if (baseChannelHex != null) {
            LoadChannel(baseChannelHex, accountViewModel) { channel ->
                ChannelNotePicture(channel, loadProfilePicture = automaticallyShowProfilePicture)
            }
        }
    }
}

@Composable
fun LoadChannel(baseChannelHex: String, accountViewModel: AccountViewModel, content: @Composable (Channel) -> Unit) {
    var channel by remember(baseChannelHex) {
        mutableStateOf<Channel?>(accountViewModel.getChannelIfExists(baseChannelHex))
    }

    if (channel == null) {
        LaunchedEffect(key1 = baseChannelHex) {
            accountViewModel.checkGetOrCreateChannel(baseChannelHex) { newChannel ->
                launch(Dispatchers.Main) {
                    channel = newChannel
                }
            }
        }
    }

    channel?.let {
        content(it)
    }
}

@Composable
private fun ChannelNotePicture(baseChannel: Channel, loadProfilePicture: Boolean) {
    val model by baseChannel.live.map {
        it.channel.profilePicture()
    }.distinctUntilChanged().observeAsState()

    val backgroundColor = MaterialTheme.colorScheme.background

    val modifier = remember {
        Modifier
            .width(30.dp)
            .height(30.dp)
            .clip(shape = CircleShape)
            .background(backgroundColor)
            .border(
                2.dp,
                backgroundColor,
                CircleShape
            )
    }

    Box(Size30Modifier) {
        RobohashAsyncImageProxy(
            robot = baseChannel.idHex,
            model = model,
            contentDescription = stringResource(R.string.group_picture),
            modifier = modifier,
            loadProfilePicture = loadProfilePicture
        )
    }
}

@Composable
private fun RepostNoteAuthorPicture(
    baseNote: Note,
    baseRepost: Note,
    accountViewModel: AccountViewModel,
    nav: (String) -> Unit
) {
    GenericRepostSection(
        baseAuthorPicture = {
            NoteAuthorPicture(
                baseNote = baseNote,
                nav = nav,
                accountViewModel = accountViewModel,
                size = Size34dp
            )
        },
        repostAuthorPicture = {
            NoteAuthorPicture(
                baseNote = baseRepost,
                nav = nav,
                accountViewModel = accountViewModel,
                size = Size34dp
            )
        }
    )
}

@Composable
@Preview
private fun GenericRepostSectionPreview() {
    GenericRepostSection(
        baseAuthorPicture = {
            Text("ab")
        },
        repostAuthorPicture = {
            Text("cd")
        }
    )
}

@Composable
private fun GenericRepostSection(
    baseAuthorPicture: @Composable () -> Unit,
    repostAuthorPicture: @Composable () -> Unit
) {
    Box(modifier = Size55Modifier) {
        Box(remember { Size35Modifier.align(Alignment.TopStart) }) {
            baseAuthorPicture()
        }

        Box(
            remember {
                Size18Modifier
                    .align(Alignment.BottomStart)
                    .padding(1.dp)
            }
        ) {
            RepostedIcon(modifier = Size18Modifier, MaterialTheme.colorScheme.placeholderText)
        }

        Box(remember { Size35Modifier.align(Alignment.BottomEnd) }, contentAlignment = BottomEnd) {
            repostAuthorPicture()
        }
    }
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
    nav: (String) -> Unit
) {
    val quote =
        remember {
            highlight
                .split("\n")
                .map { "> *${it.removeSuffix(" ")}*" }
                .joinToString("\n")
        }

    TranslatableRichTextViewer(
        quote,
        canPreview = canPreview && !makeItShort,
        remember { Modifier.fillMaxWidth() },
        EmptyTagList,
        backgroundColor,
        accountViewModel,
        nav
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
    nav: (String) -> Unit
) {
    var userBase by remember { mutableStateOf<User?>(accountViewModel.getUserIfExists(authorHex)) }

    if (userBase == null) {
        LaunchedEffect(Unit) {
            accountViewModel.checkGetOrCreateUser(authorHex) { newUserBase ->
                userBase = newUserBase
            }
        }
    }

    MeasureSpaceWidth {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(it), verticalArrangement = Arrangement.Center) {
            userBase?.let { userBase ->
                LoadAndDisplayUser(userBase, nav)
            }

            url?.let { url ->
                LoadAndDisplayUrl(url)
            }

            postAddress?.let { address ->
                LoadAndDisplayPost(address, accountViewModel, nav)
            }
        }
    }
}

@Composable
private fun LoadAndDisplayPost(postAddress: ATag, accountViewModel: AccountViewModel, nav: (String) -> Unit) {
    LoadAddressableNote(aTag = postAddress, accountViewModel) {
        it?.let { note ->
            val noteEvent by note.live().metadata.map {
                it.note.event
            }.distinctUntilChanged().observeAsState(note.event)

            val title = remember(noteEvent) {
                (noteEvent as? LongTextNoteEvent)?.title()
            }

            title?.let {
                Text(remember { "-" }, maxLines = 1)
                ClickableText(
                    text = AnnotatedString(title),
                    onClick = {
                        routeFor(note, accountViewModel.userProfile())?.let {
                            nav(it)
                        }
                    },
                    style = LocalTextStyle.current.copy(color = MaterialTheme.colorScheme.primary)
                )
            }
        }
    }
}

@Composable
private fun LoadAndDisplayUrl(url: String) {
    val validatedUrl = remember {
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
private fun LoadAndDisplayUser(
    userBase: User,
    nav: (String) -> Unit
) {
    val route = remember { "User/${userBase.pubkeyHex}" }

    val userState by userBase.live().metadata.observeAsState()
    val userDisplayName = remember(userState) { userState?.user?.toBestDisplayName() }
    val userTags = remember(userState) { userState?.user?.info?.latestMetadata?.tags?.toImmutableListOfLists() }

    if (userDisplayName != null) {
        CreateClickableTextWithEmoji(
            clickablePart = userDisplayName,
            suffix = " ",
            maxLines = 1,
            route = route,
            nav = nav,
            tags = userTags
        )
    }
}

@Composable
fun DisplayFollowingCommunityInPost(
    baseNote: Note,
    accountViewModel: AccountViewModel,
    nav: (String) -> Unit
) {
    Column(HalfStartPadding) {
        Row(verticalAlignment = CenterVertically) {
            DisplayCommunity(baseNote, nav)
        }
    }
}

@Composable
fun DisplayFollowingHashtagsInPost(
    baseNote: Note,
    accountViewModel: AccountViewModel,
    nav: (String) -> Unit
) {
    val noteEvent = remember { baseNote.event } ?: return

    val userFollowState by accountViewModel.userFollows.observeAsState()
    var firstTag by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(key1 = userFollowState) {
        launch(Dispatchers.Default) {
            val followingTags = userFollowState?.user?.cachedFollowingTagSet() ?: emptySet()
            val newFirstTag = noteEvent.firstIsTaggedHashes(followingTags)

            if (firstTag != newFirstTag) {
                launch(Dispatchers.Main) {
                    firstTag = newFirstTag
                }
            }
        }
    }

    firstTag?.let {
        Column(verticalArrangement = Arrangement.Center) {
            Row(verticalAlignment = CenterVertically) {
                DisplayTagList(it, nav)
            }
        }
    }
}

@Composable
private fun DisplayTagList(firstTag: String, nav: (String) -> Unit) {
    val displayTag = remember(firstTag) { AnnotatedString(" #$firstTag") }
    val route = remember(firstTag) { "Hashtag/$firstTag" }

    ClickableText(
        text = displayTag,
        onClick = { nav(route) },
        style = LocalTextStyle.current.copy(
            color = MaterialTheme.colorScheme.primary.copy(
                alpha = 0.52f
            )
        ),
        maxLines = 1
    )
}

@Composable
private fun DisplayCommunity(note: Note, nav: (String) -> Unit) {
    val communityTag = remember(note) {
        note.event?.getTagOfAddressableKind(CommunityDefinitionEvent.kind)
    } ?: return

    val displayTag = remember(note) { AnnotatedString(getCommunityShortName(communityTag)) }
    val route = remember(note) { "Community/${communityTag.toTag()}" }

    ClickableText(
        text = displayTag,
        onClick = { nav(route) },
        style = LocalTextStyle.current.copy(
            color = MaterialTheme.colorScheme.primary.copy(
                alpha = 0.52f
            )
        ),
        maxLines = 1
    )
}

private fun getCommunityShortName(communityTag: ATag): String {
    val name = if (communityTag.dTag.length > 10) {
        communityTag.dTag.take(10) + "..."
    } else {
        communityTag.dTag.take(10)
    }

    return "/n/$name"
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DisplayUncitedHashtags(
    hashtags: ImmutableList<String>,
    eventContent: String,
    nav: (String) -> Unit
) {
    val hasHashtags = remember(eventContent) {
        hashtags.isNotEmpty()
    }

    if (hasHashtags) {
        val unusedHashtags = remember(eventContent) {
            hashtags.filter { !eventContent.contains(it, true) }
        }

        FlowRow(
            modifier = remember { Modifier.padding(top = 5.dp) }
        ) {
            unusedHashtags.forEach { hashtag ->
                ClickableText(
                    text = remember { AnnotatedString("#$hashtag ") },
                    onClick = { nav("Hashtag/$hashtag") },
                    style = LocalTextStyle.current.copy(
                        color = MaterialTheme.colorScheme.primary.copy(
                            alpha = 0.52f
                        )
                    )
                )
            }
        }
    }
}

@Composable
fun DisplayPoW(
    pow: Int
) {
    val powStr = remember(pow) {
        "PoW-$pow"
    }

    Text(
        powStr,
        color = MaterialTheme.colorScheme.lessImportantLink,
        fontSize = Font14SP,
        fontWeight = FontWeight.Bold,
        maxLines = 1
    )
}

@Stable
data class Reward(val amount: BigDecimal)

@Composable
fun DisplayReward(
    baseReward: Reward,
    baseNote: Note,
    accountViewModel: AccountViewModel,
    nav: (String) -> Unit
) {
    var popupExpanded by remember { mutableStateOf(false) }

    Column() {
        Row(
            verticalAlignment = CenterVertically,
            modifier = Modifier.clickable { popupExpanded = true }
        ) {
            ClickableText(
                text = AnnotatedString("#bounty"),
                onClick = { nav("Hashtag/bounty") },
                style = LocalTextStyle.current.copy(
                    color = MaterialTheme.colorScheme.primary.copy(
                        alpha = 0.52f
                    )
                )
            )

            RenderPledgeAmount(baseNote, baseReward, accountViewModel)
        }

        if (popupExpanded) {
            AddBountyAmountDialog(baseNote, accountViewModel) {
                popupExpanded = false
            }
        }
    }
}

@Composable
private fun RenderPledgeAmount(
    baseNote: Note,
    baseReward: Reward,
    accountViewModel: AccountViewModel
) {
    val repliesState by baseNote.live().replies.observeAsState()
    var reward by remember {
        mutableStateOf<String>(
            showAmount(baseReward.amount)
        )
    }

    var hasPledge by remember {
        mutableStateOf<Boolean>(
            false
        )
    }

    LaunchedEffect(key1 = repliesState) {
        launch(Dispatchers.IO) {
            repliesState?.note?.pledgedAmountByOthers()?.let {
                val newRewardAmount = showAmount(baseReward.amount.add(it))
                if (newRewardAmount != reward) {
                    reward = newRewardAmount
                }
            }
            val newHasPledge = repliesState?.note?.hasPledgeBy(accountViewModel.userProfile()) == true
            if (hasPledge != newHasPledge) {
                launch(Dispatchers.Main) {
                    hasPledge = newHasPledge
                }
            }
        }
    }

    if (hasPledge) {
        ZappedIcon(modifier = Size20Modifier)
    } else {
        ZapIcon(modifier = Size20Modifier, MaterialTheme.colorScheme.placeholderText)
    }

    Text(
        text = reward,
        color = MaterialTheme.colorScheme.placeholderText,
        maxLines = 1
    )
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
                val backgroundColor = it.drawable.toBitmap(200, 200).copy(Bitmap.Config.ARGB_8888, false).get(0, 199)
                val colorFromImage = Color(backgroundColor)
                val textBackground = if (colorFromImage.luminance() > 0.5) {
                    lightColorScheme().onBackground
                } else {
                    darkColorScheme().onBackground
                }

                launch(Dispatchers.Main) {
                    backgroundFromImage = Pair(colorFromImage, textBackground)
                }
            }
        }
    }

    Row(
        modifier = Modifier
            .padding(10.dp)
            .clip(shape = CutCornerShape(20, 20, 20, 20))
            .border(
                5.dp,
                MaterialTheme.colorScheme.mediumImportanceLink,
                CutCornerShape(20)
            )
            .background(backgroundFromImage.first)
    ) {
        RenderBadge(
            image,
            name,
            backgroundFromImage.second,
            description
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
    onSuccess: (AsyncImagePainter.State.Success) -> Unit
) {
    Column {
        image.let {
            AsyncImage(
                model = it,
                contentDescription = stringResource(
                    R.string.badge_award_image_for,
                    name ?: ""
                ),
                contentScale = ContentScale.FillWidth,
                modifier = Modifier.fillMaxWidth(),
                onSuccess = onSuccess
            )
        }

        name?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 10.dp, end = 10.dp),
                color = backgroundFromImage
            )
        }

        description?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 10.dp, end = 10.dp, bottom = 10.dp),
                color = Color.Gray,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun FileHeaderDisplay(note: Note, roundedCorner: Boolean, accountViewModel: AccountViewModel) {
    val event = (note.event as? FileHeaderEvent) ?: return
    val fullUrl = event.url() ?: return

    val content by remember(note) {
        val blurHash = event.blurhash()
        val hash = event.hash()
        val dimensions = event.dimensions()
        val description = event.alt() ?: event.content
        val isImage = imageExtensions.any { fullUrl.split("?")[0].lowercase().endsWith(it) }
        val uri = note.toNostrUri()

        mutableStateOf<ZoomableContent>(
            if (isImage) {
                ZoomableUrlImage(
                    url = fullUrl,
                    description = description,
                    hash = hash,
                    blurhash = blurHash,
                    dim = dimensions,
                    uri = uri
                )
            } else {
                ZoomableUrlVideo(
                    url = fullUrl,
                    description = description,
                    hash = hash,
                    dim = dimensions,
                    uri = uri,
                    authorName = note.author?.toBestDisplayName()
                )
            }
        )
    }

    SensitivityWarning(note = note, accountViewModel = accountViewModel) {
        ZoomableContentView(content = content, roundedCorner = roundedCorner, accountViewModel = accountViewModel)
    }
}

@Composable
fun FileStorageHeaderDisplay(baseNote: Note, roundedCorner: Boolean, accountViewModel: AccountViewModel) {
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
    accountViewModel: AccountViewModel
) {
    val eventHeader = (header.event as? FileStorageHeaderEvent) ?: return

    val appContext = LocalContext.current.applicationContext

    val noteState by content.live().metadata.observeAsState()

    val content by remember(noteState) {
        // Creates a new object when the event arrives to force an update of the image.
        val note = noteState?.note
        val uri = header.toNostrUri()
        val localDir = note?.idHex?.let { File(File(appContext.cacheDir, "NIP95"), it) }
        val blurHash = eventHeader.blurhash()
        val dimensions = eventHeader.dimensions()
        val description = eventHeader.alt() ?: eventHeader.content
        val mimeType = eventHeader.mimeType()

        val newContent = if (mimeType?.startsWith("image") == true) {
            ZoomableLocalImage(
                localFile = localDir,
                mimeType = mimeType,
                description = description,
                blurhash = blurHash,
                dim = dimensions,
                isVerified = true,
                uri = uri
            )
        } else {
            ZoomableLocalVideo(
                localFile = localDir,
                mimeType = mimeType,
                description = description,
                dim = dimensions,
                isVerified = true,
                uri = uri,
                authorName = header.author?.toBestDisplayName()
            )
        }

        mutableStateOf<ZoomableContent?>(newContent)
    }

    Crossfade(targetState = content) {
        if (it != null) {
            SensitivityWarning(note = header, accountViewModel = accountViewModel) {
                ZoomableContentView(content = it, roundedCorner = roundedCorner, accountViewModel = accountViewModel)
            }
        }
    }
}

@Composable
fun AudioTrackHeader(noteEvent: AudioTrackEvent, note: Note, accountViewModel: AccountViewModel, nav: (String) -> Unit) {
    val media = remember { noteEvent.media() }
    val cover = remember { noteEvent.cover() }
    val subject = remember { noteEvent.subject() }
    val content = remember { noteEvent.content() }
    val participants = remember { noteEvent.participants() }

    var participantUsers by remember { mutableStateOf<List<Pair<Participant, User>>>(emptyList()) }

    LaunchedEffect(key1 = participants) {
        accountViewModel.loadParticipants(participants) {
            participantUsers = it
        }
    }

    Row(modifier = Modifier.padding(top = 5.dp)) {
        Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Row() {
                subject?.let {
                    Row(verticalAlignment = CenterVertically, modifier = Modifier.padding(top = 5.dp, bottom = 5.dp)) {
                        Text(
                            text = it,
                            fontWeight = FontWeight.Bold,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }

            participantUsers.forEach {
                Row(
                    verticalAlignment = CenterVertically,
                    modifier = Modifier
                        .padding(top = 5.dp, start = 10.dp, end = 10.dp)
                        .clickable {
                            nav("User/${it.second.pubkeyHex}")
                        }
                ) {
                    ClickableUserPicture(it.second, 25.dp, accountViewModel)
                    Spacer(Modifier.width(5.dp))
                    UsernameDisplay(it.second, Modifier.weight(1f))
                    Spacer(Modifier.width(5.dp))
                    it.first.role?.let {
                        Text(
                            text = it.capitalize(Locale.ROOT),
                            color = MaterialTheme.colorScheme.placeholderText,
                            maxLines = 1
                        )
                    }
                }
            }

            media?.let { media ->
                Row(
                    verticalAlignment = CenterVertically
                ) {
                    cover?.let { cover ->
                        LoadThumbAndThenVideoView(
                            videoUri = media,
                            title = noteEvent.subject(),
                            thumbUri = cover,
                            authorName = note.author?.toBestDisplayName(),
                            roundedCorner = true,
                            nostrUriCallback = "nostr:${note.toNEvent()}",
                            accountViewModel = accountViewModel
                        )
                    }
                        ?: VideoView(
                            videoUri = media,
                            title = noteEvent.subject(),
                            authorName = note.author?.toBestDisplayName(),
                            roundedCorner = true,
                            accountViewModel = accountViewModel
                        )
                }
            }
        }
    }
}

@Composable
fun AudioHeader(noteEvent: AudioHeaderEvent, note: Note, accountViewModel: AccountViewModel, nav: (String) -> Unit) {
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
                    verticalAlignment = CenterVertically
                ) {
                    VideoView(
                        videoUri = media,
                        waveform = waveform,
                        title = noteEvent.subject(),
                        authorName = note.author?.toBestDisplayName(),
                        roundedCorner = true,
                        accountViewModel = accountViewModel,
                        nostrUriCallback = note.toNostrUri()
                    )
                }
            }

            content?.let {
                Row(
                    verticalAlignment = CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 5.dp)
                ) {
                    TranslatableRichTextViewer(
                        content = it,
                        canPreview = true,
                        tags = tags,
                        backgroundColor = background,
                        accountViewModel = accountViewModel,
                        nav = nav
                    )
                }
            }

            Row(Modifier.fillMaxWidth(), verticalAlignment = CenterVertically) {
                val hashtags = remember(noteEvent) { noteEvent.hashtags().toImmutableList() }
                DisplayUncitedHashtags(hashtags, content ?: "", nav)
            }
        }
    }
}

@Composable
fun RenderLiveActivityEvent(baseNote: Note, accountViewModel: AccountViewModel, nav: (String) -> Unit) {
    Row(modifier = Modifier.padding(top = 5.dp)) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            RenderLiveActivityEventInner(baseNote = baseNote, accountViewModel, nav)
        }
    }
}

@Composable
fun RenderLiveActivityEventInner(baseNote: Note, accountViewModel: AccountViewModel, nav: (String) -> Unit) {
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
        modifier = Modifier
            .padding(vertical = 5.dp)
            .fillMaxWidth()
    ) {
        subject?.let {
            Text(
                text = it,
                fontWeight = FontWeight.Bold,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = StdHorzSpacer)

        Crossfade(targetState = status, label = "RenderLiveActivityEventInner") {
            when (it) {
                STATUS_LIVE -> {
                    media?.let {
                        CrossfadeCheckIfUrlIsOnline(it, accountViewModel) {
                            LiveFlag()
                        }
                    }
                }
                STATUS_PLANNED -> {
                    ScheduledFlag(starts)
                }
            }
        }
    }

    var participantUsers by remember {
        mutableStateOf<ImmutableList<Pair<Participant, User>>>(
            persistentListOf()
        )
    }

    LaunchedEffect(key1 = eventUpdates) {
        accountViewModel.loadParticipants(participants) { newParticipantUsers ->
            if (!equalImmutableLists(newParticipantUsers, participantUsers)) {
                participantUsers = newParticipantUsers
            }
        }
    }

    participantUsers.forEach {
        Row(
            verticalAlignment = CenterVertically,
            modifier = Modifier
                .padding(top = 5.dp, start = 10.dp, end = 10.dp)
                .clickable {
                    nav("User/${it.second.pubkeyHex}")
                }
        ) {
            ClickableUserPicture(it.second, 25.dp, accountViewModel)
            Spacer(StdHorzSpacer)
            UsernameDisplay(it.second, Modifier.weight(1f))
            Spacer(StdHorzSpacer)
            it.first.role?.let {
                Text(
                    text = it.capitalize(Locale.ROOT),
                    color = MaterialTheme.colorScheme.placeholderText,
                    maxLines = 1
                )
            }
        }
    }

    media?.let { media ->
        if (status == STATUS_LIVE) {
            CheckIfUrlIsOnline(media, accountViewModel) { isOnline ->
                if (isOnline) {
                    Row(
                        verticalAlignment = CenterVertically
                    ) {
                        VideoView(
                            videoUri = media,
                            title = subject,
                            artworkUri = cover,
                            authorName = baseNote.author?.toBestDisplayName(),
                            roundedCorner = true,
                            accountViewModel = accountViewModel,
                            nostrUriCallback = "nostr:${baseNote.toNEvent()}"
                        )
                    }
                } else {
                    Row(
                        verticalAlignment = CenterVertically,
                        modifier = Modifier
                            .padding(10.dp)
                            .height(100.dp)
                    ) {
                        Text(
                            text = stringResource(id = R.string.live_stream_is_offline),
                            color = MaterialTheme.colorScheme.onBackground,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        } else if (status == STATUS_ENDED) {
            Row(
                verticalAlignment = CenterVertically,
                modifier = Modifier
                    .padding(10.dp)
                    .height(100.dp)
            ) {
                Text(
                    text = stringResource(id = R.string.live_stream_has_ended),
                    color = MaterialTheme.colorScheme.onBackground,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun LongFormHeader(noteEvent: LongTextNoteEvent, note: Note, accountViewModel: AccountViewModel) {
    val image = remember(noteEvent) { noteEvent.image() }
    val title = remember(noteEvent) { noteEvent.title() }
    val summary = remember(noteEvent) { noteEvent.summary()?.ifBlank { null } ?: noteEvent.content.take(200).ifBlank { null } }

    Row(
        modifier = Modifier
            .padding(top = Size5dp)
            .clip(shape = QuoteBorder)
            .border(
                1.dp,
                MaterialTheme.colorScheme.subtleBorder,
                QuoteBorder
            )
    ) {
        Column {
            val automaticallyShowUrlPreview = remember {
                accountViewModel.settings.showUrlPreview.value
            }

            if (automaticallyShowUrlPreview) {
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

            title?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 10.dp, end = 10.dp, top = 10.dp)
                )
            }

            summary?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 10.dp, end = 10.dp, bottom = 10.dp),
                    color = Color.Gray,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun RenderClassifieds(noteEvent: ClassifiedsEvent, note: Note, accountViewModel: AccountViewModel) {
    val image = remember(noteEvent) { noteEvent.image() }
    val title = remember(noteEvent) { noteEvent.title() }
    val summary = remember(noteEvent) { noteEvent.summary() ?: noteEvent.content.take(200).ifBlank { null } }
    val price = remember(noteEvent) { noteEvent.price() }
    val location = remember(noteEvent) { noteEvent.location() }

    Row(
        modifier = Modifier
            .clip(shape = QuoteBorder)
            .border(
                1.dp,
                MaterialTheme.colorScheme.subtleBorder,
                QuoteBorder
            )
    ) {
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

            Row(Modifier.padding(start = 10.dp, end = 10.dp, top = 10.dp), verticalAlignment = CenterVertically) {
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

            if (summary != null || location != null) {
                Row(Modifier.padding(start = 10.dp, end = 10.dp, top = 5.dp), verticalAlignment = CenterVertically) {
                    summary?.let {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier
                                .weight(1f),
                            color = Color.Gray,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

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
                }
            }

            Spacer(modifier = DoubleVertSpacer)
        }
    }
}

@Composable
fun CreateImageHeader(
    note: Note,
    accountViewModel: AccountViewModel
) {
    val banner = remember(note.author?.info) { note.author?.info?.banner }

    Box() {
        banner?.let {
            AsyncImage(
                model = it,
                contentDescription = stringResource(
                    R.string.preview_card_image_for,
                    it
                ),
                contentScale = ContentScale.FillWidth,
                modifier = Modifier.fillMaxWidth()
            )
        } ?: Image(
            painter = painterResource(R.drawable.profile_banner),
            contentDescription = stringResource(R.string.profile_banner),
            contentScale = ContentScale.FillWidth,
            modifier = remember {
                Modifier
                    .fillMaxWidth()
                    .height(150.dp)
            }
        )

        Box(
            remember {
                Modifier
                    .width(75.dp)
                    .height(75.dp)
                    .padding(10.dp)
                    .align(Alignment.BottomStart)
            }
        ) {
            NoteAuthorPicture(baseNote = note, accountViewModel = accountViewModel, size = Size55dp)
        }
    }
}
