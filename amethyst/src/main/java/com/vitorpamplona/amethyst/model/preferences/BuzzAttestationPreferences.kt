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
 * Per-account persistence for the NIP-OA attestation this account holds
 * ([BuzzHeldAttestations]), so a held credential survives an app restart instead of needing to be
 * re-pasted. The key is namespaced by pubkey; the store used to be one device-global list because
 * each entry carried the agent key it authorized, which made the file a per-account store with
 * extra steps.
 *
 * On construction it loads this account's saved attestation and mirrors every later change back to
 * disk. Re-verification on restore is no longer done here: [BuzzHeldAttestations.put] verifies
 * against the agent key itself and rejects what fails, so a tampered on-disk credential is dropped
 * by the same gate that rejects a mistyped one. Construct once per account, eagerly.
 */
@Stable
class BuzzAttestationPreferences(
    private val context: Context,
    private val scope: CoroutineScope,
    private val pubKeyHex: HexKey,
    private val attestation: BuzzHeldAttestations,
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val key = stringPreferencesKey("$KEY_PREFIX$pubKeyHex")

    @Serializable
    private data class Entry(
        val owner: HexKey,
        val conditions: String,
        val sig: HexKey,
    )

    /** The pre-namespacing on-disk shape: one list for the whole device, each entry agent-keyed. */
    @Serializable
    private data class LegacyEntry(
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
            attestation.flow.drop(1).collect { persist(it) }
        }
    }

    private suspend fun restoreFromDisk() {
        try {
            val prefs = context.sharedPreferencesDataStore.data.first()
            // put() verifies, so a credential that no longer checks out is dropped either way.
            val saved = prefs[key]?.let { json.decodeFromString<Entry>(it) }
            if (saved != null) {
                attestation.put(OwnerAttestation(saved.owner, saved.conditions, saved.sig))
                return
            }
            // Nothing under this account's key: pick our entry out of the pre-namespacing list. That
            // list was already agent-keyed, so this migration is exact — no other account's
            // credential can match, and one that fails put()'s check is simply not reinstated.
            val legacy = prefs[LEGACY_KEY] ?: return
            json
                .decodeFromString<List<LegacyEntry>>(legacy)
                .firstOrNull { it.agent == pubKeyHex }
                ?.let { attestation.put(OwnerAttestation(it.owner, it.conditions, it.sig)) }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Log.e("BuzzAttestationPrefs") { "Error reading held attestation: ${e.message}" }
        }
    }

    private suspend fun persist(held: OwnerAttestation?) {
        try {
            context.sharedPreferencesDataStore.edit { prefs ->
                if (held == null) {
                    prefs.remove(key)
                } else {
                    prefs[key] = json.encodeToString(Entry(held.ownerPubKey, held.conditions, held.sig))
                }
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Log.e("BuzzAttestationPrefs") { "Error writing held attestation: ${e.message}" }
        }
    }

    companion object {
        private const val KEY_PREFIX = "buzz.heldAttestation."

        /** The device-global key written before the store became per-account; read-only now. */
        private val LEGACY_KEY = stringPreferencesKey("buzz.heldAttestations")
    }
}
