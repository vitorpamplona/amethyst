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
package com.vitorpamplona.amethyst.ui.navigation.routes

import com.vitorpamplona.amethyst.model.Account
import com.vitorpamplona.amethyst.model.LocalCache
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.model.User
import com.vitorpamplona.amethyst.model.emphChat.EphemeralChatChannel
import com.vitorpamplona.amethyst.model.nip28PublicChats.PublicChatChannel
import com.vitorpamplona.amethyst.model.nip53LiveActivities.LiveActivitiesChannel
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.quartz.experimental.ephemChat.chat.RoomId
import com.vitorpamplona.quartz.experimental.publicMessages.PublicMessageEvent
import com.vitorpamplona.quartz.nip01Core.core.AddressableEvent
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip04Dm.messages.PrivateDmEvent
import com.vitorpamplona.quartz.nip10Notes.TextNoteEvent
import com.vitorpamplona.quartz.nip17Dm.base.ChatroomKey
import com.vitorpamplona.quartz.nip17Dm.base.ChatroomKeyable
import com.vitorpamplona.quartz.nip22Comments.CommentEvent
import com.vitorpamplona.quartz.nip28PublicChat.admin.ChannelCreateEvent
import com.vitorpamplona.quartz.nip28PublicChat.base.IsInPublicChatChannel
import com.vitorpamplona.quartz.nip37Drafts.DraftWrapEvent
import com.vitorpamplona.quartz.nip53LiveActivities.chat.LiveActivitiesChatMessageEvent
import com.vitorpamplona.quartz.nip53LiveActivities.streaming.LiveActivitiesEvent
import com.vitorpamplona.quartz.nip59Giftwrap.seals.SealedRumorEvent
import com.vitorpamplona.quartz.nip59Giftwrap.wraps.GiftWrapEvent
import com.vitorpamplona.quartz.nip72ModCommunities.definition.CommunityDefinitionEvent
import com.vitorpamplona.quartz.nip73ExternalIds.location.isGeohashedScoped
import com.vitorpamplona.quartz.nip73ExternalIds.topics.isHashtagScoped
import com.vitorpamplona.quartz.nip89AppHandlers.definition.AppDefinitionEvent

fun routeFor(
    note: Note,
    loggedIn: Account,
): Route? {
    val noteEvent = note.event ?: return Route.EventRedirect(note.idHex)

    return routeFor(noteEvent, loggedIn)
}

fun routeFor(
    noteEvent: Event,
    loggedIn: Account,
): Route? {
    if (noteEvent is DraftWrapEvent) {
        val innerEvent = loggedIn.draftsDecryptionCache.preCachedDraft(noteEvent)

        if (innerEvent is IsInPublicChatChannel) {
            innerEvent.channelId()?.let {
                return Route.PublicChatChannel(it)
            }
        } else if (innerEvent is LiveActivitiesEvent) {
            innerEvent.address().let {
                return Route.LiveActivityChannel(it.kind, it.pubKeyHex, it.dTag)
            }
        } else if (innerEvent is LiveActivitiesChatMessageEvent) {
            innerEvent.activityAddress()?.let {
                return Route.LiveActivityChannel(it.kind, it.pubKeyHex, it.dTag)
            }
        } else if (innerEvent is ChatroomKeyable) {
            val room = innerEvent.chatroomKey(loggedIn.userProfile().pubkeyHex)
            loggedIn.chatroomList.getOrCreatePrivateChatroom(room)
            return Route.Room(room)
        } else if (innerEvent is AddressableEvent) {
            return Route.Note(noteEvent.aTag().toTag())
        } else {
            return Route.Note(noteEvent.id)
        }
    } else if (noteEvent is AppDefinitionEvent) {
        return Route.ContentDiscovery(noteEvent.id)
    } else if (noteEvent is IsInPublicChatChannel) {
        noteEvent.channelId()?.let {
            return Route.PublicChatChannel(it)
        }
    } else if (noteEvent is ChannelCreateEvent) {
        return Route.PublicChatChannel(noteEvent.id)
    } else if (noteEvent is LiveActivitiesEvent) {
        noteEvent.address().let {
            return Route.LiveActivityChannel(it.kind, it.pubKeyHex, it.dTag)
        }
    } else if (noteEvent is LiveActivitiesChatMessageEvent) {
        noteEvent.activityAddress()?.let {
            return Route.LiveActivityChannel(it.kind, it.pubKeyHex, it.dTag)
        }
    } else if (noteEvent is ChatroomKeyable) {
        val room = noteEvent.chatroomKey(loggedIn.userProfile().pubkeyHex)
        loggedIn.chatroomList.getOrCreatePrivateChatroom(room)
        return Route.Room(room)
    } else if (noteEvent is CommunityDefinitionEvent) {
        return Route.Community(noteEvent.kind, noteEvent.pubKey, noteEvent.dTag())
    } else if (noteEvent is GiftWrapEvent) {
        noteEvent.innerEventId?.let {
            return routeFor(LocalCache.getOrCreateNote(it), loggedIn)
        }
    } else if (noteEvent is SealedRumorEvent) {
        noteEvent.innerEventId?.let {
            return routeFor(LocalCache.getOrCreateNote(it), loggedIn)
        }
    } else if (noteEvent is AddressableEvent) {
        return Route.Note(noteEvent.aTag().toTag())
    } else {
        return Route.Note(noteEvent.id)
    }

    return null
}

