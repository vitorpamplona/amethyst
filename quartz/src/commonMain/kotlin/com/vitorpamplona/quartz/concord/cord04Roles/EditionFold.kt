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
import com.vitorpamplona.quartz.utils.Log

/**
 * The anti-rollback floor for one Control Plane entity: the [version] and
 * [hashHex] of an edition this client already folded to, plus (when we still hold
 * it) that [known] edition itself.
 *
 * A CORD-06 Refounding compacts the Control Plane by re-wrapping **one edition per
 * entity** at the new epoch, and the *rotator* picks which one. Nothing in a
 * signature stops it from re-wrapping version 1 of a chain that had reached
 * version 2 — restoring a revoked role, clearing a banlist, reverting metadata —
 * because every edition it serves is genuine. This is rollback by omission, and
 * the only defense is memory: a client that already folded to v2 must refuse to
 * come back down. [EntityFloor] is that memory, and [EditionFold.foldEntity] /
 * [EditionFold.admissible] are where it is enforced.
 *
 * [known] is what "keeps its existing state" means concretely: when the offered
 * chain cannot be connected to the floor, we fall back to the edition we last
 * folded rather than letting the entity vanish (an entity vanishing from the fold
 * is itself a rollback — a dropped banlist is an unban).
 */
class EntityFloor(
    val version: Long,
    val hashHex: String,
    val known: ControlEdition? = null,
)

/** This edition as an anti-rollback floor for its entity. */
fun ControlEdition.asFloor(): EntityFloor = EntityFloor(version, hashHex, this)

/**
 * Called when an entity's offered chain cannot be connected to the floor this
 * client already holds — i.e. someone tried to move the entity backwards. The
 * arguments are the entity id, the floor version we refuse to drop below, and the
 * highest version offered.
 */
typealias GapReporter = (entityIdHex: String, floorVersion: Long, offeredVersion: Long) -> Unit

