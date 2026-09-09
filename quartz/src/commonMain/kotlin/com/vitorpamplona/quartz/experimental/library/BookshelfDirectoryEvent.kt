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
import com.vitorpamplona.quartz.experimental.publications.PublicationSectionRef
import com.vitorpamplona.quartz.nip01Core.core.BaseAddressableEvent
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.TagArray
import com.vitorpamplona.quartz.nip01Core.core.TagArrayBuilder
import com.vitorpamplona.quartz.nip01Core.hints.AddressHintProvider
import com.vitorpamplona.quartz.nip01Core.hints.types.AddressHint
import com.vitorpamplona.quartz.nip01Core.signers.EventTemplate
import com.vitorpamplona.quartz.nip01Core.signers.eventTemplate
import com.vitorpamplona.quartz.nip01Core.tags.aTag.ATag
import com.vitorpamplona.quartz.nip01Core.tags.dTag.dTag
import com.vitorpamplona.quartz.nip23LongContent.tags.ImageTag
import com.vitorpamplona.quartz.nip23LongContent.tags.SummaryTag
import com.vitorpamplona.quartz.nip23LongContent.tags.TitleTag
import com.vitorpamplona.quartz.nip50Search.IndexableFieldVisitor
import com.vitorpamplona.quartz.nip50Search.SearchableEvent
import com.vitorpamplona.quartz.utils.TimeUtils

/**
 * A directory (kind 30045) — a curated shelf of publications and notes.
 *
 * **Not defined by any NIP.** The publishing client calls it a bookshelf directory: an
 * addressable personal list whose `a` and `e` tags name what is on the shelf, capped in practice
 * at a few hundred items.
 *
 * Its contents are shaped exactly like a publication index's table of contents — `a` and `e`
 * entries in tag order, carrying optional inline titles — so [items] reuses
 * [PublicationSectionRef] rather than growing a parallel parser that would drift from it.
 */
@Immutable
class BookshelfDirectoryEvent(
    id: HexKey,
    pubKey: HexKey,
    createdAt: Long,
    tags: TagArray,
    content: String,
    sig: HexKey,
) : BaseAddressableEvent(id, pubKey, createdAt, KIND, tags, content, sig),
    AddressHintProvider,
    SearchableEvent {
    override fun indexableContent() = listOfNotNull(title(), summary(), content).joinToString("\n")

    // The read path: the same fields indexableContent() joins, handed over without
    // building the joined string a scan would throw away.
    override fun forEachIndexableField(visitor: IndexableFieldVisitor) {
        if (!visitor.visit(title())) return
        if (!visitor.visit(summary())) return
        visitor.visit(content)
    }

    override fun addressHints(): List<AddressHint> = tags.mapNotNull(ATag::parseAsHint)

    override fun linkedAddressIds(): List<String> = tags.mapNotNull(ATag::parseAddressId)

    fun title() = tags.firstNotNullOfOrNull(TitleTag::parse)

    fun summary() = tags.firstNotNullOfOrNull(SummaryTag::parse)

    fun image() = tags.firstNotNullOfOrNull(ImageTag::parse)

    /** What is on the shelf, in the order the list gives. */
    fun items(): List<PublicationSectionRef> = PublicationSectionRef.fromTags(tags)

    fun itemCount() = items().size

    fun titleOrIdentifier(): String = title()?.takeIf { it.isNotBlank() } ?: dTag()

    companion object {
        const val KIND = 30045

        fun build(
            title: String,
            dTag: String,
            summary: String? = null,
            image: String? = null,
            description: String = "",
            createdAt: Long = TimeUtils.now(),
            initializer: TagArrayBuilder<BookshelfDirectoryEvent>.() -> Unit = {},
        ): EventTemplate<BookshelfDirectoryEvent> =
            eventTemplate(KIND, description, createdAt) {
                dTag(dTag)
                add(TitleTag.assemble(title))
                summary?.let { add(SummaryTag.assemble(it)) }
                image?.let { add(ImageTag.assemble(it)) }

                initializer()
            }
    }
}
