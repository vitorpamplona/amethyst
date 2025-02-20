/**
 * Copyright (c) 2024 Vitor Pamplona
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

import com.vitorpamplona.quartz.nip01Core.hints.bloom.BloomFilterMurMur3

/**
 * Instead of having one bloom filter per relay, which could create many
 * large filters for very few events, this class uses only one mega bloom
 * filter and uses the hashcode of the relay uri as differentiator in salt.
 */
class HintIndexer(
    size: Int = 10_000_000,
    rounds: Int = 5,
) {
    private val bloomFilter = BloomFilterMurMur3(size, rounds)
    private val relayDB = hashSetOf<String>()

    fun index(
        id: ByteArray,
        relay: String,
    ) {
        relayDB.add(relay)
        bloomFilter.add(id, relay.hashCode())
    }

    fun get(id: ByteArray): List<String> =
        relayDB.filter {
            bloomFilter.mightContain(id, it.hashCode())
        }
}
