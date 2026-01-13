/**
 * Copyright (c) 2025 Vitor Pamplona
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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.rooms

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.commons.model.emphChat.EphemeralChatChannel
import com.vitorpamplona.amethyst.commons.model.nip28PublicChats.PublicChatChannel
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.model.User
import com.vitorpamplona.amethyst.model.nip11RelayInfo.loadRelayInfo
import com.vitorpamplona.amethyst.service.relayClient.reqCommand.channel.observeChannel
import com.vitorpamplona.amethyst.service.relayClient.reqCommand.event.observeNoteHasEvent
import com.vitorpamplona.amethyst.service.relayClient.reqCommand.user.observeUserName
import com.vitorpamplona.amethyst.ui.components.RobohashFallbackAsyncImage
import com.vitorpamplona.amethyst.ui.layouts.ChatHeaderLayout
import com.vitorpamplona.amethyst.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.ui.navigation.routes.Route
import com.vitorpamplona.amethyst.ui.navigation.routes.routeFor
import com.vitorpamplona.amethyst.ui.note.BlankNote
import com.vitorpamplona.amethyst.ui.note.LoadDecryptedContentOrNull
import com.vitorpamplona.amethyst.ui.note.LoadPublicChatChannel
import com.vitorpamplona.amethyst.ui.note.NonClickableUserPictures
import com.vitorpamplona.amethyst.ui.note.ObserveDraftEvent
import com.vitorpamplona.amethyst.ui.note.timeAgo
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.privateDM.header.RoomNameDisplay
import com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.publicChannels.ephemChat.LoadEphemeralChatChannel
import com.vitorpamplona.amethyst.ui.stringRes
import com.vitorpamplona.amethyst.ui.theme.AccountPictureModifier
import com.vitorpamplona.amethyst.ui.theme.Height4dpModifier
import com.vitorpamplona.amethyst.ui.theme.Size55dp
import com.vitorpamplona.amethyst.ui.theme.grayText
import com.vitorpamplona.amethyst.ui.theme.newItemBubbleModifier
import com.vitorpamplona.amethyst.ui.theme.placeholderText
import com.vitorpamplona.quartz.experimental.ephemChat.chat.EphemeralChatEvent
import com.vitorpamplona.quartz.nip17Dm.base.ChatroomKey
import com.vitorpamplona.quartz.nip17Dm.base.ChatroomKeyable
import com.vitorpamplona.quartz.nip28PublicChat.admin.ChannelCreateEvent
import com.vitorpamplona.quartz.nip28PublicChat.admin.ChannelMetadataEvent
import com.vitorpamplona.quartz.nip28PublicChat.message.ChannelMessageEvent
import com.vitorpamplona.quartz.nip37Drafts.DraftWrapEvent

@Composable
fun ChatroomHeaderCompose(
    baseNote: Note,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    if (baseNote.event != null) {
        ChatroomComposeChannelOrUser(baseNote, accountViewModel, nav)
    } else {
        val hasEvent by observeNoteHasEvent(baseNote, accountViewModel)
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
    val baseNoteEvent = baseNote.event
    if (baseNoteEvent is DraftWrapEvent) {
        ObserveDraftEvent(baseNote, accountViewModel) { innerNote ->
            ChatroomEntry(innerNote, accountViewModel, nav)
        }
    } else {
        ChatroomEntry(baseNote, accountViewModel, nav)
    }
}

@Composable
private fun ChatroomEntry(
    lastMessage: Note,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val baseNoteEvent = lastMessage.event
    when (baseNoteEvent) {
        is ChannelMessageEvent ->
            baseNoteEvent.channelId()?.let {
                LoadPublicChatChannel(it, accountViewModel) { channel ->
                    ChannelRoomCompose(lastMessage, channel, accountViewModel, nav)
                }
            }
        is ChannelMetadataEvent ->
            baseNoteEvent.channelId()?.let {
                LoadPublicChatChannel(it, accountViewModel) { channel ->
                    ChannelRoomCompose(lastMessage, channel, accountViewModel, nav)
                }
            }
        is ChannelCreateEvent ->
            LoadPublicChatChannel(baseNoteEvent.id, accountViewModel) { channel ->
                ChannelRoomCompose(lastMessage, channel, accountViewModel, nav)
            }
        is ChatroomKeyable -> {
            val room = baseNoteEvent.chatroomKey(accountViewModel.userProfile().pubkeyHex)
            UserRoomCompose(room, lastMessage, accountViewModel, nav)
        }
        is EphemeralChatEvent -> {
            baseNoteEvent.roomId()?.let {
                LoadEphemeralChatChannel(it, accountViewModel) { channel ->
                    ChannelRoomCompose(lastMessage, channel, accountViewModel, nav)
                }
            }
        }
        else -> BlankNote()
    }
}

@Composable
private fun ChannelRoomCompose(
    lastMessage: Note,
    channel: PublicChatChannel,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val authorName by observeUserName(lastMessage.author!!, accountViewModel)
    val channelState by observeChannel(channel, accountViewModel)

    val channel = channelState?.channel as? PublicChatChannel ?: return

    val channelPicture = channel.profilePicture()
    val channelName = channel.toBestDisplayName()

    val noteEvent = lastMessage.event

    val description =
        if (noteEvent is ChannelCreateEvent) {
            stringRes(R.string.channel_created)
        } else if (noteEvent is ChannelMetadataEvent) {
            "${stringRes(R.string.channel_information_changed_to)} "
        } else {
            noteEvent?.content?.take(200)
        }

    val lastReadTime by accountViewModel.account.loadLastReadFlow("Channel/${channel.idHex}").collectAsStateWithLifecycle()

    ChannelName(
        channelIdHex = channel.idHex,
        channelPicture = channelPicture,
        channelTitle = { modifier -> ChannelTitleWithLabelInfo(channelName, R.string.public_chat, modifier) },
        channelLastTime = lastMessage.createdAt(),
        channelLastContent = "$authorName: $description",
        hasNewMessages = (noteEvent?.createdAt ?: Long.MIN_VALUE) > lastReadTime,
        loadProfilePicture = accountViewModel.settings.showProfilePictures(),
        loadRobohash = accountViewModel.settings.isNotPerformanceMode(),
        onClick = { nav.nav(routeFor(channel)) },
    )
}

@Composable
private fun ChannelRoomCompose(
    lastMessage: Note,
    channel: EphemeralChatChannel,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val authorName by observeUserName(lastMessage.author!!, accountViewModel)
    val channelState by observeChannel(channel, accountViewModel)

    val channel = channelState?.channel as? EphemeralChatChannel ?: return

    val relayInfo by loadRelayInfo(channel.roomId.relayUrl)

    val noteEvent = lastMessage.event
    val description = noteEvent?.content?.take(200)

    val lastReadTime by accountViewModel.account.loadLastReadFlow("Channel/${channel.roomId.toKey()}").collectAsStateWithLifecycle()

    ChannelName(
        channelIdHex = channel.roomId.toKey(),
        channelPicture = relayInfo.icon,
        channelTitle = { modifier -> ChannelTitleWithLabelInfo(channel.toBestDisplayName(), R.string.ephemeral_relay_chat, modifier) },
        channelLastTime = lastMessage.createdAt(),
        channelLastContent = "$authorName: $description",
        hasNewMessages = (noteEvent?.createdAt ?: Long.MIN_VALUE) > lastReadTime,
        loadProfilePicture = accountViewModel.settings.showProfilePictures(),
        loadRobohash = accountViewModel.settings.isNotPerformanceMode(),
        onClick = { nav.nav(routeFor(channel)) },
    )
}

@Composable
private fun ChannelTitleWithLabelInfo(
    channelName: String,
    label: Int,
    modifier: Modifier,
) {
    val label = stringRes(id = label)
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
    room: ChatroomKey,
    lastMessage: Note,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
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
            TimeAgo(lastMessage.createdAt())
        },
        secondRow = {
            LoadDecryptedContentOrNull(lastMessage, accountViewModel) { content ->
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

            val lastReadTime by accountViewModel.account.loadLastReadFlow("Room/${room.hashCode()}").collectAsStateWithLifecycle()
            if ((lastMessage.createdAt() ?: Long.MIN_VALUE) > lastReadTime) {
                Spacer(modifier = Height4dpModifier)
                NewItemsBubble()
            }
        },
        onClick = { nav.nav(Route.Room(room)) },
    )
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
            user = accountViewModel.checkGetOrCreateUser(baseUserHex)
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
                Spacer(modifier = Height4dpModifier)
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
    Box(MaterialTheme.colorScheme.newItemBubbleModifier)
}