/**
 * Folds Control Plane editions into the current head of each entity (CORD-04
 * §Edition Hashing & Chain Integrity).
 *
 * Rules enforced here:
 *  - **Genesis anchoring** — a chain starts at the lowest-version edition with no
 *    `ep` (prev hash).
 *  - **Anti-rollback floor** — when the caller supplies an [EntityFloor] (an
 *    entity head it already folded), the walk is *anchored at that floor*: it must
 *    find the exact edition (version + hash) it already knew. If it cannot, the
 *    offered chain is a **gap** and nothing above the floor is adopted — the entity
 *    keeps [EntityFloor.known] instead. This is what stops a Refounding rotator
 *    from serving v1 of a chain that had reached v2 (see [EntityFloor]).
 *  - **Refounding fallback (fresh joiner)** — when no genesis is present *and no
 *    floor is held*, anchor
 *    at the lowest-version edition available and accept it as the baseline. After
 *    a Refounding (CORD-06 §3) the compacted head still carries the `ep` it had
 *    before compaction, citing an edition in the *prior* epoch that a fresh joiner
 *    never fetches — so a dangling `prev` is the norm, not corruption, and CORD-04
 *    §1 ("Folding across a Refounding") requires the joiner to take that head as
 *    its baseline. The signature + owner-rooted authority check (applied by
 *    [com.vitorpamplona.quartz.concord.cord04Roles.AuthorityResolver] on top of this
 *    structural fold) is the whole test, so an unrooted forgery is still dropped
 *    there. Amethyst always re-folds the whole buffer from scratch, so it is
 *    structurally always a fresh joiner; it holds no prior chain to fail closed on.
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
    private const val TAG = "ConcordEditionFold"

    /**
     * The default [GapReporter]: a rollback refusal is security-relevant (a rotator
     * tried to revert an entity), so it is warned, never swallowed.
     */
    val LOG_GAP: GapReporter = { entityIdHex, floorVersion, offeredVersion ->
        Log.w(TAG) {
            "Control-plane rollback refused for entity $entityIdHex: already folded v$floorVersion, offered chain tops out at v$offeredVersion and does not connect to it"
        }
    }

    /**
     * Groups mixed [editions] by entity id and folds each to its head, honoring the
     * per-entity anti-rollback [floors] (keyed by [ControlEdition.entityIdHex]).
     *
     * Only entities actually present in [editions] are folded — re-seating an entity
     * that was omitted entirely (the cheapest rollback of all) is [admissible]'s job,
     * because it runs once on the whole pool, while this is also called on per-kind
     * subsets that must not have other kinds' heads injected into them.
     */
    fun fold(
        editions: Collection<ControlEdition>,
        floors: Map<String, EntityFloor> = emptyMap(),
        onGap: GapReporter = LOG_GAP,
    ): Map<String, ControlEdition> {
        val byEntity = editions.groupBy { it.entityIdHex }
        val out = HashMap<String, ControlEdition>(byEntity.size)
        for ((entity, list) in byEntity) {
            foldEntity(list, floors[entity], onGap)?.let { out[entity] = it }
        }
        return out
    }

    /**
     * Folds the editions of a single entity into its current head, or null.
     *
     * With no [floor] this is the fresh-joiner fold: genesis-anchored, falling back
     * to the lowest-version edition present (the compaction bootstrap). With a
     * [floor] the walk is anchored at the exact edition already folded; if that
     * edition is not among [editions] the chain is **gapped** and nothing above the
     * floor is adopted — [EntityFloor.known] is kept instead (or null when we no
     * longer hold it). A head is therefore never below the floor version.
     */
    fun foldEntity(
        editions: List<ControlEdition>,
        floor: EntityFloor? = null,
        onGap: GapReporter = LOG_GAP,
    ): ControlEdition? {
        if (editions.isEmpty()) return floor?.known

        // Index editions by version, keeping the tie-break winner where several
        // share a version (lower rumor id wins).
        val byVersion = HashMap<Long, MutableList<ControlEdition>>()
        for (e in editions) byVersion.getOrPut(e.version) { ArrayList() }.add(e)

        var head =
            if (floor != null) {
                // Anchored at what we already folded: the offered set MUST contain that exact
                // edition (same version AND same hash — a same-version sibling is a fork, not
                // our chain). Failing that, refuse to move at all rather than accept an
                // unverifiable jump; walking up from the floor also makes a head below the
                // floor version structurally impossible.
                editions.firstOrNull { it.version == floor.version && it.hashHex == floor.hashHex }
                    ?: run {
                        onGap(editions[0].entityIdHex, floor.version, editions.maxOf { it.version })
                        return floor.known
                    }
            } else {
                // Anchor at the genesis (lowest version with no prev hash), preferring the
                // tie-break winner. When no genesis is present — the compacted head of a
                // Refounded community carries a prev citing the prior epoch — a fresh joiner
                // anchors at the lowest-version edition it does hold and accepts it as the
                // baseline (CORD-04 §1 / CORD-06 §3). `editions` is non-empty here.
                editions
                    .filter { it.prevHash == null }
                    .minWithOrNull(compareBy({ it.version }, { it.rumorId }))
                    ?: editions.minWithOrNull(compareBy({ it.version }, { it.rumorId }))
                    ?: return null
            }

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

    /**
     * The subset of [editions] a client holding [floors] may consider at all — the
     * pre-filter for the layers that fold *derived* views of the same editions
     * (authority resolution, per-kind gated folds) and therefore cannot each carry
     * the floor themselves.
     *
     * Per entity: if the offered set contains the floor edition, the chain connects
     * and everything is admissible. If it does not, the entity is **gapped** and
     * every offered edition at or above the floor version is dropped, with
     * [EntityFloor.known] substituted so the entity keeps the state we last folded.
     * Entities with no floor pass through untouched (a fresh joiner must not be
     * penalized for having no history).
     */
    fun admissible(
        editions: Collection<ControlEdition>,
        floors: Map<String, EntityFloor>,
        onGap: GapReporter = LOG_GAP,
    ): List<ControlEdition> {
        if (floors.isEmpty()) return editions.toList()

        val out = ArrayList<ControlEdition>(editions.size + floors.size)
        val seen = HashSet<String>(floors.size)
        for ((entity, list) in editions.groupBy { it.entityIdHex }) {
            seen.add(entity)
            val floor = floors[entity]
            if (floor == null) {
                out.addAll(list)
                continue
            }
            if (list.any { it.version == floor.version && it.hashHex == floor.hashHex }) {
                out.addAll(list)
                continue
            }
            onGap(entity, floor.version, list.maxOf { it.version })
            // Below the floor is history we already absorbed; at or above it is the jump we
            // refuse. Re-seat the known head so the entity's state is kept, not cleared.
            list.filterTo(out) { it.version < floor.version }
            floor.known?.let { out.add(it) }
        }
        for ((entity, floor) in floors) {
            if (entity !in seen) floor.known?.let { out.add(it) }
        }
        return out
    }
}
