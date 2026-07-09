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
import com.vitorpamplona.quartz.concord.crypto.ConcordKeyDerivation
import com.vitorpamplona.quartz.concord.events.ConcordKinds
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.hexToByteArrayOrNull
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerSync
import com.vitorpamplona.quartz.nip44Encryption.Nip44
import com.vitorpamplona.quartz.utils.RandomInstance

/** A freshly minted public invite link: the shareable URL, the link keys, and the bundle to publish. */
class MintedInviteLink(
    val url: String,
    val linkSignerPubKey: String,
    val linkSignerPrivKey: ByteArray,
    val token: ByteArray,
    val bundleEvent: Event,
)

/**
 * The public invite bundle (CORD-05): a kind-33301 addressable event whose
 * content is the [CommunityInvite] NIP-44-encrypted under the bundle key derived
 * from the link's 16-byte unlock token. The event is signed by a per-link
 * `link_signer` keypair (so re-posting refreshes keys) and tagged
 * `["d",""],["vsk","6"]`.
 *
 * A server that indexes the naddr never holds the token, so it can never open the
 * bundle. Pinned to the Concord v2 reference client.
 */
object ConcordInviteBundle {
    const val KIND = ConcordKinds.INVITE_BUNDLE
    const val TAG_D = "d"
    const val TAG_VSK = "vsk"
    const val VSK_LIVE = "6"

    private fun json(invite: CommunityInvite) = ConcordJson.instance.encodeToString(CommunityInvite.serializer(), invite)

    /** Builds a kind-33301 bundle event carrying [invite], encrypted under [token] and signed by [linkSignerPrivKey]. */
    fun build(
        linkSignerPrivKey: ByteArray,
        token: ByteArray,
        invite: CommunityInvite,
        createdAt: Long,
    ): Event {
        val bundleKey = ConcordKeyDerivation.inviteBundleKey(token)
        val content = Nip44.v2.encrypt(json(invite), bundleKey).encodePayload()
        val signer = NostrSignerSync(KeyPair(privKey = linkSignerPrivKey))
        return signer.signNormal(createdAt, KIND, arrayOf(arrayOf(TAG_D, ""), arrayOf(TAG_VSK, VSK_LIVE)), content)
    }

    /** Decrypts a kind-33301 bundle [event] with the link [token], or null if it isn't a valid bundle. */
    fun parse(
        event: Event,
        token: ByteArray,
    ): CommunityInvite? {
        if (event.kind != KIND) return null
        return try {
            val bundleKey = ConcordKeyDerivation.inviteBundleKey(token)
            ConcordJson.decodeOrNull<CommunityInvite>(Nip44.v2.decrypt(event.content, bundleKey))
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Validates that an [invite]'s owner + salt actually reproduce its
     * community_id (CORD-02 self-certification), so a bundle can't smuggle a false
     * owner or a fake key for a real community.
     */
    fun validate(invite: CommunityInvite): Boolean {
        val owner = invite.owner.hexToByteArrayOrNull() ?: return false
        val salt = invite.ownerSalt.hexToByteArrayOrNull() ?: return false
        return ConcordKeyDerivation.communityId(owner, salt).toHexKey() == invite.communityId
    }

    /** True if the invite has an expiry in the past (blocks joining; preview still renders). Time in unix ms. */
    fun isExpired(
        invite: CommunityInvite,
        nowMs: Long,
    ): Boolean = invite.expiresAt?.let { it < nowMs } ?: false

    /**
     * Mints a complete public invite link for [invite]: generates a fresh 16-byte
     * token and a per-link signer, builds the bundle event and the shareable
     * `{base}/invite/{naddr}#{fragment}` URL (with optional bootstrap [relays]).
     */
    fun mintLink(
        base: String,
        invite: CommunityInvite,
        createdAt: Long,
        relays: List<String>? = null,
    ): MintedInviteLink {
        val token = RandomInstance.bytes(16)
        val linkSigner = KeyPair()
        val bundleEvent = build(linkSigner.privKey!!, token, invite, createdAt)
        val url = ConcordInviteLink.buildUrl(base, linkSigner.pubKey.toHexKey(), token, relays)
        return MintedInviteLink(url, linkSigner.pubKey.toHexKey(), linkSigner.privKey, token, bundleEvent)
    }
}
