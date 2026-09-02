/*
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
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbol
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.model.User
import com.vitorpamplona.amethyst.commons.model.chatMessageMarksRoomAsRead
import com.vitorpamplona.amethyst.commons.model.concord.ConcordChannel
import com.vitorpamplona.amethyst.commons.model.emphChat.EphemeralChatChannel
import com.vitorpamplona.amethyst.commons.model.geohashChat.GeohashChatChannel
import com.vitorpamplona.amethyst.commons.model.marmotGroups.MarmotGroupChatroom
import com.vitorpamplona.amethyst.commons.model.nip28PublicChats.PublicChatChannel
import com.vitorpamplona.amethyst.commons.model.nip29RelayGroups.RelayGroupChannel
import com.vitorpamplona.amethyst.commons.model.privateChatLastReadRoute
import com.vitorpamplona.amethyst.commons.model.privateChats.ChatPreview
import com.vitorpamplona.amethyst.commons.model.privateChats.chatPreviewOf
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.channel_created
import com.vitorpamplona.amethyst.commons.resources.channel_image
import com.vitorpamplona.amethyst.commons.resources.channel_information_changed_to
import com.vitorpamplona.amethyst.commons.resources.channel_invite_ignore
import com.vitorpamplona.amethyst.commons.resources.channel_invite_leave
import com.vitorpamplona.amethyst.commons.resources.channel_invite_row_added_you
import com.vitorpamplona.amethyst.commons.resources.channel_invite_row_added_you_by
import com.vitorpamplona.amethyst.commons.resources.chat_preview_decrypting
import com.vitorpamplona.amethyst.commons.resources.chat_preview_you_prefix
import com.vitorpamplona.amethyst.commons.resources.could_not_decrypt_the_message
import com.vitorpamplona.amethyst.commons.resources.loading_feed
import com.vitorpamplona.amethyst.commons.resources.marmot_group_no_messages_yet
import com.vitorpamplona.amethyst.commons.resources.muted_chat_content_description
import com.vitorpamplona.amethyst.commons.resources.pinned_to_top
import com.vitorpamplona.amethyst.commons.resources.referenced_event_not_found
import com.vitorpamplona.amethyst.commons.resources.relay_group_no_messages_yet
import com.vitorpamplona.amethyst.commons.ui.note.HeaderPill
import com.vitorpamplona.amethyst.model.LocalCache
import com.vitorpamplona.amethyst.model.buzz.toMembershipNotice
import com.vitorpamplona.amethyst.model.nip11RelayInfo.loadRelayInfo
import com.vitorpamplona.amethyst.service.relayClient.reqCommand.channel.observeChannel
import com.vitorpamplona.amethyst.service.relayClient.reqCommand.event.observeNoteHasEvent
import com.vitorpamplona.amethyst.service.relayClient.reqCommand.user.UserFinderByParentFilterAssemblerSubscription
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
import com.vitorpamplona.amethyst.ui.note.elements.TimeAgoStyle
import com.vitorpamplona.amethyst.ui.note.elements.ToggleableTimeAgoText
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.feed.types.buzzTimelinePreviewSummary
import com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.feed.types.observeUserNameByHex
import com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.marmotGroup.loadMarmotRelayIcon
import com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.marmotGroup.marmotGroupLastReadRoute
import com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.marmotGroup.rememberMarmotGroupIconUrl
import com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.privateDM.header.RoomNameDisplay
import com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.privateDM.header.reportWarningContentDescription
import com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.publicChannels.concord.ConcordCommunityPill
import com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.publicChannels.concord.ConcordLeaveDialog
import com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.publicChannels.concord.concordChannelLastReadRoute
import com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.publicChannels.concord.concordCommunityHasUnreadFlow
import com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.publicChannels.concord.rememberConcordImageModel
import com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.publicChannels.ephemChat.LoadEphemeralChatChannel
import com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.publicChannels.relayGroup.RelayNameChip
import com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.publicChannels.relayGroup.relayGroupChannelLastReadRoute
import com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.publicChannels.relayGroup.relayGroupServerHasUnreadFlow
import com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.rooms.dal.ConcordServerRoomNote
import com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.rooms.dal.RelayGroupServerRoomNote
import com.vitorpamplona.amethyst.ui.stringRes
import com.vitorpamplona.amethyst.ui.theme.AccountPictureModifier
import com.vitorpamplona.amethyst.ui.theme.ChatLabelMaxWidth
import com.vitorpamplona.amethyst.ui.theme.Height4dpModifier
import com.vitorpamplona.amethyst.ui.theme.Size15Modifier
import com.vitorpamplona.amethyst.ui.theme.Size55dp
import com.vitorpamplona.amethyst.ui.theme.StdHorzSpacer
import com.vitorpamplona.amethyst.ui.theme.grayText
import com.vitorpamplona.amethyst.ui.theme.newItemBubbleModifier
import com.vitorpamplona.amethyst.ui.theme.placeholderText
import com.vitorpamplona.quartz.buzz.notifications.MemberAddedNotificationEvent
import com.vitorpamplona.quartz.experimental.bitchat.geohash.GeohashChatEvent
import com.vitorpamplona.quartz.experimental.ephemChat.chat.EphemeralChatEvent
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.displayUrl
import com.vitorpamplona.quartz.nip17Dm.base.ChatroomKey
import com.vitorpamplona.quartz.nip17Dm.base.ChatroomKeyable
import com.vitorpamplona.quartz.nip28PublicChat.admin.ChannelCreateEvent
import com.vitorpamplona.quartz.nip28PublicChat.admin.ChannelMetadataEvent
import com.vitorpamplona.quartz.nip28PublicChat.message.ChannelMessageEvent
import com.vitorpamplona.quartz.nip29RelayGroups.GroupId
import com.vitorpamplona.quartz.nip29RelayGroups.groupId
import com.vitorpamplona.quartz.nip29RelayGroups.isGroupScoped
import com.vitorpamplona.quartz.nip37Drafts.DraftWrapEvent
import kotlinx.coroutines.flow.emptyFlow

@Composable
fun ChatroomHeaderCompose(
    baseNote: Note,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    // Some Messages rows have no event: a per-relay grouped row (RelayGroupServerRoomNote), or a
    // joined Marmot/NIP-29 group with no messages yet (an event-less placeholder carrying its channel
    // as a gatherer). Render these directly instead of waiting for an event that never arrives, which
    // would blank the row.
    val rendersWithoutEvent =
        baseNote is RelayGroupServerRoomNote ||
            baseNote is ConcordServerRoomNote ||
            (
                baseNote.event == null &&
                    baseNote.inGatherers?.any { it is MarmotGroupChatroom || it is RelayGroupChannel || it is ConcordChannel } == true
            )

    if (baseNote.event != null || rendersWithoutEvent) {
        ChatroomComposeChannelOrUser(baseNote, accountViewModel, nav)
    } else {
        val hasEvent by observeNoteHasEvent(baseNote, accountViewModel)
        if (hasEvent) {
            ChatroomComposeChannelOrUser(baseNote, accountViewModel, nav)
        } else {
            UserFinderByParentFilterAssemblerSubscription(baseNote, accountViewModel)
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
            ChatroomEntry(innerNote, accountViewModel, nav, isDraft = true)
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
    isDraft: Boolean = false,
) {
    if (lastMessage is RelayGroupServerRoomNote) {
        RelayGroupServerRoomCompose(lastMessage, accountViewModel, nav)
        return
    }

    if (lastMessage is ConcordServerRoomNote) {
        ConcordServerRoomCompose(lastMessage, accountViewModel, nav)
        return
    }

    val marmotGroup = lastMessage.inGatherers?.firstNotNullOfOrNull { it as? MarmotGroupChatroom }
    if (marmotGroup != null) {
        MarmotGroupRoomCompose(lastMessage, marmotGroup, accountViewModel, nav)
        return
    }

    val relayGroup = lastMessage.inGatherers?.firstNotNullOfOrNull { it as? RelayGroupChannel }
    if (relayGroup != null) {
        RelayGroupRoomCompose(lastMessage, relayGroup, accountViewModel, nav)
        return
    }

    val concordChannel = lastMessage.inGatherers?.firstNotNullOfOrNull { it as? ConcordChannel }
    if (concordChannel != null) {
        ConcordRoomCompose(lastMessage, concordChannel, accountViewModel, nav)
        return
    }

    val geohashChannel = lastMessage.inGatherers?.firstNotNullOfOrNull { it as? GeohashChatChannel }
    if (geohashChannel != null) {
        GeohashRoomCompose(lastMessage, geohashChannel, accountViewModel, nav)
        return
    }

    // A relay's "somebody added you" verdict (kind 44100) stands in for the group it invites me to.
    // Matched before the generic group-scoped fallback below, which would otherwise render it as an
    // ordinary joined-group row: with the relay keypair's npub and the raw JSON body as the preview,
    // and a long-press menu offering to leave a group I never agreed to join.
    val inviteEvent = lastMessage.event as? MemberAddedNotificationEvent
    if (inviteEvent != null) {
        ChannelInviteRoomCompose(lastMessage, accountViewModel, nav)
        return
    }

    // A NIP-29 group message whose channel gatherer didn't attach (e.g. loaded before its channel
    // existed, or via a path that skips attach) has no case in the when() below and would blank out.
    // Resolve the group from its `h` tag + provenance relay and render the group row anyway.
    val groupScopedEvent = lastMessage.event?.takeIf { it.isGroupScoped() }
    if (groupScopedEvent != null) {
        val gid = groupScopedEvent.groupId()
        val hostRelay = lastMessage.relays.firstOrNull()
        if (gid != null && hostRelay != null) {
            RelayGroupRoomCompose(
                lastMessage,
                LocalCache.getOrCreateRelayGroupChannel(GroupId(gid, hostRelay)),
                accountViewModel,
                nav,
            )
            return
        }
    }

    val baseNoteEvent = lastMessage.event
    when (baseNoteEvent) {
        // ChannelMessageEvent and ChannelMetadataEvent both expose channelId() via
        // IsInPublicChatChannel and render identically here. Matched explicitly (rather
        // than on the IsInPublicChatChannel interface) so the channel-admin events
        // ChannelHideMessageEvent/ChannelMuteUserEvent keep falling through to `else`.
        is ChannelMessageEvent, is ChannelMetadataEvent -> {
            baseNoteEvent.channelId()?.let {
                LoadPublicChatChannel(it, accountViewModel) { channel ->
                    ChannelRoomCompose(lastMessage, channel, accountViewModel, nav)
                }
            }
        }

        is ChannelCreateEvent -> {
            LoadPublicChatChannel(baseNoteEvent.id, accountViewModel) { channel ->
                ChannelRoomCompose(lastMessage, channel, accountViewModel, nav)
            }
        }

        is ChatroomKeyable -> {
            val room = baseNoteEvent.chatroomKey(accountViewModel.userProfile().pubkeyHex)
            UserRoomCompose(room, lastMessage, isDraft, accountViewModel, nav)
        }

        is EphemeralChatEvent -> {
            baseNoteEvent.roomId()?.let {
                LoadEphemeralChatChannel(it, accountViewModel) { channel ->
                    ChannelRoomCompose(lastMessage, channel, accountViewModel, nav)
                }
            }
        }

        else -> {
            UserFinderByParentFilterAssemblerSubscription(lastMessage, accountViewModel)
            BlankNote()
        }
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
            stringRes(Res.string.channel_created)
        } else if (noteEvent is ChannelMetadataEvent) {
            "${stringRes(Res.string.channel_information_changed_to)} "
        } else {
            noteEvent?.content?.take(200)
        }

    // One predicate for the row dot and the bottom-bar badge — see rowHasUnread.
    // `emptyFlow()` is a shared singleton, so a row that can never be unread allocates nothing.
    // The seed matters: collection only starts after the first composition, so without it every
    // row would paint dotless for a frame and then correct itself while scrolling.
    val unread = remember(lastMessage) { rowHasUnread(lastMessage, accountViewModel.account) }
    val hasNewMessages by (unread?.flow ?: emptyFlow()).collectAsStateWithLifecycle(unread?.initial ?: false)

    var menuOpen by remember { mutableStateOf(false) }
    // Kept as a State (no `by`) so `.value` is read only inside the title and menu-text
    // slots, confining mute-toggle invalidations to those scopes.
    val mutedChats = accountViewModel.mutedPublicChatsFlow().collectAsStateWithLifecycle()

    ChannelName(
        channelIdHex = channel.idHex,
        channelPicture = channelPicture,
        channelTitle = { modifier ->
            val isMuted = channel.idHex in mutedChats.value
            ChannelTitleWithLabelInfo(
                channelName,
                if (isMuted) MaterialSymbols.NotificationsOff else MaterialSymbols.Public,
                R.string.public_chat,
                modifier,
                labelContentDescription = if (isMuted) stringRes(Res.string.muted_chat_content_description) else null,
            )
        },
        channelLastTime = lastMessage.createdAt(),
        channelLastContent = "$authorName: $description",
        hasNewMessages = hasNewMessages,
        loadProfilePicture = accountViewModel.settings.showProfilePictures(),
        loadRobohash = accountViewModel.settings.isNotPerformanceMode(),
        autoPlayGif =
            accountViewModel.settings.autoPlayVideosFlow
                .collectAsStateWithLifecycle()
                .value,
        onClick = { nav.nav(routeFor(channel)) },
        onLongClick = { menuOpen = true },
    )

    DropdownMenu(
        expanded = menuOpen,
        onDismissRequest = { menuOpen = false },
    ) {
        DropdownMenuItem(
            text = {
                Text(
                    stringRes(
                        if (channel.idHex in mutedChats.value) {
                            R.string.unmute_notifications
                        } else {
                            R.string.mute_notifications
                        },
                    ),
                )
            },
            onClick = {
                accountViewModel.toggleMutedPublicChat(channel.idHex)
                menuOpen = false
            },
        )
    }
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
        channelTitle = { modifier -> ChannelTitleWithLabelInfo(channel.toBestDisplayName(), MaterialSymbols.Timer, R.string.ephemeral_relay_chat, modifier) },
        channelLastTime = lastMessage.createdAt(),
        channelLastContent = "$authorName: $description",
        hasNewMessages = (noteEvent?.createdAt ?: Long.MIN_VALUE) > lastReadTime,
        loadProfilePicture = accountViewModel.settings.showProfilePictures(),
        loadRobohash = accountViewModel.settings.isNotPerformanceMode(),
        autoPlayGif =
            accountViewModel.settings.autoPlayVideosFlow
                .collectAsStateWithLifecycle()
                .value,
        onClick = { nav.nav(routeFor(channel)) },
    )
}

@Composable
private fun GeohashRoomCompose(
    lastMessage: Note,
    channel: GeohashChatChannel,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val channelState by observeChannel(channel, accountViewModel)
    val geohashChannel = channelState?.channel as? GeohashChatChannel ?: channel

    // Anonymous cells have no author profile; the sender's display name lives in the message's `n` tag.
    val noteEvent = lastMessage.event as? GeohashChatEvent
    val nick = noteEvent?.nickname()?.takeIf { it.isNotBlank() } ?: lastMessage.author?.pubkeyHex?.take(8)
    val description = noteEvent?.content?.take(200)
    val lastContent = if (noteEvent != null) "$nick: $description" else ""

    val lastReadTime by accountViewModel.account.loadLastReadFlow("Geohash/${geohashChannel.geohash}").collectAsStateWithLifecycle()

    ChannelName(
        channelIdHex = "Geohash/${geohashChannel.geohash}",
        channelPicture = null,
        channelTitle = { modifier -> ChannelTitleWithLabelInfo(geohashChannel.toBestDisplayName(), MaterialSymbols.LocationOn, R.string.geohash_chat, modifier) },
        channelLastTime = lastMessage.createdAt(),
        channelLastContent = lastContent,
        hasNewMessages = (noteEvent?.createdAt ?: Long.MIN_VALUE) > lastReadTime,
        loadProfilePicture = accountViewModel.settings.showProfilePictures(),
        loadRobohash = accountViewModel.settings.isNotPerformanceMode(),
        autoPlayGif =
            accountViewModel.settings.autoPlayVideosFlow
                .collectAsStateWithLifecycle()
                .value,
        onClick = { nav.nav(Route.GeohashChat(geohashChannel.geohash)) },
    )
}

@Composable
private fun MarmotGroupRoomCompose(
    lastMessage: Note,
    chatroom: MarmotGroupChatroom,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val displayName by chatroom.displayName.collectAsStateWithLifecycle()
    val image by chatroom.image.collectAsStateWithLifecycle()
    val relays by chatroom.relays.collectAsStateWithLifecycle()
    val adminPubkeys by chatroom.adminPubkeys.collectAsStateWithLifecycle()

    val author = lastMessage.author
    val noteEvent = lastMessage.event
    val groupName = displayName?.takeIf { it.isNotBlank() } ?: "Group ${chatroom.nostrGroupId.take(8)}"

    // Prefer the group's own (encrypted) avatar; when it has none, fall back to the
    // NIP-11 icon of one of the group's relays (fetched on a cache miss).
    val channelPicture =
        if (image != null) {
            rememberMarmotGroupIconUrl(image, accountViewModel, adminPubkeys)
        } else {
            loadMarmotRelayIcon(relays)
        }

    val lastContent =
        if (author != null && noteEvent != null) {
            val authorName by observeUserName(author, accountViewModel)
            "$authorName: ${noteEvent.content.take(200)}"
        } else {
            stringRes(Res.string.marmot_group_no_messages_yet)
        }

    val lastReadTime by accountViewModel.account.loadLastReadFlow(marmotGroupLastReadRoute(chatroom.nostrGroupId)).collectAsStateWithLifecycle()

    ChannelName(
        channelIdHex = chatroom.nostrGroupId,
        channelPicture = channelPicture,
        channelTitle = { modifier -> ChannelTitleWithLabelInfo(groupName, MaterialSymbols.Lock, R.string.marmot_group, modifier) },
        channelLastTime = lastMessage.createdAt(),
        channelLastContent = lastContent,
        hasNewMessages = (lastMessage.createdAt() ?: Long.MIN_VALUE) > lastReadTime,
        loadProfilePicture = accountViewModel.settings.showProfilePictures(),
        loadRobohash = accountViewModel.settings.isNotPerformanceMode(),
        autoPlayGif =
            accountViewModel.settings.autoPlayVideosFlow
                .collectAsStateWithLifecycle()
                .value,
        onClick = { nav.nav(Route.MarmotGroupChat(chatroom.nostrGroupId)) },
    )
}

@Composable
private fun RelayGroupRoomCompose(
    lastMessage: Note,
    baseChannel: RelayGroupChannel,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val author = lastMessage.author
    val noteEvent = lastMessage.event
    val lastContent =
        if (author != null && noteEvent != null) {
            val authorName by observeUserName(author, accountViewModel)
            // A Buzz timeline row (system line, huddle/job activity, diff) carries JSON/diff in its
            // content, so show its human-readable summary — the same text the in-chat row renders —
            // rather than "author: {json}". Plain chat messages fall through to the usual framing.
            buzzTimelinePreviewSummary(noteEvent, accountViewModel) ?: "$authorName: ${noteEvent.content.take(200)}"
        } else {
            // Event-less placeholder row. Until the channel's `limit = 1` preview REQ settles we cannot
            // tell an empty channel from one whose newest message simply hasn't arrived, and claiming
            // "No messages yet" for a busy channel reads as a bug — so say "Loading" while the joined
            // fleet is still fetching, and commit to the empty wording only once it has.
            val stillFetching by accountViewModel
                .dataSources()
                .relayGroupJoinedChatTail.tail.loadingMore
                .collectAsStateWithLifecycle()
            if (stillFetching) {
                stringRes(Res.string.loading_feed)
            } else {
                stringRes(Res.string.relay_group_no_messages_yet)
            }
        }

    RelayGroupRow(
        baseChannel = baseChannel,
        lastContent = lastContent,
        lastTime = lastMessage.createdAt(),
        accountViewModel = accountViewModel,
        nav = nav,
    ) { channel, dismiss ->
        // Long-press brings the group's membership actions to the Messages row itself, mirroring the
        // group top bar so "Remove from Messages" (drop from my list, stay a member) and "Leave"
        // (kind-9022) are reachable without opening the group first.
        DropdownMenuItem(
            text = { Text(stringRes(R.string.remove_from_messages)) },
            onClick = {
                dismiss()
                accountViewModel.removeRelayGroupFromMessages(channel)
            },
        )
        DropdownMenuItem(
            text = { Text(stringRes(R.string.leave), color = MaterialTheme.colorScheme.error) },
            onClick = {
                dismiss()
                accountViewModel.leaveRelayGroup(channel)
            },
        )
    }
}

/**
 * One NIP-29 group as a Messages row: its picture, name, host-relay chip, and a caller-supplied
 * "last message" line and long-press menu.
 *
 * Everything except those two is fixed here, because every list that shows a group has to agree on it —
 * the picture fallback, the unread rule, and where a tap goes. A pending invite is a row in the same
 * sense a joined group is (see `ChannelInvitesSection`); it differs only in what its newest line says
 * and what you can do to it, which is exactly the two slots.
 */
