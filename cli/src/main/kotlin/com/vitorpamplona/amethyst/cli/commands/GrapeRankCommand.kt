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
package com.vitorpamplona.amethyst.cli.commands

import com.vitorpamplona.amethyst.cli.DataDir
import com.vitorpamplona.amethyst.cli.commands.graperank.GrapeRankCrawl
import com.vitorpamplona.amethyst.cli.commands.graperank.GrapeRankOperator
import com.vitorpamplona.amethyst.cli.commands.graperank.GrapeRankPublish
import com.vitorpamplona.amethyst.cli.commands.graperank.GrapeRankScore

/**
 * `amy graperank [OBSERVER] [flags]` — compute GrapeRank web-of-trust scores.
 *
 * GrapeRank assigns every user reachable in the follow/mute/report graph a
 * subjective trust score in `[0, 1]` from the observer's point of view (the
 * observer has full self-trust). It crawls the follow graph outward using the
 * outbox model — each user's kind:10002 write relays are located first, then
 * their kind:3 / kind:10000 / kind:1984 events are fetched from *their own*
 * relays. The crawl is exhaustive: it keeps going, with no user cap, until every
 * discovered user's outbox has been checked and their contact list pulled (an
 * unreachable outbox is retried a few times), then runs the scoring engine in
 * `commons/wot`.
 *
 * The pipeline is three separable stages, each with its own verb, and the local
 * store is the source of truth between them (the crawl persists every event it
 * fetches; the score is a pure function over the store; every score run persists
 * its result as locally-signed NIP-85 kind:30382 cards):
 *  - `amy graperank crawl [OBSERVER]` — network only: crawl the reachable graph's
 *    kind 3/10000/1984/10002 into the local store. Idempotent and cumulative, so
 *    run it a few times to make sure everything is loaded. Scores nothing.
 *  - `amy graperank status` — read-only inventory of all of the above: WoT record
 *    counts, reachability-cache freshness, operator state, persisted card sets.
 *    Answers "do I need to crawl again?" with no network and no signing.
 *  - `amy graperank score [OBSERVER]` — local only: build the graph from the store,
 *    score (same as bare `--offline`), and ALWAYS reconcile the result into the
 *    store as kind:30382 ContactCardEvent cards signed by the observer's
 *    per-observer service key (`rank = round(score*100)`, cutoff `--min-rank`):
 *    changed ranks are re-signed, unchanged ones skipped, dropped targets
 *    retracted with a kind:5. That persisted card set is what `publish` and
 *    `rank` reuse — scores are never ephemeral.
 *  - `amy graperank publish [OBSERVER]` — transport only: make the operator
 *    relay(s) converge to the local card set via a NIP-77 up-only reconcile
 *    (nothing is re-signed or re-scored), and refresh the observer's kind:10040
 *    pointer when we hold their key.
 *  - `amy relay probe` — the relay census: mass-connect every relay the store
 *    knows and record live/dead + measured RTT into the reachability cache, so the
 *    next crawl skips the dead and pre-connects the living in one parallel storm.
 *    Lives in [RelayCommands] (`graperank probe` is kept as an alias).
 *  - bare `amy graperank [OBSERVER]` — the convenience combo: crawl then score.
 *
 * Sub-verbs complete the NIP-85 experience — discovery and consumption:
 *  - `amy graperank rank USER` — read the kind:30382 cards about USER (local
 *    store first, `--refresh` to drain providers' relays): the consumer side.
 *  - `amy graperank register` — advertise a `30382:rank` provider in the
 *    account's kind:10040 TrustProviderListEvent (defaults to self, so a
 *    provider publishing ranks announces where to find them);
 *    `amy graperank unregister PROVIDER` removes entries again.
 *  - `amy graperank providers [USER]` — list a user's trusted providers.
 *
 * The verb implementations live in the `graperank` subpackage: [GrapeRankScore]
 * (the bare crawl+score combo and `score`), [GrapeRankCrawl] (`crawl` /
 * `followers`), [GrapeRankPublish] (`publish` / `rank`), and [GrapeRankOperator]
 * (`status` / `refresh` / `operator` / `register` / `unregister` / `providers`).
 */
