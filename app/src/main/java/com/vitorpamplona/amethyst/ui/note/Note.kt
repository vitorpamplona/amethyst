package com.vitorpamplona.amethyst.ui.note

import android.text.format.DateUtils.getRelativeTimeSpanString
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.Divider
import androidx.compose.material.DropdownMenu
import androidx.compose.material.DropdownMenuItem
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.navigation.compose.rememberNavController
import coil.compose.AsyncImage
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.model.toNote
import com.vitorpamplona.amethyst.service.model.ReactionEvent
import com.vitorpamplona.amethyst.service.model.RepostEvent
import com.vitorpamplona.amethyst.ui.components.RichTextViewer
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import nostr.postr.events.TextNoteEvent
import nostr.postr.toNpub

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun NoteCompose(baseNote: Note, modifier: Modifier = Modifier, isInnerNote: Boolean = false, accountViewModel: AccountViewModel, navController: NavController) {
    val noteState by baseNote.live.observeAsState()
    val note = noteState?.note

    var popupExpanded by remember { mutableStateOf(false) }

    if (note?.event == null) {
        BlankNote(modifier, isInnerNote)
    } else {
        val authorState by note.author!!.live.observeAsState()
        val author = authorState?.user

        Column(modifier =
            modifier.combinedClickable(
                onClick = { navController.navigate("Note/${note.idHex}") },
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
                    Box(modifier = Modifier.width(55.dp).padding(0.dp)) {
                        AsyncImage(
                            model = author?.profilePicture(),
                            contentDescription = "Profile Image",
                            modifier = Modifier
                                .width(55.dp)
                                .clip(shape = CircleShape)
                        )

                        // boosted picture
                        val boostedPosts = note.replyTo
                        if (note.event is RepostEvent && boostedPosts != null && boostedPosts.isNotEmpty()) {
                            AsyncImage(
                                model = boostedPosts[0].author?.profilePicture(),
                                contentDescription = "Profile Image",
                                modifier = Modifier
                                    .width(35.dp)
                                    .clip(shape = CircleShape)
                                    .align(Alignment.BottomEnd)
                                    .background(MaterialTheme.colors.background)
                                    .border(2.dp, MaterialTheme.colors.primary, CircleShape)
                            )
                        }
                    }
                }

                Column(modifier = Modifier.padding(start = if (!isInnerNote) 10.dp else 0.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (author != null)
                            UserDisplay(author)

                        if (note.event !is RepostEvent) {
                            Text(
                                timeAgo(note.event?.createdAt),
                                color = MaterialTheme.colors.onSurface.copy(alpha = 0.32f)
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
                        ReplyInformation(note.replyTo, note.mentions)
                    }

                    if (note.event is ReactionEvent || note.event is RepostEvent) {
                        note.replyTo?.mapIndexed { index, note ->
                            NoteCompose(
                                note,
                                modifier = Modifier.padding(top = 5.dp),
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
                            RichTextViewer(eventContent, note.event?.tags)

                        ReactionsRowState(note, accountViewModel)

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
fun NoteDropDownMenu(note: Note, popupExpanded: Boolean, onDismiss: () -> Unit, accountViewModel: AccountViewModel) {
    val clipboardManager = LocalClipboardManager.current

    DropdownMenu(
        expanded = popupExpanded,
        onDismissRequest = onDismiss
    ) {
        DropdownMenuItem(onClick = { clipboardManager.setText(AnnotatedString(note.event?.content ?: "")); onDismiss() }) {
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
    }
}