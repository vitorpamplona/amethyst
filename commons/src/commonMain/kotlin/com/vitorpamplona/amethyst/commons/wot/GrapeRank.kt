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
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln

/**
 * Tunable GrapeRank parameters. Defaults mirror the reference implementation at
 * <https://github.com/vitorpamplona/graperank>.
 *
 * A follow from the observer themselves counts far more than a follow from a
 * stranger deep in the graph ([directFollowConfidence] vs
 * [indirectFollowConfidence]); mutes and reports are trusted more heavily than
 * an indirect follow because negative signals are rarer and more deliberate.
 */
@Immutable
data class GrapeRankParams(
    val attenuation: Double = 0.85,
    val rigor: Double = 0.5,
    val directFollowConfidence: Double = 0.5,
    val indirectFollowConfidence: Double = 0.03,
    val muteConfidence: Double = 0.5,
    val reportConfidence: Double = 0.5,
    val convergence: Double = 0.0001,
)

/**
 * GrapeRank — a subjective, observer-centric web-of-trust score in `[0, 1]` for
 * every user reachable from an observer in a [TrustGraph]. The observer has full
 * self-trust (`1.0`); trust decays by roughly the attenuation factor each hop,
 * so scores fall to ~0 within a handful of hops.
 *
 * This is a faithful single-observer port of the reference `v3TargetedBFS`
 * variant. Rather than the reactive per-edge propagation the reference uses (it
 * assumes edges stream in one at a time), this recomputes over a graph that is
 * already fully loaded, using a worklist that:
 *   1. seeds the observer at `1.0` and enqueues the users it attests about,
 *   2. dequeues a target, recomputes its score over *all* its incoming edges,
 *   3. re-enqueues that target's out-neighbours whenever its score moved by more
 *      than [GrapeRankParams.convergence].
 *
 * Attenuation makes the update a contraction, so the worklist reaches the same
 * fixed point a full sweep would — while only ever touching users reachable from
 * the observer. See `GrapeRankTest` for the full-sweep cross-check.
 */
class GrapeRank(
    val params: GrapeRankParams = GrapeRankParams(),
) {
    /** Confidence weight [source]→target contributes, from [observer]'s point of view. */
    private fun confidence(
        edge: TrustEdge,
        observer: HexKey,
    ): Double =
        when (edge.relation) {
            TrustRelation.FOLLOW -> if (edge.source == observer) params.directFollowConfidence else params.indirectFollowConfidence
            TrustRelation.MUTE -> params.muteConfidence
            TrustRelation.REPORT -> params.reportConfidence
        }

    /** Exponential saturation curve turning accumulated weight into a confidence in `[0, 1)`. */
    private fun weightToConfidence(weight: Double): Double = 1.0 - exp(-weight * -ln(params.rigor))

    /**
     * Score every user reachable from [observer]. The returned map excludes the
     * observer itself (its score is a pinned `1.0` and not part of a ranking).
     * Users with no positive path from the observer are absent (equivalently, 0).
     */
    fun compute(
        graph: TrustGraph,
        observer: HexKey,
    ): Map<HexKey, Double> {
        val scores = HashMap<HexKey, Double>()
        scores[observer] = 1.0

        val queue = ArrayDeque<HexKey>()
        val queued = HashSet<HexKey>()

        fun enqueue(user: HexKey) {
            if (user != observer && queued.add(user)) queue.addLast(user)
        }

        graph.outgoing[observer]?.forEach(::enqueue)

        while (queue.isNotEmpty()) {
            val target = queue.removeFirst()
            queued.remove(target)

            val newScore = scoreOf(graph, scores, target, observer)
            val oldScore = scores.put(target, newScore) ?: 0.0

            if (abs(newScore - oldScore) > params.convergence) {
                graph.outgoing[target]?.forEach(::enqueue)
            }
        }

        scores.remove(observer)
        return scores
    }

    private fun scoreOf(
        graph: TrustGraph,
        scores: Map<HexKey, Double>,
        target: HexKey,
        observer: HexKey,
    ): Double {
        var sumOfWeights = 0.0
        var sumOfWeightedRatings = 0.0

        val edges = graph.incoming[target] ?: return 0.0
        for (edge in edges) {
            val sourceScore = scores[edge.source] ?: continue
            val weight = confidence(edge, observer) * sourceScore * params.attenuation
            sumOfWeights += weight
            sumOfWeightedRatings += weight * edge.relation.rating
        }

        if (abs(sumOfWeights) < 0.00001) return 0.0
        val score = weightToConfidence(sumOfWeights) * sumOfWeightedRatings / sumOfWeights
        return if (score > 0.0) score else 0.0
    }
}
