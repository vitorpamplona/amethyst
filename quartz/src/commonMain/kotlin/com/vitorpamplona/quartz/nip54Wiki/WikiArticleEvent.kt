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
package com.vitorpamplona.quartz.nip54Wiki

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.experimental.forks.IForkableEvent
import com.vitorpamplona.quartz.experimental.forks.parseForkedAddress
import com.vitorpamplona.quartz.experimental.forks.parseForkedEventId
import com.vitorpamplona.quartz.nip01Core.core.Address
import com.vitorpamplona.quartz.nip01Core.core.AddressSerializer
import com.vitorpamplona.quartz.nip01Core.core.AddressableEvent
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.TagArrayBuilder
import com.vitorpamplona.quartz.nip01Core.hints.AddressHintProvider
import com.vitorpamplona.quartz.nip01Core.hints.EventHintProvider
import com.vitorpamplona.quartz.nip01Core.hints.PubKeyHintProvider
import com.vitorpamplona.quartz.nip01Core.hints.types.AddressHint
import com.vitorpamplona.quartz.nip01Core.hints.types.EventIdHint
import com.vitorpamplona.quartz.nip01Core.hints.types.PubKeyHint
import com.vitorpamplona.quartz.nip01Core.signers.EventTemplate
import com.vitorpamplona.quartz.nip01Core.signers.eventTemplate
import com.vitorpamplona.quartz.nip01Core.tags.aTag.ATag
import com.vitorpamplona.quartz.nip01Core.tags.dTag.dTag
import com.vitorpamplona.quartz.nip01Core.tags.hashtags.hashtags
import com.vitorpamplona.quartz.nip01Core.tags.people.PTag
import com.vitorpamplona.quartz.nip01Core.tags.publishedAt.PublishedAtProvider
import com.vitorpamplona.quartz.nip10Notes.BaseNoteEvent
import com.vitorpamplona.quartz.nip10Notes.tags.MarkedETag
import com.vitorpamplona.quartz.nip18Reposts.quotes.QAddressableTag
import com.vitorpamplona.quartz.nip18Reposts.quotes.QEventTag
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
import com.vitorpamplona.quartz.nip50Search.IndexableFieldVisitor
import com.vitorpamplona.quartz.nip50Search.SearchableEvent
import com.vitorpamplona.quartz.utils.TimeUtils
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@Immutable
class WikiArticleEvent(
    id: HexKey,
    pubKey: HexKey,
    createdAt: Long,
    tags: Array<Array<String>>,
    content: String,
    sig: HexKey,
) : BaseNoteEvent(id, pubKey, createdAt, KIND, tags, content, sig),
    AddressableEvent,
    EventHintProvider,
    AddressHintProvider,
    PubKeyHintProvider,
    PublishedAtProvider,
    IForkableEvent,
    RootScope,
    SearchableEvent {
    override fun indexableContent() = listOfNotNull(title(), summary(), content).joinToString("\n")

    // The read path: hands over the same fields indexableContent() joins, without
    // building the joined string a scan would throw away.
    override fun forEachIndexableField(visitor: IndexableFieldVisitor) {
        if (!visitor.visit(title())) return
        if (!visitor.visit(summary())) return
        visitor.visit(content)
    }

    override fun eventHints(): List<EventIdHint> {
        val eHints = tags.mapNotNull(MarkedETag::parseAsHint)
        val qHints = tags.mapNotNull(QTag::parseEventAsHint)
        val nip19Hints = citedNIP19().eventHints()

        return eHints + qHints + nip19Hints
    }

    override fun linkedEventIds(): List<HexKey> {
        val result = ArrayList<HexKey>()
        result.addAll(referencedEvents())
        quotedEvents().mapTo(result) { it.eventId }
        result.addAll(citedNIP19().eventIds())
        return result
    }

    override fun addressHints(): List<AddressHint> {
        val aHints = tags.mapNotNull(ATag::parseAsHint)
        val qHints = tags.mapNotNull(QTag::parseAddressAsHint)
        val nip19Hints = citedNIP19().addressHints()

        return aHints + qHints + nip19Hints
    }

    override fun linkedAddressIds(): List<String> {
        val result = ArrayList<String>()
        result.addAll(referencedAddresses())
        quotedAddresses().mapTo(result) { it.address.toValue() }
        result.addAll(citedNIP19().addressIds())
        return result
    }

    override fun pubKeyHints(): List<PubKeyHint> {
        val pHints = tags.mapNotNull(PTag::parseAsHint)
        val nip19Hints = citedNIP19().pubKeyHints()

        return pHints + nip19Hints
    }

    override fun linkedPubKeys(): List<HexKey> {
        val result = ArrayList<HexKey>()
        result.addAll(mentionKeys())
        result.addAll(citedNIP19().pubKeys())
        return result
    }

    override fun dTag() = tags.dTag()

    override fun address() = Address(kind, pubKey, dTag())

    override fun addressTag() = Address.assemble(kind, pubKey, dTag())

    fun topics() = hashtags()

    fun title() = tags.firstNotNullOfOrNull(TitleTag::parse)

    fun image() = tags.firstNotNullOfOrNull(ImageTag::parse)

    fun summary() = tags.firstNotNullOfOrNull(SummaryTag::parse)

    /** Users this article mentions (`p`), in tag order. Same shape as [com.vitorpamplona.quartz.nip10Notes.BaseThreadedEvent.mentions]. */
    fun mentions(): List<PTag> = tags.mapNotNull(PTag::parse)

    /** The keys of [mentions], in tag order. */
    fun mentionKeys(): List<HexKey> = tags.mapNotNull(PTag::parseKey)

    /** NIP-18 quotes (`q`) of regular events, in tag order. */
    fun quotedEvents(): List<QEventTag> = tags.mapNotNull(QEventTag::parse)

    /** NIP-18 quotes (`q`) of addressable events, in tag order. */
    fun quotedAddresses(): List<QAddressableTag> = tags.mapNotNull(QAddressableTag::parse)

    /**
     * Every `e` this article carries, as ids in tag order. NIP-54 points at other article
     * versions with them, marked `fork` (the [forkFromVersion] pin) or `defer`.
     */
    fun referencedEvents(): List<HexKey> = tags.mapNotNull(MarkedETag::parseId)

    /**
     * Every `a` this article carries, as shape-checked address ids in tag order. NIP-54 points
     * at other articles with them, marked `fork` (the [forkFromAddress]) or `defer`.
     */
    fun referencedAddresses(): List<String> = tags.mapNotNull { ATag.parseAddressId(it)?.takeIf(AddressSerializer::isAddressShape) }

    override fun isAFork() = forkFromVersion() != null || forkFromAddress() != null

    override fun forkFromAddress() = tags.firstNotNullOfOrNull(::parseForkedAddress)

    override fun forkFromVersion() = tags.firstNotNullOfOrNull(MarkedETag::parseForkedEventId)

    override fun publishedAt(): Long? {
        val publishedAt = tags.firstNotNullOfOrNull(PublishedAtTag::parse) ?: return null

        // removes posts in the future.
        return if (publishedAt <= createdAt) {
            publishedAt
        } else {
            null
        }
    }

    companion object {
        const val KIND = 30818

        @OptIn(ExperimentalUuidApi::class)
        fun build(
            description: String,
            title: String,
            summary: String? = null,
            image: String? = null,
            publishedAt: Long? = null,
            dTag: String = Uuid.random().toString(),
            createdAt: Long = TimeUtils.now(),
            initializer: TagArrayBuilder<WikiArticleEvent>.() -> Unit = {},
        ): EventTemplate<WikiArticleEvent> =
            eventTemplate(KIND, description, createdAt) {
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
    "Renamed to WikiArticleEvent. NIP-54 names kind 30818 the wiki article.",
    ReplaceWith("WikiArticleEvent", "com.vitorpamplona.quartz.nip54Wiki.WikiArticleEvent"),
)
typealias WikiNoteEvent = WikiArticleEvent
