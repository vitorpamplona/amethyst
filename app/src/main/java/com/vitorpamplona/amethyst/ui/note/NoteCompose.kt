package com.vitorpamplona.amethyst.ui.note

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.Divider
import androidx.compose.material.DropdownMenu
import androidx.compose.material.DropdownMenuItem
import androidx.compose.material.Icon
import androidx.compose.material.LocalContentColor
import androidx.compose.material.LocalTextStyle
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
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
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import coil.compose.AsyncImage
import coil.compose.rememberAsyncImagePainter
import com.vitorpamplona.amethyst.NotificationCache
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.model.Account
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.model.User
import com.vitorpamplona.amethyst.model.toNote
import com.vitorpamplona.amethyst.service.model.ChannelMessageEvent
import com.vitorpamplona.amethyst.service.model.ReactionEvent
import com.vitorpamplona.amethyst.service.model.ReportEvent
import com.vitorpamplona.amethyst.service.model.RepostEvent
import com.vitorpamplona.amethyst.ui.components.RichTextViewer
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

    val context = LocalContext.current.applicationContext

    if (note?.event == null) {
        BlankNote(modifier.combinedClickable(
            onClick = {  },
            onLongClick = { popupExpanded = true },
        ), isInnerNote)
    } else if (!account.isAcceptable(noteForReports)) {
        HiddenNote(modifier.combinedClickable(
            onClick = {  },
            onLongClick = { popupExpanded = true },
        ), isInnerNote)
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

                // Draws the boosted picture outside the boosted card.
                if (!isInnerNote) {
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
                                    AsyncImage(
                                        model = channel.profilePicture(),
                                        placeholder = null,
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
                        if (eventContent != null) {
                            if (note.reports.size > 0) {
                                // Doesn't load images
                                Row() {
                                    Text(
                                        text = eventContent,
                                        style = LocalTextStyle.current.copy(textDirection = TextDirection.Content),
                                    )
                                }
                            } else {
                                RichTextViewer(eventContent, note.event?.tags, navController)
                            }
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

    Box(
        Modifier
            .width(size)
            .height(size)) {
        if (author == null) {
            AsyncImage(
                model = "https://robohash.org/ohno.png",
                contentDescription = "Unknown Author",
                placeholder = rememberAsyncImagePainter("https://robohash.org/ohno.png"),
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

    Box(
        Modifier
            .width(size)
            .height(size)) {

        AsyncImage(
            model = user.profilePicture(),
            contentDescription = "Profile Image",
            placeholder = rememberAsyncImagePainter("https://robohash.org/${user.pubkeyHex}.png"),
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