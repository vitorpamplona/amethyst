package com.vitorpamplona.amethyst.ui.note

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.Divider
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.ui.components.RichTextViewer
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel

@Composable
fun ChatroomCompose(baseNote: Note, accountViewModel: AccountViewModel, navController: NavController) {
    val noteState by baseNote.live.observeAsState()
    val note = noteState?.note

    val accountUserState by accountViewModel.userLiveData.observeAsState()
    val accountUser = accountUserState?.user

    if (note?.event == null) {
        BlankNote(Modifier)
    } else {
        val authorState by note.author!!.live.observeAsState()
        val author = authorState?.user

        val replyAuthorBase = note.mentions?.first()

        var userToComposeOn = author

        if ( replyAuthorBase != null ) {
            val replyAuthorState by replyAuthorBase.live.observeAsState()
            val replyAuthor = replyAuthorState?.user

            if (author == accountUser) {
                userToComposeOn = replyAuthor
            }
        }

        Column(modifier =
            Modifier.clickable(
                onClick = { navController.navigate("Room/${userToComposeOn?.pubkeyHex}") }
            )
        ) {
            Row(
                modifier = Modifier
                    .padding(
                        start = 12.dp,
                        end = 12.dp,
                        top = 10.dp)
            ) {

                AsyncImage(
                    model = userToComposeOn?.profilePicture(),
                    contentDescription = "Profile Image",
                    modifier = Modifier
                        .width(55.dp)
                        .clip(shape = CircleShape)
                )

                Column(modifier = Modifier.padding(start = 10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (userToComposeOn != null)
                            UserDisplay(userToComposeOn)

                        Text(
                            timeAgo(note.event?.createdAt),
                            color = MaterialTheme.colors.onSurface.copy(alpha = 0.32f)
                        )
                    }

                    val eventContent = accountViewModel.decrypt(note)
                    if (eventContent != null)
                        RichTextViewer(eventContent.take(100), note.event?.tags, note, accountViewModel, navController)
                    else
                        RichTextViewer("Referenced event not found", note.event?.tags, note, accountViewModel, navController)
                }
            }

            Divider(
                modifier = Modifier.padding(top = 10.dp),
                thickness = 0.25.dp
            )
        }
    }
}