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
import com.vitorpamplona.quartz.nip01Core.core.BaseAddressableEvent
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.diff.ContentChange
import com.vitorpamplona.quartz.nip01Core.diff.DiffableEvent
import com.vitorpamplona.quartz.nip01Core.signers.NostrSigner
import com.vitorpamplona.quartz.utils.TimeUtils
import kotlinx.coroutines.CancellationException

/**
 * One fragment of a member's Community List (CORD-02 §8, kind 33302): addressable at
 * `d` = the fragment index in decimal, NIP-44-encrypted to self, signed by the member's real key.
 *
 * The List is split across as many of these as it needs — it has no membership limit, only a
 * per-event byte ceiling — and a reader unions every fragment below the declared `frags` count
 * ([ConcordListFragmentSet]). It supersedes the single replaceable kind-13302
 * [ConcordCommunityListEvent], which a replaceable kind could never fragment.
 */
@Immutable
class ConcordCommunityListFragmentEvent(
    id: HexKey,
    pubKey: HexKey,
    createdAt: Long,
    tags: Array<Array<String>>,
    content: String,
    sig: HexKey,
) : BaseAddressableEvent(id, pubKey, createdAt, KIND, tags, content, sig),
    DiffableEvent<ConcordCommunityListDiff> {
    override fun diffFrom(older: Event): ConcordCommunityListDiff? {
        if (older !is ConcordCommunityListFragmentEvent || older.pubKey != pubKey || older.dTag() != dTag()) return null
        return ConcordCommunityListDiff(ContentChange.between(older.content, content))
    }

    override fun isContentEncoded() = true

    /**
     * The fragment index this event occupies, or null when its `d` is not a canonical decimal
     * (no sign, no leading zeros) — such an event sits at no index and is ignored.
     */
    fun index(): Int? = parseIndex(dTag())

    /**
     * The decrypted plaintext, or null when it does not open for [signer]. A null makes the
     * fragment's index unreadable, which blocks any repack — so a transient signer failure (a
     * timed-out bunker, a backgrounded signer app) costs a write, never a membership.
     * Cancellation is rethrown.
     */
    suspend fun decryptPlaintext(signer: NostrSigner): String? =
        try {
            signer.nip44Decrypt(content, signer.pubKey)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        }

    companion object {
        const val KIND = 33302
        const val ALT = "Private list of joined Concord communities"

        fun createAddress(
            pubKey: HexKey,
            index: Int,
        ) = Address(KIND, pubKey, index.toString())

        fun parseIndex(d: String): Int? {
            if (d.isEmpty() || d.length > 9) return null
            if (d.length > 1 && d[0] == '0') return null
            if (!d.all { it in '0'..'9' }) return null
            return d.toInt()
        }

        /** Encrypts [plaintext] to self and signs it as fragment [index]. */
        suspend fun create(
            signer: NostrSigner,
            index: Int,
            plaintext: String,
            createdAt: Long = TimeUtils.now(),
        ): ConcordCommunityListFragmentEvent {
            val content = signer.nip44Encrypt(plaintext, signer.pubKey)
            return signer.sign(createdAt, KIND, arrayOf(arrayOf("d", index.toString())), content)
        }
    }
}
