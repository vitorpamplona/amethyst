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
package com.vitorpamplona.amethyst.commons.wot

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.nip01Core.core.HexKey

/**
 * A single directed trust attestation between two Nostr users, mapped from a
 * kind:3 follow / kind:10000 mute / kind:1984 report. Each carries a [rating]
 * (how the relationship reflects on the target) that GrapeRank multiplies by an
 * observer-relative confidence — see [GrapeRank].
 */
@Immutable
enum class TrustRelation(
    val rating: Double,
) {
    FOLLOW(1.0),
    MUTE(-0.1),
    REPORT(-0.1),
}

/** [source] asserts [relation] about the (implicit) target it is indexed under. */
@Immutable
data class TrustEdge(
    val source: HexKey,
    val relation: TrustRelation,
)

/**
 * A protocol-agnostic web-of-trust graph keyed by pubkey hex.
 *
 * [incoming] maps every target user to the attestations pointing *at* it — the
 * only view GrapeRank needs to score a node. [outgoing] (source → the set of
 * users it attests about) is derived once and used by the propagation worklist
 * to know which nodes to re-score when a source's score moves.
 *
 * Build one with [TrustGraphBuilder.build] from a bag of Nostr events; score it
 * with [GrapeRank.compute].
 */
class TrustGraph(
    val incoming: Map<HexKey, List<TrustEdge>>,
) {
    /** source pubkey → the targets it has an outgoing edge to. */
    val outgoing: Map<HexKey, Set<HexKey>> by lazy {
        val out = HashMap<HexKey, MutableSet<HexKey>>()
        for ((target, edges) in incoming) {
            for (edge in edges) {
                out.getOrPut(edge.source) { HashSet() }.add(target)
            }
        }
        out
    }

    /** Every user that appears in the graph, as a target or as an edge source. */
    val users: Set<HexKey> by lazy {
        val all = HashSet<HexKey>(incoming.keys)
        for (edges in incoming.values) {
            for (edge in edges) all.add(edge.source)
        }
        all
    }

    fun edgeCount(): Int = incoming.values.sumOf { it.size }
}
