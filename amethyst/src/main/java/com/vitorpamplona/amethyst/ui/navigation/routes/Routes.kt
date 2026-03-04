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
package com.vitorpamplona.amethyst.ui.navigation.routes

import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavHostController
import androidx.navigation.toRoute
import com.vitorpamplona.amethyst.ui.screen.loggedIn.bookmarkgroups.BookmarkType
import com.vitorpamplona.quartz.nip01Core.core.Address
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip17Dm.base.ChatroomKey
import kotlinx.serialization.Serializable
import kotlin.reflect.KClass

sealed class Route {
    @Serializable object Home : Route()

    @Serializable object Message : Route()

    @Serializable object Video : Route()

    @Serializable object Discover : Route()

    @Serializable object Notification : Route()

    @Serializable object Chess : Route()

    @Serializable object Search : Route()

    @Serializable object SecurityFilters : Route()

    @Serializable object PrivacyOptions : Route()

    @Serializable object Bookmarks : Route()

    @Serializable object BookmarkGroups : Route()

    @Serializable data class BookmarkGroupView(
        val dTag: String,
        val bookmarkType: BookmarkType,
    ) : Route()

    @Serializable data class BookmarkGroupMetadataEdit(
        val dTag: String? = null,
    ) : Route()

    @Serializable data class PostBookmarkManagement(
        val postId: String,
    ) : Route()

    @Serializable data class ArticleBookmarkManagement(
        val kind: Int,
        val pubKeyHex: HexKey,
        val dTag: String,
    ) : Route() {
        constructor(address: Address) : this(
            kind = address.kind,
            pubKeyHex = address.pubKeyHex,
            dTag = address.dTag,
        )
    }

    @Serializable object Drafts : Route()

    @Serializable object AllSettings : Route()

    @Serializable object AccountBackup : Route()

    @Serializable object Settings : Route()

    @Serializable object UserSettings : Route()

    @Serializable object Lists : Route()

    @Serializable data class MyPeopleListView(
        val dTag: String,
    ) : Route()

    @Serializable data class MyFollowPackView(
        val dTag: String,
    ) : Route()

    @Serializable data class PeopleListMetadataEdit(
        val dTag: String? = null,
    ) : Route()

    @Serializable data class FollowPackMetadataEdit(
        val dTag: String? = null,
    ) : Route()

    @Serializable data class PeopleListManagement(
        val userToAdd: HexKey,
    ) : Route()

    @Serializable object EditProfile : Route()

    @Serializable object EditRelays : Route()

    @Serializable object EditMediaServers : Route()

    @Serializable object UpdateReactionType : Route()

    @Serializable data class Nip47NWCSetup(
        val nip47: String? = null,
    ) : Route()

    @Serializable data class UpdateZapAmount(
        val nip47: String? = null,
    ) : Route()

    @Serializable data class Profile(
        val id: String,
    ) : Route()

    @Serializable data class QRDisplay(
        val pubkey: String,
    ) : Route()

    @Serializable data class ContentDiscovery(
        val id: String,
    ) : Route()

    @Serializable data class Note(
        val id: String,
    ) : Route()

    @Serializable data class Hashtag(
        val hashtag: String,
    ) : Route()

    @Serializable data class Geohash(
        val geohash: String,
    ) : Route()

    @Serializable data class ChessGame(
        val gameId: String,
    ) : Route()

    @Serializable data class Community(
        val kind: Int,
        val pubKeyHex: HexKey,
        val dTag: String,
    ) : Route() {
        constructor(address: Address) : this(
            kind = address.kind,
            pubKeyHex = address.pubKeyHex,
            dTag = address.dTag,
        )
    }

    @Serializable data class FollowPack(
        val kind: Int,
        val pubKeyHex: HexKey,
        val dTag: String,
    ) : Route() {
        constructor(address: Address) : this(
            kind = address.kind,
            pubKeyHex = address.pubKeyHex,
            dTag = address.dTag,
        )
    }

    @Serializable data class PublicChatChannel(
        val id: String,
        val draftId: HexKey? = null,
        val replyTo: HexKey? = null,
    ) : Route()

    @Serializable data class LiveActivityChannel(
        val kind: Int,
        val pubKeyHex: HexKey,
        val dTag: String,
        val draftId: HexKey? = null,
        val replyTo: HexKey? = null,
    ) : Route()

