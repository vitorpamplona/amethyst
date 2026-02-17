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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.home

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vitorpamplona.amethyst.commons.chess.NewChessGameDialog
import com.vitorpamplona.amethyst.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.screen.loggedIn.chess.ChessViewModelFactory
import com.vitorpamplona.amethyst.ui.screen.loggedIn.chess.ChessViewModelNew

/**
 * Floating action button for creating new chess game challenges
 */
@Composable
fun NewChessGameButton(
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    var showDialog by remember { mutableStateOf(false) }

    val chessViewModel: ChessViewModelNew =
        viewModel(
            key = "ChessViewModelNew-${accountViewModel.account.userProfile().pubkeyHex}",
            factory = ChessViewModelFactory(accountViewModel.account),
        )

    FloatingActionButton(
        onClick = { showDialog = true },
    ) {
        Icon(
            imageVector = Icons.Default.Add,
            contentDescription = "New Chess Game",
        )
    }

    if (showDialog) {
        NewChessGameDialog(
            onDismiss = { showDialog = false },
            onCreateGame = { opponentPubkey, color ->
                chessViewModel.createChallenge(
                    opponentPubkey = opponentPubkey,
                    playerColor = color,
                )
                showDialog = false
            },
        )
    }
}
