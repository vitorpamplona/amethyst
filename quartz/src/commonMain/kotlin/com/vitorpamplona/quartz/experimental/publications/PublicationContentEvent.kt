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
import com.vitorpamplona.quartz.experimental.publications.tags.WikilinkTag
import com.vitorpamplona.quartz.nip01Core.core.BaseAddressableEvent
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.TagArray
import com.vitorpamplona.quartz.nip01Core.core.TagArrayBuilder
import com.vitorpamplona.quartz.nip01Core.signers.EventTemplate
import com.vitorpamplona.quartz.nip01Core.signers.eventTemplate
import com.vitorpamplona.quartz.nip01Core.tags.dTag.dTag
import com.vitorpamplona.quartz.nip23LongContent.tags.TitleTag
import com.vitorpamplona.quartz.nip50Search.SearchableEvent
import com.vitorpamplona.quartz.utils.TimeUtils

/**
 * A publication section (kind 30041) — NKBIP-01.
 *
 * Also called a zettel, episode or chapter: the actual prose that a [PublicationIndexEvent]'s
 * table of contents points at. One section per event, named by a `title` tag, with the body in
 * `content`.
 *
 * Two things make it unlike a long-form note:
 *
 * - The content **may be AsciiDoc**, not Markdown. Amethyst has no AsciiDoc renderer, so the
 *   body is shown as plain text; that degrades to something readable rather than to a screen of
 *   stray markup, since AsciiDoc's syntax is sparse in ordinary prose.
 * - It may carry `wikilink` tags — `["wikilink", "<target>", "<pubkey>", "<relay>", "<event id>"]`
 *   — resolving the `[[double bracket]]` references in the body. [wikilinkTargets] exposes the
 *   targets; resolving them to events is left to the reader that does not exist yet.
 */
@Immutable
class PublicationContentEvent(
    id: HexKey,
    pubKey: HexKey,
    createdAt: Long,
    tags: TagArray,
    content: String,
    sig: HexKey,
) : BaseAddressableEvent(id, pubKey, createdAt, KIND, tags, content, sig),
    SearchableEvent {
    override fun indexableContent() = listOfNotNull(title(), content).joinToString("\n")

    /** The section's title ("Introduction", "Chapter 1"). Required by the spec. */
    fun title() = tags.firstNotNullOfOrNull(TitleTag::parse)

    /** A display name for the section, falling back to the `d` identifier when `title` is absent. */
    fun titleOrIdentifier(): String = title()?.takeIf { it.isNotBlank() } ?: PublicationIndexEvent.humanizeIdentifier(dTag())

    /** The `[[wikilink]]` references this section declares, in event order. */
    fun wikilinks(): List<WikilinkTag> = tags.mapNotNull(WikilinkTag::parse)

    /** The `[[wikilink]]` targets this section references, in event order. */
    fun wikilinkTargets(): List<String> = wikilinks().map { it.target }

    /**
     * Looks up one `[[target]]` from the body. Matching is case-insensitive and ignores the
     * separator style, because the body writes prose ("the Farmer") where the tag carries a slug
     * ("the-farmer").
     */
    fun wikilinkFor(target: String): WikilinkTag? {
        val needle = normalizeWikilink(target)
        return wikilinks().firstOrNull { normalizeWikilink(it.target) == needle }
    }

    companion object {
        const val KIND = 30041
        const val WIKILINK_TAG = WikilinkTag.TAG_NAME

        /** Case- and separator-insensitive key for matching a body reference to a `wikilink` tag. */
        fun normalizeWikilink(target: String): String =
            target
                .trim()
                .lowercase()
                .replace(' ', '-')
                .replace('_', '-')

        fun build(
            title: String,
            dTag: String,
            content: String,
            createdAt: Long = TimeUtils.now(),
            initializer: TagArrayBuilder<PublicationContentEvent>.() -> Unit = {},
        ): EventTemplate<PublicationContentEvent> =
            eventTemplate(KIND, content, createdAt) {
                dTag(dTag)
                add(TitleTag.assemble(title))

                initializer()
            }
    }
}
