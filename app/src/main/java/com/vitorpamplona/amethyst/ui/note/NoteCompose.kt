package com.vitorpamplona.amethyst.ui.note

import android.content.Intent
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.painter.BitmapPainter
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
import androidx.core.content.ContextCompat
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.google.accompanist.flowlayout.FlowRow
import com.vitorpamplona.amethyst.NotificationCache
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.RoboHashCache
import com.vitorpamplona.amethyst.model.LocalCache
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.model.User
import com.vitorpamplona.amethyst.service.model.BadgeAwardEvent
import com.vitorpamplona.amethyst.service.model.BadgeDefinitionEvent
import com.vitorpamplona.amethyst.service.model.ChannelCreateEvent
import com.vitorpamplona.amethyst.service.model.ChannelMessageEvent
import com.vitorpamplona.amethyst.service.model.ChannelMetadataEvent
import com.vitorpamplona.amethyst.service.model.LongTextNoteEvent
import com.vitorpamplona.amethyst.service.model.PrivateDmEvent
import com.vitorpamplona.amethyst.service.model.ReactionEvent
import com.vitorpamplona.amethyst.service.model.ReportEvent
import com.vitorpamplona.amethyst.service.model.RepostEvent
import com.vitorpamplona.amethyst.service.model.TextNoteEvent
import com.vitorpamplona.amethyst.ui.components.AsyncImageProxy
import com.vitorpamplona.amethyst.ui.components.ObserveDisplayNip05Status
import com.vitorpamplona.amethyst.ui.components.ResizeImage
import com.vitorpamplona.amethyst.ui.components.TranslateableRichTextViewer
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.screen.loggedIn.ChannelHeader
import com.vitorpamplona.amethyst.ui.theme.Following

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
    parentBackgroundColor: Color? = null,
    accountViewModel: AccountViewModel,
    navController: NavController
) {
    val accountState by accountViewModel.accountLiveData.observeAsState()
    val account = accountState?.account ?: return

    val noteState by baseNote.live().metadata.observeAsState()
    val note = noteState?.note

    val noteReportsState by baseNote.live().reports.observeAsState()
    val noteForReports = noteReportsState?.note ?: return

    var popupExpanded by remember { mutableStateOf(false) }
    var showHiddenNote by remember { mutableStateOf(false) }

    val context = LocalContext.current.applicationContext

    var moreActionsExpanded by remember { mutableStateOf(false) }

    val noteEvent = note?.event
    val baseChannel = note?.channel()

    if (noteEvent == null) {
        BlankNote(
            modifier.combinedClickable(
                onClick = { },
                onLongClick = { popupExpanded = true }
            ),
            isBoostedNote
        )
    } else if (!account.isAcceptable(noteForReports) && !showHiddenNote) {
        HiddenNote(
            account.getRelevantReports(noteForReports),
            account.userProfile(),
            modifier,
            isBoostedNote,
            navController,
            onClick = { showHiddenNote = true }
        )
    } else if ((noteEvent is ChannelCreateEvent || noteEvent is ChannelMetadataEvent) && baseChannel != null) {
        ChannelHeader(baseChannel = baseChannel, account = account, navController = navController)
    } else if (noteEvent is BadgeDefinitionEvent) {
        BadgeDisplay(baseNote = note)
    } else {
        var isNew by remember { mutableStateOf<Boolean>(false) }

        LaunchedEffect(key1 = routeForLastRead) {
            routeForLastRead?.let {
                val lastTime = NotificationCache.load(it, context)

                val createdAt = note.createdAt()
                if (createdAt != null) {
                    NotificationCache.markAsRead(it, createdAt, context)
                    isNew = createdAt > lastTime
                }
            }
        }

        var backgroundColor = if (isNew) {
            val newColor = MaterialTheme.colors.primary.copy(0.12f)
            if (parentBackgroundColor != null) {
                newColor.compositeOver(parentBackgroundColor)
            } else {
                newColor.compositeOver(MaterialTheme.colors.background)
            }
        } else {
            parentBackgroundColor ?: MaterialTheme.colors.background
        }

        Column(
            modifier = modifier
                .combinedClickable(
                    onClick = {
                        if (noteEvent is ChannelMessageEvent) {
                            baseChannel?.let {
                                navController.navigate("Channel/${it.idHex}")
                            }
                        } else if (noteEvent is PrivateDmEvent) {
                            navController.navigate("Room/${note.author?.pubkeyHex}") {
                                launchSingleTop = true
                            }
                        } else {
                            navController.navigate("Note/${note.idHex}") {
                                launchSingleTop = true
                            }
                        }
                    },
                    onLongClick = { popupExpanded = true }
                )
                .background(backgroundColor)
        ) {
            Row(
                modifier = Modifier
                    .padding(
                        start = if (!isBoostedNote) 12.dp else 0.dp,
                        end = if (!isBoostedNote) 12.dp else 0.dp,
                        top = 10.dp
                    )
            ) {
                if (!isBoostedNote && !isQuotedNote) {
                    Column(Modifier.width(55.dp)) {
                        // Draws the boosted picture outside the boosted card.
                        Box(
                            modifier = Modifier
                                .width(55.dp)
                                .padding(0.dp)
                        ) {
                            NoteAuthorPicture(note, navController, account.userProfile(), 55.dp)

                            if (noteEvent is RepostEvent) {
                                note.replyTo?.lastOrNull()?.let {
                                    Box(
                                        Modifier
                                            .width(30.dp)
                                            .height(30.dp)
                                            .align(Alignment.BottomEnd)
                                    ) {
                                        NoteAuthorPicture(
                                            it,
                                            navController,
                                            account.userProfile(),
                                            35.dp,
                                            pictureModifier = Modifier.border(2.dp, MaterialTheme.colors.background, CircleShape)
                                        )
                                    }
                                }
                            }

                            // boosted picture
                            if (noteEvent is ChannelMessageEvent && baseChannel != null) {
                                val channelState by baseChannel.live.observeAsState()
                                val channel = channelState?.channel

                                if (channel != null) {
                                    Box(
                                        Modifier
                                            .width(30.dp)
                                            .height(30.dp)
                                            .align(Alignment.BottomEnd)
                                    ) {
                                        AsyncImageProxy(
                                            model = ResizeImage(channel.profilePicture(), 30.dp),
                                            placeholder = BitmapPainter(RoboHashCache.get(context, channel.idHex)),
                                            fallback = BitmapPainter(RoboHashCache.get(context, channel.idHex)),
                                            error = BitmapPainter(RoboHashCache.get(context, channel.idHex)),
                                            contentDescription = stringResource(R.string.group_picture),
                                            modifier = Modifier
                                                .width(30.dp)
                                                .height(30.dp)
                                                .clip(shape = CircleShape)
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
                        }

                        if (noteEvent is RepostEvent) {
                            note.replyTo?.lastOrNull()?.let {
                                RelayBadges(it)
                            }
                        } else {
                            RelayBadges(baseNote)
                        }
                    }
                }

                Column(
                    modifier = Modifier.padding(start = if (!isBoostedNote && !isQuotedNote) 10.dp else 0.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (isQuotedNote) {
                            NoteAuthorPicture(note, navController, account.userProfile(), 25.dp)
                            Spacer(Modifier.padding(horizontal = 5.dp))
                            NoteUsernameDisplay(note, Modifier.weight(1f))
                        } else {
                            NoteUsernameDisplay(note, Modifier.weight(1f))
                        }

                        if (noteEvent is RepostEvent) {
                            Text(
                                "  ${stringResource(id = R.string.boosted)}",
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colors.onSurface.copy(alpha = 0.32f)
                            )
                        }

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

                    if (note.author != null && !makeItShort) {
                        ObserveDisplayNip05Status(note.author!!)
                    }

                    Spacer(modifier = Modifier.height(3.dp))

                    if (noteEvent is TextNoteEvent && (note.replyTo != null || noteEvent.mentions().isNotEmpty())) {
                        val sortedMentions = noteEvent.mentions()
                            .map { LocalCache.getOrCreateUser(it) }
                            .toSet()
                            .sortedBy { account.userProfile().isFollowing(it) }

                        val replyingDirectlyTo = note.replyTo?.lastOrNull()
                        if (replyingDirectlyTo != null && unPackReply) {
                            NoteCompose(
                                baseNote = replyingDirectlyTo,
                                isQuotedNote = true,
                                modifier = Modifier
                                    .padding(0.dp)
                                    .fillMaxWidth()
                                    .clip(shape = RoundedCornerShape(15.dp))
                                    .border(
                                        1.dp,
                                        MaterialTheme.colors.onSurface.copy(alpha = 0.12f),
                                        RoundedCornerShape(15.dp)
                                    ),
                                unPackReply = false,
                                makeItShort = true,
                                parentBackgroundColor = MaterialTheme.colors.onSurface.copy(alpha = 0.05f).compositeOver(backgroundColor),
                                accountViewModel = accountViewModel,
                                navController = navController
                            )
                        } else {
                            ReplyInformation(note.replyTo, sortedMentions, account, navController)
                        }
                    } else if (noteEvent is ChannelMessageEvent && (note.replyTo != null || noteEvent.mentions() != null)) {
                        val sortedMentions = noteEvent.mentions()
                            .map { LocalCache.getOrCreateUser(it) }
                            .toSet()
                            .sortedBy { account.userProfile().isFollowing(it) }

                        note.channel()?.let {
                            ReplyInformationChannel(note.replyTo, sortedMentions, it, navController)
                        }
                    }

                    if (noteEvent is ReactionEvent || noteEvent is RepostEvent) {
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
                        if (noteEvent is ReactionEvent) {
                            val refactorReactionText =
                                if (noteEvent.content == "+") "❤" else noteEvent.content ?: " "

                            Text(
                                text = refactorReactionText
                            )
                        }
                    } else if (noteEvent is ReportEvent) {
                        val reportType = (noteEvent.reportedPost() + noteEvent.reportedAuthor()).map {
                            when (it.reportType) {
                                ReportEvent.ReportType.EXPLICIT -> stringResource(R.string.explicit_content)
                                ReportEvent.ReportType.NUDITY -> stringResource(R.string.nudity)
                                ReportEvent.ReportType.PROFANITY -> stringResource(R.string.profanity_hateful_speech)
                                ReportEvent.ReportType.SPAM -> stringResource(R.string.spam)
                                ReportEvent.ReportType.IMPERSONATION -> stringResource(R.string.impersonation)
                                ReportEvent.ReportType.ILLEGAL -> stringResource(R.string.illegal_behavior)
                                else -> stringResource(R.string.unknown)
                            }
                        }.toSet().joinToString(", ")

                        Text(
                            text = reportType
                        )

                        Divider(
                            modifier = Modifier.padding(top = 40.dp),
                            thickness = 0.25.dp
                        )
                    } else if (noteEvent is LongTextNoteEvent) {
                        LongFormHeader(noteEvent)

                        ReactionsRow(note, accountViewModel)

                        Divider(
                            modifier = Modifier.padding(top = 10.dp),
                            thickness = 0.25.dp
                        )
                    } else if (noteEvent is BadgeAwardEvent && !note.replyTo.isNullOrEmpty()) {
                        Text(text = stringResource(R.string.award_granted_to))

                        FlowRow(modifier = Modifier.padding(top = 5.dp)) {
                            noteEvent.awardees()
                                .map { LocalCache.getOrCreateUser(it) }
                                .forEach {
                                    UserPicture(
                                        user = it,
                                        navController = navController,
                                        userAccount = account.userProfile(),
                                        size = 35.dp
                                    )
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

                        ReactionsRow(note, accountViewModel)

                        Divider(
                            modifier = Modifier.padding(top = 10.dp),
                            thickness = 0.25.dp
                        )
                    } else {
                        val eventContent = accountViewModel.decrypt(note)

                        val canPreview = note.author == account.userProfile() ||
                            (note.author?.let { account.userProfile().isFollowing(it) } ?: true) ||
                            !noteForReports.hasAnyReports()

                        if (eventContent != null) {
                            TranslateableRichTextViewer(
                                eventContent,
                                canPreview = canPreview && !makeItShort,
                                Modifier.fillMaxWidth(),
                                noteEvent.tags(),
                                backgroundColor,
                                accountViewModel,
                                navController
                            )
                        }

                        if (!makeItShort) {
                            ReactionsRow(note, accountViewModel)
                        }

                        Divider(
                            modifier = Modifier.padding(top = 10.dp),
                            thickness = 0.25.dp
                        )
                    }

                    NoteQuickActionMenu(note, popupExpanded, { popupExpanded = false }, accountViewModel)
                }
            }
        }
    }
}

@Composable
fun BadgeDisplay(baseNote: Note) {
    val badgeData = baseNote.event as? BadgeDefinitionEvent ?: return

    Row(
        modifier = Modifier
            .padding(10.dp)
            .clip(shape = CutCornerShape(20, 20, 0, 0))
            .border(
                5.dp,
                MaterialTheme.colors.primary.copy(alpha = 0.32f),
                CutCornerShape(20)
            )
    ) {
        Column {
            badgeData.image()?.let {
                AsyncImage(
                    model = it,
                    contentDescription = stringResource(
                        R.string.badge_award_image_for,
                        it
                    ),
                    contentScale = ContentScale.FillWidth,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            badgeData.name()?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.body1,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 10.dp, end = 10.dp, top = 10.dp)
                )
            }

            badgeData.description()?.let {
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
private fun LongFormHeader(noteEvent: LongTextNoteEvent) {
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
        }
    }
}

@Composable
private fun RelayBadges(baseNote: Note) {
    val noteRelaysState by baseNote.live().relays.observeAsState()
    val noteRelays = noteRelaysState?.note?.relays ?: emptySet()

    var expanded by remember { mutableStateOf(false) }

    val relaysToDisplay = if (expanded) noteRelays else noteRelays.take(3)

    val uri = LocalUriHandler.current
    val ctx = LocalContext.current.applicationContext

    FlowRow(Modifier.padding(top = 10.dp, start = 5.dp, end = 4.dp)) {
        relaysToDisplay.forEach {
            val url = it.removePrefix("wss://").removePrefix("ws://")
            Box(
                Modifier
                    .size(15.dp)
                    .padding(1.dp)
            ) {
                AsyncImage(
                    model = "https://$url/favicon.ico",
                    placeholder = BitmapPainter(RoboHashCache.get(ctx, url)),
                    fallback = BitmapPainter(RoboHashCache.get(ctx, url)),
                    error = BitmapPainter(RoboHashCache.get(ctx, url)),
                    contentDescription = stringResource(R.string.relay_icon),
                    colorFilter = ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) }),
                    modifier = Modifier
                        .fillMaxSize(1f)
                        .clip(shape = CircleShape)
                        .background(MaterialTheme.colors.background)
                        .clickable(onClick = { uri.openUri("https://" + url) })
                )
            }
        }
    }

    if (noteRelays.size > 3 && !expanded) {
        Row(
            Modifier
                .fillMaxWidth()
                .height(25.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.Top
        ) {
            IconButton(
                modifier = Modifier.then(Modifier.size(24.dp)),
                onClick = { expanded = true }
            ) {
                Icon(
                    imageVector = Icons.Default.ExpandMore,
                    null,
                    modifier = Modifier.size(15.dp),
                    tint = MaterialTheme.colors.onSurface.copy(alpha = 0.32f)
                )
            }
        }
    }
}

@Composable
fun NoteAuthorPicture(
    note: Note,
    navController: NavController,
    userAccount: User,
    size: Dp,
    pictureModifier: Modifier = Modifier
) {
    NoteAuthorPicture(note, userAccount, size, pictureModifier) {
        navController.navigate("User/${it.pubkeyHex}")
    }
}

@Composable
fun NoteAuthorPicture(
    baseNote: Note,
    baseUserAccount: User,
    size: Dp,
    pictureModifier: Modifier = Modifier,
    onClick: ((User) -> Unit)? = null
) {
    val noteState by baseNote.live().metadata.observeAsState()
    val note = noteState?.note ?: return

    val author = note.author

    val ctx = LocalContext.current.applicationContext

    Box(
        Modifier
            .width(size)
            .height(size)
    ) {
        if (author == null) {
            Image(
                painter = BitmapPainter(RoboHashCache.get(ctx, "ohnothisauthorisnotfound")),
                contentDescription = stringResource(R.string.unknown_author),
                modifier = pictureModifier
                    .fillMaxSize(1f)
                    .clip(shape = CircleShape)
                    .background(MaterialTheme.colors.background)
            )
        } else {
            UserPicture(author, baseUserAccount, size, pictureModifier, onClick)
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
    pictureModifier: Modifier = Modifier,
    onClick: ((User) -> Unit)? = null,
    onLongClick: ((User) -> Unit)? = null
) {
    val userState by baseUser.live().metadata.observeAsState()
    val user = userState?.user ?: return

    val ctx = LocalContext.current.applicationContext

    Box(
        Modifier
            .width(size)
            .height(size)
    ) {
        AsyncImageProxy(
            model = ResizeImage(user.profilePicture(), size),
            contentDescription = stringResource(id = R.string.profile_image),
            placeholder = BitmapPainter(RoboHashCache.get(ctx, user.pubkeyHex)),
            fallback = BitmapPainter(RoboHashCache.get(ctx, user.pubkeyHex)),
            error = BitmapPainter(RoboHashCache.get(ctx, user.pubkeyHex)),
            modifier = pictureModifier
                .fillMaxSize(1f)
                .clip(shape = CircleShape)
                .background(MaterialTheme.colors.background)
                .run {
                    if (onClick != null && onLongClick != null) {
                        this.combinedClickable(onClick = { onClick(user) }, onLongClick = { onLongClick(user) })
                    } else if (onClick != null) {
                        this.clickable(onClick = { onClick(user) })
                    } else {
                        this
                    }
                }

        )

        val accountState by baseUserAccount.live().follows.observeAsState()
        val accountUser = accountState?.user ?: return

        if (accountUser.isFollowing(user) || user == accountUser) {
            Box(
                Modifier
                    .width(size.div(3.5f))
                    .height(size.div(3.5f))
                    .align(Alignment.TopEnd),
                contentAlignment = Alignment.Center
            ) {
                // Background for the transparent checkmark
                Box(
                    Modifier
                        .clip(CircleShape)
                        .fillMaxSize(0.6f)
                        .align(Alignment.Center)
                        .background(MaterialTheme.colors.background)
                )

                Icon(
                    painter = painterResource(R.drawable.ic_verified),
                    stringResource(id = R.string.following),
                    modifier = Modifier.fillMaxSize(),
                    tint = Following
                )
            }
        }
    }
}

@Composable
fun NoteDropDownMenu(note: Note, popupExpanded: Boolean, onDismiss: () -> Unit, accountViewModel: AccountViewModel) {
    val clipboardManager = LocalClipboardManager.current
    val appContext = LocalContext.current.applicationContext
    val actContext = LocalContext.current

    DropdownMenu(
        expanded = popupExpanded,
        onDismissRequest = onDismiss
    ) {
        if (note.author != accountViewModel.accountLiveData.value?.account?.userProfile() && !accountViewModel.accountLiveData.value?.account?.userProfile()!!.isFollowing(note.author!!)) {
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
        DropdownMenuItem(onClick = { clipboardManager.setText(AnnotatedString("@${note.author?.pubkeyNpub()}" ?: "")); onDismiss() }) {
            Text(stringResource(R.string.copy_user_pubkey))
        }
        DropdownMenuItem(onClick = { clipboardManager.setText(AnnotatedString(note.idNote())); onDismiss() }) {
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
        DropdownMenuItem(onClick = { accountViewModel.broadcast(note); onDismiss() }) {
            Text(stringResource(R.string.broadcast))
        }
        if (note.author == accountViewModel.accountLiveData.value?.account?.userProfile()) {
            Divider()
            DropdownMenuItem(onClick = { accountViewModel.delete(note); onDismiss() }) {
                Text("Request Deletion")
            }
        }
        if (note.author != accountViewModel.accountLiveData.value?.account?.userProfile()) {
            Divider()
            DropdownMenuItem(onClick = {
                note.author?.let {
                    accountViewModel.hide(
                        it,
                        appContext
                    )
                }; onDismiss()
            }) {
                Text(stringResource(R.string.block_hide_user))
            }
            Divider()
            DropdownMenuItem(onClick = {
                accountViewModel.report(note, ReportEvent.ReportType.SPAM)
                note.author?.let { accountViewModel.hide(it, appContext) }
                onDismiss()
            }) {
                Text(stringResource(R.string.report_spam_scam))
            }
            DropdownMenuItem(onClick = {
                accountViewModel.report(note, ReportEvent.ReportType.PROFANITY)
                note.author?.let { accountViewModel.hide(it, appContext) }
                onDismiss()
            }) {
                Text(stringResource(R.string.report_hateful_speech))
            }
            DropdownMenuItem(onClick = {
                accountViewModel.report(note, ReportEvent.ReportType.IMPERSONATION)
                note.author?.let { accountViewModel.hide(it, appContext) }
                onDismiss()
            }) {
                Text(stringResource(R.string.report_impersonation))
            }
            DropdownMenuItem(onClick = {
                accountViewModel.report(note, ReportEvent.ReportType.NUDITY)
                note.author?.let { accountViewModel.hide(it, appContext) }
                onDismiss()
            }) {
                Text(stringResource(R.string.report_nudity_porn))
            }
            DropdownMenuItem(onClick = {
                accountViewModel.report(note, ReportEvent.ReportType.ILLEGAL)
                note.author?.let { accountViewModel.hide(it, appContext) }
                onDismiss()
            }) {
                Text(stringResource(R.string.report_illegal_behaviour))
            }
        }
    }
}
