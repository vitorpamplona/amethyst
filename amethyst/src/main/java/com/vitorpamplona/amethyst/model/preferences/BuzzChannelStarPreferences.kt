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
package com.vitorpamplona.amethyst.model.preferences

import android.content.Context
import androidx.compose.runtime.Stable
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.vitorpamplona.amethyst.commons.model.buzz.BuzzChannelStars
import com.vitorpamplona.quartz.utils.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException

/**
 * Device-global persistence for the set of starred Buzz workspace channels ([BuzzChannelStars]),
 * so favorites survive a restart. Mirrors [BuzzWorkspacePreferences]: app-wide (not per-account),
 * loads the saved ids into the singleton on construction, then writes every later change back.
 * Construct once, eagerly.
 */
@Stable
class BuzzChannelStarPreferences(
    private val context: Context,
    private val scope: CoroutineScope,
) {
    init {
        scope.launch {
            restoreFromDisk()
            // drop(1) skips the value present at collection start, which restoreFromDisk already wrote.
            BuzzChannelStars.flow.drop(1).collect { persist(it) }
        }
    }

    private suspend fun restoreFromDisk() {
        try {
            val raw = context.sharedPreferencesDataStore.data.first()[KEY] ?: return
            if (raw.isNotEmpty()) BuzzChannelStars.restore(raw)
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Log.e("BuzzChannelStarPrefs") { "Error reading starred channels: ${e.message}" }
        }
    }

    private suspend fun persist(ids: Set<String>) {
        try {
            context.sharedPreferencesDataStore.edit { prefs -> prefs[KEY] = ids }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Log.e("BuzzChannelStarPrefs") { "Error writing starred channels: ${e.message}" }
        }
    }

    companion object {
        private val KEY = stringSetPreferencesKey("buzz.starredChannels")
    }
}
