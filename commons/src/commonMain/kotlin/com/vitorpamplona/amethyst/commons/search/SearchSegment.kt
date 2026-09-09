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
package com.vitorpamplona.amethyst.commons.search

import androidx.compose.runtime.Immutable
import com.vitorpamplona.amethyst.commons.search.calendar.DateField
import com.vitorpamplona.amethyst.commons.search.calendar.SearchDate

/** Which end of a `from:`/`to:` pair a key token was written with. */
enum class KeyField {
    FROM,
    TO,
    ;

    val token: String get() = if (this == FROM) "from" else "to"

    companion object {
        fun of(name: String): KeyField? =
            when (name.lowercase()) {
                "from" -> FROM
                "to" -> TO
                else -> null
            }
    }
}

/**
 * One piece of what was typed: either a stretch of plain text, or a token lifted out of it.
 *
 * Every token carries [raw] exactly as it was typed, so the field can draw a chip over precisely
 * the characters it stands for and put them back verbatim when the chip is edited, beside the
 * normalised value the relay is actually asked for. A corrupt value — a failed bech32 checksum,
 * a day that does not exist — never becomes a token; it stays [Text], because a chip would claim
 * a filter that the query builder would not send.
 */
@Immutable
sealed interface SearchSegment {
    /** How many characters of the typed string this segment covers. */
    val length: Int

    /** A stretch of plain text: search terms, and anything that failed to become a token. */
    @Immutable
    data class Text(
        val text: String,
    ) : SearchSegment {
        override val length get() = text.length
    }

    /** `from:<npub>`, `to:<npub>` or a bare `npub1…`. A bare key has no [field] and stays a term. */
    @Immutable
    data class Key(
        val raw: String,
        val field: KeyField?,
        val pubkey: String,
    ) : SearchSegment {
        override val length get() = raw.length
    }

    /** `to:<note|nevent|naddr>` — an event, asked with `#e`, or an address, asked with `#a`. */
    @Immutable
    data class Pointer(
        val raw: String,
        val tag: String,
        val value: String,
    ) : SearchSegment {
        override val length get() = raw.length
    }

    /** `#hashtag`. [raw] keeps a trailing hyphen the normalised [tag] drops. */
    @Immutable
    data class Hashtag(
        val raw: String,
        val tag: String,
    ) : SearchSegment {
        override val length get() = raw.length
    }

    /** `since:YYYY-MM-DD` / `until:YYYY-MM-DD`, already resolved to the unix second it bounds. */
    @Immutable
    data class DateBound(
        val raw: String,
        val field: DateField,
        val date: SearchDate,
        val at: Long,
    ) : SearchSegment {
        override val length get() = raw.length
    }

    /** `label:<mark>` — a NIP-32 mark, opaque and asked as it stands. */
    @Immutable
    data class Label(
        val raw: String,
        val value: String,
    ) : SearchSegment {
        override val length get() = raw.length
    }

    /** A NIP-73 external scope: `site:`, `isbn:`, `geo:`, `isan:`, `doi:`, `podcast:*`. */
    @Immutable
    data class Scope(
        val raw: String,
        val field: String,
        val value: String,
    ) : SearchSegment {
        override val length get() = raw.length
    }

    /** `group:<id>` — a NIP-29 group, asked with `#h`. Case-exact: `General` and `general` differ. */
    @Immutable
    data class Group(
        val raw: String,
        val id: String,
    ) : SearchSegment {
        override val length get() = raw.length
    }
}

/** The characters a segment covers, whichever kind it is. */
val SearchSegment.rawText: String
    get() =
        when (this) {
            is SearchSegment.Text -> text
            is SearchSegment.Key -> raw
            is SearchSegment.Pointer -> raw
            is SearchSegment.Hashtag -> raw
            is SearchSegment.DateBound -> raw
            is SearchSegment.Label -> raw
            is SearchSegment.Scope -> raw
            is SearchSegment.Group -> raw
        }
