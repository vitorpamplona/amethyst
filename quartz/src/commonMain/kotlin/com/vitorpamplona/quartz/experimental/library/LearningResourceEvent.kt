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
import com.vitorpamplona.quartz.nip01Core.signers.EventTemplate
import com.vitorpamplona.quartz.nip01Core.signers.eventTemplate
import com.vitorpamplona.quartz.nip01Core.tags.dTag.dTag
import com.vitorpamplona.quartz.nip01Core.tags.hashtags.hashtags
import com.vitorpamplona.quartz.nip23LongContent.tags.ImageTag
import com.vitorpamplona.quartz.nip23LongContent.tags.SummaryTag
import com.vitorpamplona.quartz.nip23LongContent.tags.TitleTag
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

    companion object {
        const val KIND = 30142

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
