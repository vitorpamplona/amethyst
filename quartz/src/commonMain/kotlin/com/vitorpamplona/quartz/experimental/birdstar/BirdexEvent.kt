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
import com.vitorpamplona.quartz.nip01Core.core.firstTagValue
import com.vitorpamplona.quartz.nip01Core.core.mapValueTagged

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
 * Amethyst renders a minimal, fixed-size summary card from [speciesNames] and
 * [speciesCount]; it does not resolve the Wikidata references to images.
 */
@Immutable
class BirdexEvent(
    id: HexKey,
    pubKey: HexKey,
    createdAt: Long,
    tags: Array<Array<String>>,
    content: String,
    sig: HexKey,
) : BaseReplaceableEvent(id, pubKey, createdAt, KIND, tags, content, sig) {
    /** Scientific names of the collected species, in event order, from the `n` tags. */
    fun speciesNames() = tags.mapValueTagged("n") { it }

    /** Number of collected species (one `n` tag per species). */
    fun speciesCount() = tags.count { it.size > 1 && it[0] == "n" }

    /** Publisher-provided human-readable summary, from the `alt` tag (may be null). */
    fun summary() = tags.firstTagValue("alt")

    companion object {
        const val KIND = 12473
        const val ALT_DESCRIPTION = "A Birdex species collection"
    }
}
