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
package com.vitorpamplona.amethyst.commons.model.preferences

import androidx.compose.runtime.Stable
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import com.vitorpamplona.quartz.utils.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException

/**
 * Device-global persistence for the docked notification panel's collapse: on a wide window the
 * user can fold the panel away to just its bell, and the window comes back folded the way they
 * left it instead of reopening on every launch. A per-device view choice, so it is never
 * published to relays.
 *
 * Mirrors [DrawerSectionCollapsePreferences]: app-wide (not per-account), loads the saved flag
 * on construction (build it eagerly at startup), then writes every later change back.
 *
 * [collapsedAt] — the moment the current fold began, which the collapsed bell's missed-count
 * badge counts from — is deliberately in-memory only: a restart reopens the fold with an empty
 * badge rather than greeting the user with everything that arrived while the app was closed,
 * and expanding always resets the count by clearing it.
 */
@Stable
class NotificationPanelCollapsePreferences(
    private val store: DataStore<Preferences>,
    scope: CoroutineScope,
) {
    private val collapsed = MutableStateFlow(false)

    /** Whether the panel is folded away; the shell collects this to draw the panel or its strip. */
    val flow: StateFlow<Boolean> = collapsed.asStateFlow()

    private val collapseInstant = MutableStateFlow<Long?>(null)

    /** When the current fold began, in epoch seconds; null while expanded. Never written to disk. */
    val collapsedAt: StateFlow<Long?> = collapseInstant.asStateFlow()

    init {
        scope.launch {
            restoreFromDisk()
            // drop(1) skips the value present at collection start, which restoreFromDisk already read.
            collapsed.drop(1).collect { persist(it) }
        }
    }

    /** Folds the panel, stamping the fold so the missed-count badge counts from [atSeconds]. */
    fun collapse(atSeconds: Long) {
        collapseInstant.value = atSeconds
        collapsed.value = true
    }

    /** Opens the panel back up, resetting the missed count by clearing the fold's instant. */
    fun expand() {
        collapsed.value = false
        collapseInstant.value = null
    }

    private suspend fun restoreFromDisk() {
        try {
            collapsed.value = store.data.first()[KEY] ?: false
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Log.e("NotifPanelCollapsePrefs") { "Error reading notification panel collapse: ${e.message}" }
        }
    }

    private suspend fun persist(value: Boolean) {
        try {
            store.edit { prefs -> prefs[KEY] = value }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Log.e("NotifPanelCollapsePrefs") { "Error writing notification panel collapse: ${e.message}" }
        }
    }

    companion object {
        private val KEY = booleanPreferencesKey("ui.notificationPanel.collapsed")
    }
}