@Composable
fun RelayGroupRow(
    baseChannel: RelayGroupChannel,
    lastContent: String?,
    lastTime: Long?,
    accountViewModel: AccountViewModel,
    nav: INav,
    menuContent: @Composable ColumnScope.(channel: RelayGroupChannel, dismiss: () -> Unit) -> Unit,
) {
    val channelState by observeChannel(baseChannel, accountViewModel)
    val channel = channelState?.channel as? RelayGroupChannel ?: baseChannel

    val groupPicture = channel.profilePicture()?.ifBlank { null }
    val channelPicture =
        if (groupPicture != null) {
            groupPicture
        } else {
            // Missing/blank group picture: fall back to the host relay's NIP-11 icon
            // (loadRelayInfo fetches the doc on a cache miss).
            val relayInfo by loadRelayInfo(channel.groupId.relayUrl)
            relayInfo.icon?.ifBlank { null }
        }

    // Unread dot: the newest chat is newer than the last time I opened this group. Same last-read
    // route the open group's feed advances (relayGroupChannelLastReadRoute), so opening clears it.
    // A placeholder row (no messages yet) has a null createdAt and never lights the dot.
    val lastReadTime by accountViewModel.account.loadLastReadFlow(relayGroupChannelLastReadRoute(channel.groupId)).collectAsStateWithLifecycle()

    var menuOpen by remember { mutableStateOf(false) }

    Box {
        ChannelName(
            channelIdHex = channel.groupId.id,
            channelPicture = channelPicture,
            channelTitle = { modifier ->
                Row(verticalAlignment = Alignment.CenterVertically, modifier = modifier) {
                    Text(
                        text = channel.toBestDisplayName(),
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    Spacer(Modifier.width(6.dp))
                    RelayNameChip(
                        label = channel.groupId.relayUrl.displayUrl(),
                        onClick = { nav.nav(Route.RelayGroupServer(channel.groupId.relayUrl.url)) },
                    )
                }
            },
            channelLastTime = lastTime,
            channelLastContent = lastContent,
            hasNewMessages = (lastTime ?: Long.MIN_VALUE) > lastReadTime,
            loadProfilePicture = accountViewModel.settings.showProfilePictures(),
            loadRobohash = accountViewModel.settings.isNotPerformanceMode(),
            autoPlayGif =
                accountViewModel.settings.autoPlayVideosFlow
                    .collectAsStateWithLifecycle()
                    .value,
            onClick = { nav.nav(Route.RelayGroup(channel.groupId.id, channel.groupId.relayUrl.url)) },
            onLongClick = { menuOpen = true },
        )

        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            menuContent(channel) { menuOpen = false }
        }
    }
}

