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
package com.vitorpamplona.quartz.concord.cord06Rekey

import com.vitorpamplona.quartz.concord.cord02Community.ConcordCommunityState
import com.vitorpamplona.quartz.concord.cord02Community.HeldRoot
import com.vitorpamplona.quartz.concord.cord04Roles.AuthorityCitation
import com.vitorpamplona.quartz.concord.cord04Roles.ControlEdition
import com.vitorpamplona.quartz.concord.cord04Roles.control.tags.VacTag
import com.vitorpamplona.quartz.concord.crypto.ConcordKeyDerivation
import com.vitorpamplona.quartz.concord.crypto.ControlPlaneKeys
import com.vitorpamplona.quartz.concord.crypto.GroupKey
import com.vitorpamplona.quartz.concord.envelope.ConcordStreamEnvelope
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.firstTagValue
import com.vitorpamplona.quartz.nip01Core.core.hexToByteArray
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.nip01Core.signers.NostrSigner
import com.vitorpamplona.quartz.nip59Giftwrap.rumors.RumorAssembler
import com.vitorpamplona.quartz.utils.RandomInstance

/**
 * The events a Refounding produces (CORD-06 §3): the [controlWraps] (the current
 * Control Plane, compacted to its per-entity head editions and re-sealed at the
 * new epoch's split Control address — signed by the fresh `control_root`-derived
 * signer, readable under the fresh [newRoot]-derived read key) and the
 * [rekeyWraps] (kind-3303 base-rotation blobs, sealed under the **prior** root,
 * that deliver [newRoot] + the new `control_pk` to every retained member — and
 * the [newControlRoot] secret to staff — and to nobody else). Publish
 * [controlWraps] first (the new epoch's state) then [rekeyWraps] (the key that
 * unlocks it).
 */
class RefoundingBuild(
    val newRoot: ByteArray,
    /** The fresh staff write key, minted beside [newRoot] (CORD-02 §2). */
    val newControlRoot: ByteArray,
    val newEpoch: Long,
    /** The new epoch's split Control Plane keys (rotator view: signer held). */
    val newControlKeys: ControlPlaneKeys,
    val controlWraps: List<Event>,
    val rekeyWraps: List<Event>,
) {
    /** The new epoch's Control Plane address, delivered to every member in the base blobs. */
    val newControlPk: ByteArray get() = newControlKeys.address.hexToByteArray()
}

/**
 * A retained member's decrypted rekey result: the [newRoot] delivered at
 * [newEpoch] by [rotator], plus the next epoch's Control Plane keys — the
 * [newControlPk] every member's blob carries, and, for a staff recipient, the
 * [newControlRoot] write secret (CORD-06 §1). A null [newControlPk] marks a
 * legacy, pre-split 72-byte rotation (CORD-06 §3): its acceptor folds that
 * epoch's Control at the legacy address, honored when reading old rotations and
 * never minted by a compliant Rotator.
 */
class ReceivedRefounding(
    val newRoot: ByteArray,
    val newEpoch: Long,
    val rotator: HexKey,
    val newControlPk: ByteArray? = null,
    val newControlRoot: ByteArray? = null,
    /**
     * The Grant the rotator claims to act under (`vac`, CORD-06 §3 "Authority"), null when absent
     * (the owner cites nothing). The caller verifies it against its fold before adopting — see
     * [ConcordRotationAuthority.citationSatisfied].
     */
    val authority: AuthorityCitation? = null,
) {
    /** True when this was a legacy pre-split rotation (72-byte base blob). */
    val legacy: Boolean get() = newControlPk == null
}

/**
 * A Refounding the rotator has already minted keys for (CORD-06 §3 "Failure and races"): the
 * fresh [newRoot] + [newControlRoot] reserved for the rotation from ([rootEpoch], [prevCommit]).
 * A retry of the same rotation MUST reuse them — rotations correlate by (rotator, newepoch,
 * prevcommit), so a retry with a fresh root would merge into the first attempt's set and split
 * the members across two roots at one epoch. Keep it until the rotation is adopted, then drop it.
 */
