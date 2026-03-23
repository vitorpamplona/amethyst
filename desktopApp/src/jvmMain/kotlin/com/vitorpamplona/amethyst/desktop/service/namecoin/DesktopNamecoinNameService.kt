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

import com.vitorpamplona.amethyst.commons.model.nip05DnsIdentifiers.namecoin.NamecoinResolveState
import com.vitorpamplona.amethyst.commons.model.nip05DnsIdentifiers.namecoin.NamecoinSettings
import com.vitorpamplona.quartz.nip05DnsIdentifiers.namecoin.DEFAULT_ELECTRUMX_SERVERS
import com.vitorpamplona.quartz.nip05DnsIdentifiers.namecoin.ElectrumXClient
import com.vitorpamplona.quartz.nip05DnsIdentifiers.namecoin.ElectrumxServer
import com.vitorpamplona.quartz.nip05DnsIdentifiers.namecoin.NamecoinLookupCache
import com.vitorpamplona.quartz.nip05DnsIdentifiers.namecoin.NamecoinNameResolver
import com.vitorpamplona.quartz.nip05DnsIdentifiers.namecoin.NamecoinNostrResult
import com.vitorpamplona.quartz.nip05DnsIdentifiers.namecoin.NamecoinResolveOutcome
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.net.SocketFactory

/**
 * Desktop application-level singleton for Namecoin name resolution.
 *
 * Same functionality as the Android `NamecoinNameService` but instantiated
 * directly (no Koin/Hilt DI). Uses plain JVM sockets (no Tor support on
 * Desktop yet).
 */
class DesktopNamecoinNameService(
    private val preferencesProvider: () -> NamecoinSettings = { NamecoinSettings.DEFAULT },
) {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val electrumxClient = ElectrumXClient(
        socketFactory = { SocketFactory.getDefault() },
    )

    private val resolver = NamecoinNameResolver(
        electrumxClient = electrumxClient,
        serverListProvider = {
            val settings = preferencesProvider()
            settings.toElectrumxServers() ?: DEFAULT_ELECTRUMX_SERVERS
        },
    )

    private val cache = NamecoinLookupCache()

    // ── Public API ─────────────────────────────────────────────────────

    /**
     * Resolve a Namecoin identifier to a Nostr pubkey.
     *
     * Returns cached results when available. This is the primary method
     * that the search bar and NIP-05 verifier should call.
     *
     * @param identifier e.g. "alice@example.bit", "id/bob", "example.bit"
     * @return [NamecoinNostrResult] or null
     */
    suspend fun resolve(identifier: String): NamecoinNostrResult? {
        val cached = cache.get(identifier)
        if (cached != null) return cached.result

        val result = resolver.resolve(identifier)
        cache.put(identifier, result)
        return result
    }

    /**
     * Resolve and return just the hex pubkey, or null.
     * Convenience for follow-import integration.
     */
    suspend fun resolvePubkey(identifier: String): String? = resolve(identifier)?.pubkey

    /**
     * Resolve with detailed outcome for error reporting.
     */
    suspend fun resolveDetailed(identifier: String): NamecoinResolveOutcome =
        resolver.resolveDetailed(identifier)

    /**
     * Verify that a Namecoin name maps to the expected pubkey.
     */
    suspend fun verifyNip05(
        nip05Address: String,
        expectedPubkeyHex: String,
    ): Boolean {
        if (!NamecoinNameResolver.isNamecoinIdentifier(nip05Address)) return false
        val result = resolve(nip05Address) ?: return false
        return result.pubkey.equals(expectedPubkeyHex, ignoreCase = true)
    }

    /**
     * Perform a lookup and emit results via a StateFlow.
     *
     * Useful for composable UIs that observe resolution state.
     */
    fun resolveLive(
        identifier: String,
        scope: CoroutineScope = this.scope,
    ): StateFlow<NamecoinResolveState> {
        val state = MutableStateFlow<NamecoinResolveState>(NamecoinResolveState.Loading)
        scope.launch {
            try {
                val result = resolve(identifier)
                state.value = if (result != null) {
                    NamecoinResolveState.Resolved(result)
                } else {
                    NamecoinResolveState.NotFound
                }
            } catch (e: Exception) {
                state.value = NamecoinResolveState.Error(e.message ?: "Unknown error")
            }
        }
        return state
    }

    /**
     * Clear the resolution cache.
     */
    suspend fun clearCache() = cache.clear()
}
