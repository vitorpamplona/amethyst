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
package com.vitorpamplona.quartz.experimental.graperank

import com.vitorpamplona.quartz.nip01Core.core.HexKey

/**
 * A trust relationship kind and the GrapeRank rating it carries. [code] is the
 * 2-bit tag packed alongside a source node id in the edge arrays (see
 * [TrustGraph]); keep it in `0..3`.
 */
enum class TrustRelation(
    val rating: Double,
    val code: Int,
) {
    FOLLOW(1.0, 0),
    MUTE(-0.1, 1),
    REPORT(-0.1, 2),
}

/**
 * A web-of-trust graph over Nostr pubkeys, stored compactly so it scales to the
 * whole network (millions of edges) without a `String`-keyed edge object per
 * relationship.
 *
 * Pubkeys are interned to dense `Int` node ids. Edges live in two
 * compressed-sparse-row (CSR) layouts backed by flat `IntArray`s — one indexed
 * by target (what [GrapeRank] reads to score a node) and one by source (what the
 * propagation worklist follows). Each incoming entry packs the source id in the
 * low 29 bits and the [TrustRelation.code] in the top bits, so an edge is a
 * single `int`. A 100M-edge graph is then ~0.8 GB of primitive arrays instead of
 * tens of GB of objects.
 *
 * Build one with [TrustGraphBuilder], feeding contact lists / mutes / reports in
 * as they stream off the relays.
 */
class TrustGraph internal constructor(
    val nodeCount: Int,
    private val pubkeys: Array<HexKey>,
    private val ids: HashMap<HexKey, Int>,
    // CSR by target: incoming edges of node t are inPacked[inOffsets[t] until inOffsets[t+1]],
    // each packing source id (low 29 bits) + relation code (top bits).
    internal val inOffsets: IntArray,
    internal val inPacked: IntArray,
    // CSR by source: out-neighbour targets of node s are outTargets[outOffsets[s] until outOffsets[s+1]].
    internal val outOffsets: IntArray,
    internal val outTargets: IntArray,
) {
    /** Node id for [pubkey], or `-1` if it never appeared in the graph. */
    fun idOf(pubkey: HexKey): Int = ids[pubkey] ?: -1

    /** Pubkey for a node [id]. */
    fun pubkeyOf(id: Int): HexKey = pubkeys[id]

    fun edgeCount(): Int = inPacked.size

    companion object {
        const val SOURCE_BITS = 29
        const val SOURCE_MASK = (1 shl SOURCE_BITS) - 1
        const val MAX_NODES = SOURCE_MASK // ids must fit in the low 29 bits
    }
}

/** A minimal growable `int[]` — avoids boxing `Int`s in an `ArrayList` at graph scale. */
internal class IntArrayList(
    initialCapacity: Int = 16,
) {
    var data: IntArray = IntArray(initialCapacity.coerceAtLeast(1))
        private set
    var size: Int = 0
        private set

    fun add(value: Int) {
        if (size == data.size) data = data.copyOf(data.size * 2)
        data[size++] = value
    }

    fun get(index: Int): Int = data[index]

    fun removeLast(): Int = data[--size]

    fun isNotEmpty(): Boolean = size > 0
}
