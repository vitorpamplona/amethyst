/**
 * Copyright (c) 2024 Vitor Pamplona
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of
 * this software and associated documentation files (the "Software"), to deal in
 * the Software without restriction, including without limitation the rights to use,
 * copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the
 * Software, and to permit persons to whom the Software is furnished to do so,
 * subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
 * FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
 * COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN
 * AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION
 * WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package com.vitorpamplona.amethyst.ui.screen.loggedIn.chatrooms

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
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
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.distinctUntilChanged
import androidx.lifecycle.map
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.model.Channel
import com.vitorpamplona.amethyst.model.FeatureSetType
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.model.User
import com.vitorpamplona.amethyst.ui.actions.CrossfadeIfEnabled
import com.vitorpamplona.amethyst.ui.components.CreateTextWithEmoji
import com.vitorpamplona.amethyst.ui.components.RobohashFallbackAsyncImage
import com.vitorpamplona.amethyst.ui.layouts.ChatHeaderLayout
import com.vitorpamplona.amethyst.ui.navigation.INav
import com.vitorpamplona.amethyst.ui.note.BlankNote
import com.vitorpamplona.amethyst.ui.note.LoadChannel
import com.vitorpamplona.amethyst.ui.note.LoadDecryptedContentOrNull
import com.vitorpamplona.amethyst.ui.note.NonClickableUserPictures
import com.vitorpamplona.amethyst.ui.note.ObserveDraftEvent
import com.vitorpamplona.amethyst.ui.note.UsernameDisplay
import com.vitorpamplona.amethyst.ui.note.timeAgo
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.stringRes
import com.vitorpamplona.amethyst.ui.theme.AccountPictureModifier
import com.vitorpamplona.amethyst.ui.theme.Size55dp
import com.vitorpamplona.amethyst.ui.theme.grayText
import com.vitorpamplona.amethyst.ui.theme.placeholderText
import com.vitorpamplona.quartz.nip01Core.HexKey
import com.vitorpamplona.quartz.nip17Dm.base.ChatroomKey
import com.vitorpamplona.quartz.nip17Dm.base.ChatroomKeyable
import com.vitorpamplona.quartz.nip28PublicChat.admin.ChannelCreateEvent
import com.vitorpamplona.quartz.nip28PublicChat.admin.ChannelMetadataEvent
import com.vitorpamplona.quartz.nip37Drafts.DraftEvent
import kotlin.math.min

@Composable
fun ChatroomHeaderCompose(
    baseNote: Note,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    if (baseNote.event != null) {
        ChatroomComposeChannelOrUser(baseNote, accountViewModel, nav)
    } else {
        val hasEvent by baseNote.live().hasEvent.observeAsState(baseNote.event != null)
        if (hasEvent) {
            ChatroomComposeChannelOrUser(baseNote, accountViewModel, nav)
        } else {
            BlankNote()
        }
    }
}

@Composable
fun ChatroomComposeChannelOrUser(
    baseNote: Note,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    if (baseNote.event is DraftEvent) {
        ObserveDraftEvent(baseNote, accountViewModel) {
            val channelHex by remember(it) { derivedStateOf { it.channelHex() } }

            if (channelHex != null) {
                ChatroomChannel(channelHex!!, it, accountViewModel, nav)
            } else {
                ChatroomPrivateMessages(it, accountViewModel, nav)
            }
        }
    } else {
        val channelHex by remember(baseNote) { derivedStateOf { baseNote.channelHex() } }

        if (channelHex != null) {
            ChatroomChannel(channelHex!!, baseNote, accountViewModel, nav)
        } else {
            ChatroomPrivateMessages(baseNote, accountViewModel, nav)
        }
    }
}

@Composable
private fun ChatroomPrivateMessages(
    baseNote: Note,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val userRoom by
        remember(baseNote) {
            derivedStateOf {
                (baseNote.event as? ChatroomKeyable)?.chatroomKey(accountViewModel.userProfile().pubkeyHex)
            }
        }

    CrossfadeIfEnabled(targetState = userRoom, label = "ChatroomPrivateMessages", accountViewModel = accountViewModel) { room ->
        if (room != null) {
            UserRoomCompose(baseNote, room, accountViewModel, nav)
        } else {
            BlankNote()
        }
    }
}

@Composable
private fun ChatroomChannel(
    channelHex: HexKey,
    baseNote: Note,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    LoadChannel(baseChannelHex = channelHex, accountViewModel) { channel ->
        ChannelRoomCompose(baseNote, channel, accountViewModel, nav)
    }
}

@Composable
private fun ChannelRoomCompose(
    note: Note,
    channel: Channel,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val authorState by note.author!!
        .live()
        .metadata
        .observeAsState()
    val authorName = remember(note, authorState) { authorState?.user?.toBestDisplayName() }

    val channelState by channel.live.observeAsState()

    val channelPicture = channelState?.channel?.profilePicture() ?: channel.profilePicture()
    val channelName = channelState?.channel?.toBestDisplayName() ?: channel.toBestDisplayName()

    val noteEvent = note.event

    val route = "Channel/${channel.idHex}"

    val description =
        if (noteEvent is ChannelCreateEvent) {
            stringRes(R.string.channel_created)
        } else if (noteEvent is ChannelMetadataEvent) {
            "${stringRes(R.string.channel_information_changed_to)} "
        } else {
            noteEvent?.content?.take(200)
        }

    val lastReadTime by accountViewModel.account.loadLastReadFlow(route).collectAsStateWithLifecycle()

    ChannelName(
        channelIdHex = channel.idHex,
        channelPicture = channelPicture,
        channelTitle = { modifier -> ChannelTitleWithLabelInfo(channelName, modifier) },
        channelLastTime = note.createdAt(),
        channelLastContent = "$authorName: $description",
        hasNewMessages = (noteEvent?.createdAt ?: Long.MIN_VALUE) > lastReadTime,
        loadProfilePicture = accountViewModel.settings.showProfilePictures.value,
        loadRobohash = accountViewModel.settings.featureSet != FeatureSetType.PERFORMANCE,
        onClick = { nav.nav(route) },
    )
}

@Composable
private fun ChannelTitleWithLabelInfo(
    channelName: String,
    modifier: Modifier,
) {
    val label = stringRes(id = R.string.public_chat)
    val placeHolderColor = MaterialTheme.colorScheme.placeholderText
    val channelNameAndBoostInfo =
        remember(channelName) {
            buildAnnotatedString {
                withStyle(
                    SpanStyle(
                        fontWeight = FontWeight.Bold,
                    ),
                ) {
                    append(channelName)
                }

                withStyle(
                    SpanStyle(
                        color = placeHolderColor,
                        fontWeight = FontWeight.Normal,
                    ),
                ) {
                    append(" $label")
                }
            }
        }

    Text(
        text = channelNameAndBoostInfo,
        fontWeight = FontWeight.Bold,
        modifier = modifier,
        style = LocalTextStyle.current.copy(textDirection = TextDirection.Content),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
private fun UserRoomCompose(
    note: Note,
    room: ChatroomKey,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val route = "Room/${room.hashCode()}"

    ChatHeaderLayout(
        channelPicture = {
            NonClickableUserPictures(
                room = room,
                accountViewModel = accountViewModel,
                size = Size55dp,
            )
        },
        firstRow = {
            RoomNameDisplay(room, Modifier.weight(1f), accountViewModel)
            TimeAgo(note.createdAt())
        },
        secondRow = {
            LoadDecryptedContentOrNull(note, accountViewModel) { content ->
                if (content != null) {
                    Text(
                        content,
                        color = MaterialTheme.colorScheme.grayText,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = LocalTextStyle.current.copy(textDirection = TextDirection.Content),
                        modifier = Modifier.weight(1f),
                    )
                } else {
                    Text(
                        stringRes(R.string.referenced_event_not_found),
                        color = MaterialTheme.colorScheme.grayText,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            val lastReadTime by accountViewModel.account.loadLastReadFlow(route).collectAsStateWithLifecycle()
            if ((note.createdAt() ?: Long.MIN_VALUE) > lastReadTime) {
                NewItemsBubble()
            }
        },
        onClick = { nav.nav(route) },
    )
}

@Composable
fun RoomNameDisplay(
    room: ChatroomKey,
    modifier: Modifier,
    accountViewModel: AccountViewModel,
) {
    val roomSubject by
        accountViewModel
            .userProfile()
            .live()
            .messages
            .map { it.user.privateChatrooms[room]?.subject }
            .distinctUntilChanged()
            .observeAsState(accountViewModel.userProfile().privateChatrooms[room]?.subject)

    CrossfadeIfEnabled(targetState = roomSubject, modifier, label = "RoomNameDisplay", accountViewModel = accountViewModel) {
        if (!it.isNullOrBlank()) {
            if (room.users.size > 1) {
                DisplayRoomSubject(it)
            } else {
                DisplayUserAndSubject(room.users.first(), it, accountViewModel)
            }
        } else {
            DisplayUserSetAsSubject(room, accountViewModel)
        }
    }
}

@Composable
private fun DisplayUserAndSubject(
    user: HexKey,
    subject: String,
    accountViewModel: AccountViewModel,
) {
    Row {
        Text(
            text = subject,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = " - ",
            fontWeight = FontWeight.Bold,
            maxLines = 1,
        )
        LoadUser(baseUserHex = user, accountViewModel = accountViewModel) {
            it?.let { UsernameDisplay(it, Modifier.weight(1f), accountViewModel = accountViewModel) }
        }
    }
}

@Composable
fun DisplayUserSetAsSubject(
    room: ChatroomKey,
    accountViewModel: AccountViewModel,
    fontWeight: FontWeight = FontWeight.Bold,
) {
    val userList = remember(room) { room.users.toList() }

    if (userList.size == 1) {
        // Regular Design
        Row {
            LoadUser(baseUserHex = userList[0], accountViewModel) {
                it?.let { UsernameDisplay(it, Modifier.weight(1f), fontWeight = fontWeight, accountViewModel = accountViewModel) }
            }
        }
    } else {
        Row {
            userList.take(4).forEachIndexed { index, value ->
                LoadUser(baseUserHex = value, accountViewModel) {
                    it?.let { ShortUsernameDisplay(baseUser = it, fontWeight = fontWeight, accountViewModel = accountViewModel) }
                }

                if (min(userList.size, 4) - 1 != index) {
                    Text(
                        text = ", ",
                        fontWeight = fontWeight,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

@Composable
fun DisplayRoomSubject(
    roomSubject: String,
    fontWeight: FontWeight = FontWeight.Bold,
) {
    Row {
        Text(
            text = roomSubject,
            fontWeight = fontWeight,
            maxLines = 1,
        )
    }
}

@Composable
fun ShortUsernameDisplay(
    baseUser: User,
    weight: Modifier = Modifier,
    fontWeight: FontWeight = FontWeight.Bold,
    accountViewModel: AccountViewModel,
) {
    val userName by
        baseUser
            .live()
            .metadata
            .map { it.user.toBestShortFirstName() }
            .distinctUntilChanged()
            .observeAsState(baseUser.toBestShortFirstName())

    CrossfadeIfEnabled(targetState = userName, modifier = weight, accountViewModel = accountViewModel) {
        CreateTextWithEmoji(
            text = it,
            tags = baseUser.info?.tags,
            fontWeight = fontWeight,
            maxLines = 1,
        )
    }
}

@Composable
fun LoadUser(
    baseUserHex: String,
    accountViewModel: AccountViewModel,
    content: @Composable (User?) -> Unit,
) {
    var user by
        remember(baseUserHex) { mutableStateOf(accountViewModel.getUserIfExists(baseUserHex)) }

    if (user == null) {
        LaunchedEffect(key1 = baseUserHex) {
            accountViewModel.checkGetOrCreateUser(baseUserHex) { newUser ->
                if (user != newUser) {
                    user = newUser
                }
            }
        }
    }

    content(user)
}

@Composable
fun ChannelName(
    channelIdHex: String,
    channelPicture: String?,
    channelTitle: @Composable (Modifier) -> Unit,
    channelLastTime: Long?,
    channelLastContent: String?,
    hasNewMessages: Boolean,
    loadProfilePicture: Boolean,
    loadRobohash: Boolean,
    onClick: () -> Unit,
) {
    ChannelName(
        channelPicture = {
            RobohashFallbackAsyncImage(
                robot = channelIdHex,
                model = channelPicture,
                contentDescription = stringRes(R.string.channel_image),
                modifier = AccountPictureModifier,
                loadProfilePicture = loadProfilePicture,
                loadRobohash = loadRobohash,
            )
        },
        channelTitle,
        channelLastTime,
        channelLastContent,
        hasNewMessages,
        onClick,
    )
}

@Composable
fun ChannelName(
    channelPicture: @Composable () -> Unit,
    channelTitle: @Composable (Modifier) -> Unit,
    channelLastTime: Long?,
    channelLastContent: String?,
    hasNewMessages: Boolean,
    onClick: () -> Unit,
) {
    ChatHeaderLayout(
        channelPicture = channelPicture,
        firstRow = {
            channelTitle(Modifier.weight(1f))
            TimeAgo(channelLastTime)
        },
        secondRow = {
            if (channelLastContent != null) {
                Text(
                    channelLastContent,
                    color = MaterialTheme.colorScheme.grayText,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = LocalTextStyle.current.copy(textDirection = TextDirection.Content),
                    modifier = Modifier.weight(1f),
                )
            } else {
                Text(
                    stringRes(R.string.referenced_event_not_found),
                    color = MaterialTheme.colorScheme.grayText,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
            }

            if (hasNewMessages) {
                NewItemsBubble()
            }
        },
        onClick = onClick,
    )
}

@Composable
private fun TimeAgo(channelLastTime: Long?) {
    if (channelLastTime == null) return

    val context = LocalContext.current
    val timeAgo = remember(channelLastTime) { timeAgo(channelLastTime, context) }
    Text(
        text = timeAgo,
        color = MaterialTheme.colorScheme.grayText,
        maxLines = 1,
    )
}

@Composable
fun NewItemsBubble() {
    Box(
        modifier =
            Modifier
                .padding(start = 3.dp)
                .width(10.dp)
                .height(10.dp)
                .clip(shape = CircleShape)
                .background(MaterialTheme.colorScheme.primary),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            "",
            color = Color.White,
            textAlign = TextAlign.Center,
            fontSize = 12.sp,
            maxLines = 1,
            modifier = Modifier.wrapContentHeight().align(Alignment.Center),
        )
    }
}
