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

import kotlinx.serialization.Serializable

/**
 * One slot in the bottom navigation bar. A single ordered list of these (persisted in
 * [com.vitorpamplona.amethyst.model.UiSettings.bottomBarItems]) holds **both** built-in destinations
 * and favorite apps, so the user can pin and drag-reorder them together in one list.
 *
 * - [BuiltIn] resolves its [Route][com.vitorpamplona.amethyst.ui.navigation.routes.Route] (and its
 *   icon/label/notification badge) through [NavBarCatalog], like before.
 * - [Favorite] points at a [FavoriteApp][com.vitorpamplona.amethyst.commons.favorites.FavoriteApp] by
 *   its stable id (which already encodes the route's parameters — the `url` or addressable
 *   `coordinate`); the bar resolves it to a live favorite for its icon/label and to
 *   `Route.FavoriteWebApp` / `Route.FavoriteNostrApp` for navigation.
 */
@Serializable
sealed interface BottomBarEntry {
    @Serializable
    data class BuiltIn(
        val item: NavBarItem,
    ) : BottomBarEntry

    @Serializable
    data class Favorite(
        val favoriteId: String,
    ) : BottomBarEntry
}

/** The favorite-app ids in this bottom-bar config — the apps that should be kept warm as bottom-row tabs. */
fun List<BottomBarEntry>.favoriteIds(): List<String> = mapNotNull { (it as? BottomBarEntry.Favorite)?.favoriteId }
