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
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GrapeRankTest {
    private val obs = "observer"

    private fun graphOf(vararg edges: Triple<HexKey, HexKey, TrustRelation>): TrustGraph {
        val incoming = HashMap<HexKey, MutableList<TrustEdge>>()
        for ((source, target, relation) in edges) {
            incoming.getOrPut(target) { mutableListOf() }.add(TrustEdge(source, relation))
        }
        return TrustGraph(incoming)
    }

    @Test
    fun observerIsExcludedFromRanking() {
        val scores = GrapeRank().compute(graphOf(Triple(obs, "a", TrustRelation.FOLLOW)), obs)
        assertNull(scores[obs], "observer's pinned self-trust is not part of the ranking")
    }

    @Test
    fun directFollowMatchesHandComputedValue() {
        val scores = GrapeRank().compute(graphOf(Triple(obs, "a", TrustRelation.FOLLOW)), obs)
        // weight = 0.5 * 1.0 * 0.85 = 0.425 ; conf(0.425) = 1 - 2^-0.425
        // score = conf * (0.425 / 0.425) = 0.2551612...
        assertEquals(0.25516127, scores.getValue("a"), 1e-6)
    }

    @Test
    fun trustDecaysSteeplyAcrossHops() {
        val scores =
            GrapeRank().compute(
                graphOf(
                    Triple(obs, "a", TrustRelation.FOLLOW),
                    Triple("a", "b", TrustRelation.FOLLOW),
                ),
                obs,
            )
        val a = scores.getValue("a")
        val b = scores.getValue("b")
        // Indirect follow from a (conf 0.03) two hops out: ~0.0045, an ~56x drop.
        assertEquals(0.004499, b, 1e-5)
        assertTrue(b < a / 10.0, "two-hop trust should be far below one-hop trust")
    }

    @Test
    fun aMuteFromAnEndorsedUserLowersTheScore() {
        val followOnly = GrapeRank().compute(graphOf(Triple(obs, "b", TrustRelation.FOLLOW)), obs)
        val withMute =
            GrapeRank().compute(
                graphOf(
                    Triple(obs, "a", TrustRelation.FOLLOW),
                    Triple(obs, "b", TrustRelation.FOLLOW),
                    Triple("a", "b", TrustRelation.MUTE),
                ),
                obs,
            )
        assertTrue(
            withMute.getValue("b") < followOnly.getValue("b"),
            "a mute from a trusted user should pull b's score below the follow-only baseline",
        )
    }

    @Test
    fun purelyReportedUserFloorsAtZero() {
        val scores =
            GrapeRank().compute(
                graphOf(
                    Triple(obs, "a", TrustRelation.FOLLOW),
                    Triple("a", "d", TrustRelation.REPORT),
                ),
                obs,
            )
        assertEquals(0.0, scores.getValue("d"), 1e-9, "negative-only signals floor at zero")
    }

    @Test
    fun unreachableUsersAreNotScored() {
        // x -> y exists but neither is reachable from the observer.
        val scores =
            GrapeRank().compute(
                graphOf(
                    Triple(obs, "a", TrustRelation.FOLLOW),
                    Triple("x", "y", TrustRelation.FOLLOW),
                ),
                obs,
            )
        assertTrue("a" in scores)
        assertNull(scores["y"], "a user with no path from the observer is absent from the result")
    }

    @Test
    fun cyclesConverge() {
        // a<->b mutual follow plus observer->a. Must terminate at a fixed point.
        val scores =
            GrapeRank().compute(
                graphOf(
                    Triple(obs, "a", TrustRelation.FOLLOW),
                    Triple("a", "b", TrustRelation.FOLLOW),
                    Triple("b", "a", TrustRelation.FOLLOW),
                ),
                obs,
            )
        assertTrue(scores.getValue("a") > 0.0)
        assertTrue(scores.getValue("b") > 0.0)
    }

    /**
     * Adversarial cross-check: the worklist propagation must reach the same fixed
     * point as a naive full-sweep (the reference `v1FullSweep`) on random graphs.
     */
    @Test
    fun worklistMatchesFullSweepOnRandomGraphs() {
        // Tight convergence so both methods settle onto essentially the same
        // fixed point (attenuation < 1 makes the update a contraction), leaving
        // only floating-point slop to compare against.
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
            val graph = graphOf(*edges.toTypedArray())
            val observer = nodes.first()

            val worklist = engine.compute(graph, observer)
            val fullSweep = fullSweep(graph, observer, params)

            for (node in graph.users) {
                if (node == observer) continue
                val a = worklist[node] ?: 0.0
                val b = fullSweep[node] ?: 0.0
                assertEquals(b, a, 1e-5, "seed=$seed node=$node worklist=$a fullSweep=$b")
            }
        }
    }

    // Reference implementation: blind full sweep over every user until nothing changes.
    private fun fullSweep(
        graph: TrustGraph,
        observer: HexKey,
        params: GrapeRankParams,
    ): Map<HexKey, Double> {
        fun confidence(edge: TrustEdge): Double =
            when (edge.relation) {
                TrustRelation.FOLLOW -> if (edge.source == observer) params.directFollowConfidence else params.indirectFollowConfidence
                TrustRelation.MUTE -> params.muteConfidence
                TrustRelation.REPORT -> params.reportConfidence
            }

        fun weightToConfidence(w: Double) = 1.0 - exp(-w * -ln(params.rigor))

        val scores = HashMap<HexKey, Double>()
        scores[observer] = 1.0
        do {
            var changed = false
            for (target in graph.users) {
                if (target == observer) continue
                var sumW = 0.0
                var sumWR = 0.0
                for (edge in graph.incoming[target] ?: emptyList()) {
                    val s = scores[edge.source] ?: continue
                    val w = confidence(edge) * s * params.attenuation
                    sumW += w
                    sumWR += w * edge.relation.rating
                }
                val newScore = if (abs(sumW) < 0.00001) 0.0 else max(weightToConfidence(sumW) * sumWR / sumW, 0.0)
                val old = scores.put(target, newScore) ?: 0.0
                changed = changed || abs(newScore - old) > params.convergence
            }
        } while (changed)
        scores.remove(observer)
        return scores
    }
}
