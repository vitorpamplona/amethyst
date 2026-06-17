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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.music.datasource.subassemblies

import com.vitorpamplona.amethyst.service.relays.SincePerRelayMap
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.relay.client.pool.RelayBasedFilter
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl

/**
 * Builds the relay filters for the "Mine" music selector: the user's own tracks/playlists,
 * queried by author against their outbox relays. Mirrors `filterBadgesMine` /
 * `filterCommunitiesMine`. The `kinds` list scopes it to tracks (36787) or playlists (34139).
 */
fun filterMusicEventsMine(
    pubkey: HexKey,
    kinds: List<Int>,
    relays: Set<NormalizedRelayUrl>,
    since: SincePerRelayMap?,
): List<RelayBasedFilter> {
    if (relays.isEmpty() || pubkey.isEmpty()) return emptyList()
    val authors = listOf(pubkey)
    return relays.map { relay ->
        RelayBasedFilter(
            relay = relay,
            filter =
                Filter(
                    kinds = kinds,
                    authors = authors,
                    limit = 200,
                    since = since?.get(relay)?.time,
                ),
        )
    }
}
