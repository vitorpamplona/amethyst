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
package com.vitorpamplona.quartz.nipCCGeocaching.curation

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.nip01Core.core.Address
import com.vitorpamplona.quartz.nip01Core.core.BaseAddressableEvent
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.TagArrayBuilder
import com.vitorpamplona.quartz.nip01Core.core.containsAllTagNamesWithValues
import com.vitorpamplona.quartz.nip01Core.hints.AddressHintProvider
import com.vitorpamplona.quartz.nip01Core.signers.eventTemplate
import com.vitorpamplona.quartz.nip01Core.tags.aTag.ATag
import com.vitorpamplona.quartz.nip01Core.tags.dTag.DTag
import com.vitorpamplona.quartz.nip01Core.tags.dTag.dTag
import com.vitorpamplona.quartz.nip01Core.tags.geohash.geohashes
import com.vitorpamplona.quartz.nip50Search.IndexableFieldVisitor
import com.vitorpamplona.quartz.nip50Search.SearchableEvent
import com.vitorpamplona.quartz.nip51Lists.tags.TitleTag
import com.vitorpamplona.quartz.nipCCGeocaching.curation.tags.ListTheme
import com.vitorpamplona.quartz.nipCCGeocaching.curation.tags.MapStyle
import com.vitorpamplona.quartz.utils.TimeUtils
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * A geocache curation list (kind 37517): an ordered collection of caches, presented as an
 * adventure, a trail, a treasure hunt, or whatever the creator has in mind.
 *
 * The `a` references may point at caches by any author — a list is not limited to its creator's
 * own caches — and their order is meaningful, so [geocaches] preserves it.
 *
 * `theme` and `map` are presentation *defaults* the viewer is allowed to override; see
 * [com.vitorpamplona.quartz.nipCCGeocaching.curation.tags.ListThemeTag].
 *
 * Required tags: `d`, `title`, and at least one `a`.
 */
@Immutable
class GeocacheCurationListEvent(
    id: HexKey,
    pubKey: HexKey,
    createdAt: Long,
    tags: Array<Array<String>>,
    content: String,
    sig: HexKey,
) : BaseAddressableEvent(id, pubKey, createdAt, KIND, tags, content, sig),
    AddressHintProvider,
    SearchableEvent {
    override fun indexableContent() = listOfNotNull(title(), description(), content).joinToString("\n")

    override fun forEachIndexableField(visitor: IndexableFieldVisitor) {
        if (!visitor.visit(title())) return
        if (!visitor.visit(description())) return
        visitor.visit(content)
    }

    override fun addressHints() = tags.mapNotNull(ATag::parseAsHint)

    override fun linkedAddressIds() = tags.mapNotNull(ATag::parseAddressId)

    fun title() = tags.listTitle()

    fun description() = tags.listDescription()

    fun image() = tags.listImage()

    fun geohashes() = tags.geohashes()

    /** The list's default page theme, or null when it names one this version does not know. */
    fun theme() = tags.listTheme()

    fun themeCode() = tags.listThemeCode()

    /** The list's initial map style, or null when it names one this version does not know. */
    fun mapStyle() = tags.listMapStyle()

    fun mapStyleCode() = tags.listMapStyleCode()

    /** The curated caches, in list order. */
    fun geocaches() = tags.curatedGeocaches()

    /** Every `a` reference in order, including any that do not point at a cache. */
    fun addresses() = tags.curatedAddresses()

    fun isWellFormed() = tags.containsAllTagNamesWithValues(REQUIRED_FIELDS) && geocaches().isNotEmpty()

    companion object {
        const val KIND = 37517

        /** Coarsest `g` tag worth publishing for a list (~156km). */
        const val MIN_TAGGED_PRECISION = 3

        /** Finest `g` tag NIP-CC asks a list for (~1.2km) — a trail centre, not a hiding spot. */
        const val MAX_TAGGED_PRECISION = 6

        val REQUIRED_FIELDS = setOf(DTag.TAG_NAME, TitleTag.TAG_NAME, ATag.TAG_NAME)

        @OptIn(ExperimentalUuidApi::class)
        fun build(
            title: String,
            geocaches: List<Address>,
            content: String = "",
            description: String? = null,
            image: String? = null,
            geohash: String? = null,
            theme: ListTheme? = null,
            mapStyle: MapStyle? = null,
            dTag: String = Uuid.random().toString(),
            createdAt: Long = TimeUtils.now(),
            initializer: TagArrayBuilder<GeocacheCurationListEvent>.() -> Unit = {},
        ) = eventTemplate(KIND, content, createdAt) {
            dTag(dTag)
            listTitle(title)
            geocaches(geocaches)

            description?.let { listDescription(it) }
            image?.let { listImage(it) }
            geohash?.let { listLocation(it) }
            theme?.let { listTheme(it) }
            mapStyle?.let { listMapStyle(it) }

            initializer()
        }
    }
}