class PendingRefounding(
    val communityId: HexKey,
    val rootEpoch: Long,
    val prevCommit: HexKey,
    val newRoot: ByteArray,
    val newControlRoot: ByteArray,
) {
    /** True when this reservation is for the rotation that leaves [priorRoot] at [rootEpoch]. */
    fun matches(
        communityId: HexKey,
        rootEpoch: Long,
        priorRoot: ByteArray,
    ): Boolean =
        this.communityId.equals(communityId, ignoreCase = true) &&
            this.rootEpoch == rootEpoch &&
            prevCommit == ConcordKeyDerivation.epochKeyCommitment(rootEpoch, priorRoot).toHexKey()
}

/**
 * Thrown when a Refounding cannot carry the whole Control Plane into the new epoch (CORD-06 §3:
 * "If the Refounder cannot reliably fold all Control events, the Refounding must be aborted").
 * [missing] names the entities (`eid` hex) whose honored head is not in the compaction.
 */
class IncompleteControlPlaneException(
    val missing: List<String>,
) : IllegalStateException("Refounding aborted: the Control Plane could not be folded in full (${missing.size} entity head(s) missing)")

/**
 * Whole-community Refounding (CORD-06 §3): rotate `community_root` to sever a
 * removed member absolutely. Public Channels and the Control/Guestbook planes all
 * derive from the root, so rolling it rotates every plane at once; Private Channels
 * (independently keyed) are rekeyed separately and are not handled here.
 *
 * A compliant Rotator performing any base rotation MUST mint the `control_root`
 * split (CORD-02 §2) — a fresh secret beside the new root, both riding the same
 * blobs — so a legacy Community upgrades as a side effect of its next Refounding,
 * with nobody deciding to.
 *
 * The builder is pure — the caller sources the retained-recipient set (from the
 * Guestbook membership minus the removed/banned) and the staff subset (the folded
 * Roster's `staffMembers()`, CORD-04 §3) and owns publish + persistence. All
 * crypto is signer-based so a NIP-46 bunker owner can refound without exposing a
 * raw key.
 */
object ConcordRefounding {
    /**
     * Builds a Refounding: compacts the Control Plane onto the new epoch's split
     * Control address and mints the base-rotation rekey blobs delivering [newRoot]
     * + the new `control_pk` to [recipientsXOnly] (the [staffXOnly] subset also
     * receiving [newControlRoot]).
     *
     * @param priorRoot         the community_root being rotated out (at [rootEpoch])
     * @param newRoot           the freshly generated 32-byte community_root
     * @param newControlRoot    the freshly minted 32-byte staff write key (CORD-02 §2)
     * @param priorControlWraps the current Control Plane's kind-1059 wraps (any subset that folds)
     * @param priorControlKeys  the Control Plane keys at [rootEpoch] (split or legacy)
     * @param recipientsXOnly   the retained members' x-only pubkeys (hex) to re-key
     * @param staffXOnly        the subset of [recipientsXOnly] that is staff (owner + Control-writing
     *                          permission holders, CORD-04 §3) and receives the 136-byte blob
     * @param authority         the rotator's `vac` citation (CORD-06 §3 "Authority"), stamped on every
     *                          rekey chunk; null when the owner rotates
     * @param mustCarry         the entity heads (`eid` hex -> version) the rotator currently honors; the
     *                          compaction must carry each at or above that version, or the Refounding
     *                          aborts with [IncompleteControlPlaneException] (CORD-06 §3)
     */
    suspend fun build(
        rotatorSigner: NostrSigner,
        communityId: ByteArray,
        priorRoot: ByteArray,
        newRoot: ByteArray,
        newControlRoot: ByteArray,
        rootEpoch: Long,
        priorControlWraps: List<Event>,
        priorControlKeys: ControlPlaneKeys,
        recipientsXOnly: List<HexKey>,
        staffXOnly: Set<HexKey>,
        createdAt: Long,
        ownerPubKey: HexKey,
        authority: AuthorityCitation? = null,
        mustCarry: Map<String, Long> = emptyMap(),
    ): RefoundingBuild {
        val newEpoch = rootEpoch + 1
        val newControlKeys = ControlPlaneKeys.forStaff(newRoot, communityId, newEpoch, newControlRoot)

        // Acquired in full BEFORE anything is published (CORD-06 §3): throws when the plane cannot be
        // carried whole, so a failed fold never leaves a half rotation as the only copy.
        val controlWraps = compactControlPlane(priorControlWraps, priorControlKeys, newControlKeys, communityId, ownerPubKey, mustCarry)

        val baseRekeyKey = ConcordKeyDerivation.baseRekeyAddress(priorRoot, communityId, newEpoch)
        val prevCommit = ConcordKeyDerivation.epochKeyCommitment(rootEpoch, priorRoot).toHexKey()
        val rekeyWraps =
            buildBaseRekeyWraps(
                rotatorSigner = rotatorSigner,
                baseRekeyKey = baseRekeyKey,
                recipientsXOnly = recipientsXOnly,
                staffXOnly = staffXOnly,
                newRoot = newRoot,
                newControlPk = newControlKeys.address.hexToByteArray(),
                newControlRoot = newControlRoot,
                newEpoch = newEpoch,
                prevEpoch = rootEpoch,
                prevCommit = prevCommit,
                createdAt = createdAt,
                authority = authority,
            )

        return RefoundingBuild(newRoot, newControlRoot, newEpoch, newControlKeys, controlWraps, rekeyWraps)
    }

