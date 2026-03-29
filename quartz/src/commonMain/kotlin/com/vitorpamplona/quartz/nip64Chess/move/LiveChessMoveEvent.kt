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
package com.vitorpamplona.quartz.nip64Chess.move

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.TagArrayBuilder
import com.vitorpamplona.quartz.nip01Core.signers.eventTemplate
import com.vitorpamplona.quartz.nip01Core.tags.dTag.dTag
import com.vitorpamplona.quartz.nip31Alts.alt
import com.vitorpamplona.quartz.nip64Chess.baseEvent.BaseChessEvent
import com.vitorpamplona.quartz.nip64Chess.baseEvent.opponent
import com.vitorpamplona.quartz.nip64Chess.baseEvent.tags.OpponentTag
import com.vitorpamplona.quartz.utils.TimeUtils

/**
 * Live Chess Move Event (Kind 30066)
 *
 * Individual move in a live chess game
 *
 * Tags:
 * - d: game_id
 * - move_number: move number (1-based)
 * - san: move in Standard Algebraic Notation
 * - fen: resulting position in FEN notation
 * - p: opponent pubkey
 *
 * Content: Optional move comment
 */
@Immutable
class LiveChessMoveEvent(
    id: HexKey,
    pubKey: HexKey,
    createdAt: Long,
    tags: Array<Array<String>>,
    content: String,
    sig: HexKey,
) : BaseChessEvent(id, pubKey, createdAt, KIND, tags, content, sig) {
    fun gameId() = tags.gameId()

    fun moveNumber() = tags.moveNumber()

    fun san() = tags.san()

    fun fen() = tags.fen()

    fun comment(): String = content

    companion object {
        const val KIND = 30066
        const val ALT_DESCRIPTION = "Chess move"

        fun build(
            gameId: String,
            moveNumber: Int,
            san: String,
            fen: String,
            opponent: OpponentTag,
            comment: String = "",
            createdAt: Long = TimeUtils.now(),
            initializer: TagArrayBuilder<LiveChessMoveEvent>.() -> Unit = {},
        ) = eventTemplate(KIND, comment, createdAt) {
            dTag("$gameId-$moveNumber")
            gameId(gameId)
            moveNumber(moveNumber)
            san(san)
            fen(fen)
            opponent(opponent)
            alt("$ALT_DESCRIPTION: $san")
            initializer()
        }
    }
}
