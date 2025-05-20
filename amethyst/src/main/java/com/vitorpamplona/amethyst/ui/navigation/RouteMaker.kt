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
package com.vitorpamplona.amethyst.ui.navigation

import com.vitorpamplona.amethyst.model.Channel
import com.vitorpamplona.amethyst.model.EphemeralChatChannel
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.model.User
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.quartz.experimental.ephemChat.chat.RoomId
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
import com.vitorpamplona.quartz.nip37Drafts.DraftEvent
import com.vitorpamplona.quartz.nip53LiveActivities.chat.LiveActivitiesChatMessageEvent
import com.vitorpamplona.quartz.nip53LiveActivities.streaming.LiveActivitiesEvent
import com.vitorpamplona.quartz.nip72ModCommunities.definition.CommunityDefinitionEvent
import com.vitorpamplona.quartz.nip73ExternalIds.location.isGeohashedScoped
import com.vitorpamplona.quartz.nip73ExternalIds.topics.isHashtagScoped
import com.vitorpamplona.quartz.nip89AppHandlers.definition.AppDefinitionEvent

fun routeFor(
    note: Note,
    loggedIn: User,
): Route? {
    val noteEvent = note.event ?: return Route.Note(note.idHex)

    return routeFor(noteEvent, loggedIn)
}

fun routeFor(
    noteEvent: Event,
    loggedIn: User,
): Route? {
    if (noteEvent is DraftEvent) {
        val innerEvent = noteEvent.preCachedDraft(loggedIn.pubkeyHex)

        if (innerEvent is IsInPublicChatChannel) {
            innerEvent.channelId()?.let {
                return Route.Channel(it)
            }
        } else if (innerEvent is LiveActivitiesEvent) {
            innerEvent.aTag().toTag().let {
                return Route.Channel(it)
            }
        } else if (innerEvent is LiveActivitiesChatMessageEvent) {
            innerEvent.activity()?.toTag()?.let {
                return Route.Channel(it)
            }
        } else if (innerEvent is ChatroomKeyable) {
            val room = innerEvent.chatroomKey(loggedIn.pubkeyHex)
            loggedIn.createChatroom(room)
            return Route.Room(room.hashCode())
        } else if (innerEvent is AddressableEvent) {
            return Route.Note(noteEvent.aTag().toTag())
        } else {
            return Route.Note(noteEvent.id)
        }
    } else if (noteEvent is AppDefinitionEvent) {
        return Route.ContentDiscovery(noteEvent.id)
    } else if (noteEvent is IsInPublicChatChannel) {
        noteEvent.channelId()?.let {
            return Route.Channel(it)
        }
    } else if (noteEvent is ChannelCreateEvent) {
        return Route.Channel(noteEvent.id)
    } else if (noteEvent is LiveActivitiesEvent) {
        noteEvent.aTag().toTag().let {
            return Route.Channel(it)
        }
    } else if (noteEvent is LiveActivitiesChatMessageEvent) {
        noteEvent.activity()?.toTag()?.let {
            return Route.Channel(it)
        }
    } else if (noteEvent is ChatroomKeyable) {
        val room = noteEvent.chatroomKey(loggedIn.pubkeyHex)
        loggedIn.createChatroom(room)
        return Route.Room(room.hashCode())
    } else if (noteEvent is CommunityDefinitionEvent) {
        return Route.Community(noteEvent.aTag().toTag())
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
): Route = routeToMessage(room, draftMessage, replyId, draftId, accountViewModel.userProfile())

fun routeToMessage(
    room: ChatroomKey,
    draftMessage: String?,
    replyId: HexKey? = null,
    draftId: HexKey? = null,
    fromUser: User,
): Route {
    fromUser.createChatroom(room)

    return Route.Room(room.hashCode(), draftMessage, replyId, draftId)
}

fun routeToMessage(
    user: User,
    draftMessage: String?,
    replyId: HexKey? = null,
    draftId: HexKey? = null,
    accountViewModel: AccountViewModel,
): Route = routeToMessage(user.pubkeyHex, draftMessage, replyId, draftId, accountViewModel)

fun routeFor(note: Channel): Route =
    if (note is EphemeralChatChannel) {
        Route.EphemeralChat(note.roomId.id, note.roomId.relayUrl)
    } else {
        Route.Channel(note.idHex)
    }

fun routeFor(roomId: RoomId): Route = Route.EphemeralChat(roomId.id, roomId.relayUrl)

fun routeFor(user: User): Route.Profile = Route.Profile(user.pubkeyHex)

fun authorRouteFor(note: Note): Route.Profile? = note.author?.pubkeyHex?.let { Route.Profile(it) }

fun routeReplyTo(
    note: Note,
    asUser: User,
): Route? {
    val noteEvent = note.event
    return when (noteEvent) {
        is TextNoteEvent -> Route.NewPost(baseReplyTo = note.idHex)
        is PrivateDmEvent ->
            routeToMessage(
                room = noteEvent.chatroomKey(asUser.pubkeyHex),
                draftMessage = null,
                replyId = noteEvent.id,
                draftId = null,
                fromUser = asUser,
            )
        is ChatroomKeyable ->
            routeToMessage(
                room = noteEvent.chatroomKey(asUser.pubkeyHex),
                draftMessage = null,
                replyId = noteEvent.id,
                draftId = null,
                fromUser = asUser,
            )
        is CommentEvent -> {
            if (noteEvent.isGeohashedScoped()) {
                Route.GeoPost(replyTo = note.idHex)
            } else if (noteEvent.isHashtagScoped()) {
                Route.HashtagPost(replyTo = note.idHex)
            } else {
                null
            }
        }

        else -> null
    }
}

fun routeQuote(
    note: Note,
    asUser: User,
): Route? {
    val noteEvent = note.event
    return when (noteEvent) {
        is TextNoteEvent -> Route.NewPost(baseReplyTo = note.idHex)
        is PrivateDmEvent ->
            routeToMessage(
                room = noteEvent.chatroomKey(asUser.pubkeyHex),
                draftMessage = null,
                replyId = noteEvent.id,
                draftId = null,
                fromUser = asUser,
            )
        is ChatroomKeyable ->
            routeToMessage(
                room = noteEvent.chatroomKey(asUser.pubkeyHex),
                draftMessage = null,
                replyId = noteEvent.id,
                draftId = null,
                fromUser = asUser,
            )
        is CommentEvent -> Route.GeoPost(replyTo = note.idHex)

        else -> null
    }
}
