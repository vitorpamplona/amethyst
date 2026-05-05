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

import androidx.compose.runtime.compositionLocalOf
import com.vitorpamplona.amethyst.commons.feeds.custom.FeedDefinitionRepository
import com.vitorpamplona.amethyst.commons.feeds.custom.FeedDefinitionSerializer
import com.vitorpamplona.amethyst.commons.feeds.custom.defaultFeeds
import com.vitorpamplona.amethyst.desktop.cache.DesktopLocalCache
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import java.util.prefs.Preferences

private const val FEEDS_PREFS_KEY = "custom_feeds_json"

private val feedPrefs: Preferences by lazy {
    Preferences.userRoot().node("amethyst/feeds")
}

private val defaultRepository by lazy {
    val repo = FeedDefinitionRepository(GlobalScope)

    // Load persisted feeds (or defaults on first run)
    val json = feedPrefs.get(FEEDS_PREFS_KEY, "")
    val persisted = FeedDefinitionSerializer.deserializeList(json)
    if (persisted.isNotEmpty()) {
        repo.load(persisted)
    } else {
        repo.load(defaultFeeds())
    }

    // Auto-persist on every change
    repo.feeds
        .onEach { feeds ->
            val serialized = FeedDefinitionSerializer.serializeList(feeds)
            feedPrefs.put(FEEDS_PREFS_KEY, serialized)
            feedPrefs.flush()
        }.launchIn(GlobalScope)

    repo
}

val LocalFeedRepository =
    compositionLocalOf<FeedDefinitionRepository> {
        defaultRepository
    }

val LocalFeedScope =
    compositionLocalOf<CoroutineScope> {
        GlobalScope
    }

val LocalDesktopCache =
    compositionLocalOf<DesktopLocalCache?> {
        null
    }