    /**
     * Compacts [priorWraps] into a slim snapshot re-published under [newControlKeys]
     * (CORD-06 §3): keep only the head (highest-version) edition per entity and
     * re-wrap its **original plaintext seal** — which carries the original author's
     * signature — at the new epoch's Control address. Because Control Plane seals
     * are plaintext (CORD-02 §5), re-encryption preserves those signatures, so a
     * fresh joiner verifies the compacted state exactly as it verified the full
     * chain. [priorControlKeys] may be legacy (a pre-split epoch's compaction is
     * exactly how a Community upgrades to the split) or split; [newControlKeys]
     * must hold the new signer. A Rotator MUST NOT mirror editions to the new
     * epoch's legacy-derived address to appease stale readers — the mirror
     * re-opens exactly the member-writable surface the split closes.
     *
     * Only plaintext-sealed editions are carried ([ControlEdition.fromOpened]): an
     * encrypted-seal edition is not a Control edition (CORD-02 §5), and re-wrapping one
     * would republish it under a signature over ciphertext no reader can keep. Heads of
     * sub-kinds this client does not model (Pins, Signals, anything newer) ride through
     * verbatim like every other head, so our Refounding never erases another client's state
     * (CORD-06 §3: the compaction re-wraps each entity's current head).
     */
    fun compactControlPlane(
        priorWraps: List<Event>,
        priorControlKeys: ControlPlaneKeys,
        newControlKeys: ControlPlaneKeys,
        communityId: ByteArray,
        ownerPubKey: HexKey,
        mustCarry: Map<String, Long> = emptyMap(),
    ): List<Event> {
        // entity coordinate -> every edition we can open, paired with its verified seal.
        val byCoordinate = HashMap<String, MutableList<Pair<ControlEdition, Event>>>()
        for (wrap in priorWraps) {
            val opened = ConcordStreamEnvelope.openOrNull(wrap, priorControlKeys) ?: continue
            val edition = ControlEdition.fromOpened(opened) ?: continue
            val coord = edition.vsk + ":" + edition.entityIdHex
            byCoordinate.getOrPut(coord) { ArrayList() }.add(edition to opened.seal)
        }

        // The head to carry forward is the one every READER honors — the authority-gated head — not
        // the highest version and not the bare structural chain head.
        //
        // Raw highest version made an honest rotator the delivery mechanism for a disconnected stray:
        // an edition minted at an arbitrary version never joins the chain, but it won that comparison
        // and was re-wrapped into the new epoch as the entity's whole history (B1 in
        // `docs/concord-soft-ban-audit.md`). The bare chain walk is *worse*, and this is the trap:
        // with no floor it anchors at the lowest-version edition carrying no `prev`, and after a prior
        // compaction the real head's `prev` dangles by design — so a forged `version = 1, prev = null`
        // decoy outranks a genuine v50→v52 chain and, because nothing here checks signatures, becomes
        // the entity's entire carried-forward state. A forged empty banlist would erase every ban.
        //
        // Gating on the owner-rooted roster is the only selection that cannot be gamed by an
        // unprivileged author, and it is exactly what ConcordCommunityState.fold would seat, so the
        // compacted epoch starts where the previous one left off.
        val editions = byCoordinate.values.flatten()
        val honored = ConcordCommunityState.authorizedHeads(editions.map { it.first }, communityId, ownerPubKey)
        val out = ArrayList<Event>(honored.size)
        val missing = ArrayList<String>()
        for ((entity, floor) in honored) {
            val head = floor.known
            val seal = head?.let { h -> editions.firstOrNull { it.first.rumorId == h.rumorId }?.second }
            if (seal == null) {
                // A head we honor whose signed seal we cannot re-wrap would silently drop the entity
                // from the new epoch: abort instead (fold-all-or-abort, CORD-06 §3).
                missing.add(entity)
                continue
            }
            out.add(ConcordStreamEnvelope.wrapSeal(seal, newControlKeys, createdAt = seal.createdAt))
        }
        // Every head the caller already folds must survive at or above the version it honors: a
        // shorter compaction means the fetch we compacted from was partial, and publishing it would
        // roll the community back for every member who follows the new epoch.
        for ((entity, version) in mustCarry) {
            val carried = honored[entity]?.known?.version
            if (carried == null || carried < version) missing.add(entity)
        }
        if (missing.isNotEmpty()) throw IncompleteControlPlaneException(missing.distinct())
        return out
    }

