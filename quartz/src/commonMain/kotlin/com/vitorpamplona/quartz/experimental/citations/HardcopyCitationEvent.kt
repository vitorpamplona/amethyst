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
package com.vitorpamplona.quartz.experimental.citations

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.experimental.citations.tags.CitationTags
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.TagArray
import com.vitorpamplona.quartz.nip01Core.core.TagArrayBuilder
import com.vitorpamplona.quartz.nip01Core.signers.EventTemplate
import com.vitorpamplona.quartz.nip01Core.signers.eventTemplate
import com.vitorpamplona.quartz.nip23LongContent.tags.TitleTag
import com.vitorpamplona.quartz.utils.TimeUtils

/** A citation of something printed (kind 32): a book, a chapter, a journal paper. */
@Immutable
class HardcopyCitationEvent(
    id: HexKey,
    pubKey: HexKey,
    createdAt: Long,
    tags: TagArray,
    content: String,
    sig: HexKey,
) : CitationEvent(id, pubKey, createdAt, KIND, tags, content, sig) {
    fun pageRange() = value(CitationTags.PAGE_RANGE)

    fun chapterTitle() = value(CitationTags.CHAPTER_TITLE)

    fun editor() = value(CitationTags.EDITOR)

    /** The containing work — a journal or collection. */
    fun publishedIn() = value(CitationTags.PUBLISHED_IN)

    /** The volume, which rides in the second slot of the `published_in` tag rather than its own. */
    fun volume() = tags.firstNotNullOfOrNull { CitationTags.secondValue(it, CitationTags.PUBLISHED_IN) }

    fun doi() = value(CitationTags.DOI)

    override fun displayTitle(): String? = title() ?: chapterTitle() ?: publishedIn() ?: author()

    override fun hasSource(): Boolean = doi() != null || publishedIn() != null || super.hasSource()

    companion object {
        const val KIND = 32

        fun build(
            accessedOn: String,
            title: String? = null,
            author: String? = null,
            publishedIn: String? = null,
            volume: String? = null,
            pageRange: String? = null,
            doi: String? = null,
            note: String = "",
            createdAt: Long = TimeUtils.now(),
            initializer: TagArrayBuilder<HardcopyCitationEvent>.() -> Unit = {},
        ): EventTemplate<HardcopyCitationEvent> =
            eventTemplate(KIND, note, createdAt) {
                add(CitationTags.assemble(CitationTags.ACCESSED_ON, accessedOn))
                title?.let { add(TitleTag.assemble(it)) }
                author?.let { add(CitationTags.assemble(CitationTags.AUTHOR, it)) }
                pageRange?.let { add(CitationTags.assemble(CitationTags.PAGE_RANGE, it)) }
                // The volume shares the `published_in` tag, so it can only be written with it.
                publishedIn?.let { add(arrayOf(CitationTags.PUBLISHED_IN, it, volume ?: "")) }
                doi?.let { add(CitationTags.assemble(CitationTags.DOI, it)) }

                initializer()
            }
    }
}
