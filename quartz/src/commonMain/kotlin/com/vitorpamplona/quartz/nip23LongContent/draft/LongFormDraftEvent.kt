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
package com.vitorpamplona.quartz.nip23LongContent.draft

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
import com.vitorpamplona.quartz.nip10Notes.BaseNoteEvent
import com.vitorpamplona.quartz.nip23LongContent.LongFormContentEvent
import com.vitorpamplona.quartz.nip23LongContent.forEachLongFormIndexableField
import com.vitorpamplona.quartz.nip23LongContent.longFormAddressHints
import com.vitorpamplona.quartz.nip23LongContent.longFormEventHints
import com.vitorpamplona.quartz.nip23LongContent.longFormImage
import com.vitorpamplona.quartz.nip23LongContent.longFormIndexableContent
import com.vitorpamplona.quartz.nip23LongContent.longFormLinkedAddressIds
import com.vitorpamplona.quartz.nip23LongContent.longFormLinkedEventIds
import com.vitorpamplona.quartz.nip23LongContent.longFormLinkedPubKeys
import com.vitorpamplona.quartz.nip23LongContent.longFormPubKeyHints
import com.vitorpamplona.quartz.nip23LongContent.longFormPublishedAt
import com.vitorpamplona.quartz.nip23LongContent.longFormSummary
import com.vitorpamplona.quartz.nip23LongContent.longFormTitle
import com.vitorpamplona.quartz.nip50Search.IndexableFieldVisitor
import com.vitorpamplona.quartz.nip50Search.SearchableEvent
import com.vitorpamplona.quartz.utils.TimeUtils
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * NIP-23 long-form **draft**, kind 30024: "the same structure" as a published article
 * ([LongFormContentEvent], kind 30023) — a markdown `content`, `title`, `summary`, `image`,
 * `published_at`, `t` hashtags and NIP-27 references — under a separate kind so clients keep it out of
 * article feeds. Publishing a draft means signing the same tags and content as a 30023.
 *
 * Not the NIP-37 draft wrap (kind 31234, `DraftWrapEvent`), which hides an encrypted draft of any
 * kind; a 30024 is plain text anyone can read.
 *
 * Accessors, hint providers and search text are those of kind 30023, read through the same
 * `TagArray` helpers. Two deliberate differences: a draft is not a NIP-10 thread
 * (it extends [BaseNoteEvent], not `BaseThreadedEvent`) and not a NIP-22 root, since comments belong
 * on the published article.
 *
 * Searchable with the same fields as kind 30023 (`title`, `summary`, `content`, then the `t` topics): the author published
 * this human-written text unencrypted, which is what the search policy indexes, and a search filtered
 * to kind 30023 never returns drafts.
 */
@Immutable
class LongFormDraftEvent(
    id: HexKey,
    pubKey: HexKey,
    createdAt: Long,
    tags: Array<Array<String>>,
    content: String,
    sig: HexKey,
) : BaseNoteEvent(id, pubKey, createdAt, KIND, tags, content, sig),
    AddressableEvent,
    EventHintProvider,
    PubKeyHintProvider,
    AddressHintProvider,
    PublishedAtProvider,
    SearchableEvent {
    override fun indexableContent() = tags.longFormIndexableContent(content)

    // The read path: hands over the same fields indexableContent() joins, without
    // building the joined string a scan would throw away.
    override fun forEachIndexableField(visitor: IndexableFieldVisitor) = tags.forEachLongFormIndexableField(content, visitor)

    override fun eventHints(): List<EventIdHint> = tags.longFormEventHints(citedNIP19())

    /** `QUOTE`: events quoted with `q`; `MENTION`: events cited as `nostr:` URIs in the text. */
    override fun linkedEventIds(): List<HexKey> = tags.longFormLinkedEventIds(citedNIP19())

    override fun addressHints(): List<AddressHint> = tags.longFormAddressHints(citedNIP19())

    /** `QUOTE`: addresses quoted with `q`; `MENTION`: addresses cited as `nostr:` URIs in the text. */
    override fun linkedAddressIds(): List<String> = tags.longFormLinkedAddressIds(citedNIP19())

    override fun pubKeyHints(): List<PubKeyHint> = tags.longFormPubKeyHints(citedNIP19())

    /** `MENTION`: people tagged with `p` or cited as `nostr:` URIs in the text; `ZAP_SPLIT`: NIP-57 zap-split recipients. */
    override fun linkedPubKeys(): List<HexKey> = tags.longFormLinkedPubKeys(citedNIP19())

    override fun dTag() = tags.dTag()

    override fun address() = Address(kind, pubKey, dTag())

    override fun addressTag() = Address.assemble(kind, pubKey, dTag())

    fun topics() = hashtags()

    fun title() = tags.longFormTitle()

    fun image() = tags.longFormImage()

    fun summary() = tags.longFormSummary()

    override fun publishedAt(): Long? = tags.longFormPublishedAt(createdAt)

    companion object {
        const val KIND = 30024
        const val ALT_DESCRIPTION = "Long-form draft"

        @OptIn(ExperimentalUuidApi::class)
        fun build(
            description: String,
            title: String,
            summary: String? = null,
            image: String? = null,
            publishedAt: Long? = null,
            dTag: String = Uuid.random().toString(),
            createdAt: Long = TimeUtils.now(),
            initializer: TagArrayBuilder<LongFormDraftEvent>.() -> Unit = {},
        ) = eventTemplate<LongFormDraftEvent>(KIND, description, createdAt) {
            dTag(dTag)

            title(title)
            summary?.let { summary(it) }
            image?.let { image(it) }
            publishedAt?.let { publishedAt(it) }

            initializer()
        }
    }
}
