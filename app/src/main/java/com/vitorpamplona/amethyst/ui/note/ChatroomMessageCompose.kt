package com.vitorpamplona.amethyst.ui.note

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.service.model.ChannelMessageEvent
import com.vitorpamplona.amethyst.ui.components.RichTextViewer
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel

val ChatBubbleShapeMe = RoundedCornerShape(15.dp, 15.dp, 3.dp, 15.dp)
val ChatBubbleShapeThem = RoundedCornerShape(3.dp, 15.dp, 15.dp, 15.dp)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ChatroomMessageCompose(baseNote: Note, accountViewModel: AccountViewModel, navController: NavController) {
    val noteState by baseNote.live.observeAsState()
    val note = noteState?.note

    val accountUserState by accountViewModel.userLiveData.observeAsState()
    val accountUser = accountUserState?.user

    var popupExpanded by remember { mutableStateOf(false) }

    if (note?.event == null) {
        BlankNote(Modifier)
    } else {
        val authorState by note.author!!.live.observeAsState()
        val author = authorState?.user

        var backgroundBubbleColor: Color
        var alignment: Arrangement.Horizontal
        var shape: Shape

        if (author == accountUser) {
            backgroundBubbleColor = MaterialTheme.colors.primary.copy(alpha = 0.32f)
            alignment = Arrangement.End
            shape = ChatBubbleShapeMe
        } else {
            backgroundBubbleColor = MaterialTheme.colors.onSurface.copy(alpha = 0.12f)
            alignment = Arrangement.Start
            shape = ChatBubbleShapeThem
        }

        Column() {
            Row(
                modifier = Modifier.fillMaxWidth(1f).padding(
                    start = 12.dp,
                    end = 12.dp,
                    top = 5.dp,
                    bottom = 5.dp
                ),
                horizontalArrangement = alignment
            ) {
                Row(
                    horizontalArrangement = alignment,
                    modifier = Modifier.fillMaxWidth(0.85f)
                ) {

                    Surface(
                        color = backgroundBubbleColor,
                        shape = shape,
                        modifier = Modifier
                            .combinedClickable(
                                onClick = { },
                                onLongClick = { popupExpanded = true }
                            )
                    ) {
                        Column(
                            modifier = Modifier.padding(start = 10.dp, end = 10.dp, bottom = 5.dp),
                        ) {

                            if (author != accountUser && note.event is ChannelMessageEvent) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = alignment,
                                    modifier = Modifier.padding(top = 5.dp)
                                ) {
                                    AsyncImage(
                                        model = author?.profilePicture(),
                                        contentDescription = "Profile Image",
                                        modifier = Modifier
                                            .width(25.dp)
                                            .height(25.dp)
                                            .clip(shape = CircleShape)
                                            .clickable(onClick = {
                                                author?.let {
                                                    navController.navigate("User/${it.pubkeyHex}")
                                                }
                                            })
                                    )

                                    Text(
                                        "  ${author?.toBestDisplayName()}",
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.clickable(onClick = {
                                                author?.let {
                                                    navController.navigate("User/${it.pubkeyHex}")
                                                }
                                            })
                                    )
                                }
                            }

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                val eventContent = accountViewModel.decrypt(note)

                                if (eventContent != null)
                                    RichTextViewer(
                                        eventContent,
                                        note.event?.tags,
                                        note,
                                        accountViewModel,
                                        navController
                                    )
                                else
                                    RichTextViewer(
                                        "Could Not decrypt the message",
                                        note.event?.tags,
                                        note,
                                        accountViewModel,
                                        navController
                                    )
                            }

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.End,
                                modifier = Modifier.padding(top = 2.dp)
                            ) {
                                Text(
                                    timeAgoLong(note.event?.createdAt),
                                    color = MaterialTheme.colors.onSurface.copy(alpha = 0.32f),
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }

                }

                NoteDropDownMenu(note, popupExpanded, { popupExpanded = false }, accountViewModel)
            }
        }
    }
}

