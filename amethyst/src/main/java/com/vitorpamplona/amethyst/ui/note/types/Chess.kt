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
package com.vitorpamplona.amethyst.ui.note.types

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vitorpamplona.amethyst.commons.chess.ChessChallenge
import com.vitorpamplona.amethyst.commons.chess.ChessGameViewer
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.ui.components.SensitivityWarning
import com.vitorpamplona.amethyst.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.ui.navigation.routes.Route
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.screen.loggedIn.chess.ChessViewModelFactory
import com.vitorpamplona.amethyst.ui.screen.loggedIn.chess.ChessViewModelNew
import com.vitorpamplona.quartz.nip64Chess.ChessGameEvent
import com.vitorpamplona.quartz.nip64Chess.LiveChessGameChallengeEvent
import com.vitorpamplona.quartz.nip64Chess.LiveChessGameEndEvent
import com.vitorpamplona.quartz.nip64Chess.Color as ChessColor

/**
 * Render NIP-64 Chess Game event (Kind 64)
 *
 * Displays interactive chess board with PGN game replay functionality.
 * Wraps content in sensitivity warning for user-controlled content filtering.
 *
 * @param note The note containing the ChessGameEvent
 * @param backgroundColor Mutable state for background color
 * @param accountViewModel Account view model for sensitivity checking
 * @param nav Navigation interface
 */
@Composable
fun RenderChessGame(
    note: Note,
    backgroundColor: MutableState<Color>,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val event = (note.event as? ChessGameEvent) ?: return

    SensitivityWarning(note = note, accountViewModel = accountViewModel) {
        ChessGameViewer(pgnContent = event.pgn())
    }
}

/**
 * Render Live Chess Challenge event (Kind 30064)
 *
 * Shows a challenge card with Accept/Decline actions for incoming challenges
 * or status for sent challenges
 */
@Composable
fun RenderLiveChessChallenge(
    note: Note,
    backgroundColor: MutableState<Color>,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val event = (note.event as? LiveChessGameChallengeEvent) ?: return
    val gameId = event.gameId() ?: return

    val chessViewModel: ChessViewModelNew =
        viewModel(
            key = "ChessViewModelNew-${accountViewModel.account.userProfile().pubkeyHex}",
            factory = ChessViewModelFactory(accountViewModel.account),
        )

    val isOpenChallenge = event.opponentPubkey() == null
    val isIncomingChallenge = event.opponentPubkey() == accountViewModel.account.userProfile().pubkeyHex

    val borderColor =
        when {
            isOpenChallenge -> Color(0xFF4CAF50)

            // Green for open
            isIncomingChallenge -> Color(0xFFFFA726)

            // Orange for incoming
            else -> MaterialTheme.colorScheme.outline // Gray for sent
        }

    val icon =
        when {
            isOpenChallenge -> "🔓"
            isIncomingChallenge -> "💌"
            else -> "⏳"
        }

    val title =
        when {
            isOpenChallenge -> "Open Challenge"
            isIncomingChallenge -> "Challenge from ${note.author?.toBestDisplayName()}"
            else -> "Challenge sent to ${event.opponentPubkey()?.take(8)}"
        }

    Card(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp)
                .border(2.dp, borderColor, MaterialTheme.shapes.medium),
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
            ),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = "$icon $title",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )

                Text(
                    text =
                        if (event.playerColor()?.name == "WHITE") "White" else "Black",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            event.timeControl()?.let {
                Text(
                    text = "Time: $it",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            // Action buttons
            if (isIncomingChallenge || isOpenChallenge) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    OutlinedButton(onClick = { /* TODO: Decline */ }) {
                        Text("Decline")
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Button(
                        onClick = {
                            // Accept challenge and navigate to game
                            val challengerPubkey = note.author?.pubkeyHex ?: return@Button

                            // Create ChessChallenge from Note data
                            val challenge =
                                ChessChallenge(
                                    eventId = note.idHex,
                                    gameId = gameId,
                                    challengerPubkey = challengerPubkey,
                                    challengerDisplayName = note.author?.toBestDisplayName(),
                                    challengerAvatarUrl = note.author?.profilePicture(),
                                    opponentPubkey = event.opponentPubkey(),
                                    challengerColor = event.playerColor() ?: ChessColor.WHITE,
                                    createdAt = event.createdAt,
                                )

                            chessViewModel.acceptChallenge(challenge)

                            // Navigate to game
                            nav.nav(Route.ChessGame(gameId))
                        },
                    ) {
                        Text("Accept")
                    }
                }
            }
        }
    }
}

/**
 * Render Live Chess Game End event (Kind 30067)
 *
 * Shows final game result with PGN viewer
 */
@Composable
fun RenderLiveChessGameEnd(
    note: Note,
    backgroundColor: MutableState<Color>,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val event = (note.event as? LiveChessGameEndEvent) ?: return

    SensitivityWarning(note = note, accountViewModel = accountViewModel) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            // Result header
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors =
                    CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    ),
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = "🏆 Game Ended",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = "Result: ${event.result()}",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    event.termination()?.let {
                        Text(
                            text = "By: ${it.replace("_", " ")}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                        )
                    }
                }
            }

            // Show PGN if available
            event.pgn().takeIf { it.isNotBlank() }?.let { pgn ->
                ChessGameViewer(pgnContent = pgn)
            }
        }
    }
}
