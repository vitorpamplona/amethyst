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
import androidx.datastore.preferences.core.stringPreferencesKey
import com.vitorpamplona.amethyst.commons.model.buzz.BuzzHeldAttestations
import com.vitorpamplona.quartz.buzz.oaOwnerAttestation.OwnerAttestation
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.utils.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.coroutines.cancellation.CancellationException

/**
 * Device-global persistence for the NIP-OA attestations this device holds
 * ([BuzzHeldAttestations]), so a held credential survives an app restart instead of
 * needing to be re-pasted. Uses the app-wide [sharedPreferencesDataStore] like
 * [NamecoinSharedPreferences] (not per-account — the store is already keyed by the agent
 * pubkey each attestation authorizes).
 *
 * On construction it loads the saved entries into the singleton — **re-verifying each
 * against its agent key**, so a tampered on-disk credential is dropped rather than trusted
 * — then mirrors every later change back to disk. Construct once, eagerly, at startup.
 */
@Stable
class BuzzAttestationPreferences(
    private val context: Context,
    private val scope: CoroutineScope,
) {
    private val json = Json { ignoreUnknownKeys = true }

    @Serializable
    private data class Entry(
        val agent: HexKey,
        val owner: HexKey,
        val conditions: String,
        val sig: HexKey,
    )

    init {
        scope.launch {
            restoreFromDisk()
            // Persist on every change AFTER the initial restore (drop(1) skips the value
            // present at collection start, which restoreFromDisk already wrote).
            BuzzHeldAttestations.flow.drop(1).collect { persist(it) }
        }
    }

    private suspend fun restoreFromDisk() {
        try {
            val raw = context.sharedPreferencesDataStore.data.first()[KEY] ?: return
            val verified =
                json
                    .decodeFromString<List<Entry>>(raw)
                    .mapNotNull { e ->
                        val attestation = OwnerAttestation(e.owner, e.conditions, e.sig)
                        // Only reinstate a credential that still verifies for its agent key.
                        if (attestation.verify(e.agent)) e.agent to attestation else null
                    }.toMap()
            if (verified.isNotEmpty()) BuzzHeldAttestations.restore(verified)
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Log.e("BuzzAttestationPrefs") { "Error reading held attestations: ${e.message}" }
        }
    }

    private suspend fun persist(entries: Map<HexKey, OwnerAttestation>) {
        try {
            val list = entries.map { (agent, a) -> Entry(agent, a.ownerPubKey, a.conditions, a.sig) }
            context.sharedPreferencesDataStore.edit { prefs ->
                prefs[KEY] = json.encodeToString(list)
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Log.e("BuzzAttestationPrefs") { "Error writing held attestations: ${e.message}" }
        }
    }

    companion object {
        private val KEY = stringPreferencesKey("buzz.heldAttestations")
    }
}
