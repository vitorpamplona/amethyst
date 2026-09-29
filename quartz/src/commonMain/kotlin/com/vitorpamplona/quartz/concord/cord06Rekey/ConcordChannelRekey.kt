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

import com.vitorpamplona.quartz.concord.cord04Roles.AuthorityCitation
import com.vitorpamplona.quartz.concord.cord04Roles.control.tags.VacTag
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
import com.vitorpamplona.quartz.utils.RandomInstance

/**
 * One Private Channel's rotation as a receiver sees it (CORD-06 §2): the kind-3303 chunks one
 * [rotator] published at one ([newEpoch], [prevCommit]) — two Rotators concurrently rekeying the
 * same epoch never merge into one set. [complete] once every chunk `1..total` is held; a missing
 * chunk is never a removal. [createdAt] is the newest chunk's `created_at` (seconds), which a
 * receiver compares against its join time: a rotation predating the join is not an exclusion.
 */
class ChannelRotation(
    val rotator: HexKey,
    val channelIdHex: HexKey,
    val newEpoch: Long,
    val prevEpoch: Long,
    val prevCommit: HexKey,
    val total: Int,
    val chunks: Map<Int, List<RekeyBlob>>,
    val authority: AuthorityCitation?,
    val createdAt: Long,
) {
    val complete: Boolean get() = (1..total).all { it in chunks }

    /** Every blob across the held chunks. */
    fun blobs(): List<RekeyBlob> = chunks.values.flatten()
}

/** A key the receiver stepped off while walking a channel's rotations: it still reads its own era. */
class SteppedChannelKey(
    val key: ByteArray,
    val epoch: Long,
    /** When the rotation that superseded it was published (seconds). */
    val retiredAt: Long,
)

/** What a Private Channel's pending rotations mean for the key this account holds (CORD-06 §2). */
sealed class ChannelRekeyOutcome {
    /** Nothing to act on (no honored complete rotation past the held epoch, or one we cannot yet verify). */
    object None : ChannelRekeyOutcome()

    /** Adopt [key] at [epoch]; [steppedOver] are the keys the walk left behind, newest first. */
    class Adopted(
        val key: ByteArray,
        val epoch: Long,
        val steppedOver: List<SteppedChannelKey>,
    ) : ChannelRekeyOutcome()

    /**
     * A complete, honored rotation to [epoch] that could have carried our blob did not: we were cut
     * from the channel. Drop the key and record the cut, so a stale bundle cannot restore it.
     */
    class Removed(
        val epoch: Long,
    ) : ChannelRekeyOutcome()
}

/**
 * Single-channel Rekeys (CORD-06 §1-2): rotate one Private Channel's independent key to exactly the
 * members who should keep reading it, and follow such a rotation as a member.
 *
 *  - **Address.** A channel rotation to `new_epoch` rides `group_key("concord/rekey-pseudonym",
 *    community_root, channel_id, new_epoch)` — keyed by the *community* root, not the channel key
 *    (CORD-02 derivation table), so every member can precompute it. A Refounding seals its channel
 *    rekeys under the **prior** root (CORD-06 §3), so a receiver watches under the root it holds and
 *    the one before it.
 *  - **Blob.** 72 bytes, `scope_id[32] ‖ epoch_be[8] ‖ new_key[32]`, with the channel id as the
 *    scope ([RekeyPayload]); scope and epoch are verified against the tags before adoption.
 *  - **Continuity.** `prevcommit` is the epoch-key commitment over the channel key being replaced at
 *    its epoch; a receiver adopts only a rotation off the exact key it holds, walking several
 *    rotations in one pass when it missed some.
 *  - **Authority.** A single-channel Rekey needs `MANAGE_CHANNELS`, a Refounding's needs `BAN`, and
 *    the Rotator must strictly outrank every removed target — so a receiver treats "no blob for me"
 *    as a removal only from a Rotator that outranks it (Armada `useChannelRekeyWatch`).
 *
 * Pinned byte-for-byte to the reference client's `lib/rekey.ts` (`encodeWrappedKey`,
 * `buildRekeyRumors`, `channelRekeyGroupKey`) and walk (`useChannelRekeyWatch`).
 */
