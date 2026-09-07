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
package com.vitorpamplona.quartz.experimental.publications

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.experimental.publications.tags.AuthorTag
import com.vitorpamplona.quartz.experimental.publications.tags.PublicationTypeTag
import com.vitorpamplona.quartz.experimental.publications.tags.VersionTag
import com.vitorpamplona.quartz.nip01Core.core.BaseAddressableEvent
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.TagArray
import com.vitorpamplona.quartz.nip01Core.core.TagArrayBuilder
import com.vitorpamplona.quartz.nip01Core.hints.AddressHintProvider
import com.vitorpamplona.quartz.nip01Core.hints.PubKeyHintProvider
import com.vitorpamplona.quartz.nip01Core.hints.types.AddressHint
import com.vitorpamplona.quartz.nip01Core.hints.types.PubKeyHint
import com.vitorpamplona.quartz.nip01Core.signers.EventTemplate
import com.vitorpamplona.quartz.nip01Core.signers.eventTemplate
import com.vitorpamplona.quartz.nip01Core.tags.aTag.ATag
import com.vitorpamplona.quartz.nip01Core.tags.dTag.dTag
import com.vitorpamplona.quartz.nip01Core.tags.hashtags.hashtags
import com.vitorpamplona.quartz.nip01Core.tags.people.PTag
import com.vitorpamplona.quartz.nip23LongContent.tags.ImageTag
import com.vitorpamplona.quartz.nip23LongContent.tags.SummaryTag
import com.vitorpamplona.quartz.nip23LongContent.tags.TitleTag
import com.vitorpamplona.quartz.nip50Search.SearchableEvent
import com.vitorpamplona.quartz.utils.TimeUtils

/**
 * A curated publication index (kind 30040) — NKBIP-01.
 *
 * **Not defined by a merged NIP.** NKBIP-01 is GitCitadel's "Curated Publications"
 * specification: an ordered, optionally hierarchical assembly of other events, the way a book
 * organizes chapters. The index carries the metadata and the table of contents; the sections
 * themselves are kind-30041 events named by its `a` tags.
 *
 * Amethyst parses this kind so a publication can be **named** — resolving the target of an
 * entity rating, a quote, or a reference. It deliberately does **not** implement the reader:
 * [sections] gives you the ordered coordinates, but assembling and rendering a book from them
 * is a separate feature.
 *
 * Per the spec the `content` field is empty; everything meaningful is in the tags.
 */
@Immutable
class PublicationIndexEvent(
    id: HexKey,
    pubKey: HexKey,
    createdAt: Long,
    tags: TagArray,
    content: String,
    sig: HexKey,
) : BaseAddressableEvent(id, pubKey, createdAt, KIND, tags, content, sig),
    AddressHintProvider,
    PubKeyHintProvider,
    SearchableEvent {
    override fun indexableContent() = listOfNotNull(title(), author(), summary()).joinToString("\n")

    override fun addressHints(): List<AddressHint> = tags.mapNotNull(ATag::parseAsHint)

    override fun linkedAddressIds(): List<String> = tags.mapNotNull(ATag::parseAddressId)

    override fun pubKeyHints(): List<PubKeyHint> = tags.mapNotNull(PTag::parseAsHint)

    override fun linkedPubKeys(): List<HexKey> = tags.mapNotNull(PTag::parseKey)

    /** The full title of the publication. Required by the spec, but absent in the wild. */
    fun title() = tags.firstNotNullOfOrNull(TitleTag::parse)

    /** The work's author as a human-readable name ("Aesop"), not a pubkey. */
    fun author() = tags.firstNotNullOfOrNull(AuthorTag::parse)

    fun summary() = tags.firstNotNullOfOrNull(SummaryTag::parse)

    fun image() = tags.firstNotNullOfOrNull(ImageTag::parse)

    /** How the publication wants to be displayed; `book` when unspecified. */
    fun type() = tags.firstNotNullOfOrNull(PublicationTypeTag::parse) ?: PublicationTypeTag.BOOK

    /** The edition label, e.g. "3rd edition". */
    fun version() = tags.firstNotNullOfOrNull(VersionTag::parse)

    fun topics() = hashtags()

    /**
     * The table of contents: the referenced section coordinates, in the order the spec says they
     * should be displayed in. Usually kind 30041 sections or nested 30040 indices.
     */
    fun sections() = tags.mapNotNull(ATag::parseAddressId)

    fun sectionCount() = sections().size

    /**
     * A display name for the publication, falling back to the `d` identifier when the (mandatory)
     * `title` tag is missing — slugs like `wuthering-heights` are readable enough to be worth
     * showing, and are what a reference to an unfetched publication has to work with.
     */
    fun titleOrIdentifier(): String = title()?.takeIf { it.isNotBlank() } ?: humanizeIdentifier(dTag())

    companion object {
        const val KIND = 30040

        /** `wuthering-heights` -> `Wuthering Heights`. Leaves anything that is not a slug alone. */
        fun humanizeIdentifier(dTag: String): String {
            val cleaned = dTag.replace('-', ' ').replace('_', ' ').trim()
            if (cleaned.isEmpty()) return dTag
            return cleaned
                .split(' ')
                .filter { it.isNotEmpty() }
                .joinToString(" ") { word ->
                    word.replaceFirstChar { if (it.isLowerCase()) it.titlecaseChar() else it }
                }
        }

        fun build(
            title: String,
            dTag: String,
            author: String? = null,
            summary: String? = null,
            image: String? = null,
            createdAt: Long = TimeUtils.now(),
            initializer: TagArrayBuilder<PublicationIndexEvent>.() -> Unit = {},
        ): EventTemplate<PublicationIndexEvent> =
            // The spec requires an empty content for this kind.
            eventTemplate(KIND, "", createdAt) {
                dTag(dTag)
                add(TitleTag.assemble(title))
                author?.let { add(AuthorTag.assemble(it)) }
                summary?.let { add(SummaryTag.assemble(it)) }
                image?.let { add(ImageTag.assemble(it)) }

                initializer()
            }
    }
}
