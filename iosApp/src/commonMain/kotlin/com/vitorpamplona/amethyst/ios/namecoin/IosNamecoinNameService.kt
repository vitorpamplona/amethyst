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

import com.vitorpamplona.quartz.nip05DnsIdentifiers.namecoin.ElectrumXClient
import com.vitorpamplona.quartz.nip05DnsIdentifiers.namecoin.NamecoinLookupCache
import com.vitorpamplona.quartz.nip05DnsIdentifiers.namecoin.NamecoinNameResolver
import com.vitorpamplona.quartz.nip05DnsIdentifiers.namecoin.NamecoinNostrResult
import com.vitorpamplona.quartz.nip05DnsIdentifiers.namecoin.NamecoinResolveOutcome
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Sealed class representing the UI-visible state of a Namecoin resolution.
 */
sealed class NamecoinResolveState {
    data object Idle : NamecoinResolveState()

    data object Resolving : NamecoinResolveState()

    data class Resolved(
        val result: NamecoinNostrResult,
    ) : NamecoinResolveState()

    data class Error(
        val message: String,
    ) : NamecoinResolveState()
}

/**
 * iOS Namecoin name service — wraps NamecoinNameResolver with caching,
 * preferences-aware server list, and StateFlow for UI observation.
 *
 * Mirrors the Desktop's DesktopNamecoinNameService pattern but uses
 * iOS-native persistence (NSUserDefaults via IosNamecoinPreferences).
 */
class IosNamecoinNameService(
    private val preferences: IosNamecoinPreferences,
) {
    private val client = ElectrumXClient()
    private val cache = NamecoinLookupCache()

    private val resolver =
        NamecoinNameResolver(
            electrumxClient = client,
            serverListProvider = { preferences.getServers() },
        )

    private val _resolveState = MutableStateFlow<NamecoinResolveState>(NamecoinResolveState.Idle)
    val resolveState: StateFlow<NamecoinResolveState> = _resolveState

    /**
     * Check whether Namecoin resolution is enabled in preferences.
     */
    fun isEnabled(): Boolean = preferences.isEnabled()

    /**
     * Resolve a Namecoin identifier (.bit domain, d/ or id/ name) to a Nostr pubkey.
     *
     * Results are cached for 1 hour. The resolve state flow is updated
     * throughout the process for UI observation.
     *
     * @param identifier e.g. "alice@example.bit", "example.bit", "d/example"
     * @return [NamecoinNostrResult] on success, null on failure
     */
    suspend fun resolve(identifier: String): NamecoinNostrResult? {
        if (!preferences.isEnabled()) {
            _resolveState.value = NamecoinResolveState.Error("Namecoin resolution is disabled")
            return null
        }

        // Check cache first
        val cached = cache.get(identifier)
        if (cached != null) {
            if (cached.result != null) {
                _resolveState.value = NamecoinResolveState.Resolved(cached.result)
            }
            return cached.result
        }

        _resolveState.value = NamecoinResolveState.Resolving

        val outcome = resolver.resolveDetailed(identifier)

        return when (outcome) {
            is NamecoinResolveOutcome.Success -> {
                cache.put(identifier, outcome.result)
                _resolveState.value = NamecoinResolveState.Resolved(outcome.result)
                outcome.result
            }

            is NamecoinResolveOutcome.NameNotFound -> {
                cache.put(identifier, null)
                _resolveState.value = NamecoinResolveState.Error("Name not found: $identifier")
                null
            }

            is NamecoinResolveOutcome.NoNostrField -> {
                cache.put(identifier, null)
                _resolveState.value = NamecoinResolveState.Error("No Nostr field in name value")
                null
            }

            is NamecoinResolveOutcome.ServersUnreachable -> {
                _resolveState.value = NamecoinResolveState.Error("ElectrumX servers unreachable")
                null
            }

            is NamecoinResolveOutcome.InvalidIdentifier -> {
                _resolveState.value = NamecoinResolveState.Error("Invalid identifier: $identifier")
                null
            }

            is NamecoinResolveOutcome.Timeout -> {
                _resolveState.value = NamecoinResolveState.Error("Resolution timed out")
                null
            }
        }
    }

    /**
     * Quick check: is this identifier a Namecoin name?
     */
    fun isNamecoinIdentifier(identifier: String): Boolean = NamecoinNameResolver.isNamecoinIdentifier(identifier)

    /**
     * Reset state to idle (e.g. when clearing search).
     */
    fun resetState() {
        _resolveState.value = NamecoinResolveState.Idle
    }

    /**
     * Invalidate a cached entry (e.g. for manual refresh).
     */
    suspend fun invalidateCache(identifier: String) {
        cache.invalidate(identifier)
    }
}