object GrapeRankCommand {
    val USAGE: String =
        """
        |amy graperank — GrapeRank web-of-trust: crawl + score + publish NIP-85 cards
        |
        |  graperank [OBSERVER]                       crawl + score: subjective trust (0..1) over the
        |    [--min-rank N] [--offline]                follow/mute/report graph, then persist the result
        |    [--limit N] [--min-score X]               as local NIP-85 kind:30382 cards (ranks >=
        |    [--rigor X] [--attenuation X]             --min-rank, default 2). --offline skips the crawl.
        |    [--max-hops N] [--diagnose]               OBSERVER: npub|nprofile|hex|name@domain (self).
        |  graperank crawl [OBSERVER]                 network only: crawl the graph (kind 3/10000/1984/
        |    [--max-hops N] [--max-rounds N]           10002) into the local store, no scoring.
        |    [--no-preconnect] [--preconnect-cap N]    Idempotent — run a few times to load everything.
        |  graperank followers [OBSERVER]             reverse crawl: pull kind:3 lists that #p-tag the
        |    [--relay URL[,URL…]] [--max N]            observer from every relay the store knows, so
        |    [--timeout SECS] [--relay-concurrency N]  every follower becomes a graph edge for `score`.
        |    [--insert-batch N]
        |  graperank score [OBSERVER]                 local only: score from the store + persist cards
        |                                              (= bare --offline; same flags). No network.
        |  graperank publish [OBSERVER]               push local cards to the operator relay(s) via a
        |    [--relay URL[,URL…]] [--timeout SECS]     NIP-77 up-sync (nothing re-scored), and refresh
        |    [--relay-concurrency N]                   the observer's kind:10040 when we hold their key.
        |  graperank rank USER [--provider PUBKEY]    read the kind:30382 cards about USER, one rank per
        |    [--refresh] [--timeout SECS]              provider; --refresh drains relays on a miss.
        |  graperank status                           read-only local inventory: record counts, cache
        |                                              freshness, operator state, cards per observer.
        |  graperank refresh [--down] [--up]          re-sync known authors' records (kind 0/3/10002/
        |    [--relay-concurrency N] [--author-chunk N] 1984) from their outboxes via NIP-77, so score
        |    [--min-authors N] [--report-limit N]       runs on current data. (`update` is a deprecated
        |    [--no-sync-deletions] [--timeout SECS]     alias.)
        |  graperank register [PROVIDER]              declare a NIP-85 provider in your kind:10040
        |    [--service KIND:TAG] [--relay URL]        (default: self as 30382:rank at your 1st outbox).
        |    [--private]
        |  graperank unregister PROVIDER              remove matching entries from your kind:10040;
        |    [--service KIND:TAG] [--relay URL]        --service/--relay narrow, else all for that key.
        |  graperank providers [USER] [--refresh]     list a user's declared NIP-85 providers.
        |    [--timeout SECS]
        |  graperank operator                         operator keys (~/.amy/operator/): `relay URL…`
        |    [status | relay URL… | keys]              sets the publish target; `keys` maps observer
        |                                              -> service-key. (default: status)
        |  graperank probe                            deprecated alias for `relay probe` (relay census).
        """.trimMargin()

    suspend fun dispatch(
        dataDir: DataDir,
        tail: Array<String>,
    ): Int =
        // Sub-verbs are explicit words; anything else (npub / hex / nprofile /
        // NIP-05, or nothing) is the OBSERVER positional for a score computation.
        // The bare-positional default means route() can't be used here, so
        // handle --help explicitly before the fall-through.
        when (tail.firstOrNull()) {
            "--help", "-h", "help" -> {
                System.err.println(USAGE)
                0
            }
            "register" -> GrapeRankOperator.register(dataDir, tail.drop(1).toTypedArray())
            "unregister" -> GrapeRankOperator.unregister(dataDir, tail.drop(1).toTypedArray())
            "providers" -> GrapeRankOperator.providers(dataDir, tail.drop(1).toTypedArray())
            "operator" -> GrapeRankOperator.operator(dataDir, tail.drop(1).toTypedArray())
            "crawl" -> GrapeRankCrawl.crawl(dataDir, tail.drop(1).toTypedArray())
            "followers" -> GrapeRankCrawl.followers(dataDir, tail.drop(1).toTypedArray())
            "status" -> GrapeRankOperator.status(dataDir)
            // The relay census outgrew graperank (it feeds the shared NIP-66
            // reachability cache every command reads) and moved to `amy relay
            // probe`; this alias keeps the old spelling working.
            "probe" -> {
                System.err.println("[amy] `graperank probe` is deprecated — use `relay probe` (the relay census).")
                RelayCommands.probe(dataDir, tail.drop(1).toTypedArray())
            }
            // `refresh` is canonical (it refreshes the WoT record kinds from each
            // author's outbox); `update` is the pre-rename back-compat alias.
            "refresh" -> GrapeRankOperator.refresh(dataDir, tail.drop(1).toTypedArray())
            "update" -> {
                System.err.println("[amy] `graperank update` is deprecated — use `graperank refresh`.")
                GrapeRankOperator.refresh(dataDir, tail.drop(1).toTypedArray())
            }
            "score" -> GrapeRankScore.run(dataDir, tail.drop(1).toTypedArray(), forceOffline = true)
            "publish" -> GrapeRankPublish.publish(dataDir, tail.drop(1).toTypedArray())
            "rank" -> GrapeRankPublish.rank(dataDir, tail.drop(1).toTypedArray())
            else -> GrapeRankScore.run(dataDir, tail)
        }
}
