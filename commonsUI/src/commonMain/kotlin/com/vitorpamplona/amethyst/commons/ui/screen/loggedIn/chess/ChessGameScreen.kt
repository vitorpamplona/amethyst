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
package com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.chess

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.model.cache.LocalCache
import com.vitorpamplona.amethyst.commons.model.navigation.Route
import com.vitorpamplona.amethyst.commons.nip64Chess.ChessGameSnapshot
import com.vitorpamplona.amethyst.commons.nip64Chess.ChessGameViewModel
import com.vitorpamplona.amethyst.commons.nip64Chess.ui.LiveChessGameScreen
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.back
import com.vitorpamplona.amethyst.commons.resources.chess_color_black
import com.vitorpamplona.amethyst.commons.resources.chess_color_white
import com.vitorpamplona.amethyst.commons.resources.chess_game_default_name
import com.vitorpamplona.amethyst.commons.resources.chess_game_not_found
import com.vitorpamplona.amethyst.commons.resources.chess_loading_game
import com.vitorpamplona.amethyst.commons.ui.components.rememberViewModel
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.chess.datasource.ChessGameFilterAssemblerSubscription
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.quartz.nip64Chess.ChessGameNameGenerator

/**
 * The board of one game. The game streams in over its own REQ for as long as the screen is open,
 * and every move, the user's or the opponent's, redraws it from LocalCache.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChessGameScreen(
    gameId: String,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val viewModel: ChessGameViewModel =
        rememberViewModel(
            key = gameId + "ChessGameViewModel",
            factory = ChessGameViewModel.Factory(gameId, accountViewModel.account),
        )

    ChessGameFilterAssemblerSubscription(viewModel.startNote, accountViewModel)

    val game by viewModel.game.collectAsStateWithLifecycle()
    val startMetadata by viewModel.startNote
        .flow()
        .metadata.stateFlow
        .collectAsStateWithLifecycle()

    val defaultGameName = stringRes(Res.string.chess_game_default_name)
    val gameName = remember(gameId) { ChessGameNameGenerator.extractDisplayName(gameId) ?: defaultGameName }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(gameName) },
                navigationIcon = {
                    IconButton(onClick = { nav.popBack() }) {
                        Icon(
                            symbol = MaterialSymbols.AutoMirrored.ArrowBack,
                            contentDescription = stringRes(Res.string.back),
                        )
                    }
                },
            )
        },
    ) { paddingValues ->
        val current = game
        when {
            current != null -> {
                ChessBoardContent(current, viewModel, accountViewModel, nav, Modifier.padding(paddingValues))
            }

            // The start event is still on its way.
            startMetadata.note.event == null -> {
                Box(Modifier.fillMaxSize().padding(paddingValues), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator()
                        Spacer(Modifier.height(16.dp))
                        Text(stringRes(Res.string.chess_loading_game))
                    }
                }
            }

            // Loaded, but not a Jester game start.
            else -> {
                Column(
                    modifier = Modifier.fillMaxSize().padding(paddingValues).padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(
                        text = stringRes(Res.string.chess_game_not_found),
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
    }
}

@Composable
private fun ChessBoardContent(
    game: ChessGameSnapshot,
    viewModel: ChessGameViewModel,
    accountViewModel: AccountViewModel,
    nav: INav,
    modifier: Modifier,
) {
    val whiteHex = game.whitePubkey.orEmpty()
    val blackHex = game.blackPubkey.orEmpty()
    val white = remember(whiteHex) { whiteHex.ifEmpty { null }?.let { LocalCache.checkGetOrCreateUser(it) } }
    val black = remember(blackHex) { blackHex.ifEmpty { null }?.let { LocalCache.checkGetOrCreateUser(it) } }

    LiveChessGameScreen(
        game = game,
        onMoveMade = { from, to, _ -> accountViewModel.launchSigner { viewModel.move(from, to) } },
        onResign = { accountViewModel.launchSigner { viewModel.resign() } },
        modifier = modifier,
        whiteName = white?.toBestDisplayName() ?: stringRes(Res.string.chess_color_white),
        whiteHex = whiteHex,
        whiteAvatarUrl = white?.profilePicture(),
        blackName = black?.toBestDisplayName() ?: stringRes(Res.string.chess_color_black),
        blackHex = blackHex,
        blackAvatarUrl = black?.profilePicture(),
        onPlayerClick = { pubkeyHex -> if (pubkeyHex.isNotEmpty()) nav.nav(Route.Profile(pubkeyHex)) },
    )
}