object ConcordChannelRekey {
    /**
     * How many channel epochs past the held one a member watches. Watching only `held + 1` strands
     * anyone who missed a rotation — they'd never learn they were removed. The reference client's
     * `CHANNEL_REKEY_LOOKAHEAD`.
     */
    const val LOOKAHEAD = 8

    /** The rekey address a rotation of [channelId] to [newEpoch] rides, sealed under [sealingRoot]. */
    fun address(
        sealingRoot: ByteArray,
        channelId: ByteArray,
        newEpoch: Long,
    ): GroupKey = ConcordKeyDerivation.channelRekeyAddress(sealingRoot, channelId, newEpoch)

    /** The `prevcommit` a rotation off [heldKey] at [heldEpoch] carries (CORD-02 A.5). */
    fun prevCommit(
        heldEpoch: Long,
        heldKey: ByteArray,
    ): HexKey = ConcordKeyDerivation.epochKeyCommitment(heldEpoch, heldKey).toHexKey()

    /** A fresh 32-byte channel key. */
    fun mintKey(): ByteArray = RandomInstance.bytes(32)

    /**
     * The kind-1059 wraps of one channel rotation: the chunked kind-3303 rumors delivering
     * [newKey] at `heldEpoch + 1` to [recipients] (the rotator should include itself, or nobody
     * could rotate the channel next time), each chunk carrying [authority] (`vac`), sealed
     * (encrypted, rotator-signed) at [address] under [sealingRoot].
     */
    suspend fun build(
        rotatorSigner: NostrSigner,
        sealingRoot: ByteArray,
        channelId: ByteArray,
        heldKey: ByteArray,
        heldEpoch: Long,
        newKey: ByteArray,
        recipients: Collection<HexKey>,
        createdAt: Long,
        authority: AuthorityCitation? = null,
    ): List<Event> {
        val newEpoch = heldEpoch + 1
        val prevCommit = prevCommit(heldEpoch, heldKey)
        val blobs =
            recipients.map { it.lowercase() }.distinct().map { recipient ->
                ConcordRekey.blobForSigner(rotatorSigner, recipient.hexToByteArray(), channelId, newEpoch, newKey)
            }
        val widest = blobs.size.coerceAtLeast(1)
        val envelopeTags = ConcordRekey.tags(channelId, newEpoch, heldEpoch, prevCommit, widest, widest, authority)
        val envelope =
            RumorAssembler
                .assembleRumor<Event>(rotatorSigner.pubKey, createdAt, ConcordRekey.KIND, envelopeTags, "")
                .toJson()
                .encodeToByteArray()
                .size
        val chunks = ConcordRekey.chunkBlobs(blobs, envelope)
        val stream = address(sealingRoot, channelId, newEpoch)
        return chunks.mapIndexed { index, chunk ->
            val tags = ConcordRekey.tags(channelId, newEpoch, heldEpoch, prevCommit, index + 1, chunks.size, authority)
            val rumor = RumorAssembler.assembleRumor<Event>(rotatorSigner.pubKey, createdAt, ConcordRekey.KIND, tags, ConcordRekey.encodeContent(chunk))
            ConcordStreamEnvelope.wrap(rumor, stream, rotatorSigner, encrypted = true, createdAt = createdAt)
        }
    }

