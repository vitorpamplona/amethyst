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
import com.vitorpamplona.quartz.nip01Core.core.BaseAddressableEvent
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.TagArrayBuilder
import com.vitorpamplona.quartz.nip01Core.signers.eventTemplate
import com.vitorpamplona.quartz.nip31Alts.alt
import com.vitorpamplona.quartz.utils.TimeUtils

/**
 * Live Chess Game Challenge Event (Kind 30064)
 *
 * Challenge another player to a chess game or create an open challenge.
 * This is a parameterized replaceable event.
 *
 * Tags:
 * - d: game_id (unique identifier for this game)
 * - p: opponent pubkey (optional, for direct challenges)
 * - player_color: "white" or "black"
 * - time_control: optional time control (e.g., "10+0", "5+3")
 */
@Immutable
class LiveChessGameChallengeEvent(
    id: HexKey,
    pubKey: HexKey,
    createdAt: Long,
    tags: Array<Array<String>>,
    content: String,
    sig: HexKey,
) : BaseAddressableEvent(id, pubKey, createdAt, KIND, tags, content, sig) {
    companion object {
        const val KIND = 30064

        fun build(
            gameId: String,
            playerColor: Color,
            opponentPubkey: String? = null,
            timeControl: String? = null,
            createdAt: Long = TimeUtils.now(),
            initializer: TagArrayBuilder<LiveChessGameChallengeEvent>.() -> Unit = {},
        ) = eventTemplate(KIND, "", createdAt) {
            add(arrayOf("d", gameId))
            add(arrayOf("player_color", if (playerColor == Color.WHITE) "white" else "black"))
            opponentPubkey?.let { add(arrayOf("p", it)) }
            timeControl?.let { add(arrayOf("time_control", it)) }
            alt("Chess game challenge")
            initializer()
        }
    }

    fun gameId(): String? = tags.firstOrNull { it.size >= 2 && it[0] == "d" }?.get(1)

    fun playerColor(): Color? =
        tags
            .firstOrNull { it.size >= 2 && it[0] == "player_color" }
            ?.get(1)
            ?.let { if (it == "white") Color.WHITE else Color.BLACK }

    fun opponentPubkey(): String? = tags.firstOrNull { it.size >= 2 && it[0] == "p" }?.get(1)

    fun timeControl(): String? = tags.firstOrNull { it.size >= 2 && it[0] == "time_control" }?.get(1)
}

/**
 * Live Chess Game Accept Event (Kind 30065)
 *
 * Accept a chess game challenge
 *
 * Tags:
 * - d: game_id (same as challenge)
 * - e: challenge event ID
 * - p: challenger pubkey
 */
