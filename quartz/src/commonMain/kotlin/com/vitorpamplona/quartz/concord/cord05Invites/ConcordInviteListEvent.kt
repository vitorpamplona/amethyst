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

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.nip01Core.core.Address
import com.vitorpamplona.quartz.nip01Core.core.BaseReplaceableEvent
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.signers.NostrSigner
import com.vitorpamplona.quartz.utils.TimeUtils

/**
 * The CORD-05 **Invite List** (kind 13303): the creator's private, NIP-44 self-encrypted record of
 * every link they minted — `token` (the unlock secret and merge key) and `signer_sk` (the link
 * signer's private key) per entry.
 *
 * It exists so a link can be *refreshed*: the kind-33301 bundle is addressable and authored by the
 * link signer, so re-posting under it moves the link to the current epoch behind the same URL (e.g.
 * after a Rekey). Without the list a client cannot re-sign at that coordinate, every rotation
 * orphans every outstanding link, and stranded recovery — whose whole premise is re-resolving the
 * link you joined through — can never fire.
 *
 * Replaceable and per-creator: the coordinate is (kind, creator pubkey, ""), so a creator's devices
 * converge on one list. Merge by `token` ([ConcordInviteList.merge]) rather than overwriting, or two
 * devices minting concurrently lose each other's links.
 */
@Immutable
class ConcordInviteListEvent(
    id: HexKey,
    pubKey: HexKey,
    createdAt: Long,
    tags: Array<Array<String>>,
    content: String,
    sig: HexKey,
) : BaseReplaceableEvent(id, pubKey, createdAt, KIND, tags, content, sig) {
    /**
     * Decrypts the whole document with [signer] — entries, tombstones and the document residue — or
     * **null** if it cannot be decrypted or parsed.
     *
     * Null, never empty: a caller that reads a decrypt failure as "no links yet" and republishes
     * wipes every `signer_sk` on this replaceable coordinate. A bunker signer that momentarily
     * refuses is enough to trigger it. Use this (never a partial read) whenever the result will be
     * re-encoded, or another client's unknown keys are dropped on the next publish.
     */
    suspend fun decrypt(signer: NostrSigner): ConcordInviteListDocument? =
        try {
            ConcordInviteList.decodeOrNull(signer.nip44Decrypt(content, signer.pubKey))
        } catch (_: Exception) {
            null
        }

    companion object {
        const val KIND = 13303

        fun createAddress(pubKey: HexKey) = Address(KIND, pubKey, "")

        suspend fun create(
            signer: NostrSigner,
            document: ConcordInviteListDocument,
            createdAt: Long = TimeUtils.now(),
        ): ConcordInviteListEvent {
            val content = signer.nip44Encrypt(ConcordInviteList.encode(document), signer.pubKey)
            return signer.sign(createdAt, KIND, emptyArray(), content)
        }
    }
}