fun routeToMessage(
    user: HexKey,
    draftMessage: String?,
    replyId: HexKey? = null,
    draftId: HexKey? = null,
    accountViewModel: AccountViewModel,
): Route =
    routeToMessage(
        setOf(user),
        draftMessage,
        replyId,
        draftId,
        accountViewModel,
    )

fun routeToMessage(
    users: Set<HexKey>,
    draftMessage: String?,
    replyId: HexKey? = null,
    draftId: HexKey? = null,
    accountViewModel: AccountViewModel,
) = routeToMessage(
    ChatroomKey(users),
    draftMessage,
    replyId,
    draftId,
    accountViewModel,
)

fun routeToMessage(
    room: ChatroomKey,
    draftMessage: String?,
    replyId: HexKey? = null,
    draftId: HexKey? = null,
    accountViewModel: AccountViewModel,
): Route = routeToMessage(room, draftMessage, replyId, draftId, accountViewModel.account)

fun routeToMessage(
    room: ChatroomKey,
    draftMessage: String? = null,
    replyId: HexKey? = null,
    draftId: HexKey? = null,
    account: Account,
): Route {
    account.chatroomList.getOrCreatePrivateChatroom(room)

    return Route.Room(room, draftMessage, replyId, draftId)
}

fun routeToMessage(
    user: User,
    draftMessage: String?,
    replyId: HexKey? = null,
    draftId: HexKey? = null,
    accountViewModel: AccountViewModel,
): Route = routeToMessage(user.pubkeyHex, draftMessage, replyId, draftId, accountViewModel)

fun routeFor(note: EphemeralChatChannel): Route = Route.EphemeralChat(note.roomId.id, note.roomId.relayUrl.url)

fun routeFor(note: PublicChatChannel): Route = Route.PublicChatChannel(note.idHex)

fun routeFor(note: LiveActivitiesChannel): Route = Route.LiveActivityChannel(note.address.kind, note.address.pubKeyHex, note.address.dTag)

fun routeFor(roomId: RoomId): Route = Route.EphemeralChat(roomId.id, roomId.relayUrl.url)

fun routeFor(user: User): Route.Profile = Route.Profile(user.pubkeyHex)

fun authorRouteFor(note: Note): Route.Profile? = note.author?.pubkeyHex?.let { Route.Profile(it) }

fun routeReplyTo(
    note: Note,
    account: Account,
): Route? {
    val noteEvent = note.event
    return when (noteEvent) {
        is PublicMessageEvent ->
            Route.NewPublicMessage(
                users = noteEvent.groupKeySet() - account.userProfile().pubkeyHex,
                parentId = noteEvent.id,
            )
        is TextNoteEvent -> Route.NewPost(baseReplyTo = note.idHex)
        is PrivateDmEvent ->
            routeToMessage(
                room = noteEvent.chatroomKey(account.userProfile().pubkeyHex),
                draftMessage = null,
                replyId = noteEvent.id,
                draftId = null,
                account = account,
            )
        is ChatroomKeyable ->
            routeToMessage(
                room = noteEvent.chatroomKey(account.userProfile().pubkeyHex),
                draftMessage = null,
                replyId = noteEvent.id,
                draftId = null,
                account = account,
            )
        is CommentEvent -> {
            if (noteEvent.isGeohashedScoped()) {
                Route.GeoPost(replyTo = note.idHex)
            } else if (noteEvent.isHashtagScoped()) {
                Route.HashtagPost(replyTo = note.idHex)
            } else {
                Route.GenericCommentPost(replyTo = note.idHex)
            }
        }

        else -> Route.GenericCommentPost(replyTo = note.idHex)
    }
}
