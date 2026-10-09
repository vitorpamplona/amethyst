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

import com.vitorpamplona.amethyst.commons.feeds.custom.FeedDefinitionSerializer
import com.vitorpamplona.amethyst.commons.feeds.custom.FeedSource
import com.vitorpamplona.amethyst.commons.model.AccountSettings
import com.vitorpamplona.quartz.utils.Log
import java.io.File
import java.util.prefs.Preferences

/**
 * The legacy desktop app kept its custom feeds in java.util.prefs, shared by every login. The
 * first account that loads after the upgrade takes them, once; the legacy node is left as it is.
 * Only the filter feeds come over: the legacy list, interest-set, DVM, relay, Following and Global
 * entries are already in the Home picker.
 */
class LegacyCustomFeedImport(
    filesDir: File,
) {
    private val marker = File(filesDir, "legacy-desktop-feeds.imported")

    @Synchronized
    fun importInto(settings: AccountSettings) {
        // A temporary login saves nothing: importing into it would lose the legacy data for good.
        if (settings.transientAccount || marker.exists()) return
        try {
            val root = Preferences.userRoot()
            val json = if (root.nodeExists(NODE)) root.node(NODE).get(KEY, null) else null
            val known = settings.customFeeds.value.mapTo(HashSet()) { it.id }
            json
                ?.let { FeedDefinitionSerializer.deserializeList(it) }
                .orEmpty()
                .filter { it.source is FeedSource.Filter && it.id !in known }
                .forEach { settings.saveCustomFeed(it) }
            marker.createNewFile()
        } catch (e: Exception) {
            // Left unmarked, so the next start tries again.
            Log.w("LegacyCustomFeedImport", "Could not import the legacy desktop feeds", e)
        }
    }

    private companion object {
        // Where the legacy LocalFeedProvider saved them.
        const val NODE = "amethyst/feeds"
        const val KEY = "custom_feeds_json"
    }
}
