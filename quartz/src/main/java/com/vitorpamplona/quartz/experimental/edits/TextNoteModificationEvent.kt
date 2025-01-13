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
package com.vitorpamplona.quartz.experimental.edits

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.nip01Core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.events.firstTaggedEvent
import com.vitorpamplona.quartz.nip01Core.signers.NostrSigner
import com.vitorpamplona.quartz.utils.TimeUtils

@Immutable
class TextNoteModificationEvent(
    id: HexKey,
    pubKey: HexKey,
    createdAt: Long,
    tags: Array<Array<String>>,
    content: String,
    sig: HexKey,
) : Event(id, pubKey, createdAt, KIND, tags, content, sig) {
    fun editedNote() = firstTaggedEvent()

    fun summary() = tags.firstOrNull { it.size > 1 && it[0] == "summary" }?.get(1)

    companion object {
        const val KIND = 1010
        const val ALT = "Content Change Event"

        fun create(
            content: String,
            eventId: HexKey,
            notify: HexKey?,
            summary: String?,
            signer: NostrSigner,
            createdAt: Long = TimeUtils.now(),
            onReady: (TextNoteModificationEvent) -> Unit,
        ) {
            val tags = mutableListOf(arrayOf("e", eventId))

            notify?.let {
                tags.add(arrayOf("p", it))
            }

            summary?.let {
                tags.add(arrayOf("summary", it))
            }

            tags.add(arrayOf("alt", ALT))

            signer.sign(createdAt, KIND, tags.toTypedArray(), content, onReady)
        }
    }
}
