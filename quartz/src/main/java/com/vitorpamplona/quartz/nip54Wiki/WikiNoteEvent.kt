/**
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
package com.vitorpamplona.quartz.nip54Wiki

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.nip01Core.core.AddressableEvent
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.hints.ExtendedHintProvider
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.signers.NostrSigner
import com.vitorpamplona.quartz.nip01Core.tags.addressables.ATag
import com.vitorpamplona.quartz.nip01Core.tags.addressables.Address
import com.vitorpamplona.quartz.nip01Core.tags.dTags.dTag
import com.vitorpamplona.quartz.nip01Core.tags.hashtags.hashtags
import com.vitorpamplona.quartz.nip01Core.tags.publishedAt.PublishedAtProvider
import com.vitorpamplona.quartz.nip10Notes.BaseThreadedEvent
import com.vitorpamplona.quartz.nip31Alts.AltTag
import com.vitorpamplona.quartz.nip50Search.SearchableEvent
import com.vitorpamplona.quartz.utils.TimeUtils

@Immutable
class WikiNoteEvent(
    id: HexKey,
    pubKey: HexKey,
    createdAt: Long,
    tags: Array<Array<String>>,
    content: String,
    sig: HexKey,
) : BaseThreadedEvent(id, pubKey, createdAt, KIND, tags, content, sig),
    AddressableEvent,
    ExtendedHintProvider,
    PublishedAtProvider,
    SearchableEvent {
    override fun indexableContent() = "title: " + title() + "\nsummary: " + summary() + "\n" + content

    override fun dTag() = tags.dTag()

    override fun aTag(relayHint: NormalizedRelayUrl?) = ATag(kind, pubKey, dTag(), relayHint)

    override fun address() = Address(kind, pubKey, dTag())

    override fun addressTag() = Address.assemble(kind, pubKey, dTag())

    fun topics() = hashtags()

    fun title() = tags.firstOrNull { it.size > 1 && it[0] == "title" }?.get(1)

    fun summary() = tags.firstOrNull { it.size > 1 && it[0] == "summary" }?.get(1)

    fun image() = tags.firstOrNull { it.size > 1 && it[0] == "image" }?.get(1)

    override fun publishedAt() =
        try {
            tags.firstOrNull { it.size > 1 && it[0] == "published_at" }?.get(1)?.toLongOrNull()
        } catch (_: Exception) {
            null
        }

    companion object {
        const val KIND = 30818

        suspend fun create(
            msg: String,
            title: String?,
            replyTos: List<String>?,
            mentions: List<String>?,
            signer: NostrSigner,
            createdAt: Long = TimeUtils.now(),
        ): WikiNoteEvent {
            val tags = mutableListOf<Array<String>>()
            replyTos?.forEach { tags.add(arrayOf("e", it)) }
            mentions?.forEach { tags.add(arrayOf("p", it)) }
            title?.let { tags.add(arrayOf("title", it)) }
            tags.add(AltTag.assemble("Wiki Post: $title"))
            return signer.sign(createdAt, KIND, tags.toTypedArray(), msg)
        }
    }
}