/**
 * A pending channel invite as a Messages row: the group it invites me to, with the invitation itself
 * as the row's newest line.
 *
 * New Requests is a list of rooms awaiting a decision and this is one, so it sorts in among the
 * unaccepted DMs by when the invite landed rather than being pinned above them. Deciding happens the
 * same way it does for a joined group: tap opens the channel so it can be read first (its top bar
 * offers "Add to Messages"), long-press brings the three answers to the row.
 */
@Composable
private fun ChannelInviteRoomCompose(
    inviteNote: Note,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    // Collected rather than read as .value, for the same reason as RenderChannelInvite: the workspace
    // set lands asynchronously, and a snapshot read never recomposes, so a row drawn before its
    // workspace was known would never appear.
    val workspaces by accountViewModel.account.buzzWorkspaces.flow
        .collectAsStateWithLifecycle()
    val notice = remember(inviteNote, workspaces) { inviteNote.toMembershipNotice(workspaces) } ?: return
    val baseChannel =
        remember(notice) { LocalCache.getOrCreateRelayGroupChannel(GroupId(notice.channelId, notice.relay)) }

    // The actor, not the signer: a kind-44100 is signed by the relay keypair reporting the membership
    // change it made, so naming its author here would put an npub on every invite.
    val actorName = observeUserNameByHex(notice.actor, accountViewModel)
    val lastContent =
        if (notice.actor != null) {
            stringRes(Res.string.channel_invite_row_added_you_by, actorName)
        } else {
            stringRes(Res.string.channel_invite_row_added_you)
        }

    RelayGroupRow(
        baseChannel = baseChannel,
        lastContent = lastContent,
        lastTime = notice.createdAt,
        accountViewModel = accountViewModel,
        nav = nav,
    ) { channel, dismiss ->
        DropdownMenuItem(
            text = { Text(stringRes(R.string.add_to_messages)) },
            onClick = {
                dismiss()
                accountViewModel.acceptChannelInvite(channel)
            },
        )
        // Ignore is a local display choice that leaves me in the roster; Leave is the kind-9022 that
        // actually removes me from the channel. Keeping both means "get this off my list" never has
        // to mean "announce to the relay that I left".
        DropdownMenuItem(
            text = { Text(stringRes(Res.string.channel_invite_ignore)) },
            onClick = {
                dismiss()
                accountViewModel.dismissChannelInvite(channel.groupId.id)
            },
        )
        DropdownMenuItem(
            text = { Text(stringRes(Res.string.channel_invite_leave), color = MaterialTheme.colorScheme.error) },
            onClick = {
                dismiss()
                accountViewModel.leaveChannelInvite(channel)
            },
        )
    }
}

