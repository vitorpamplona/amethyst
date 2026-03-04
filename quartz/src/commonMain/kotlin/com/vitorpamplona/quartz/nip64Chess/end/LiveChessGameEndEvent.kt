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
package com.vitorpamplona.quartz.nip64Chess.end

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.TagArrayBuilder
import com.vitorpamplona.quartz.nip01Core.signers.eventTemplate
import com.vitorpamplona.quartz.nip01Core.tags.dTag.dTag
import com.vitorpamplona.quartz.nip31Alts.alt
import com.vitorpamplona.quartz.nip64Chess.GameResult
import com.vitorpamplona.quartz.nip64Chess.GameTermination
import com.vitorpamplona.quartz.nip64Chess.baseEvent.BaseChessEvent
import com.vitorpamplona.quartz.nip64Chess.baseEvent.opponent
import com.vitorpamplona.quartz.nip64Chess.baseEvent.tags.OpponentTag
import com.vitorpamplona.quartz.utils.TimeUtils

/**
 * Live Chess Game End Event (Kind 30067)
 *
 * Game result and termination
 *
 * Tags:
 * - d: game_id
 * - result: "1-0"|"0-1"|"1/2-1/2"
 * - termination: reason for game end
 * - winner: pubkey of winner (if applicable)
 * - p: opponent pubkey
 *
 * Content: Optional PGN of complete game
 */
@Immutable
class LiveChessGameEndEvent(
    id: HexKey,
    pubKey: HexKey,
    createdAt: Long,
    tags: Array<Array<String>>,
    content: String,
    sig: HexKey,
) : BaseChessEvent(id, pubKey, createdAt, KIND, tags, content, sig) {
    fun result() = tags.result()

    fun termination() = tags.termination()

    fun winnerPubkey() = tags.winnerPubkey()

    fun pgn(): String = content

    companion object {
        const val KIND = 30067
        const val ALT_DESCRIPTION = "Chess game ended"

        fun build(
            gameId: String,
            result: GameResult,
            termination: GameTermination,
            winnerPubkey: String? = null,
            opponent: OpponentTag,
            pgn: String = "",
            createdAt: Long = TimeUtils.now(),
            initializer: TagArrayBuilder<LiveChessGameEndEvent>.() -> Unit = {},
        ) = eventTemplate(KIND, pgn, createdAt) {
            dTag(gameId)
            result(result)
            termination(termination)
            winnerPubkey?.let { winner(it) }
            opponent(opponent)
            alt("$ALT_DESCRIPTION: ${result.notation}")
            initializer()
        }
    }
}
