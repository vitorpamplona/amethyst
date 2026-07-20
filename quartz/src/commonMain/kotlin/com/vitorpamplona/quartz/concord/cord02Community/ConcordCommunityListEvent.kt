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

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.nip01Core.core.Address
import com.vitorpamplona.quartz.nip01Core.core.BaseReplaceableEvent
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.signers.NostrSigner
import com.vitorpamplona.quartz.utils.TimeUtils

/**
 * The member's private, self-encrypted list of joined Concord communities (kind
 * 13302, CORD-05). A replaceable event whose
 * `content` is the NIP-44 self-encryption of the [ConcordCommunityListEntry] JSON
 * — including each community's secrets (`community_root`, salt, epoch,
 * private-channel keys), so a single event both syncs membership across devices
 * and carries the keys needed to re-derive every plane.
 *
 * This is the Concord analog of NIP-29's kind-10009 `SimpleGroupListEvent` and
 * NIP-17's chatroom home base; it is what the Account's `ConcordChannelListState`
 * observes. Only the owner's key decrypts it, so relays store ciphertext.
 */
@Immutable
class ConcordCommunityListEvent(
    id: HexKey,
    pubKey: HexKey,
    createdAt: Long,
    tags: Array<Array<String>>,
    content: String,
    sig: HexKey,
) : BaseReplaceableEvent(id, pubKey, createdAt, KIND, tags, content, sig) {
    override fun isContentEncoded() = true

    /** Decrypts this list's entries with [signer], or empty on failure / wrong key. */
    suspend fun decrypt(signer: NostrSigner): List<ConcordCommunityListEntry> = decryptDocument(signer).entries

    /**
     * Decrypts the whole document with [signer] — entries plus the residue (unknown keys and
     * tombstones) that a read-modify-write must hand back untouched. Use this, not [decrypt],
     * whenever the result will be re-encoded: [decrypt] discards the document-level residue.
     */
    suspend fun decryptDocument(signer: NostrSigner): ConcordCommunityListDocument =
        try {
            ConcordCommunityList.decodeDocument(signer.nip44Decrypt(content, signer.pubKey))
        } catch (_: Exception) {
            ConcordCommunityListDocument(emptyList())
        }

    companion object {
        const val KIND = 13302
        const val ALT = "Private list of joined Concord communities"

        /** The replaceable coordinate for a member's list: `(13302, pubkey, "")`. */
        fun createAddress(pubKey: HexKey) = Address(KIND, pubKey, "")

        /** Builds a signed, self-encrypted list event from [entries]. */
        suspend fun create(
            signer: NostrSigner,
            entries: List<ConcordCommunityListEntry>,
            createdAt: Long = TimeUtils.now(),
            residue: ConcordListResidue = ConcordListResidue.EMPTY,
        ): ConcordCommunityListEvent {
            val content = signer.nip44Encrypt(ConcordCommunityList.encode(entries, residue), signer.pubKey)
            return signer.sign(createdAt, KIND, emptyArray(), content)
        }
    }
}
