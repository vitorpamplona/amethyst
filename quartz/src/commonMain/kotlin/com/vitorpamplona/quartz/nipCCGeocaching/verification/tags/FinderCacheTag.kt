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
package com.vitorpamplona.quartz.nipCCGeocaching.verification.tags

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.nip01Core.core.Address
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.has
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip19Bech32.entities.NAddress
import com.vitorpamplona.quartz.utils.Hex
import com.vitorpamplona.quartz.utils.ensure

/**
 * The `a` tag of a geocache verification event (kind 7517): who was at which cache.
 *
 * **This is not a NIP-01 `a` tag.** NIP-CC defines its value as
 * `"<finder-pubkey-hex>:<geocache-naddr>"` — a colon-joined pair, where a NIP-01 address is
 * `kind:pubkey:d`. [com.vitorpamplona.quartz.nip01Core.core.Address.parse] rejects it (two
 * segments fail its three-segment guard, and the remainder does not start with `naddr1`) and logs
 * a warning on the way out, so a generic `a`-tag reader that meets one of these gets nothing
 * useful and a noisy log. Hence a parser of its own.
 *
 * Reading is slightly more generous than writing: the cache half goes through
 * [Address.parse], which accepts both the spec's `naddr1…` and a plain `kind:pubkey:d`. The
 * finder half must be exactly 64 hex characters, which is what keeps the two forms unambiguous —
 * a bare NIP-01 address would have `37516` on the left and is rejected.
 */
@Immutable
data class FinderCacheTag(
    val finderPubKey: HexKey,
    val cache: Address,
) {
    fun toTagArray() = assemble(finderPubKey, cache, null)

    companion object {
        const val TAG_NAME = "a"

        private fun isPubKey(value: String) = value.length == 64 && Hex.isHex64(value)

        fun isTag(tag: Array<String>) = parse(tag) != null

        fun parse(tag: Array<String>): FinderCacheTag? {
            ensure(tag.has(1)) { return null }
            ensure(tag[0] == TAG_NAME) { return null }

            val separator = tag[1].indexOf(':')
            ensure(separator > 0) { return null }

            val finder = tag[1].substring(0, separator)
            ensure(isPubKey(finder)) { return null }

            val cache = Address.parse(tag[1].substring(separator + 1)) ?: return null

            return FinderCacheTag(finder, cache)
        }

        fun parseFinder(tag: Array<String>) = parse(tag)?.finderPubKey

        fun parseCache(tag: Array<String>) = parse(tag)?.cache

        fun assemble(
            finderPubKey: HexKey,
            cache: Address,
            relayHint: NormalizedRelayUrl?,
        ) = arrayOf(TAG_NAME, "$finderPubKey:${NAddress.create(cache.kind, cache.pubKeyHex, cache.dTag, relayHint)}")
    }
}