@Composable
private fun ConcordRoomCompose(
    lastMessage: Note,
    baseChannel: ConcordChannel,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val channelState by observeChannel(baseChannel, accountViewModel)
    val channel = channelState?.channel as? ConcordChannel ?: baseChannel

    val author = lastMessage.author
    val noteEvent = lastMessage.event
    val lastContent =
        if (author != null && noteEvent != null) {
            val authorName by observeUserName(author, accountViewModel)
            "$authorName: ${noteEvent.content.take(200)}"
        } else {
            // Event-less placeholder row for a just-joined channel with no messages yet.
            channel.communityName ?: stringRes(Res.string.relay_group_no_messages_yet)
        }

    // Unread dot: the newest timeline message is newer than the last time I opened this channel.
    // Same last-read route the open channel's feed advances (concordChannelLastReadRoute), so
    // opening clears it. `lastMessage` is already the newest timeline note (or a null-timestamp
    // placeholder), so this stays in step with the badges on the Concord channel-list screen.
    val lastReadTime by accountViewModel.account
        .loadLastReadFlow(concordChannelLastReadRoute(channel.channelId.communityId, channel.channelId.channelId))
        .collectAsStateWithLifecycle()

    // Concord has no server-side membership beyond my own kind-13302 list, so there is no soft
    // "Remove from Messages" distinct from leaving — the only action is "Leave" (drop the community
    // from my list = I'm out). Long-press surfaces it on the row with the same confirm the community
    // screen uses; leaving a channel row leaves the whole community it belongs to (the dialog names it).
    val communityId = channel.channelId.communityId
    val isOwner =
        accountViewModel.account.concordSessions
            .sessionFor(communityId)
            ?.entry
            ?.owner == accountViewModel.account.signer.pubKey
    var menuOpen by remember { mutableStateOf(false) }
    var showLeave by remember { mutableStateOf(false) }

    if (showLeave) {
        ConcordLeaveDialog(
            communityName = channel.communityName ?: channel.toBestDisplayName(),
            isOwner = isOwner,
            onDismiss = { showLeave = false },
            onConfirm = {
                showLeave = false
                accountViewModel.leaveConcordCommunity(communityId)
            },
        )
    }

    Box {
        ChannelName(
            channelIdHex = channel.channelId.channelId,
            channelPicture = rememberConcordImageModel(channel.communityIcon, accountViewModel),
            channelTitle = { modifier ->
                Row(verticalAlignment = Alignment.CenterVertically, modifier = modifier) {
                    Text(
                        text = channel.toBestDisplayName(),
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    channel.communityName?.let { communityName ->
                        Spacer(Modifier.width(6.dp))
                        // The chip names the parent community and, when tapped, opens that community's
                        // channel list — the "chip that opens the Concord Channel" entry point.
                        ConcordCommunityPill(
                            communityName = communityName,
                            onClick = { nav.nav(Route.ConcordServer(channel.channelId.communityId)) },
                        )
                    }
                }
            },
            channelLastTime = lastMessage.createdAt(),
            channelLastContent = lastContent,
            hasNewMessages = (lastMessage.createdAt() ?: Long.MIN_VALUE) > lastReadTime,
            loadProfilePicture = accountViewModel.settings.showProfilePictures(),
            loadRobohash = accountViewModel.settings.isNotPerformanceMode(),
            autoPlayGif =
                accountViewModel.settings.autoPlayVideosFlow
                    .collectAsStateWithLifecycle()
                    .value,
            onClick = { nav.nav(Route.Concord(channel.channelId.communityId, channel.channelId.channelId)) },
            onLongClick = { menuOpen = true },
        )

        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            DropdownMenuItem(
                text = { Text(stringRes(R.string.leave), color = MaterialTheme.colorScheme.error) },
                onClick = {
                    menuOpen = false
                    showLeave = true
                },
            )
        }
    }
}

