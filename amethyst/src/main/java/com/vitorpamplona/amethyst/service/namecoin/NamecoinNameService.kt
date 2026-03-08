/**
 * NamecoinNameService.kt
 *
 * Application-level singleton for Namecoin name resolution.
 * Provides caching, reactive state, and NIP-05 verification for .bit identifiers.
 *
 * SPDX-License-Identifier: MIT
 */
package com.vitorpamplona.amethyst.service.namecoin

import com.vitorpamplona.quartz.nip05.namecoin.ElectrumxClient
import com.vitorpamplona.quartz.nip05.namecoin.ElectrumxServer
import com.vitorpamplona.quartz.nip05.namecoin.NamecoinLookupCache
import com.vitorpamplona.quartz.nip05.namecoin.NamecoinNameResolver
import com.vitorpamplona.quartz.nip05.namecoin.NamecoinNostrResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class NamecoinNameService private constructor() {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val electrumxClient = ElectrumxClient()
    private val resolver = NamecoinNameResolver(electrumxClient)
    private val cache = NamecoinLookupCache()

    companion object {
        @Volatile private var INSTANCE: NamecoinNameService? = null
        fun getInstance(): NamecoinNameService =
            INSTANCE ?: synchronized(this) { INSTANCE ?: NamecoinNameService().also { INSTANCE = it } }
    }

    /**
     * Resolve a Namecoin identifier to a Nostr pubkey. Returns cached results when available.
     */
    suspend fun resolve(identifier: String): NamecoinNostrResult? {
        val cached = cache.get(identifier)
        if (cached != null) return cached.result
        val result = resolver.resolve(identifier)
        cache.put(identifier, result)
        return result
    }

    /**
     * Resolve and return just the hex pubkey, or null. Convenience for follow-import integration.
     */
    suspend fun resolvePubkey(identifier: String): String? = resolve(identifier)?.pubkey

    /**
     * Verify that a Namecoin name maps to the expected pubkey (NIP-05 .bit verification).
     */
    suspend fun verifyNip05(nip05Address: String, expectedPubkeyHex: String): Boolean {
        if (!NamecoinNameResolver.isNamecoinIdentifier(nip05Address)) return false
        val result = resolve(nip05Address) ?: return false
        return result.pubkey.equals(expectedPubkeyHex, ignoreCase = true)
    }

    suspend fun clearCache() = cache.clear()
}
