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
package com.vitorpamplona.quartz.concord.cord05Invites

import com.vitorpamplona.quartz.concord.cord04Roles.ConcordJson
import com.vitorpamplona.quartz.concord.events.ConcordKinds
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.signers.NostrSigner
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerInternal
import com.vitorpamplona.quartz.nip59Giftwrap.rumors.RumorAssembler
import com.vitorpamplona.quartz.nip59Giftwrap.seals.SealedRumorEvent
import com.vitorpamplona.quartz.nip59Giftwrap.wraps.GiftWrapEvent

/**
 * Direct invites (CORD-05): for a known npub, the invite skips the public bundle
 * and is delivered as a standard NIP-59 giftwrap — a kind-3313 rumor carrying the
 * [CommunityInvite], sealed (kind 13) to the recipient and wrapped (kind 1059)
 * with `["p", recipient]` and a `["k", "3313"]` index tag so the recipient can
 * query for pending invites without decrypting every giftwrap.
 *
 * It cannot be revoked — the recipient holds the keys the moment it lands.
 */
object ConcordDirectInvite {
    const val KIND: Int = ConcordKinds.DIRECT_INVITE
    const val TAG_P = "p"
    const val TAG_K = "k"

    private fun json(invite: CommunityInvite) = ConcordJson.instance.encodeToString(CommunityInvite.serializer(), invite)

    /**
     * Builds a giftwrapped direct invite from [senderSigner] to [recipientPubKey].
     * Returns the kind-1059 wrap to publish to the recipient's inbox relays.
     */
    suspend fun build(
        senderSigner: NostrSigner,
        recipientPubKey: HexKey,
        invite: CommunityInvite,
        createdAt: Long,
    ): GiftWrapEvent {
        val rumor = RumorAssembler.assembleRumor<Event>(senderSigner.pubKey, createdAt, KIND, emptyArray(), json(invite))
        val seal = SealedRumorEvent.create(rumor, recipientPubKey, senderSigner, createdAt = createdAt)

        // Wrap with a random ephemeral key, adding the ["k","3313"] index tag.
        val wrapSigner = NostrSignerInternal(KeyPair())
        val content = wrapSigner.nip44Encrypt(seal.toJson(), recipientPubKey)
        return wrapSigner.sign(
            createdAt = createdAt,
            kind = GiftWrapEvent.KIND,
            tags = arrayOf(arrayOf(TAG_P, recipientPubKey), arrayOf(TAG_K, KIND.toString())),
            content = content,
        )
    }

    /**
     * Opens a direct-invite giftwrap addressed to [recipientSigner] and returns the
     * [CommunityInvite], or null if it isn't a valid direct invite for this user.
     * Callers should still [ConcordInviteBundle.validate] the result.
     */
    suspend fun parse(
        wrap: GiftWrapEvent,
        recipientSigner: NostrSigner,
    ): CommunityInvite? {
        val seal = wrap.unwrapOrNull(recipientSigner) ?: return null
        if (seal !is SealedRumorEvent) return null
        val rumor = seal.unsealOrNull(recipientSigner) ?: return null
        if (rumor.kind != KIND) return null
        return ConcordJson.decodeOrNull<CommunityInvite>(rumor.content)
    }
}
