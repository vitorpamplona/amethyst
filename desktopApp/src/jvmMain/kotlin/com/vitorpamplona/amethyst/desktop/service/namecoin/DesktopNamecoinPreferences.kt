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
package com.vitorpamplona.amethyst.desktop.service.namecoin

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.vitorpamplona.amethyst.commons.model.nip05DnsIdentifiers.namecoin.NamecoinSettings
import com.vitorpamplona.quartz.nip05DnsIdentifiers.namecoin.ElectrumxServer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.prefs.Preferences

/**
 * Persistent storage for [NamecoinSettings] on Desktop.
 *
 * Uses [java.util.prefs.Preferences] API, following the same pattern as
 * [com.vitorpamplona.amethyst.desktop.DesktopPreferences].
 *
 * The current settings are available synchronously via [settings] (a
 * [StateFlow]) and can be read in non-suspend contexts (e.g. in a
 * `serverListProvider` lambda).
 */
class DesktopNamecoinPreferences(
    private val prefs: Preferences =
        Preferences.userNodeForPackage(
            DesktopNamecoinPreferences::class.java,
        ),
) {
    private val mapper = jacksonObjectMapper()

    companion object {
        private const val KEY_ENABLED = "namecoin.enabled"
        private const val KEY_CUSTOM_SERVERS = "namecoin.customServers"
    }

    private val _settings = MutableStateFlow(loadFromDisk())
    val settings: StateFlow<NamecoinSettings> = _settings

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
    }

    // ── Internal ───────────────────────────────────────────────────────

    private fun persist(settings: NamecoinSettings) {
        _settings.value = settings
        try {
            prefs.putBoolean(KEY_ENABLED, settings.enabled)
            prefs.put(
                KEY_CUSTOM_SERVERS,
                mapper.writeValueAsString(settings.customServers.filter { it.isNotBlank() }),
            )
            prefs.flush()
        } catch (e: Exception) {
            System.err.println("NamecoinPrefs: Error writing preferences: ${e.message}")
        }
    }

    private fun loadFromDisk(): NamecoinSettings =
        try {
            val enabled = prefs.getBoolean(KEY_ENABLED, true)
            val serversJson = prefs.get(KEY_CUSTOM_SERVERS, null)
            val servers =
                if (serversJson != null) {
                    try {
                        mapper.readValue<List<String>>(serversJson)
                    } catch (_: Exception) {
                        emptyList()
                    }
                } else {
                    emptyList()
                }
            NamecoinSettings(enabled = enabled, customServers = servers)
        } catch (e: Exception) {
            System.err.println("NamecoinPrefs: Error reading preferences: ${e.message}")
            NamecoinSettings.DEFAULT
        }
}
