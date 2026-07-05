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
package com.vitorpamplona.quartz.experimental.ps1saves

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.nip01Core.core.BaseAddressableEvent
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.firstTagValue
import com.vitorpamplona.quartz.nip31Alts.alt
import com.vitorpamplona.quartz.nip50Search.SearchableEvent

/**
 * PlayStation 1 memory-card save block (kind 38192).
 *
 * An experimental **addressable** kind with a single known publisher — a
 * "PS1 memory cards over nostr" project — and **no** NIP; the schema below is
 * derived from events seen in the wild. Each event carries one raw 8 KiB
 * memory-card block, hex-encoded in the content (the kind number itself echoes
 * the 8192-byte block size). Being addressable, re-saving a block replaces the
 * previous event for the same `(pubkey, kind, d)` — memory-card semantics.
 *
 * Tags seen in the wild:
 *
 * - `d` — unique block address, `<memory card id>-<block number>`.
 * - `m` — the memory card this block belongs to.
 * - `block` — block number within the card, as a decimal string.
 * - `x` — SHA-256 of the block data.
 * - `state` — the block's position in a multi-block save chain (e.g. `first`).
 * - `filename` — the PS1 save filename, i.e. the game's product code
 *   (e.g. `BASCUS-00001SOFTCARD`).
 * - `region` — the game region (e.g. `America`).
 * - `title` — the save's human-readable name.
 * - `alt` — NIP-31 fallback, e.g. `PS1 save '<title>' (<filename>)`.
 */
@Immutable
class Ps1SaveEvent(
    id: HexKey,
    pubKey: HexKey,
    createdAt: Long,
    tags: Array<Array<String>>,
    content: String,
    sig: HexKey,
) : BaseAddressableEvent(id, pubKey, createdAt, KIND, tags, content, sig),
    SearchableEvent {
    override fun indexableContent() = listOfNotNull(summary(), saveTitle(), filename()).joinToString("\n")

    /** Human-readable save name, from the `title` tag (may be null). */
    fun saveTitle() = tags.firstTagValue("title")

    /** PS1 save filename / game product code, from the `filename` tag (may be null). */
    fun filename() = tags.firstTagValue("filename")

    /** Game region, from the `region` tag (may be null). */
    fun region() = tags.firstTagValue("region")

    /** Block number within the card, from the `block` tag (null when missing or not a number). */
    fun blockNumber() = tags.firstTagValue("block")?.toIntOrNull()

    /** Publisher-provided human-readable summary, from the NIP-31 `alt` tag (may be null). */
    fun summary() = tags.alt()

    /**
     * The BIOS-style animated save icon decoded from this block's title frame.
     * Null unless this is a save's first block with a valid icon (see [Ps1SaveIcon]).
     */
    fun icon() = Ps1SaveIcon.parse(content)

    /**
     * True when the block holds no save data: erased flash sectors (all `ff`)
     * or zero-filled blocks, whatever the publisher's hex casing. UIs show
     * these as empty slots instead of a hex dump.
     */
    fun isBlankBlock() = content.isNotEmpty() && content.all { it.equals(content[0], ignoreCase = true) }

    companion object {
        const val KIND = 38192
    }
}
