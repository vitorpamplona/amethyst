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

import com.vitorpamplona.amethyst.cli.Args
import com.vitorpamplona.amethyst.cli.Context
import com.vitorpamplona.amethyst.cli.DataDir
import com.vitorpamplona.amethyst.cli.Output
import com.vitorpamplona.amethyst.commons.defaults.Constants
import com.vitorpamplona.quartz.experimental.graperank.GrapeRankPublisher
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer
import com.vitorpamplona.quartz.nip85TrustedAssertions.list.TrustProviderListEvent
import com.vitorpamplona.quartz.nip85TrustedAssertions.list.serviceProviders
import com.vitorpamplona.quartz.nip85TrustedAssertions.list.tags.ProviderTypes
import com.vitorpamplona.quartz.nip85TrustedAssertions.list.tags.ServiceProviderTag
import com.vitorpamplona.quartz.nip85TrustedAssertions.users.ContactCardEvent

/**
 * The transport + consumption sides of NIP-85: `graperank publish` pushes the
 * locally-persisted card set to the operator relay(s); `graperank rank` reads
 * the kind:30382 cards other providers published about a user.
 */
object GrapeRankPublish {
    /**
     * `amy graperank publish [OBSERVER] [--relay URL[,URL…]] [--relay-concurrency N] [--timeout SECS]`
     *
     * Transport only: make the operator relay(s) converge to the local card set
     * that `graperank score` persisted for OBSERVER (default: the active account).
     * One NIP-77 up-only reconcile per relay over the provider service key's
     * kind:30382 cards + kind:5 retractions — nothing is re-scored or re-signed,
     * and a card the relay lost is restored. A relay that can't reconcile gets the
     * full local set blast-published instead. Also refreshes the observer's
     * kind:10040 provider pointer when we hold their key.
     */
    suspend fun publish(
        dataDir: DataDir,
        rest: Array<String>,
    ): Int {
        val args = Args(rest)
        val observerArg = args.positionalOrNull(0)
        val relayArg = args.flag("relay")
        // --relay-concurrency is canonical for "relays worked at once" across the
        // graperank verbs; --concurrency is accepted everywhere as its alias.
        val relayConcurrency = args.intFlag(FLAG_RELAY_CONCURRENCY, args.intFlag(FLAG_CONCURRENCY, 4))
        // Idle watchdog per relay reconcile (not a total budget), like `refresh`.
        val idleTimeoutMs = args.longFlag("timeout", 30L) * 1000
        args.rejectUnknown()

        Context.open(dataDir).use { ctx ->
            ctx.prepare()
            val observer = observerArg?.let { ctx.requireUserHex(it) } ?: ctx.identity.pubKeyHex
            val opKeys = ctx.dataDir.operatorKeys()
            val providerPubkey = opKeys.serviceKey(observer).pubKey.toHexKey()

            // Cards live on the operator's own relay(s); --relay overrides.
            val relays =
                relayArg
                    ?.split(",")
                    ?.mapNotNull { RelayUrlNormalizer.normalizeOrNull(it.trim()) }
                    ?.toSet()
                    ?.takeIf { it.isNotEmpty() }
                    ?: opKeys.operatorRelays()
            if (relays.isEmpty()) {
                return Output.error("no_relays", "no operator relay configured — run `amy graperank operator relay <url>` or pass --relay")
            }

            val publisher = GrapeRankPublisher(ctx.store) { System.err.println(it) }
            val sync =
                publisher.syncToRelays(
                    client = ctx.client,
                    providerPubkey = providerPubkey,
                    relays = relays,
                    relayConcurrency = relayConcurrency,
                    idleTimeoutMs = idleTimeoutMs,
                )

            if (sync.cards == 0 && sync.deletions == 0) {
                Output.emit(
                    linkedMapOf<String, Any?>(
                        "observer" to observer,
                        "provider_pubkey" to providerPubkey,
                        "cards" to 0,
                        "note" to "no local cards for this observer — run `amy graperank score` first",
                    ),
                )
                return 0
            }

            // Help the observer point clients at this provider: publish their
            // kind:10040 (30382:rank -> providerPubkey @ operator relay) to
            // their outbox — but only when we actually hold their key.
            val observer10040 = maybePublishObserverProviderList(ctx, observer, providerPubkey, relays.first())

            Output.emit(
                linkedMapOf<String, Any?>(
                    "observer" to observer,
                    "provider_pubkey" to providerPubkey,
                    "cards" to sync.cards,
                    "deletions" to sync.deletions,
                    "relays" to sync.perRelay.size,
                    "relays_ok" to sync.perRelay.count { it.ok },
                    "relays_failed" to sync.perRelay.count { !it.ok },
                    "uploaded" to sync.perRelay.sumOf { it.uploaded },
                    "fallback_published" to sync.perRelay.sumOf { it.fallbackPublished },
                    "per_relay" to
                        sync.perRelay.map {
                            linkedMapOf<String, Any?>(
                                "relay" to it.relay.url,
                                "uploaded" to it.uploaded,
                                "fallback_published" to it.fallbackPublished,
                                "fallback_rejected" to it.fallbackRejected,
                                "error" to it.error,
                            )
                        },
                    "observer_10040" to observer10040,
                ),
            )
            return 0
        }
    }

