package com.vitorpamplona.amethyst.ui.note

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.Divider
import androidx.compose.material.DropdownMenu
import androidx.compose.material.DropdownMenuItem
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.LocalContentColor
import androidx.compose.material.LocalTextStyle
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import coil.compose.AsyncImage
import coil.compose.rememberAsyncImagePainter
import com.google.accompanist.flowlayout.FlowRow
import com.vitorpamplona.amethyst.NotificationCache
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.RoboHashCache
import com.vitorpamplona.amethyst.model.Account
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.model.User
import com.vitorpamplona.amethyst.model.toNote
import com.vitorpamplona.amethyst.service.model.ChannelMessageEvent
import com.vitorpamplona.amethyst.service.model.LnZapEvent
import com.vitorpamplona.amethyst.service.model.ReactionEvent
import com.vitorpamplona.amethyst.service.model.ReportEvent
import com.vitorpamplona.amethyst.service.model.RepostEvent
import com.vitorpamplona.amethyst.ui.components.AsyncImageProxy
import com.vitorpamplona.amethyst.ui.components.ResizeImage
import com.vitorpamplona.amethyst.ui.components.RichTextViewer
import com.vitorpamplona.amethyst.ui.components.TranslateableRichTextViewer
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.theme.Following
import nostr.postr.events.TextNoteEvent
import nostr.postr.toNpub

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun NoteCompose(
    baseNote: Note,
    routeForLastRead: String? = null,
    modifier: Modifier = Modifier,
    isInnerNote: Boolean = false,
    accountViewModel: AccountViewModel,
    navController: NavController
) {
    val accountState by accountViewModel.accountLiveData.observeAsState()
    val account = accountState?.account ?: return

    val noteState by baseNote.live.observeAsState()
    val note = noteState?.note

    val noteReportsState by baseNote.liveReports.observeAsState()
    val noteForReports = noteReportsState?.note ?: return

    var popupExpanded by remember { mutableStateOf(false) }
    var showHiddenNote by remember { mutableStateOf(false) }

    val context = LocalContext.current.applicationContext

    var moreActionsExpanded by remember { mutableStateOf(false) }


    if (note?.event == null) {
        BlankNote(modifier.combinedClickable(
            onClick = {  },
            onLongClick = { popupExpanded = true },
        ), isInnerNote)
    } else if (!account.isAcceptable(noteForReports) && !showHiddenNote) {
        HiddenNote(
            account.getRelevantReports(noteForReports),
            account.userProfile(),
            modifier,
            isInnerNote,
            navController,
            onClick = { showHiddenNote = true }
        )
    } else {
        val isNew = routeForLastRead?.run {
            val lastTime = NotificationCache.load(this, context)

            val createdAt = note.event?.createdAt
            if (createdAt != null) {
                NotificationCache.markAsRead(this, createdAt, context)
                createdAt > lastTime
            } else {
                false
            }
        } ?: false

        Column(modifier =
            modifier.combinedClickable(
                onClick = {
                    if (note.event !is ChannelMessageEvent) {
                        navController.navigate("Note/${note.idHex}"){
                            launchSingleTop = true
                        }
                    } else {
                        note.channel?.let {
                            navController.navigate("Channel/${it.idHex}")
                        }
                    }
                },
                onLongClick = { popupExpanded = true }
            ).run {
                if (isNew) {
                    this.background(MaterialTheme.colors.primary.copy(0.12f))
                } else {
                    this
                }
            }
        ) {
            Row(
                modifier = Modifier
                    .padding(
                        start = if (!isInnerNote) 12.dp else 0.dp,
                        end = if (!isInnerNote) 12.dp else 0.dp,
                        top = 10.dp)
            ) {

                if (!isInnerNote) {
                    Column(Modifier.width(55.dp)) {
                    // Draws the boosted picture outside the boosted card.
                        Box(modifier = Modifier
                            .width(55.dp)
                            .padding(0.dp)) {

                            NoteAuthorPicture(note, navController, account.userProfile(), 55.dp)

                            if (note.event is RepostEvent) {
                                note.replyTo?.lastOrNull()?.let {
                                    Box(
                                        Modifier
                                            .width(30.dp)
                                            .height(30.dp)
                                            .align(Alignment.BottomEnd)) {
                                        NoteAuthorPicture(it, navController, account.userProfile(), 35.dp,
                                            pictureModifier = Modifier.border(2.dp, MaterialTheme.colors.background, CircleShape)
                                        )
                                    }
                                }
                            }

                            // boosted picture
                            val baseChannel = note.channel
                            if (note.event is ChannelMessageEvent && baseChannel != null) {
                                val channelState by baseChannel.live.observeAsState()
                                val channel = channelState?.channel

                                if (channel != null) {
                                    Box(
                                        Modifier
                                            .width(30.dp)
                                            .height(30.dp)
                                            .align(Alignment.BottomEnd)) {
                                        AsyncImageProxy(
                                            model = ResizeImage(channel.profilePicture(), 30.dp),
                                            placeholder = BitmapPainter(RoboHashCache.get(context, channel.idHex)),
                                            fallback = BitmapPainter(RoboHashCache.get(context, channel.idHex)),
                                            error = BitmapPainter(RoboHashCache.get(context, channel.idHex)),
                                            contentDescription = "Group Picture",
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

                        if (note.event is RepostEvent) {
                            note.replyTo?.lastOrNull()?.let {
                                RelayBadges(it)
                            }
                        } else {
                            RelayBadges(baseNote)
                        }
                    }
                }

                Column(modifier = Modifier.padding(start = if (!isInnerNote) 10.dp else 0.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        NoteUsernameDisplay(note, Modifier.weight(1f))

                        if (note.event !is RepostEvent) {
                            Text(
                                timeAgo(note.event?.createdAt),
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
                                    tint = MaterialTheme.colors.onSurface.copy(alpha = 0.32f),
                                )

                                NoteDropDownMenu(baseNote, moreActionsExpanded, { moreActionsExpanded = false }, accountViewModel)
                            }
                        } else {
                            Text(
                                "  boosted",
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colors.onSurface.copy(alpha = 0.32f)
                            )
                        }
                    }

                    if (note.event is TextNoteEvent && (note.replyTo != null || note.mentions != null)) {
                        ReplyInformation(note.replyTo, note.mentions, navController)
                    } else if (note.event is ChannelMessageEvent && (note.replyTo != null || note.mentions != null)) {
                        note.channel?.let {
                            ReplyInformationChannel(note.replyTo, note.mentions, it, navController)
                        }
                    }

                    if (note.event is ReactionEvent || note.event is RepostEvent) {
                        note.replyTo?.lastOrNull()?.let {
                            NoteCompose(
                                it,
                                modifier = Modifier,
                                isInnerNote = true,
                                accountViewModel = accountViewModel,
                                navController = navController
                            )
                        }

                        // Reposts have trash in their contents.
                        if (note.event is ReactionEvent) {
                            val refactorReactionText =
                                if (note.event?.content == "+") "❤" else note.event?.content ?: " "

                            Text(
                                text = refactorReactionText
                            )
                        }
                    } else {
                        val eventContent = note.event?.content
                        val canPreview = note.author == account.userProfile()
                          || (note.author?.let { account.userProfile().isFollowing(it) } ?: true )
                          || !noteForReports.hasAnyReports()

                        if (eventContent != null) {
                            TranslateableRichTextViewer(
                                eventContent,
                                canPreview,
                                note.event?.tags,
                                accountViewModel,
                                navController
                            )
                        }

                        ReactionsRow(note, accountViewModel)

                        Divider(
                            modifier = Modifier.padding(top = 10.dp),
                            thickness = 0.25.dp
                        )
                    }

                    NoteDropDownMenu(note, popupExpanded, { popupExpanded = false }, accountViewModel)
                }
            }
        }
    }
}

@Composable
private fun RelayBadges(baseNote: Note) {
    val noteRelaysState by baseNote.liveRelays.observeAsState()
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
                    .padding(1.dp)) {
                AsyncImageProxy(
                    model = ResizeImage("https://${url}/favicon.ico", 15.dp),
                    placeholder = BitmapPainter(RoboHashCache.get(ctx, url)),
                    fallback = BitmapPainter(RoboHashCache.get(ctx, url)),
                    error = BitmapPainter(RoboHashCache.get(ctx, url)),
                    contentDescription = "Relay Icon",
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
                .height(25.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.Top) {
            IconButton(
                modifier = Modifier.then(Modifier.size(24.dp)),
                onClick = { expanded = true }
            ) {
                Icon(
                    imageVector = Icons.Default.ExpandMore,
                    null,
                    modifier = Modifier.size(15.dp),
                    tint = MaterialTheme.colors.onSurface.copy(alpha = 0.32f),
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
    val noteState by baseNote.live.observeAsState()
    val note = noteState?.note ?: return

    val author = note.author

    val ctx = LocalContext.current.applicationContext

    Box(
        Modifier
            .width(size)
            .height(size)) {
        if (author == null) {
            Image(
                painter = BitmapPainter(RoboHashCache.get(ctx, "ohnothisauthorisnotfound")),
                contentDescription = "Unknown Author",
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

@Composable
fun UserPicture(
    baseUser: User,
    baseUserAccount: User,
    size: Dp,
    pictureModifier: Modifier = Modifier,
    onClick: ((User) -> Unit)? = null
) {
    val userState by baseUser.liveMetadata.observeAsState()
    val user = userState?.user ?: return

    val ctx = LocalContext.current.applicationContext

    Box(
        Modifier
            .width(size)
            .height(size)) {

        AsyncImageProxy(
            model = ResizeImage(user.profilePicture(), size),
            contentDescription = "Profile Image",
            placeholder = BitmapPainter(RoboHashCache.get(ctx, user.pubkeyHex)),
            fallback = BitmapPainter(RoboHashCache.get(ctx, user.pubkeyHex)),
            error = BitmapPainter(RoboHashCache.get(ctx, user.pubkeyHex)),
            modifier = pictureModifier
                .fillMaxSize(1f)
                .clip(shape = CircleShape)
                .background(MaterialTheme.colors.background)
                .run {
                    if (onClick != null)
                        this.clickable(onClick = { onClick(user) } )
                    else
                        this
                }

        )

        val accountState by baseUserAccount.liveFollows.observeAsState()
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
                Text(
                    "x",
                    Modifier
                        .padding(4.dp)
                        .fillMaxSize()
                        .align(Alignment.Center)
                        .background(MaterialTheme.colors.background)
                        .clip(shape = CircleShape)
                )

                Icon(
                    painter = painterResource(R.drawable.ic_verified),
                    "Following",
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
    val context = LocalContext.current.applicationContext

    DropdownMenu(
        expanded = popupExpanded,
        onDismissRequest = onDismiss
    ) {
        DropdownMenuItem(onClick = { clipboardManager.setText(AnnotatedString(accountViewModel.decrypt(note) ?: "")); onDismiss() }) {
            Text("Copy Text")
        }
        DropdownMenuItem(onClick = { clipboardManager.setText(AnnotatedString(note.author?.pubkey?.toNpub() ?: "")); onDismiss() }) {
            Text("Copy User PubKey")
        }
        DropdownMenuItem(onClick = { clipboardManager.setText(AnnotatedString(note.id.toNote())); onDismiss() }) {
            Text("Copy Note ID")
        }
        Divider()
        DropdownMenuItem(onClick = { accountViewModel.broadcast(note); onDismiss() }) {
            Text("Broadcast")
        }
        Divider()
        DropdownMenuItem(onClick = { note.author?.let { accountViewModel.hide(it, context) }; onDismiss() }) {
            Text("Hide User")
        }
        Divider()
        DropdownMenuItem(onClick = {
            accountViewModel.report(note, ReportEvent.ReportType.SPAM);
            note.author?.let { accountViewModel.hide(it, context) }
            onDismiss()
        }) {
            Text("Report Spam / Scam")
        }
        DropdownMenuItem(onClick = {
            accountViewModel.report(note, ReportEvent.ReportType.IMPERSONATION);
            note.author?.let { accountViewModel.hide(it, context) }
            onDismiss()
        }) {
            Text("Report Impersonation")
        }
        DropdownMenuItem(onClick = {
            accountViewModel.report(note, ReportEvent.ReportType.EXPLICIT);
            note.author?.let { accountViewModel.hide(it, context) }
            onDismiss()
        }) {
            Text("Report Explicit Content")
        }
        DropdownMenuItem(onClick = {
            accountViewModel.report(note, ReportEvent.ReportType.ILLEGAL);
            note.author?.let { accountViewModel.hide(it, context) }
            onDismiss()
        }) {
            Text("Report Illegal Behaviour")
        }
    }
}