    /**
     * Opens the kind-1059 [wraps] seen at channel-rekey addresses ([keys], address hex → key) and
     * groups the well-formed chunks for [channelIdHex] into [ChannelRotation]s, keyed by
     * (rotator, newepoch, prevcommit). Drops: a wrap at no known address, a plaintext seal (a rekey
     * seal is encrypted, CORD-02 §5), a non-3303 rumor, a scope other than the channel, a
     * non-decimal or 0-based chunk, a malformed `vac`. A rotation whose chunks disagree on
     * `prevepoch`, `total` or citation is distrusted whole.
     */
    fun rotations(
        wraps: Collection<Event>,
        keys: Map<HexKey, GroupKey>,
        channelIdHex: HexKey,
    ): List<ChannelRotation> {
        val scope = channelIdHex.lowercase()
        val groups = LinkedHashMap<String, MutableList<Parsed>>()
        for (wrap in wraps.distinctBy { it.id }) {
            val key = keys[wrap.pubKey] ?: continue
            val opened = ConcordStreamEnvelope.openOrNull(wrap, key) ?: continue
            if (opened.sealKind != ConcordStreamEnvelope.KIND_SEAL_ENCRYPTED) continue
            val rumor = opened.rumor
            if (rumor.kind != ConcordRekey.KIND) continue
            if (rumor.tags.firstTagValue(ConcordRekey.TAG_SCOPE)?.lowercase() != scope) continue
            val newEpoch = strictLong(rumor.tags.firstTagValue(ConcordRekey.TAG_NEWEPOCH)) ?: continue
            val prevEpoch = strictLong(rumor.tags.firstTagValue(ConcordRekey.TAG_PREVEPOCH)) ?: continue
            val prevCommit = rumor.tags.firstTagValue(ConcordRekey.TAG_PREVCOMMIT)?.lowercase() ?: continue
            if (!HEX64.matches(prevCommit)) continue
            val (index, total) = ConcordRekey.chunkOf(rumor.tags) ?: continue
            val vacTag = rumor.tags.firstOrNull { it.isNotEmpty() && it[0] == VacTag.TAG_NAME }
            val citation = if (vacTag == null) null else VacTag.parse(vacTag) ?: continue
            val rotator = opened.author.lowercase()
            groups
                .getOrPut("$rotator:$newEpoch:$prevCommit") { ArrayList() }
                .add(Parsed(rotator, newEpoch, prevEpoch, prevCommit, index, total, ConcordRekey.decodeContent(rumor.content), citation, rumor.createdAt))
        }
        return groups.values.mapNotNull { parsed ->
            val first = parsed.first()
            if (parsed.any { it.prevEpoch != first.prevEpoch || it.total != first.total }) return@mapNotNull null
            val citations = parsed.map { p -> p.citation?.let { VacTag.assemble(it).joinToString(",") } }.distinct()
            if (citations.size > 1) return@mapNotNull null
            ChannelRotation(
                rotator = first.rotator,
                channelIdHex = scope,
                newEpoch = first.newEpoch,
                prevEpoch = first.prevEpoch,
                prevCommit = first.prevCommit,
                total = first.total,
                chunks = parsed.groupBy { it.index }.mapValues { (_, same) -> same.first().blobs },
                authority = first.citation,
                createdAt = parsed.maxOf { it.createdAt },
            )
        }
    }

