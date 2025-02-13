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
package com.vitorpamplona.quartz.nip50Search

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.nip01Core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.BaseReplaceableEvent
import com.vitorpamplona.quartz.nip01Core.signers.NostrSigner
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerSync
import com.vitorpamplona.quartz.nip01Core.tags.addressables.ATag
import com.vitorpamplona.quartz.nip31Alts.AltTag
import com.vitorpamplona.quartz.utils.TimeUtils

@Immutable
class SearchRelayListEvent(
    id: HexKey,
    pubKey: HexKey,
    createdAt: Long,
    tags: Array<Array<String>>,
    content: String,
    sig: HexKey,
) : BaseReplaceableEvent(id, pubKey, createdAt, KIND, tags, content, sig) {
    fun relays(): List<String> =
        tags.mapNotNull {
            if (it.size > 1 && it[0] == "relay") {
                it[1]
            } else {
                null
            }
        }

    companion object {
        const val KIND = 10007

        fun createAddressATag(pubKey: HexKey): ATag = ATag(KIND, pubKey, FIXED_D_TAG, null)

        fun createAddressTag(pubKey: HexKey): String = ATag.assembleATagId(KIND, pubKey, FIXED_D_TAG)

        fun createTagArray(relays: List<String>): Array<Array<String>> =
            relays
                .map {
                    arrayOf("relay", it)
                }.plusElement(AltTag.assemble("Relay list to use for Search"))
                .toTypedArray()

        fun updateRelayList(
            earlierVersion: SearchRelayListEvent,
            relays: List<String>,
            signer: NostrSigner,
            createdAt: Long = TimeUtils.now(),
            onReady: (SearchRelayListEvent) -> Unit,
        ) {
            val tags =
                earlierVersion.tags
                    .filter { it[0] != "relay" }
                    .plus(
                        relays.map {
                            arrayOf("relay", it)
                        },
                    ).toTypedArray()

            signer.sign(createdAt, KIND, tags, earlierVersion.content, onReady)
        }

        fun createFromScratch(
            relays: List<String>,
            signer: NostrSigner,
            createdAt: Long = TimeUtils.now(),
            onReady: (SearchRelayListEvent) -> Unit,
        ) {
            create(relays, signer, createdAt, onReady)
        }

        fun create(
            relays: List<String>,
            signer: NostrSigner,
            createdAt: Long = TimeUtils.now(),
            onReady: (SearchRelayListEvent) -> Unit,
        ) {
            signer.sign(createdAt, KIND, createTagArray(relays), "", onReady)
        }

        fun create(
            relays: List<String>,
            signer: NostrSignerSync,
            createdAt: Long = TimeUtils.now(),
        ): SearchRelayListEvent? = signer.sign(createdAt, KIND, createTagArray(relays), "")
    }
}
