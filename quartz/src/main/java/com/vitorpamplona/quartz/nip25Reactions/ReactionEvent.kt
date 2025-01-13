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
package com.vitorpamplona.quartz.nip25Reactions

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.nip01Core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.AddressableEvent
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.signers.NostrSigner
import com.vitorpamplona.quartz.nip30CustomEmoji.EmojiUrl
import com.vitorpamplona.quartz.utils.TimeUtils

@Immutable
class ReactionEvent(
    id: HexKey,
    pubKey: HexKey,
    createdAt: Long,
    tags: Array<Array<String>>,
    content: String,
    sig: HexKey,
) : Event(id, pubKey, createdAt, KIND, tags, content, sig) {
    fun originalPost() = tags.filter { it.size > 1 && it[0] == "e" }.map { it[1] }

    fun originalAuthor() = tags.filter { it.size > 1 && it[0] == "p" }.map { it[1] }

    companion object {
        const val KIND = 7

        fun createWarning(
            originalNote: Event,
            signer: NostrSigner,
            createdAt: Long = TimeUtils.now(),
            onReady: (ReactionEvent) -> Unit,
        ) = create("\u26A0\uFE0F", originalNote, signer, createdAt, onReady)

        fun createLike(
            originalNote: Event,
            signer: NostrSigner,
            createdAt: Long = TimeUtils.now(),
            onReady: (ReactionEvent) -> Unit,
        ) = create("+", originalNote, signer, createdAt, onReady)

        fun create(
            content: String,
            originalNote: Event,
            signer: NostrSigner,
            createdAt: Long = TimeUtils.now(),
            onReady: (ReactionEvent) -> Unit,
        ) {
            var tags =
                listOf(
                    arrayOf("e", originalNote.id),
                    arrayOf("p", originalNote.pubKey),
                    arrayOf("k", originalNote.kind.toString()),
                )
            if (originalNote is AddressableEvent) {
                tags = tags + listOf(arrayOf("a", originalNote.address().toTag()))
            }

            return signer.sign(createdAt, KIND, tags.toTypedArray(), content, onReady)
        }

        fun create(
            emojiUrl: EmojiUrl,
            originalNote: Event,
            signer: NostrSigner,
            createdAt: Long = TimeUtils.now(),
            onReady: (ReactionEvent) -> Unit,
        ) {
            val content = ":${emojiUrl.code}:"

            var tags =
                arrayOf(
                    arrayOf("e", originalNote.id),
                    arrayOf("p", originalNote.pubKey),
                    arrayOf("emoji", emojiUrl.code, emojiUrl.url),
                )

            if (originalNote is AddressableEvent) {
                tags += arrayOf(arrayOf("a", originalNote.address().toTag()))
            }

            signer.sign(createdAt, KIND, tags, content, onReady)
        }
    }
}
