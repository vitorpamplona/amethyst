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

import com.vitorpamplona.quartz.concord.cord04Roles.ControlEdition
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
) {
    /** True when this was a legacy pre-split rotation (72-byte base blob). */
    val legacy: Boolean get() = newControlPk == null
}

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
    ): RefoundingBuild {
        val newEpoch = rootEpoch + 1
        val newControlKeys = ControlPlaneKeys.forStaff(newRoot, communityId, newEpoch, newControlRoot)

        val controlWraps = compactControlPlane(priorControlWraps, priorControlKeys, newControlKeys)

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
     */
    fun compactControlPlane(
        priorWraps: List<Event>,
        priorControlKeys: ControlPlaneKeys,
        newControlKeys: ControlPlaneKeys,
    ): List<Event> {
        // entity coordinate -> (head edition, its verified seal)
        val heads = HashMap<String, Pair<ControlEdition, Event>>()
        for (wrap in priorWraps) {
            val opened = ConcordStreamEnvelope.openOrNull(wrap, priorControlKeys) ?: continue
            val edition = ControlEdition.fromRumor(opened.rumor) ?: continue
            val coord = edition.entityKind.wire + ":" + edition.entityIdHex
            val current = heads[coord]
            if (current == null || edition.version > current.first.version) {
                heads[coord] = edition to opened.seal
            }
        }
        return heads.values.map { (_, seal) -> ConcordStreamEnvelope.wrapSeal(seal, newControlKeys, createdAt = seal.createdAt) }
    }

    /**
     * Mints the base-rotation rekey blobs delivering [newRoot] + [newControlPk] to
     * [recipientsXOnly] — the [staffXOnly] subset also receiving [newControlRoot]
     * in the 136-byte staff form (CORD-06 §1) — chunked at
     * [ConcordRekey.MAX_BLOBS_PER_CHUNK] and wrapped (encrypted seal,
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
        val chunks = blobs.chunked(ConcordRekey.MAX_BLOBS_PER_CHUNK)
        val total = chunks.size
        return chunks.mapIndexed { index, chunk ->
            val tags = ConcordRekey.tags(ConcordRekey.ROOT_SCOPE, newEpoch, prevEpoch, prevCommit, index, total)
            val rumor = RumorAssembler.assembleRumor<Event>(rotatorSigner.pubKey, createdAt, ConcordRekey.KIND, tags, ConcordRekey.encodeContent(chunk))
            ConcordStreamEnvelope.wrap(rumor, baseRekeyKey, rotatorSigner, encrypted = true, createdAt = createdAt)
        }
    }

    /**
     * Receives a base rotation for the member behind [recipientSigner]: opens the
     * kind-3303 [wraps] at the member's next base-rekey address ([baseRekeyKey]),
     * verifies each is a well-formed root rotation to [newEpoch] whose `prevcommit`
     * continues the [priorRoot] the member holds, and returns the delivered new
     * root and Control Plane keys (with the rotator's real pubkey, so the caller
     * can authorize it against the folded roster). A staff blob's delivered secret
     * must derive to exactly the delivered `control_pk` (CORD-02 §5) — a
     * mismatched pair is refused rather than adopting a plane split from its
     * readers. Null if no chunk carries this member's blob — which only means
     * "removed" once the caller confirms it holds every chunk of the rotation.
     */
    suspend fun findNewRoot(
        wraps: List<Event>,
        baseRekeyKey: GroupKey,
        recipientSigner: NostrSigner,
        communityId: ByteArray,
        priorRoot: ByteArray,
        rootEpoch: Long,
    ): ReceivedRefounding? {
        val newEpoch = rootEpoch + 1
        val expectedScope = ConcordRekey.ROOT_SCOPE.toHexKey()
        val expectedCommit = ConcordKeyDerivation.epochKeyCommitment(rootEpoch, priorRoot).toHexKey()
        for (wrap in wraps) {
            val opened = ConcordStreamEnvelope.openOrNull(wrap, baseRekeyKey) ?: continue
            val rumor = opened.rumor
            if (rumor.kind != ConcordRekey.KIND) continue
            if (rumor.tags.firstTagValue(ConcordRekey.TAG_SCOPE) != expectedScope) continue
            if (rumor.tags.firstTagValue(ConcordRekey.TAG_NEWEPOCH)?.toLongOrNull() != newEpoch) continue
            if (rumor.tags.firstTagValue(ConcordRekey.TAG_PREVCOMMIT) != expectedCommit) continue

            val blobs = ConcordRekey.decodeContent(rumor.content)
            val rotatorXOnly = opened.author.hexToByteArray()
            val payload = ConcordRekey.findPayloadWithSigner(blobs, recipientSigner, rotatorXOnly, ConcordRekey.ROOT_SCOPE, newEpoch) ?: continue
            val controlRoot = payload.newControlRoot
            val controlPk = payload.newControlPk
            if (controlRoot != null && controlPk != null) {
                // The staff derive-check (CORD-06 §1): refuse a pair whose secret does not
                // derive to the pk the other members were handed — fails closed.
                val derived = ConcordKeyDerivation.controlSignerKey(controlRoot, communityId, newEpoch).publicKey
                if (!derived.contentEquals(controlPk)) continue
            }
            return ReceivedRefounding(payload.newKey, newEpoch, opened.author, controlPk, controlRoot)
        }
        return null
    }
}
