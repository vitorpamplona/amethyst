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
package com.vitorpamplona.quartz.nip99Classifieds

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.nip01Core.core.BaseAddressableEvent
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.TagArrayBuilder
import com.vitorpamplona.quartz.nip01Core.core.containsAllTagNamesWithValues
import com.vitorpamplona.quartz.nip01Core.hints.AddressHintProvider
import com.vitorpamplona.quartz.nip01Core.hints.EventHintProvider
import com.vitorpamplona.quartz.nip01Core.hints.PubKeyHintProvider
import com.vitorpamplona.quartz.nip01Core.hints.types.AddressHint
import com.vitorpamplona.quartz.nip01Core.hints.types.EventIdHint
import com.vitorpamplona.quartz.nip01Core.hints.types.PubKeyHint
import com.vitorpamplona.quartz.nip01Core.signers.eventTemplate
import com.vitorpamplona.quartz.nip01Core.tags.aTag.ATag
import com.vitorpamplona.quartz.nip01Core.tags.dTag.dTag
import com.vitorpamplona.quartz.nip01Core.tags.events.ETag
import com.vitorpamplona.quartz.nip01Core.tags.hashtags.HashtagTag
import com.vitorpamplona.quartz.nip01Core.tags.hashtags.hashtags
import com.vitorpamplona.quartz.nip01Core.tags.people.PTag
import com.vitorpamplona.quartz.nip01Core.tags.publishedAt.PublishedAtProvider
import com.vitorpamplona.quartz.nip10Notes.content.findNostrUris
import com.vitorpamplona.quartz.nip18Reposts.quotes.QTag
import com.vitorpamplona.quartz.nip19Bech32.addressHints
import com.vitorpamplona.quartz.nip19Bech32.addressIds
import com.vitorpamplona.quartz.nip19Bech32.entities.Entity
import com.vitorpamplona.quartz.nip19Bech32.eventHints
import com.vitorpamplona.quartz.nip19Bech32.eventIds
import com.vitorpamplona.quartz.nip19Bech32.pubKeyHints
import com.vitorpamplona.quartz.nip19Bech32.pubKeys
import com.vitorpamplona.quartz.nip23LongContent.tags.ImageTag
import com.vitorpamplona.quartz.nip23LongContent.tags.PublishedAtTag
import com.vitorpamplona.quartz.nip23LongContent.tags.SummaryTag
import com.vitorpamplona.quartz.nip23LongContent.tags.TitleTag
import com.vitorpamplona.quartz.nip50Search.IndexableFieldVisitor
import com.vitorpamplona.quartz.nip50Search.SearchableEvent
import com.vitorpamplona.quartz.nip57Zaps.splits.zapSplitHints
import com.vitorpamplona.quartz.nip57Zaps.splits.zapSplitPubKeys
import com.vitorpamplona.quartz.nip92IMeta.imetas
import com.vitorpamplona.quartz.nip99Classifieds.tags.ConditionTag
import com.vitorpamplona.quartz.nip99Classifieds.tags.LocationTag
import com.vitorpamplona.quartz.nip99Classifieds.tags.PriceTag
import com.vitorpamplona.quartz.nip99Classifieds.tags.StatusTag
import com.vitorpamplona.quartz.utils.TimeUtils
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@Immutable
class ClassifiedsEvent(
    id: HexKey,
    pubKey: HexKey,
    createdAt: Long,
    tags: Array<Array<String>>,
    content: String,
    sig: HexKey,
) : BaseAddressableEvent(id, pubKey, createdAt, KIND, tags, content, sig),
    PublishedAtProvider,
    PubKeyHintProvider,
    EventHintProvider,
    AddressHintProvider,
    SearchableEvent {
    // A listing is searched by where it is and what category it is in as much as by
    // its title: the location and the `t` categories follow the body.
    override fun indexableContent() = (listOfNotNull(title(), summary(), content, location()) + categories()).joinToString("\n")

    // The read path: hands over the same fields indexableContent() joins, without
    // building the joined string a scan would throw away.
    override fun forEachIndexableField(visitor: IndexableFieldVisitor) {
        if (!visitor.visit(title())) return
        if (!visitor.visit(summary())) return
        if (!visitor.visit(content)) return
        if (!visitor.visit(location())) return
        // Walks the tags in place: this runs per event per search keystroke, and
        // categories() would build a list only to discard it.
        for (tag in tags) {
            val category = HashtagTag.parse(tag) ?: continue
            if (!visitor.visit(category)) return
        }
    }

    @kotlinx.serialization.Transient
    @kotlin.jvm.Transient
    private var citedNIP19Cache: List<Entity>? = null

    /** NIP-19 entities cited as `nostr:` URIs in the markdown description, parsed once. */
    fun citedNIP19(): List<Entity> = citedNIP19Cache ?: findNostrUris(content).also { citedNIP19Cache = it }

    override fun pubKeyHints(): List<PubKeyHint> = tags.mapNotNull(PTag::parseAsHint) + tags.zapSplitHints() + citedNIP19().pubKeyHints()

    // linked*() run on every relay copy of the event, and each is three tag scans plus
    // concatenations; the event is immutable, so they are built once per instance.
    @kotlinx.serialization.Transient
    @kotlin.jvm.Transient
    private var linkedPubKeysCache: List<HexKey>? = null

    @kotlinx.serialization.Transient
    @kotlin.jvm.Transient
    private var linkedEventIdsCache: List<HexKey>? = null

    @kotlinx.serialization.Transient
    @kotlin.jvm.Transient
    private var linkedAddressIdsCache: List<String>? = null

    override fun linkedPubKeys(): List<HexKey> = linkedPubKeysCache ?: (tags.mapNotNull(PTag::parseKey) + tags.zapSplitPubKeys() + citedNIP19().pubKeys()).also { linkedPubKeysCache = it }

    override fun eventHints(): List<EventIdHint> = tags.mapNotNull(ETag::parseAsHint) + tags.mapNotNull(QTag::parseEventAsHint) + citedNIP19().eventHints()

    override fun linkedEventIds(): List<HexKey> = linkedEventIdsCache ?: (tags.mapNotNull(ETag::parseId) + tags.mapNotNull(QTag::parseEventId) + citedNIP19().eventIds()).also { linkedEventIdsCache = it }

    override fun addressHints(): List<AddressHint> = tags.mapNotNull(ATag::parseAsHint) + tags.mapNotNull(QTag::parseAddressAsHint) + citedNIP19().addressHints()

    override fun linkedAddressIds(): List<String> =
        linkedAddressIdsCache
            ?: (tags.mapNotNull(ATag::parseAddressId) + tags.mapNotNull(QTag::parseValidAddress) + citedNIP19().addressIds()).also { linkedAddressIdsCache = it }

    fun title() = tags.firstNotNullOfOrNull(TitleTag::parse)

    fun image() = tags.firstNotNullOfOrNull(ImageTag::parse)

    fun condition() = tags.firstNotNullOfOrNull(ConditionTag::parse)

    fun conditionValid() = tags.firstNotNullOfOrNull(ConditionTag::parseCondition)

    fun images() = tags.mapNotNull(ImageTag::parse)

    fun status() = tags.firstNotNullOfOrNull(StatusTag::parse)

    fun summary() = tags.firstNotNullOfOrNull(SummaryTag::parse)

    fun price() = tags.firstNotNullOfOrNull(PriceTag::parse)

    fun location() = tags.firstNotNullOfOrNull(LocationTag::parse)

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

    fun categories() = tags.hashtags()

    fun isWellFormed() = tags.containsAllTagNamesWithValues(REQUIRED_FIELDS)

    fun imageMetas(): List<ProductImageMeta> {
        val images = images()
        val imetas = imetas()

        val imetaSet = imetas.associateBy { it.url }

        return images.map {
            val imeta = imetaSet[it]
            if (imeta != null) {
                ProductImageMeta.parse(imeta)
            } else {
                ProductImageMeta(it)
            }
        }
    }

    companion object {
        const val KIND = 30402

        val REQUIRED_FIELDS = setOf(TitleTag.TAG_NAME, PriceTag.TAG_NAME, ImageTag.TAG_NAME)

        @OptIn(ExperimentalUuidApi::class)
        fun build(
            title: String,
            price: PriceTag,
            description: String,
            location: String? = null,
            condition: ConditionTag.CONDITION? = null,
            images: List<String>? = null,
            status: StatusTag.STATUS = StatusTag.STATUS.ACTIVE,
            dTag: String = Uuid.random().toString(),
            createdAt: Long = TimeUtils.now(),
            initializer: TagArrayBuilder<ClassifiedsEvent>.() -> Unit = {},
        ) = eventTemplate(KIND, description, createdAt) {
            dTag(dTag)
            title(title)
            price(price)
            status(status)

            condition?.let { condition(it) }
            location?.let { location(it) }
            images?.let { images(images) }

            initializer()
        }
    }
}
