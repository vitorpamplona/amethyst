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
import com.vitorpamplona.amethyst.model.nip03Timestamp.OtsSettings
import com.vitorpamplona.quartz.utils.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlin.coroutines.cancellation.CancellationException

/**
 * Persistent storage for [OtsSettings], following the same pattern as
 * [NamecoinSharedPreferences].
 *
 * Uses the app-wide [sharedPreferencesDataStore] so OTS explorer settings
 * are global — not per-account.
 */
@Stable
class OtsSharedPreferences(
    private val context: Context,
    private val scope: CoroutineScope,
) {
    companion object {
        val KEY_CUSTOM_EXPLORER_URL = stringPreferencesKey("ots.customExplorerUrl")
    }

    /**
     * Current settings, loaded synchronously at init to avoid races.
     */
    private val _settings =
        MutableStateFlow(
            runBlocking { loadFromDisk() ?: OtsSettings.DEFAULT },
        )
    val settings: StateFlow<OtsSettings> = _settings

    /** Synchronous snapshot — safe to call from resolver builder lambdas. */
    val current: OtsSettings get() = _settings.value

    // ── Mutators ───────────────────────────────────────────────────────

    suspend fun setCustomExplorerUrl(url: String?) {
        val normalized = url?.trim()?.takeIf { it.isNotBlank() }
        persist(current.copy(customExplorerUrl = normalized))
    }

    suspend fun reset() {
        persist(OtsSettings.DEFAULT)
    }

    // ── Internal ───────────────────────────────────────────────────────

    private suspend fun persist(settings: OtsSettings) {
        _settings.value = settings
        try {
            context.sharedPreferencesDataStore.edit { prefs ->
                val url = settings.customExplorerUrl
                if (url != null) {
                    prefs[KEY_CUSTOM_EXPLORER_URL] = url
                } else {
                    prefs.remove(KEY_CUSTOM_EXPLORER_URL)
                }
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Log.e("OtsPrefs") { "Error writing DataStore: ${e.message}" }
        }
    }

    private suspend fun loadFromDisk(): OtsSettings? =
        try {
            val prefs = context.sharedPreferencesDataStore.data.first()
            val url = prefs[KEY_CUSTOM_EXPLORER_URL]?.takeIf { it.isNotBlank() }
            OtsSettings(customExplorerUrl = url)
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Log.e("OtsPrefs") { "Error reading DataStore: ${e.message}" }
            null
        }
}