@Composable
private fun RelayGroupServerRoomCompose(
    row: RelayGroupServerRoomNote,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val relay = row.relay
    val relayInfo by loadRelayInfo(relay)
    val host = relay.displayUrl()
    val name = relayInfo.name?.takeIf { it.isNotBlank() } ?: host

    val author = row.newestMessage?.author
    val noteEvent = row.newestMessage?.event
    val lastContent =
        if (author != null && noteEvent != null) {
            val authorName by observeUserName(author, accountViewModel)
            // Buzz timeline rows (system/huddle/job/diff) carry JSON/diff content — summarize them
            // like the in-chat row instead of printing raw payload; plain chat falls through.
            buzzTimelinePreviewSummary(noteEvent, accountViewModel) ?: "$authorName: ${noteEvent.content.take(200)}"
        } else {
            stringRes(Res.string.relay_group_no_messages_yet)
        }

    // Collapsed relay row: light the dot when ANY joined group on this relay has unread chat.
    val hasNewMessages by remember(relay) {
        relayGroupServerHasUnreadFlow(accountViewModel.account, relay)
    }.collectAsStateWithLifecycle(false)

    ChannelName(
        channelIdHex = relay.url,
        channelPicture = relayInfo.icon,
        channelTitle = { modifier -> ChannelTitleWithLabelInfo(name, MaterialSymbols.Dns, R.string.relay_group_server_label, modifier) },
        channelLastTime = row.newestMessage?.createdAt(),
        channelLastContent = lastContent,
        hasNewMessages = hasNewMessages,
        loadProfilePicture = accountViewModel.settings.showProfilePictures(),
        loadRobohash = accountViewModel.settings.isNotPerformanceMode(),
        autoPlayGif =
            accountViewModel.settings.autoPlayVideosFlow
                .collectAsStateWithLifecycle()
                .value,
        onClick = { nav.nav(Route.RelayGroupServer(relay.url)) },
    )
}

