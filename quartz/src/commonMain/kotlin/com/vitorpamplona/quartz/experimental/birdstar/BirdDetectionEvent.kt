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
package com.vitorpamplona.quartz.experimental.birdstar

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.firstTagValue
import com.vitorpamplona.quartz.nip31Alts.alt
import com.vitorpamplona.quartz.nip50Search.SearchableEvent

/**
 * Birdstar bird detection (kind 2473).
 *
 * An app-specific **regular** kind published by the Birdstar app
 * (`birdstar.app`) — a single bird sighting/detection. It is **not** defined by
 * any NIP; the schema below is derived from events seen in the wild. It is the
 * per-sighting companion of the replaceable [BirdexEvent] (kind 12473) life list.
 *
 * The event carries no body — its payload is in the tags:
 *
 * - `n` — the detected species' scientific name (e.g. `Porphyrio martinica`).
 * - `i` — an external identity reference for the species, a Wikidata entity URL
 *         (NIP-73 style, e.g. `https://www.wikidata.org/entity/Q27074644`).
 * - `g` — a geohash of the detection location (standard NIP-01 `g` tag; use
 *         `Event.geohashes()` to read it).
 * - `alt` — a human-readable summary written by the publisher
 *           (e.g. `Bird detection: Purple Gallinule (Porphyrio martinica)`).
 */
@Immutable
class BirdDetectionEvent(
    id: HexKey,
    pubKey: HexKey,
    createdAt: Long,
    tags: Array<Array<String>>,
    content: String,
    sig: HexKey,
) : Event(id, pubKey, createdAt, KIND, tags, content, sig),
    SearchableEvent {
    override fun indexableContent() = listOfNotNull(summary(), speciesName()).joinToString("\n")

    /** Scientific name of the detected species, from the `n` tag (may be null). */
    fun speciesName() = tags.firstTagValue("n")

    /**
     * External species reference (Wikidata entity URL), from the `i` tag.
     * Only http(s) URLs are returned — UIs render this as a clickable link,
     * so other schemes are rejected here rather than at every call site.
     */
    fun speciesReference() = tags.firstTagValue("i")?.takeIf { it.startsWith("https://") || it.startsWith("http://") }

    /** Publisher-provided human-readable summary, from the NIP-31 `alt` tag (may be null). */
    fun summary() = tags.alt()

    /**
     * Common (vernacular) species name, parsed out of the `alt` tag. Birdstar
     * currently writes `Bird detection: <Common name> (<Scientific name>)` (in
     * English); this strips the fixed prefix and the trailing parenthetical.
     * Null when the `alt` tag is missing or nothing is left after stripping —
     * including if a future Birdstar release rewords the alt text.
     */
    fun commonName(): String? {
        val alt = summary() ?: return null
        if (!alt.startsWith(ALT_PREFIX)) return null
        return alt
            .removePrefix(ALT_PREFIX)
            .substringBeforeLast(" (")
            .trim()
            .ifBlank { null }
    }

    companion object {
        const val KIND = 2473
        private const val ALT_PREFIX = "Bird detection:"
    }
}
