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
import com.vitorpamplona.quartz.nip01Core.core.BaseReplaceableEvent
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.mapValueTagged
import com.vitorpamplona.quartz.nip31Alts.alt
import com.vitorpamplona.quartz.nip50Search.IndexableFieldVisitor
import com.vitorpamplona.quartz.nip50Search.SearchableEvent

/**
 * Birdstar "Birdex" species collection (kind 12473).
 *
 * An app-specific **replaceable** kind published by the Birdstar app
 * (`birdstar.app`) — a birdwatching life-list. It is **not** defined by any NIP;
 * the schema below is derived from events seen in the wild. Being replaceable,
 * each author keeps a single, latest Birdex.
 *
 * The event carries no body and no images — its payload is the species list,
 * one entry per observed species, as alternating tags:
 *
 * - `n` — the species' scientific name (e.g. `Icterus galbula`).
 * - `i` — an external identity reference for the species, a Wikidata entity URL
 *         (NIP-73 style, e.g. `https://www.wikidata.org/entity/Q805774`).
 * - `alt` — a human-readable summary written by the publisher
 *           (e.g. `Birdex: 24 species`).
 *
 * Amethyst renders a minimal summary card from [species], linking each name to
 * its Wikidata entry; it does not resolve the references to images.
 */
@Immutable
class BirdexEvent(
    id: HexKey,
    pubKey: HexKey,
    createdAt: Long,
    tags: Array<Array<String>>,
    content: String,
    sig: HexKey,
) : BaseReplaceableEvent(id, pubKey, createdAt, KIND, tags, content, sig),
    SearchableEvent {
    override fun indexableContent() = (listOfNotNull(summary()) + speciesNames()).joinToString("\n")

    // The read path: the same fields indexableContent() joins, without the join.
    override fun forEachIndexableField(visitor: IndexableFieldVisitor) {
        if (!visitor.visit(summary())) return
        speciesNames().forEach { if (!visitor.visit(it)) return }
    }

    /** Scientific names of the collected species, in event order, from the `n` tags. */
    fun speciesNames() = tags.mapValueTagged("n") { it }

    /**
     * The collected species, in event order, each paired with the external
     * reference (Wikidata entity URL) the publisher filed it under.
     *
     * The two tag families are positional, not keyed: Birdstar writes an `i`
     * immediately before its `n`, so an `i` binds to the `n` **adjacent** to it
     * — the tag right after it, or, when the pair is written the other way
     * round, the tag right before. Adjacency is required, not merely proximity:
     * a lone `i` elsewhere in the event (a NIP-73 identity for the event
     * itself, say) is nobody's species reference, and pairing it with the next
     * name would point that link at the wrong page.
     *
     * A name with no adjacent `i` (or one whose `i` is not a web URL, which a UI
     * could not open) keeps a null reference and renders as plain text.
     */
    fun species(): List<BirdexSpecies> {
        val species = ArrayList<BirdexSpecies>(tags.size / 2)
        var pendingReference: String? = null
        var pendingIndex = -1
        var nameIndex = -1

        for (index in tags.indices) {
            val tag = tags[index]
            if (tag.size < 2) continue

            when (tag[0]) {
                "n" -> {
                    species.add(BirdexSpecies(tag[1], if (pendingIndex == index - 1) pendingReference else null))
                    pendingReference = null
                    pendingIndex = -1
                    nameIndex = index
                }
                "i" -> {
                    val reference = tag[1].asWebReference()
                    val last = species.lastOrNull()
                    if (nameIndex == index - 1 && last != null && last.reference == null) {
                        species[species.lastIndex] = BirdexSpecies(last.name, reference)
                    } else {
                        pendingReference = reference
                        pendingIndex = index
                    }
                    nameIndex = -1
                }
            }
        }

        return species
    }

    /** Number of collected species (one `n` tag per species). */
    fun speciesCount() = tags.count { it.size > 1 && it[0] == "n" }

    /** Publisher-provided human-readable summary, from the NIP-31 `alt` tag (may be null). */
    fun summary() = tags.alt()

    companion object {
        const val KIND = 12473
    }
}

/** One entry of a [BirdexEvent] life list: a scientific name and where it points. */
@Immutable
class BirdexSpecies(
    /** The species' scientific name, from an `n` tag (e.g. `Icterus galbula`). */
    val name: String,
    /** The species' Wikidata entity URL, from the adjacent `i` tag, or null. */
    val reference: String?,
)

/**
 * Keeps only references a UI can open as a link. Birdstar's `i` tags are
 * Wikidata entity URLs, but the tag is free-form, so anything that is not
 * http(s) is dropped here rather than at every call site.
 */
internal fun String.asWebReference() = takeIf { it.startsWith("https://") || it.startsWith("http://") }
