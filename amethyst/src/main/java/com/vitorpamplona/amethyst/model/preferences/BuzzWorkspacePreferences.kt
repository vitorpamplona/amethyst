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
import com.vitorpamplona.amethyst.commons.model.buzz.BuzzWorkspaces
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer
import com.vitorpamplona.quartz.utils.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException

/**
 * Device-global persistence for the set of joined `block/buzz` workspaces ([BuzzWorkspaces]),
 * so the app knows which relays to connect + NIP-42-authenticate + run member-channel discovery
 * against on a cold start — Buzz membership is server-side (granted by the HTTP invite claim),
 * with no NIP-51/kind-10009 join event to rebuild the set from. Uses the app-wide
 * [sharedPreferencesDataStore] like [BuzzAttestationPreferences] (not per-account: a joined
 * relay is workspace-wide, and restoring only marks relays to sync — the relay still gates every
 * read/write by the authenticated key).
 *
 * On construction it loads the saved relay URLs into the singleton (re-normalizing each, dropping
 * any that no longer parse), then mirrors every later change back to disk. Construct once, eagerly.
 */
@Stable
class BuzzWorkspacePreferences(
    private val context: Context,
    private val scope: CoroutineScope,
) {
    init {
        scope.launch {
            restoreFromDisk()
            // Persist on every change AFTER the initial restore (drop(1) skips the value present
            // at collection start, which restoreFromDisk already wrote).
            BuzzWorkspaces.flow.drop(1).collect { persist(it) }
        }
    }

    private suspend fun restoreFromDisk() {
        try {
            val raw = context.sharedPreferencesDataStore.data.first()[KEY] ?: return
            val relays = raw.mapNotNull { RelayUrlNormalizer.normalizeOrNull(it) }.toSet()
            if (relays.isNotEmpty()) BuzzWorkspaces.restore(relays)
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Log.e("BuzzWorkspacePrefs") { "Error reading joined workspaces: ${e.message}" }
        }
    }

    private suspend fun persist(relays: Set<NormalizedRelayUrl>) {
        try {
            context.sharedPreferencesDataStore.edit { prefs ->
                prefs[KEY] = relays.map { it.url }.toSet()
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Log.e("BuzzWorkspacePrefs") { "Error writing joined workspaces: ${e.message}" }
        }
    }

    companion object {
        private val KEY = stringSetPreferencesKey("buzz.joinedWorkspaces")
    }
}
