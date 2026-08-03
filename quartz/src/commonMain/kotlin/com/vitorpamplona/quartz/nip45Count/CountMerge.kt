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
package com.vitorpamplona.quartz.nip45Count

import com.vitorpamplona.quartz.nip01Core.relay.commands.toClient.CountResult

/**
 * Combines NIP-45 COUNT answers from several relays into one figure.
 *
 * **Never sum.** Relays mirror each other, so the same event is normally held by several of them
 * and adding their counts multiplies the result by an unknown factor. Every honest combination is
 * therefore either a set union or a lower bound:
 *
 *  - **HyperLogLog union.** Relays that ship the optional `hll` field give 256 registers that merge
 *    by per-register maximum; the union's cardinality is re-estimated from the merged registers, so
 *    events held by several relays collapse into one instead of stacking.
 *  - **Largest plain count.** A relay's own count is a lower bound on the union, so the biggest one
 *    is the tightest bound available without registers to union.
 *
 * When both kinds arrive, neither wins by default: the HLL union covers only the relays that
 * supplied registers, and a plain count from a relay outside that set can easily exceed it — so the
 * answer is the larger of the two. Taking the estimate alone would silently discard the better
 * bound.
 *
 * Relays that don't implement COUNT simply never answer and are absent here. That can only make the
 * result too small, which is the safe direction for a number presented as "at least this many".
 *
 * @return the merged result, or null when nothing to merge.
 */
fun mergeCountResults(results: Collection<CountResult>): CountResult? {
    if (results.isEmpty()) return null

    val hlls = results.mapNotNull { it.hll }
    if (hlls.isEmpty()) {
        return results.maxByOrNull { it.count }
    }

    val merged = HyperLogLog.merge(hlls)
    val union = HyperLogLog.estimate(merged).toInt()

    // Relays that answered without registers are not represented in the union above.
    val bestPlain = results.filter { it.hll == null }.maxOfOrNull { it.count } ?: 0

    return CountResult(
        count = maxOf(union, bestPlain),
        approximate = true,
        hll = merged,
    )
}
