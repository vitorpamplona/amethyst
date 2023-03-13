package com.vitorpamplona.amethyst.ui.note

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.Divider
import androidx.compose.material.LocalTextStyle
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.vitorpamplona.amethyst.NotificationCache
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.service.model.ChannelCreateEvent
import com.vitorpamplona.amethyst.service.model.ChannelMetadataEvent
import com.vitorpamplona.amethyst.ui.components.ResizeImage
import com.vitorpamplona.amethyst.ui.components.RobohashAsyncImageProxy
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun ChatroomCompose(
    baseNote: Note,
    accountViewModel: AccountViewModel,
    navController: NavController
) {
    val noteState by baseNote.live().metadata.observeAsState()
    val note = noteState?.note

    val notificationCacheState = NotificationCache.live.observeAsState()
    val notificationCache = notificationCacheState.value ?: return

    if (note?.event == null) {
        BlankNote(Modifier)
    } else if (note.channel() != null) {
        val authorState by note.author!!.live().metadata.observeAsState()
        val author = authorState?.user

        val channelState by note.channel()!!.live.observeAsState()
        val channel = channelState?.channel

        val noteEvent = note.event

        val description = if (noteEvent is ChannelCreateEvent) {
            stringResource(R.string.channel_created)
        } else if (noteEvent is ChannelMetadataEvent) {
            "${stringResource(R.string.channel_information_changed_to)} "
        } else {
            noteEvent?.content()
        }
        channel?.let { chan ->
            var hasNewMessages by remember { mutableStateOf<Boolean>(false) }

            LaunchedEffect(key1 = notificationCache, key2 = note) {
                withContext(Dispatchers.IO) {
                    note.createdAt()?.let { timestamp ->
                        hasNewMessages =
                            timestamp > notificationCache.cache.load("Channel/${chan.idHex}")
                    }
                }
            }

            ChannelName(
                channelIdHex = chan.idHex,
                channelPicture = chan.profilePicture(),
                channelTitle = { modifier ->
                    Text(
                        text = buildAnnotatedString {
                            withStyle(
                                SpanStyle(
                                    fontWeight = FontWeight.Bold
                                )
                            ) {
                                append(chan.info.name)
                            }

                            withStyle(
                                SpanStyle(
                                    color = MaterialTheme.colors.onSurface.copy(alpha = 0.32f),
                                    fontWeight = FontWeight.Normal
                                )
                            ) {
                                append(" ${stringResource(id = R.string.public_chat)}")
                            }
                        },
                        fontWeight = FontWeight.Bold,
                        modifier = modifier,
                        style = LocalTextStyle.current.copy(textDirection = TextDirection.Content)
                    )
                },
                channelLastTime = note.createdAt(),
                channelLastContent = "${author?.toBestDisplayName()}: " + description,
                hasNewMessages = hasNewMessages,
                onClick = { navController.navigate("Channel/${chan.idHex}") }
            )
        }
    } else {
        val replyAuthorBase = note.mentions?.first()

        var userToComposeOn = note.author!!

        if (replyAuthorBase != null) {
            if (note.author == accountViewModel.userProfile()) {
                userToComposeOn = replyAuthorBase
            }
        }

        val noteEvent = note.event

        userToComposeOn.let { user ->
            var hasNewMessages by remember { mutableStateOf<Boolean>(false) }

            LaunchedEffect(key1 = notificationCache, key2 = note) {
                withContext(Dispatchers.IO) {
                    noteEvent?.let {
                        hasNewMessages = it.createdAt() > notificationCache.cache.load(
                            "Room/${userToComposeOn.pubkeyHex}"
                        )
                    }
                }
            }

            ChannelName(
                channelPicture = {
                    UserPicture(
                        userToComposeOn,
                        accountViewModel.userProfile(),
                        size = 55.dp
                    )
                },
                channelTitle = { UsernameDisplay(userToComposeOn, it) },
                channelLastTime = note.createdAt(),
                channelLastContent = accountViewModel.decrypt(note),
                hasNewMessages = hasNewMessages,
                onClick = { navController.navigate("Room/${user.pubkeyHex}") }
            )
        }
    }
}

@Composable
fun ChannelName(
    channelIdHex: String,
    channelPicture: String?,
    channelTitle: @Composable (Modifier) -> Unit,
    channelLastTime: Long?,
    channelLastContent: String?,
    hasNewMessages: Boolean,
    onClick: () -> Unit
) {
    ChannelName(
        channelPicture = {
            RobohashAsyncImageProxy(
                robot = channelIdHex,
                model = ResizeImage(channelPicture, 55.dp),
                contentDescription = stringResource(R.string.channel_image),
                modifier = Modifier
                    .width(55.dp)
                    .height(55.dp)
                    .clip(shape = CircleShape)
            )
        },
        channelTitle,
        channelLastTime,
        channelLastContent,
        hasNewMessages,
        onClick
    )
}

@Composable
fun ChannelName(
    channelPicture: @Composable () -> Unit,
    channelTitle: @Composable (Modifier) -> Unit,
    channelLastTime: Long?,
    channelLastContent: String?,
    hasNewMessages: Boolean,
    onClick: () -> Unit
) {
    val context = LocalContext.current
    Column(modifier = Modifier.clickable(onClick = onClick)) {
        Row(
            modifier = Modifier.padding(start = 12.dp, end = 12.dp, top = 10.dp)
        ) {
            channelPicture()

            Column(
                modifier = Modifier.padding(start = 10.dp),
                verticalArrangement = Arrangement.SpaceAround
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(bottom = 4.dp)
                ) {
                    channelTitle(Modifier.weight(1f))

                    channelLastTime?.let {
                        Text(
                            timeAgo(channelLastTime, context),
                            color = MaterialTheme.colors.onSurface.copy(alpha = 0.52f)
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (channelLastContent != null) {
                        Text(
                            channelLastContent,
                            color = MaterialTheme.colors.onSurface.copy(alpha = 0.52f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = LocalTextStyle.current.copy(textDirection = TextDirection.Content),
                            modifier = Modifier.weight(1f)
                        )
                    } else {
                        Text(
                            stringResource(R.string.referenced_event_not_found),
                            color = MaterialTheme.colors.onSurface.copy(alpha = 0.52f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    if (hasNewMessages) {
                        NewItemsBubble()
                    }
                }
            }
        }

        Divider(
            modifier = Modifier.padding(top = 10.dp),
            thickness = 0.25.dp
        )
    }
}

@Composable
fun NewItemsBubble() {
    Box(
        modifier = Modifier
            .padding(start = 3.dp)
            .width(10.dp)
            .height(10.dp)
            .clip(shape = CircleShape)
            .background(MaterialTheme.colors.primary),
        contentAlignment = Alignment.Center
    ) {
        Text(
            "",
            color = Color.White,
            textAlign = TextAlign.Center,
            fontSize = 12.sp,
            modifier = Modifier
                .wrapContentHeight()
                .align(Alignment.Center)
        )
    }
}
