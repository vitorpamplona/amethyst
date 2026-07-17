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
package com.vitorpamplona.amethyst.ui.navigation.bottombars

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * One slot in the bottom navigation bar. A single ordered list of these (persisted in
 * [com.vitorpamplona.amethyst.model.UiSettings.bottomBarItems]) holds built-in destinations,
 * favorite apps, and individual joined chats/groups, so the user can pin and drag-reorder them
 * together in one list.
 *
 * - [BuiltIn] resolves its [Route][com.vitorpamplona.amethyst.ui.navigation.routes.Route] (and its
 *   icon/label/notification badge) through [NavBarCatalog], like before.
 * - [Favorite] points at a [FavoriteApp][com.vitorpamplona.amethyst.commons.favorites.FavoriteApp] by
 *   its stable id (which already encodes the route's parameters — the `url` or addressable
 *   `coordinate`); the bar resolves it to a live favorite for its icon/label and to
 *   `Route.WebApp` / `Route.NostrApp` for navigation.
 * - [PublicChat], [RelayGroup] and [Concord] each pin one specific joined chat the user picked from
 *   their joined list (NIP-28 channel, NIP-29 relay group, or a Concord community). The bar resolves
 *   each to the chat's avatar + name from the local cache and to its chat/home route for navigation.
 */
@Serializable
sealed interface BottomBarEntry {
    // Stable discriminators so persisted bottom-bar configs survive class renames/moves (the default
    // polymorphic discriminator is the fully-qualified class name, which is fragile across refactors).
    @Serializable
    @SerialName("builtIn")
    data class BuiltIn(
        val item: NavBarItem,
    ) : BottomBarEntry

    @Serializable
    @SerialName("favorite")
    data class Favorite(
        val favoriteId: String,
    ) : BottomBarEntry

    /** A pinned NIP-28 public chat channel, keyed by its channel event id (hex). */
    @Serializable
    @SerialName("publicChat")
    data class PublicChat(
        val channelId: String,
    ) : BottomBarEntry

    /** A pinned NIP-29 relay group, keyed by the (group id, host relay) pair — the group's real key. */
    @Serializable
    @SerialName("relayGroup")
    data class RelayGroup(
        val groupId: String,
        val relayUrl: String,
    ) : BottomBarEntry

    /** A pinned Concord community, keyed by its community id; opens the community's channel list. */
    @Serializable
    @SerialName("concord")
    data class Concord(
        val communityId: String,
    ) : BottomBarEntry

    /** A pinned Bitchat geohash location channel, keyed by its geohash cell; opens the location chat. */
    @Serializable
    @SerialName("geohash")
    data class Geohash(
        val geohash: String,
    ) : BottomBarEntry
}

/**
 * Stable, type-discriminated identity for an entry — used as a Compose list key and for membership
 * checks / de-duplication in the settings picker.
 */
val BottomBarEntry.stableKey: String
    get() =
        when (this) {
            is BottomBarEntry.BuiltIn -> "builtIn:${item.name}"
            is BottomBarEntry.Favorite -> "favorite:$favoriteId"
            is BottomBarEntry.PublicChat -> "publicChat:$channelId"
            is BottomBarEntry.RelayGroup -> "relayGroup:$relayUrl|$groupId"
            is BottomBarEntry.Concord -> "concord:$communityId"
            is BottomBarEntry.Geohash -> "geohash:$geohash"
        }

/** The favorite-app ids in this bottom-bar config — the apps that should be kept warm as bottom-row tabs. */
fun List<BottomBarEntry>.favoriteIds(): List<String> = mapNotNull { (it as? BottomBarEntry.Favorite)?.favoriteId }
