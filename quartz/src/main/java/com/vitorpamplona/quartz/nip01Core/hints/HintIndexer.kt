/**
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
import com.vitorpamplona.quartz.nip01Core.core.hexToByteArray
import com.vitorpamplona.quartz.nip01Core.hints.bloom.BloomFilterMurMur3
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.utils.LargeCache

/**
 * Instead of having one bloom filter per relay per type, which could create
 * many large bloom filters for collections of very few items, this class uses
 * only one mega bloom filter per type and uses the hashcode of the relay uri
 * as seed differentiator in the hash function.
 */
class HintIndexer {
    private val eventHints = BloomFilterMurMur3(10_000_000, 5)
    private val addressHints = BloomFilterMurMur3(2_000_000, 5)
    private val pubKeyHints = BloomFilterMurMur3(10_000_000, 5)
    private val relayDB = LargeCache<NormalizedRelayUrl, NormalizedRelayUrl>()

    private fun add(
        id: ByteArray,
        relay: NormalizedRelayUrl,
        bloom: BloomFilterMurMur3,
    ) {
        relayDB.put(relay, relay)
        bloom.add(id, relay.hashCode())
    }

    private fun getHintsFor(
        id: ByteArray,
        bloom: BloomFilterMurMur3,
    ) = relayDB.filter { relay, _ -> bloom.mightContain(id, relay.hashCode()) }

    // --------------------
    // Event Host hints
    // --------------------
    fun addEvent(
        eventId: ByteArray,
        relay: NormalizedRelayUrl,
    ) = add(eventId, relay, eventHints)

    fun addEvent(
        eventId: HexKey,
        relay: NormalizedRelayUrl,
    ) = addEvent(eventId.hexToByteArray(), relay)

    fun hintsForEvent(eventId: ByteArray) = getHintsFor(eventId, eventHints)

    fun hintsForEvent(eventId: HexKey) = hintsForEvent(eventId.hexToByteArray())

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
    ) = addAddress(addressId.toByteArray(), relay)

    fun hintsForAddress(addressId: ByteArray) = getHintsFor(addressId, addressHints)

    fun hintsForAddress(addressId: String) = hintsForAddress(addressId.toByteArray())

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
    ) = addKey(key.hexToByteArray(), relay)

    fun hintsForKey(key: ByteArray) = getHintsFor(key, pubKeyHints)

    fun hintsForKey(key: HexKey) = hintsForKey(key.hexToByteArray())
}
