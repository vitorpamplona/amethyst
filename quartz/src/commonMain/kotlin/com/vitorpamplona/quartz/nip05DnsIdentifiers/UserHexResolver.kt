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
package com.vitorpamplona.quartz.nip05DnsIdentifiers

import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip19Bech32.decodePublicKeyAsHexOrNull
import kotlinx.coroutines.CancellationException

/**
 * Resolve any user identifier the UI or a CLI might throw at us to a 64-hex
 * Nostr pubkey. Accepts:
 *
 *  - raw 64-hex pubkey
 *  - bech32 `npub1…`
 *  - bech32 `nprofile1…`           (extracts the pubkey component)
 *  - bech32 `nsec1…`                (derives the public key from the secret)
 *  - NIP-05 internet identifier (`name@domain.tld` or bare domain)
 *
 * Returns null when the input doesn't match any of the above or when a NIP-05
 * lookup fails (network error, no match on server). Throws only on
 * [CancellationException] so callers can still use structured concurrency.
 *
 * Pass a non-null [nip05Client] to enable NIP-05 resolution. When it's null,
 * NIP-05-shaped inputs fall through and this returns null — that's the right
 * behaviour for call sites that don't have HTTP access (e.g. a pure-offline
 * context).
 */
suspend fun resolveUserHexOrNull(
    input: String,
    nip05Client: INip05Client? = null,
): HexKey? {
    val trimmed = input.trim()
    if (trimmed.isEmpty()) return null

    // First try the bech32/hex path — it's pure and synchronous.
    decodePublicKeyAsHexOrNull(trimmed)?.let { return it }

    // NIP-05: name@domain.tld (bare `@domain` = root "_" account).
    if (looksLikeNip05(trimmed) && nip05Client != null) {
        try {
            val id = Nip05Id.parse(trimmed) ?: return null
            return nip05Client.get(id)?.pubkey
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            return null
        }
    }
    return null
}

/**
 * Cheap precheck — a plausible NIP-05 identifier has an `@` followed by at
 * least one dot. Keeps us from issuing HTTP fetches on obvious non-matches
 * like hex strings or single bech32 tokens.
 */
internal fun looksLikeNip05(value: String): Boolean {
    val at = value.indexOf('@')
    if (at <= 0 || at == value.length - 1) return false
    val afterAt = value.substring(at + 1)
    return afterAt.contains('.') && !afterAt.contains('@')
}
