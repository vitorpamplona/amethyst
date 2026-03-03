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
package com.vitorpamplona.amethyst.model.nip64Chess

import com.vitorpamplona.quartz.nip01Core.signers.NostrSigner
import com.vitorpamplona.quartz.nip64Chess.Color
import com.vitorpamplona.quartz.nip64Chess.GameResult
import com.vitorpamplona.quartz.nip64Chess.GameTermination
import com.vitorpamplona.quartz.nip64Chess.LiveChessGameAcceptEvent
import com.vitorpamplona.quartz.nip64Chess.LiveChessGameChallengeEvent
import com.vitorpamplona.quartz.nip64Chess.LiveChessGameEndEvent
import com.vitorpamplona.quartz.nip64Chess.LiveChessMoveEvent

/**
 * Action class for creating and signing live chess events
 */
class ChessAction {
    companion object {
        /**
         * Create a new chess game challenge
         *
         * @param gameId Unique identifier for the game
         * @param playerColor Color the challenger wants to play
         * @param opponentPubkey Optional opponent pubkey (null = open challenge)
         * @param timeControl Optional time control (e.g., "10+0")
         * @param signer Nostr signer
         */
        suspend fun createChallenge(
            gameId: String,
            playerColor: Color,
            opponentPubkey: String? = null,
            timeControl: String? = null,
            signer: NostrSigner,
        ): LiveChessGameChallengeEvent =
            signer.sign(
                LiveChessGameChallengeEvent.build(
                    gameId = gameId,
                    playerColor = playerColor,
                    opponentPubkey = opponentPubkey,
                    timeControl = timeControl,
                ),
            )

        /**
         * Accept a chess game challenge
         *
         * @param gameId Game identifier from the challenge
         * @param challengeEventId Event ID of the challenge
         * @param challengerPubkey Pubkey of the challenger
         * @param signer Nostr signer
         */
        suspend fun acceptChallenge(
            gameId: String,
            challengeEventId: String,
            challengerPubkey: String,
            signer: NostrSigner,
        ): LiveChessGameAcceptEvent =
            signer.sign(
                LiveChessGameAcceptEvent.build(
                    gameId = gameId,
                    challengeEventId = challengeEventId,
                    challengerPubkey = challengerPubkey,
                ),
            )

        /**
         * Publish a move in a live chess game
         *
         * @param gameId Game identifier
         * @param moveNumber Move number (1-based)
         * @param san Move in Standard Algebraic Notation
         * @param fen Resulting position in FEN notation
         * @param opponentPubkey Opponent's pubkey
         * @param comment Optional move comment
         * @param signer Nostr signer
         */
        suspend fun publishMove(
            gameId: String,
            moveNumber: Int,
            san: String,
            fen: String,
            opponentPubkey: String,
            comment: String = "",
            signer: NostrSigner,
        ): LiveChessMoveEvent =
            signer.sign(
                LiveChessMoveEvent.build(
                    gameId = gameId,
                    moveNumber = moveNumber,
                    san = san,
                    fen = fen,
                    opponentPubkey = opponentPubkey,
                    comment = comment,
                ),
            )

        /**
         * End a chess game and publish result
         *
         * @param gameId Game identifier
         * @param result Game result (1-0, 0-1, or 1/2-1/2)
         * @param termination How the game ended
         * @param winnerPubkey Pubkey of winner (if applicable)
         * @param opponentPubkey Opponent's pubkey
         * @param pgn Optional PGN of complete game
         * @param signer Nostr signer
         */
        suspend fun endGame(
            gameId: String,
            result: GameResult,
            termination: GameTermination,
            winnerPubkey: String? = null,
            opponentPubkey: String,
            pgn: String = "",
            signer: NostrSigner,
        ): LiveChessGameEndEvent =
            signer.sign(
                LiveChessGameEndEvent.build(
                    gameId = gameId,
                    result = result,
                    termination = termination,
                    winnerPubkey = winnerPubkey,
                    opponentPubkey = opponentPubkey,
                    pgn = pgn,
                ),
            )
    }
}
