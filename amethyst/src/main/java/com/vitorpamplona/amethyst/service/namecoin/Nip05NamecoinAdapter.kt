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

import com.vitorpamplona.quartz.nip05.namecoin.NamecoinNameResolver

/**
 * Static adapter for hooking Namecoin into NIP-05 verification.
 */
object Nip05NamecoinAdapter {
    /**
     * Attempt Namecoin-based NIP-05 verification.
     *
     * @param nip05Address The `nip05` field from a kind-0 event
     * @param expectedPubkeyHex The hex pubkey from the same event
     * @return `true` if verified via Namecoin, `false` if Namecoin says
     *         the mapping is wrong, or `null` if this identifier is not
     *         a Namecoin identifier (caller should fall through to HTTP NIP-05).
     */
    suspend fun tryVerify(
        nip05Address: String,
        expectedPubkeyHex: String,
    ): Boolean? {
        if (!NamecoinNameResolver.isNamecoinIdentifier(nip05Address)) {
            return null // Not a Namecoin identifier → let caller handle via HTTP
        }
        return NamecoinNameService.getInstance().verifyNip05(nip05Address, expectedPubkeyHex)
    }

    /**
     * Attempt to resolve a Namecoin identifier from the search bar.
     *
     * Called from the search/discovery flow when the user types an
     * identifier. Returns the hex pubkey if found, null otherwise.
     *
     * @param query The user's search input
     * @return Pair of (pubkey, relayList) or null
     */
    suspend fun tryResolveSearch(query: String): Pair<String, List<String>>? {
        if (!NamecoinNameResolver.isNamecoinIdentifier(query)) return null
        val result = NamecoinNameService.getInstance().resolve(query) ?: return null
        return Pair(result.pubkey, result.relays)
    }
}
