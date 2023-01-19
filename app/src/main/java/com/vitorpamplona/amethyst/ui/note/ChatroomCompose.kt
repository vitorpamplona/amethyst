package com.vitorpamplona.amethyst.ui.note

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.service.model.ChannelCreateEvent
import com.vitorpamplona.amethyst.service.model.ChannelMetadataEvent
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel

@Composable
fun ChatroomCompose(baseNote: Note, accountViewModel: AccountViewModel, navController: NavController) {
    val noteState by baseNote.live.observeAsState()
    val note = noteState?.note

    val accountUserState by accountViewModel.userLiveData.observeAsState()
    val accountUser = accountUserState?.user

    if (note?.event == null) {
        BlankNote(Modifier)
    } else if (note.channel != null) {
        val authorState by note.author!!.live.observeAsState()
        val author = authorState?.user

        val channelState by note.channel!!.live.observeAsState()
        val channel = channelState?.channel

        val description = if (note.event is ChannelCreateEvent) {
            "Channel created"
        } else if (note.event is ChannelMetadataEvent) {
            "Channel Information changed to "
        } else {
            note.event?.content
        }
        channel?.let {
            ChannelName(
                channelPicture = it.profilePicture(),
                channelTitle = {
                    Text(
                        "${it.info.name}",
                        fontWeight = FontWeight.Bold
                    )
                },
                channelLastTime = note.event?.createdAt,
                channelLastContent = "${author?.toBestDisplayName()}: " + description,
                onClick = { navController.navigate("Channel/${it.idHex}") })
        }

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

        userToComposeOn?.let {
            ChannelName(
                channelPicture = it.profilePicture(),
                channelTitle = { UsernameDisplay(it) },
                channelLastTime = note.event?.createdAt,
                channelLastContent = accountViewModel.decrypt(note),
                onClick = { navController.navigate("Room/${it.pubkeyHex}") })
        }
    }

}

@Composable
fun ChannelName(
    channelPicture: String,
    channelTitle: @Composable () -> Unit,
    channelLastTime: Long?,
    channelLastContent: String?,
    onClick: () -> Unit
) {
    Column(modifier = Modifier.clickable(onClick = onClick) ) {
        Row(
            modifier = Modifier.padding(start = 12.dp, end = 12.dp, top = 10.dp)
        ) {

            AsyncImage(
                model = channelPicture,
                contentDescription = "Profile Image",
                modifier = Modifier
                    .width(55.dp)
                    .height(55.dp)
                    .clip(shape = CircleShape)
            )

            Column(modifier = Modifier.padding(start = 10.dp),
            verticalArrangement = Arrangement.SpaceAround) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(bottom = 4.dp)
                ) {
                    channelTitle()

                    channelLastTime?.let {
                        Text(
                            timeAgo(channelLastTime),
                            color = MaterialTheme.colors.onSurface.copy(alpha = 0.52f)
                        )
                    }

                }

                if (channelLastContent != null)
                    Text(
                        channelLastContent,
                        color = MaterialTheme.colors.onSurface.copy(alpha = 0.52f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                else
                    Text(
                        "Referenced event not found",
                        color = MaterialTheme.colors.onSurface.copy(alpha = 0.52f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
            }
        }

        Divider(
            modifier = Modifier.padding(top = 10.dp),
            thickness = 0.25.dp
        )
    }
}