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
package com.vitorpamplona.quartz.nip64Chess

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.TagArrayBuilder
import com.vitorpamplona.quartz.nip01Core.signers.eventTemplate
import com.vitorpamplona.quartz.nip31Alts.alt
import com.vitorpamplona.quartz.utils.TimeUtils

/**
 * NIP-64: Chess Game Event
 *
 * Kind 64 events contain chess games in PGN (Portable Game Notation) format.
 * Per NIP-64 specification:
 * - Content must be valid PGN format (PGN-database)
 * - Clients should accept "import format" (human-created, flexible)
 * - Clients should publish in "export format" (machine-generated, strict)
 * - Moves must comply with chess rules
 * - Clients should display content as rendered chessboard
 *
 * @see <a href="https://github.com/nostr-protocol/nips/blob/master/64.md">NIP-64</a>
 */
@Immutable
class ChessGameEvent(
    id: HexKey,
    pubKey: HexKey,
    createdAt: Long,
    tags: Array<Array<String>>,
    content: String, // PGN database format
    sig: HexKey,
) : Event(id, pubKey, createdAt, KIND, tags, content, sig) {
    companion object {
        const val KIND = 64
        const val ALT_DESCRIPTION = "Chess Game"

        /**
         * Create a chess game event from PGN content
         * Per NIP-64, clients should publish in strict "export format"
         *
         * @param pgnContent Valid PGN format string
         * @param altDescription Alt text for non-supporting clients (NIP-31)
         * @param createdAt Event timestamp
         * @param initializer Additional tag builder operations
         */
        fun build(
            pgnContent: String,
            altDescription: String = ALT_DESCRIPTION,
            createdAt: Long = TimeUtils.now(),
            initializer: TagArrayBuilder<ChessGameEvent>.() -> Unit = {},
        ) = eventTemplate(KIND, pgnContent, createdAt) {
            alt(altDescription)
            initializer()
        }
    }

    /**
     * Get PGN content from event
     */
    fun pgn(): String = content

    /**
     * Get alt text for non-supporting clients (NIP-31)
     */
    fun altText(): String? = tags.firstOrNull { it.size >= 2 && it[0] == "alt" }?.get(1)
}
