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
package com.vitorpamplona.amethyst.commons.relayClient.polls.results

import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer
import com.vitorpamplona.quartz.nip88Polls.poll.PollEvent

/**
 * A small fixed set of relays where ballots actually land when the respondent's client ignores
 * NIP-88's advice to publish on the poll's own `relay` tags and writes to the voter's relays
 * instead — relays the poll author cannot know about. Asking only the poll's relays (plus the
 * author's inbox) under-counts any poll voted on from other clients: on one 23-voter poll they
 * held 9 of the ballots between them.
 *
 * A discovery relay can surface a ballot that exists elsewhere but cannot invent one: the tally
 * still applies NIP-88's rules (latest ballot per pubkey, the poll's time window, first `response`
 * tag only), and relays mirror each other's events.
 */
private val DISCOVERY_RELAYS: List<NormalizedRelayUrl> =
    listOf(
        "wss://relay.primal.net",
        "wss://relay.damus.io",
        "wss://relay.snort.social",
        "wss://nos.lol",
        "wss://relay.ditto.pub",
    ).map { RelayUrlNormalizer.normalize(it) }

/**
 * Every relay a poll's votes could plausibly be on, in query order: the poll's own `relay` tags
 * first (the one place NIP-88 asks every client to file ballots), then the reaction relays (the
 * author's inbox plus where we saw the poll), then the fixed discovery set. Deduplicated, with
 * the poll's relays keeping their lead.
 *
 * The subscription that fetches the ballots and the COUNT that estimates what it missed build
 * their relay list through this one function, so the bars and the vote total cannot disagree.
 */
fun voteRelaySet(
    poll: PollEvent?,
    reactionRelays: List<NormalizedRelayUrl>,
): Set<NormalizedRelayUrl> = (poll?.relays().orEmpty() + reactionRelays + DISCOVERY_RELAYS).toSet()