@Composable
private fun ConcordServerRoomCompose(
    row: ConcordServerRoomNote,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    // Community name/icon from the folded Control Plane (bumped via the session revision).
    val revision by accountViewModel.account.concordSessions.revision
        .collectAsStateWithLifecycle()
    val metadata =
        remember(row.communityId, revision) {
            accountViewModel.account.concordSessions
                .sessionFor(row.communityId)
                ?.state
                ?.value
                ?.metadata
        }
    val name = metadata?.name?.takeIf { it.isNotBlank() } ?: stringRes(R.string.concord_home_title)

    val author = row.newestMessage?.author
    val noteEvent = row.newestMessage?.event
    val lastContent =
        if (author != null && noteEvent != null) {
            val authorName by observeUserName(author, accountViewModel)
            "$authorName: ${noteEvent.content.take(200)}"
        } else {
            stringRes(Res.string.relay_group_no_messages_yet)
        }

    // Collapsed community row: light the dot when ANY channel in this community has unread messages.
    val hasNewMessages by remember(row.communityId) {
        concordCommunityHasUnreadFlow(accountViewModel.account, row.communityId)
    }.collectAsStateWithLifecycle(false)

    ChannelName(
        channelIdHex = row.communityId,
        channelPicture = rememberConcordImageModel(metadata?.icon, accountViewModel),
        channelTitle = { modifier -> ChannelTitleWithLabelInfo(name, MaterialSymbols.Group, R.string.concord_server_label, modifier) },
        channelLastTime = row.newestMessage?.createdAt(),
        channelLastContent = lastContent,
        hasNewMessages = hasNewMessages,
        loadProfilePicture = accountViewModel.settings.showProfilePictures(),
        loadRobohash = accountViewModel.settings.isNotPerformanceMode(),
        autoPlayGif =
            accountViewModel.settings.autoPlayVideosFlow
                .collectAsStateWithLifecycle()
                .value,
        onClick = { nav.nav(Route.ConcordServer(row.communityId)) },
    )
}

