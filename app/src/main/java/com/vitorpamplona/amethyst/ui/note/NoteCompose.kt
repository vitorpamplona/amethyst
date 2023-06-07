package com.vitorpamplona.amethyst.ui.note

import android.content.Intent
import android.graphics.Bitmap
import android.util.Log
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.ClickableText
import androidx.compose.material.Button
import androidx.compose.material.ButtonDefaults
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Alignment.Companion.CenterHorizontally
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
import coil.compose.AsyncImage
import coil.compose.AsyncImagePainter
import coil.request.SuccessResult
import com.vitorpamplona.amethyst.NotificationCache
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.model.Channel
import com.vitorpamplona.amethyst.model.LocalCache
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.model.User
import com.vitorpamplona.amethyst.model.UserMetadata
import com.vitorpamplona.amethyst.service.model.AppDefinitionEvent
import com.vitorpamplona.amethyst.service.model.AudioTrackEvent
import com.vitorpamplona.amethyst.service.model.BadgeAwardEvent
import com.vitorpamplona.amethyst.service.model.BadgeDefinitionEvent
import com.vitorpamplona.amethyst.service.model.ChannelCreateEvent
import com.vitorpamplona.amethyst.service.model.ChannelMessageEvent
import com.vitorpamplona.amethyst.service.model.ChannelMetadataEvent
import com.vitorpamplona.amethyst.service.model.EventInterface
import com.vitorpamplona.amethyst.service.model.FileHeaderEvent
import com.vitorpamplona.amethyst.service.model.FileStorageHeaderEvent
import com.vitorpamplona.amethyst.service.model.HighlightEvent
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
import com.vitorpamplona.amethyst.ui.components.ResizeImage
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
import com.vitorpamplona.amethyst.ui.screen.loggedIn.ReportNoteDialog
import com.vitorpamplona.amethyst.ui.theme.BitcoinOrange
import com.vitorpamplona.amethyst.ui.theme.Following
import com.vitorpamplona.amethyst.ui.theme.newItemBackgroundColor
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toImmutableSet
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import nostr.postr.toNpub
import java.io.File
import java.math.BigDecimal
import java.net.URL
import java.util.Locale

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun NoteCompose(
    baseNote: Note,
    routeForLastRead: String? = null,
    modifier: Modifier = remember { Modifier },
    isBoostedNote: Boolean = false,
    isQuotedNote: Boolean = false,
    unPackReply: Boolean = true,
    makeItShort: Boolean = false,
    addMarginTop: Boolean = true,
    parentBackgroundColor: Color? = null,
    accountViewModel: AccountViewModel,
    nav: (String) -> Unit
) {
    val noteState by baseNote.live().metadata.observeAsState()
    val noteEvent = remember(noteState) { noteState?.note?.event }

    if (noteEvent == null) {
        var popupExpanded by remember { mutableStateOf(false) }

        BlankNote(
            remember {
                modifier.combinedClickable(
                    onClick = { },
                    onLongClick = { popupExpanded = true }
                )
            },
            isBoostedNote
        )

        NoteQuickActionMenu(baseNote, popupExpanded, { popupExpanded = false }, accountViewModel)
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
    parentBackgroundColor: Color? = null,
    accountViewModel: AccountViewModel,
    nav: (String) -> Unit
) {
    val accountState by accountViewModel.accountLiveData.observeAsState()

    val isHidden by remember(accountState) {
        derivedStateOf {
            val isSensitive = note.event?.isSensitive() ?: false

            accountState?.account?.isHidden(note.author!!) == true || (isSensitive && accountState?.account?.showSensitiveContent == false)
        }
    }

    if (!isHidden) {
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
    parentBackgroundColor: Color? = null,
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

    WatchForReports(note, accountViewModel) { newIsAcceptable, newCanPreview, newRelevantReports ->
        if (newIsAcceptable != state.isAcceptable || newCanPreview != state.canPreview) {
            state = NoteComposeReportState(newIsAcceptable, newCanPreview, newRelevantReports.toImmutableSet())
        }
    }

    var showReportedNote by remember { mutableStateOf(false) }

    val showHiddenNote by remember(state, showReportedNote) {
        derivedStateOf {
            !state.isAcceptable && !showReportedNote
        }
    }

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
        val canPreview by remember(state, showReportedNote) {
            derivedStateOf {
                (!state.isAcceptable && showReportedNote) || state.canPreview
            }
        }

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

@Composable
private fun WatchForReports(
    note: Note,
    accountViewModel: AccountViewModel,
    onChange: (Boolean, Boolean, Set<Note>) -> Unit
) {
    val accountState by accountViewModel.accountLiveData.observeAsState()
    val noteReportsState by note.live().reports.observeAsState()

    LaunchedEffect(key1 = noteReportsState, key2 = accountState) {
        launch(Dispatchers.Default) {
            accountState?.account?.let { loggedIn ->
                val newCanPreview = note.author?.pubkeyHex == loggedIn.userProfile().pubkeyHex ||
                    (note.author?.let { loggedIn.userProfile().isFollowingCached(it) } ?: true) ||
                    noteReportsState?.note?.hasAnyReports() != true

                val newIsAcceptable = noteReportsState?.note?.let {
                    loggedIn.isAcceptable(it)
                } ?: true
                val newRelevantReports = noteReportsState?.note?.let {
                    loggedIn.getRelevantReports(it)
                } ?: emptySet()

                onChange(newIsAcceptable, newCanPreview, newRelevantReports)
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
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
    parentBackgroundColor: Color? = null,
    accountViewModel: AccountViewModel,
    nav: (String) -> Unit
) {
    val noteEvent = remember { baseNote.event }
    val channelHex = remember { baseNote.channelHex() }

    if ((noteEvent is ChannelCreateEvent || noteEvent is ChannelMetadataEvent) && channelHex != null) {
        ChannelHeader(channelHex = channelHex, accountViewModel = accountViewModel, nav = nav)
    } else if (noteEvent is BadgeDefinitionEvent) {
        BadgeDisplay(baseNote = baseNote)
    } else if (noteEvent is FileHeaderEvent) {
        FileHeaderDisplay(baseNote)
    } else if (noteEvent is FileStorageHeaderEvent) {
        FileStorageHeaderDisplay(baseNote)
    } else {
        var isNew by remember { mutableStateOf<Boolean>(false) }
        var popupExpanded by remember { mutableStateOf(false) }

        val scope = rememberCoroutineScope()

        LaunchedEffect(key1 = routeForLastRead) {
            launch(Dispatchers.IO) {
                routeForLastRead?.let {
                    val lastTime = NotificationCache.load(it)

                    val createdAt = baseNote.createdAt()
                    if (createdAt != null) {
                        NotificationCache.markAsRead(it, createdAt)

                        val newIsNew = createdAt > lastTime
                        if (newIsNew != isNew) {
                            isNew = newIsNew
                        }
                    }
                }
            }
        }

        val primaryColor = MaterialTheme.colors.newItemBackgroundColor
        val defaultBackgroundColor = MaterialTheme.colors.background

        val backgroundColor = remember(isNew, parentBackgroundColor) {
            if (isNew) {
                if (parentBackgroundColor != null) {
                    primaryColor.compositeOver(parentBackgroundColor)
                } else {
                    primaryColor.compositeOver(defaultBackgroundColor)
                }
            } else {
                parentBackgroundColor ?: defaultBackgroundColor
            }
        }

        val columnModifier = remember(backgroundColor) {
            modifier
                .combinedClickable(
                    onClick = {
                        scope.launch {
                            routeFor(baseNote, accountViewModel.userProfile())?.let {
                                nav(it)
                            }
                        }
                    },
                    onLongClick = { popupExpanded = true }
                )
                .background(backgroundColor)
        }

        Column(modifier = columnModifier) {
            Row(
                modifier = remember {
                    Modifier
                        .padding(
                            start = if (!isBoostedNote) 12.dp else 0.dp,
                            end = if (!isBoostedNote) 12.dp else 0.dp,
                            top = if (addMarginTop && !isBoostedNote) 10.dp else 0.dp
                        )
                }
            ) {
                if (!isBoostedNote && !isQuotedNote) {
                    DrawAuthorImages(baseNote, accountViewModel, nav)
                }

                Column(
                    modifier = remember {
                        Modifier
                            .padding(start = if (!isBoostedNote && !isQuotedNote) 10.dp else 0.dp)
                    }
                ) {
                    FirstUserInfoRow(
                        baseNote = baseNote,
                        showAuthorPicture = isQuotedNote,
                        accountViewModel = accountViewModel,
                        nav = nav
                    )

                    if (noteEvent !is RepostEvent && !makeItShort && !isQuotedNote) {
                        SecondUserInfoRow(
                            baseNote,
                            accountViewModel,
                            nav
                        )
                    }

                    Spacer(modifier = Modifier.height(2.dp))

                    if (!makeItShort) {
                        ReplyRow(
                            baseNote,
                            unPackReply,
                            backgroundColor,
                            accountViewModel,
                            nav
                        )
                    }

                    when (noteEvent) {
                        is AppDefinitionEvent -> {
                            RenderAppDefinition(baseNote, accountViewModel, nav)
                        }

                        is ReactionEvent -> {
                            RenderReaction(baseNote, backgroundColor, accountViewModel, nav)
                        }

                        is RepostEvent -> {
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
                            RenderPeopleList(baseNote, backgroundColor, accountViewModel, nav)
                        }

                        is RelaySetEvent -> {
                            RelaySetList(baseNote, backgroundColor, accountViewModel, nav)
                        }

                        is AudioTrackEvent -> {
                            RenderAudioTrack(baseNote, accountViewModel, nav)
                        }

                        is PinListEvent -> {
                            RenderPinListEvent(baseNote, backgroundColor, accountViewModel, nav)
                        }

                        is PrivateDmEvent -> {
                            RenderPrivateMessage(baseNote, makeItShort, canPreview, backgroundColor, accountViewModel, nav)
                        }

                        is HighlightEvent -> {
                            RenderHighlight(baseNote, makeItShort, canPreview, backgroundColor, accountViewModel, nav)
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

                    NoteQuickActionMenu(baseNote, popupExpanded, { popupExpanded = false }, accountViewModel)
                }
            }

            if (!makeItShort) {
                ReactionsRow(baseNote, !isBoostedNote && !isQuotedNote, accountViewModel, nav)
            }

            Divider(
                modifier = Modifier.padding(top = 10.dp),
                thickness = 0.25.dp
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
    } else if (noteEvent is PrivateDmEvent) {
        return "Room/${noteEvent.talkingWith(loggedIn.pubkeyHex)}"
    } else {
        return "Note/${note.idHex}"
    }

    return null
}

@Composable
private fun RenderTextEvent(
    note: Note,
    makeItShort: Boolean,
    canPreview: Boolean,
    backgroundColor: Color,
    accountViewModel: AccountViewModel,
    nav: (String) -> Unit
) {
    val eventContent = remember(note.event) { accountViewModel.decrypt(note) }

    if (eventContent != null) {
        val isAuthorTheLoggedUser = remember(note.event) { accountViewModel.isLoggedUser(note.author) }

        if (makeItShort && isAuthorTheLoggedUser) {
            Text(
                text = eventContent,
                color = MaterialTheme.colors.onSurface.copy(alpha = 0.32f),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        } else {
            val hasSensitiveContent = remember(note.event) { note.event?.isSensitive() ?: false }

            SensitivityWarning(
                hasSensitiveContent = hasSensitiveContent,
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
private fun RenderPoll(
    note: Note,
    makeItShort: Boolean,
    canPreview: Boolean,
    backgroundColor: Color,
    accountViewModel: AccountViewModel,
    nav: (String) -> Unit
) {
    val noteEvent = note.event as? PollNoteEvent ?: return
    val eventContent = remember { noteEvent.content() }

    if (makeItShort && accountViewModel.isLoggedUser(note.author)) {
        Text(
            text = eventContent,
            color = MaterialTheme.colors.onSurface.copy(alpha = 0.32f),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    } else {
        val hasSensitiveContent = remember(note.event) { note.event?.isSensitive() ?: false }

        val tags = remember(note) { note.event?.tags()?.toImmutableListOfLists() ?: ImmutableListOfLists() }

        SensitivityWarning(
            hasSensitiveContent = hasSensitiveContent,
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

        var hashtags = remember { noteEvent.hashtags().toImmutableList() }
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

    val clipboardManager = LocalClipboardManager.current
    val uri = LocalUriHandler.current

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
                            .height(35.dp)
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
                            tint = MaterialTheme.colors.onSurface.copy(alpha = 0.32f),
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
                        TranslatableRichTextViewer(
                            content = it,
                            canPreview = false,
                            tags = tags,
                            backgroundColor = MaterialTheme.colors.background,
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
    backgroundColor: Color,
    accountViewModel: AccountViewModel,
    nav: (String) -> Unit
) {
    val noteEvent = note.event as? HighlightEvent ?: return

    DisplayHighlight(
        noteEvent.quote(),
        noteEvent.author(),
        noteEvent.inUrl(),
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
    backgroundColor: Color,
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
                    color = MaterialTheme.colors.onSurface.copy(alpha = 0.32f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            } else {
                val hasSensitiveContent = remember(note.event) { note.event?.isSensitive() ?: false }
                SensitivityWarning(
                    hasSensitiveContent = hasSensitiveContent,
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
fun RelaySetList(
    baseNote: Note,
    backgroundColor: Color,
    accountViewModel: AccountViewModel,
    nav: (String) -> Unit
) {
    DisplayRelaySet(baseNote, backgroundColor, accountViewModel, nav)
}

@Composable
fun RenderPeopleList(
    baseNote: Note,
    backgroundColor: Color,
    accountViewModel: AccountViewModel,
    nav: (String) -> Unit
) {
    DisplayPeopleList(baseNote, backgroundColor, accountViewModel, nav)
}

@Composable
fun DisplayRelaySet(
    baseNote: Note,
    backgroundColor: Color,
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
                        RelayOptionsAction(relay, accountViewModel)
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
                    .background(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                backgroundColor.copy(alpha = 0f),
                                backgroundColor
                            )
                        )
                    )
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
    accountViewModel: AccountViewModel
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
        NewRelayListView({ wantsToAddRelay = "" }, accountViewModel, wantsToAddRelay)
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
    backgroundColor: Color,
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
                    .background(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                backgroundColor.copy(alpha = 0f),
                                backgroundColor
                            )
                        )
                    )
            ) {
                Button(
                    modifier = Modifier.padding(top = 10.dp),
                    onClick = { expanded = !expanded },
                    shape = RoundedCornerShape(20.dp),
                    colors = ButtonDefaults.buttonColors(
                        backgroundColor = MaterialTheme.colors.primary.copy(alpha = 0.32f)
                            .compositeOver(MaterialTheme.colors.background)
                    ),
                    contentPadding = PaddingValues(vertical = 6.dp, horizontal = 16.dp)
                ) {
                    Text(text = stringResource(R.string.show_more), color = Color.White)
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RenderBadgeAward(
    note: Note,
    backgroundColor: Color,
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
                    .size(size = 35.dp)
                    .clickable {
                        nav("User/${user.pubkeyHex}")
                    },
                verticalAlignment = Alignment.CenterVertically
            ) {
                UserPicture(
                    baseUser = user,
                    accountViewModel = accountViewModel,
                    size = 35.dp
                )
            }
        }

        if (awardees.size > 100) {
            Text(" and ${awardees.size - 100} others")
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
    backgroundColor: Color,
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
        text = refactorReactionText
    )
}

@Composable
private fun RenderRepost(
    note: Note,
    backgroundColor: Color,
    accountViewModel: AccountViewModel,
    nav: (String) -> Unit
) {
    val boostedNote = remember {
        note.replyTo?.lastOrNull()
    }

    boostedNote?.let {
        NoteCompose(
            it,
            modifier = remember { Modifier },
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
    backgroundColor: Color,
    accountViewModel: AccountViewModel,
    nav: (String) -> Unit
) {
    PinListHeader(baseNote, backgroundColor, accountViewModel, nav)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PinListHeader(
    baseNote: Note,
    backgroundColor: Color,
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
                    .background(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                backgroundColor.copy(alpha = 0f),
                                backgroundColor
                            )
                        )
                    )
            ) {
                Button(
                    modifier = Modifier.padding(top = 10.dp),
                    onClick = { expanded = !expanded },
                    shape = RoundedCornerShape(20.dp),
                    colors = ButtonDefaults.buttonColors(
                        backgroundColor = MaterialTheme.colors.primary.copy(alpha = 0.32f)
                            .compositeOver(MaterialTheme.colors.background)
                    ),
                    contentPadding = PaddingValues(vertical = 6.dp, horizontal = 16.dp)
                ) {
                    Text(text = stringResource(R.string.show_more), color = Color.White)
                }
            }
        }
    }
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
    backgroundColor: Color,
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
                .clip(shape = RoundedCornerShape(15.dp))
                .border(
                    1.dp,
                    MaterialTheme.colors.onSurface.copy(alpha = 0.12f),
                    RoundedCornerShape(15.dp)
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
    backgroundColor: Color,
    accountViewModel: AccountViewModel,
    nav: (String) -> Unit
) {
    val noteEvent = note.event

    if (noteEvent is TextNoteEvent && (note.replyTo != null || noteEvent.hasAnyTaggedUser())) {
        val replyingDirectlyTo = remember {
            note.replyTo?.lastOrNull()
        }
        if (replyingDirectlyTo != null && unPackReply) {
            NoteCompose(
                baseNote = replyingDirectlyTo,
                isQuotedNote = true,
                modifier = Modifier
                    .padding(top = 5.dp)
                    .fillMaxWidth()
                    .clip(shape = RoundedCornerShape(15.dp))
                    .border(
                        1.dp,
                        MaterialTheme.colors.onSurface.copy(alpha = 0.12f),
                        RoundedCornerShape(15.dp)
                    ),
                unPackReply = false,
                makeItShort = true,
                parentBackgroundColor = MaterialTheme.colors.onSurface.copy(alpha = 0.05f)
                    .compositeOver(backgroundColor),
                accountViewModel = accountViewModel,
                nav = nav
            )
        } else {
            // ReplyInformation(note.replyTo, noteEvent.mentions(), accountViewModel, nav)
        }

        Spacer(modifier = Modifier.height(5.dp))
    } else if (noteEvent is ChannelMessageEvent && (note.replyTo != null || noteEvent.hasAnyTaggedUser())) {
        val channelHex = note.channelHex()
        channelHex?.let {
            val replies = remember { note.replyTo?.toImmutableList() }
            val mentions = remember { noteEvent.mentions().toImmutableList() }

            ReplyInformationChannel(replies, mentions, it, accountViewModel, nav)
        }

        Spacer(modifier = Modifier.height(5.dp))
    }
}

@Composable
private fun SecondUserInfoRow(
    note: Note,
    accountViewModel: AccountViewModel,
    nav: (String) -> Unit
) {
    val noteEvent = remember { note.event } ?: return
    val noteAuthor = remember { note.author } ?: return

    Row(verticalAlignment = Alignment.CenterVertically) {
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
    val eventNote = remember { baseNote.event } ?: return
    val time = remember { baseNote.createdAt() } ?: return
    val padding = remember {
        Modifier.padding(horizontal = 5.dp)
    }

    Row(verticalAlignment = Alignment.CenterVertically) {
        if (showAuthorPicture) {
            NoteAuthorPicture(baseNote, nav, accountViewModel, remember { 25.dp })
            Spacer(padding)
            NoteUsernameDisplay(baseNote, remember { Modifier.weight(1f) })
        } else {
            NoteUsernameDisplay(baseNote, remember { Modifier.weight(1f) })
        }

        if (eventNote is RepostEvent) {
            Text(
                "  ${stringResource(id = R.string.boosted)}",
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colors.onSurface.copy(alpha = 0.32f)
            )
        } else {
            DisplayFollowingHashtagsInPost(eventNote, accountViewModel, nav)
        }

        TimeAgo(time)

        MoreOptionsButton(baseNote, accountViewModel)
    }
}

@Composable
private fun MoreOptionsButton(
    baseNote: Note,
    accountViewModel: AccountViewModel
) {
    var moreActionsExpanded by remember { mutableStateOf(false) }

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

        NoteDropDownMenu(
            baseNote,
            moreActionsExpanded,
            { moreActionsExpanded = false },
            accountViewModel
        )
    }
}

@Composable
fun TimeAgo(time: Long) {
    val context = LocalContext.current
    var timeStr by remember { mutableStateOf("") }

    LaunchedEffect(key1 = time) {
        launch(Dispatchers.IO) {
            val newTimeStr = timeAgo(time, context = context)
            if (newTimeStr != timeStr) {
                timeStr = newTimeStr
            }
        }
    }

    Text(
        timeStr,
        color = MaterialTheme.colors.onSurface.copy(alpha = 0.32f),
        maxLines = 1
    )
}

@Composable
private fun DrawAuthorImages(baseNote: Note, accountViewModel: AccountViewModel, nav: (String) -> Unit) {
    val baseChannelHex = remember { baseNote.channelHex() }
    val modifier = remember { Modifier.width(55.dp) }

    Column(modifier) {
        // Draws the boosted picture outside the boosted card.
        Box(modifier = modifier, contentAlignment = Alignment.BottomEnd) {
            NoteAuthorPicture(baseNote, nav, accountViewModel, 55.dp)

            if (baseNote.event is RepostEvent) {
                RepostNoteAuthorPicture(baseNote, accountViewModel, nav)
            }

            if (baseNote.event is ChannelMessageEvent && baseChannelHex != null) {
                LoadChannel(baseChannelHex) { channel ->
                    ChannelNotePicture(channel)
                }
            }
        }

        if (baseNote.event is RepostEvent) {
            val baseReply = remember {
                baseNote.replyTo?.lastOrNull()
            }
            baseReply?.let {
                RelayBadges(it)
            }
        } else {
            RelayBadges(baseNote)
        }
    }
}

@Composable
fun LoadChannel(baseChannelHex: String, content: @Composable (Channel) -> Unit) {
    var channel by remember(baseChannelHex) {
        mutableStateOf<Channel?>(null)
    }

    LaunchedEffect(key1 = baseChannelHex) {
        if (channel == null) {
            launch(Dispatchers.IO) {
                channel = LocalCache.checkGetOrCreateChannel(baseChannelHex)
            }
        }
    }

    channel?.let {
        content(it)
    }
}

@Composable
private fun ChannelNotePicture(baseChannel: Channel) {
    val channelState by baseChannel.live.observeAsState()
    val channel = remember(channelState) { channelState?.channel } ?: return

    val modifier = remember {
        Modifier
            .width(30.dp)
            .height(30.dp)
            .clip(shape = CircleShape)
    }

    val boxModifier = remember {
        Modifier
            .width(30.dp)
            .height(30.dp)
    }

    val model = remember(channelState) {
        ResizeImage(channel.profilePicture(), 30.dp)
    }

    Box(boxModifier) {
        RobohashAsyncImageProxy(
            robot = channel.idHex,
            model = model,
            contentDescription = stringResource(R.string.group_picture),
            modifier = modifier
                .background(MaterialTheme.colors.background)
                .border(
                    2.dp,
                    MaterialTheme.colors.background,
                    CircleShape
                )
        )
    }
}

@Composable
private fun RepostNoteAuthorPicture(
    baseNote: Note,
    accountViewModel: AccountViewModel,
    nav: (String) -> Unit
) {
    val baseRepost = remember { baseNote.replyTo?.lastOrNull() }

    val modifier = remember {
        Modifier.size(30.dp)
    }

    baseRepost?.let {
        Box(modifier) {
            NoteAuthorPicture(
                baseNote = it,
                nav = nav,
                accountViewModel = accountViewModel,
                size = remember { 30.dp },
                pictureModifier = Modifier.border(
                    2.dp,
                    MaterialTheme.colors.background,
                    CircleShape
                )
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DisplayHighlight(
    highlight: String,
    authorHex: String?,
    url: String?,
    makeItShort: Boolean,
    canPreview: Boolean,
    backgroundColor: Color,
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

    var userBase by remember { mutableStateOf<User?>(null) }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            if (authorHex != null) {
                userBase = LocalCache.checkGetOrCreateUser(authorHex)
            }
        }
    }

    FlowRow() {
        authorHex?.let { authorHex ->
            userBase?.let { userBase ->
                val userState by userBase.live().metadata.observeAsState()
                val route = remember { "User/${userBase.pubkeyHex}" }
                val userDisplayName = remember(userState) { userState?.user?.toBestDisplayName() }
                val userTags = remember(userState) { userState?.user?.info?.latestMetadata?.tags?.toImmutableListOfLists() }

                if (userDisplayName != null) {
                    CreateClickableTextWithEmoji(
                        clickablePart = userDisplayName,
                        suffix = " ",
                        tags = userTags,
                        route = route,
                        nav = nav
                    )
                }
            }
        }

        url?.let { url ->
            val validatedUrl = remember {
                try {
                    URL(url)
                } catch (e: Exception) {
                    Log.w("Note Compose", "Invalid URI: $url")
                    null
                }
            }

            validatedUrl?.host?.let { host ->
                Text("on ")
                ClickableUrl(urlText = host, url = url)
            }
        }
    }
}

@Composable
fun DisplayFollowingHashtagsInPost(
    noteEvent: EventInterface,
    accountViewModel: AccountViewModel,
    nav: (String) -> Unit
) {
    val accountState by accountViewModel.accountLiveData.observeAsState()
    var firstTag by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(key1 = accountState) {
        launch(Dispatchers.Default) {
            val followingTags = accountState?.account?.followingTagSet() ?: emptySet()
            val newFirstTag = noteEvent.firstIsTaggedHashes(followingTags)

            if (firstTag != newFirstTag) {
                firstTag = newFirstTag
            }
        }
    }

    Column() {
        Row(verticalAlignment = Alignment.CenterVertically) {
            firstTag?.let {
                val displayTag = remember(firstTag) { AnnotatedString(" #$firstTag") }
                val route = remember(firstTag) { "Hashtag/$firstTag" }

                ClickableText(
                    text = displayTag,
                    onClick = { nav(route) },
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DisplayUncitedHashtags(
    hashtags: ImmutableList<String>,
    eventContent: String,
    nav: (String) -> Unit
) {
    if (hashtags.isNotEmpty()) {
        FlowRow(
            modifier = Modifier.padding(top = 5.dp)
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
    Text(
        "PoW-$pow",
        color = MaterialTheme.colors.primary.copy(
            alpha = 0.52f
        ),
        fontSize = 14.sp,
        fontWeight = FontWeight.Bold
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
                hasPledge = newHasPledge
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
            tint = MaterialTheme.colors.onSurface.copy(alpha = 0.32f)
        )
    }

    Text(
        text = reward,
        color = MaterialTheme.colors.onSurface.copy(alpha = 0.32f)
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

                backgroundFromImage = Pair(colorFromImage, textBackground)
            }
        }
    }

    Row(
        modifier = Modifier
            .padding(10.dp)
            .clip(shape = CutCornerShape(20, 20, 20, 20))
            .border(
                5.dp,
                MaterialTheme.colors.primary.copy(alpha = 0.32f),
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

    LaunchedEffect(key1 = event.id) {
        if (content == null) {
            launch(Dispatchers.IO) {
                val blurHash = event.blurhash()
                val hash = event.hash()
                val dimensions = event.dimensions()
                val description = event.content
                val removedParamsFromUrl = fullUrl.split("?")[0].lowercase()
                val isImage = imageExtensions.any { removedParamsFromUrl.endsWith(it) }
                val uri = "nostr:" + note.toNEvent()
                content = if (isImage) {
                    ZoomableUrlImage(fullUrl, description, hash, blurHash, dimensions, uri)
                } else {
                    ZoomableUrlVideo(fullUrl, description, hash, uri)
                }
            }
        }
    }

    content?.let {
        ZoomableContentView(content = it)
    }
}

@Composable
fun FileStorageHeaderDisplay(baseNote: Note) {
    val appContext = LocalContext.current.applicationContext
    val eventHeader = (baseNote.event as? FileStorageHeaderEvent) ?: return

    var fileNote by remember { mutableStateOf<Note?>(null) }

    LaunchedEffect(key1 = eventHeader.id) {
        launch(Dispatchers.IO) {
            fileNote = eventHeader.dataEventId()?.let { LocalCache.checkGetOrCreateNote(it) }
        }
    }

    fileNote?.let { fileNote2 ->
        val noteState by fileNote2.live().metadata.observeAsState()
        val note = remember(noteState) { noteState?.note }

        var content by remember { mutableStateOf<ZoomableContent?>(null) }

        LaunchedEffect(key1 = eventHeader.id, key2 = noteState, key3 = note?.event) {
            if (content == null) {
                launch(Dispatchers.IO) {
                    val uri = "nostr:" + baseNote.toNEvent()
                    val localDir = note?.idHex?.let { File(File(appContext.externalCacheDir, "NIP95"), it) }
                    val blurHash = eventHeader.blurhash()
                    val dimensions = eventHeader.dimensions()
                    val description = eventHeader.content
                    val mimeType = eventHeader.mimeType()

                    content = if (mimeType?.startsWith("image") == true) {
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
                }
            }
        }

        content?.let {
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
        withContext(Dispatchers.IO) {
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
                    UserPicture(it.second, 25.dp, accountViewModel)
                    Spacer(Modifier.width(5.dp))
                    UsernameDisplay(it.second, Modifier.weight(1f))
                    Spacer(Modifier.width(5.dp))
                    it.first.role?.let {
                        Text(
                            text = it.capitalize(Locale.ROOT),
                            color = MaterialTheme.colors.onSurface.copy(alpha = 0.32f),
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
private fun LongFormHeader(noteEvent: LongTextNoteEvent, note: Note, accountViewModel: AccountViewModel) {
    val image = remember(noteEvent) { noteEvent.image() }
    val title = remember(noteEvent) { noteEvent.title() }
    val summary = remember(noteEvent) { noteEvent.summary() ?: noteEvent.content.take(200).ifBlank { null } }

    Row(
        modifier = Modifier
            .clip(shape = RoundedCornerShape(15.dp))
            .border(
                1.dp,
                MaterialTheme.colors.onSurface.copy(alpha = 0.12f),
                RoundedCornerShape(15.dp)
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
            modifier = Modifier
                .fillMaxWidth()
                .height(150.dp)
        )

        Box(
            Modifier
                .width(75.dp)
                .height(75.dp)
                .padding(10.dp)
                .align(Alignment.BottomStart)
        ) {
            NoteAuthorPicture(baseNote = note, accountViewModel = accountViewModel, size = 55.dp)
        }
    }
}

@Composable
private fun RelayBadges(baseNote: Note) {
    var expanded by remember { mutableStateOf(false) }
    var showShowMore by remember { mutableStateOf(false) }

    var lazyRelayList by remember { mutableStateOf<ImmutableList<String>>(persistentListOf()) }
    var shortRelayList by remember { mutableStateOf<ImmutableList<String>>(persistentListOf()) }

    WatchRelayLists(baseNote) { relayList ->
        if (!equalImmutableLists(relayList, lazyRelayList)) {
            lazyRelayList = relayList.toImmutableList()
            shortRelayList = relayList.take(3).toImmutableList()
        }

        val nextShowMore = relayList.size > 3
        if (nextShowMore != showShowMore) {
            // only triggers recomposition when actually different
            showShowMore = nextShowMore
        }
    }

    Spacer(Modifier.height(10.dp))

    if (expanded) {
        VerticalRelayPanelWithFlow(lazyRelayList)
    } else {
        VerticalRelayPanelWithFlow(shortRelayList)
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
    relays: ImmutableList<String>
) {
    // FlowRow Seems to be a lot faster than LazyVerticalGrid
    FlowRow() {
        relays.forEach { url ->
            RenderRelay(url)
        }
    }
}

@Composable
private fun ShowMoreRelaysButton(onClick: () -> Unit) {
    val boxModifier = remember {
        Modifier
            .fillMaxWidth()
            .height(25.dp)
    }
    val iconButtonModifier = remember { Modifier.size(24.dp) }
    val iconModifier = remember { Modifier.size(15.dp) }

    Row(
        boxModifier,
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.Top
    ) {
        IconButton(
            modifier = iconButtonModifier,
            onClick = onClick
        ) {
            Icon(
                imageVector = Icons.Default.ExpandMore,
                null,
                modifier = iconModifier,
                tint = MaterialTheme.colors.onSurface.copy(alpha = 0.32f)
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
    val noteState by baseNote.live().metadata.observeAsState()
    val author by remember(noteState) {
        derivedStateOf {
            noteState?.note?.author
        }
    }

    if (author == null) {
        val nullModifier = remember {
            modifier
                .size(size)
                .clip(shape = CircleShape)
        }

        RobohashAsyncImage(
            robot = "authornotfound",
            robotSize = size,
            contentDescription = stringResource(R.string.unknown_author),
            modifier = nullModifier.background(MaterialTheme.colors.background)
        )
    } else {
        UserPicture(author!!, size, accountViewModel, modifier, onClick)
    }
}

@Composable
fun UserPicture(
    user: User,
    nav: (String) -> Unit,
    accountViewModel: AccountViewModel,
    size: Dp,
    pictureModifier: Modifier = Modifier
) {
    UserPicture(user, size, accountViewModel, pictureModifier) {
        nav("User/${it.pubkeyHex}")
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun UserPicture(
    baseUser: User,
    size: Dp,
    accountViewModel: AccountViewModel,
    modifier: Modifier = remember { Modifier },
    onClick: ((User) -> Unit)? = null,
    onLongClick: ((User) -> Unit)? = null
) {
    val userState by baseUser.live().metadata.observeAsState()

    val userPubkey = remember {
        baseUser.pubkeyHex
    }

    val userProfile by remember(userState) {
        derivedStateOf {
            userState?.user?.profilePicture()
        }
    }

    // BaseUser is the same reference as accountState.user
    val myModifier = remember {
        if (onClick != null && onLongClick != null) {
            Modifier.combinedClickable(
                onClick = { onClick(baseUser) },
                onLongClick = { onLongClick(baseUser) }
            )
        } else if (onClick != null) {
            Modifier.clickable(onClick = { onClick(baseUser) })
        } else {
            Modifier
        }
    }

    Row(modifier = myModifier) {
        UserPicture(
            userHex = userPubkey,
            userPicture = userProfile,
            size = size,
            modifier = modifier,
            accountViewModel = accountViewModel
        )
    }
}

@Composable
fun UserPicture(
    userHex: String,
    userPicture: String?,
    size: Dp,
    modifier: Modifier = Modifier,
    accountViewModel: AccountViewModel
) {
    val myBoxModifier = remember {
        Modifier.size(size)
    }

    val myImageModifier = remember {
        modifier
            .size(size)
            .clip(shape = CircleShape)
    }

    val myIconSize = remember(size) { size.div(3.5f) }

    Box(myBoxModifier, contentAlignment = TopEnd) {
        RobohashAsyncImageProxy(
            robot = userHex,
            model = remember(userPicture) {
                ResizeImage(userPicture, size)
            },
            contentDescription = stringResource(id = R.string.profile_image),
            modifier = myImageModifier.background(MaterialTheme.colors.background)
        )

        ObserveAndDisplayFollowingMark(userHex, myIconSize, accountViewModel)
    }
}

@Composable
private fun ObserveAndDisplayFollowingMark(userHex: String, iconSize: Dp, accountViewModel: AccountViewModel) {
    val accountFollowsState by accountViewModel.account.userProfile().live().follows.observeAsState()

    var showFollowingMark by remember { mutableStateOf(false) }

    LaunchedEffect(key1 = accountFollowsState) {
        launch(Dispatchers.Default) {
            val newShowFollowingMark =
                accountFollowsState?.user?.isFollowingCached(userHex) == true ||
                    (userHex == accountViewModel.account.userProfile().pubkeyHex)

            if (newShowFollowingMark != showFollowingMark) {
                showFollowingMark = newShowFollowingMark
            }
        }
    }

    if (showFollowingMark) {
        FollowingIcon(iconSize)
    }
}

@Composable
private fun FollowingIcon(iconSize: Dp) {
    val myIconBoxModifier = remember {
        Modifier.size(iconSize)
    }

    Box(myIconBoxModifier, contentAlignment = Alignment.Center) {
        val myIconBackgroundModifier = remember {
            Modifier
                .clip(CircleShape)
                .fillMaxSize(0.6f)
        }

        Box(
            myIconBackgroundModifier.background(MaterialTheme.colors.background)
        )

        val myIconModifier = remember {
            Modifier.size(iconSize)
        }

        Icon(
            painter = painterResource(R.drawable.ic_verified),
            stringResource(id = R.string.following),
            modifier = myIconModifier,
            tint = Following
        )
    }
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
    val clipboardManager = LocalClipboardManager.current
    val appContext = LocalContext.current.applicationContext
    val actContext = LocalContext.current
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
            onNew(
                DropDownParams(
                    isFollowingAuthor = accountViewModel.isFollowing(note.author),
                    isPrivateBookmarkNote = accountViewModel.isInPrivateBookmarks(note),
                    isPublicBookmarkNote = accountViewModel.isInPublicBookmarks(note),
                    isLoggedUser = accountViewModel.isLoggedUser(note.author),
                    isSensitive = note.event?.isSensitive() ?: false,
                    showSensitiveContent = accountState?.account?.showSensitiveContent
                )
            )
        }
    }
}