    /**
     * `amy graperank rank USER [--provider PUBKEY] [--refresh] [--timeout SECS]`
     *
     * The consumer side of NIP-85: read the kind:30382 cards about USER and print
     * one rank per provider (newest card each). Cache-first — a `graperank score`
     * run on this machine already left its cards in the store — falling back to a
     * relay drain on a miss or with `--refresh` (sources: the operator relays, the
     * relays declared in the account's kind:10040, and the bootstrap set).
     * `--provider` narrows to one provider key.
     */
    suspend fun rank(
        dataDir: DataDir,
        rest: Array<String>,
    ): Int {
        val args = Args(rest)
        val userArg =
            args.positionalOrNull(0)
                ?: return Output.error("bad_args", "usage: amy graperank rank USER [--provider PUBKEY] [--refresh] [--timeout SECS]")
        val providerArg = args.flag("provider")
        val refresh = args.bool("refresh")
        val timeoutMs = args.longFlag("timeout", 8L) * 1000
        args.rejectUnknown()

        Context.openOrAnonymous(dataDir).use { ctx ->
            ctx.prepare()
            val user = ctx.requireUserHex(userArg)
            val provider = providerArg?.let { ctx.requireUserHex(it) }
            val cardFilter =
                Filter(
                    kinds = listOf(ContactCardEvent.KIND),
                    tags = mapOf("d" to listOf(user)),
                    authors = provider?.let { listOf(it) },
                )

            suspend fun localCards(): List<ContactCardEvent> = ctx.store.query<Event>(cardFilter).filterIsInstance<ContactCardEvent>()

            var cards = localCards()
            if (refresh || cards.isEmpty()) {
                val relays = rankSourceRelays(ctx, provider)
                if (relays.isNotEmpty()) {
                    ctx.drain(relays.associateWith { listOf(cardFilter.copy(limit = 50)) }, timeoutMs)
                    cards = localCards()
                }
            }

            // Newest card per provider key, strongest assertion first.
            val newest =
                cards
                    .groupBy { it.pubKey }
                    .mapNotNull { (_, list) -> list.maxByOrNull { it.createdAt } }
                    .sortedWith(compareByDescending<ContactCardEvent> { it.rank() ?: -1 }.thenByDescending { it.createdAt })

            // A provider key this machine's operator master derived maps back to
            // the observer whose subjective view the rank expresses.
            val providerToObserver =
                ctx.dataDir
                    .operatorKeys()
                    .providers()
                    .entries
                    .associate { (observer, rec) -> rec.providerPubKey to observer }

            Output.emit(
                linkedMapOf<String, Any?>(
                    "user" to user,
                    "found" to newest.isNotEmpty(),
                    "cards" to
                        newest.map { card ->
                            linkedMapOf<String, Any?>(
                                "provider" to card.pubKey,
                                "rank" to card.rank(),
                                "followers" to card.followerCount(),
                                "hops" to card.hops(),
                                "observer" to providerToObserver[card.pubKey],
                                "created_at" to card.createdAt,
                                "event_id" to card.id,
                            )
                        },
                ),
            )
            return 0
        }
    }

    /**
     * Relays worth draining for someone's kind:30382 cards: the machine's own
     * operator relay(s), every relay the account's kind:10040 declares for a
     * 30382 service (narrowed to [provider] when given), and the bootstrap set.
     */
    private suspend fun rankSourceRelays(
        ctx: Context,
        provider: HexKey?,
    ): Set<NormalizedRelayUrl> {
        val declared =
            if (!ctx.anonymous) {
                providerListOf(ctx, ctx.identity.pubKeyHex)
                    ?.serviceProviders()
                    ?.filter { it.service.kind == ContactCardEvent.KIND && (provider == null || it.pubkey == provider) }
                    ?.map { it.relayUrl }
                    .orEmpty()
            } else {
                emptyList()
            }
        return ctx.dataDir.operatorKeys().operatorRelays() + declared + ctx.bootstrapRelays() + Constants.eventFinderRelays
    }

    /**
     * If the active account IS the observer (so we hold their key), publish/refresh
     * their kind:10040 declaring `30382:rank` -> [providerPubkey] at [relay], to
     * their own outbox relays — the NIP-85 pointer a client follows to find these
     * cards. Returns the 10040 event id, or null when we don't hold the key (a
     * third-party observer must add the provider to their 10040 out-of-band).
     */
    private suspend fun maybePublishObserverProviderList(
        ctx: Context,
        observer: HexKey,
        providerPubkey: HexKey,
        relay: NormalizedRelayUrl,
    ): String? {
        if (observer != ctx.identity.pubKeyHex) return null
        val service = ProviderTypes.rank
        val outbox = ctx.outboxRelays()
        val latest = fetchLatestProviderList(ctx, observer, outbox, 8_000)
        val alreadyListed =
            latest?.serviceProviders()?.any {
                it.service == service && it.pubkey == providerPubkey && it.relayUrl == relay
            } ?: false
        if (alreadyListed) return latest.id

        val tag = ServiceProviderTag(service, providerPubkey, relay)
        val event =
            if (latest == null) {
                TrustProviderListEvent.create(tag, isPrivate = false, signer = ctx.signer)
            } else {
                TrustProviderListEvent.add(latest, tag, isPrivate = false, signer = ctx.signer)
            }
        ctx.publish(event, outbox)
        return event.id
    }
}
