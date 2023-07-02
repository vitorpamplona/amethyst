package com.vitorpamplona.amethyst.ui.note

import android.content.Intent
import android.graphics.Bitmap
import android.util.Log
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.foundation.text.ClickableText
import androidx.compose.material.Divider
import androidx.compose.material.DropdownMenu
import androidx.compose.material.DropdownMenuItem
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.LocalTextStyle
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.darkColors
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.lightColors
import androidx.compose.material.ripple.rememberRipple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
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
import androidx.compose.ui.Alignment.Companion.CenterVertically
import androidx.compose.ui.Alignment.Companion.TopEnd
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import androidx.core.graphics.get
import androidx.lifecycle.distinctUntilChanged
import androidx.lifecycle.map
import coil.compose.AsyncImage
import coil.compose.AsyncImagePainter
import coil.request.SuccessResult
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.model.Channel
import com.vitorpamplona.amethyst.model.LocalCache
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.model.User
import com.vitorpamplona.amethyst.model.UserMetadata
import com.vitorpamplona.amethyst.service.OnlineChecker
import com.vitorpamplona.amethyst.service.model.AppDefinitionEvent
import com.vitorpamplona.amethyst.service.model.AudioTrackEvent
import com.vitorpamplona.amethyst.service.model.BadgeAwardEvent
import com.vitorpamplona.amethyst.service.model.BadgeDefinitionEvent
import com.vitorpamplona.amethyst.service.model.BaseTextNoteEvent
import com.vitorpamplona.amethyst.service.model.ChannelCreateEvent
import com.vitorpamplona.amethyst.service.model.ChannelMessageEvent
import com.vitorpamplona.amethyst.service.model.ChannelMetadataEvent
import com.vitorpamplona.amethyst.service.model.FileHeaderEvent
import com.vitorpamplona.amethyst.service.model.FileStorageHeaderEvent
import com.vitorpamplona.amethyst.service.model.GenericRepostEvent
import com.vitorpamplona.amethyst.service.model.HighlightEvent
import com.vitorpamplona.amethyst.service.model.LiveActivitiesChatMessageEvent
import com.vitorpamplona.amethyst.service.model.LiveActivitiesEvent
import com.vitorpamplona.amethyst.service.model.LongTextNoteEvent
import com.vitorpamplona.amethyst.service.model.Participant
import com.vitorpamplona.amethyst.service.model.PeopleListEvent
import com.vitorpamplona.amethyst.service.model.PinListEvent
import com.vitorpamplona.amethyst.service.model.PollNoteEvent
import com.vitorpamplona.amethyst.service.model.PrivateDmEvent
import com.vitorpamplona.amethyst.service.model.ReactionEvent
import com.vitorpamplona.amethyst.service.model.RelaySetEvent
import com.vitorpamplona.amethyst.service.model.ReportEvent
import com.vitorpamplona.amethyst.service.model.RepostEvent
import com.vitorpamplona.amethyst.service.model.TextNoteEvent
import com.vitorpamplona.amethyst.ui.actions.ImmutableListOfLists
import com.vitorpamplona.amethyst.ui.actions.NewRelayListView
import com.vitorpamplona.amethyst.ui.actions.toImmutableListOfLists
import com.vitorpamplona.amethyst.ui.components.ClickableUrl
import com.vitorpamplona.amethyst.ui.components.CreateClickableTextWithEmoji
import com.vitorpamplona.amethyst.ui.components.CreateTextWithEmoji
import com.vitorpamplona.amethyst.ui.components.LoadThumbAndThenVideoView
import com.vitorpamplona.amethyst.ui.components.ObserveDisplayNip05Status
import com.vitorpamplona.amethyst.ui.components.RobohashAsyncImage
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
import com.vitorpamplona.amethyst.ui.screen.loggedIn.LiveFlag
import com.vitorpamplona.amethyst.ui.screen.loggedIn.ReportNoteDialog
import com.vitorpamplona.amethyst.ui.screen.loggedIn.ScheduledFlag
import com.vitorpamplona.amethyst.ui.theme.BitcoinOrange
import com.vitorpamplona.amethyst.ui.theme.DividerThickness
import com.vitorpamplona.amethyst.ui.theme.DoubleHorzSpacer
import com.vitorpamplona.amethyst.ui.theme.DoubleVertSpacer
import com.vitorpamplona.amethyst.ui.theme.Font14SP
import com.vitorpamplona.amethyst.ui.theme.HalfPadding
import com.vitorpamplona.amethyst.ui.theme.HalfVertSpacer
import com.vitorpamplona.amethyst.ui.theme.QuoteBorder
import com.vitorpamplona.amethyst.ui.theme.ShowMoreRelaysButtonBoxModifer
import com.vitorpamplona.amethyst.ui.theme.ShowMoreRelaysButtonIconButtonModifier
import com.vitorpamplona.amethyst.ui.theme.ShowMoreRelaysButtonIconModifier
import com.vitorpamplona.amethyst.ui.theme.Size15Modifier
import com.vitorpamplona.amethyst.ui.theme.Size24Modifier
import com.vitorpamplona.amethyst.ui.theme.Size25dp
import com.vitorpamplona.amethyst.ui.theme.Size30Modifier
import com.vitorpamplona.amethyst.ui.theme.Size30dp
import com.vitorpamplona.amethyst.ui.theme.Size35dp
import com.vitorpamplona.amethyst.ui.theme.Size55Modifier
import com.vitorpamplona.amethyst.ui.theme.Size55dp
import com.vitorpamplona.amethyst.ui.theme.StdHorzSpacer
import com.vitorpamplona.amethyst.ui.theme.StdStartPadding
import com.vitorpamplona.amethyst.ui.theme.StdVertSpacer
import com.vitorpamplona.amethyst.ui.theme.UserNameMaxRowHeight
import com.vitorpamplona.amethyst.ui.theme.UserNameRowHeight
import com.vitorpamplona.amethyst.ui.theme.WidthAuthorPictureModifier
import com.vitorpamplona.amethyst.ui.theme.lessImportantLink
import com.vitorpamplona.amethyst.ui.theme.mediumImportanceLink
import com.vitorpamplona.amethyst.ui.theme.newItemBackgroundColor
import com.vitorpamplona.amethyst.ui.theme.placeholderText
import com.vitorpamplona.amethyst.ui.theme.replyBackground
import com.vitorpamplona.amethyst.ui.theme.replyModifier
import com.vitorpamplona.amethyst.ui.theme.repostProfileBorder
import com.vitorpamplona.amethyst.ui.theme.subtleBorder
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import nostr.postr.toNpub
import java.io.File
import java.math.BigDecimal
import java.net.URL
import java.util.Locale
import kotlin.time.ExperimentalTime
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
    parentBackgroundColor: MutableState<Color>? = null,
    accountViewModel: AccountViewModel,
    nav: (String) -> Unit
) {
    val isBlank by baseNote.live().metadata.map {
        it.note.event == null
    }.distinctUntilChanged().observeAsState(baseNote.event == null)

    Crossfade(targetState = isBlank) {
        if (it) {
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
        } else {
            CheckHiddenNoteCompose(
                baseNote,
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
    parentBackgroundColor: MutableState<Color>? = null,
    accountViewModel: AccountViewModel,
    nav: (String) -> Unit
) {
    val isHidden by accountViewModel.accountLiveData.map {
        accountViewModel.isNoteHidden(note)
    }.distinctUntilChanged().observeAsState(accountViewModel.isNoteHidden(note))

    Crossfade(targetState = isHidden) {
        if (!it) {
            LoadedNoteCompose(
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
}

@Immutable
data class NoteComposeReportState(
    val isAcceptable: Boolean,
    val canPreview: Boolean,
    val relevantReports: ImmutableSet<Note>
)

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
            NoteComposeReportState(
                isAcceptable = true,
                canPreview = true,
                relevantReports = persistentSetOf()
            )
        )
    }

    val scope = rememberCoroutineScope()

    WatchForReports(note, accountViewModel) { newIsAcceptable, newCanPreview, newRelevantReports ->
        if (newIsAcceptable != state.isAcceptable || newCanPreview != state.canPreview) {
            val newState = NoteComposeReportState(newIsAcceptable, newCanPreview, newRelevantReports)
            scope.launch(Dispatchers.Main) {
                state = newState
            }
        }
    }

    Crossfade(targetState = state) {
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
    state: NoteComposeReportState,
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

    Crossfade(targetState = !state.isAcceptable && !showReportedNote) { showHiddenNote ->
        if (showHiddenNote) {
            HiddenNote(
                state.relevantReports,
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
    onChange: (Boolean, Boolean, ImmutableSet<Note>) -> Unit
) {
    val userFollowsState by accountViewModel.userFollows.observeAsState()
    val noteReportsState by note.live().reports.observeAsState()

    LaunchedEffect(key1 = noteReportsState, key2 = userFollowsState) {
        launch(Dispatchers.Default) {
            accountViewModel.isNoteAcceptable(note, onChange)
        }
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
    when (baseNote.event) {
        is ChannelCreateEvent, is ChannelMetadataEvent -> ChannelHeader(
            channelNote = baseNote,
            showVideo = !makeItShort,
            showBottomDiviser = true,
            accountViewModel = accountViewModel,
            nav = nav
        )
        is BadgeDefinitionEvent -> BadgeDisplay(baseNote = baseNote)
        is FileHeaderEvent -> FileHeaderDisplay(baseNote)
        is FileStorageHeaderEvent -> FileStorageHeaderDisplay(baseNote)
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
    val newItemColor = MaterialTheme.colors.newItemBackgroundColor
    val defaultBackgroundColor = MaterialTheme.colors.background
    val backgroundColor = remember { mutableStateOf<Color>(defaultBackgroundColor) }

    LaunchedEffect(key1 = routeForLastRead, key2 = parentBackgroundColor?.value) {
        launch(Dispatchers.IO) {
            routeForLastRead?.let {
                val lastTime = accountViewModel.account.loadLastRead(it)

                val createdAt = baseNote.createdAt()
                if (createdAt != null) {
                    accountViewModel.account.markAsRead(it, createdAt)

                    val isNew = createdAt > lastTime

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
private fun ClickableNote(
    baseNote: Note,
    modifier: Modifier,
    backgroundColor: MutableState<Color>,
    accountViewModel: AccountViewModel,
    showPopup: () -> Unit,
    nav: (String) -> Unit,
    content: @Composable () -> Unit
) {
    val scope = rememberCoroutineScope()

    val updatedModifier = remember(backgroundColor.value) {
        modifier
            .combinedClickable(
                onClick = {
                    scope.launch {
                        routeFor(baseNote, accountViewModel.userProfile())?.let {
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

@OptIn(ExperimentalTime::class)
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
            Modifier.fillMaxWidth()
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

    Spacer(modifier = HalfVertSpacer)

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
    when (remember { baseNote.event }) {
        is AppDefinitionEvent -> {
            RenderAppDefinition(baseNote, accountViewModel, nav)
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

        is AudioTrackEvent -> {
            RenderAudioTrack(baseNote, accountViewModel, nav)
        }

        is PinListEvent -> {
            RenderPinListEvent(baseNote, backgroundColor, accountViewModel, nav)
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
            return "Channel/$it"
        }
    } else if (noteEvent is PrivateDmEvent) {
        return "Room/${noteEvent.talkingWith(loggedIn.pubkeyHex)}"
    } else {
        return "Note/${note.idHex}"
    }

    return null
}

fun routeFor(user: User): String {
    return "User/${user.pubkeyHex}"
}

fun authorRouteFor(note: Note): String {
    return "User/${note.author?.pubkeyHex}"
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
    val eventContent = remember(note.event) {
        val subject = (note.event as? TextNoteEvent)?.subject()?.ifEmpty { null }
        val body = accountViewModel.decrypt(note)

        if (subject != null) {
            "## $subject\n$body"
        } else {
            body
        }
    }

    if (eventContent != null) {
        val isAuthorTheLoggedUser = remember(note.event) { accountViewModel.isLoggedUser(note.author) }

        if (makeItShort && isAuthorTheLoggedUser) {
            Text(
                text = eventContent,
                color = MaterialTheme.colors.placeholderText,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        } else {
            SensitivityWarning(
                note = note,
                accountViewModel = accountViewModel
            ) {
                val modifier = remember(note) { Modifier.fillMaxWidth() }
                val tags = remember(note) { note.event?.tags()?.toImmutableListOfLists() ?: ImmutableListOfLists() }

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
    val eventContent = remember { noteEvent.content() }

    if (makeItShort && accountViewModel.isLoggedUser(note.author)) {
        Text(
            text = eventContent,
            color = MaterialTheme.colors.placeholderText,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    } else {
        val tags = remember(note) { note.event?.tags()?.toImmutableListOfLists() ?: ImmutableListOfLists() }

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
                    ZoomableImageDialog(imageUrl = figureOutMimeType(it.banner!!), onDismiss = { zoomImageDialogOpen = false })
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
                                        MaterialTheme.colors.background,
                                        CircleShape
                                    )
                                    .clip(shape = CircleShape)
                                    .fillMaxSize()
                                    .background(MaterialTheme.colors.background)
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
                        ZoomableImageDialog(imageUrl = figureOutMimeType(it.banner!!), onDismiss = { zoomImageDialogOpen = false })
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
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            tint = MaterialTheme.colors.placeholderText,
                            imageVector = Icons.Default.Link,
                            contentDescription = stringResource(R.string.website),
                            modifier = Modifier.size(16.dp)
                        )

                        ClickableText(
                            text = AnnotatedString(website.removePrefix("https://")),
                            onClick = { website.let { runCatching { uri.openUri(it) } } },
                            style = LocalTextStyle.current.copy(color = MaterialTheme.colors.primary),
                            modifier = Modifier.padding(top = 1.dp, bottom = 1.dp, start = 5.dp)
                        )
                    }
                }

                it.about?.let {
                    Row(
                        modifier = Modifier.padding(top = 5.dp, bottom = 5.dp)
                    ) {
                        val tags = remember(note) { note.event?.tags()?.toImmutableListOfLists() ?: ImmutableListOfLists() }
                        val bgColor = MaterialTheme.colors.background
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

    DisplayHighlight(
        quote,
        author,
        url,
        makeItShort,
        canPreview,
        backgroundColor,
        accountViewModel,
        nav
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
        val eventContent = remember { accountViewModel.decrypt(note) }

        val hashtags = remember(note.event?.id()) { note.event?.hashtags()?.toImmutableList() ?: persistentListOf() }
        val modifier = remember(note.event?.id()) { Modifier.fillMaxWidth() }
        val isAuthorTheLoggedUser = remember(note.event?.id()) { accountViewModel.isLoggedUser(note.author) }

        val tags = remember(note) { note.event?.tags()?.toImmutableListOfLists() ?: ImmutableListOfLists() }

        if (eventContent != null) {
            if (makeItShort && isAuthorTheLoggedUser) {
                Text(
                    text = eventContent,
                    color = MaterialTheme.colors.placeholderText,
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
            ImmutableListOfLists(),
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

    val relays by remember {
        mutableStateOf<ImmutableList<String>>(
            noteEvent.relays().toImmutableList()
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
                        relay.trim().removePrefix("wss://").removePrefix("ws://").removeSuffix("/"),
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .padding(start = 10.dp, bottom = 5.dp)
                            .weight(1f)
                    )

                    Column(modifier = Modifier.padding(start = 10.dp)) {
                        RelayOptionsAction(relay, accountViewModel, nav)
                    }
                }
            }
        }

        if (relays.size > 3 && !expanded) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
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

    var members by remember { mutableStateOf<List<User>>(listOf()) }

    val account = accountViewModel.userProfile()
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
        launch(Dispatchers.IO) {
            members = noteEvent.bookmarkedPeople().mapNotNull { hex ->
                LocalCache.checkGetOrCreateUser(hex)
            }.sortedBy { account.isFollowing(it) }.reversed()
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
                verticalAlignment = Alignment.CenterVertically,
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

    val account = accountViewModel.userProfile()

    Text(text = stringResource(R.string.award_granted_to))

    LaunchedEffect(key1 = note) {
        launch(Dispatchers.IO) {
            awardees = noteEvent.awardees().mapNotNull { hex ->
                LocalCache.checkGetOrCreateUser(hex)
            }.sortedBy { account.isFollowing(it) }.reversed()
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
                verticalAlignment = Alignment.CenterVertically
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
private fun RenderPinListEvent(
    baseNote: Note,
    backgroundColor: MutableState<Color>,
    accountViewModel: AccountViewModel,
    nav: (String) -> Unit
) {
    PinListHeader(baseNote, backgroundColor, accountViewModel, nav)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PinListHeader(
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
                    Icon(
                        imageVector = Icons.Default.PushPin,
                        contentDescription = null,
                        tint = MaterialTheme.colors.onBackground.copy(0.32f),
                        modifier = Modifier.size(15.dp)
                    )

                    Spacer(modifier = Modifier.width(5.dp))

                    TranslatableRichTextViewer(
                        content = pin,
                        canPreview = true,
                        tags = remember { ImmutableListOfLists() },
                        backgroundColor = backgroundColor,
                        accountViewModel = accountViewModel,
                        nav = nav
                    )
                }
            }
        }

        if (pins.size > 3 && !expanded) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
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

    AudioTrackHeader(noteEvent, accountViewModel, nav)
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
        modifier = remember { Modifier },
        tags = remember { ImmutableListOfLists() },
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
                    MaterialTheme.colors.subtleBorder,
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

    val showReply by remember {
        derivedStateOf {
            noteEvent is TextNoteEvent && (note.replyTo != null || noteEvent.hasAnyTaggedUser())
        }
    }

    if (showReply) {
        val replyingDirectlyTo = remember { note.replyTo?.lastOrNull() }
        if (replyingDirectlyTo != null && unPackReply) {
            ReplyNoteComposition(replyingDirectlyTo, backgroundColor, accountViewModel, nav)
            Spacer(modifier = StdVertSpacer)
        } else {
            // ReplyInformation(note.replyTo, noteEvent.mentions(), accountViewModel, nav)
        }
    } else {
        val showChannelReply by remember {
            derivedStateOf {
                (noteEvent is ChannelMessageEvent && (note.replyTo != null || noteEvent.hasAnyTaggedUser())) ||
                    (noteEvent is LiveActivitiesChatMessageEvent && (note.replyTo != null || noteEvent.hasAnyTaggedUser()))
            }
        }

        if (showChannelReply) {
            val channelHex = note.channelHex()
            channelHex?.let {
                ChannelHeader(
                    channelHex = channelHex,
                    showVideo = false,
                    showBottomDiviser = false,
                    modifier = remember { Modifier.padding(vertical = 5.dp) },
                    accountViewModel = accountViewModel,
                    nav = nav
                )

                val replies = remember { note.replyTo?.toImmutableList() }
                val mentions = remember { (note.event as? BaseTextNoteEvent)?.mentions()?.toImmutableList() ?: persistentListOf() }

                ReplyInformationChannel(replies, mentions, accountViewModel, nav)
            }
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
    val defaultReplyBackground = MaterialTheme.colors.replyBackground

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
        modifier = MaterialTheme.colors.replyModifier,
        unPackReply = false,
        makeItShort = true,
        parentBackgroundColor = replyBackgroundColor,
        accountViewModel = accountViewModel,
        nav = nav
    )
}

@Composable
private fun SecondUserInfoRow(
    note: Note,
    accountViewModel: AccountViewModel,
    nav: (String) -> Unit
) {
    val noteEvent = remember { note.event } ?: return
    val noteAuthor = remember { note.author } ?: return

    Row(verticalAlignment = CenterVertically, modifier = UserNameMaxRowHeight) {
        ObserveDisplayNip05Status(noteAuthor, remember { Modifier.weight(1f) })

        val baseReward = remember { noteEvent.getReward()?.let { Reward(it) } }
        if (baseReward != null) {
            DisplayReward(baseReward, note, accountViewModel, nav)
        }

        val pow = remember { noteEvent.getPoWRank() }
        if (pow > 20) {
            DisplayPoW(pow)
        }
    }
}

@Composable
private fun FirstUserInfoRow(
    baseNote: Note,
    showAuthorPicture: Boolean,
    accountViewModel: AccountViewModel,
    nav: (String) -> Unit
) {
    Row(verticalAlignment = CenterVertically, modifier = remember { UserNameRowHeight }) {
        val isRepost by remember {
            derivedStateOf {
                baseNote.event is RepostEvent || baseNote.event is GenericRepostEvent
            }
        }

        if (showAuthorPicture) {
            NoteAuthorPicture(baseNote, nav, accountViewModel, Size25dp)
            Spacer(HalfPadding)
            NoteUsernameDisplay(baseNote, remember { Modifier.weight(1f) })
        } else {
            NoteUsernameDisplay(baseNote, remember { Modifier.weight(1f) })
        }

        if (isRepost) {
            BoostedMark()
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
        color = MaterialTheme.colors.placeholderText,
        maxLines = 1,
        modifier = StdStartPadding
    )
}

@Composable
private fun MoreOptionsButton(
    baseNote: Note,
    accountViewModel: AccountViewModel
) {
    var moreActionsExpanded by remember { mutableStateOf(false) }

    IconButton(
        modifier = Size24Modifier,
        onClick = { moreActionsExpanded = true }
    ) {
        Icon(
            imageVector = Icons.Default.MoreVert,
            null,
            modifier = Size15Modifier,
            tint = MaterialTheme.colors.placeholderText
        )

        NoteDropDownMenu(
            baseNote,
            moreActionsExpanded,
            { moreActionsExpanded = false },
            accountViewModel
        )
    }
}

@Composable
fun TimeAgo(note: Note) {
    val time = remember { note.createdAt() } ?: return
    TimeAgo(time)
}

@Composable
fun TimeAgo(time: Long) {
    val context = LocalContext.current
    val timeStr by remember { mutableStateOf(timeAgo(time, context = context)) }

    Text(
        text = timeStr,
        color = MaterialTheme.colors.placeholderText,
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
    val isRepost by remember {
        derivedStateOf {
            baseNote.event is RepostEvent || baseNote.event is GenericRepostEvent
        }
    }

    if (isRepost) {
        val baseReply by remember {
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
    NoteAuthorPicture(baseNote, nav, accountViewModel, Size55dp)

    val isRepost = baseNote.event is RepostEvent || baseNote.event is GenericRepostEvent

    if (isRepost) {
        RepostNoteAuthorPicture(baseNote, accountViewModel, nav)
    }

    val isChannel = baseNote.event is ChannelMessageEvent && baseNote.channelHex() != null

    if (isChannel) {
        val baseChannelHex = remember { baseNote.channelHex() }
        if (baseChannelHex != null) {
            LoadChannel(baseChannelHex) { channel ->
                ChannelNotePicture(channel)
            }
        }
    }
}

@Composable
fun LoadChannel(baseChannelHex: String, content: @Composable (Channel) -> Unit) {
    var channel by remember(baseChannelHex) {
        mutableStateOf<Channel?>(LocalCache.getChannelIfExists(baseChannelHex))
    }

    if (channel == null) {
        LaunchedEffect(key1 = baseChannelHex) {
            launch(Dispatchers.IO) {
                val newChannel = LocalCache.checkGetOrCreateChannel(baseChannelHex)
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
private fun ChannelNotePicture(baseChannel: Channel) {
    val model by baseChannel.live.map {
        it.channel.profilePicture()
    }.distinctUntilChanged().observeAsState()

    val backgroundColor = MaterialTheme.colors.background

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
            modifier = modifier
        )
    }
}

@Composable
private fun RepostNoteAuthorPicture(
    baseNote: Note,
    accountViewModel: AccountViewModel,
    nav: (String) -> Unit
) {
    val baseRepost by remember {
        derivedStateOf {
            baseNote.replyTo?.lastOrNull()
        }
    }

    baseRepost?.let {
        Box(Size30Modifier) {
            NoteAuthorPicture(
                baseNote = it,
                nav = nav,
                accountViewModel = accountViewModel,
                size = Size30dp,
                pictureModifier = MaterialTheme.colors.repostProfileBorder
            )
        }
    }
}

@Composable
fun DisplayHighlight(
    highlight: String,
    authorHex: String?,
    url: String?,
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
        remember { ImmutableListOfLists<String>(emptyList()) },
        backgroundColor,
        accountViewModel,
        nav
    )

    DisplayQuoteAuthor(authorHex ?: "", url, nav)
}

@Composable
private fun DisplayQuoteAuthor(
    authorHex: String,
    url: String?,
    nav: (String) -> Unit
) {
    var userBase by remember { mutableStateOf<User?>(LocalCache.getUserIfExists(authorHex)) }

    LaunchedEffect(Unit) {
        if (userBase == null) {
            launch(Dispatchers.IO) {
                val newUserBase = LocalCache.checkGetOrCreateUser(authorHex)
                launch(Dispatchers.Main) {
                    userBase = newUserBase
                }
            }
        }
    }

    Row {
        userBase?.let { userBase ->
            LoadAndDisplayUser(userBase, nav)
        }

        url?.let { url ->
            LoadAndDisplayUrl(url)
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
        Text(remember { "on " }, maxLines = 1)
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
fun DisplayFollowingHashtagsInPost(
    baseNote: Note,
    accountViewModel: AccountViewModel,
    nav: (String) -> Unit
) {
    val noteEvent = remember { baseNote.event } ?: return

    val accountState by accountViewModel.accountLiveData.observeAsState()
    var firstTag by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(key1 = accountState) {
        launch(Dispatchers.Default) {
            val followingTags = accountState?.account?.followingTagSet() ?: emptySet()
            val newFirstTag = noteEvent.firstIsTaggedHashes(followingTags)

            if (firstTag != newFirstTag) {
                launch(Dispatchers.Main) {
                    firstTag = newFirstTag
                }
            }
        }
    }

    firstTag?.let {
        Column() {
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
            color = MaterialTheme.colors.primary.copy(
                alpha = 0.52f
            )
        ),
        maxLines = 1
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DisplayUncitedHashtags(
    hashtags: ImmutableList<String>,
    eventContent: String,
    nav: (String) -> Unit
) {
    val hasHashtags = remember {
        hashtags.isNotEmpty()
    }

    if (hasHashtags) {
        FlowRow(
            modifier = remember { Modifier.padding(top = 5.dp) }
        ) {
            hashtags.forEach { hashtag ->
                if (!eventContent.contains(hashtag, true)) {
                    ClickableText(
                        text = AnnotatedString("#$hashtag "),
                        onClick = { nav("Hashtag/$hashtag") },
                        style = LocalTextStyle.current.copy(
                            color = MaterialTheme.colors.primary.copy(
                                alpha = 0.52f
                            )
                        )
                    )
                }
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
        color = MaterialTheme.colors.lessImportantLink,
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
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.clickable { popupExpanded = true }
        ) {
            ClickableText(
                text = AnnotatedString("#bounty"),
                onClick = { nav("Hashtag/bounty") },
                style = LocalTextStyle.current.copy(
                    color = MaterialTheme.colors.primary.copy(
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
        Icon(
            imageVector = Icons.Default.Bolt,
            contentDescription = stringResource(R.string.zaps),
            modifier = Modifier.size(20.dp),
            tint = BitcoinOrange
        )
    } else {
        Icon(
            imageVector = Icons.Default.Bolt,
            contentDescription = stringResource(R.string.zaps),
            modifier = Modifier.size(20.dp),
            tint = MaterialTheme.colors.placeholderText
        )
    }

    Text(
        text = reward,
        color = MaterialTheme.colors.placeholderText,
        maxLines = 1
    )
}

@Composable
fun BadgeDisplay(baseNote: Note) {
    val background = MaterialTheme.colors.background
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
                    lightColors().onBackground
                } else {
                    darkColors().onBackground
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
                MaterialTheme.colors.mediumImportanceLink,
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
                style = MaterialTheme.typography.body1,
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
                style = MaterialTheme.typography.caption,
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
fun FileHeaderDisplay(note: Note) {
    val event = (note.event as? FileHeaderEvent) ?: return
    val fullUrl = event.url() ?: return

    var content by remember { mutableStateOf<ZoomableContent?>(null) }

    if (content == null) {
        LaunchedEffect(key1 = event.id) {
            launch(Dispatchers.IO) {
                val blurHash = event.blurhash()
                val hash = event.hash()
                val dimensions = event.dimensions()
                val description = event.content
                val removedParamsFromUrl = fullUrl.split("?")[0].lowercase()
                val isImage = imageExtensions.any { removedParamsFromUrl.endsWith(it) }
                val uri = "nostr:" + note.toNEvent()
                val newContent = if (isImage) {
                    ZoomableUrlImage(fullUrl, description, hash, blurHash, dimensions, uri)
                } else {
                    ZoomableUrlVideo(fullUrl, description, hash, uri)
                }

                launch(Dispatchers.Main) {
                    content = newContent
                }
            }
        }
    }

    Crossfade(targetState = content) {
        if (it != null) {
            ZoomableContentView(content = it)
        }
    }
}

@Composable
fun FileStorageHeaderDisplay(baseNote: Note) {
    val eventHeader = (baseNote.event as? FileStorageHeaderEvent) ?: return

    var fileNote by remember { mutableStateOf<Note?>(null) }

    if (fileNote == null) {
        LaunchedEffect(key1 = eventHeader.id) {
            launch(Dispatchers.IO) {
                val newFileNote = eventHeader.dataEventId()?.let { LocalCache.checkGetOrCreateNote(it) }
                launch(Dispatchers.Main) {
                    fileNote = newFileNote
                }
            }
        }
    }

    Crossfade(targetState = fileNote) {
        if (it != null) {
            RenderNIP95(it, eventHeader, baseNote)
        }
    }
}

@Composable
private fun RenderNIP95(
    it: Note,
    eventHeader: FileStorageHeaderEvent,
    baseNote: Note
) {
    val appContext = LocalContext.current.applicationContext

    val noteState by it.live().metadata.observeAsState()
    val note = remember(noteState) { noteState?.note }

    var content by remember { mutableStateOf<ZoomableContent?>(null) }

    if (content == null) {
        LaunchedEffect(key1 = eventHeader.id, key2 = noteState, key3 = note?.event) {
            launch(Dispatchers.IO) {
                val uri = "nostr:" + baseNote.toNEvent()
                val localDir =
                    note?.idHex?.let { File(File(appContext.externalCacheDir, "NIP95"), it) }
                val blurHash = eventHeader.blurhash()
                val dimensions = eventHeader.dimensions()
                val description = eventHeader.content
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
                        uri = uri
                    )
                }

                launch(Dispatchers.Main) {
                    content = newContent
                }
            }
        }
    }

    Crossfade(targetState = content) {
        if (it != null) {
            ZoomableContentView(content = it)
        }
    }
}

@Composable
fun AudioTrackHeader(noteEvent: AudioTrackEvent, accountViewModel: AccountViewModel, nav: (String) -> Unit) {
    val media = remember { noteEvent.media() }
    val cover = remember { noteEvent.cover() }
    val subject = remember { noteEvent.subject() }
    val content = remember { noteEvent.content() }
    val participants = remember { noteEvent.participants() }

    var participantUsers by remember { mutableStateOf<List<Pair<Participant, User>>>(emptyList()) }

    LaunchedEffect(key1 = participants) {
        launch(Dispatchers.IO) {
            participantUsers = participants.mapNotNull { part -> LocalCache.checkGetOrCreateUser(part.key)?.let { Pair(part, it) } }
        }
    }

    Row(modifier = Modifier.padding(top = 5.dp)) {
        Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Row() {
                subject?.let {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 5.dp, bottom = 5.dp)) {
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
                    verticalAlignment = Alignment.CenterVertically,
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
                            color = MaterialTheme.colors.placeholderText,
                            maxLines = 1
                        )
                    }
                }
            }

            media?.let { media ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(10.dp)
                ) {
                    cover?.let { cover ->
                        LoadThumbAndThenVideoView(
                            videoUri = media,
                            description = noteEvent.subject(),
                            thumbUri = cover
                        )
                    }
                        ?: VideoView(
                            videoUri = media,
                            noteEvent.subject()
                        )
                }
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

    var isOnline by remember { mutableStateOf(false) }

    LaunchedEffect(key1 = media) {
        launch(Dispatchers.IO) {
            isOnline = OnlineChecker.isOnline(media)
        }
    }

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

        Crossfade(targetState = status) {
            when (it) {
                "live" -> {
                    if (isOnline) {
                        LiveFlag()
                    }
                }
                "planned" -> {
                    ScheduledFlag()
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
        launch(Dispatchers.IO) {
            val newParticipantUsers = participants.mapNotNull { part ->
                LocalCache.checkGetOrCreateUser(part.key)?.let { Pair(part, it) }
            }.toImmutableList()

            if (!equalImmutableLists(newParticipantUsers, participantUsers)) {
                participantUsers = newParticipantUsers
            }
        }
    }

    participantUsers.forEach {
        Row(
            verticalAlignment = Alignment.CenterVertically,
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
                    color = MaterialTheme.colors.placeholderText,
                    maxLines = 1
                )
            }
        }
    }

    media?.let { media ->
        if (status == "live") {
            if (isOnline) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(10.dp)
                ) {
                    VideoView(
                        videoUri = media,
                        description = subject
                    )
                }
            } else {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .padding(10.dp)
                        .height(100.dp)
                ) {
                    Text(
                        text = stringResource(id = R.string.live_stream_is_offline),
                        color = MaterialTheme.colors.onBackground,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        } else if (status == "ended") {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .padding(10.dp)
                    .height(100.dp)
            ) {
                Text(
                    text = stringResource(id = R.string.live_stream_has_ended),
                    color = MaterialTheme.colors.onBackground,
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
    val summary = remember(noteEvent) { noteEvent.summary() ?: noteEvent.content.take(200).ifBlank { null } }

    Row(
        modifier = Modifier
            .clip(shape = QuoteBorder)
            .border(
                1.dp,
                MaterialTheme.colors.subtleBorder,
                QuoteBorder
            )
    ) {
        Column {
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

            title?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.body1,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 10.dp, end = 10.dp, top = 10.dp)
                )
            }

            summary?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.caption,
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
private fun CreateImageHeader(
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

@Composable
private fun RelayBadges(baseNote: Note, accountViewModel: AccountViewModel, nav: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    var showShowMore by remember { mutableStateOf(false) }

    var lazyRelayList by remember {
        val baseNumber = baseNote.relays.map {
            it.removePrefix("wss://").removePrefix("ws://")
        }.toImmutableList()

        mutableStateOf(baseNumber)
    }
    var shortRelayList by remember {
        mutableStateOf(lazyRelayList.take(3).toImmutableList())
    }

    val scope = rememberCoroutineScope()

    WatchRelayLists(baseNote) { relayList ->
        if (!equalImmutableLists(relayList, lazyRelayList)) {
            scope.launch(Dispatchers.Main) {
                lazyRelayList = relayList
                shortRelayList = relayList.take(3).toImmutableList()
            }
        }

        val nextShowMore = relayList.size > 3
        if (nextShowMore != showShowMore) {
            scope.launch(Dispatchers.Main) {
                // only triggers recomposition when actually different
                showShowMore = nextShowMore
            }
        }
    }

    Spacer(DoubleVertSpacer)

    if (expanded) {
        VerticalRelayPanelWithFlow(lazyRelayList, accountViewModel, nav)
    } else {
        VerticalRelayPanelWithFlow(shortRelayList, accountViewModel, nav)
    }

    if (showShowMore && !expanded) {
        ShowMoreRelaysButton {
            expanded = true
        }
    }
}

@Composable
private fun WatchRelayLists(baseNote: Note, onListChanges: (ImmutableList<String>) -> Unit) {
    val noteRelaysState by baseNote.live().relays.observeAsState()

    LaunchedEffect(key1 = noteRelaysState) {
        launch(Dispatchers.IO) {
            val relayList = noteRelaysState?.note?.relays?.map {
                it.removePrefix("wss://").removePrefix("ws://")
            } ?: emptyList()

            onListChanges(relayList.toImmutableList())
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
@Stable
private fun VerticalRelayPanelWithFlow(
    relays: ImmutableList<String>,
    accountViewModel: AccountViewModel,
    nav: (String) -> Unit
) {
    // FlowRow Seems to be a lot faster than LazyVerticalGrid
    FlowRow() {
        relays.forEach { url ->
            RenderRelay(url, accountViewModel, nav)
        }
    }
}

@Composable
private fun ShowMoreRelaysButton(onClick: () -> Unit) {
    Row(
        modifier = ShowMoreRelaysButtonBoxModifer,
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.Top
    ) {
        IconButton(
            modifier = ShowMoreRelaysButtonIconButtonModifier,
            onClick = onClick
        ) {
            Icon(
                imageVector = Icons.Default.ExpandMore,
                null,
                modifier = ShowMoreRelaysButtonIconModifier,
                tint = MaterialTheme.colors.placeholderText
            )
        }
    }
}

@Composable
fun NoteAuthorPicture(
    baseNote: Note,
    nav: (String) -> Unit,
    accountViewModel: AccountViewModel,
    size: Dp,
    pictureModifier: Modifier = Modifier
) {
    NoteAuthorPicture(baseNote, size, accountViewModel, pictureModifier) {
        nav("User/${it.pubkeyHex}")
    }
}

@Composable
fun NoteAuthorPicture(
    baseNote: Note,
    size: Dp,
    accountViewModel: AccountViewModel,
    modifier: Modifier = Modifier,
    onClick: ((User) -> Unit)? = null
) {
    val author by baseNote.live().metadata.map {
        it.note.author
    }.distinctUntilChanged().observeAsState(baseNote.author)

    Crossfade(targetState = author) {
        if (it == null) {
            DisplayBlankAuthor(size, modifier)
        } else {
            ClickableUserPicture(it, size, accountViewModel, modifier, onClick)
        }
    }
}

@Composable
fun DisplayBlankAuthor(size: Dp, modifier: Modifier = Modifier) {
    val backgroundColor = MaterialTheme.colors.background

    val nullModifier = remember {
        modifier
            .size(size)
            .clip(shape = CircleShape)
            .background(backgroundColor)
    }

    RobohashAsyncImage(
        robot = "authornotfound",
        contentDescription = stringResource(R.string.unknown_author),
        modifier = nullModifier
    )
}

@Composable
fun UserPicture(
    user: User,
    size: Dp,
    pictureModifier: Modifier = remember { Modifier },
    accountViewModel: AccountViewModel,
    nav: (String) -> Unit
) {
    val route by remember {
        derivedStateOf {
            "User/${user.pubkeyHex}"
        }
    }

    val scope = rememberCoroutineScope()

    ClickableUserPicture(
        baseUser = user,
        size = size,
        accountViewModel = accountViewModel,
        modifier = pictureModifier,
        onClick = {
            scope.launch {
                nav(route)
            }
        }
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ClickableUserPicture(
    baseUser: User,
    size: Dp,
    accountViewModel: AccountViewModel,
    modifier: Modifier = remember { Modifier },
    onClick: ((User) -> Unit)? = null,
    onLongClick: ((User) -> Unit)? = null
) {
    val interactionSource = remember { MutableInteractionSource() }
    val ripple = rememberRipple(bounded = false, radius = size)

    // BaseUser is the same reference as accountState.user
    val myModifier = remember {
        if (onClick != null && onLongClick != null) {
            Modifier
                .size(size)
                .combinedClickable(
                    onClick = { onClick(baseUser) },
                    onLongClick = { onLongClick(baseUser) },
                    role = Role.Button,
                    interactionSource = interactionSource,
                    indication = ripple
                )
        } else if (onClick != null) {
            Modifier
                .size(size)
                .clickable(
                    onClick = { onClick(baseUser) },
                    role = Role.Button,
                    interactionSource = interactionSource,
                    indication = ripple
                )
        } else {
            Modifier.size(size)
        }
    }

    Box(modifier = myModifier, contentAlignment = TopEnd) {
        BaseUserPicture(baseUser, size, accountViewModel, modifier)
    }
}

@Composable
fun NonClickableUserPicture(
    baseUser: User,
    size: Dp,
    accountViewModel: AccountViewModel,
    modifier: Modifier = remember { Modifier }
) {
    val myBoxModifier = remember {
        Modifier.size(size)
    }

    Box(myBoxModifier, contentAlignment = TopEnd) {
        BaseUserPicture(baseUser, size, accountViewModel, modifier)
    }
}

@Composable
fun BaseUserPicture(
    baseUser: User,
    size: Dp,
    accountViewModel: AccountViewModel,
    modifier: Modifier = remember { Modifier }
) {
    val userPubkey = remember {
        baseUser.pubkeyHex
    }

    val userProfile by baseUser.live().metadata.map {
        it.user.profilePicture()
    }.distinctUntilChanged().observeAsState(baseUser.profilePicture())

    val myBoxModifier = remember {
        Modifier.size(size)
    }

    Box(myBoxModifier, contentAlignment = TopEnd) {
        PictureAndFollowingMark(
            userHex = userPubkey,
            userPicture = userProfile,
            size = size,
            modifier = modifier,
            accountViewModel = accountViewModel
        )
    }
}

@Composable
fun PictureAndFollowingMark(
    userHex: String,
    userPicture: String?,
    size: Dp,
    modifier: Modifier,
    accountViewModel: AccountViewModel
) {
    val backgroundColor = MaterialTheme.colors.background
    val myImageModifier = remember {
        modifier
            .size(size)
            .clip(shape = CircleShape)
            .background(backgroundColor)
    }

    RobohashAsyncImageProxy(
        robot = userHex,
        model = userPicture,
        contentDescription = stringResource(id = R.string.profile_image),
        modifier = myImageModifier,
        contentScale = ContentScale.Crop
    )

    val myIconSize by remember(size) {
        derivedStateOf {
            size.div(3.5f)
        }
    }
    ObserveAndDisplayFollowingMark(userHex, myIconSize, accountViewModel)
}

@Composable
fun ObserveAndDisplayFollowingMark(userHex: String, iconSize: Dp, accountViewModel: AccountViewModel) {
    WatchFollows(userHex, accountViewModel) {
        Crossfade(targetState = it) {
            if (it) {
                Box(contentAlignment = TopEnd) {
                    FollowingIcon(iconSize)
                }
            }
        }
    }
}

@Composable
fun WatchFollows(userHex: String, accountViewModel: AccountViewModel, onFollowChanges: @Composable (Boolean) -> Unit) {
    val showFollowingMark by accountViewModel.userFollows.map {
        it.user.isFollowingCached(userHex) || (userHex == accountViewModel.account.userProfile().pubkeyHex)
    }.distinctUntilChanged().observeAsState(
        accountViewModel.account.userProfile().isFollowingCached(userHex) || (userHex == accountViewModel.account.userProfile().pubkeyHex)
    )

    onFollowChanges(showFollowingMark)
}

@Composable
fun FollowingIcon(iconSize: Dp) {
    val modifier = remember {
        Modifier.size(iconSize)
    }

    Icon(
        painter = painterResource(R.drawable.verified_follow_shield),
        contentDescription = stringResource(id = R.string.following),
        modifier = modifier,
        tint = Color.Unspecified
    )
}

@Immutable
data class DropDownParams(
    val isFollowingAuthor: Boolean,
    val isPrivateBookmarkNote: Boolean,
    val isPublicBookmarkNote: Boolean,
    val isLoggedUser: Boolean,
    val isSensitive: Boolean,
    val showSensitiveContent: Boolean?
)

@Composable
fun NoteDropDownMenu(note: Note, popupExpanded: Boolean, onDismiss: () -> Unit, accountViewModel: AccountViewModel) {
    var reportDialogShowing by remember { mutableStateOf(false) }

    var state by remember {
        mutableStateOf<DropDownParams>(
            DropDownParams(
                isFollowingAuthor = false,
                isPrivateBookmarkNote = false,
                isPublicBookmarkNote = false,
                isLoggedUser = false,
                isSensitive = false,
                showSensitiveContent = null
            )
        )
    }

    DropdownMenu(
        expanded = popupExpanded,
        onDismissRequest = onDismiss
    ) {
        val clipboardManager = LocalClipboardManager.current
        val appContext = LocalContext.current.applicationContext
        val actContext = LocalContext.current

        WatchBookmarksFollowsAndAccount(note, accountViewModel) { newState ->
            if (state != newState) {
                state = newState
            }
        }

        val scope = rememberCoroutineScope()

        if (!state.isFollowingAuthor) {
            DropdownMenuItem(onClick = {
                accountViewModel.follow(
                    note.author ?: return@DropdownMenuItem
                ); onDismiss()
            }) {
                Text(stringResource(R.string.follow))
            }
            Divider()
        }
        DropdownMenuItem(
            onClick = {
                scope.launch(Dispatchers.IO) {
                    clipboardManager.setText(AnnotatedString(accountViewModel.decrypt(note) ?: ""))
                    onDismiss()
                }
            }
        ) {
            Text(stringResource(R.string.copy_text))
        }
        DropdownMenuItem(
            onClick = {
                scope.launch(Dispatchers.IO) {
                    clipboardManager.setText(AnnotatedString("nostr:${note.author?.pubkeyNpub()}"))
                    onDismiss()
                }
            }
        ) {
            Text(stringResource(R.string.copy_user_pubkey))
        }
        DropdownMenuItem(onClick = {
            scope.launch(Dispatchers.IO) {
                clipboardManager.setText(AnnotatedString("nostr:" + note.toNEvent()))
                onDismiss()
            }
        }) {
            Text(stringResource(R.string.copy_note_id))
        }
        DropdownMenuItem(onClick = {
            val sendIntent = Intent().apply {
                action = Intent.ACTION_SEND
                type = "text/plain"
                putExtra(
                    Intent.EXTRA_TEXT,
                    externalLinkForNote(note)
                )
                putExtra(Intent.EXTRA_TITLE, actContext.getString(R.string.quick_action_share_browser_link))
            }

            val shareIntent = Intent.createChooser(sendIntent, appContext.getString(R.string.quick_action_share))
            ContextCompat.startActivity(actContext, shareIntent, null)
            onDismiss()
        }) {
            Text(stringResource(R.string.quick_action_share))
        }
        Divider()
        if (state.isPrivateBookmarkNote) {
            DropdownMenuItem(onClick = { scope.launch(Dispatchers.IO) { accountViewModel.removePrivateBookmark(note); onDismiss() } }) {
                Text(stringResource(R.string.remove_from_private_bookmarks))
            }
        } else {
            DropdownMenuItem(onClick = { scope.launch(Dispatchers.IO) { accountViewModel.addPrivateBookmark(note); onDismiss() } }) {
                Text(stringResource(R.string.add_to_private_bookmarks))
            }
        }
        if (state.isPublicBookmarkNote) {
            DropdownMenuItem(onClick = { scope.launch(Dispatchers.IO) { accountViewModel.removePublicBookmark(note); onDismiss() } }) {
                Text(stringResource(R.string.remove_from_public_bookmarks))
            }
        } else {
            DropdownMenuItem(onClick = { scope.launch(Dispatchers.IO) { accountViewModel.addPublicBookmark(note); onDismiss() } }) {
                Text(stringResource(R.string.add_to_public_bookmarks))
            }
        }
        Divider()
        DropdownMenuItem(onClick = { scope.launch(Dispatchers.IO) { accountViewModel.broadcast(note); onDismiss() } }) {
            Text(stringResource(R.string.broadcast))
        }
        Divider()
        if (state.isLoggedUser) {
            DropdownMenuItem(onClick = { scope.launch(Dispatchers.IO) { accountViewModel.delete(note); onDismiss() } }) {
                Text(stringResource(R.string.request_deletion))
            }
        } else {
            DropdownMenuItem(onClick = { reportDialogShowing = true }) {
                Text("Block / Report")
            }
        }
        Divider()
        if (state.showSensitiveContent == null || state.showSensitiveContent == true) {
            DropdownMenuItem(onClick = { scope.launch(Dispatchers.IO) { accountViewModel.hideSensitiveContent(); onDismiss() } }) {
                Text(stringResource(R.string.content_warning_hide_all_sensitive_content))
            }
        }
        if (state.showSensitiveContent == null || state.showSensitiveContent == false) {
            DropdownMenuItem(onClick = { scope.launch(Dispatchers.IO) { accountViewModel.disableContentWarnings(); onDismiss() } }) {
                Text(stringResource(R.string.content_warning_show_all_sensitive_content))
            }
        }
        if (state.showSensitiveContent != null) {
            DropdownMenuItem(onClick = { scope.launch(Dispatchers.IO) { accountViewModel.seeContentWarnings(); onDismiss() } }) {
                Text(stringResource(R.string.content_warning_see_warnings))
            }
        }
    }

    if (reportDialogShowing) {
        ReportNoteDialog(note = note, accountViewModel = accountViewModel) {
            reportDialogShowing = false
            onDismiss()
        }
    }
}

@Composable
fun WatchBookmarksFollowsAndAccount(note: Note, accountViewModel: AccountViewModel, onNew: (DropDownParams) -> Unit) {
    val followState by accountViewModel.userProfile().live().follows.observeAsState()
    val bookmarkState by accountViewModel.userProfile().live().bookmarks.observeAsState()
    val accountState by accountViewModel.accountLiveData.observeAsState()

    LaunchedEffect(key1 = followState, key2 = bookmarkState, key3 = accountState) {
        launch(Dispatchers.IO) {
            val newState = DropDownParams(
                isFollowingAuthor = accountViewModel.isFollowing(note.author),
                isPrivateBookmarkNote = accountViewModel.isInPrivateBookmarks(note),
                isPublicBookmarkNote = accountViewModel.isInPublicBookmarks(note),
                isLoggedUser = accountViewModel.isLoggedUser(note.author),
                isSensitive = note.event?.isSensitive() ?: false,
                showSensitiveContent = accountState?.account?.showSensitiveContent
            )

            launch(Dispatchers.Main) {
                onNew(
                    newState
                )
            }
        }
    }
}
