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
import com.vitorpamplona.quartz.experimental.nipsOnNostr.NipTextEvent
import com.vitorpamplona.quartz.nip01Core.core.Address
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.Tag
import com.vitorpamplona.quartz.nip01Core.core.TagArray
import com.vitorpamplona.quartz.nip01Core.core.has
import com.vitorpamplona.quartz.nip01Core.core.isValid
import com.vitorpamplona.quartz.nip23LongContent.LongTextNoteEvent
import com.vitorpamplona.quartz.nip54Wiki.WikiNoteEvent

/**
 * One entry in a kind-30040 publication's table of contents.
 *
 * NKBIP-01 documents `["a", "<kind:pubkey:d>", "<relay hint>", "<event id>"]`, but what the
 * publishing clients actually emit is looser, and reading only the documented shape loses most
 * of a real book's contents:
 *
 * - **`e` tags are sections too**, interleaved with `a` tags in tag order. An index may list its
 *   chapters entirely by event id.
 * - **Slot 2 may be a title** rather than a relay hint. This is the valuable one: it lets a
 *   reader draw the whole table of contents from the index alone, before a single section event
 *   has been fetched.
 * - **Slot 3 may be a nesting level** (1..6) rather than an event id — a part containing
 *   chapters. Both are accepted; a 64-hex value is the documented version pin, a small integer
 *   is a level.
 * - **Uppercase `A` / `E` are not sections.** On a derivative work they name the original
 *   source, so matching is case-sensitive; treating them as content would splice the wrong
 *   book into the contents.
 */
@Immutable
data class PublicationSectionRef(
    /** The coordinate this entry points at, when it was listed by address. */
    val address: Address?,
    /** The event id this entry points at, when it was listed by id (or pinned to a revision). */
    val eventId: HexKey?,
    /** A title carried by the index itself, available without fetching the section. */
    val title: String?,
    /** Nesting depth, 1 for a top-level entry. */
    val level: Int,
) {
    /** A stable key for de-duplicating and for keying a lazy list row. */
    fun key(): String = address?.toValue() ?: eventId ?: ""

    companion object {
        const val MAX_LEVEL = 6

        /** The kinds an index may list. Anything else is a reference, not a section to read. */
        val SECTION_KINDS =
            setOf(
                PublicationContentEvent.KIND,
                PublicationIndexEvent.KIND,
                LongTextNoteEvent.KIND,
                WikiNoteEvent.KIND,
                NipTextEvent.KIND,
            )

        /**
         * Reads the ordered contents of an index. Order is tag order, because that is the order
         * the spec says to display in, and `a` and `e` entries interleave.
         */
        fun fromIndex(event: PublicationIndexEvent): List<PublicationSectionRef> = fromTags(event.tags)

        /**
         * The tag-level entry point. A kind-30045 directory lists its shelf with the same `a`/`e`
         * grammar, so it reads its contents through here rather than growing a parallel parser
         * that would drift from this one.
         */
        fun fromTags(tags: TagArray): List<PublicationSectionRef> =
            tags.mapNotNull { tag ->
                when {
                    isSectionAddressTag(tag) -> fromAddressTag(tag)
                    isSectionEventTag(tag) -> fromEventTag(tag)
                    else -> null
                }
            }

        // Case-sensitive: `A` is the original-source tag of a derivative work, not a section.
        private fun isSectionAddressTag(tag: Tag) = tag.has(1) && tag[0] == "a" && tag[1].isNotEmpty()

        private fun isSectionEventTag(tag: Tag) = tag.has(1) && tag[0] == "e" && tag[1].isNotEmpty()

        private fun fromAddressTag(tag: Tag): PublicationSectionRef? {
            val address = Address.parse(tag[1].trim()) ?: return null

            val slot2 = tag.getOrNull(2)?.trim().orEmpty()
            val slot3 = tag.getOrNull(3)?.trim().orEmpty()

            return PublicationSectionRef(
                address = address,
                // The documented slot-3 event id pins the entry to one revision.
                eventId = slot3.takeIf { it.isValid() },
                title = slot2.takeIf { it.isNotEmpty() && !looksLikeRelayUrl(it) && it.toIntOrNull() == null },
                level = slot3.toIntOrNull()?.coerceIn(1, MAX_LEVEL) ?: 1,
            )
        }

        private fun fromEventTag(tag: Tag): PublicationSectionRef? {
            val id = tag[1].trim().lowercase()
            if (!id.isValid()) return null

            val slot3 = tag.getOrNull(3)?.trim().orEmpty()

            return PublicationSectionRef(
                address = null,
                eventId = id,
                // An `e` entry has no coordinate to derive a name from, so the index's own title
                // slot is the only one available before the event arrives.
                title = tag.getOrNull(2)?.trim()?.takeIf { it.isNotEmpty() && !looksLikeRelayUrl(it) },
                level = slot3.toIntOrNull()?.coerceIn(1, MAX_LEVEL) ?: 1,
            )
        }

        private fun looksLikeRelayUrl(value: String): Boolean =
            value.startsWith("wss://", ignoreCase = true) ||
                value.startsWith("ws://", ignoreCase = true) ||
                value.startsWith("http://", ignoreCase = true) ||
                value.startsWith("https://", ignoreCase = true)
    }
}