/**
 * Renders a Messages row title as the channel name followed by a muted [HeaderPill] naming the room
 * type (Public Chat, Marmot Group, ...). The pill mirrors the Concord community chip so every group
 * kind reads the same way across the screen: bold name, then a faint rounded chip with a type icon
 * and short label. The name yields space to the chip so a long title can't crowd it out.
 */
@Composable
private fun ChannelTitleWithLabelInfo(
    channelName: String,
    labelIcon: MaterialSymbol,
    label: Int,
    modifier: Modifier,
    labelContentDescription: String? = null,
) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = modifier) {
        Text(
            text = channelName,
            fontWeight = FontWeight.Bold,
            style = LocalTextStyle.current.copy(textDirection = TextDirection.Content),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
        Spacer(Modifier.width(6.dp))
        HeaderPill(
            symbol = labelIcon,
            text = stringRes(id = label),
            modifier = Modifier.widthIn(max = ChatLabelMaxWidth),
            contentDescription = labelContentDescription,
        )
    }
}

/**
 * A warning glyph on the row of a 1:1 room whose counterpart has been reported by someone the user
 * follows. This is the surface where the user decides whether to open an unsolicited DM at all, so it
 * warns before engagement rather than after. Group rooms render nothing.
 */
@Composable
private fun RoomReportWarningIcon(
    preloadedUser: User?,
    accountViewModel: AccountViewModel,
) {
    val user = preloadedUser ?: return
    val flow = remember(user) { accountViewModel.createUserReportWarningFlow(user) }
    val state by flow.collectAsStateWithLifecycle()

    if (state.shouldWarn) {
        Icon(
            symbol = MaterialSymbols.Warning,
            contentDescription = reportWarningContentDescription(state),
            modifier = Size15Modifier,
            tint = MaterialTheme.colorScheme.error,
        )
        Spacer(modifier = StdHorzSpacer)
    }
}

