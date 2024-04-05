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
class LongTextNoteEvent(
    id: HexKey,
    pubKey: HexKey,
    createdAt: Long,
    tags: Array<Array<String>>,
    content: String,
    sig: HexKey,
) : BaseTextNoteEvent(id, pubKey, createdAt, KIND, tags, content, sig), AddressableEvent {
    override fun dTag() = tags.firstOrNull { it.size > 1 && it[0] == "d" }?.get(1) ?: ""

    override fun address() = ATag(kind, pubKey, dTag(), null)

    override fun addressTag() = ATag.assembleATag(kind, pubKey, dTag())

    fun topics() = hashtags()

    fun title() = tags.firstOrNull { it.size > 1 && it[0] == "title" }?.get(1)

    fun image() = tags.firstOrNull { it.size > 1 && it[0] == "image" }?.get(1)

    fun summary() = tags.firstOrNull { it.size > 1 && it[0] == "summary" }?.get(1)

    fun publishedAt() =
        try {
            tags.firstOrNull { it.size > 1 && it[0] == "published_at" }?.get(1)?.toLongOrNull()
        } catch (_: Exception) {
            null
        }

    companion object {
        const val KIND = 30023

        fun create(
            msg: String,
            title: String?,
            replyTos: List<String>?,
            mentions: List<String>?,
            signer: NostrSigner,
            createdAt: Long = TimeUtils.now(),
            onReady: (LongTextNoteEvent) -> Unit,
        ) {
            val tags = mutableListOf<Array<String>>()
            replyTos?.forEach { tags.add(arrayOf("e", it)) }
            mentions?.forEach { tags.add(arrayOf("p", it)) }
            title?.let { tags.add(arrayOf("title", it)) }
            tags.add(arrayOf("alt", "Blog post: $title"))
            signer.sign(createdAt, KIND, tags.toTypedArray(), msg, onReady)
        }
    }
}
