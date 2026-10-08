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
package com.vitorpamplona.quartz.nip23LongContent

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.nip01Core.core.Address
import com.vitorpamplona.quartz.nip01Core.core.AddressableEvent
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.TagArrayBuilder
import com.vitorpamplona.quartz.nip01Core.hints.AddressHintProvider
import com.vitorpamplona.quartz.nip01Core.hints.EventHintProvider
import com.vitorpamplona.quartz.nip01Core.hints.PubKeyHintProvider
import com.vitorpamplona.quartz.nip01Core.hints.types.AddressHint
import com.vitorpamplona.quartz.nip01Core.hints.types.EventIdHint
import com.vitorpamplona.quartz.nip01Core.hints.types.PubKeyHint
import com.vitorpamplona.quartz.nip01Core.signers.eventTemplate
import com.vitorpamplona.quartz.nip01Core.tags.dTag.dTag
import com.vitorpamplona.quartz.nip01Core.tags.hashtags.hashtags
import com.vitorpamplona.quartz.nip01Core.tags.publishedAt.PublishedAtProvider
import com.vitorpamplona.quartz.nip10Notes.BaseThreadedEvent
import com.vitorpamplona.quartz.nip22Comments.RootScope
import com.vitorpamplona.quartz.nip50Search.IndexableFieldVisitor
import com.vitorpamplona.quartz.nip50Search.SearchableEvent
import com.vitorpamplona.quartz.utils.TimeUtils
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@Immutable
class LongFormContentEvent(
    id: HexKey,
    pubKey: HexKey,
    createdAt: Long,
    tags: Array<Array<String>>,
    content: String,
    sig: HexKey,
) : BaseThreadedEvent(id, pubKey, createdAt, KIND, tags, content, sig),
    AddressableEvent,
    EventHintProvider,
    PubKeyHintProvider,
    AddressHintProvider,
    PublishedAtProvider,
    RootScope,
    SearchableEvent {
    // Topics (`t`) are appended after the body, as CommentEvent does: authors pick
    // topics that the article text does not necessarily mention. Shared with the draft (30024).
    override fun indexableContent() = tags.longFormIndexableContent(content)

    // The read path: hands over the same fields indexableContent() joins, without
    // building the joined string a scan would throw away.
    override fun forEachIndexableField(visitor: IndexableFieldVisitor) = tags.forEachLongFormIndexableField(content, visitor)

    override fun eventHints(): List<EventIdHint> = tags.longFormEventHints(citedNIP19())

    override fun linkedEventIds(): List<HexKey> = tags.longFormLinkedEventIds(citedNIP19())

    override fun addressHints(): List<AddressHint> = tags.longFormAddressHints(citedNIP19())

    override fun linkedAddressIds(): List<String> = tags.longFormLinkedAddressIds(citedNIP19())

    override fun pubKeyHints(): List<PubKeyHint> = tags.longFormPubKeyHints(citedNIP19())

    override fun linkedPubKeys(): List<HexKey> = tags.longFormLinkedPubKeys(citedNIP19())

    override fun dTag() = tags.dTag()

    override fun address() = Address(kind, pubKey, dTag())

    override fun addressTag() = Address.assemble(kind, pubKey, dTag())

    fun topics() = hashtags()

    fun title() = tags.longFormTitle()

    fun image() = tags.longFormImage()

    fun summary() = tags.longFormSummary()

    // Drops a `published_at` in the future.
    override fun publishedAt(): Long? = tags.longFormPublishedAt(createdAt)

    companion object {
        const val KIND = 30023

        @OptIn(ExperimentalUuidApi::class)
        fun build(
            description: String,
            title: String,
            summary: String? = null,
            image: String? = null,
            publishedAt: Long? = null,
            dTag: String = Uuid.random().toString(),
            createdAt: Long = TimeUtils.now(),
            initializer: TagArrayBuilder<LongFormContentEvent>.() -> Unit = {},
        ) = eventTemplate(KIND, description, createdAt) {
            dTag(dTag)

            title(title)
            summary?.let { summary(it) }
            image?.let { image(it) }
            publishedAt?.let { publishedAt(it) }

            initializer()
        }
    }
}

@Deprecated(
    "Renamed to LongFormContentEvent. NIP-23 names kind 30023 long-form content.",
    ReplaceWith("LongFormContentEvent", "com.vitorpamplona.quartz.nip23LongContent.LongFormContentEvent"),
)
typealias LongTextNoteEvent = LongFormContentEvent
