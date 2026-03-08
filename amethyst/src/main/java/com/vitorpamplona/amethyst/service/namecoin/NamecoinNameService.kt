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

import com.vitorpamplona.quartz.nip05.namecoin.ElectrumxClient
import com.vitorpamplona.quartz.nip05.namecoin.NamecoinLookupCache
import com.vitorpamplona.quartz.nip05.namecoin.NamecoinNameResolver
import com.vitorpamplona.quartz.nip05.namecoin.NamecoinNostrResult
import com.vitorpamplona.quartz.nip05.namecoin.NamecoinResolveOutcome
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class NamecoinNameService private constructor() {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val electrumxClient = ElectrumxClient()
    private val resolver = NamecoinNameResolver(electrumxClient)
    private val cache = NamecoinLookupCache()

    companion object {
        @Volatile private var instance: NamecoinNameService? = null

        fun getInstance(): NamecoinNameService = instance ?: synchronized(this) { instance ?: NamecoinNameService().also { instance = it } }
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
     * Resolve with detailed outcome for error reporting.
     */
    suspend fun resolveDetailed(identifier: String): NamecoinResolveOutcome = resolver.resolveDetailed(identifier)

    /**
     * Verify that a Namecoin name maps to the expected pubkey (NIP-05 .bit verification).
     */
    suspend fun verifyNip05(
        nip05Address: String,
        expectedPubkeyHex: String,
    ): Boolean {
        if (!NamecoinNameResolver.isNamecoinIdentifier(nip05Address)) return false
        val result = resolve(nip05Address) ?: return false
        return result.pubkey.equals(expectedPubkeyHex, ignoreCase = true)
    }

    suspend fun clearCache() = cache.clear()
}
