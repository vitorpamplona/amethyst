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
package com.vitorpamplona.amethyst.ios.namecoin

import com.vitorpamplona.quartz.nip05DnsIdentifiers.namecoin.DEFAULT_ELECTRUMX_SERVERS
import com.vitorpamplona.quartz.nip05DnsIdentifiers.namecoin.ElectrumxServer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import platform.Foundation.NSUserDefaults

/**
 * Persists Namecoin settings using NSUserDefaults.
 *
 * Stores:
 * - Whether Namecoin resolution is enabled
 * - Custom ElectrumX server list (host:port format)
 */
class IosNamecoinPreferences(
    private val defaults: NSUserDefaults = NSUserDefaults.standardUserDefaults,
) {
    private val _enabled = MutableStateFlow(loadEnabled())
    val enabled: StateFlow<Boolean> = _enabled

    private val _customServers = MutableStateFlow(loadCustomServers())
    val customServers: StateFlow<List<ElectrumxServer>> = _customServers

    fun isEnabled(): Boolean = _enabled.value

    fun setEnabled(value: Boolean) {
        defaults.setBool(value, forKey = KEY_ENABLED)
        defaults.synchronize()
        _enabled.value = value
    }

    fun getServers(): List<ElectrumxServer> {
        val custom = _customServers.value
        return custom.ifEmpty { DEFAULT_ELECTRUMX_SERVERS }
    }

    fun setCustomServers(servers: List<ElectrumxServer>) {
        val encoded = servers.joinToString(";") { "${it.host}:${it.port}:${it.useSsl}" }
        defaults.setObject(encoded, forKey = KEY_CUSTOM_SERVERS)
        defaults.synchronize()
        _customServers.value = servers
    }

    fun addCustomServer(
        host: String,
        port: Int,
        useSsl: Boolean = true,
    ) {
        val current = _customServers.value.toMutableList()
        current.add(ElectrumxServer(host, port, useSsl, usePinnedTrustStore = true))
        setCustomServers(current)
    }

    fun removeCustomServer(index: Int) {
        val current = _customServers.value.toMutableList()
        if (index in current.indices) {
            current.removeAt(index)
            setCustomServers(current)
        }
    }

    fun resetToDefaults() {
        setCustomServers(emptyList())
    }

    private fun loadEnabled(): Boolean =
        if (defaults.objectForKey(KEY_ENABLED) != null) {
            defaults.boolForKey(KEY_ENABLED)
        } else {
            true // enabled by default
        }

    private fun loadCustomServers(): List<ElectrumxServer> {
        val raw = defaults.stringForKey(KEY_CUSTOM_SERVERS) ?: return emptyList()
        if (raw.isBlank()) return emptyList()
        return raw.split(";").mapNotNull { entry ->
            val parts = entry.split(":")
            if (parts.size >= 2) {
                val host = parts[0]
                val port = parts[1].toIntOrNull() ?: return@mapNotNull null
                val useSsl = if (parts.size >= 3) parts[2].toBooleanStrictOrNull() ?: true else true
                ElectrumxServer(host, port, useSsl, usePinnedTrustStore = true)
            } else {
                null
            }
        }
    }

    companion object {
        private const val KEY_ENABLED = "namecoin_enabled"
        private const val KEY_CUSTOM_SERVERS = "namecoin_custom_servers"
    }
}
