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
package com.vitorpamplona.quartz.nip75ZapGoals

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.nip01Core.HexKey
import com.vitorpamplona.quartz.nip01Core.addressables.AddressableEvent
import com.vitorpamplona.quartz.nip01Core.addressables.BaseAddressableEvent
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.signers.NostrSigner
import com.vitorpamplona.quartz.utils.TimeUtils

@Immutable
class GoalEvent(
    id: HexKey,
    pubKey: HexKey,
    createdAt: Long,
    tags: Array<Array<String>>,
    content: String,
    sig: HexKey,
) : BaseAddressableEvent(id, pubKey, createdAt, KIND, tags, content, sig) {
    companion object {
        const val KIND = 9041
        const val ALT = "Zap Goal"

        private const val SUMMARY = "summary"
        private const val CLOSED_AT = "closed_at"
        private const val IMAGE = "image"
        private const val AMOUNT = "amount"

        fun create(
            description: String,
            amount: Long,
            relays: Set<String>,
            closedAt: Long? = null,
            image: String? = null,
            summary: String? = null,
            websiteUrl: String? = null,
            linkedEvent: Event? = null,
            signer: NostrSigner,
            createdAt: Long = TimeUtils.now(),
            onReady: (GoalEvent) -> Unit,
        ) {
            val tags =
                mutableListOf(
                    arrayOf(AMOUNT, amount.toString()),
                    arrayOf("relays") + relays,
                    arrayOf("alt", ALT),
                )

            if (linkedEvent is AddressableEvent) {
                tags.add(arrayOf("a", linkedEvent.address().toTag()))
            } else if (linkedEvent is Event) {
                tags.add(arrayOf("e", linkedEvent.id))
            }

            closedAt?.let { tags.add(arrayOf(CLOSED_AT, it.toString())) }
            summary?.let { tags.add(arrayOf(SUMMARY, it)) }
            image?.let { tags.add(arrayOf(IMAGE, it)) }
            websiteUrl?.let { tags.add(arrayOf("r", it)) }

            signer.sign(createdAt, KIND, tags.toTypedArray(), description, onReady)
        }
    }
}
