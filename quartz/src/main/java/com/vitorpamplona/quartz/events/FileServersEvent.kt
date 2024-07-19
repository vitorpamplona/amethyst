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
package com.vitorpamplona.quartz.events

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.encoders.ATag
import com.vitorpamplona.quartz.encoders.HexKey
import com.vitorpamplona.quartz.signers.NostrSigner
import com.vitorpamplona.quartz.utils.TimeUtils

@Immutable
class FileServersEvent(
    id: HexKey,
    pubKey: HexKey,
    createdAt: Long,
    tags: Array<Array<String>>,
    content: String,
    sig: HexKey,
) : BaseAddressableEvent(id, pubKey, createdAt, KIND, tags, content, sig) {
    override fun dTag() = FIXED_D_TAG

    fun servers(): List<String> =
        tags.mapNotNull {
            if (it.size > 1 && it[0] == "server") {
                it[1]
            } else {
                null
            }
        }

    companion object {
        const val KIND = 10096
        const val FIXED_D_TAG = ""
        const val ALT = "File servers used by the author"

        fun createAddressATag(pubKey: HexKey): ATag = ATag(KIND, pubKey, FIXED_D_TAG, null)

        fun createAddressTag(pubKey: HexKey): String = ATag.assembleATag(KIND, pubKey, FIXED_D_TAG)

        fun createTagArray(servers: List<String>): Array<Array<String>> =
            servers
                .map {
                    arrayOf("server", it)
                }.plusElement(arrayOf("alt", ALT))
                .toTypedArray()

        fun updateRelayList(
            earlierVersion: FileServersEvent,
            relays: List<String>,
            signer: NostrSigner,
            createdAt: Long = TimeUtils.now(),
            onReady: (FileServersEvent) -> Unit,
        ) {
            val tags =
                earlierVersion.tags
                    .filter { it[0] != "server" }
                    .plus(
                        relays.map {
                            arrayOf("server", it)
                        },
                    ).toTypedArray()

            signer.sign(createdAt, KIND, tags, earlierVersion.content, onReady)
        }

        fun createFromScratch(
            relays: List<String>,
            signer: NostrSigner,
            createdAt: Long = TimeUtils.now(),
            onReady: (FileServersEvent) -> Unit,
        ) {
            create(relays, signer, createdAt, onReady)
        }

        fun create(
            servers: List<String>,
            signer: NostrSigner,
            createdAt: Long = TimeUtils.now(),
            onReady: (FileServersEvent) -> Unit,
        ) {
            signer.sign(createdAt, KIND, createTagArray(servers), "", onReady)
        }
    }
}
