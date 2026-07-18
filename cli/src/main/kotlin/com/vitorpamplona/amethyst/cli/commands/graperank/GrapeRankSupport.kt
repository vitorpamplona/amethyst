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
package com.vitorpamplona.amethyst.cli.commands.graperank

import com.vitorpamplona.amethyst.cli.Context
import com.vitorpamplona.amethyst.commons.defaults.DefaultIndexerRelayList
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer
import com.vitorpamplona.quartz.nip85TrustedAssertions.list.TrustProviderListEvent

/** Flag names + defaults shared by several graperank verbs. */
internal const val FLAG_INSERT_BATCH = "insert-batch"
internal const val FLAG_RELAY_CONCURRENCY = "relay-concurrency"
internal const val FLAG_CONCURRENCY = "concurrency"
internal const val INSERT_BATCH_DEFAULT = 500

// args.bool on a mistyped flag silently returns false, so the one flag read from
// three different functions goes through a compile-time-checked name.
internal const val NO_REACHABILITY_CACHE_FLAG = "no-reachability-cache"

// Default score cutoff for counting a follower as "trusted" (the `followers`
// tag). Matches NosFabrica Brainstorm's verifiedFollowersInfluenceCutoff.
internal const val DEFAULT_FOLLOWERS_THRESHOLD = 0.02

// Broad, big general relays that carry kind:10002 for many users, added to the
// crawler's discovery set to raise the odds of resolving a stranger's outbox.
internal val EXTRA_DISCOVERY_RELAYS: Set<NormalizedRelayUrl> =
    listOf(
        "wss://relay.damus.io",
        "wss://relay.snort.social",
        "wss://offchain.pub",
        "wss://nostr.land",
        "wss://eden.nostr.land",
    ).mapNotNull { RelayUrlNormalizer.normalizeOrNull(it) }.toSet()

// Network-wide aggregators that scrape and hold kind:3 for users whose own
// outbox lacks it. The crawler queries these for a straggler's CONTENT (kind:3),
// not just their kind:10002 relay list. Measured on observer 460c25e6, the distinct
// missing authors whose kind:3 each holds: kindpag.es 369, yabu 126, oxtr.dev 76,
// nos.lol 72, ditto 56, nostr1 29, momostr 11, mostr 3. So beyond the profile
// indexers (kindpag/purplepag/coracle/yabu/nostr1) and the ActivityPub bridges
// (ditto/momostr/mostr, which host bridged users' lists), two big general relays --
// nostr.oxtr.dev and nos.lol -- carry ~150 more that no indexer has.
internal val CONTENT_AGGREGATOR_RELAYS: Set<NormalizedRelayUrl> =
    DefaultIndexerRelayList +
        listOf(
            "wss://relay.ditto.pub",
            "wss://relay.momostr.pink",
            "wss://relay.mostr.pub",
            "wss://nostr.oxtr.dev",
            "wss://nos.lol",
        ).mapNotNull { RelayUrlNormalizer.normalizeOrNull(it) }.toSet()

/** Latest known kind:10040 provider list for [pubKey] from the local store. */
internal suspend fun providerListOf(
    ctx: Context,
    pubKey: HexKey,
): TrustProviderListEvent? =
    ctx.store
        .query<Event>(Filter(kinds = listOf(TrustProviderListEvent.KIND), authors = listOf(pubKey), limit = 1))
        .firstOrNull() as? TrustProviderListEvent

/**
 * Fetch the freshest kind:10040 for [pubKey] from [relays] so a register
 * builds on top of the current provider set instead of clobbering it.
 */
internal suspend fun fetchLatestProviderList(
    ctx: Context,
    pubKey: HexKey,
    relays: Set<NormalizedRelayUrl>,
    timeoutMs: Long,
): TrustProviderListEvent? {
    if (relays.isEmpty()) return providerListOf(ctx, pubKey)
    val filter = Filter(kinds = listOf(TrustProviderListEvent.KIND), authors = listOf(pubKey), limit = 1)
    ctx.drain(relays.associateWith { listOf(filter) }, timeoutMs)
    return providerListOf(ctx, pubKey)
}
