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
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.lightColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.capitalize
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import androidx.core.graphics.get
import androidx.navigation.NavController
import coil.compose.AsyncImage
import coil.compose.AsyncImagePainter
import com.google.accompanist.flowlayout.FlowRow
import com.vitorpamplona.amethyst.NotificationCache
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.model.Account
import com.vitorpamplona.amethyst.model.Channel
import com.vitorpamplona.amethyst.model.LocalCache
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.model.NoteState
import com.vitorpamplona.amethyst.model.User
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
import com.vitorpamplona.amethyst.service.model.PollNoteEvent
import com.vitorpamplona.amethyst.service.model.PrivateDmEvent
import com.vitorpamplona.amethyst.service.model.ReactionEvent
import com.vitorpamplona.amethyst.service.model.ReportEvent
import com.vitorpamplona.amethyst.service.model.ReportedKey
import com.vitorpamplona.amethyst.service.model.RepostEvent
import com.vitorpamplona.amethyst.service.model.TextNoteEvent
import com.vitorpamplona.amethyst.ui.components.ClickableUrl
import com.vitorpamplona.amethyst.ui.components.CreateClickableText
import com.vitorpamplona.amethyst.ui.components.ObserveDisplayNip05Status
import com.vitorpamplona.amethyst.ui.components.ResizeImage
import com.vitorpamplona.amethyst.ui.components.RobohashAsyncImage
import com.vitorpamplona.amethyst.ui.components.RobohashAsyncImageProxy
import com.vitorpamplona.amethyst.ui.components.RobohashFallbackAsyncImage
import com.vitorpamplona.amethyst.ui.components.TranslatableRichTextViewer
import com.vitorpamplona.amethyst.ui.components.VideoView
import com.vitorpamplona.amethyst.ui.components.ZoomableContent
import com.vitorpamplona.amethyst.ui.components.ZoomableContentView
import com.vitorpamplona.amethyst.ui.components.ZoomableLocalImage
import com.vitorpamplona.amethyst.ui.components.ZoomableLocalVideo
import com.vitorpamplona.amethyst.ui.components.ZoomableUrlImage
import com.vitorpamplona.amethyst.ui.components.ZoomableUrlVideo
import com.vitorpamplona.amethyst.ui.components.imageExtensions
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.screen.loggedIn.ChannelHeader
import com.vitorpamplona.amethyst.ui.screen.loggedIn.ReportNoteDialog
import com.vitorpamplona.amethyst.ui.theme.BitcoinOrange
import com.vitorpamplona.amethyst.ui.theme.Following
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import nostr.postr.toNpub
import java.io.File
import java.math.BigDecimal
import java.net.URL
import java.util.Locale
import kotlin.time.ExperimentalTime
import kotlin.time.measureTimedValue

