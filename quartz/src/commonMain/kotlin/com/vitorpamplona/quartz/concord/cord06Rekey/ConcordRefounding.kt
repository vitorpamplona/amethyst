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
 * Control Plane, compacted to its per-entity head editions and re-sealed under the
 * fresh [newRoot] at [newEpoch]) and the [rekeyWraps] (kind-3303 base-rotation
 * blobs, sealed under the **prior** root, that deliver [newRoot] to every retained
 * member and to nobody else). Publish [controlWraps] first (the new epoch's state)
 * then [rekeyWraps] (the key that unlocks it).
 */
class RefoundingBuild(
    val newRoot: ByteArray,
    val newEpoch: Long,
    val controlWraps: List<Event>,
    val rekeyWraps: List<Event>,
)

/** A retained member's decrypted rekey result: the [newRoot] delivered at [newEpoch] by [rotator]. */
class ReceivedRefounding(
    val newRoot: ByteArray,
    val newEpoch: Long,
    val rotator: HexKey,
)

/**
 * Whole-community Refounding (CORD-06 §3): rotate `community_root` to sever a
 * removed member absolutely. Public Channels and the Control/Guestbook planes all
 * derive from the root, so rolling it rotates every plane at once; Private Channels
 * (independently keyed) are rekeyed separately and are not handled here.
 *
 * The builder is pure — the caller sources the retained-recipient set (from the
 * Guestbook membership minus the removed/banned) and owns publish + persistence.
 * All crypto is signer-based so a NIP-46 bunker owner can refound without exposing
 * a raw key.
 */
object ConcordRefounding {
    /**
     * Builds a Refounding: compacts the Control Plane under [newRoot] and mints the
     * base-rotation rekey blobs delivering [newRoot] to [recipientsXOnly].
     *
     * @param priorRoot         the community_root being rotated out (at [rootEpoch])
     * @param newRoot           the freshly generated 32-byte community_root
     * @param priorControlWraps the current Control Plane's kind-1059 wraps (any subset that folds)
     * @param priorControlKey   the Control Plane group key at [rootEpoch]
     * @param recipientsXOnly   the retained members' x-only pubkeys (hex) to re-key
     */
    suspend fun build(
        rotatorSigner: NostrSigner,
        communityId: ByteArray,
        priorRoot: ByteArray,
        newRoot: ByteArray,
        rootEpoch: Long,
        priorControlWraps: List<Event>,
        priorControlKey: GroupKey,
        recipientsXOnly: List<HexKey>,
        createdAt: Long,
    ): RefoundingBuild {
        val newEpoch = rootEpoch + 1
        val newControlKey = ConcordKeyDerivation.controlPlaneKey(newRoot, communityId, newEpoch)

        val controlWraps = compactControlPlane(priorControlWraps, priorControlKey, newControlKey)

        val baseRekeyKey = ConcordKeyDerivation.baseRekeyAddress(priorRoot, communityId, newEpoch)
        val prevCommit = ConcordKeyDerivation.epochKeyCommitment(rootEpoch, priorRoot).toHexKey()
        val rekeyWraps =
            buildBaseRekeyWraps(
                rotatorSigner = rotatorSigner,
                baseRekeyKey = baseRekeyKey,
                recipientsXOnly = recipientsXOnly,
                newRoot = newRoot,
                newEpoch = newEpoch,
                prevEpoch = rootEpoch,
                prevCommit = prevCommit,
                createdAt = createdAt,
            )

        return RefoundingBuild(newRoot, newEpoch, controlWraps, rekeyWraps)
    }

    /**
     * Compacts [priorWraps] into a slim snapshot re-published under [newControlKey]
     * (CORD-06 §3): keep only the head (highest-version) edition per entity and
     * re-wrap its **original plaintext seal** — which carries the original author's
     * signature — under the new root. Because Control Plane seals are plaintext
     * (CORD-02 §5), re-encryption preserves those signatures, so a fresh joiner
     * verifies the compacted state exactly as it verified the full chain.
     */
    fun compactControlPlane(
        priorWraps: List<Event>,
        priorControlKey: GroupKey,
        newControlKey: GroupKey,
    ): List<Event> {
        // entity coordinate -> (head edition, its verified seal)
        val heads = HashMap<String, Pair<ControlEdition, Event>>()
        for (wrap in priorWraps) {
            val opened = ConcordStreamEnvelope.openOrNull(wrap, priorControlKey) ?: continue
            val edition = ControlEdition.fromRumor(opened.rumor) ?: continue
            val coord = edition.entityKind.wire + ":" + edition.entityIdHex
            val current = heads[coord]
            if (current == null || edition.version > current.first.version) {
                heads[coord] = edition to opened.seal
            }
        }
        return heads.values.map { (_, seal) -> ConcordStreamEnvelope.wrapSeal(seal, newControlKey, createdAt = seal.createdAt) }
    }

    /**
     * Mints the base-rotation rekey blobs delivering [newRoot] to [recipientsXOnly],
     * chunked at [ConcordRekey.MAX_BLOBS_PER_CHUNK] and wrapped (encrypted seal,
     * rotator-signed) on the [baseRekeyKey] address so every current member — who
     * precomputes that address from the prior root — receives it live.
     */
    suspend fun buildBaseRekeyWraps(
        rotatorSigner: NostrSigner,
        baseRekeyKey: GroupKey,
        recipientsXOnly: List<HexKey>,
        newRoot: ByteArray,
        newEpoch: Long,
        prevEpoch: Long,
        prevCommit: HexKey,
        createdAt: Long,
    ): List<Event> {
        if (recipientsXOnly.isEmpty()) return emptyList()
        val blobs =
            recipientsXOnly.map { recipient ->
                ConcordRekey.blobForSigner(rotatorSigner, recipient.hexToByteArray(), ConcordRekey.ROOT_SCOPE, newEpoch, newRoot)
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
     * continues the [priorRoot] the member holds, and returns the delivered new root
     * (with the rotator's real pubkey, so the caller can authorize it against the
     * folded roster). Null if no chunk carries this member's blob — which only means
     * "removed" once the caller confirms it holds every chunk of the rotation.
     */
    suspend fun findNewRoot(
        wraps: List<Event>,
        baseRekeyKey: GroupKey,
        recipientSigner: NostrSigner,
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
            val newRoot = ConcordRekey.findNewKeyWithSigner(blobs, recipientSigner, rotatorXOnly, ConcordRekey.ROOT_SCOPE, newEpoch) ?: continue
            return ReceivedRefounding(newRoot, newEpoch, opened.author)
        }
        return null
    }
}
