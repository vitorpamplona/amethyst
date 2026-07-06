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

import com.vitorpamplona.quartz.nip01Core.core.HexKey
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GrapeRankTest {
    private val obs = "observer"

    private fun graphOf(edges: List<Triple<HexKey, HexKey, TrustRelation>>): TrustGraph {
        val b = TrustGraphBuilder()
        for ((source, target, relation) in edges) {
            when (relation) {
                TrustRelation.FOLLOW -> b.addFollows(source, listOf(target))
                TrustRelation.MUTE -> b.addMutes(source, listOf(target))
                TrustRelation.REPORT -> b.addReports(source, listOf(target))
            }
        }
        return b.build()
    }

    private fun graphOf(vararg edges: Triple<HexKey, HexKey, TrustRelation>) = graphOf(edges.toList())

    /** Score for a pubkey (0.0 if absent from the graph). */
    private fun DoubleArray.of(
        graph: TrustGraph,
        pubkey: HexKey,
    ): Double {
        val id = graph.idOf(pubkey)
        return if (id < 0) 0.0 else this[id]
    }

    @Test
    fun observerIsPinnedAtFullSelfTrust() {
        val graph = graphOf(Triple(obs, "a", TrustRelation.FOLLOW))
        val scores = GrapeRank().compute(graph, obs)
        assertEquals(1.0, scores.of(graph, obs), 1e-12)
    }

    @Test
    fun directFollowMatchesHandComputedValue() {
        val graph = graphOf(Triple(obs, "a", TrustRelation.FOLLOW))
        val scores = GrapeRank().compute(graph, obs)
        // weight = 0.5 * 1.0 * 0.85 = 0.425 ; score = conf(0.425) = 0.2551612...
        assertEquals(0.25516127, scores.of(graph, "a"), 1e-6)
    }

    @Test
    fun trustDecaysSteeplyAcrossHops() {
        val graph =
            graphOf(
                Triple(obs, "a", TrustRelation.FOLLOW),
                Triple("a", "b", TrustRelation.FOLLOW),
            )
        val scores = GrapeRank().compute(graph, obs)
        val a = scores.of(graph, "a")
        val b = scores.of(graph, "b")
        assertEquals(0.004499, b, 1e-5)
        assertTrue(b < a / 10.0, "two-hop trust should be far below one-hop trust")
    }

    @Test
    fun aMuteFromAnEndorsedUserLowersTheScore() {
        val followOnlyGraph = graphOf(Triple(obs, "b", TrustRelation.FOLLOW))
        val followOnly = GrapeRank().compute(followOnlyGraph, obs).of(followOnlyGraph, "b")

        val muteGraph =
            graphOf(
                Triple(obs, "a", TrustRelation.FOLLOW),
                Triple(obs, "b", TrustRelation.FOLLOW),
                Triple("a", "b", TrustRelation.MUTE),
            )
        val withMute = GrapeRank().compute(muteGraph, obs).of(muteGraph, "b")

        assertTrue(withMute < followOnly, "a mute from a trusted user should pull b below the follow-only baseline")
    }

    @Test
    fun purelyReportedUserFloorsAtZero() {
        val graph =
            graphOf(
                Triple(obs, "a", TrustRelation.FOLLOW),
                Triple("a", "d", TrustRelation.REPORT),
            )
        val scores = GrapeRank().compute(graph, obs)
        assertEquals(0.0, scores.of(graph, "d"), 1e-9)
    }

    @Test
    fun unreachableUsersAreNotScored() {
        val graph =
            graphOf(
                Triple(obs, "a", TrustRelation.FOLLOW),
                Triple("x", "y", TrustRelation.FOLLOW),
            )
        val scores = GrapeRank().compute(graph, obs)
        assertTrue(scores.of(graph, "a") > 0.0)
        assertEquals(0.0, scores.of(graph, "y"), 1e-12, "a user with no path from the observer stays 0")
    }

    @Test
    fun cyclesConverge() {
        val graph =
            graphOf(
                Triple(obs, "a", TrustRelation.FOLLOW),
                Triple("a", "b", TrustRelation.FOLLOW),
                Triple("b", "a", TrustRelation.FOLLOW),
            )
        val scores = GrapeRank().compute(graph, obs)
        assertTrue(scores.of(graph, "a") > 0.0)
        assertTrue(scores.of(graph, "b") > 0.0)
    }

    @Test
    fun deduplicatesRepeatedReportEdges() {
        // Two report edges a->d collapse to one; the score matches a single report.
        val once = graphOf(Triple(obs, "a", TrustRelation.FOLLOW), Triple("a", "d", TrustRelation.REPORT))
        val twice =
            graphOf(
                Triple(obs, "a", TrustRelation.FOLLOW),
                Triple("a", "d", TrustRelation.REPORT),
                Triple("a", "d", TrustRelation.REPORT),
            )
        assertEquals(2, twice.edgeCount(), "duplicate report edge should be dropped")
        assertEquals(
            GrapeRank().compute(once, obs).of(once, "d"),
            GrapeRank().compute(twice, obs).of(twice, "d"),
            1e-12,
        )
    }

    /**
     * Adversarial cross-check: the worklist propagation must reach the same fixed
     * point as a naive full-sweep (the reference `v1FullSweep`) on random graphs.
     */
    @Test
    fun worklistMatchesFullSweepOnRandomGraphs() {
        val params = GrapeRankParams(convergence = 1e-10)
        val engine = GrapeRank(params)
        repeat(50) { seed ->
            val rng = Random(seed)
            val n = 3 + rng.nextInt(12)
            val nodes = (0 until n).map { "u$it" }
            val edges = ArrayList<Triple<HexKey, HexKey, TrustRelation>>()
            for (src in nodes) {
                for (dst in nodes) {
                    if (src == dst) continue
                    if (rng.nextDouble() < 0.25) {
                        val relation =
                            when (rng.nextInt(5)) {
                                0 -> TrustRelation.MUTE
                                1 -> TrustRelation.REPORT
                                else -> TrustRelation.FOLLOW
                            }
                        edges.add(Triple(src, dst, relation))
                    }
                }
            }
            val observer = nodes.first()
            val graph = graphOf(edges)
            val scores = engine.compute(graph, observer)
            val reference = fullSweep(edges, nodes, observer, params)

            for (node in nodes) {
                if (node == observer) continue // observer self-trust is not part of a ranking
                val a = scores.of(graph, node)
                val b = reference[node] ?: 0.0
                assertEquals(b, a, 1e-5, "seed=$seed node=$node worklist=$a fullSweep=$b")
            }
        }
    }

    // Reference: blind full sweep over every user until nothing changes.
    private fun fullSweep(
        edges: List<Triple<HexKey, HexKey, TrustRelation>>,
        nodes: List<HexKey>,
        observer: HexKey,
        params: GrapeRankParams,
    ): Map<HexKey, Double> {
        // Dedup identical edges (mirrors the builder: report edges dedup; follow/mute
        // sets are unique per source anyway).
        val incoming = HashMap<HexKey, MutableSet<Pair<HexKey, TrustRelation>>>()
        for ((s, t, r) in edges) {
            if (s == t) continue
            incoming.getOrPut(t) { LinkedHashSet() }.add(s to r)
        }

        fun confidence(
            r: TrustRelation,
            source: HexKey,
        ) = when (r) {
            TrustRelation.FOLLOW -> if (source == observer) params.directFollowConfidence else params.indirectFollowConfidence
            TrustRelation.MUTE -> params.muteConfidence
            TrustRelation.REPORT -> params.reportConfidence
        }

        fun weightToConfidence(w: Double) = 1.0 - exp(-w * -ln(params.rigor))

        val scores = HashMap<HexKey, Double>()
        scores[observer] = 1.0
        do {
            var changed = false
            for (target in nodes) {
                if (target == observer) continue
                var sumW = 0.0
                var sumWR = 0.0
                for ((source, r) in incoming[target] ?: emptySet()) {
                    val s = scores[source] ?: continue
                    val w = confidence(r, source) * s * params.attenuation
                    sumW += w
                    sumWR += w * r.rating
                }
                val newScore = if (abs(sumW) < 0.00001) 0.0 else max(weightToConfidence(sumW) * sumWR / sumW, 0.0)
                val old = scores.put(target, newScore) ?: 0.0
                changed = changed || abs(newScore - old) > params.convergence
            }
        } while (changed)
        return scores
    }
}
