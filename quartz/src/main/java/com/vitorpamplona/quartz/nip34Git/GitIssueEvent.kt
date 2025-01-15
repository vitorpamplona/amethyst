/**
 * Copyright (c) 2024 Vitor Pamplona
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
package com.vitorpamplona.quartz.nip34Git

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.nip01Core.HexKey
import com.vitorpamplona.quartz.nip01Core.signers.NostrSigner
import com.vitorpamplona.quartz.nip01Core.tags.addressables.ATag
import com.vitorpamplona.quartz.nip10Notes.BaseTextNoteEvent
import com.vitorpamplona.quartz.nip19Bech32.parse
import com.vitorpamplona.quartz.nip31Alts.AltTagSerializer
import com.vitorpamplona.quartz.utils.TimeUtils

@Immutable
class GitIssueEvent(
    id: HexKey,
    pubKey: HexKey,
    createdAt: Long,
    tags: Array<Array<String>>,
    content: String,
    sig: HexKey,
) : BaseTextNoteEvent(id, pubKey, createdAt, KIND, tags, content, sig) {
    private fun innerRepository() =
        tags.firstOrNull { it.size > 3 && it[0] == "a" && it[3] == "root" }
            ?: tags.firstOrNull { it.size > 1 && it[0] == "a" }

    private fun repositoryHex() = innerRepository()?.getOrNull(1)

    fun rootIssueOrPatch() = tags.lastOrNull { it.size > 3 && it[0] == "e" && it[3] == "root" }?.get(1)

    fun repository() =
        innerRepository()?.let {
            if (it.size > 1) {
                val aTagValue = it[1]
                val relay = it.getOrNull(2)

                ATag.parse(aTagValue, relay)
            } else {
                null
            }
        }

    companion object {
        const val KIND = 1621
        const val ALT = "A Git Issue"

        fun create(
            patch: String,
            createdAt: Long = TimeUtils.now(),
            signer: NostrSigner,
            onReady: (GitIssueEvent) -> Unit,
        ) {
            val content = patch
            val tags =
                mutableListOf(
                    arrayOf<String>(),
                )

            tags.add(AltTagSerializer.toTagArray(ALT))

            signer.sign(createdAt, KIND, tags.toTypedArray(), content, onReady)
        }
    }
}
