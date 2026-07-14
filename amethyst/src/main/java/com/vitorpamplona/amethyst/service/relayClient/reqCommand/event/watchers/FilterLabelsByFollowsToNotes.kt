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
package com.vitorpamplona.amethyst.service.relayClient.reqCommand.event.watchers

import com.vitorpamplona.amethyst.model.Account
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.service.relays.SincePerRelayMap
import com.vitorpamplona.quartz.nip01Core.relay.client.pool.RelayBasedFilter
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip32Labeling.LabelEvent

/**
 * NIP-32: watches for kind 1985 label events that tag the notes on screen, restricted to
 * the accounts the user follows and routed through the outbox model: each follow's labels
 * are requested from THAT follow's outbox relays, with each relay's filter limited to the
 * follows that actually write there. Labels from non-follows are never requested.
 */
fun filterLabelsByFollowsToNotes(
    account: Account,
    notes: List<Note>,
    since: SincePerRelayMap?,
): List<RelayBasedFilter> {
    if (notes.isEmpty()) return emptyList()

    val noteIds = notes.map { it.idHex }.sorted()

    return account.followsPerRelay.value.mapNotNull { (relay, authors) ->
        if (authors.isEmpty()) return@mapNotNull null
        RelayBasedFilter(
            relay = relay,
            filter =
                Filter(
                    kinds = listOf(LabelEvent.KIND),
                    authors = authors.sorted(),
                    tags = mapOf("e" to noteIds),
                    since = since?.get(relay)?.time,
                    // Labels are rare; this only bounds pathological cases.
                    limit = 500,
                ),
        )
    }
}
