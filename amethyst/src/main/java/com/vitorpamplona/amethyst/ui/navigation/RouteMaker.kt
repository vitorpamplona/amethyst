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
package com.vitorpamplona.amethyst.ui.navigation

import com.vitorpamplona.amethyst.model.Channel
import com.vitorpamplona.amethyst.model.LocalCache.users
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.model.User
import com.vitorpamplona.amethyst.service.NostrUserProfileDataSource.user
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.quartz.nip01Core.core.AddressableEvent
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip17Dm.base.ChatroomKey
import com.vitorpamplona.quartz.nip17Dm.base.ChatroomKeyable
import com.vitorpamplona.quartz.nip28PublicChat.admin.ChannelCreateEvent
import com.vitorpamplona.quartz.nip28PublicChat.base.IsInPublicChatChannel
import com.vitorpamplona.quartz.nip37Drafts.DraftEvent
import com.vitorpamplona.quartz.nip53LiveActivities.chat.LiveActivitiesChatMessageEvent
import com.vitorpamplona.quartz.nip53LiveActivities.streaming.LiveActivitiesEvent
import com.vitorpamplona.quartz.nip72ModCommunities.definition.CommunityDefinitionEvent
import com.vitorpamplona.quartz.nip89AppHandlers.definition.AppDefinitionEvent
import java.net.URLEncoder

fun routeFor(
    note: Note,
    loggedIn: User,
): String? {
    val noteEvent = note.event ?: return "Note/${URLEncoder.encode(note.idHex, "utf-8")}"

    return routeFor(noteEvent, loggedIn)
}

fun routeFor(
    noteEvent: Event,
    loggedIn: User,
): String? {
    if (noteEvent is DraftEvent) {
        val innerEvent = noteEvent.preCachedDraft(loggedIn.pubkeyHex)

        if (innerEvent is IsInPublicChatChannel) {
            innerEvent.channelId()?.let {
                return "Channel/$it"
            }
        } else if (innerEvent is LiveActivitiesEvent) {
            innerEvent.aTag().toTag().let {
                return "Channel/${URLEncoder.encode(it, "utf-8")}"
            }
        } else if (innerEvent is LiveActivitiesChatMessageEvent) {
            innerEvent.activity()?.toTag()?.let {
                return "Channel/${URLEncoder.encode(it, "utf-8")}"
            }
        } else if (innerEvent is ChatroomKeyable) {
            val room = innerEvent.chatroomKey(loggedIn.pubkeyHex)
            loggedIn.createChatroom(room)
            return "Room/${room.hashCode()}"
        } else if (innerEvent is AddressableEvent) {
            return "Note/${URLEncoder.encode(noteEvent.aTag().toTag(), "utf-8")}"
        } else {
            return "Note/${URLEncoder.encode(noteEvent.id, "utf-8")}"
        }
    } else if (noteEvent is AppDefinitionEvent) {
        return "ContentDiscovery/${noteEvent.id}"
    } else if (noteEvent is IsInPublicChatChannel) {
        noteEvent.channelId()?.let {
            return "Channel/$it"
        }
    } else if (noteEvent is ChannelCreateEvent) {
        return "Channel/${noteEvent.id}"
    } else if (noteEvent is LiveActivitiesEvent) {
        noteEvent.aTag().toTag().let {
            return "Channel/${URLEncoder.encode(it, "utf-8")}"
        }
    } else if (noteEvent is LiveActivitiesChatMessageEvent) {
        noteEvent.activity()?.toTag()?.let {
            return "Channel/${URLEncoder.encode(it, "utf-8")}"
        }
    } else if (noteEvent is ChatroomKeyable) {
        val room = noteEvent.chatroomKey(loggedIn.pubkeyHex)
        loggedIn.createChatroom(room)
        return "Room/${room.hashCode()}"
    } else if (noteEvent is CommunityDefinitionEvent) {
        return "Community/${URLEncoder.encode(noteEvent.aTag().toTag(), "utf-8")}"
    } else if (noteEvent is AddressableEvent) {
        return "Note/${URLEncoder.encode(noteEvent.aTag().toTag(), "utf-8")}"
    } else {
        return "Note/${URLEncoder.encode(noteEvent.id, "utf-8")}"
    }

    return null
}

fun routeToMessage(
    user: HexKey,
    draftMessage: String?,
    replyId: HexKey? = null,
    quoteId: HexKey? = null,
    accountViewModel: AccountViewModel,
): String =
    routeToMessage(
        setOf(user),
        draftMessage,
        replyId,
        quoteId,
        accountViewModel,
    )

fun routeToMessage(
    users: Set<HexKey>,
    draftMessage: String?,
    replyId: HexKey? = null,
    quoteId: HexKey? = null,
    accountViewModel: AccountViewModel,
) = routeToMessage(
    ChatroomKey(users),
    draftMessage,
    replyId,
    quoteId,
    accountViewModel,
)

fun routeToMessage(
    room: ChatroomKey,
    draftMessage: String?,
    replyId: HexKey? = null,
    quoteId: HexKey? = null,
    accountViewModel: AccountViewModel,
): String {
    accountViewModel.account.userProfile().createChatroom(room)

    val params =
        listOfNotNull(
            draftMessage?.let {
                "message=${URLEncoder.encode(it, "utf-8")}"
            },
            replyId?.let {
                "replyId=${URLEncoder.encode(it, "utf-8")}"
            },
            quoteId?.let {
                "quoteId=${URLEncoder.encode(it, "utf-8")}"
            },
        )

    return buildString {
        append("Room/")
        append(room.hashCode().toString())
        if (params.isNotEmpty()) {
            append("?")
            append(params.joinToString("&"))
        }
    }
}

fun routeToMessage(
    user: User,
    draftMessage: String?,
    replyId: HexKey? = null,
    quoteId: HexKey? = null,
    accountViewModel: AccountViewModel,
): String = routeToMessage(user.pubkeyHex, draftMessage, replyId, quoteId, accountViewModel)

fun routeFor(note: Channel): String = "Channel/${note.idHex}"

fun routeFor(user: User): String = "User/${user.pubkeyHex}"

fun authorRouteFor(note: Note): String = "User/${note.author?.pubkeyHex}"
