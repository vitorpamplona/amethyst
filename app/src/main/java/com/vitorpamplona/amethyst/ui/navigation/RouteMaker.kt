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
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.model.User
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.quartz.encoders.HexKey
import com.vitorpamplona.quartz.events.AddressableEvent
import com.vitorpamplona.quartz.events.ChannelCreateEvent
import com.vitorpamplona.quartz.events.ChatroomKey
import com.vitorpamplona.quartz.events.ChatroomKeyable
import com.vitorpamplona.quartz.events.CommunityDefinitionEvent
import com.vitorpamplona.quartz.events.DraftEvent
import com.vitorpamplona.quartz.events.EventInterface
import com.vitorpamplona.quartz.events.IsInPublicChatChannel
import com.vitorpamplona.quartz.events.LiveActivitiesChatMessageEvent
import com.vitorpamplona.quartz.events.LiveActivitiesEvent
import kotlinx.collections.immutable.persistentSetOf
import java.net.URLEncoder

fun routeFor(
    note: Note,
    loggedIn: User,
): String? {
    val noteEvent = note.event ?: return "Note/${URLEncoder.encode(note.idHex, "utf-8")}"

    return routeFor(noteEvent, loggedIn)
}

fun routeFor(
    noteEvent: EventInterface,
    loggedIn: User,
): String? {
    if (noteEvent is DraftEvent) {
        val innerEvent = noteEvent.preCachedDraft(loggedIn.pubkeyHex)

        if (innerEvent is IsInPublicChatChannel) {
            innerEvent.channel()?.let {
                return "Channel/$it"
            }
        } else if (innerEvent is LiveActivitiesEvent) {
            innerEvent.address().toTag().let {
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
            return "Note/${URLEncoder.encode(noteEvent.address().toTag(), "utf-8")}"
        } else {
            return "Note/${URLEncoder.encode(noteEvent.id(), "utf-8")}"
        }
    } else if (noteEvent is IsInPublicChatChannel) {
        noteEvent.channel()?.let {
            return "Channel/$it"
        }
    } else if (noteEvent is ChannelCreateEvent) {
        return "Channel/${noteEvent.id}"
    } else if (noteEvent is LiveActivitiesEvent) {
        noteEvent.address().toTag().let {
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
        return "Community/${URLEncoder.encode(noteEvent.address().toTag(), "utf-8")}"
    } else if (noteEvent is AddressableEvent) {
        return "Note/${URLEncoder.encode(noteEvent.address().toTag(), "utf-8")}"
    } else {
        return "Note/${URLEncoder.encode(noteEvent.id(), "utf-8")}"
    }

    return null
}

fun routeToMessage(
    user: HexKey,
    draftMessage: String?,
    accountViewModel: AccountViewModel,
): String {
    val withKey = ChatroomKey(persistentSetOf(user))
    accountViewModel.account.userProfile().createChatroom(withKey)
    return if (draftMessage != null) {
        val encodedMessage = URLEncoder.encode(draftMessage, "utf-8")
        "Room/${withKey.hashCode()}?message=$encodedMessage"
    } else {
        "Room/${withKey.hashCode()}"
    }
}

fun routeToMessage(
    user: User,
    draftMessage: String?,
    accountViewModel: AccountViewModel,
): String {
    return routeToMessage(user.pubkeyHex, draftMessage, accountViewModel)
}

fun routeFor(note: Channel): String {
    return "Channel/${note.idHex}"
}

fun routeFor(user: User): String {
    return "User/${user.pubkeyHex}"
}

fun authorRouteFor(note: Note): String {
    return "User/${note.author?.pubkeyHex}"
}
