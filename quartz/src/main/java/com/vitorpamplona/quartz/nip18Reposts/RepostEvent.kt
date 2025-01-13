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
package com.vitorpamplona.quartz.nip18Reposts

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.nip01Core.HexKey
import com.vitorpamplona.quartz.nip01Core.addressables.AddressableEvent
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.events.taggedEvents
import com.vitorpamplona.quartz.nip01Core.people.taggedUsers
import com.vitorpamplona.quartz.nip01Core.signers.NostrSigner
import com.vitorpamplona.quartz.utils.TimeUtils

@Immutable
class RepostEvent(
    id: HexKey,
    pubKey: HexKey,
    createdAt: Long,
    tags: Array<Array<String>>,
    content: String,
    sig: HexKey,
) : Event(id, pubKey, createdAt, KIND, tags, content, sig) {
    fun boostedPost() = taggedEvents()

    fun originalAuthor() = taggedUsers()

    fun containedPost() =
        try {
            fromJson(content)
        } catch (e: Exception) {
            null
        }

    companion object {
        const val KIND = 6
        const val ALT = "Repost event"

        fun create(
            boostedPost: Event,
            signer: NostrSigner,
            createdAt: Long = TimeUtils.now(),
            onReady: (RepostEvent) -> Unit,
        ) {
            val content = boostedPost.toJson()

            val replyToPost = arrayOf("e", boostedPost.id)
            val replyToAuthor = arrayOf("p", boostedPost.pubKey)

            var tags: Array<Array<String>> = arrayOf(replyToPost, replyToAuthor)

            if (boostedPost is AddressableEvent) {
                tags += listOf(arrayOf("a", boostedPost.address().toTag()))
            }

            tags += listOf(arrayOf("alt", ALT))

            signer.sign(createdAt, KIND, tags, content, onReady)
        }
    }
}
