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
import com.vitorpamplona.quartz.nip01Core.tags.people.PTag
import com.vitorpamplona.quartz.nip01Core.tags.publishedAt.PublishedAtProvider
import com.vitorpamplona.quartz.nip10Notes.BaseThreadedEvent
import com.vitorpamplona.quartz.nip18Reposts.quotes.QTag
import com.vitorpamplona.quartz.nip19Bech32.addressHints
import com.vitorpamplona.quartz.nip19Bech32.addressIds
import com.vitorpamplona.quartz.nip19Bech32.eventHints
import com.vitorpamplona.quartz.nip19Bech32.eventIds
import com.vitorpamplona.quartz.nip19Bech32.pubKeyHints
import com.vitorpamplona.quartz.nip19Bech32.pubKeys
import com.vitorpamplona.quartz.nip22Comments.RootScope
import com.vitorpamplona.quartz.nip23LongContent.tags.ImageTag
import com.vitorpamplona.quartz.nip23LongContent.tags.PublishedAtTag
import com.vitorpamplona.quartz.nip23LongContent.tags.SummaryTag
import com.vitorpamplona.quartz.nip23LongContent.tags.TitleTag
import com.vitorpamplona.quartz.nip31Alts.alt
import com.vitorpamplona.quartz.nip50Search.SearchableEvent
import com.vitorpamplona.quartz.utils.TimeUtils
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@Immutable
class LongTextNoteEvent(
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
    override fun indexableContent() = "title: " + title() + "\nsummary: " + summary() + "\n" + content

    override fun eventHints(): List<EventIdHint> {
        val qHints = tags.mapNotNull(QTag::parseEventAsHint)
        val nip19Hints = citedNIP19().eventHints()

        return qHints + nip19Hints
    }

    override fun linkedEventIds(): List<HexKey> {
        val qHints = tags.mapNotNull(QTag::parseEventId)
        val nip19Hints = citedNIP19().eventIds()

        return qHints + nip19Hints
    }

    override fun addressHints(): List<AddressHint> {
        val qHints = tags.mapNotNull(QTag::parseAddressAsHint)
        val nip19Hints = citedNIP19().addressHints()

        return qHints + nip19Hints
    }

    override fun linkedAddressIds(): List<String> {
        val qHints = tags.mapNotNull(QTag::parseAddressId)
        val nip19Hints = citedNIP19().addressIds()

        return qHints + nip19Hints
    }

    override fun pubKeyHints(): List<PubKeyHint> {
        val pHints = tags.mapNotNull(PTag::parseAsHint)
        val nip19Hints = citedNIP19().pubKeyHints()

        return pHints + nip19Hints
    }

    override fun linkedPubKeys(): List<HexKey> {
        val pHints = tags.mapNotNull(PTag::parseKey)
        val nip19Hints = citedNIP19().pubKeys()

        return pHints + nip19Hints
    }

    override fun dTag() = tags.dTag()

    override fun address() = Address(kind, pubKey, dTag())

    override fun addressTag() = Address.assemble(kind, pubKey, dTag())

    fun topics() = hashtags()

    fun title() = tags.firstNotNullOfOrNull(TitleTag::parse)

    fun image() = tags.firstNotNullOfOrNull(ImageTag::parse)

    fun summary() = tags.firstNotNullOfOrNull(SummaryTag::parse)

    override fun publishedAt(): Long? {
        val publishedAt = tags.firstNotNullOfOrNull(PublishedAtTag::parse)

        if (publishedAt == null) return null

        // removes posts in the future.
        return if (publishedAt <= createdAt) {
            publishedAt
        } else {
            null
        }
    }

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
            initializer: TagArrayBuilder<LongTextNoteEvent>.() -> Unit = {},
        ) = eventTemplate(KIND, description, createdAt) {
            dTag(dTag)
            alt("Blog post: $title")

            title(title)
            summary?.let { summary(it) }
            image?.let { image(it) }
            publishedAt?.let { publishedAt(it) }

            initializer()
        }
    }
}
