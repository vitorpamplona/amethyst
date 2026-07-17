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
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TrustGraphBuilderTest {
    private val alice = "alice"
    private val bob = "bob"
    private val carol = "carol"
    private val dave = "dave"

    /** Decode a node's incoming edges back to (source, relation) pairs from the CSR. */
    private fun TrustGraph.incomingOf(pubkey: HexKey): Set<Pair<HexKey, TrustRelation>> {
        val t = idOf(pubkey)
        if (t < 0) return emptySet()
        val out = HashSet<Pair<HexKey, TrustRelation>>()
        var i = inOffsets[t]
        val end = inOffsets[t + 1]
        while (i < end) {
            val packed = inPacked[i]
            val source = pubkeyOf(packed and TrustGraph.SOURCE_MASK)
            val relation = TrustRelation.entries.first { it.code == (packed ushr TrustGraph.SOURCE_BITS) }
            out.add(source to relation)
            i++
        }
        return out
    }

    @Test
    fun buildsFollowMuteAndReportEdges() {
        val b = TrustGraphBuilder()
        b.addFollows(alice, listOf(bob, carol))
        b.addMutes(bob, listOf(dave))
        b.addReports(carol, listOf(dave))
        val graph = b.build()

        assertEquals(setOf(alice to TrustRelation.FOLLOW), graph.incomingOf(bob))
        assertEquals(setOf(alice to TrustRelation.FOLLOW), graph.incomingOf(carol))
        assertEquals(
            setOf(bob to TrustRelation.MUTE, carol to TrustRelation.REPORT),
            graph.incomingOf(dave),
        )
    }

    @Test
    fun dropsSelfEdges() {
        val b = TrustGraphBuilder()
        b.addFollows(alice, listOf(alice, bob))
        val graph = b.build()
        assertTrue(graph.incomingOf(alice).isEmpty(), "a self-follow must not become an edge")
        assertEquals(setOf(alice to TrustRelation.FOLLOW), graph.incomingOf(bob))
    }

    @Test
    fun dedupesRepeatedReports() {
        val b = TrustGraphBuilder()
        b.addReports(alice, listOf(dave))
        b.addReports(alice, listOf(dave))
        val graph = b.build()
        assertEquals(1, graph.edgeCount())
        assertEquals(setOf(alice to TrustRelation.REPORT), graph.incomingOf(dave))
    }

    @Test
    fun keepsFollowAndMuteFromSameSourceAsDistinctEdges() {
        val b = TrustGraphBuilder()
        b.addFollows(alice, listOf(bob))
        b.addMutes(alice, listOf(bob))
        val graph = b.build()
        assertEquals(
            setOf(alice to TrustRelation.FOLLOW, alice to TrustRelation.MUTE),
            graph.incomingOf(bob),
        )
    }

    @Test
    fun internsEachPubkeyOnce() {
        val b = TrustGraphBuilder()
        b.addFollows(alice, listOf(bob, carol))
        b.addFollows(bob, listOf(carol))
        val graph = b.build()
        assertEquals(3, graph.nodeCount, "alice, bob, carol interned once each")
        assertEquals(3, graph.edgeCount())
    }

    private fun TrustGraph.hop(
        observer: HexKey,
        target: HexKey,
    ): Int {
        val id = idOf(target)
        return if (id < 0) TrustGraph.UNREACHABLE else hopsFrom(observer)[id]
    }

    @Test
    fun hopsCountFollowDistanceFromTheObserver() {
        val b = TrustGraphBuilder()
        b.addFollows(alice, listOf(bob)) // alice -> bob (1 hop)
        b.addFollows(bob, listOf(carol)) // bob -> carol (2 hops)
        b.addFollows(carol, listOf(dave)) // carol -> dave (3 hops)
        val graph = b.build()

        assertEquals(0, graph.hop(alice, alice), "the observer is 0 hops from itself")
        assertEquals(1, graph.hop(alice, bob), "a direct follow is 1 hop")
        assertEquals(2, graph.hop(alice, carol))
        assertEquals(3, graph.hop(alice, dave))
    }

    @Test
    fun hopsTakeTheShortestFollowPath() {
        val b = TrustGraphBuilder()
        b.addFollows(alice, listOf(bob, dave)) // dave is also a direct follow…
        b.addFollows(bob, listOf(carol))
        b.addFollows(carol, listOf(dave)) // …as well as reachable at 3 hops
        val graph = b.build()
        assertEquals(1, graph.hop(alice, dave), "BFS keeps the shortest of two follow paths")
    }

    @Test
    fun hopsIgnoreMuteAndReportEdges() {
        val b = TrustGraphBuilder()
        b.addMutes(alice, listOf(bob)) // a mute is not reachability
        b.addReports(alice, listOf(carol)) // neither is a report
        val graph = b.build()
        assertEquals(TrustGraph.UNREACHABLE, graph.hop(alice, bob), "a muted user is not reachable by follows")
        assertEquals(TrustGraph.UNREACHABLE, graph.hop(alice, carol), "a reported user is not reachable by follows")
    }

    @Test
    fun hopsAreUnreachableWithNoFollowPath() {
        val b = TrustGraphBuilder()
        b.addFollows(alice, listOf(bob))
        b.addFollows(carol, listOf(dave)) // a separate island
        val graph = b.build()
        assertEquals(TrustGraph.UNREACHABLE, graph.hop(alice, dave))
    }

    @Test
    fun trustedFollowerCountsOnlyCountFollowersAboveTheThreshold() {
        val b = TrustGraphBuilder()
        // carol is followed by alice, bob and dave (three follow edges in).
        b.addFollows(alice, listOf(carol))
        b.addFollows(bob, listOf(carol))
        b.addFollows(dave, listOf(carol))
        // dave also MUTES carol — a mute must never count as a follower.
        b.addMutes(dave, listOf(carol))
        val graph = b.build()

        val carolId = graph.idOf(carol)
        val scores = DoubleArray(graph.nodeCount)
        scores[graph.idOf(alice)] = 0.9 // trusted
        scores[graph.idOf(bob)] = 0.01 // below the 0.02 cutoff
        scores[graph.idOf(dave)] = 0.5 // trusted

        val counts = graph.trustedFollowerCounts(scores, minScore = 0.02)
        assertEquals(2, counts[carolId], "only alice and dave clear the cutoff; bob is too low and dave's mute doesn't count")
    }
}
