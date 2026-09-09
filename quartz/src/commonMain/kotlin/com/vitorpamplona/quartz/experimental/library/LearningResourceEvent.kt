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
package com.vitorpamplona.quartz.experimental.library

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.nip01Core.core.BaseAddressableEvent
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.TagArray
import com.vitorpamplona.quartz.nip01Core.core.TagArrayBuilder
import com.vitorpamplona.quartz.nip01Core.core.fastForEach
import com.vitorpamplona.quartz.nip01Core.core.firstTagValue
import com.vitorpamplona.quartz.nip01Core.core.firstTagValueAsLong
import com.vitorpamplona.quartz.nip01Core.core.firstTagValueFor
import com.vitorpamplona.quartz.nip01Core.signers.EventTemplate
import com.vitorpamplona.quartz.nip01Core.signers.eventTemplate
import com.vitorpamplona.quartz.nip01Core.tags.dTag.dTag
import com.vitorpamplona.quartz.nip01Core.tags.hashtags.hashtags
import com.vitorpamplona.quartz.nip23LongContent.tags.ImageTag
import com.vitorpamplona.quartz.nip23LongContent.tags.SummaryTag
import com.vitorpamplona.quartz.nip23LongContent.tags.TitleTag
import com.vitorpamplona.quartz.nip50Search.IndexableFieldVisitor
import com.vitorpamplona.quartz.nip50Search.SearchableEvent
import com.vitorpamplona.quartz.nip51Lists.tags.DescriptionTag
import com.vitorpamplona.quartz.nip51Lists.tags.NameTag
import com.vitorpamplona.quartz.utils.TimeUtils

/**
 * A learning resource (kind 30142) — a course, tutorial or lesson.
 *
 * **Not defined by any NIP.** The shape is what the publishing clients emit: a titled,
 * cover-illustrated body, addressable so it can be revised in place. The reference Android client
 * marks it `reader = true`, i.e. long enough to read rather than skim, which is why the body is
 * rendered in full rather than as a blurb.
 */
