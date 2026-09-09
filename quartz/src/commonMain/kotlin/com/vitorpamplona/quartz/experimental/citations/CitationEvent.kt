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
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.TagArray
import com.vitorpamplona.quartz.nip23LongContent.tags.TitleTag
import com.vitorpamplona.quartz.nip50Search.SearchableEvent

/**
 * A citation — a reference to a source, published as its own event so other events can point at
 * it (kinds 31, 32 and 33).
 *
 * **Not defined by any NIP.** The shape is what `silberengel/jumble` publishes and renders. Four
 * kinds exist in that vocabulary; three are modelled here:
 *
 * | kind | | |
 * |---|---|---|
 * | 30 | internal (another nostr event) | **not** implemented — see below |
 * | 31 | [ExternalCitationEvent] | a URL |
 * | 32 | [HardcopyCitationEvent] | a book, chapter or paper |
 * | 33 | [PromptCitationEvent] | an LLM prompt and its answer |
 *
 * Kind 30 is deliberately left out: Quartz already registers kind 30 as a Jester chess move
 * (`JesterEvent`), so the two vocabularies collide on the wire and a kind-30 citation already
 * parses as a chess event here. Picking a winner is a protocol decision, not a parsing one.
 *
 * Everything shared across the three lives on this base; each subclass adds only its own fields.
 */
@Immutable
abstract class CitationEvent(
    id: HexKey,
    pubKey: HexKey,
    createdAt: Long,
    kind: Int,
    tags: TagArray,
    content: String,
    sig: HexKey,
) : Event(id, pubKey, createdAt, kind, tags, content, sig),
    SearchableEvent {
    override fun indexableContent() = listOfNotNull(title(), summary(), content).joinToString("\n")

    fun title() = tags.firstNotNullOfOrNull(TitleTag::parse)

    fun author() = value(CitationTags.AUTHOR)

    fun summary() = value(CitationTags.SUMMARY)

    /** When the citer consulted the source. Free-text as published, not a parsed date. */
    fun accessedOn() = value(CitationTags.ACCESSED_ON)

    fun publishedOn() = value(CitationTags.PUBLISHED_ON)

    fun publishedBy() = value(CitationTags.PUBLISHED_BY)

    fun location() = value(CitationTags.LOCATION)

    fun version() = value(CitationTags.VERSION)

    fun geohash() = value(CitationTags.GEOHASH)

    /** A display name for the citation, falling back to whatever else identifies the source. */
    open fun displayTitle(): String? = title() ?: author()

    /** A citation that names nothing cannot be rendered as a reference to anything. */
    open fun hasSource(): Boolean = title() != null || author() != null || content.isNotBlank()

    protected fun value(name: String) = tags.firstNotNullOfOrNull { CitationTags.value(it, name) }

    companion object {
        /** The kinds this file models. Kind 30 is excluded — see the class doc. */
        val KINDS = setOf(ExternalCitationEvent.KIND, HardcopyCitationEvent.KIND, PromptCitationEvent.KIND)
    }
}
