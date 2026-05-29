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

import com.fasterxml.jackson.annotation.JsonAutoDetect
import com.fasterxml.jackson.annotation.PropertyAccessor
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.vitorpamplona.amethyst.commons.model.nip05DnsIdentifiers.namecoin.NamecoinSettings
import com.vitorpamplona.quartz.nip05DnsIdentifiers.namecoin.ElectrumxServer
import com.vitorpamplona.quartz.nip05DnsIdentifiers.namecoin.NamecoinBackend
import com.vitorpamplona.quartz.nip05DnsIdentifiers.namecoin.NamecoinCoreRpcConfig
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
 *
 * Backend / Core RPC / fallback fields mirror the Android
 * `NamecoinSharedPreferences` API exactly, except the mutators are
 * non-suspend here because [java.util.prefs.Preferences] is synchronous —
 * unlike Android's coroutine-backed DataStore.
 *
 * Note on credential storage: the Core RPC password lives in plain
 * `java.util.prefs` (alongside the URL/username), exactly as Android
 * stores it in DataStore. Both are user-readable on disk; we accept
 * that trade-off because the alternative (system keychain) would
 * require platform-specific plumbing that isn't worth it for an
 * already-local secret the user typed in themselves.
 */
class DesktopNamecoinPreferences(
    private val prefs: Preferences =
        Preferences.userNodeForPackage(
            DesktopNamecoinPreferences::class.java,
        ),
) {
    /**
     * Jackson mapper configured to round-trip kotlinx `@Serializable` data
     * classes that ship computed `is*` properties (e.g.
     * [NamecoinCoreRpcConfig.isUsable]). Without these tweaks Jackson
     * writes `isUsable` on the way out but fails on the way back in
     * because the class has no matching setter, which silently nukes
     * the persisted Core RPC config.
     *
     * - Field-only visibility → ignore Kotlin's computed getters.
     * - FAIL_ON_UNKNOWN_PROPERTIES=false → belt-and-braces in case a
     *   future quartz release adds another computed property.
     */
    private val mapper =
        jacksonObjectMapper().apply {
            setVisibility(
                serializationConfig.defaultVisibilityChecker
                    .withFieldVisibility(JsonAutoDetect.Visibility.ANY)
                    .withGetterVisibility(JsonAutoDetect.Visibility.NONE)
                    .withIsGetterVisibility(JsonAutoDetect.Visibility.NONE)
                    .withSetterVisibility(JsonAutoDetect.Visibility.NONE)
                    .withCreatorVisibility(JsonAutoDetect.Visibility.ANY),
            )
            setVisibility(PropertyAccessor.FIELD, JsonAutoDetect.Visibility.ANY)
            disable(SerializationFeature.FAIL_ON_EMPTY_BEANS)
            disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
        }

    companion object {
        private const val KEY_ENABLED = "namecoin.enabled"
        private const val KEY_CUSTOM_SERVERS = "namecoin.customServers"
        private const val KEY_PINNED_CERTS = "namecoin.pinnedCerts"
        private const val KEY_BACKEND = "namecoin.backend"
        private const val KEY_CORE_RPC = "namecoin.coreRpc"
        private const val KEY_FALLBACK_CUSTOM_EX = "namecoin.fallback.customElectrumx"
        private const val KEY_FALLBACK_DEFAULT_EX = "namecoin.fallback.defaultElectrumx"
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

    /**
     * Change the primary resolution backend (ElectrumX vs Namecoin Core RPC).
     * Mirrors `NamecoinSharedPreferences.setBackend` on Android.
     */
    suspend fun setBackend(backend: NamecoinBackend) {
        val updated = current.copy(backend = backend)
        persist(updated)
    }

    /**
     * Persist the user's Namecoin Core RPC connection details.
     * Mirrors `NamecoinSharedPreferences.setCoreRpcConfig` on Android.
     */
    suspend fun setCoreRpcConfig(cfg: NamecoinCoreRpcConfig) {
        val updated = current.copy(namecoinCoreRpc = cfg)
        persist(updated)
    }

    /**
     * Toggle fallback from Core RPC to custom ElectrumX servers when the
     * primary backend can't be reached. Only meaningful when
     * `backend == NAMECOIN_CORE_RPC`.
     */
    suspend fun setFallbackToCustomElectrumx(enabled: Boolean) {
        val updated = current.copy(fallbackToCustomElectrumx = enabled)
        persist(updated)
    }

    /**
     * Toggle fallback to the hardcoded public ElectrumX defaults. Applies
     * to both backends — see [NamecoinSettings.fallbackToDefaultElectrumx].
     */
    suspend fun setFallbackToDefaultElectrumx(enabled: Boolean) {
        val updated = current.copy(fallbackToDefaultElectrumx = enabled)
        persist(updated)
    }

    suspend fun reset() {
        persist(NamecoinSettings.DEFAULT)
        clearPinnedCerts()
    }

    // ── Pinned certs (TOFU) ────────────────────────────────────────────

    /**
     * Store a PEM-encoded certificate that the user accepted via Test
     * Connection. The cert is appended to the existing list (deduplicated)
     * and persisted; callers are expected to push the updated list into
     * [com.vitorpamplona.quartz.nip05DnsIdentifiers.namecoin.ElectrumXClient]
     * via `setDynamicCerts(...)` so it takes effect immediately.
     *
     * The same store is shared with the Namecoin Core RPC client — both
     * `ElectrumXClient` and `NamecoinCoreRpcClient` accept
     * `setDynamicCerts(...)` and only consume the certs that actually
     * match their own connection. This matches Android's
     * `NamecoinSharedPreferences` behaviour exactly.
     */
    fun addPinnedCert(pem: String) {
        if (pem.isBlank()) return
        val existing = loadPinnedCertsFromDisk()
        val updated = (existing + pem).distinct()
        savePinnedCerts(updated)
    }

    /** Load all user-pinned certs from disk (for startup sync). */
    fun loadPinnedCerts(): List<String> = loadPinnedCertsFromDisk()

    /** Wipe all user-pinned certs. Called by [reset]. */
    private fun clearPinnedCerts() = savePinnedCerts(emptyList())

    private fun savePinnedCerts(certs: List<String>) {
        try {
            prefs.put(KEY_PINNED_CERTS, mapper.writeValueAsString(certs))
            prefs.flush()
        } catch (e: Exception) {
            System.err.println("NamecoinPrefs: Error writing pinned certs: ${e.message}")
        }
    }

    private fun loadPinnedCertsFromDisk(): List<String> =
        try {
            val raw = prefs.get(KEY_PINNED_CERTS, null)
            if (raw != null) {
                mapper.readValue<List<String>>(raw)
            } else {
                emptyList()
            }
        } catch (_: Exception) {
            emptyList()
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
            prefs.put(KEY_BACKEND, settings.backend.name)
            prefs.put(KEY_CORE_RPC, mapper.writeValueAsString(settings.namecoinCoreRpc))
            prefs.putBoolean(KEY_FALLBACK_CUSTOM_EX, settings.fallbackToCustomElectrumx)
            prefs.putBoolean(KEY_FALLBACK_DEFAULT_EX, settings.fallbackToDefaultElectrumx)
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
            val backend =
                prefs.get(KEY_BACKEND, null)?.let {
                    try {
                        NamecoinBackend.valueOf(it)
                    } catch (_: Exception) {
                        NamecoinBackend.ELECTRUMX
                    }
                } ?: NamecoinBackend.ELECTRUMX
            val coreRpc =
                prefs.get(KEY_CORE_RPC, null)?.let { raw ->
                    try {
                        mapper.readValue<NamecoinCoreRpcConfig>(raw)
                    } catch (_: Exception) {
                        null
                    }
                } ?: NamecoinCoreRpcConfig()
            val fallbackCustom = prefs.getBoolean(KEY_FALLBACK_CUSTOM_EX, false)
            val fallbackDefault = prefs.getBoolean(KEY_FALLBACK_DEFAULT_EX, false)
            NamecoinSettings(
                enabled = enabled,
                customServers = servers,
                backend = backend,
                namecoinCoreRpc = coreRpc,
                fallbackToCustomElectrumx = fallbackCustom,
                fallbackToDefaultElectrumx = fallbackDefault,
            )
        } catch (e: Exception) {
            System.err.println("NamecoinPrefs: Error reading preferences: ${e.message}")
            NamecoinSettings.DEFAULT
        }
}
