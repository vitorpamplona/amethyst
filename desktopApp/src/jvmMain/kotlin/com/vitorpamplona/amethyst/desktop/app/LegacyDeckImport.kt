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
package com.vitorpamplona.amethyst.desktop.app

import com.vitorpamplona.amethyst.commons.model.AccountSettings
import com.vitorpamplona.amethyst.commons.model.deck.DeckLayout
import com.vitorpamplona.amethyst.commons.model.deck.LegacyDeckParser
import com.vitorpamplona.quartz.utils.Log
import java.io.File
import java.util.prefs.Preferences

/**
 * Brings the legacy desktop app's deck over, once: its workspaces (or, from builds before
 * workspaces, its one list of columns) become the deck of the first account that loads, and a
 * legacy user who ran the deck gets it turned on. The legacy app kept one deck for every login.
 *
 * The columns without a shared screen are listed in [DeckLayout.importNotice] ([LegacyDeckParser])
 * so the user is told once. A custom feed is still in the Home picker.
 */
class LegacyDeckImport(
    filesDir: File,
    private val turnDeckOn: () -> Unit,
) {
    private val marker = File(filesDir, "legacy-desktop-deck.imported")

    @Synchronized
    fun importInto(
        settings: AccountSettings,
        myPubKey: String,
    ) {
        if (marker.exists()) return
        try {
            val root = Preferences.userRoot()
            if (root.nodeExists(NODE)) {
                val prefs = root.node(NODE)
                val feedNames = settings.customFeeds.value.associate { it.id to "${it.emoji} ${it.name}".trim() }
                val layout = LegacyDeckParser.parse(prefs.get(KEY_WORKSPACES, ""), prefs.get(KEY_DECK_COLUMNS, ""), myPubKey) { feedNames[it] }
                if (layout != null) {
                    settings.updateDeck { layout }
                    if (prefs.get(KEY_LAYOUT_MODE, "") == "DECK") turnDeckOn()
                }
            }
            marker.createNewFile()
        } catch (e: Exception) {
            // Left unmarked, so the next start tries again.
            Log.w("LegacyDeckImport", "Could not import the legacy desktop deck", e)
        }
    }

    companion object {
        // DesktopPreferences' node: Preferences.userNodeForPackage of com.vitorpamplona.amethyst.desktop.
        private const val NODE = "com/vitorpamplona/amethyst/desktop"
        private const val KEY_WORKSPACES = "workspaces"
        private const val KEY_DECK_COLUMNS = "deck_columns"
        private const val KEY_LAYOUT_MODE = "layout_mode"
    }
}