@Immutable
class LearningResourceEvent(
    id: HexKey,
    pubKey: HexKey,
    createdAt: Long,
    tags: TagArray,
    content: String,
    sig: HexKey,
) : BaseAddressableEvent(id, pubKey, createdAt, KIND, tags, content, sig),
    SearchableEvent {
    override fun indexableContent() = listOfNotNull(title(), summary(), content).joinToString("\n")

    // The read path: the same fields indexableContent() joins, handed over without
    // building the joined string a scan would throw away.
    override fun forEachIndexableField(visitor: IndexableFieldVisitor) {
        if (!visitor.visit(title())) return
        if (!visitor.visit(summary())) return
        visitor.visit(content)
    }

    /**
     * Publishers split on which vocabulary they use: most emit `title`/`summary`, but the ones
     * that tag themselves `type: LearningResource` follow schema.org and emit `name`/`description`
     * instead. Reading only the first spelling left those rendering as their `d` slug, so both are
     * accepted with `title`/`summary` winning where an event carries both.
     */
    fun title() = tags.firstNotNullOfOrNull(TitleTag::parse) ?: tags.firstNotNullOfOrNull(NameTag::parse)

    fun summary() = tags.firstNotNullOfOrNull(SummaryTag::parse) ?: tags.firstNotNullOfOrNull(DescriptionTag::parse)

    fun image() = tags.firstNotNullOfOrNull(ImageTag::parse)

    fun topics() = hashtags()

    /** A display name, falling back to the `d` identifier when the title is missing. */
    fun titleOrIdentifier(): String = title()?.takeIf { it.isNotBlank() } ?: dTag()

    /**
     * Who made it. The book publishers write `author`/`artist`; the schema.org publishers write a
     * structured creator, of which only `creator:name` is worth showing.
     */
    fun author() = tags.firstTagValueFor(AUTHOR, ARTIST) ?: tags.firstTagValue(CREATOR_NAME)

    /** A year or a date. Four spellings are in the wild and none of them is normalised. */
    fun published() = tags.firstTagValueFor(PUBLISHED, PUBLISHED_ON, DATE_PUBLISHED, RELEASE_DATE)

    /** BCP-47-ish, e.g. `de`. Note this is `inLanguage`, not NIP-32's `l`, which is a label. */
    fun language() = tags.firstTagValue(IN_LANGUAGE)

    fun license() = tags.firstTagValue(LICENSE_ID)

    /** `null` when the publisher did not say, which is not the same as "no". */
    fun isFreeToAccess() = tags.firstTagValue(IS_ACCESSIBLE_FOR_FREE)?.let { it.equals("true", ignoreCase = true) }

    /** The file this resource *is*, when it ships as one: a PDF, a webxdc bundle, an archive. */
    fun contentUrl() = tags.firstTagValue(ENCODING_CONTENT_URL)

    fun contentFormat() = tags.firstTagValue(ENCODING_FORMAT)

    fun contentSize() = tags.firstTagValueAsLong(ENCODING_SIZE)

    /** The Blossom hash of that file, which is also how the viewer verifies what it downloaded. */
    fun contentHash() = tags.firstTagValue(ENCODING_SHA256)

    /** School subjects: "Biologie", "Informatik". */
    fun subjects(preferredLanguage: String? = null) = facetLabelsFor(ABOUT, preferredLanguage)

    /** What kind of thing it is: "Arbeitsmaterial", "Softwareanwendung". */
    fun resourceTypes(preferredLanguage: String? = null) = facetLabelsFor(LEARNING_RESOURCE_TYPE, preferredLanguage)

    /** Who it is for: "Primarbereich", "Sekundarbereich I". */
    fun educationalLevels(preferredLanguage: String? = null) = facetLabelsFor(EDUCATIONAL_LEVEL, preferredLanguage)

    /**
     * Every facet worth showing, in the order a reader wants them: what kind of thing it is, who
     * it is for, what it is about.
     *
     * Deduplicated across facets because the vocabularies overlap — a resource about "Informatik"
     * routinely carries that label under two ids and again under a second facet — and a chip row
     * that repeats itself reads as a bug.
     */
    fun facetLabels(preferredLanguage: String? = null): List<String> =
        LinkedHashSet<String>()
            .apply {
                addAll(resourceTypes(preferredLanguage))
                addAll(educationalLevels(preferredLanguage))
                addAll(subjects(preferredLanguage))
            }.toList()

    /**
     * One facet of the schema.org vocabularies these publishers use.
     *
     * A facet is spelled as a pair of flat tags — `about:id` carries a URI nobody wants to read
     * and `about:prefLabel:de` carries the label they do — repeated once per value. Only labels
     * are returned, and only in one language: an event routinely carries the same facet in six
     * languages, so concatenating them all would read as gibberish. The same label also repeats
     * across ids that map to it, so values are deduplicated in publication order.
     */
    private fun facetLabelsFor(
        facet: String,
        preferredLanguage: String?,
    ): List<String> {
        val prefix = "$facet:$PREF_LABEL:"
        val byLanguage = LinkedHashMap<String, LinkedHashSet<String>>()

        tags.fastForEach { tag ->
            if (tag.size > 1 && tag[1].isNotEmpty() && tag[0].startsWith(prefix)) {
                byLanguage.getOrPut(tag[0].substring(prefix.length)) { LinkedHashSet() }.add(tag[1])
            }
        }

        if (byLanguage.isEmpty()) return emptyList()

        val language =
            preferredLanguage?.takeIf { byLanguage.containsKey(it) }
                ?: language()?.takeIf { byLanguage.containsKey(it) }
                ?: byLanguage.keys.first()

        return byLanguage[language]?.toList() ?: emptyList()
    }

    companion object {
        const val KIND = 30142

        private const val AUTHOR = "author"
        private const val ARTIST = "artist"
        private const val CREATOR_NAME = "creator:name"
        private const val PUBLISHED = "published"
        private const val PUBLISHED_ON = "published_on"
        private const val DATE_PUBLISHED = "datePublished"
        private const val RELEASE_DATE = "release_date"
        private const val IN_LANGUAGE = "inLanguage"
        private const val LICENSE_ID = "license:id"
        private const val IS_ACCESSIBLE_FOR_FREE = "isAccessibleForFree"
        private const val ENCODING_CONTENT_URL = "encoding:contentUrl"
        private const val ENCODING_FORMAT = "encoding:encodingFormat"
        private const val ENCODING_SIZE = "encoding:contentSize"
        private const val ENCODING_SHA256 = "encoding:sha256"
        private const val PREF_LABEL = "prefLabel"
        private const val ABOUT = "about"
        private const val LEARNING_RESOURCE_TYPE = "learningResourceType"
        private const val EDUCATIONAL_LEVEL = "educationalLevel"

        fun build(
            title: String,
            dTag: String,
            content: String,
            summary: String? = null,
            image: String? = null,
            createdAt: Long = TimeUtils.now(),
            initializer: TagArrayBuilder<LearningResourceEvent>.() -> Unit = {},
        ): EventTemplate<LearningResourceEvent> =
            eventTemplate(KIND, content, createdAt) {
                dTag(dTag)
                add(TitleTag.assemble(title))
                summary?.let { add(SummaryTag.assemble(it)) }
                image?.let { add(ImageTag.assemble(it)) }

                initializer()
            }
    }
}
