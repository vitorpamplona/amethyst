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
package com.vitorpamplona.amethyst.commons.nip64Chess.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.model.navigation.Route
import com.vitorpamplona.amethyst.commons.nip64Chess.ChessGameSnapshot
import com.vitorpamplona.amethyst.commons.nip64Chess.GameStatus
import com.vitorpamplona.amethyst.commons.nip64Chess.jesterGameEvents
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.chess_color_black
import com.vitorpamplona.amethyst.commons.resources.chess_color_white
import com.vitorpamplona.amethyst.commons.resources.chess_game_default_name
import com.vitorpamplona.amethyst.commons.resources.chess_join_as_black
import com.vitorpamplona.amethyst.commons.resources.chess_join_as_white
import com.vitorpamplona.amethyst.commons.resources.chess_live_open_challenge
import com.vitorpamplona.amethyst.commons.resources.chess_live_opponents_turn
import com.vitorpamplona.amethyst.commons.resources.chess_moves_count
import com.vitorpamplona.amethyst.commons.resources.chess_open_seat
import com.vitorpamplona.amethyst.commons.resources.chess_result_black_wins
import com.vitorpamplona.amethyst.commons.resources.chess_result_draw
import com.vitorpamplona.amethyst.commons.resources.chess_result_white_wins
import com.vitorpamplona.amethyst.commons.resources.chess_result_you_lost
import com.vitorpamplona.amethyst.commons.resources.chess_result_you_won
import com.vitorpamplona.amethyst.commons.resources.chess_turn_black
import com.vitorpamplona.amethyst.commons.resources.chess_turn_white
import com.vitorpamplona.amethyst.commons.resources.chess_waiting_for_opponent_accept
import com.vitorpamplona.amethyst.commons.resources.chess_your_turn
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.note.ClickableUserPicture
import com.vitorpamplona.amethyst.commons.ui.note.LoadUser
import com.vitorpamplona.amethyst.commons.ui.note.UsernameDisplay
import com.vitorpamplona.amethyst.commons.ui.note.types.KindCard
import com.vitorpamplona.amethyst.commons.ui.note.types.KindTone
import com.vitorpamplona.amethyst.commons.ui.pluralStringRes
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip64Chess.ChessPiece
import com.vitorpamplona.quartz.nip64Chess.Color
import com.vitorpamplona.quartz.nip64Chess.GameResult
import com.vitorpamplona.quartz.nip64Chess.PieceType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The game a Jester start note opened, as LocalCache has it now: rebuilt off the main thread
 * each time a move is linked to the note. Null until the start event is loaded.
 */
@Composable
fun observeChessGame(
    startNote: Note,
    viewer: HexKey,
): State<ChessGameSnapshot?> {
    val replies by startNote
        .flow()
        .replies.stateFlow
        .collectAsStateWithLifecycle()
    val metadata by startNote
        .flow()
        .metadata.stateFlow
        .collectAsStateWithLifecycle()
    return produceState<ChessGameSnapshot?>(null, startNote, replies, metadata) {
        value = withContext(Dispatchers.Default) { startNote.jesterGameEvents()?.let { ChessGameSnapshot.build(it, viewer) } }
    }
}

/**
 * A chess game in a feed: the position with its last move, who plays which colour, and whose turn
 * it is or how it ended. Opening it (the note's own click, or Join on a seat the user can take)
 * goes to the board.
 */
@Composable
fun ChessGameCard(
    startNote: Note,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val game by observeChessGame(startNote, accountViewModel.account.userProfile().pubkeyHex)
    val snapshot = game ?: return

    val (status, tone) = chessStatus(snapshot)

    KindCard(
        icon = MaterialSymbols.ChessKnight,
        title = stringRes(if (snapshot.isOpenSeat) Res.string.chess_live_open_challenge else Res.string.chess_game_default_name),
        subtitle = if (snapshot.moveCount > 0) pluralStringRes(Res.plurals.chess_moves_count, snapshot.moveCount, snapshot.moveCount) else null,
        status = status,
        statusTone = tone,
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ChessBoard(
                position = snapshot.state.currentPosition,
                boardSize = 128.dp,
                showCoordinates = false,
                flipped = snapshot.myColor == Color.BLACK,
                lastMove = snapshot.replay.last.lastMove,
            )
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                CardPlayer(Color.WHITE, snapshot.whitePubkey, accountViewModel, nav)
                CardPlayer(Color.BLACK, snapshot.blackPubkey, accountViewModel, nav)
                if (snapshot.isJoinable && accountViewModel.account.isWriteable()) {
                    Button(onClick = { nav.nav(Route.ChessGame(snapshot.gameId)) }) {
                        Text(stringRes(if (snapshot.myColor == Color.WHITE) Res.string.chess_join_as_white else Res.string.chess_join_as_black))
                    }
                }
            }
        }
    }
}

/** One side of the game: its king, then the player, or the open seat. */
@Composable
private fun CardPlayer(
    color: Color,
    pubkey: String?,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Image(
            imageVector = ChessPiece(PieceType.KING, color).toImageVector(),
            contentDescription = stringRes(if (color == Color.WHITE) Res.string.chess_color_white else Res.string.chess_color_black),
            modifier = Modifier.size(20.dp),
        )
        if (pubkey == null) {
            Text(
                text = stringRes(Res.string.chess_open_seat),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            LoadUser(pubkey) { user ->
                if (user != null) {
                    ClickableUserPicture(baseUser = user, size = 24.dp, accountViewModel = accountViewModel, onClick = { nav.nav(Route.Profile(pubkey)) })
                    UsernameDisplay(baseUser = user, accountViewModel = accountViewModel)
                }
            }
        }
    }
}

@Composable
private fun chessStatus(game: ChessGameSnapshot): Pair<String, KindTone> {
    val status = game.state.gameStatus
    return when {
        status is GameStatus.Finished -> resultLabel(status.result, game.myColor.takeIf { !game.isOpenSeat })
        game.isWaitingForOpponent -> stringRes(Res.string.chess_waiting_for_opponent_accept) to KindTone.Warning
        game.isJoinable -> stringRes(Res.string.chess_open_seat) to KindTone.Primary
        game.isMyTurn -> stringRes(Res.string.chess_your_turn) to KindTone.Primary
        game.myColor != null && !game.isOpenSeat -> stringRes(Res.string.chess_live_opponents_turn) to KindTone.Neutral
        game.state.currentPosition.activeColor == Color.WHITE -> stringRes(Res.string.chess_turn_white) to KindTone.Neutral
        else -> stringRes(Res.string.chess_turn_black) to KindTone.Neutral
    }
}

@Composable
private fun resultLabel(
    result: GameResult,
    myColor: Color?,
): Pair<String, KindTone> {
    val winner =
        when (result) {
            GameResult.WHITE_WINS -> Color.WHITE
            GameResult.BLACK_WINS -> Color.BLACK
            else -> null
        }
    return when {
        winner == null -> stringRes(Res.string.chess_result_draw) to KindTone.Neutral
        myColor == null -> stringRes(if (winner == Color.WHITE) Res.string.chess_result_white_wins else Res.string.chess_result_black_wins) to KindTone.Neutral
        myColor == winner -> stringRes(Res.string.chess_result_you_won) to KindTone.Positive
        else -> stringRes(Res.string.chess_result_you_lost) to KindTone.Negative
    }
}
