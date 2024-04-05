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
import com.vitorpamplona.quartz.encoders.HexKey
import com.vitorpamplona.quartz.signers.NostrSigner
import com.vitorpamplona.quartz.utils.TimeUtils

@Immutable
class DeletionEvent(
    id: HexKey,
    pubKey: HexKey,
    createdAt: Long,
    tags: Array<Array<String>>,
    content: String,
    sig: HexKey,
) : Event(id, pubKey, createdAt, KIND, tags, content, sig) {
    fun deleteEvents() = taggedEvents()

    fun deleteAddresses() = taggedAddresses()

    fun deleteAddressTags() = tags.mapNotNull { if (it.size > 1 && it[0] == "a") it[1] else null }

    companion object {
        const val KIND = 5
        const val ALT = "Deletion event"

        fun create(
            deleteEvents: List<Event>,
            signer: NostrSigner,
            createdAt: Long = TimeUtils.now(),
            onReady: (DeletionEvent) -> Unit,
        ) {
            val content = ""
            val tags = mutableListOf<Array<String>>()

            val kinds =
                deleteEvents.mapTo(HashSet()) {
                    "${it.kind}"
                }.map {
                    arrayOf("k", it)
                }

            tags.addAll(deleteEvents.map { arrayOf("e", it.id) })
            tags.addAll(deleteEvents.mapNotNull { if (it is AddressableEvent) arrayOf("a", it.address().toTag()) else null })
            tags.addAll(kinds)
            tags.add(arrayOf("alt", ALT))

            signer.sign(createdAt, KIND, tags.toTypedArray(), content, onReady)
        }

        fun createForVersionOnly(
            deleteEvents: List<Event>,
            signer: NostrSigner,
            createdAt: Long = TimeUtils.now(),
            onReady: (DeletionEvent) -> Unit,
        ) {
            val content = ""
            val tags = mutableListOf<Array<String>>()

            val kinds =
                deleteEvents.mapTo(HashSet()) {
                    "${it.kind}"
                }.map {
                    arrayOf("k", it)
                }

            tags.addAll(deleteEvents.map { arrayOf("e", it.id) })
            tags.addAll(kinds)
            tags.add(arrayOf("alt", ALT))

            signer.sign(createdAt, KIND, tags.toTypedArray(), content, onReady)
        }
    }
}