@Immutable
class LiveChessGameAcceptEvent(
    id: HexKey,
    pubKey: HexKey,
    createdAt: Long,
    tags: Array<Array<String>>,
    content: String,
    sig: HexKey,
) : BaseAddressableEvent(id, pubKey, createdAt, KIND, tags, content, sig) {
    companion object {
        const val KIND = 30065

        fun build(
            gameId: String,
            challengeEventId: String,
            challengerPubkey: String,
            createdAt: Long = TimeUtils.now(),
            initializer: TagArrayBuilder<LiveChessGameAcceptEvent>.() -> Unit = {},
        ) = eventTemplate(KIND, "", createdAt) {
            add(arrayOf("d", gameId))
            add(arrayOf("e", challengeEventId))
            add(arrayOf("p", challengerPubkey))
            alt("Chess game acceptance")
            initializer()
        }
    }

    fun gameId(): String? = tags.firstOrNull { it.size >= 2 && it[0] == "d" }?.get(1)

    fun challengeEventId(): String? = tags.firstOrNull { it.size >= 2 && it[0] == "e" }?.get(1)

    fun challengerPubkey(): String? = tags.firstOrNull { it.size >= 2 && it[0] == "p" }?.get(1)
}

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
) : BaseAddressableEvent(id, pubKey, createdAt, KIND, tags, content, sig) {
    companion object {
        const val KIND = 30066

        fun build(
            gameId: String,
            moveNumber: Int,
            san: String,
            fen: String,
            opponentPubkey: String,
            comment: String = "",
            createdAt: Long = TimeUtils.now(),
            initializer: TagArrayBuilder<LiveChessMoveEvent>.() -> Unit = {},
        ) = eventTemplate(KIND, comment, createdAt) {
            add(arrayOf("d", "$gameId-$moveNumber"))
            add(arrayOf("game_id", gameId))
            add(arrayOf("move_number", moveNumber.toString()))
            add(arrayOf("san", san))
            add(arrayOf("fen", fen))
            add(arrayOf("p", opponentPubkey))
            alt("Chess move: $san")
            initializer()
        }
    }

    fun gameId(): String? = tags.firstOrNull { it.size >= 2 && it[0] == "game_id" }?.get(1)

    fun moveNumber(): Int? =
        tags
            .firstOrNull { it.size >= 2 && it[0] == "move_number" }
            ?.get(1)
            ?.toIntOrNull()

    fun san(): String? = tags.firstOrNull { it.size >= 2 && it[0] == "san" }?.get(1)

    fun fen(): String? = tags.firstOrNull { it.size >= 2 && it[0] == "fen" }?.get(1)

    fun opponentPubkey(): String? = tags.firstOrNull { it.size >= 2 && it[0] == "p" }?.get(1)

    fun comment(): String = content
}

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
) : BaseAddressableEvent(id, pubKey, createdAt, KIND, tags, content, sig) {
    companion object {
        const val KIND = 30067

        fun build(
            gameId: String,
            result: GameResult,
            termination: GameTermination,
            winnerPubkey: String? = null,
            opponentPubkey: String,
            pgn: String = "",
            createdAt: Long = TimeUtils.now(),
            initializer: TagArrayBuilder<LiveChessGameEndEvent>.() -> Unit = {},
        ) = eventTemplate(KIND, pgn, createdAt) {
            add(arrayOf("d", gameId))
            add(arrayOf("result", result.notation))
            add(arrayOf("termination", termination.name.lowercase()))
            winnerPubkey?.let { add(arrayOf("winner", it)) }
            add(arrayOf("p", opponentPubkey))
            alt("Chess game ended: ${result.notation}")
            initializer()
        }
    }

    fun gameId(): String? = tags.firstOrNull { it.size >= 2 && it[0] == "d" }?.get(1)

    fun result(): String? = tags.firstOrNull { it.size >= 2 && it[0] == "result" }?.get(1)

    fun termination(): String? = tags.firstOrNull { it.size >= 2 && it[0] == "termination" }?.get(1)

    fun winnerPubkey(): String? = tags.firstOrNull { it.size >= 2 && it[0] == "winner" }?.get(1)

    fun opponentPubkey(): String? = tags.firstOrNull { it.size >= 2 && it[0] == "p" }?.get(1)

    fun pgn(): String = content
}

/**
 * Live Chess Draw Offer Event (Kind 30068)
 *
 * Offer a draw to opponent. Opponent can accept by sending a game end event
 * with DRAW_AGREEMENT termination, or decline/ignore by making their next move.
 *
 * Tags:
 * - d: game_id
 * - p: opponent pubkey
 *
 * Content: Optional message
 */
@Immutable
class LiveChessDrawOfferEvent(
    id: HexKey,
    pubKey: HexKey,
    createdAt: Long,
    tags: Array<Array<String>>,
    content: String,
    sig: HexKey,
) : BaseAddressableEvent(id, pubKey, createdAt, KIND, tags, content, sig) {
    companion object {
        const val KIND = 30068

        fun build(
            gameId: String,
            opponentPubkey: String,
            message: String = "",
            createdAt: Long = TimeUtils.now(),
            initializer: TagArrayBuilder<LiveChessDrawOfferEvent>.() -> Unit = {},
        ) = eventTemplate(KIND, message, createdAt) {
            add(arrayOf("d", gameId))
            add(arrayOf("p", opponentPubkey))
            alt("Chess draw offer")
            initializer()
        }
    }

    fun gameId(): String? = tags.firstOrNull { it.size >= 2 && it[0] == "d" }?.get(1)

    fun opponentPubkey(): String? = tags.firstOrNull { it.size >= 2 && it[0] == "p" }?.get(1)

    fun message(): String = content
}