    /**
     * The version of every entity head [editions] honor (`eid` hex -> version) — what a
     * Refounder passes as `mustCarry`, so the compaction aborts rather than drop a head the
     * Refounder itself folds (CORD-06 §3).
     */
    fun headVersions(
        editions: Collection<ControlEdition>,
        communityId: ByteArray,
        ownerPubKey: HexKey,
    ): Map<String, Long> = ConcordCommunityState.authorizedHeads(editions, communityId, ownerPubKey).mapValues { it.value.version }

    /**
     * Mints the base-rotation rekey blobs delivering [newRoot] + [newControlPk] to
     * [recipientsXOnly] — the [staffXOnly] subset also receiving [newControlRoot]
     * in the 136-byte staff form (CORD-06 §1) — chunked by count and bytes
     * ([ConcordRekey.chunkBlobs], 1-based `chunk` tags, [authority] cited on every
     * chunk) and wrapped (encrypted seal,
     * rotator-signed) on the [baseRekeyKey] address so every current member — who
     * precomputes that address from the prior root — receives it live.
     */
    suspend fun buildBaseRekeyWraps(
        rotatorSigner: NostrSigner,
        baseRekeyKey: GroupKey,
        recipientsXOnly: List<HexKey>,
        staffXOnly: Set<HexKey>,
        newRoot: ByteArray,
        newControlPk: ByteArray,
        newControlRoot: ByteArray,
        newEpoch: Long,
        prevEpoch: Long,
        prevCommit: HexKey,
        createdAt: Long,
        authority: AuthorityCitation? = null,
    ): List<Event> {
        if (recipientsXOnly.isEmpty()) return emptyList()
        val staffLower = staffXOnly.mapTo(HashSet()) { it.lowercase() }
        val blobs =
            recipientsXOnly.map { recipient ->
                ConcordRekey.blobForSigner(
                    rotatorSigner = rotatorSigner,
                    recipientXOnly = recipient.hexToByteArray(),
                    scopeId = ConcordRekey.ROOT_SCOPE,
                    newEpoch = newEpoch,
                    newKey = newRoot,
                    newControlPk = newControlPk,
                    newControlRoot = if (recipient.lowercase() in staffLower) newControlRoot else null,
                )
            }
        // The envelope is measured once at the widest `chunk` tag this rotation could carry.
        val widest = blobs.size.coerceAtLeast(1)
        val envelopeTags = ConcordRekey.tags(ConcordRekey.ROOT_SCOPE, newEpoch, prevEpoch, prevCommit, widest, widest, authority)
        val envelope =
            RumorAssembler
                .assembleRumor<Event>(rotatorSigner.pubKey, createdAt, ConcordRekey.KIND, envelopeTags, "")
                .toJson()
                .encodeToByteArray()
                .size
        val chunks = ConcordRekey.chunkBlobs(blobs, envelope)
        val total = chunks.size
        return chunks.mapIndexed { index, chunk ->
            val tags = ConcordRekey.tags(ConcordRekey.ROOT_SCOPE, newEpoch, prevEpoch, prevCommit, index + 1, total, authority)
            val rumor = RumorAssembler.assembleRumor<Event>(rotatorSigner.pubKey, createdAt, ConcordRekey.KIND, tags, ConcordRekey.encodeContent(chunk))
            ConcordStreamEnvelope.wrap(rumor, baseRekeyKey, rotatorSigner, encrypted = true, createdAt = createdAt)
        }
    }

