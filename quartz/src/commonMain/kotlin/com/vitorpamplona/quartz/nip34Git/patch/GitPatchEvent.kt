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
package com.vitorpamplona.quartz.nip34Git.patch

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.nip01Core.core.Address
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.hints.AddressHintProvider
import com.vitorpamplona.quartz.nip01Core.hints.EventHintProvider
import com.vitorpamplona.quartz.nip01Core.hints.PubKeyHintProvider
import com.vitorpamplona.quartz.nip01Core.signers.NostrSigner
import com.vitorpamplona.quartz.nip01Core.tags.aTag.ATag
import com.vitorpamplona.quartz.nip01Core.tags.people.PTag
import com.vitorpamplona.quartz.nip10Notes.tags.MarkedETag
import com.vitorpamplona.quartz.nip31Alts.AltTag
import com.vitorpamplona.quartz.utils.TimeUtils

@Immutable
class GitPatchEvent(
    id: HexKey,
    pubKey: HexKey,
    createdAt: Long,
    tags: Array<Array<String>>,
    content: String,
    sig: HexKey,
) : Event(id, pubKey, createdAt, KIND, tags, content, sig),
    PubKeyHintProvider,
    EventHintProvider,
    AddressHintProvider {
    override fun pubKeyHints() = tags.mapNotNull(PTag::parseAsHint)

    override fun linkedPubKeys() = tags.mapNotNull(PTag::parseKey)

    override fun eventHints() = tags.mapNotNull(MarkedETag::parseAsHint)

    override fun linkedEventIds() = tags.mapNotNull(MarkedETag::parseId)

    override fun addressHints() = tags.mapNotNull(ATag::parseAsHint)

    override fun linkedAddressIds() = tags.mapNotNull(ATag::parseAddressId)

    private fun innerRepository() =
        tags.firstOrNull { it.size > 3 && it[0] == "a" && it[3] == "root" }
            ?: tags.firstOrNull { it.size > 1 && it[0] == "a" }

    private fun repositoryHex() = innerRepository()?.getOrNull(1)

    fun repositoryAddress() =
        innerRepository()?.let {
            if (it.size > 1) {
                Address.parse(it[1])
            } else {
                null
            }
        }

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

    fun commit() = tags.firstOrNull { it.size > 1 && it[0] == "commit" }?.get(1)

    fun parentCommit() = tags.firstOrNull { it.size > 1 && it[0] == "parent-commit" }?.get(1)

    fun commitPGPSig() = tags.firstOrNull { it.size > 1 && it[0] == "commit-pgp-sig" }?.get(1)

    fun committer() =
        tags.filter { it.size > 1 && it[0] == "committer" }.mapNotNull {
            Committer(it.getOrNull(1), it.getOrNull(2), it.getOrNull(3), it.getOrNull(4))
        }

    data class Committer(
        val name: String?,
        val email: String?,
        val timestamp: String?,
        val timezoneInMinutes: String?,
    )

    companion object {
        const val KIND = 1617
        const val ALT = "A Git Patch"

        suspend fun create(
            patch: String,
            createdAt: Long = TimeUtils.now(),
            signer: NostrSigner,
        ): GitPatchEvent {
            val content = patch
            val tags =
                mutableListOf(
                    arrayOf<String>(),
                )

            tags.add(AltTag.assemble(ALT))

            return signer.sign(createdAt, KIND, tags.toTypedArray(), content)
        }
    }
}
