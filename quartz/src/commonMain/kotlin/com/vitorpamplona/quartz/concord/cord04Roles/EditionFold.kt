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
package com.vitorpamplona.quartz.concord.cord04Roles

import com.vitorpamplona.quartz.nip01Core.core.toHexKey

/**
 * Folds Control Plane editions into the current head of each entity (CORD-04
 * §Edition Hashing & Chain Integrity).
 *
 * Rules enforced here:
 *  - **Genesis anchoring** — a chain starts at the lowest-version edition with no
 *    `ep` (prev hash). Without a genesis, the entity has no accepted head (we
 *    hold rather than trust an unrooted later edition).
 *  - **Intact chain / no downgrades** — the head advances to `version + 1` only
 *    when that edition's `ep` cites the current head's [ControlEdition.hash].
 *    Lower or non-chaining versions are ignored.
 *  - **Deterministic convergence** — at equal version, ties break on the lower
 *    rumor id, so every honest client folds to the same head.
 *
 * Authority-weighted tie-break ("authority first, then the lower rumor id") and
 * the owner-rooted `vac` verification are applied by the resolver layer on top of
 * this structural fold; this class is purely the chain walk.
 */
object EditionFold {
    /** Groups mixed [editions] by entity id and folds each to its head. */
    fun fold(editions: Collection<ControlEdition>): Map<String, ControlEdition> {
        val byEntity = editions.groupBy { it.entityIdHex }
        val out = HashMap<String, ControlEdition>(byEntity.size)
        for ((entity, list) in byEntity) {
            foldEntity(list)?.let { out[entity] = it }
        }
        return out
    }

    /** Folds the editions of a single entity into its current head, or null. */
    fun foldEntity(editions: List<ControlEdition>): ControlEdition? {
        if (editions.isEmpty()) return null

        // Index editions by version, keeping the tie-break winner where several
        // share a version (lower rumor id wins).
        val byVersion = HashMap<Long, MutableList<ControlEdition>>()
        for (e in editions) byVersion.getOrPut(e.version) { ArrayList() }.add(e)

        // Genesis: lowest version with no prev hash. Prefer the tie-break winner.
        var head =
            editions
                .filter { it.prevHash == null }
                .minWithOrNull(compareBy({ it.version }, { it.rumorId }))
                ?: return null

        // Walk the chain upward while the next version chains from the current head.
        while (true) {
            val next =
                byVersion[head.version + 1]
                    ?.filter { it.prevHash != null && it.prevHash.toHexKey() == head.hashHex }
                    ?.minByOrNull { it.rumorId }
                    ?: break
            head = next
        }
        return head
    }
}