    /**
     * Receives a base rotation for the member behind [recipientSigner]: opens the
     * kind-3303 [wraps] at the member's next base-rekey address ([baseRekeyKey]),
     * verifies each is a well-formed root rotation to `rootEpoch + 1` whose
     * `prevepoch`/`prevcommit` continue the [priorRoot] the member holds, and returns
     * the delivered new root and Control Plane keys with the rotator's real pubkey and
     * `vac` citation, so the caller can authorize it against the folded roster.
     *
     * A rekey rumor must ride an **encrypted** (20013) seal (CORD-02 §5, Appendix B); a
     * malformed `chunk` tag (0-based, `i > n`, non-decimal) or `vac` tag drops the
     * chunk, and a rotator whose chunks cite different Grants is distrusted whole. A
     * staff blob's delivered secret must derive to exactly the delivered `control_pk`
     * (CORD-02 §5) — a mismatched pair is refused rather than adopting a plane split
     * from its readers.
     *
     * Race convergence (CORD-06 §3): among the candidates [accept] admits (the
     * caller's authority check — authorize BEFORE converging, or an unauthorized
     * lower root would win), the lexicographically lowest new base key wins; the
     * control pair rides the winner's blob and is never compared.
     *
     * Null if no chunk carries this member's blob — which only means "removed" once
     * the caller confirms it holds every chunk of the rotation.
     */
    suspend fun findNewRoot(
        wraps: List<Event>,
        baseRekeyKey: GroupKey,
        recipientSigner: NostrSigner,
        communityId: ByteArray,
        priorRoot: ByteArray,
        rootEpoch: Long,
        accept: (ReceivedRefounding) -> Boolean = { true },
    ): ReceivedRefounding? = converge(findNewRoots(wraps, baseRekeyKey, recipientSigner, communityId, priorRoot, rootEpoch).filter(accept))

    /**
     * Every candidate rotation delivering this member a new root from [priorRoot] at
     * [rootEpoch] (one per rotator; see [findNewRoot] for the checks), unauthorized and
     * unconverged. Callers normally want [findNewRoot].
     */
    suspend fun findNewRoots(
        wraps: List<Event>,
        baseRekeyKey: GroupKey,
        recipientSigner: NostrSigner,
        communityId: ByteArray,
        priorRoot: ByteArray,
        rootEpoch: Long,
    ): List<ReceivedRefounding> {
        val newEpoch = rootEpoch + 1
        val expectedScope = ConcordRekey.ROOT_SCOPE.toHexKey()
        val expectedCommit = ConcordKeyDerivation.epochKeyCommitment(rootEpoch, priorRoot).toHexKey()

        // Pass 1: the well-formed chunks of this continuity point, grouped by rotator.
        val byRotator = LinkedHashMap<HexKey, MutableList<RekeyChunk>>()
        for (wrap in wraps) {
            val opened = ConcordStreamEnvelope.openOrNull(wrap, baseRekeyKey) ?: continue
            // Rekey seals are encrypted (CORD-02 §5): a plaintext seal would expose the rotator.
            if (opened.sealKind != ConcordStreamEnvelope.KIND_SEAL_ENCRYPTED) continue
            val rumor = opened.rumor
            if (rumor.kind != ConcordRekey.KIND) continue
            if (rumor.tags.firstTagValue(ConcordRekey.TAG_SCOPE)?.lowercase() != expectedScope) continue
            if (rumor.tags.firstTagValue(ConcordRekey.TAG_NEWEPOCH)?.toLongOrNull() != newEpoch) continue
            if (rumor.tags.firstTagValue(ConcordRekey.TAG_PREVEPOCH)?.toLongOrNull() != rootEpoch) continue
            if (rumor.tags.firstTagValue(ConcordRekey.TAG_PREVCOMMIT)?.lowercase() != expectedCommit) continue
            if (ConcordRekey.chunkOf(rumor.tags) == null) continue
            // A present-but-malformed citation is a corrupt chunk; absent means the owner acts.
            val vacTag = rumor.tags.firstOrNull { it.isNotEmpty() && it[0] == VacTag.TAG_NAME }
            val citation = if (vacTag == null) null else VacTag.parse(vacTag) ?: continue
            byRotator.getOrPut(opened.author.lowercase()) { ArrayList() }.add(RekeyChunk(opened.author, rumor.content, citation))
        }

        // Pass 2: per rotator, find this member's blob.
        val out = ArrayList<ReceivedRefounding>()
        for ((_, chunks) in byRotator) {
            // Every chunk of one rotation must cite the same Grant; a disagreeing set is distrusted.
            val citations = chunks.map { c -> c.citation?.let { VacTag.assemble(it).joinToString(",") } }.distinct()
            if (citations.size > 1) continue
            val rotator = chunks.first().rotator
            val rotatorXOnly = rotator.hexToByteArray()
            for (chunk in chunks) {
                val blobs = ConcordRekey.decodeContent(chunk.content)
                val payload = ConcordRekey.findPayloadWithSigner(blobs, recipientSigner, rotatorXOnly, ConcordRekey.ROOT_SCOPE, newEpoch) ?: continue
                val controlRoot = payload.newControlRoot
                val controlPk = payload.newControlPk
                if (controlRoot != null && controlPk != null) {
                    // The staff derive-check (CORD-06 §1): refuse a pair whose secret does not
                    // derive to the pk the other members were handed — fails closed.
                    val derived = ConcordKeyDerivation.controlSignerKey(controlRoot, communityId, newEpoch).publicKey
                    if (!derived.contentEquals(controlPk)) continue
                }
                out.add(ReceivedRefounding(payload.newKey, newEpoch, rotator, controlPk, controlRoot, chunk.citation))
                break
            }
        }
        return out
    }

