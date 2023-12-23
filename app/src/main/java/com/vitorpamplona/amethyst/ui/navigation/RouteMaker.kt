package com.vitorpamplona.amethyst.ui.navigation

import com.vitorpamplona.amethyst.model.Channel
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.model.User
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.quartz.encoders.HexKey
import com.vitorpamplona.quartz.events.ChannelCreateEvent
import com.vitorpamplona.quartz.events.ChannelMessageEvent
import com.vitorpamplona.quartz.events.ChannelMetadataEvent
import com.vitorpamplona.quartz.events.ChatroomKey
import com.vitorpamplona.quartz.events.ChatroomKeyable
import com.vitorpamplona.quartz.events.CommunityDefinitionEvent
import com.vitorpamplona.quartz.events.LiveActivitiesChatMessageEvent
import com.vitorpamplona.quartz.events.LiveActivitiesEvent
import kotlinx.collections.immutable.persistentSetOf
import java.net.URLEncoder

fun routeFor(note: Note, loggedIn: User): String? {
    val noteEvent = note.event

    if (noteEvent is ChannelMessageEvent || noteEvent is ChannelCreateEvent || noteEvent is ChannelMetadataEvent) {
        note.channelHex()?.let {
            return "Channel/$it"
        }
    } else if (noteEvent is LiveActivitiesEvent || noteEvent is LiveActivitiesChatMessageEvent) {
        note.channelHex()?.let {
            return "Channel/${URLEncoder.encode(it, "utf-8")}"
        }
    } else if (noteEvent is ChatroomKeyable) {
        val room = noteEvent.chatroomKey(loggedIn.pubkeyHex)
        loggedIn.createChatroom(room)
        return "Room/${room.hashCode()}"
    } else if (noteEvent is CommunityDefinitionEvent) {
        return "Community/${URLEncoder.encode(note.idHex, "utf-8")}"
    } else {
        return "Note/${URLEncoder.encode(note.idHex, "utf-8")}"
    }

    return null
}

fun routeToMessage(user: HexKey, draftMessage: String?, accountViewModel: AccountViewModel): String {
    val withKey = ChatroomKey(persistentSetOf(user))
    accountViewModel.account.userProfile().createChatroom(withKey)
    return if (draftMessage != null) {
        "Room/${withKey.hashCode()}?message=$draftMessage"
    } else {
        "Room/${withKey.hashCode()}"
    }
}

fun routeToMessage(user: User, draftMessage: String?, accountViewModel: AccountViewModel): String {
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