@Composable
private fun UserRoomCompose(
    room: ChatroomKey,
    lastMessage: Note,
    isDraft: Boolean,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    var popupExpanded by remember { mutableStateOf(false) }
    // Kept as a State (no `by`) so `.value` is only read inside the firstRow and
    // menu-text slots, confining pin-toggle invalidations to those scopes.
    val pinnedRooms = accountViewModel.pinnedChatroomsFlow().collectAsStateWithLifecycle()

    ChatHeaderLayout(
        channelPicture = {
            NonClickableUserPictures(
                room = room,
                accountViewModel = accountViewModel,
                size = Size55dp,
            )
        },
        firstRow = {
            val counterpartHex = room.users.singleOrNull()
            if (counterpartHex != null) {
                // 1:1 room: resolve the counterpart once and share it between the name and the
                // report-warning icon below, instead of each doing its own LoadUser.
                LoadUser(baseUserHex = counterpartHex, accountViewModel = accountViewModel) { counterpart ->
                    RoomNameDisplay(room, Modifier.weight(1f), accountViewModel, preloadedUser = counterpart)
                    RoomReportWarningIcon(counterpart, accountViewModel)
                }
            } else {
                RoomNameDisplay(room, Modifier.weight(1f), accountViewModel, preloadedUser = null)
            }
            if (room in pinnedRooms.value) {
                Icon(
                    symbol = MaterialSymbols.PushPin,
                    contentDescription = stringRes(Res.string.pinned_to_top),
                    modifier = Size15Modifier,
                    tint = MaterialTheme.colorScheme.placeholderText,
                )
                Spacer(modifier = StdHorzSpacer)
            }
            TimeAgo(lastMessage.createdAt())
        },
        secondRow = {
            LastMessagePreview(lastMessage, accountViewModel)

            // A sent message I authored counts as read (#1286, #1287); an unsent draft still needs my attention.
            val newestEvent = lastMessage.event
            val countsAsRead =
                !isDraft &&
                    newestEvent != null &&
                    chatMessageMarksRoomAsRead(newestEvent, room, accountViewModel.account.signer.pubKey)

            val lastReadTime by accountViewModel.account.loadLastReadFlow(privateChatLastReadRoute(room)).collectAsStateWithLifecycle()
            if (!countsAsRead && (lastMessage.createdAt() ?: Long.MIN_VALUE) > lastReadTime) {
                Spacer(modifier = Height4dpModifier)
                NewItemsBubble()
            }
        },
        onClick = { nav.nav(Route.Room(room)) },
        onLongClick = { popupExpanded = true },
    )

    DropdownMenu(
        expanded = popupExpanded,
        onDismissRequest = { popupExpanded = false },
    ) {
        DropdownMenuItem(
            text = {
                Text(stringRes(if (room in pinnedRooms.value) R.string.unpin_conversation else R.string.pin_conversation))
            },
            onClick = {
                accountViewModel.toggleChatroomPin(room)
                popupExpanded = false
            },
        )
    }
}

/**
 * The one-line preview of the room's newest message.
 *
 * NIP-04 rooms carry ciphertext in `event.content`, so the preview may only ever come from the
 * decryption cache. [chatPreviewOf] keeps the three not-a-body outcomes apart — still decrypting,
 * never decryptable, and no event at all — so a message that simply hasn't been opened yet isn't
 * mislabelled as unreadable. The pending state resolves on its own: [LoadDecryptedContentOrNull]
 * pushes the plaintext into its state as soon as the signer answers.
 */
@Composable
private fun RowScope.LastMessagePreview(
    lastMessage: Note,
    accountViewModel: AccountViewModel,
) {
    LoadDecryptedContentOrNull(lastMessage, accountViewModel) { content ->
        // Keyed so a scrolling list doesn't re-scan the DM's `p` tags on every recomposition.
        val preview =
            remember(lastMessage.event, content) {
                chatPreviewOf(
                    event = lastMessage.event,
                    decrypted = content,
                    myPubKey = accountViewModel.account.signer.pubKey,
                    canDecrypt = accountViewModel.account.isWriteable(),
                )
            }

        val text =
            when (preview) {
                is ChatPreview.Body -> {
                    // Mark my own newest message with a "You:" prefix (like other messengers) so a
                    // 1:1 room's preview shows who spoke last instead of reading like the counterpart.
                    val sentByMe = lastMessage.author?.pubkeyHex == accountViewModel.account.signer.pubKey
                    if (sentByMe) stringRes(Res.string.chat_preview_you_prefix, preview.text) else preview.text
                }
                ChatPreview.Decrypting -> stringRes(Res.string.chat_preview_decrypting)
                ChatPreview.Undecryptable -> stringRes(Res.string.could_not_decrypt_the_message)
                ChatPreview.Missing -> stringRes(Res.string.referenced_event_not_found)
            }

        Text(
            text,
            color = MaterialTheme.colorScheme.grayText,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.bodyMedium.copy(textDirection = TextDirection.Content),
            modifier = Modifier.weight(1f),
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
    autoPlayGif: Boolean,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
) {
    ChannelName(
        channelPicture = {
            RobohashFallbackAsyncImage(
                robot = channelIdHex,
                model = channelPicture,
                contentDescription = stringRes(Res.string.channel_image),
                modifier = AccountPictureModifier,
                loadProfilePicture = loadProfilePicture,
                loadRobohash = loadRobohash,
                autoPlayGif = autoPlayGif,
            )
        },
        channelTitle,
        channelLastTime,
        channelLastContent,
        hasNewMessages,
        onClick,
        onLongClick,
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
    onLongClick: (() -> Unit)? = null,
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
                    style = MaterialTheme.typography.bodyMedium.copy(textDirection = TextDirection.Content),
                    modifier = Modifier.weight(1f),
                )
            } else {
                Text(
                    stringRes(Res.string.referenced_event_not_found),
                    color = MaterialTheme.colorScheme.grayText,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodyMedium.copy(textDirection = TextDirection.Content),
                    modifier = Modifier.weight(1f),
                )
            }

            if (hasNewMessages) {
                Spacer(modifier = Height4dpModifier)
                NewItemsBubble()
            }
        },
        onClick = onClick,
        onLongClick = onLongClick,
    )
}

@Composable
private fun TimeAgo(channelLastTime: Long?) {
    if (channelLastTime == null) return
    ToggleableTimeAgoText(
        timestamp = channelLastTime,
        style = TimeAgoStyle.Dotted,
        color = MaterialTheme.colorScheme.grayText,
    )
}

@Composable
fun NewItemsBubble() {
    Box(MaterialTheme.colorScheme.newItemBubbleModifier)
}
