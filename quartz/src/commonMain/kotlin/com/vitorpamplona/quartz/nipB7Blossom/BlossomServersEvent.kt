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
package com.vitorpamplona.quartz.nipB7Blossom

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.nip01Core.core.Address
import com.vitorpamplona.quartz.nip01Core.core.BaseReplaceableEvent
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.signers.NostrSigner
import com.vitorpamplona.quartz.nip01Core.tags.aTag.ATag
import com.vitorpamplona.quartz.nip31Alts.AltTag
import com.vitorpamplona.quartz.utils.TimeUtils

@Immutable
class BlossomServersEvent(
    id: HexKey,
    pubKey: HexKey,
    createdAt: Long,
    tags: Array<Array<String>>,
    content: String,
    sig: HexKey,
) : BaseReplaceableEvent(id, pubKey, createdAt, KIND, tags, content, sig) {
    fun servers(): List<String> =
        tags.mapNotNull {
            if (it.size > 1 && it[0] == "server") {
                it[1]
            } else {
                null
            }
        }

    companion object {
        const val KIND = 10063
        const val ALT = "File servers used by the author"

        fun createAddress(pubKey: HexKey): Address = Address(KIND, pubKey, FIXED_D_TAG)

        fun createAddressATag(pubKey: HexKey): ATag = ATag(KIND, pubKey, FIXED_D_TAG, null)

        fun createAddressTag(pubKey: HexKey): String = Address.assemble(KIND, pubKey, FIXED_D_TAG)

        fun createTagArray(servers: List<String>): Array<Array<String>> =
            servers
                .map {
                    arrayOf("server", it)
                }.plusElement(AltTag.assemble(ALT))
                .toTypedArray()

        suspend fun updateRelayList(
            earlierVersion: BlossomServersEvent,
            servers: List<String>,
            signer: NostrSigner,
            createdAt: Long = TimeUtils.now(),
        ): BlossomServersEvent {
            val tags =
                earlierVersion.tags
                    .filter { it[0] != "server" }
                    .plus(servers.map { arrayOf("server", it) })
                    .toTypedArray()

            return signer.sign(createdAt, KIND, tags, earlierVersion.content)
        }

        suspend fun createFromScratch(
            relays: List<String>,
            signer: NostrSigner,
            createdAt: Long = TimeUtils.now(),
        ) = create(relays, signer, createdAt)

        suspend fun create(
            servers: List<String>,
            signer: NostrSigner,
            createdAt: Long = TimeUtils.now(),
        ) = signer.sign<BlossomServersEvent>(createdAt, KIND, createTagArray(servers), "")
    }
}