    private class RekeyChunk(
        val rotator: HexKey,
        val content: String,
        val citation: AuthorityCitation?,
    )

    /**
     * The winner among authorized candidates racing to one epoch (CORD-06 §3): the
     * lexicographically lowest new base key. Every client computes the same winner, so
     * concurrent Refoundings converge; null on no candidates.
     */
    fun converge(candidates: List<ReceivedRefounding>): ReceivedRefounding? = candidates.minWithOrNull { a, b -> compareKeys(a.newRoot, b.newRoot) }

    /** Unsigned lexicographic order of two keys — the order the convergence rule compares in. */
    fun compareKeys(
        a: ByteArray,
        b: ByteArray,
    ): Int {
        for (i in 0 until minOf(a.size, b.size)) {
            val x = a[i].toInt() and 0xFF
            val y = b[i].toInt() and 0xFF
            if (x != y) return x - y
        }
        return a.size - b.size
    }

    /**
     * The down-only heal (CORD-06 §3): true when [candidate] should replace the [held] root
     * of the same epoch — only a **strictly lower** sibling does, so a flaky fetch that
     * returns only the higher sibling can never re-fork a settled epoch.
     */
    fun healsTo(
        held: ByteArray,
        candidate: ByteArray,
    ): Boolean = compareKeys(candidate, held) < 0

    /**
     * The held roots a client folds Control from: per epoch, the lowest key — the one the
     * convergence rule settled on. A higher sibling at the same epoch is a losing fork's root,
     * kept only so the messages sent into that fork stay readable (CORD-06 §3: "Both forks'
     * keys are retained"); its Control Plane is not the community's.
     */
    fun canonicalHeldRoots(heldRoots: List<HeldRoot>): List<HeldRoot> =
        heldRoots
            .groupBy { it.epoch }
            .values
            .mapNotNull { sameEpoch -> sameEpoch.minWithOrNull { a, b -> a.key.lowercase().compareTo(b.key.lowercase()) } }

    /**
     * The keys for the Refounding that leaves [priorRoot] at [rootEpoch]: [pending] when it
     * was reserved for exactly this rotation (a retry — CORD-06 §3 requires every step to be
     * idempotent, so a retry must re-deliver the SAME root), a fresh pair otherwise. The
     * caller persists the result before publishing anything and drops it once adopted.
     */
    fun reserveKeys(
        pending: PendingRefounding?,
        communityId: HexKey,
        rootEpoch: Long,
        priorRoot: ByteArray,
    ): PendingRefounding {
        if (pending != null && pending.matches(communityId, rootEpoch, priorRoot)) return pending
        return PendingRefounding(
            communityId = communityId.lowercase(),
            rootEpoch = rootEpoch,
            prevCommit = ConcordKeyDerivation.epochKeyCommitment(rootEpoch, priorRoot).toHexKey(),
            newRoot = RandomInstance.bytes(32),
            newControlRoot = RandomInstance.bytes(32),
        )
    }
}
