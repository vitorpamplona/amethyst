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
package com.vitorpamplona.amethyst.desktop

import com.vitorpamplona.amethyst.desktop.subscriptions.FeedMode
import java.util.prefs.Preferences

/**
 * Simple preferences storage using Java's Preferences API.
 * Data is stored in platform-appropriate location:
 * - macOS: ~/Library/Preferences/com.apple.java.util.prefs.plist
 * - Linux: ~/.java/.userPrefs/
 * - Windows: Registry under HKEY_CURRENT_USER\Software\JavaSoft\Prefs
 */
object DesktopPreferences {
    private val prefs: Preferences = Preferences.userNodeForPackage(DesktopPreferences::class.java)

    private const val KEY_FEED_MODE = "feed_mode"
    private const val KEY_LAST_SCREEN = "last_screen"
    private const val KEY_DECK_COLUMNS = "deck_columns"
    private const val KEY_LAYOUT_MODE = "layout_mode"

    var feedMode: FeedMode
        get() {
            val name = prefs.get(KEY_FEED_MODE, FeedMode.GLOBAL.name)
            return try {
                FeedMode.valueOf(name)
            } catch (e: Exception) {
                FeedMode.GLOBAL
            }
        }
        set(value) {
            prefs.put(KEY_FEED_MODE, value.name)
        }

    var lastScreen: String
        get() = prefs.get(KEY_LAST_SCREEN, "Feed")
        set(value) {
            prefs.put(KEY_LAST_SCREEN, value)
        }

    var deckColumns: String
        get() = prefs.get(KEY_DECK_COLUMNS, "")
        set(value) {
            prefs.put(KEY_DECK_COLUMNS, value)
        }

    var layoutMode: String
        get() = prefs.get(KEY_LAYOUT_MODE, "SINGLE_PANE")
        set(value) {
            prefs.put(KEY_LAYOUT_MODE, value)
        }

    private const val KEY_WORKSPACES = "workspaces"

    var workspaces: String
        get() = prefs.get(KEY_WORKSPACES, "")
        set(value) {
            prefs.put(KEY_WORKSPACES, value)
        }

    private const val KEY_PINNED_NAV_ITEMS = "pinned_nav_items"

    var pinnedNavItems: String
        get() = prefs.get(KEY_PINNED_NAV_ITEMS, "")
        set(value) {
            prefs.put(KEY_PINNED_NAV_ITEMS, value)
        }

    private const val KEY_BLOSSOM_SERVERS = "blossom_servers"
    private const val DEFAULT_BLOSSOM_SERVER = "https://blossom.primal.net"

    var blossomServers: List<String>
        get() {
            val raw = prefs.get(KEY_BLOSSOM_SERVERS, DEFAULT_BLOSSOM_SERVER)
            return if (raw.isBlank()) emptyList() else raw.split(",")
        }
        set(value) = prefs.put(KEY_BLOSSOM_SERVERS, value.joinToString(","))

    val preferredBlossomServer: String
        get() = blossomServers.firstOrNull() ?: DEFAULT_BLOSSOM_SERVER
}