    @Serializable data class RelayInfo(
        val url: String,
    ) : Route()

    @Serializable data class RelayFeed(
        val url: String,
    ) : Route()

    @Serializable data class EphemeralChat(
        val id: String,
        val relayUrl: String,
        val draftId: HexKey? = null,
        val replyTo: HexKey? = null,
    ) : Route()

    @Serializable object NewEphemeralChat : Route()

    @Serializable data class ChannelMetadataEdit(
        val id: String? = null,
    ) : Route()

    @Serializable data class NewGroupDM(
        val message: String? = null,
        val attachment: String? = null,
    ) : Route()

    @Serializable data class Room(
        val id: String,
        val message: String? = null,
        val replyId: HexKey? = null,
        val draftId: HexKey? = null,
        val expiresDays: Int? = null,
    ) : Route() {
        constructor(key: ChatroomKey, message: String? = null, replyId: HexKey? = null, draftId: HexKey? = null, expiresDays: Int? = null) : this(
            id = key.users.joinToString(","),
            message = message,
            replyId = replyId,
            draftId = draftId,
            expiresDays = expiresDays,
        )

        fun toKey(): ChatroomKey = ChatroomKey(id.split(",").toSet())
    }

    @Serializable data class NewPublicMessage(
        val to: String,
        val replyId: HexKey? = null,
        val draftId: HexKey? = null,
    ) : Route() {
        constructor(users: Set<HexKey>, parentId: HexKey, draftId: HexKey? = null) : this(
            to = users.joinToString(","),
            replyId = parentId,
            draftId = draftId,
        )

        fun toKey(): Set<HexKey> = to.split(",").toSet()
    }

    @Serializable data class RoomByAuthor(
        val id: String,
    ) : Route()

    @Serializable data class EventRedirect(
        val id: String,
    ) : Route()

    @Serializable
    data class NewProduct(
        val message: String? = null,
        val attachment: String? = null,
        val quote: String? = null,
        val draft: String? = null,
    ) : Route()

    @Serializable
    data class GeoPost(
        val geohash: String? = null,
        val message: String? = null,
        val attachment: String? = null,
        val replyTo: String? = null,
        val quote: String? = null,
        val draft: String? = null,
    ) : Route()

    @Serializable
    data class HashtagPost(
        val hashtag: String? = null,
        val message: String? = null,
        val attachment: String? = null,
        val replyTo: String? = null,
        val quote: String? = null,
        val draft: String? = null,
    ) : Route()

    @Serializable
    data class GenericCommentPost(
        val message: String? = null,
        val attachment: String? = null,
        val replyTo: String? = null,
        val quote: String? = null,
        val draft: String? = null,
    ) : Route()

    @Serializable
    data class NewShortNote(
        val message: String? = null,
        val attachment: String? = null,
        val baseReplyTo: String? = null,
        val quote: String? = null,
        val fork: String? = null,
        val version: String? = null,
        val draft: String? = null,
    ) : Route()

    @Serializable
    data class VoiceReply(
        val replyToNoteId: String,
        val recordingFilePath: String,
        val mimeType: String,
        val duration: Int,
        val amplitudes: String, // JSON-encoded List<Float>
    ) : Route()

    @Serializable
    data class ManualZapSplitPayment(
        val paymentId: String,
    ) : Route()
}

inline fun <reified T : Route> isBaseRoute(navController: NavHostController): Boolean = navController.currentBackStackEntry?.destination?.hasRoute<T>() == true

fun <T : Route> getRouteWithArguments(
    klazz: KClass<T>,
    navController: NavHostController,
): Route? {
    val entry = navController.currentBackStackEntry ?: return null
    val dest = entry.destination

    return if (dest.hasRoute(klazz)) {
        entry.toRoute(klazz)
    } else {
        null
    }
}

fun isSameRoute(
    currentRoute: Route?,
    newRoute: Route,
): Boolean {
    if (currentRoute == null) return false

    if (currentRoute == newRoute) {
        return true
    }

    if (newRoute is Route.EventRedirect) {
        return when (currentRoute) {
            is Route.Note -> newRoute.id == currentRoute.id
            is Route.PublicChatChannel -> newRoute.id == currentRoute.id
            else -> false
        }
    }

    return false
}
