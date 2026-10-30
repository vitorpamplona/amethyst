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
package com.vitorpamplona.amethyst.commons.nip64Chess

import com.vitorpamplona.quartz.nip64Chess.ChessGameEnd
import com.vitorpamplona.quartz.nip64Chess.ChessMoveEvent
import com.vitorpamplona.quartz.nip64Chess.Color
import com.vitorpamplona.quartz.nip64Chess.jester.JesterEvent
import com.vitorpamplona.quartz.nip64Chess.jester.JesterGameEvents
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The chess model lives in the account's shared store, so it outlives the chess screens: it must
 * poll relays only while a screen holds it, or it keeps hitting relays after the user left chess.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ChessViewModelPollingTest {
    private class CountingFetcher : ChessRelayFetcher {
        var challengeFetches = 0

        override suspend fun fetchGameEvents(startEventId: String) = JesterGameEvents(startEvent = null, moves = emptyList())

        override suspend fun fetchChallenges(onProgress: ((RelayFetchProgress) -> Unit)?): List<JesterEvent> {
            challengeFetches++
            return emptyList()
        }

        override suspend fun fetchRecentGames(): List<RelayGameSummary> = emptyList()

        override suspend fun fetchUserGameIds(onProgress: ((RelayFetchProgress) -> Unit)?): Set<String> = emptySet()

        override fun getRelayUrls(): List<String> = emptyList()
    }

    private object NoPublisher : ChessEventPublisher {
        override suspend fun publishStart(
            playerColor: Color,
            opponentPubkey: String?,
        ): String? = null

        override suspend fun publishMove(move: ChessMoveEvent): String? = null

        override suspend fun publishGameEnd(gameEnd: ChessGameEnd) = false

        override fun getWriteRelayCount() = 0
    }

    private object NoMetadata : IUserMetadataProvider {
        override fun getDisplayName(pubkey: String) = pubkey

        override fun getPictureUrl(pubkey: String): String? = null
    }

    private val config = ChessPollingConfig(activeGamePollInterval = 1_000L, challengePollInterval = 10_000L)

    private fun TestScope.newViewModel(fetcher: ChessRelayFetcher) =
        ChessViewModel(
            userPubkey = "a".repeat(64),
            publisher = NoPublisher,
            fetcher = fetcher,
            metadataProvider = NoMetadata,
            pollingConfig = config,
            dismissedStorage = null,
            scope = backgroundScope,
        )

    @Test
    fun doesNotPollUntilAScreenHoldsIt() =
        runTest {
            val fetcher = CountingFetcher()
            newViewModel(fetcher)

            advanceTimeBy(60_000L)
            runCurrent()

            assertEquals(0, fetcher.challengeFetches)
        }

    @Test
    fun keepsPollingWhileAnyScreenHoldsItAndStopsWhenTheLastLeaves() =
        runTest {
            val fetcher = CountingFetcher()
            val viewModel = newViewModel(fetcher)

            viewModel.holdPolling() // lobby
            runCurrent()
            assertEquals(1, fetcher.challengeFetches) // the initial fetch

            viewModel.holdPolling() // board opens over the lobby
            viewModel.releasePolling() // then the lobby leaves
            advanceTimeBy(config.challengePollInterval + 1)
            assertEquals(2, fetcher.challengeFetches)

            viewModel.releasePolling() // the board leaves: no chess screen is left
            advanceTimeBy(10 * config.challengePollInterval)
            assertEquals(2, fetcher.challengeFetches)
        }

    @Test
    fun anExtraReleaseDoesNotUnderflow() =
        runTest {
            val fetcher = CountingFetcher()
            val viewModel = newViewModel(fetcher)

            viewModel.releasePolling()
            viewModel.holdPolling()
            runCurrent()

            assertEquals(1, fetcher.challengeFetches)
        }
}
