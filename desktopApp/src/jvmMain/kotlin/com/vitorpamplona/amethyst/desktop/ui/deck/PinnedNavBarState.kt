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
package com.vitorpamplona.amethyst.desktop.ui.deck

import com.vitorpamplona.amethyst.desktop.DesktopPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Manages which screens are pinned to the navigation sidebar.
 * Persists to DesktopPreferences as CSV of typeKey strings.
 */
class PinnedNavBarState {
    private val _pinnedScreens = MutableStateFlow(DEFAULT_PINNED)
    val pinnedScreens: StateFlow<List<DeckColumnType>> = _pinnedScreens.asStateFlow()

    fun isPinned(type: DeckColumnType): Boolean = _pinnedScreens.value.any { it.typeKey() == type.typeKey() }

    fun pin(type: DeckColumnType) {
        if (isPinned(type)) return
        if (!isPinnable(type)) return
        _pinnedScreens.update { it + type }
        save()
    }

    fun unpin(type: DeckColumnType) {
        if (!isUnpinnable(type)) return
        _pinnedScreens.update { current -> current.filter { it.typeKey() != type.typeKey() } }
        save()
    }

    fun move(
        fromIndex: Int,
        toIndex: Int,
    ) {
        _pinnedScreens.update { current ->
            if (fromIndex !in current.indices || toIndex !in current.indices) return@update current
            val mutable = current.toMutableList()
            val item = mutable.removeAt(fromIndex)
            mutable.add(toIndex, item)
            mutable.toList()
        }
        save()
    }

    fun save() {
        DesktopPreferences.pinnedNavItems = _pinnedScreens.value.joinToString(",") { it.typeKey() }
    }

    fun load() {
        val raw = DesktopPreferences.pinnedNavItems
        if (raw.isBlank()) {
            _pinnedScreens.value = DEFAULT_PINNED
            return
        }
        val keys = raw.split(",").filter { it.isNotBlank() }
        val screens =
            keys.mapNotNull { key ->
                PINNABLE_SCREENS.find { it.typeKey() == key }
            }
        _pinnedScreens.value = screens.ifEmpty { DEFAULT_PINNED }
    }

    companion object {
        // Only object types are pinnable (no parameterized types like Hashtag, Editor)
        val PINNABLE_SCREENS: List<DeckColumnType> =
            LAUNCHABLE_SCREENS.filter { !it.requiresInput() && it !is DeckColumnType.Editor }

        val DEFAULT_PINNED: List<DeckColumnType> =
            listOf(
                DeckColumnType.HomeFeed,
                DeckColumnType.Reads,
                DeckColumnType.Drafts,
                DeckColumnType.MyHighlights,
                DeckColumnType.Search,
                DeckColumnType.Bookmarks,
                DeckColumnType.Messages,
                DeckColumnType.Notifications,
                DeckColumnType.MyProfile,
                DeckColumnType.Chess,
                DeckColumnType.Settings,
            )

        // These screens cannot be unpinned
        private val ALWAYS_PINNED = setOf("home", "settings")

        fun isPinnable(type: DeckColumnType): Boolean = PINNABLE_SCREENS.any { it.typeKey() == type.typeKey() }

        fun isUnpinnable(type: DeckColumnType): Boolean = type.typeKey() !in ALWAYS_PINNED
    }
}
