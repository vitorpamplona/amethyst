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
package com.vitorpamplona.amethyst.service.namecoin

import androidx.compose.runtime.Stable
import com.vitorpamplona.quartz.nip05DnsIdentifiers.namecoin.ElectrumxServer
import kotlinx.serialization.Serializable

/**
 * Immutable data class representing the current Namecoin resolution config.
 *
 * When custom servers are configured, they are used EXCLUSIVELY and the
 * hardcoded defaults are ignored. This gives privacy-conscious users full
 * control over which ElectrumX servers observe their name lookups.
 */
@Serializable
@Stable
data class NamecoinSettings(
    /** Whether Namecoin resolution is enabled at all. */
    val enabled: Boolean = true,
    /**
     * Custom ElectrumX servers.  When non-empty, these replace the defaults.
     *
     * Each entry is `host:port` (TLS) or `host:port:tcp` (plaintext).
     */
    val customServers: List<String> = emptyList(),
) {
    /** True when the user has configured at least one custom server. */
    val hasCustomServers: Boolean get() = customServers.isNotEmpty()

    /**
     * Convert to [ElectrumxServer] instances used by the resolver.
     * Returns `null` when no valid custom servers are configured (use defaults).
     */
    fun toElectrumxServers(): List<ElectrumxServer>? {
        if (customServers.isEmpty()) return null
        return customServers
            .mapNotNull { parseServerString(it) }
            .ifEmpty { null }
    }

    companion object {
        val DEFAULT = NamecoinSettings()

        /**
         * Parse `host:port` or `host:port:tcp` into an [ElectrumxServer].
         *
         * TLS is the default protocol.  Append `:tcp` for plaintext
         * (useful for `.onion` addresses and local servers).
         *
         * `.onion` addresses automatically get `trustAllCerts = true`
         * since certificate verification is meaningless over Tor.
         */
        fun parseServerString(s: String): ElectrumxServer? {
            val parts = s.trim().split(":")
            if (parts.size < 2) return null
            val host = parts[0].trim()
            val port = parts[1].trim().toIntOrNull() ?: return null
            if (host.isEmpty() || port <= 0 || port > 65535) return null
            val useSsl = parts.getOrNull(2)?.trim()?.lowercase() != "tcp"
            val isOnion = host.endsWith(".onion")
            // All custom servers set trustAllCerts=true, which (despite the
            // legacy name) means "use the pinned trust store" rather than
            // "trust all certificates". ElectrumX servers almost universally
            // use self-signed certs, so we route them through our pinned
            // SSLSocketFactory (hardcoded defaults + TOFU-pinned certs).
            // TODO: rename trustAllCerts → usePinnedTrustStore for clarity.
            return ElectrumxServer(
                host = host,
                port = port,
                useSsl = useSsl,
                trustAllCerts = true,
            )
        }

        /**
         * Format an [ElectrumxServer] back to the `host:port[:tcp]` string form.
         */
        fun formatServerString(server: ElectrumxServer): String {
            val base = "${server.host}:${server.port}"
            return if (server.useSsl) base else "$base:tcp"
        }
    }
}
