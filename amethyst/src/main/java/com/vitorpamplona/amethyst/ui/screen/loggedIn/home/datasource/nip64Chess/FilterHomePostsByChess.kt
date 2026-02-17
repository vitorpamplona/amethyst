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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.home.datasource.nip64Chess

import com.vitorpamplona.amethyst.model.topNavFeeds.chess.ChessTopNavPerRelayFilterSet
import com.vitorpamplona.amethyst.service.relays.SincePerRelayMap
import com.vitorpamplona.quartz.nip01Core.relay.client.pool.RelayBasedFilter
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip64Chess.ChessGameEvent
import com.vitorpamplona.quartz.nip64Chess.LiveChessGameChallengeEvent
import com.vitorpamplona.quartz.nip64Chess.LiveChessGameEndEvent

val ChessPostsKinds =
    listOf(
        ChessGameEvent.KIND, // Completed games (Kind 64)
        LiveChessGameChallengeEvent.KIND, // Challenges (Kind 30064)
        LiveChessGameEndEvent.KIND, // Game endings (Kind 30067)
    )

fun filterHomePostsByChess(
    relays: ChessTopNavPerRelayFilterSet,
    since: SincePerRelayMap?,
    newThreadSince: Long?,
): List<RelayBasedFilter> {
    if (relays.set.isEmpty()) return emptyList()

    return relays.set.map {
        val since = since?.get(it.key)?.time
        val relayUrl = it.key
        RelayBasedFilter(
            relay = relayUrl,
            filter =
                Filter(
                    kinds = ChessPostsKinds,
                    limit = 50,
                    since = since ?: newThreadSince,
                ),
        )
    }
}