@OptIn(ExperimentalTime::class)
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
    parentBackgroundColor: Color? = null,
    accountViewModel: AccountViewModel,
    navController: NavController
) {
    val (value, elapsed) = measureTimedValue {
        NoteComposeInner(
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
            navController
        )
    }

    Log.d("Time", "Note Compose in $elapsed for ${baseNote.idHex} ${baseNote.event?.kind()} ${baseNote.event?.content()?.split("\n")?.get(0)?.take(100)}")
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalTime::class)
@Composable
fun NoteComposeInner(
    baseNote: Note,
    routeForLastRead: String? = null,
    modifier: Modifier = Modifier,
    isBoostedNote: Boolean = false,
    isQuotedNote: Boolean = false,
    unPackReply: Boolean = true,
    makeItShort: Boolean = false,
    addMarginTop: Boolean = true,
    parentBackgroundColor: Color? = null,
    accountViewModel: AccountViewModel,
    navController: NavController
) {
    val accountState by accountViewModel.accountLiveData.observeAsState()
    val account = remember(accountState) { accountState?.account } ?: return
    val loggedIn = remember(accountState) { accountState?.account?.userProfile() } ?: return

    val noteState by baseNote.live().metadata.observeAsState()
    val note = remember(noteState) { noteState?.note }

    val noteReportsState by baseNote.live().reports.observeAsState()
    val noteForReports = remember(noteReportsState) { noteReportsState?.note } ?: return

    val noteEvent = note?.event
    val baseChannel = note?.channel()

    var popupExpanded by remember { mutableStateOf(false) }

    if (noteEvent == null) {
        BlankNote(
            remember {
                modifier.combinedClickable(
                    onClick = { },
                    onLongClick = { popupExpanded = true }
                )
            },
            isBoostedNote
        )

        note?.let {
            NoteQuickActionMenu(it, popupExpanded, { popupExpanded = false }, accountViewModel)
        }
    } else {
        var showHiddenNote by remember { mutableStateOf(false) }
        var isAcceptableAndCanPreview by remember { mutableStateOf(Pair(true, true)) }

        LaunchedEffect(key1 = noteReportsState, key2 = accountState) {
            withContext(Dispatchers.IO) {
                account.userProfile().let { loggedIn ->
                    val newCanPreview = note.author === loggedIn ||
                        (note.author?.let { loggedIn.isFollowingCached(it) } ?: true) ||
                        !(noteForReports.hasAnyReports())

                    val newIsAcceptable = account.isAcceptable(noteForReports)

                    if (newIsAcceptable != isAcceptableAndCanPreview.first && newCanPreview != isAcceptableAndCanPreview.second) {
                        isAcceptableAndCanPreview = Pair(newIsAcceptable, newCanPreview)
                    }
                }
            }
        }

        if (!isAcceptableAndCanPreview.first && !showHiddenNote) {
            if (!account.isHidden(noteForReports.author!!)) {
                HiddenNote(
                    account.getRelevantReports(noteForReports),
                    account.userProfile(),
                    modifier,
                    isBoostedNote,
                    navController,
                    onClick = { showHiddenNote = true }
                )
            }
        } else if ((noteEvent is ChannelCreateEvent || noteEvent is ChannelMetadataEvent) && baseChannel != null) {
            ChannelHeader(baseChannel = baseChannel, account = account, navController = navController)
        } else if (noteEvent is BadgeDefinitionEvent) {
            BadgeDisplay(baseNote = note)
        } else if (noteEvent is FileHeaderEvent) {
            FileHeaderDisplay(note)
        } else if (noteEvent is FileStorageHeaderEvent) {
            FileStorageHeaderDisplay(note)
        } else {
            var isNew by remember { mutableStateOf<Boolean>(false) }

            val scope = rememberCoroutineScope()

            LaunchedEffect(key1 = routeForLastRead) {
                scope.launch(Dispatchers.IO) {
                    routeForLastRead?.let {
                        val lastTime = NotificationCache.load(it)

                        val createdAt = note.createdAt()
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

            val backgroundColor = if (isNew) {
                val newColor = MaterialTheme.colors.primary.copy(0.12f)
                if (parentBackgroundColor != null) {
                    newColor.compositeOver(parentBackgroundColor)
                } else {
                    newColor.compositeOver(MaterialTheme.colors.background)
                }
            } else {
                parentBackgroundColor ?: MaterialTheme.colors.background
            }

            val columnModifier = remember(backgroundColor) {
                modifier
                    .combinedClickable(
                        onClick = {
                            scope.launch {
                                routeFor(note, loggedIn)?.let {
                                    navController.navigate(it)
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
                        DrawAuthorImages(baseNote, loggedIn, navController)
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
                            account = account,
                            accountViewModel = accountViewModel,
                            navController = navController
                        )

                        if (noteEvent !is RepostEvent && !makeItShort && !isQuotedNote) {
                            SecondUserInfoRow(
                                note,
                                account,
                                navController
                            )
                        }

                        Spacer(modifier = Modifier.height(2.dp))

                        if (!makeItShort) {
                            ReplyRow(
                                note,
                                unPackReply,
                                backgroundColor,
                                account,
                                accountViewModel,
                                navController
                            )
                        }

                        when (noteEvent) {
                            is ReactionEvent -> {
                                RenderReaction(note, backgroundColor, accountViewModel, navController)
                            }

                            is RepostEvent -> {
                                RenderRepost(note, backgroundColor, accountViewModel, navController)
                            }

                            is ReportEvent -> {
                                RenderReport(note)
                            }

                            is LongTextNoteEvent -> {
                                RenderLongFormContent(note, loggedIn, accountViewModel, navController)
                            }

                            is BadgeAwardEvent -> {
                                RenderBadgeAward(note, backgroundColor, accountViewModel, navController)
                            }

                            is PeopleListEvent -> {
                                RenderPeopleList(noteState, backgroundColor, accountViewModel, navController)
                            }

                            is AudioTrackEvent -> {
                                RenderAudioTrack(note, loggedIn, accountViewModel, navController)
                            }

                            is PrivateDmEvent -> {
                                RenderPrivateMessage(note, makeItShort, isAcceptableAndCanPreview.second, backgroundColor, accountViewModel, navController)
                            }

                            is HighlightEvent -> {
                                RenderHighlight(note, makeItShort, isAcceptableAndCanPreview.second, backgroundColor, accountViewModel, navController)
                            }

                            is PollNoteEvent -> {
                                RenderPoll(
                                    note,
                                    makeItShort,
                                    isAcceptableAndCanPreview.second,
                                    backgroundColor,
                                    accountViewModel,
                                    navController
                                )
                            }

                            else -> {
                                RenderTextEvent(
                                    note,
                                    makeItShort,
                                    isAcceptableAndCanPreview.second,
                                    backgroundColor,
                                    accountViewModel,
                                    navController
                                )
                            }
                        }

                        NoteQuickActionMenu(note, popupExpanded, { popupExpanded = false }, accountViewModel)
                    }
                }
            }
        }
    }
}

fun routeFor(note: Note, loggedIn: User): String? {
    val noteEvent = note.event

    if (noteEvent is ChannelMessageEvent || noteEvent is ChannelCreateEvent || noteEvent is ChannelMetadataEvent) {
        note.channel()?.let {
            return "Channel/${it.idHex}"
        }
    } else if (noteEvent is PrivateDmEvent) {
        val replyAuthorBase =
            (note.event as? PrivateDmEvent)
                ?.verifiedRecipientPubKey()
                ?.let { LocalCache.getOrCreateUser(it) }

        var userToComposeOn = note.author!!

        if (replyAuthorBase != null) {
            if (note.author == loggedIn) {
                userToComposeOn = replyAuthorBase
            }
        }

        return "Room/${userToComposeOn.pubkeyHex}"
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
    navController: NavController
) {
    val tags = remember { note.event?.tags() }
    val hashtags = remember { note.event?.hashtags() ?: emptyList() }
    val eventContent = remember { accountViewModel.decrypt(note) }
    val modifier = remember { Modifier.fillMaxWidth() }
    val isAuthorTheLoggedUser = remember { accountViewModel.isLoggedUser(note.author) }

    if (eventContent != null) {
        if (makeItShort && isAuthorTheLoggedUser) {
            Text(
                text = eventContent,
                color = MaterialTheme.colors.onSurface.copy(alpha = 0.32f),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        } else {
            TranslatableRichTextViewer(
                content = eventContent,
                canPreview = canPreview && !makeItShort,
                modifier = modifier,
                tags = tags,
                backgroundColor = backgroundColor,
                accountViewModel = accountViewModel,
                navController = navController
            )

            DisplayUncitedHashtags(hashtags, eventContent, navController)
        }
    }

    if (!makeItShort) {
        ReactionsRow(note, accountViewModel, navController)
    }

    Divider(
        modifier = Modifier.padding(top = 10.dp),
        thickness = 0.25.dp
    )
}

@Composable
private fun RenderPoll(
    note: Note,
    makeItShort: Boolean,
    canPreview: Boolean,
    backgroundColor: Color,
    accountViewModel: AccountViewModel,
    navController: NavController
) {
    val noteEvent = note.event as? PollNoteEvent ?: return
    val eventContent = noteEvent.content()

    if (makeItShort && accountViewModel.isLoggedUser(note.author)) {
        Text(
            text = eventContent,
            color = MaterialTheme.colors.onSurface.copy(alpha = 0.32f),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    } else {
        TranslatableRichTextViewer(
            eventContent,
            canPreview = canPreview && !makeItShort,
            Modifier.fillMaxWidth(),
            noteEvent.tags(),
            backgroundColor,
            accountViewModel,
            navController
        )

        DisplayUncitedHashtags(noteEvent.hashtags(), eventContent, navController)

        PollNote(
            note,
            canPreview = canPreview && !makeItShort,
            backgroundColor,
            accountViewModel,
            navController
        )
    }

    if (!makeItShort) {
        ReactionsRow(note, accountViewModel, navController)
    }

    Divider(
        modifier = Modifier.padding(top = 10.dp),
        thickness = 0.25.dp
    )
}

@Composable
private fun RenderHighlight(
    note: Note,
    makeItShort: Boolean,
    canPreview: Boolean,
    backgroundColor: Color,
    accountViewModel: AccountViewModel,
    navController: NavController
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
        navController
    )

    if (!makeItShort) {
        ReactionsRow(note, accountViewModel, navController)
    }

    Divider(
        modifier = Modifier.padding(top = 10.dp),
        thickness = 0.25.dp
    )
}

@Composable
private fun RenderPrivateMessage(
    note: Note,
    makeItShort: Boolean,
    canPreview: Boolean,
    backgroundColor: Color,
    accountViewModel: AccountViewModel,
    navController: NavController
) {
    val noteEvent = note.event as? PrivateDmEvent ?: return
    val withMe = remember { noteEvent.with(accountViewModel.userProfile().pubkeyHex) }

    if (withMe) {
        val eventContent = remember { accountViewModel.decrypt(note) }

        if (eventContent != null) {
            if (makeItShort && accountViewModel.isLoggedUser(note.author)) {
                Text(
                    text = eventContent,
                    color = MaterialTheme.colors.onSurface.copy(alpha = 0.32f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            } else {
                TranslatableRichTextViewer(
                    content = eventContent,
                    canPreview = canPreview && !makeItShort,
                    modifier = Modifier.fillMaxWidth(),
                    tags = noteEvent.tags(),
                    backgroundColor = backgroundColor,
                    accountViewModel = accountViewModel,
                    navController = navController
                )

                DisplayUncitedHashtags(noteEvent.hashtags(), eventContent, navController)
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
            noteEvent.tags(),
            backgroundColor,
            accountViewModel,
            navController
        )
    }

    if (!makeItShort) {
        ReactionsRow(note, accountViewModel, navController)
    }

    Divider(
        modifier = Modifier.padding(top = 10.dp),
        thickness = 0.25.dp
    )
}

@Composable
fun RenderPeopleList(
    noteState: NoteState?,
    backgroundColor: Color,
    accountViewModel: AccountViewModel,
    navController: NavController
) {
    DisplayPeopleList(noteState, backgroundColor, accountViewModel, navController)

    noteState?.note?.let {
        ReactionsRow(it, accountViewModel, navController)
    }

    Divider(
        modifier = Modifier.padding(top = 10.dp),
        thickness = 0.25.dp
    )
}

@Composable
fun DisplayPeopleList(
    noteState: NoteState?,
    backgroundColor: Color,
    accountViewModel: AccountViewModel,
    navController: NavController
) {
    val note = remember(noteState) { noteState?.note } ?: return
    val noteEvent = note.event as? PeopleListEvent ?: return

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

    LaunchedEffect(key1 = noteState) {
        withContext(Dispatchers.IO) {
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
                        navController = navController
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

@Composable
private fun RenderBadgeAward(
    note: Note,
    backgroundColor: Color,
    accountViewModel: AccountViewModel,
    navController: NavController
) {
    if (note.replyTo.isNullOrEmpty()) return

    val noteEvent = note.event as? BadgeAwardEvent ?: return
    var awardees by remember { mutableStateOf<List<User>>(listOf()) }

    val account = accountViewModel.userProfile()

    Text(text = stringResource(R.string.award_granted_to))

    LaunchedEffect(key1 = note) {
        withContext(Dispatchers.IO) {
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
                        navController.navigate("User/${user.pubkeyHex}")
                    },
                verticalAlignment = Alignment.CenterVertically
            ) {
                UserPicture(
                    baseUser = user,
                    baseUserAccount = accountViewModel.userProfile(),
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
            navController = navController
        )
    }

    ReactionsRow(note, accountViewModel, navController)

    Divider(
        modifier = Modifier.padding(top = 10.dp),
        thickness = 0.25.dp
    )
}

@Composable
private fun RenderReaction(
    note: Note,
    backgroundColor: Color,
    accountViewModel: AccountViewModel,
    navController: NavController
) {
    note.replyTo?.lastOrNull()?.let {
        NoteCompose(
            it,
            modifier = Modifier,
            isBoostedNote = true,
            unPackReply = false,
            parentBackgroundColor = backgroundColor,
            accountViewModel = accountViewModel,
            navController = navController
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
    navController: NavController
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
            navController = navController
        )
    }
}

@Composable
private fun RenderAudioTrack(
    note: Note,
    loggedIn: User,
    accountViewModel: AccountViewModel,
    navController: NavController
) {
    val noteEvent = note.event as? AudioTrackEvent ?: return

    AudioTrackHeader(noteEvent, note, loggedIn, navController)

    ReactionsRow(note, accountViewModel, navController)

    Divider(
        modifier = Modifier.padding(top = 10.dp),
        thickness = 0.25.dp
    )
}

@Composable
private fun RenderLongFormContent(
    note: Note,
    loggedIn: User,
    accountViewModel: AccountViewModel,
    navController: NavController
) {
    val noteEvent = note.event as? LongTextNoteEvent ?: return

    LongFormHeader(noteEvent, note, loggedIn)

    ReactionsRow(note, accountViewModel, navController)

    Divider(
        modifier = Modifier.padding(top = 10.dp),
        thickness = 0.25.dp
    )
}

@Composable
private fun RenderReport(note: Note) {
    val base = remember {
        val noteEvent = note.event as? ReportEvent
        if (noteEvent == null) {
            emptyList<ReportedKey>()
        } else {
            (noteEvent.reportedPost() + noteEvent.reportedAuthor())
        }
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

    Text(
        text = reportType
    )

    Divider(
        modifier = Modifier.padding(top = 40.dp),
        thickness = 0.25.dp
    )
}

@Composable
private fun ReplyRow(
    note: Note,
    unPackReply: Boolean,
    backgroundColor: Color,
    account: Account,
    accountViewModel: AccountViewModel,
    navController: NavController
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
                navController = navController
            )
        } else {
            ReplyInformation(note.replyTo, noteEvent.mentions(), account, navController)
        }

        Spacer(modifier = Modifier.height(5.dp))
    } else if (noteEvent is ChannelMessageEvent && (note.replyTo != null || noteEvent.hasAnyTaggedUser())) {
        note.channel()?.let {
            ReplyInformationChannel(note.replyTo, noteEvent.mentions(), it, account, navController)
        }

        Spacer(modifier = Modifier.height(5.dp))
    }
}

@Composable
private fun SecondUserInfoRow(
    note: Note,
    account: Account,
    navController: NavController
) {
    val noteEvent = remember { note.event } ?: return
    val noteAuthor = remember { note.author } ?: return

    Row(verticalAlignment = Alignment.CenterVertically) {
        ObserveDisplayNip05Status(noteAuthor, Modifier.weight(1f))

        val baseReward = remember { noteEvent.getReward() }
        if (baseReward != null) {
            DisplayReward(baseReward, note, account, navController)
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
    account: Account,
    accountViewModel: AccountViewModel,
    navController: NavController
) {
    var moreActionsExpanded by remember { mutableStateOf(false) }
    val eventNote = remember { baseNote.event } ?: return
    val time = remember { baseNote.createdAt() } ?: return
    val loggedIn = remember { account.userProfile() }
    val padding = remember {
        Modifier.padding(horizontal = 5.dp)
    }

    Row(verticalAlignment = Alignment.CenterVertically) {
        if (showAuthorPicture) {
            NoteAuthorPicture(baseNote, navController, loggedIn, 25.dp)
            Spacer(padding)
            NoteUsernameDisplay(baseNote, Modifier.weight(1f))
        } else {
            NoteUsernameDisplay(baseNote, Modifier.weight(1f))
        }

        if (eventNote is RepostEvent) {
            Text(
                "  ${stringResource(id = R.string.boosted)}",
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colors.onSurface.copy(alpha = 0.32f)
            )
        } else {
            DisplayFollowingHashtagsInPost(eventNote, account, navController)
        }

        TimeAgo(time)

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
}

@Composable
fun TimeAgo(time: Long) {
    val context = LocalContext.current

    var timeStr by remember { mutableStateOf("") }

    LaunchedEffect(key1 = time) {
        withContext(Dispatchers.IO) {
            timeStr = timeAgo(time, context = context)
        }
    }

    Text(
        timeStr,
        color = MaterialTheme.colors.onSurface.copy(alpha = 0.32f),
        maxLines = 1
    )
}

@Composable
private fun DrawAuthorImages(baseNote: Note, loggedIn: User, navController: NavController) {
    val baseChannel = remember { baseNote.channel() }
    val modifier = remember { Modifier.width(55.dp) }

    Column(modifier) {
        // Draws the boosted picture outside the boosted card.
        Box(modifier = modifier, contentAlignment = Alignment.BottomEnd) {
            NoteAuthorPicture(baseNote, navController, loggedIn, 55.dp)

            if (baseNote.event is RepostEvent) {
                RepostNoteAuthorPicture(baseNote, navController, loggedIn)
            }

            if (baseNote.event is ChannelMessageEvent && baseChannel != null) {
                ChannelNotePicture(baseChannel)
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
private fun ChannelNotePicture(baseChannel: Channel) {
    val channelState by baseChannel.live.observeAsState()
    val channel = channelState?.channel

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

    if (channel != null) {
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
}

@Composable
private fun RepostNoteAuthorPicture(
    baseNote: Note,
    navController: NavController,
    loggedIn: User
) {
    val baseRepost = remember { baseNote.replyTo?.lastOrNull() }

    val modifier = remember {
        Modifier
            .width(30.dp)
            .height(30.dp)
    }

    baseRepost?.let {
        Box(modifier) {
            NoteAuthorPicture(
                it,
                navController,
                loggedIn,
                35.dp,
                pictureModifier = Modifier.border(
                    2.dp,
                    MaterialTheme.colors.background,
                    CircleShape
                )
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
    backgroundColor: Color,
    accountViewModel: AccountViewModel,
    navController: NavController
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
        Modifier.fillMaxWidth(),
        emptyList(),
        backgroundColor,
        accountViewModel,
        navController
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

                if (userDisplayName != null) {
                    CreateClickableText(
                        userDisplayName,
                        "",
                        route,
                        navController
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
    account: Account,
    navController: NavController
) {
    var firstTag by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(key1 = noteEvent.id()) {
        withContext(Dispatchers.IO) {
            firstTag = noteEvent.firstIsTaggedHashes(account.followingTagSet())
        }
    }

    Column() {
        Row(verticalAlignment = Alignment.CenterVertically) {
            firstTag?.let {
                ClickableText(
                    text = AnnotatedString(" #$firstTag"),
                    onClick = { navController.navigate("Hashtag/$firstTag") },
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

@Composable
fun DisplayUncitedHashtags(
    hashtags: List<String>,
    eventContent: String,
    navController: NavController
) {
    if (hashtags.isNotEmpty()) {
        FlowRow(
            modifier = Modifier.padding(top = 5.dp)
        ) {
            hashtags.forEach { hashtag ->
                if (!eventContent.contains(hashtag, true)) {
                    ClickableText(
                        text = AnnotatedString("#$hashtag "),
                        onClick = { navController.navigate("Hashtag/$hashtag") },
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

@Composable
fun DisplayReward(
    baseReward: BigDecimal,
    baseNote: Note,
    account: Account,
    navController: NavController
) {
    var popupExpanded by remember { mutableStateOf(false) }

    Column() {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.clickable { popupExpanded = true }
        ) {
            ClickableText(
                text = AnnotatedString("#bounty"),
                onClick = { navController.navigate("Hashtag/bounty") },
                style = LocalTextStyle.current.copy(
                    color = MaterialTheme.colors.primary.copy(
                        alpha = 0.52f
                    )
                )
            )

            val repliesState by baseNote.live().replies.observeAsState()
            val replyNote = repliesState?.note

            if (replyNote?.hasPledgeBy(account.userProfile()) == true) {
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

            var rewardAmount by remember {
                mutableStateOf<BigDecimal?>(
                    baseReward
                )
            }

            LaunchedEffect(key1 = repliesState) {
                withContext(Dispatchers.IO) {
                    replyNote?.pledgedAmountByOthers()?.let {
                        rewardAmount = baseReward.add(it)
                    }
                }
            }

            Text(
                showAmount(rewardAmount),
                color = MaterialTheme.colors.onSurface.copy(alpha = 0.32f)
            )
        }

        if (popupExpanded) {
            AddBountyAmountDialog(baseNote, account) {
                popupExpanded = false
            }
        }
    }
}

@Composable
fun BadgeDisplay(baseNote: Note) {
    val background = MaterialTheme.colors.background
    val badgeData = baseNote.event as? BadgeDefinitionEvent ?: return

    val image = remember { badgeData.image() }
    val name = remember { badgeData.name() }
    val description = remember { badgeData.description() }

    var backgroundFromImage by remember { mutableStateOf(Pair(background, background)) }
    var imageResult by remember { mutableStateOf<AsyncImagePainter.State.Success?>(null) }

    LaunchedEffect(key1 = imageResult) {
        withContext(Dispatchers.IO) {
            imageResult?.let {
                val backgroundColor = it.result.drawable.toBitmap(200, 200).copy(Bitmap.Config.ARGB_8888, false).get(0, 199)
                val colorFromImage = Color(backgroundColor)
                val textBackground = if (colorFromImage.luminance() > 0.5) lightColors().onBackground else darkColors().onBackground
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
                    onSuccess = {
                        imageResult = it
                    }
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
                    color = backgroundFromImage.second
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
}

@Composable
fun FileHeaderDisplay(note: Note) {
    val event = (note.event as? FileHeaderEvent) ?: return
    val fullUrl = event.url() ?: return

    var content by remember { mutableStateOf<ZoomableContent?>(null) }

    LaunchedEffect(key1 = event.id) {
        withContext(Dispatchers.IO) {
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

    content?.let {
        ZoomableContentView(content = it, listOf(it))
    }
}

@Composable
fun FileStorageHeaderDisplay(baseNote: Note) {
    val appContext = LocalContext.current.applicationContext
    val eventHeader = (baseNote.event as? FileStorageHeaderEvent) ?: return

    var fileNote by remember { mutableStateOf<Note?>(null) }

    LaunchedEffect(key1 = eventHeader.id) {
        withContext(Dispatchers.IO) {
            fileNote = eventHeader.dataEventId()?.let { LocalCache.checkGetOrCreateNote(it) }
        }
    }

    val noteState = fileNote?.live()?.metadata?.observeAsState()
    val note = noteState?.value?.note

    var content by remember { mutableStateOf<ZoomableContent?>(null) }

    LaunchedEffect(key1 = eventHeader.id, key2 = noteState, key3 = note?.event) {
        withContext(Dispatchers.IO) {
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

    content?.let {
        ZoomableContentView(content = it, listOf(it))
    }
}

@Composable
fun AudioTrackHeader(noteEvent: AudioTrackEvent, note: Note, loggedIn: User, navController: NavController) {
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
                    modifier = Modifier.padding(top = 5.dp, start = 10.dp, end = 10.dp).clickable {
                        navController.navigate("User/${it.second.pubkeyHex}")
                    }
                ) {
                    UserPicture(it.second, loggedIn, 25.dp)
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
                        VideoView(
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
private fun LongFormHeader(noteEvent: LongTextNoteEvent, note: Note, loggedIn: User) {
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
            } ?: Box() {
                note.author?.info?.banner?.let {
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
                    NoteAuthorPicture(baseNote = note, baseUserAccount = loggedIn, size = 55.dp)
                }
            }

            noteEvent.title()?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.body1,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 10.dp, end = 10.dp, top = 10.dp)
                )
            }

            noteEvent.summary()?.let {
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
                ?: Text(
                    text = noteEvent.content.take(200),
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

@Composable
private fun RelayBadges(baseNote: Note) {
    val noteRelaysState by baseNote.live().relays.observeAsState()
    val noteRelays = noteRelaysState?.note ?: return

    var expanded by remember { mutableStateOf(false) }
    var showShowMore by remember { mutableStateOf(false) }
    var lazyRelayList by remember { mutableStateOf(emptyList<String>()) }

    LaunchedEffect(key1 = noteRelaysState, key2 = expanded) {
        withContext(Dispatchers.IO) {
            val relayList = noteRelays.relays.map {
                it.removePrefix("wss://").removePrefix("ws://")
            }

            val relaysToDisplay = if (expanded) relayList else relayList.take(3)
            val shouldListChange = lazyRelayList.size < 3 || lazyRelayList.size != relayList.size

            if (shouldListChange) {
                lazyRelayList = relaysToDisplay
            }

            val nextShowMore = relayList.size > 3 && !expanded
            if (nextShowMore != showShowMore) {
                // only triggers recomposition when actually different
                showShowMore = nextShowMore
            }
        }
    }

    Spacer(Modifier.height(10.dp))

    VerticalRelayPanelWithFlow(lazyRelayList)

    if (showShowMore) {
        ShowMoreRelaysButton {
            expanded = true
        }
    }
}

@Composable
@Stable
private fun VerticalRelayPanelWithFlow(
    relays: List<String>
) {
    // FlowRow Seems to be a lot faster than LazyVerticalGrid
    FlowRow() {
        relays.forEach { url ->
            RelayIconCompose(url)
        }
    }
}

@Composable
@Stable
private fun RelayIconCompose(url: String) {
    val uri = LocalUriHandler.current

    val model = remember(url) { "https://$url/favicon.ico" }

    val boxModifier = remember {
        Modifier
            .padding(1.dp)
            .size(15.dp)
    }
    val iconModifier = remember(url) {
        Modifier
            .width(13.dp)
            .height(13.dp)
            .clip(shape = CircleShape)
            .clickable(onClick = { uri.openUri("https://$url") })
    }
    val colorFilter = remember {
        ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) })
    }

    Box(boxModifier) {
        RobohashFallbackAsyncImage(
            robot = model,
            robotSize = 15.dp,
            model = model,
            contentDescription = stringResource(R.string.relay_icon),
            colorFilter = colorFilter,
            modifier = iconModifier.background(MaterialTheme.colors.background)
        )
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
    navController: NavController,
    userAccount: User,
    size: Dp,
    pictureModifier: Modifier = Modifier
) {
    NoteAuthorPicture(baseNote, userAccount, size, pictureModifier) {
        navController.navigate("User/${it.pubkeyHex}")
    }
}

@Composable
fun NoteAuthorPicture(
    baseNote: Note,
    baseUserAccount: User,
    size: Dp,
    modifier: Modifier = Modifier,
    onClick: ((User) -> Unit)? = null
) {
    val noteState by baseNote.live().metadata.observeAsState()
    val author = remember(noteState) {
        noteState?.note?.author
    }

    val boxModifier = remember {
        Modifier
            .width(size)
            .height(size)
    }

    val nullModifier = remember {
        modifier
            .width(size)
            .height(size)
            .clip(shape = CircleShape)
    }

    Box(boxModifier) {
        if (author == null) {
            RobohashAsyncImage(
                robot = "authornotfound",
                robotSize = size,
                contentDescription = stringResource(R.string.unknown_author),
                modifier = nullModifier.background(MaterialTheme.colors.background)
            )
        } else {
            UserPicture(author, baseUserAccount, size, modifier, onClick)
        }
    }
}

@Composable
fun UserPicture(
    user: User,
    navController: NavController,
    userAccount: User,
    size: Dp,
    pictureModifier: Modifier = Modifier
) {
    UserPicture(user, userAccount, size, pictureModifier) {
        navController.navigate("User/${it.pubkeyHex}")
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun UserPicture(
    baseUser: User,
    baseUserAccount: User,
    size: Dp,
    modifier: Modifier = Modifier,
    onClick: ((User) -> Unit)? = null,
    onLongClick: ((User) -> Unit)? = null
) {
    val userState by baseUser.live().metadata.observeAsState()
    val accountState by baseUserAccount.live().follows.observeAsState()

    val userPubkey = remember {
        baseUser.pubkeyHex
    }

    val userProfile = remember(userState) {
        userState?.user?.profilePicture()
    }

    val showFollowingMark = remember(accountState) {
        accountState?.user?.isFollowingCached(baseUser) == true || baseUser === accountState?.user
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
            showFollowingMark = showFollowingMark,
            size = size,
            modifier = modifier
        )
    }
}

@Composable
fun UserPicture(
    userHex: String,
    userPicture: String?,
    showFollowingMark: Boolean,
    size: Dp,
    modifier: Modifier = Modifier
) {
    val myBoxModifier = remember {
        Modifier
            .width(size)
            .height(size)
    }

    val myImageModifier = remember {
        modifier
            .width(size)
            .height(size)
            .clip(shape = CircleShape)
    }

    val myResizeImage = remember(userPicture) {
        ResizeImage(userPicture, size)
    }

    Box(myBoxModifier) {
        RobohashAsyncImageProxy(
            robot = userHex,
            model = myResizeImage,
            contentDescription = stringResource(id = R.string.profile_image),
            modifier = myImageModifier.background(MaterialTheme.colors.background)
        )

        if (showFollowingMark) {
            val myIconBoxModifier = remember {
                Modifier
                    .width(size.div(3.5f))
                    .height(size.div(3.5f))
                    .align(Alignment.TopEnd)
            }

            val myIconBackgroundModifier = remember {
                Modifier
                    .clip(CircleShape)
                    .fillMaxSize(0.6f)
                    .align(Alignment.Center)
            }

            val myIconModifier = remember {
                Modifier.fillMaxSize()
            }

            Box(myIconBoxModifier, contentAlignment = Alignment.Center) {
                Box(
                    myIconBackgroundModifier.background(MaterialTheme.colors.background)
                )

                Icon(
                    painter = painterResource(R.drawable.ic_verified),
                    stringResource(id = R.string.following),
                    modifier = myIconModifier,
                    tint = Following
                )
            }
        }
    }
}

data class DropDownParams(
    val isFollowingAuthor: Boolean,
    val isPrivateBookmarkNote: Boolean,
    val isPublicBookmarkNote: Boolean,
    val isLoggedUser: Boolean
)

@Composable
fun NoteDropDownMenu(note: Note, popupExpanded: Boolean, onDismiss: () -> Unit, accountViewModel: AccountViewModel) {
    val clipboardManager = LocalClipboardManager.current
    val appContext = LocalContext.current.applicationContext
    val actContext = LocalContext.current
    var reportDialogShowing by remember { mutableStateOf(false) }

    var state by remember {
        mutableStateOf<DropDownParams>(
            DropDownParams(false, false, false, false)
        )
    }

    LaunchedEffect(key1 = note) {
        withContext(Dispatchers.IO) {
            state = DropDownParams(
                accountViewModel.isFollowing(note.author),
                accountViewModel.isInPrivateBookmarks(note),
                accountViewModel.isInPublicBookmarks(note),
                accountViewModel.isLoggedUser(note.author)
            )
        }
    }

    DropdownMenu(
        expanded = popupExpanded,
        onDismissRequest = onDismiss
    ) {
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
        DropdownMenuItem(onClick = { clipboardManager.setText(AnnotatedString(accountViewModel.decrypt(note) ?: "")); onDismiss() }) {
            Text(stringResource(R.string.copy_text))
        }
        DropdownMenuItem(onClick = { clipboardManager.setText(AnnotatedString("nostr:${note.author?.pubkeyNpub()}")); onDismiss() }) {
            Text(stringResource(R.string.copy_user_pubkey))
        }
        DropdownMenuItem(onClick = { clipboardManager.setText(AnnotatedString("nostr:" + note.toNEvent())); onDismiss() }) {
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
            DropdownMenuItem(onClick = { accountViewModel.removePrivateBookmark(note); onDismiss() }) {
                Text(stringResource(R.string.remove_from_private_bookmarks))
            }
        } else {
            DropdownMenuItem(onClick = { accountViewModel.addPrivateBookmark(note); onDismiss() }) {
                Text(stringResource(R.string.add_to_private_bookmarks))
            }
        }
        if (state.isPublicBookmarkNote) {
            DropdownMenuItem(onClick = { accountViewModel.removePublicBookmark(note); onDismiss() }) {
                Text(stringResource(R.string.remove_from_public_bookmarks))
            }
        } else {
            DropdownMenuItem(onClick = { accountViewModel.addPublicBookmark(note); onDismiss() }) {
                Text(stringResource(R.string.add_to_public_bookmarks))
            }
        }
        Divider()
        DropdownMenuItem(onClick = { accountViewModel.broadcast(note); onDismiss() }) {
            Text(stringResource(R.string.broadcast))
        }
        Divider()
        if (state.isLoggedUser) {
            DropdownMenuItem(onClick = { accountViewModel.delete(note); onDismiss() }) {
                Text(stringResource(R.string.request_deletion))
            }
        } else {
            DropdownMenuItem(onClick = { reportDialogShowing = true }) {
                Text("Block / Report")
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
