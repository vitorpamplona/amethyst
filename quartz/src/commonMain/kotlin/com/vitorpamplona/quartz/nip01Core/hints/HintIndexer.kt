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
package com.vitorpamplona.quartz.nip01Core.hints

import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.hints.bloom.BloomFilterMurMur3
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.isLocalHost
import com.vitorpamplona.quartz.utils.Hex
import com.vitorpamplona.quartz.utils.cache.LargeCache

/**
 * Instead of having one bloom filter per relay per type, which could create
 * many large bloom filters for collections of very few items, this class uses
 * only one mega bloom filter per type and uses the hashcode of the relay uri
 * as seed differentiator in the hash function.
 */
class HintIndexer {
    // 5MB for events
    private val eventHints = BloomFilterMurMur3(40_000_000, 10)

    // 875 KB for addresses
    private val addressHints = BloomFilterMurMur3(7_000_000, 10)

    // 3.75MB for keys
    private val pubKeyHints = BloomFilterMurMur3(30_000_000, 10)

    val relayDB = LargeCache<NormalizedRelayUrl, NormalizedRelayUrl>()

    private fun add(
        id: ByteArray,
        relay: NormalizedRelayUrl,
        bloom: BloomFilterMurMur3,
    ) {
        if (!relay.isLocalHost()) {
            relayDB.put(relay, relay)
            bloom.add(id, relay.hashCode())
        }
    }

    private fun getHintsFor(
        id: ByteArray,
        bloom: BloomFilterMurMur3,
    ) = relayDB.filter { relay, _ ->
        bloom.mightContain(id, relay.hashCode())
    }

    // --------------------
    // Event Host hints
    // --------------------
    fun addEvent(
        eventId: ByteArray,
        relay: NormalizedRelayUrl,
    ) = add(eventId, relay, eventHints)

    // The HexKey overloads take ids and keys straight from tags of received events, so they are
    // untrusted: the unchecked decoder throws on a char above U+00FF and silently decodes other
    // non-hex chars into a garbage id. A malformed value is skipped (or has no hints) instead.
    fun addEvent(
        eventId: HexKey,
        relay: NormalizedRelayUrl,
    ) {
        addEvent(Hex.decode64OrNull(eventId) ?: return, relay)
    }

    fun hintsForEvent(eventId: ByteArray) = getHintsFor(eventId, eventHints)

    fun hintsForEvent(eventId: HexKey): List<NormalizedRelayUrl> = hintsForEvent(Hex.decode64OrNull(eventId) ?: return emptyList())

    // --------------------
    // PubKeys Outbox hints
    // --------------------
    fun addAddress(
        addressId: ByteArray,
        relay: NormalizedRelayUrl,
    ) = add(addressId, relay, addressHints)

    fun addAddress(
        addressId: String,
        relay: NormalizedRelayUrl,
    ) = addAddress(addressId.encodeToByteArray(), relay)

    fun hintsForAddress(addressId: ByteArray) = getHintsFor(addressId, addressHints)

    fun hintsForAddress(addressId: String) = hintsForAddress(addressId.encodeToByteArray())

    // --------------------
    // PubKeys Outbox hints
    // --------------------
    fun addKey(
        key: ByteArray,
        relay: NormalizedRelayUrl,
    ) = add(key, relay, pubKeyHints)

    fun addKey(
        key: HexKey,
        relay: NormalizedRelayUrl,
    ) {
        addKey(Hex.decode64OrNull(key) ?: return, relay)
    }

    fun hintsForKey(key: ByteArray) = getHintsFor(key, pubKeyHints)

    fun hintsForKey(key: HexKey): List<NormalizedRelayUrl> = hintsForKey(Hex.decode64OrNull(key) ?: return emptyList())
}
