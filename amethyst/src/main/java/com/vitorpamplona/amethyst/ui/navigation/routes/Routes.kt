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

    @Serializable data class Nip47NWCSetup(
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

fun getRouteWithArguments(navController: NavHostController): Route? {
    val entry = navController.currentBackStackEntry ?: return null
    val dest = entry.destination

    return when {
        dest.hasRoute<Route.Home>() -> entry.toRoute<Route.Home>()
        dest.hasRoute<Route.Message>() -> entry.toRoute<Route.Message>()
        dest.hasRoute<Route.Video>() -> entry.toRoute<Route.Video>()
        dest.hasRoute<Route.Discover>() -> entry.toRoute<Route.Discover>()
        dest.hasRoute<Route.Notification>() -> entry.toRoute<Route.Notification>()
        dest.hasRoute<Route.Chess>() -> entry.toRoute<Route.Chess>()
        dest.hasRoute<Route.Search>() -> entry.toRoute<Route.Search>()
        dest.hasRoute<Route.SecurityFilters>() -> entry.toRoute<Route.SecurityFilters>()
        dest.hasRoute<Route.PrivacyOptions>() -> entry.toRoute<Route.PrivacyOptions>()
        dest.hasRoute<Route.Bookmarks>() -> entry.toRoute<Route.Bookmarks>()
        dest.hasRoute<Route.ContentDiscovery>() -> entry.toRoute<Route.ContentDiscovery>()
        dest.hasRoute<Route.Drafts>() -> entry.toRoute<Route.Drafts>()
        dest.hasRoute<Route.Settings>() -> entry.toRoute<Route.Settings>()
        dest.hasRoute<Route.EditProfile>() -> entry.toRoute<Route.EditProfile>()
        dest.hasRoute<Route.Profile>() -> entry.toRoute<Route.Profile>()
        dest.hasRoute<Route.Note>() -> entry.toRoute<Route.Note>()
        dest.hasRoute<Route.Hashtag>() -> entry.toRoute<Route.Hashtag>()
        dest.hasRoute<Route.Geohash>() -> entry.toRoute<Route.Geohash>()
        dest.hasRoute<Route.Community>() -> entry.toRoute<Route.Community>()
        dest.hasRoute<Route.QRDisplay>() -> entry.toRoute<Route.QRDisplay>()
        dest.hasRoute<Route.RelayInfo>() -> entry.toRoute<Route.RelayInfo>()
        dest.hasRoute<Route.RoomByAuthor>() -> entry.toRoute<Route.RoomByAuthor>()
        dest.hasRoute<Route.PublicChatChannel>() -> entry.toRoute<Route.PublicChatChannel>()
        dest.hasRoute<Route.LiveActivityChannel>() -> entry.toRoute<Route.LiveActivityChannel>()
        dest.hasRoute<Route.ChannelMetadataEdit>() -> entry.toRoute<Route.ChannelMetadataEdit>()
        dest.hasRoute<Route.EphemeralChat>() -> entry.toRoute<Route.EphemeralChat>()
        dest.hasRoute<Route.NewEphemeralChat>() -> entry.toRoute<Route.NewEphemeralChat>()
        dest.hasRoute<Route.EventRedirect>() -> entry.toRoute<Route.EventRedirect>()
        dest.hasRoute<Route.EditRelays>() -> entry.toRoute<Route.EditRelays>()
        dest.hasRoute<Route.EditMediaServers>() -> entry.toRoute<Route.EditMediaServers>()
        dest.hasRoute<Route.Nip47NWCSetup>() -> entry.toRoute<Route.Nip47NWCSetup>()
        dest.hasRoute<Route.Room>() -> entry.toRoute<Route.Room>()
        dest.hasRoute<Route.NewShortNote>() -> entry.toRoute<Route.NewShortNote>()
        dest.hasRoute<Route.VoiceReply>() -> entry.toRoute<Route.VoiceReply>()
        dest.hasRoute<Route.NewProduct>() -> entry.toRoute<Route.NewProduct>()
        dest.hasRoute<Route.GeoPost>() -> entry.toRoute<Route.GeoPost>()
        dest.hasRoute<Route.HashtagPost>() -> entry.toRoute<Route.HashtagPost>()
        dest.hasRoute<Route.GenericCommentPost>() -> entry.toRoute<Route.GenericCommentPost>()
        dest.hasRoute<Route.NewPublicMessage>() -> entry.toRoute<Route.NewPublicMessage>()
        dest.hasRoute<Route.Lists>() -> entry.toRoute<Route.Lists>()
        dest.hasRoute<Route.MyPeopleListView>() -> entry.toRoute<Route.MyPeopleListView>()
        dest.hasRoute<Route.MyFollowPackView>() -> entry.toRoute<Route.MyFollowPackView>()
        dest.hasRoute<Route.PeopleListMetadataEdit>() -> entry.toRoute<Route.PeopleListMetadataEdit>()
        dest.hasRoute<Route.FollowPackMetadataEdit>() -> entry.toRoute<Route.FollowPackMetadataEdit>()
        dest.hasRoute<Route.PeopleListManagement>() -> entry.toRoute<Route.PeopleListManagement>()
        dest.hasRoute<Route.NewGroupDM>() -> entry.toRoute<Route.NewGroupDM>()
        dest.hasRoute<Route.UserSettings>() -> entry.toRoute<Route.UserSettings>()
        dest.hasRoute<Route.ManualZapSplitPayment>() -> entry.toRoute<Route.ManualZapSplitPayment>()
        else -> null
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
