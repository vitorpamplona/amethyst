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
 * Builds a [TrustGraph] incrementally so callers never have to hold every contact
 * list in memory at once — feed each user's follows / mutes / reports as they
 * stream off the relays (or out of the store), then call [build].
 *
 * Interns pubkeys to dense ids on the fly and accumulates edges in flat growable
 * int arrays. Follows and mutes are replaceable (one list per author, deduped by
 * the caller via latest-per-author + set-valued tags); reports are regular events,
 * so `(reporter → reported)` report edges are deduped here. Self-edges are dropped.
 */
class TrustGraphBuilder {
    private val ids = HashMap<HexKey, Int>()
    private val pubkeys = ArrayList<HexKey>()

    // Parallel edge arrays: edge i is source edgeSource[i] --relation--> edgeTarget[i],
    // with the relation packed into the top bits of edgeSource[i].
    private val edgeTargets = IntArrayList()
    private val edgeSourcesPacked = IntArrayList()

    // Dedup for report edges only (reporters can file many kind:1984 for one target).
    private val reportSeen = HashSet<Long>()

    private fun intern(pubkey: HexKey): Int =
        ids.getOrPut(pubkey) {
            val id = pubkeys.size
            pubkeys.add(pubkey)
            id
        }

    private fun addEdge(
        source: HexKey,
        target: HexKey,
        relation: TrustRelation,
    ) {
        if (source == target) return
        val s = intern(source)
        val t = intern(target)
        if (relation == TrustRelation.REPORT) {
            val key = (s.toLong() shl 32) or (t.toLong() and 0xFFFFFFFFL)
            if (!reportSeen.add(key)) return
        }
        edgeTargets.add(t)
        edgeSourcesPacked.add(s or (relation.code shl TrustGraph.SOURCE_BITS))
    }

    fun addFollows(
        source: HexKey,
        follows: Iterable<HexKey>,
    ) {
        for (target in follows) addEdge(source, target, TrustRelation.FOLLOW)
    }

    fun addMutes(
        source: HexKey,
        muted: Iterable<HexKey>,
    ) {
        for (target in muted) addEdge(source, target, TrustRelation.MUTE)
    }

    fun addReports(
        source: HexKey,
        reported: Iterable<HexKey>,
    ) {
        for (target in reported) addEdge(source, target, TrustRelation.REPORT)
    }

    fun nodeCount(): Int = pubkeys.size

    fun edgeCount(): Int = edgeTargets.size

    /** Freeze the accumulated edges into the two CSR layouts. */
    fun build(): TrustGraph {
        val n = pubkeys.size
        val m = edgeTargets.size

        // Incoming CSR (by target).
        val inOffsets = IntArray(n + 1)
        for (i in 0 until m) inOffsets[edgeTargets.get(i) + 1]++
        for (i in 1..n) inOffsets[i] += inOffsets[i - 1]
        val inPacked = IntArray(m)
        val inCursor = inOffsets.copyOf()
        for (i in 0 until m) {
            val t = edgeTargets.get(i)
            inPacked[inCursor[t]++] = edgeSourcesPacked.get(i)
        }

        // Outgoing CSR (by source). Each entry packs the target id + relation code
        // in the same layout as the incoming CSR, so a forward walk (e.g. the
        // follow-only BFS in TrustGraph.hopsFrom) can filter edges by relation.
        val outOffsets = IntArray(n + 1)
        for (i in 0 until m) {
            val s = edgeSourcesPacked.get(i) and TrustGraph.SOURCE_MASK
            outOffsets[s + 1]++
        }
        for (i in 1..n) outOffsets[i] += outOffsets[i - 1]
        val outPacked = IntArray(m)
        val outCursor = outOffsets.copyOf()
        for (i in 0 until m) {
            val packedSource = edgeSourcesPacked.get(i)
            val s = packedSource and TrustGraph.SOURCE_MASK
            val relationCode = packedSource ushr TrustGraph.SOURCE_BITS
            outPacked[outCursor[s]++] = edgeTargets.get(i) or (relationCode shl TrustGraph.SOURCE_BITS)
        }

        return TrustGraph(n, pubkeys.toTypedArray(), ids, inOffsets, inPacked, outOffsets, outPacked)
    }
}
