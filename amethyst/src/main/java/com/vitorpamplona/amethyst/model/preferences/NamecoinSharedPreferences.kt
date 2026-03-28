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
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.vitorpamplona.amethyst.service.namecoin.NamecoinSettings
import com.vitorpamplona.quartz.nip05DnsIdentifiers.namecoin.ElectrumxServer
import com.vitorpamplona.quartz.utils.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.coroutines.cancellation.CancellationException

/**
 * Persistent storage for [NamecoinSettings], following the same pattern as
 * [TorSharedPreferences].
 *
 * Uses the app-wide [sharedPreferencesDataStore] so Namecoin resolution
 * settings (like Tor settings) are global — not per-account.
 *
 * The current settings are available synchronously via [settings] (a
 * [StateFlow]) and can be read in non-suspend contexts (e.g. in a
 * `serverListProvider` lambda).
 */
@Stable
class NamecoinSharedPreferences(
    private val context: Context,
    private val scope: CoroutineScope,
) {
    private val json = Json { ignoreUnknownKeys = true }

    companion object {
        val KEY_ENABLED = booleanPreferencesKey("namecoin.enabled")
        val KEY_CUSTOM_SERVERS = stringPreferencesKey("namecoin.customServers")
        val KEY_PINNED_CERTS = stringPreferencesKey("namecoin.pinnedCerts")
    }

    /**
     * Current settings, loaded synchronously at init to avoid races.
     */
    private val _settings = MutableStateFlow(NamecoinSettings.DEFAULT)
    val settings: StateFlow<NamecoinSettings> = _settings

    init {
        scope.launch {
            _settings.tryEmit(loadFromDisk() ?: NamecoinSettings.DEFAULT)
        }
    }

    /** Synchronous snapshot — safe to call from `serverListProvider` lambdas. */
    val current: NamecoinSettings get() = _settings.value

    /**
     * Parsed [ElectrumxServer] list from current custom settings, or `null`
     * if the user hasn't configured any (meaning "use defaults").
     */
    val customServersOrNull: List<ElectrumxServer>?
        get() = current.toElectrumxServers()

    // ── Mutators ───────────────────────────────────────────────────────

    suspend fun setEnabled(enabled: Boolean) {
        val updated = current.copy(enabled = enabled)
        persist(updated)
    }

    suspend fun addServer(server: String) {
        if (server.isBlank() || server in current.customServers) return
        val updated = current.copy(customServers = current.customServers + server)
        persist(updated)
    }

    suspend fun removeServer(server: String) {
        val updated = current.copy(customServers = current.customServers - server)
        persist(updated)
    }

    suspend fun reset() {
        persist(NamecoinSettings.DEFAULT)
        clearPinnedCerts()
    }

    /**
     * Store a PEM-encoded certificate that the user accepted via Test Connection.
     * The cert is appended to the existing list and synced to the ElectrumXClient.
     */
    suspend fun addPinnedCert(pem: String) {
        val existing = loadPinnedCertsFromDisk()
        val updated = (existing + pem).distinct()
        savePinnedCerts(updated)
    }

    /** Load all user-pinned certs from disk (for startup sync). */
    suspend fun loadPinnedCerts(): List<String> = loadPinnedCertsFromDisk()

    private suspend fun clearPinnedCerts() = savePinnedCerts(emptyList())

    private suspend fun savePinnedCerts(certs: List<String>) {
        try {
            context.sharedPreferencesDataStore.edit { prefs ->
                prefs[KEY_PINNED_CERTS] = json.encodeToString(certs)
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Log.e("NamecoinPrefs") { "Error writing pinned certs: ${e.message}" }
        }
    }

    private suspend fun loadPinnedCertsFromDisk(): List<String> =
        try {
            val prefs = context.sharedPreferencesDataStore.data.first()
            val certsJson = prefs[KEY_PINNED_CERTS]
            if (certsJson != null) {
                json.decodeFromString<List<String>>(certsJson)
            } else {
                emptyList()
            }
        } catch (_: Exception) {
            emptyList()
        }

    // ── Internal ───────────────────────────────────────────────────────

    private suspend fun persist(settings: NamecoinSettings) {
        _settings.value = settings
        try {
            context.sharedPreferencesDataStore.edit { prefs ->
                prefs[KEY_ENABLED] = settings.enabled
                prefs[KEY_CUSTOM_SERVERS] =
                    json.encodeToString(
                        settings.customServers.filter { it.isNotBlank() },
                    )
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Log.e("NamecoinPrefs") { "Error writing DataStore: ${e.message}" }
        }
    }

    private suspend fun loadFromDisk(): NamecoinSettings? =
        try {
            val prefs = context.sharedPreferencesDataStore.data.first()
            val enabled = prefs[KEY_ENABLED] ?: true
            val serversJson = prefs[KEY_CUSTOM_SERVERS]
            val servers =
                if (serversJson != null) {
                    try {
                        json.decodeFromString<List<String>>(serversJson)
                    } catch (_: Exception) {
                        emptyList()
                    }
                } else {
                    emptyList()
                }
            NamecoinSettings(enabled = enabled, customServers = servers)
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Log.e("NamecoinPrefs") { "Error reading DataStore: ${e.message}" }
            null
        }
}
