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
 * <https://github.com/vitorpamplona/graperank> and NosFabrica's Brainstorm
 * `DEFAULT` preset.
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
 * every user reachable from an observer in a [TrustGraph]. See the algorithm
 * notes in `TrustGraph`/`GrapeRankTest`; this is the single-observer worklist
 * form, operating on the compact int-CSR graph so it scales to the whole network.
 *
 * [compute] returns a `DoubleArray` indexed by node id (`graph.idOf(pubkey)`),
 * not a map — at millions of nodes a boxed map would dwarf the graph itself. The
 * observer's own entry stays pinned at `1.0`; callers rank the others.
 */
class GrapeRank(
    val params: GrapeRankParams = GrapeRankParams(),
) {
    private val rigidity = -ln(params.rigor)

    /** Exponential saturation turning accumulated weight into a confidence in `[0, 1)`. */
    private fun weightToConfidence(weight: Double): Double = 1.0 - exp(-weight * rigidity)

    private fun confidence(
        relationCode: Int,
        sourceIsObserver: Boolean,
    ): Double =
        when (relationCode) {
            TrustRelation.FOLLOW.code -> if (sourceIsObserver) params.directFollowConfidence else params.indirectFollowConfidence
            TrustRelation.MUTE.code -> params.muteConfidence
            else -> params.reportConfidence
        }

    private fun rating(relationCode: Int): Double =
        when (relationCode) {
            TrustRelation.FOLLOW.code -> TrustRelation.FOLLOW.rating
            TrustRelation.MUTE.code -> TrustRelation.MUTE.rating
            else -> TrustRelation.REPORT.rating
        }

    /**
     * Score every node reachable from [observer]. Returns scores by node id, or an
     * all-zero array if the observer isn't in the graph. [onProgress] fires once
     * per worklist visit with `(visited, queued)` running counts.
     */
    fun compute(
        graph: TrustGraph,
        observer: HexKey,
        onProgress: ((visited: Long, queued: Int) -> Unit)? = null,
    ): DoubleArray {
        val n = graph.nodeCount
        val scores = DoubleArray(n)
        val observerId = graph.idOf(observer)
        if (observerId < 0) return scores

        scores[observerId] = 1.0

        val inQueue = BooleanArray(n)
        val queue = IntArrayList(1024)

        fun enqueue(node: Int) {
            if (node != observerId && !inQueue[node]) {
                inQueue[node] = true
                queue.add(node)
            }
        }

        enqueueOutNeighbours(graph, observerId, ::enqueue)

        var visited = 0L
        while (queue.isNotEmpty()) {
            val target = queue.removeLast()
            inQueue[target] = false

            var sumOfWeights = 0.0
            var sumOfWeightedRatings = 0.0
            var i = graph.inOffsets[target]
            val end = graph.inOffsets[target + 1]
            while (i < end) {
                val packed = graph.inPacked[i]
                val source = packed and TrustGraph.SOURCE_MASK
                val sourceScore = scores[source]
                if (sourceScore != 0.0) {
                    val relationCode = packed ushr TrustGraph.SOURCE_BITS
                    val weight = confidence(relationCode, source == observerId) * sourceScore * params.attenuation
                    sumOfWeights += weight
                    sumOfWeightedRatings += weight * rating(relationCode)
                }
                i++
            }

            val newScore =
                if (abs(sumOfWeights) < 0.00001) {
                    0.0
                } else {
                    val s = weightToConfidence(sumOfWeights) * sumOfWeightedRatings / sumOfWeights
                    if (s > 0.0) s else 0.0
                }

            val oldScore = scores[target]
            scores[target] = newScore
            if (abs(newScore - oldScore) > params.convergence) {
                enqueueOutNeighbours(graph, target, ::enqueue)
            }

            visited++
            onProgress?.invoke(visited, queue.size)
        }

        return scores
    }

    private inline fun enqueueOutNeighbours(
        graph: TrustGraph,
        node: Int,
        enqueue: (Int) -> Unit,
    ) {
        var i = graph.outOffsets[node]
        val end = graph.outOffsets[node + 1]
        while (i < end) {
            enqueue(graph.outTargets[i])
            i++
        }
    }
}