    /**
     * Walks [rotations] of the channel whose key this account holds ([heldKey] at [heldEpoch]) and
     * decides what to do (Armada `useChannelRekeyWatch`):
     *
     *  - only **complete** rotations past [heldEpoch] from an [honored] Rotator count;
     *  - epoch by epoch, ascending: a rotation carrying our blob **and** continuing the key we hold
     *    at that point (`prevepoch`/`prevcommit`) hands us the next key — racing Rotators at one
     *    epoch converge on the lexicographically lowest key — and the walk moves on from it;
     *  - a rotation that carries our blob off a key we can't verify is neither adoption nor removal
     *    (a gap to fetch);
     *  - a rotation with no blob for us, published at or after [joinedAtSecs] by a Rotator who
     *    [outranksMe], is the read-cut (removal needs no chain: hiding is local and safe);
     *  - a key adopted above the newest exclusion is a re-admission; otherwise the exclusion wins.
     *
     * The blob opens through [recipientSigner] (one NIP-44 decrypt: bunker-friendly).
     */
    suspend fun walk(
        rotations: List<ChannelRotation>,
        channelIdHex: HexKey,
        heldKey: ByteArray,
        heldEpoch: Long,
        recipientSigner: NostrSigner,
        joinedAtSecs: Long,
        honored: (ChannelRotation) -> Boolean,
        outranksMe: (HexKey) -> Boolean,
    ): ChannelRekeyOutcome {
        val channelId = channelIdHex.hexToByteArray()
        val byEpoch =
            rotations
                .filter { it.channelIdHex.equals(channelIdHex, ignoreCase = true) && it.newEpoch > heldEpoch && it.complete && honored(it) }
                .groupBy { it.newEpoch }
                .toSortedMap()
        if (byEpoch.isEmpty()) return ChannelRekeyOutcome.None

        var chainEpoch = heldEpoch
        var chainKey = heldKey
        var adoptedEpoch: Long? = null
        var excludedAt: Long? = null
        val stepped = ArrayList<SteppedChannelKey>()

        for ((epoch, candidates) in byEpoch) {
            var keyHere: ByteArray? = null
            var publishedHere: Long? = null
            var addressedHere = false
            for (set in candidates) {
                val locator =
                    ConcordKeyDerivation
                        .recipientLocator(set.rotator.hexToByteArray(), recipientSigner.pubKey.hexToByteArray(), channelId, epoch)
                        .toHexKey()
                if (set.blobs().none { it.locator == locator }) continue
                addressedHere = true
                // Only a rotation off the key we hold at this point can hand us the next one.
                if (set.prevEpoch != chainEpoch || set.prevCommit != prevCommit(chainEpoch, chainKey)) continue
                val payload =
                    ConcordRekey.findPayloadWithSigner(set.blobs(), recipientSigner, set.rotator.hexToByteArray(), channelId, epoch)
                // A channel blob is exactly 72 bytes; a wider (base-form) payload is malformed here.
                if (payload == null || payload.newControlPk != null) continue
                keyHere = keyHere?.let { if (ConcordRefounding.compareKeys(payload.newKey, it) < 0) payload.newKey else it } ?: payload.newKey
                publishedHere = publishedHere?.let { minOf(it, set.createdAt) } ?: set.createdAt
            }
            val next = keyHere
            if (next != null) {
                stepped.add(0, SteppedChannelKey(chainKey, chainEpoch, publishedHere ?: 0))
                chainEpoch = epoch
                chainKey = next
                adoptedEpoch = epoch
                continue
            }
            if (addressedHere) continue
            if (candidates.any { it.createdAt >= joinedAtSecs && outranksMe(it.rotator) }) excludedAt = epoch
        }

        val adopted = adoptedEpoch
        if (adopted != null && (excludedAt == null || adopted > excludedAt)) {
            return ChannelRekeyOutcome.Adopted(chainKey, adopted, stepped)
        }
        return excludedAt?.let { ChannelRekeyOutcome.Removed(it) } ?: ChannelRekeyOutcome.None
    }

    private class Parsed(
        val rotator: HexKey,
        val newEpoch: Long,
        val prevEpoch: Long,
        val prevCommit: HexKey,
        val index: Int,
        val total: Int,
        val blobs: List<RekeyBlob>,
        val citation: AuthorityCitation?,
        val createdAt: Long,
    )

    private val HEX64 = Regex("^[0-9a-f]{64}$")

    /** Strict decimal (`0|[1-9][0-9]*`), as the reference client's `isTagDecimal`. */
    private fun strictLong(value: String?): Long? {
        if (value.isNullOrEmpty() || value.length > 18 || !value.all { it in '0'..'9' }) return null
        if (value.length > 1 && value[0] == '0') return null
        return value.toLong()
    }
}
