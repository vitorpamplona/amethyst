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
import com.vitorpamplona.quartz.utils.concurrent.ConcurrentSet

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
     * Rollback refusals already reported by [LOG_GAP], keyed by the exact refusal
     * (entity + floor + offered version) rather than by entity, so a *new* rollback
     * attempt against the same entity is still reported.
     *
     * Bounded in practice: a key is only ever added when an entity is offered a chain
     * it already refused at a version pair it has not seen, which happens once per real
     * rotation, not once per fold. Deliberately never cleared and deliberately
     * fail-open — see [LOG_GAP].
     */
    private val reportedGaps = ConcurrentSet<String>()

    /** Above this many distinct refusals, stop deduplicating and just warn. See [LOG_GAP]. */
    private const val MAX_TRACKED_GAPS = 4096

    /**
     * The default [GapReporter]: a rollback refusal is security-relevant (a rotator
     * tried to revert an entity), so it is warned, never swallowed.
     *
     * Warned **once per distinct refusal**, though. Amethyst re-folds the whole buffer
     * from scratch on every control-plane change, so an entity stuck refusing a rollback
     * re-reports the identical refusal on every refold — 22 byte-identical warnings in an
     * 80s cold start, which reads as 22 attacks rather than one unchanged state. The
     * dedup is on the message's own contents, so nothing a reader could act on is lost.
     *
     * Fails **open**: past [MAX_TRACKED_GAPS] distinct refusals it reverts to warning
     * every time. A flood of distinct rollback attempts is exactly when the warnings
     * matter most, so the failure mode is a noisy log, never a silenced one.
     */
    val LOG_GAP: GapReporter = { entityIdHex, floorVersion, offeredVersion ->
        val firstReport =
            reportedGaps.size() >= MAX_TRACKED_GAPS ||
                reportedGaps.add("$entityIdHex@$floorVersion<-$offeredVersion")

        if (firstReport) {
            Log.w(TAG) {
                "Control-plane rollback refused for entity $entityIdHex: already folded v$floorVersion, offered chain tops out at v$offeredVersion and does not connect to it"
            }
        }
    }

    /**
     * The compaction-era head: the highest-version edition at or above [floorVersion], ties
     * broken by the lower rumor id — no `prev`, no hash, no contiguity. See the compaction arm
     * in [foldEntity] for why version is the right (and only sound) anchor behind a Refounding.
     * Null when nothing at or above the floor was offered, which is the only gap this arm has.
     */
    private fun bootstrapHead(
        editions: List<ControlEdition>,
        floorVersion: Long,
    ): ControlEdition? =
        editions
            .filter { it.version >= floorVersion && it.version - floorVersion <= MAX_COMPACTION_VERSION_JUMP }
            .minWithOrNull(compareByDescending<ControlEdition> { it.version }.thenBy { it.rumorId })

    /**
     * How far above the floor the compaction arm will follow an edition in one step.
     *
     * The arm trades contiguity for cross-epoch tolerance, which made VERSION the only contest an
     * edition had to win — so a single authorized edition at `version = Long.MAX_VALUE` used to
     * become an entity's permanent head: it won the arm, `authorizedHeads` raised the floor to
     * `Long.MAX_VALUE`, and from there no honest edition could ever exceed the floor again. Even a
     * Refounding that dropped the poison did not help, because nothing was then offered at or above
     * the floor and the fold fell back to [EntityFloor.known] — the poison itself. See B1 in
     * `docs/concord-soft-ban-audit.md`.
     *
     * A compacted head is legitimately ahead of the floor by however many editions the entity gained
     * while we were away — a chain's worth, not 2^63. This bound is deliberately far above any real
     * community (a channel renamed a thousand times a day for three years stays under it) and far
     * below the point where the version space can be exhausted. Anything beyond it is not a
     * compaction we missed; it is someone reaching for the ceiling, and it is treated as a gap.
     */
    const val MAX_COMPACTION_VERSION_JUMP = 1_000_000L

    /**
     * The floor-anchored chain head among [editions], or null when nothing connects to [floor].
     *
     * The same anchor-then-walk the main path uses, factored out so the compaction arm can try it
     * first: the anchor is the floor's own edition (same version AND hash) or its immediate
     * successor citing that hash, then the walk climbs while each `version + 1` cites the current
     * head. Reports no gap — a null here means "fall back", not "refuse".
     */
    private fun chainHead(
        editions: List<ControlEdition>,
        floor: EntityFloor,
    ): ControlEdition? {
        val byVersion = HashMap<Long, MutableList<ControlEdition>>()
        for (e in editions) byVersion.getOrPut(e.version) { ArrayList() }.add(e)

        val lowest = byVersion.keys.filter { it >= floor.version }.minOrNull() ?: return null
        val winner = byVersion[lowest]?.minByOrNull { it.rumorId } ?: return null
        var head =
            when (lowest) {
                floor.version -> winner.takeIf { it.hashHex == floor.hashHex }
                floor.version + 1 -> winner.takeIf { it.prevHash != null && it.prevHash.toHexKey() == floor.hashHex }
                else -> null
            } ?: return null

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
        snapshot: Set<String>? = null,
        onGap: GapReporter = LOG_GAP,
    ): Map<String, ControlEdition> {
        val byEntity = editions.groupBy { it.entityIdHex }
        val out = HashMap<String, ControlEdition>(byEntity.size)
        for ((entity, list) in byEntity) {
            foldEntity(list, floors[entity], snapshot, onGap)?.let { out[entity] = it }
        }
        return out
    }

    /**
     * Folds the editions of a single entity into its current head, or null.
     *
     * With no [floor] this is the fresh-joiner fold: genesis-anchored, falling back
     * to the lowest-version edition present (the compaction bootstrap). With a
     * [floor] the walk is anchored at the edition already folded **or** at its
     * immediate successor when that successor cites the floor's hash; failing both
     * the chain is **gapped** and nothing above the floor is adopted —
     * [EntityFloor.known] is kept instead (or null when we no longer hold it). A head
     * is therefore never below the floor version.
     *
     * [snapshot] is the set of [ControlEdition.rumorId]s belonging to the epoch being
     * folded, supplied once a community has Refounded. An entity present in it takes
     * the version-anchored compaction arm instead of the chain walk — see the arm
     * itself for why. Null (the default) keeps the pure chain walk, which is right for
     * a single-epoch fold and for every caller that has no epoch to speak of.
     */
    fun foldEntity(
        editions: List<ControlEdition>,
        floor: EntityFloor? = null,
        snapshot: Set<String>? = null,
        onGap: GapReporter = LOG_GAP,
    ): ControlEdition? {
        if (editions.isEmpty()) return floor?.known

        // Compaction arm (CORD-06 §3). Once this entity has been re-wrapped into the epoch
        // being folded, `prev` chaining across the epoch boundary is meaningless: a Refounding
        // trims history and re-wraps the head alone, so the predecessor our floor names stays
        // behind on the older epoch and every offered `prev` dangles by design. Anchor on
        // VERSION instead — a re-wrap preserves the original author's signature but cannot
        // raise the version inside the signed seal, so a re-served stale edition always loses
        // to the compacted head. Presence of the entity in the snapshot selects the ARM;
        // version selects the HEAD, over every edition we hold and not just the subset.
        if (floor != null && snapshot != null && editions.any { it.rumorId in snapshot }) {
            // Chain first, bootstrap only as the fallback. The arm exists for the case where the
            // offered head genuinely cannot be connected — but when it CAN be, the connected head is
            // strictly better evidence than "highest number wins", and preferring it denies a stray
            // high-version edition its free win in every ordinary fold. The bootstrap keeps the
            // cross-epoch case working, now bounded by MAX_COMPACTION_VERSION_JUMP.
            chainHead(editions, floor)?.let { return it }
            return bootstrapHead(editions, floor.version)
                ?: run {
                    // Nothing admissible at or above the floor was served: the head we already
                    // accepted vanished from the offered set — withheld, so fail closed.
                    onGap(editions[0].entityIdHex, floor.version, editions.maxOf { it.version })
                    floor.known
                }
        }

        // Index editions by version, keeping the tie-break winner where several
        // share a version (lower rumor id wins).
        val byVersion = HashMap<Long, MutableList<ControlEdition>>()
        for (e in editions) byVersion.getOrPut(e.version) { ArrayList() }.add(e)

        var head =
            if (floor != null) {
                // Anchored at what we already folded. Below the floor is history we absorbed, so
                // the anchor is the lowest offered version at or above it, and only two shapes
                // connect: that edition IS the floor (same version AND hash — a same-version
                // sibling is a fork, not our chain), or it is the floor's immediate successor and
                // cites the floor's hash. The latter is the ordinary cross-epoch shape and is a
                // STRONGER proof of connection than mere presence. Anything else is a jump we
                // refuse; walking up from the anchor also makes a head below the floor version
                // structurally impossible.
                val lowest = byVersion.keys.filter { it >= floor.version }.minOrNull()
                val winner = lowest?.let { v -> byVersion[v]?.minByOrNull { it.rumorId } }
                val anchor =
                    when {
                        winner == null -> null
                        lowest == floor.version -> winner.takeIf { it.hashHex == floor.hashHex }
                        lowest == floor.version + 1 ->
                            winner.takeIf { it.prevHash != null && it.prevHash.toHexKey() == floor.hashHex }
                        else -> null
                    }
                anchor
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
     * One entity's [editions] as an **ordered candidate list** for its head:
     *
     *  1. the chain-verified [foldEntity] head first — the steady-state answer, and
     *     the compaction bootstrap too;
     *  2. then every remaining edition, version-**descending** (ties by the lower
     *     rumor id, the fold's tie-break winner first).
     *
     * The caller layers its authority gate on top and takes the first candidate that
     * passes ([foldGated]). That ordering is the whole point: an edition that fails
     * the gate must be *skipped*, never allowed to truncate the chain.
     *
     * Filtering the unauthorized editions out **before** the walk is what CORD-04
     * §1 ("an edition whose signer isn't authorized is dropped") reads like, but it
     * is a fork bomb: `foldEntity` only advances to `version + 1` when that edition
     * cites the current head's hash, so deleting a rejected edition from the middle
     * of the chain orphans every honest edition above it — permanently, since the
     * honest editions keep citing it. One unauthorized edition anywhere in an
     * entity's history would freeze that entity for good (a member's roles, a
     * channel, the banlist), recoverable only by a CORD-06 Refounding. Walking the
     * *unfiltered* chain for priority and gating the candidates instead keeps the
     * rejected edition inert while its honest successors still resolve — the same
     * shape Armada's `headCandidates` + `pickHead` use, so the two clients converge.
     *
     * The rogue-higher-version hole stays closed because the gate still decides: a
     * forged edition is never admissible at any position, and the chain-verified
     * head outranks any dangling higher version.
     *
     * [floor] semantics match [foldEntity]: anchored at the floor edition, and on a
     * gap nothing above the floor is offered — only [EntityFloor.known].
     */
    fun candidates(
        editions: List<ControlEdition>,
        floor: EntityFloor? = null,
        snapshot: Set<String>? = null,
        onGap: GapReporter = LOG_GAP,
    ): List<ControlEdition> {
        // Ask the fold whether it gapped rather than re-deriving the condition here: with the
        // compaction arm and the successor anchor there are three ways to connect, and a second
        // copy of that test is a bug waiting to drift out of sync with the first.
        var gapped = false
        val head =
            foldEntity(editions, floor, snapshot) { e, f, o ->
                gapped = true
                onGap(e, f, o)
            } ?: return emptyList()
        // A gap re-seated the known head: nothing from the offered set is admissible above
        // the floor, so the known edition is the only candidate.
        if (floor != null && gapped) {
            return listOf(head)
        }
        val out = ArrayList<ControlEdition>(editions.size)
        out.add(head)
        editions
            .filterTo(ArrayList()) { it.rumorId != head.rumorId && (floor == null || it.version >= floor.version) }
            .sortedWith(compareByDescending<ControlEdition> { it.version }.thenBy { it.rumorId })
            .let(out::addAll)
        return out
    }

    /**
     * The head of one entity: the highest-priority [candidates] entry that passes
     * [gate], or null when none does. See [candidates] for why the gate is applied
     * *after* the chain walk rather than before it.
     */
    fun foldEntityGated(
        editions: List<ControlEdition>,
        floor: EntityFloor? = null,
        snapshot: Set<String>? = null,
        onGap: GapReporter = LOG_GAP,
        gate: (ControlEdition) -> Boolean,
    ): ControlEdition? = candidates(editions, floor, snapshot, onGap).firstOrNull(gate)

    /**
     * Groups mixed [editions] by entity id and folds each to the highest-priority
     * head passing [gate] — the gated counterpart of [fold]. See [candidates].
     */
    fun foldGated(
        editions: Collection<ControlEdition>,
        floors: Map<String, EntityFloor> = emptyMap(),
        snapshot: Set<String>? = null,
        onGap: GapReporter = LOG_GAP,
        gate: (ControlEdition) -> Boolean,
    ): Map<String, ControlEdition> {
        val byEntity = editions.groupBy { it.entityIdHex }
        val out = HashMap<String, ControlEdition>(byEntity.size)
        for ((entity, list) in byEntity) {
            foldEntityGated(list, floors[entity], snapshot, onGap, gate)?.let { out[entity] = it }
        }
        return out
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
        snapshot: Set<String>? = null,
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
            // Same three-way connection test as the fold, asked of the fold itself so the two
            // can't drift apart: present at the floor, a successor citing it, or the compaction
            // arm's version anchor.
            var gapped = false
            foldEntity(list, floor, snapshot) { _, _, _ -> gapped = true }
            if (!gapped) {
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
