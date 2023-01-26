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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import coil.compose.AsyncImage
import coil.compose.rememberAsyncImagePainter
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.model.Account
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.model.User
import com.vitorpamplona.amethyst.model.toNote
import com.vitorpamplona.amethyst.service.model.ChannelMessageEvent
import com.vitorpamplona.amethyst.service.model.ReactionEvent
import com.vitorpamplona.amethyst.service.model.RepostEvent
import com.vitorpamplona.amethyst.ui.components.RichTextViewer
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.theme.Following
import nostr.postr.events.TextNoteEvent
import nostr.postr.toNpub

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun NoteCompose(baseNote: Note, modifier: Modifier = Modifier, isInnerNote: Boolean = false, accountViewModel: AccountViewModel, navController: NavController) {
    val accountState by accountViewModel.accountLiveData.observeAsState()
    val account = accountState?.account ?: return

    val noteState by baseNote.live.observeAsState()
    val note = noteState?.note

    var popupExpanded by remember { mutableStateOf(false) }

    if (note?.event == null) {
        BlankNote(modifier.combinedClickable(
            onClick = {  },
            onLongClick = { popupExpanded = true },
        ), isInnerNote)
    } else if (account?.isAcceptable(note) == false) {
        HiddenNote(modifier.combinedClickable(
            onClick = {  },
            onLongClick = { popupExpanded = true },
        ), isInnerNote)
    } else {
        val authorState by note.author?.live!!.observeAsState()
        val author = authorState?.user ?: return // if it has event, it should have an author

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
                onLongClick = { popupExpanded = true },
            )
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
                        UserPicture(author, navController, account.userProfile(), 55.dp)

                        // boosted picture
                        val boostedPosts = note.replyTo
                        if (note.event is RepostEvent && boostedPosts != null && boostedPosts.isNotEmpty()) {
                            val boostedAuthor = boostedPosts[0].author
                            Box(
                                Modifier
                                    .width(30.dp)
                                    .height(30.dp)
                                    .align(Alignment.BottomEnd)) {
                                UserPicture(boostedAuthor, navController, account.userProfile(), 35.dp,
                                    pictureModifier = Modifier.border(2.dp, MaterialTheme.colors.background, CircleShape)
                                )
                            }
                        }
                    }
                }

                Column(modifier = Modifier.padding(start = if (!isInnerNote) 10.dp else 0.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (author != null)
                            UsernameDisplay(author, Modifier.weight(1f))

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
                        note.replyTo?.mapIndexed { index, note ->
                            NoteCompose(
                                note,
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
                        if (eventContent != null)
                            RichTextViewer(eventContent, note.event?.tags, navController)

                        if (note.event !is ChannelMessageEvent) {
                            ReactionsRow(note, accountViewModel)
                        }

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
fun UserPicture(
    user: User?,
    navController: NavController,
    userAccount: User,
    size: Dp,
    pictureModifier: Modifier = Modifier
) {
    UserPicture(user, userAccount, size, pictureModifier) {
        user?.let {
            navController.navigate("User/${it.pubkeyHex}")
        }
    }
}

@Composable
fun UserPicture(
    user: User?,
    userAccount: User,
    size: Dp,
    pictureModifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null
) {
    Box(
        Modifier
            .width(size)
            .height(size)) {
        if (user == null) {
            AsyncImage(
                model = "https://robohash.org/ohno.png",
                contentDescription = "Profile Image",
                placeholder = rememberAsyncImagePainter("https://robohash.org/ohno.png"),
                modifier = pictureModifier
                    .fillMaxSize(1f)
                    .clip(shape = CircleShape)
                    .background(MaterialTheme.colors.background)
            )
        } else {
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
                            this.clickable(onClick = onClick)
                        else
                            this
                    }

            )

            if (userAccount.isFollowing(user) || user == userAccount) {
                Box(Modifier
                    .width(size.div(3.5f))
                    .height(size.div(3.5f))
                    .align(Alignment.TopEnd),
                    contentAlignment = Alignment.Center
                ) {
                    // Background for the transparent checkmark
                    Text(
                        "x",
                          Modifier
                        .padding(4.dp).fillMaxSize()
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
            Text("Copy User ID")
        }
        DropdownMenuItem(onClick = { clipboardManager.setText(AnnotatedString(note.id.toNote())); onDismiss() }) {
            Text("Copy Note ID")
        }
        Divider()
        DropdownMenuItem(onClick = { accountViewModel.broadcast(note); onDismiss() }) {
            Text("Broadcast")
        }
        Divider()
        DropdownMenuItem(onClick = { accountViewModel.report(note); onDismiss() }) {
            Text("Report Post")
        }
        DropdownMenuItem(onClick = { note.author?.let { accountViewModel.hide(it, context) }; onDismiss() }) {
            Text("Hide User")
        }
    }
}