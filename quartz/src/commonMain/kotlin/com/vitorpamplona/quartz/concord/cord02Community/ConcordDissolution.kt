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
package com.vitorpamplona.quartz.concord.cord02Community

import com.vitorpamplona.quartz.concord.cord04Roles.ControlEntityKind
import com.vitorpamplona.quartz.concord.cord04Roles.control.ControlEditionEvent
import com.vitorpamplona.quartz.concord.cord04Roles.control.tags.EidTag
import com.vitorpamplona.quartz.concord.cord04Roles.control.tags.VskTag
import com.vitorpamplona.quartz.concord.crypto.ConcordKeyDerivation
import com.vitorpamplona.quartz.concord.crypto.GroupKey
import com.vitorpamplona.quartz.concord.envelope.ConcordStreamEnvelope
import com.vitorpamplona.quartz.concord.envelope.OpenedStreamEvent
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.hexToByteArray
import com.vitorpamplona.quartz.nip01Core.signers.NostrSigner
import com.vitorpamplona.quartz.nip01Core.signers.eventTemplate
import com.vitorpamplona.quartz.nip59Giftwrap.rumors.RumorAssembler
import com.vitorpamplona.quartz.utils.TimeUtils

/**
 * Community Dissolution (CORD-02 §9): the owner-signed, chainless tombstone that ends a
 * Community for good.
 *
 * The tombstone is a kind-3308 rumor carrying only `["vsk","10"]` and `["eid", community_id]`,
 * plaintext-sealed (20014) by the owner and wrapped at [planeKey] — an address derived from the
 * `community_id` alone, so no epoch or key is needed to find it and a Refounding can never strand
 * the grave. It is **not** a Control Plane edition: it has no `ev`/`ep`/`vac`, and presence of one
 * valid tombstone is the whole state.
 *
 * ## The `eid` binding is the security boundary
 *
 * The plane is public: anyone holding the `community_id` (it ships in every invite) derives the
 * whole keypair and can sign wraps at it. The one thing they cannot make is an owner-signed
 * `vsk 10` rumor — but a plaintext seal re-wraps verbatim, so an owner's genuine tombstone for
 * community X could be lifted and re-wrapped at the address of any other community Y the same
 * owner runs. [isTombstoneFor] therefore requires `eid == community_id`, and refuses anything
 * else, including the all-zero placeholder earlier spec revisions used. Refusing that legacy
 * value leaves an old dissolution reading alive (the owner re-dissolves); accepting it lets any
 * multi-community owner's other communities be killed irrecoverably.
 */
object ConcordDissolution {
    /** The dissolution tombstone's address + keys for [communityIdHex] (CORD-02 §9, A.6). */
    fun planeKey(communityIdHex: HexKey): GroupKey = ConcordKeyDerivation.dissolvedPlaneKey(communityIdHex.hexToByteArray())

    /** The unsigned tombstone rumor for [communityIdHex], authored by [ownerPubKey]. */
    fun rumor(
        ownerPubKey: HexKey,
        communityIdHex: HexKey,
        createdAt: Long = TimeUtils.now(),
    ): Event =
        RumorAssembler.assembleRumor(
            ownerPubKey,
            // Chainless: exactly vsk + eid, no ev/ep/vac (CORD-02 §9).
            eventTemplate<ControlEditionEvent>(ControlEditionEvent.KIND, "", createdAt) {
                add(VskTag.assemble(ControlEntityKind.DISSOLVED))
                add(EidTag.assemble(communityIdHex.hexToByteArray()))
            },
        )

    /**
     * Seals the tombstone with [ownerSigner] (plaintext 20014, so the owner's signature survives
     * any re-wrap) and wraps it at the community's dissolved address. Only the owner's signature
     * counts, so a non-owner [ownerSigner] produces a wrap every verifier ignores.
     */
    suspend fun build(
        ownerSigner: NostrSigner,
        communityIdHex: HexKey,
        createdAt: Long = TimeUtils.now(),
    ): Event {
        val plane = planeKey(communityIdHex)
        val seal = ConcordStreamEnvelope.seal(rumor(ownerSigner.pubKey, communityIdHex, createdAt), plane, ownerSigner, encrypted = false)
        return ConcordStreamEnvelope.wrapSeal(seal, plane, createdAt = createdAt)
    }

    /**
     * Whether [opened] is a valid tombstone for [communityIdHex] owned by [ownerPubKey]: a
     * plaintext-sealed kind-3308 rumor, authored (seal signer) by the owner, `vsk 10`, and an
     * `eid` equal to this community's id. Anything else — a wrong or all-zero `eid`, an encrypted
     * seal, another author — is noise.
     */
    fun isTombstoneFor(
        opened: OpenedStreamEvent,
        communityIdHex: HexKey,
        ownerPubKey: HexKey,
    ): Boolean {
        if (!opened.author.equals(ownerPubKey, ignoreCase = true)) return false
        if (opened.sealKind != ConcordStreamEnvelope.KIND_SEAL_PLAINTEXT) return false
        val rumor = opened.rumor
        if (rumor.kind != ControlEditionEvent.KIND) return false
        val vsk = rumor.tags.firstOrNull { it.size >= 2 && it[0] == VskTag.TAG_NAME }?.get(1)
        val eid = rumor.tags.firstOrNull { it.size >= 2 && it[0] == EidTag.TAG_NAME }?.get(1)
        return vsk == ControlEntityKind.DISSOLVED.wire && eid != null && eid.equals(communityIdHex, ignoreCase = true)
    }

    /** Opens [wrap] at [communityIdHex]'s dissolved address and checks it with [isTombstoneFor]. */
    fun isTombstoneWrap(
        wrap: Event,
        communityIdHex: HexKey,
        ownerPubKey: HexKey,
    ): Boolean {
        val opened = ConcordStreamEnvelope.openOrNull(wrap, planeKey(communityIdHex)) ?: return false
        return isTombstoneFor(opened, communityIdHex, ownerPubKey)
    }

    /** True when any of [wraps] is a valid tombstone for this community (CORD-02 §9). */
    fun isDissolved(
        wraps: Collection<Event>,
        communityIdHex: HexKey,
        ownerPubKey: HexKey,
    ): Boolean {
        if (wraps.isEmpty()) return false
        val plane = planeKey(communityIdHex)
        return wraps.any { wrap ->
            val opened = ConcordStreamEnvelope.openOrNull(wrap, plane) ?: return@any false
            isTombstoneFor(opened, communityIdHex, ownerPubKey)
        }
    }
}
