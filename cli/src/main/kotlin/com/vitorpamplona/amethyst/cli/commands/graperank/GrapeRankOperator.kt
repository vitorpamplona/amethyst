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
import com.vitorpamplona.amethyst.cli.commands.RawEventSupport
import com.vitorpamplona.amethyst.commons.defaults.Constants
import com.vitorpamplona.quartz.experimental.graperank.GrapeRankUpdater
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.metadata.MetadataEvent
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerInternal
import com.vitorpamplona.quartz.nip02FollowList.ContactListEvent
import com.vitorpamplona.quartz.nip09Deletions.DeletionEvent
import com.vitorpamplona.quartz.nip51Lists.muteList.MuteListEvent
import com.vitorpamplona.quartz.nip56Reports.ReportEvent
import com.vitorpamplona.quartz.nip65RelayList.AdvertisedRelayListEvent
import com.vitorpamplona.quartz.nip66RelayMonitor.discovery.RelayDiscoveryEvent
import com.vitorpamplona.quartz.nip66RelayMonitor.reachability.RelayReachabilityStore
import com.vitorpamplona.quartz.nip85TrustedAssertions.list.TrustProviderListEvent
import com.vitorpamplona.quartz.nip85TrustedAssertions.list.serviceProviders
import com.vitorpamplona.quartz.nip85TrustedAssertions.list.tags.ProviderTypes
import com.vitorpamplona.quartz.nip85TrustedAssertions.list.tags.ServiceProviderTag
import com.vitorpamplona.quartz.nip85TrustedAssertions.list.tags.ServiceType
import com.vitorpamplona.quartz.nip85TrustedAssertions.users.ContactCardEvent
import com.vitorpamplona.quartz.utils.TimeUtils

/**
 * The bookkeeping graperank verbs: the read-only local inventory (`status`), the
 * NIP-77 record refresh (`refresh`), the machine's operator-key management
 * (`operator`), and the NIP-85 kind:10040 provider-list verbs
 * (`register` / `unregister` / `providers`).
 */
object GrapeRankOperator {
    /**
     * `amy graperank status` — read-only inventory of everything a GrapeRank run
     * depends on, straight from the local store: WoT record counts (the "do I
     * need to crawl again?" answer), reachability-cache size + freshness,
     * operator/service-key state, and the persisted card set per observer.
     * No network, no signing, no side effects.
     */
    suspend fun status(dataDir: DataDir): Int {
        Context.openOrAnonymous(dataDir).use { ctx ->
            // Deliberately no ctx.prepare(): status must stay offline (nothing here
            // needs a relay connection or marmot state).
            suspend fun countKind(kind: Int) = ctx.store.count(Filter(kinds = listOf(kind)))

            // The store keeps only the newest replaceable event per author, so the
            // kind:3 count is "users whose follow list we hold" — the graph size a
            // `score` would see.
            val contactLists = countKind(ContactListEvent.KIND)

            // Read-only reachability view: ctx.reachability would lazily derive the
            // monitor key and thereby CREATE the operator master on a fresh machine;
            // a throwaway signer reads the same kind:30166 records without that
            // side effect (the signer is only used for writes).
            val reach = RelayReachabilityStore(store = ctx.store, signer = NostrSignerInternal(KeyPair())).snapshot()
            val newestReachRecord =
                ctx.store
                    .query<Event>(Filter(kinds = listOf(RelayDiscoveryEvent.KIND), limit = 1))
                    .firstOrNull()
                    ?.createdAt

            val opKeys = ctx.dataDir.operatorKeys()
            val cards =
                opKeys.providers().map { (observer, rec) ->
                    linkedMapOf<String, Any?>(
                        "observer" to observer,
                        "provider_pubkey" to rec.providerPubKey,
                        "cards" to ctx.store.count(Filter(kinds = listOf(ContactCardEvent.KIND), authors = listOf(rec.providerPubKey))),
                        "retractions" to ctx.store.count(Filter(kinds = listOf(DeletionEvent.KIND), authors = listOf(rec.providerPubKey))),
                    )
                }

            Output.emit(
                linkedMapOf<String, Any?>(
                    "store" to
                        linkedMapOf<String, Any?>(
                            "profiles" to countKind(MetadataEvent.KIND),
                            "contact_lists" to contactLists,
                            "mute_lists" to countKind(MuteListEvent.KIND),
                            "reports" to countKind(ReportEvent.KIND),
                            "relay_lists" to countKind(AdvertisedRelayListEvent.KIND),
                        ),
                    "reachability" to
                        linkedMapOf<String, Any?>(
                            "live" to reach.live.size,
                            "dead" to reach.dead.size,
                            "newest_record_age_s" to newestReachRecord?.let { (TimeUtils.now() - it).coerceAtLeast(0) },
                        ),
                    "operator" to
                        if (opKeys.exists()) {
                            linkedMapOf<String, Any?>(
                                "initialized" to true,
                                "master_pubkey" to opKeys.masterPubKey(),
                                "relays" to opKeys.operatorRelays().map { it.url },
                            )
                        } else {
                            linkedMapOf<String, Any?>("initialized" to false)
                        },
                    "cards" to cards,
                    "note" to if (contactLists == 0) "no contact lists in the local store — run `amy graperank crawl` first" else null,
                ),
            )
        }
        return 0
    }

    /**
     * `amy graperank refresh [flags]` (alias: `update`) — refresh every locally-known author's WoT
     * record kinds (0 / 3 / 10002 / 1984) straight from their own outbox, so the
     * next `graperank score` runs on current data without a full follow-graph crawl.
     *
     * Thin wrapper over quartz's [GrapeRankUpdater]: it reads every kind:10002 in the
     * store, inverts them into a `write-relay -> authors` map (the outbox model), and
     * runs one NIP-77 negentropy reconcile per write relay scoped to its authors —
     * bidirectional, settling deletions over the residual (its applyDown direction
     * downloads the relay's kind:5 when an uploaded record was rejected), and falling
     * back to a full paged download when a relay can't reconcile. This command only
     * parses flags and renders the [GrapeRankUpdater.Result] as text/JSON.
     *
     * Flags: `--timeout SECS` (per-group idle watchdog, default 30),
     * `--relay-concurrency N` (relays reconciled at once, default 4),
     * `--author-chunk N` (authors per reconcile filter, default 500),
     * `--min-authors N` (skip relays hosting fewer than N of our authors, default 1),
     * `--report-limit N` (per-relay rows in the JSON, default 50),
     * `--down` / `--up` / `--no-sync-deletions`.
     */
    suspend fun refresh(
        dataDir: DataDir,
        rest: Array<String>,
    ): Int {
        val args = Args(rest)
        val reportLimit = args.intFlag("report-limit", 50).coerceAtLeast(0)
        // Default is bidirectional; a single --down/--up narrows to that direction.
        val downFlag = args.bool("down")
        val upFlag = args.bool("up")
        // The remaining flags are read later inside the updater Config.
        args.rejectUnknown(
            "no-sync-deletions",
            FLAG_RELAY_CONCURRENCY,
            FLAG_CONCURRENCY,
            "author-chunk",
            "min-authors",
            "timeout",
            NO_REACHABILITY_CACHE_FLAG,
        )

        Context.openOrAnonymous(dataDir).use { ctx ->
            ctx.prepare()

            // Skip relays a crawl/monitor proved dead within the cache's TTL — a dead
            // relay cannot serve its authors, so reconciling it only burns a timeout.
            // Live author-advertised relays are always synced (--no-reachability-cache
            // to reconcile every relay regardless).
            val knownDead =
                if (args.bool(NO_REACHABILITY_CACHE_FLAG)) emptySet() else ctx.reachability.snapshot().dead

            val updater =
                GrapeRankUpdater(
                    client = ctx.client,
                    store = ctx.store,
                    config =
                        GrapeRankUpdater.Config(
                            down = downFlag || !upFlag,
                            up = upFlag || !downFlag,
                            syncDeletions = !args.bool("no-sync-deletions"),
                            relayConcurrency = args.intFlag(FLAG_RELAY_CONCURRENCY, args.intFlag(FLAG_CONCURRENCY, 4)),
                            authorChunk = args.intFlag("author-chunk", 500),
                            minAuthors = args.intFlag("min-authors", 1),
                            idleTimeoutMs = args.longFlag("timeout", 30L) * 1000,
                            knownDead = knownDead,
                        ),
                    log = { System.err.println(it) },
                )

            val result = updater.update()

            if (result.relays == 0) {
                Output.emit(
                    linkedMapOf<String, Any?>(
                        "relay_lists_in_store" to result.relayListsInStore,
                        "authors_with_outbox" to result.authorsWithOutbox,
                        "relays" to 0,
                        "note" to "no kind:10002 write relays in the local store — run `graperank crawl` first",
                    ),
                )
                return 0
            }

            // Busiest relays first, capped so a many-thousand-relay run still emits a
            // bounded JSON object; totals below always cover every relay.
            val report =
                result.perRelay
                    .sortedByDescending { it.downloaded + it.uploaded }
                    .take(reportLimit)
                    .map {
                        linkedMapOf<String, Any?>(
                            "relay" to it.relay.url,
                            "authors" to it.authors,
                            "need" to it.need,
                            "have" to it.have,
                            "downloaded" to it.downloaded,
                            "uploaded" to it.uploaded,
                            "deletions_sent_up" to it.deletionsSentUp,
                            "deletions_applied_down" to it.deletionsAppliedDown,
                            "paged_fallback" to it.pagedFallback,
                            "error" to it.error,
                        )
                    }

            Output.emit(
                linkedMapOf<String, Any?>(
                    "kinds" to GrapeRankUpdater.DEFAULT_KINDS,
                    "relay_lists_in_store" to result.relayListsInStore,
                    "authors_with_outbox" to result.authorsWithOutbox,
                    "relays" to result.relays,
                    "relays_ok" to result.relaysOk,
                    "relays_failed" to result.relaysFailed,
                    "relays_paged_fallback" to result.relaysPagedFallback,
                    "downloaded" to result.downloaded,
                    "uploaded" to result.uploaded,
                    "deletions_sent_up" to result.deletionsSentUp,
                    "deletions_applied_down" to result.deletionsAppliedDown,
                    "report_limit" to reportLimit,
                    "per_relay" to report,
                ),
            )
            return 0
        }
    }

    /**
     * `amy graperank operator [status | relay <url>… | keys]`
     *
     * Manage the machine's operator keys used to sign trusted-assertion cards.
     *  - `status` (default): master pubkey, configured relay(s), service-key count.
     *  - `relay <url>…`: set the operator relay(s) the cards + retractions publish
     *    to; creates the operator master on first use.
     *  - `keys` (alias: the pre-rename `providers`, which collided with
     *    `graperank providers`): the observer -> service-key mapping derived so
     *    far — what a third-party observer wires into their kind:10040.
     */
    fun operator(
        dataDir: DataDir,
        rest: Array<String>,
    ): Int {
        val opKeys = dataDir.operatorKeys()
        return when (rest.firstOrNull()) {
            "relay" -> {
                val urls = rest.drop(1).filter { it.isNotBlank() }
                val normalized = urls.mapNotNull { RelayUrlNormalizer.normalizeOrNull(it) }
                if (normalized.isEmpty()) return Output.error("bad_args", "usage: amy graperank operator relay <wss://…> [<wss://…> …]")
                opKeys.setRelays(urls)
                Output.emit(mapOf("master_pubkey" to opKeys.masterPubKey(), "relays" to normalized.map { it.url }))
                0
            }

            "keys", "providers" -> {
                Output.emit(
                    mapOf(
                        "master_pubkey" to if (opKeys.exists()) opKeys.masterPubKey() else null,
                        "keys" to opKeys.providers().map { (observer, rec) -> mapOf("observer" to observer, "provider_pubkey" to rec.providerPubKey) },
                    ),
                )
                0
            }

            null, "status" -> {
                if (!opKeys.exists()) {
                    Output.emit(mapOf("initialized" to false))
                } else {
                    Output.emit(
                        mapOf(
                            "initialized" to true,
                            "master_pubkey" to opKeys.masterPubKey(),
                            "relays" to opKeys.operatorRelays().map { it.url },
                            "keys" to opKeys.providers().size,
                        ),
                    )
                }
                0
            }

            else -> Output.error("bad_args", "unknown operator subcommand '${rest.first()}' (status | relay | keys)")
        }
    }

    /**
     * `amy graperank register [PROVIDER] [--service KIND:TAG] [--relay URL] [--private]`
     *
     * Add a NIP-85 provider entry to the account's kind:10040
     * [TrustProviderListEvent] — the declaration a client reads to discover which
     * key publishes which assertion, and where. Defaults to declaring *self* as
     * the `30382:rank` provider at the account's first outbox relay, which is the
     * self-advertisement a GrapeRank provider makes so its followers can find the
     * cards it publishes. Fetches the freshest list first so existing providers
     * are preserved.
     */
    suspend fun register(
        dataDir: DataDir,
        rest: Array<String>,
    ): Int {
        val args = Args(rest)
        val providerArg = args.positionalOrNull(0) ?: args.flag("provider")
        val serviceArg = args.flag("service")
        val relayArg = args.flag("relay")
        val isPrivate = args.bool("private")
        val timeoutMs = args.longFlag("timeout", 8L) * 1000
        args.rejectUnknown()

        val service =
            serviceArg?.let {
                ServiceType.parse(it) ?: return Output.error("bad_args", "--service must be KIND:TAG, e.g. 30382:rank")
            } ?: ProviderTypes.rank

        Context.open(dataDir).use { ctx ->
            ctx.prepare()
            val self = ctx.identity.pubKeyHex
            val provider = providerArg?.let { ctx.requireUserHex(it) } ?: self

            val outbox = ctx.outboxRelays()
            val relay =
                relayArg?.let { RelayUrlNormalizer.normalizeOrNull(it) }
                    ?: outbox.firstOrNull()
                    ?: return Output.error("no_relays", "no relay hint; pass --relay URL or configure outbox relays")

            val latest = fetchLatestProviderList(ctx, self, outbox, timeoutMs)
            val alreadyListed =
                latest?.serviceProviders()?.any {
                    it.service == service && it.pubkey == provider && it.relayUrl == relay
                } ?: false

            if (alreadyListed) {
                Output.emit(
                    mapOf(
                        "service" to service.toValue(),
                        "provider" to provider,
                        "relay" to relay.url,
                        "changed" to false,
                        "based_on" to latest.id,
                    ),
                )
                return 0
            }

            val tag = ServiceProviderTag(service, provider, relay)
            val event =
                if (latest == null) {
                    TrustProviderListEvent.create(tag, isPrivate = isPrivate, signer = ctx.signer)
                } else {
                    TrustProviderListEvent.add(latest, tag, isPrivate = isPrivate, signer = ctx.signer)
                }

            val ack = ctx.publish(event, outbox)
            RawEventSupport.publishGuard(ack, event.id)?.let { return it }
            Output.emit(
                mapOf(
                    "service" to service.toValue(),
                    "provider" to provider,
                    "relay" to relay.url,
                    "private" to isPrivate,
                    "changed" to true,
                    "event_id" to event.id,
                    "based_on" to latest?.id,
                    "published_to" to ack.filterValues { it }.keys.map { it.url },
                    "rejected_by" to ack.filterValues { !it }.keys.map { it.url },
                ),
            )
            return 0
        }
    }

    /**
     * `amy graperank unregister PROVIDER [--service KIND:TAG] [--relay URL] [--timeout SECS]`
     *
     * The inverse of [register]: drop matching provider entries — public AND
     * private — from the account's kind:10040 [TrustProviderListEvent] and
     * re-publish it. PROVIDER is required; `--service` / `--relay` narrow the
     * match when the same key is listed for several services or relays — without
     * them, every entry for that provider key is removed. Fetches the freshest
     * list first so the removal applies to the current provider set.
     */
    suspend fun unregister(
        dataDir: DataDir,
        rest: Array<String>,
    ): Int {
        val args = Args(rest)
        val providerArg =
            args.positionalOrNull(0)
                ?: args.flag("provider")
                ?: return Output.error("bad_args", "usage: amy graperank unregister PROVIDER [--service KIND:TAG] [--relay URL]")
        val serviceArg = args.flag("service")
        val relayArg = args.flag("relay")
        val timeoutMs = args.longFlag("timeout", 8L) * 1000
        args.rejectUnknown()

        val service =
            serviceArg?.let {
                ServiceType.parse(it) ?: return Output.error("bad_args", "--service must be KIND:TAG, e.g. 30382:rank")
            }
        val relay =
            relayArg?.let {
                RelayUrlNormalizer.normalizeOrNull(it) ?: return Output.error("bad_args", "--relay is not a valid relay URL")
            }

        Context.open(dataDir).use { ctx ->
            ctx.prepare()
            val provider = ctx.requireUserHex(providerArg)
            val outbox = ctx.outboxRelays()

            val latest =
                fetchLatestProviderList(ctx, ctx.identity.pubKeyHex, outbox, timeoutMs)
                    ?: return Output.error("not_found", "no kind:10040 provider list found for this account")

            fun matches(tag: ServiceProviderTag) =
                tag.pubkey == provider &&
                    (service == null || tag.service == service) &&
                    (relay == null || tag.relayUrl == relay)

            val publicMatches = latest.serviceProviders().filter(::matches)
            val privateMatches =
                latest
                    .privateTags(ctx.signer)
                    ?.serviceProviders()
                    .orEmpty()
                    .filter(::matches)
            val toRemove = (publicMatches + privateMatches).distinct()

            if (toRemove.isEmpty()) {
                Output.emit(
                    mapOf(
                        "provider" to provider,
                        "changed" to false,
                        "removed" to emptyList<Any>(),
                        "based_on" to latest.id,
                    ),
                )
                return 0
            }

            // remove() strips the tag from both the public and the private set,
            // re-signing each round; only the final version is published.
            var event = latest
            for (tag in toRemove) {
                event = TrustProviderListEvent.remove(event, tag, ctx.signer)
            }

            val ack = ctx.publish(event, outbox)
            RawEventSupport.publishGuard(ack, event.id)?.let { return it }
            Output.emit(
                mapOf(
                    "provider" to provider,
                    "changed" to true,
                    "removed" to toRemove.map { mapOf("service" to it.service.toValue(), "relay" to it.relayUrl.url) },
                    "event_id" to event.id,
                    "based_on" to latest.id,
                    "published_to" to ack.filterValues { it }.keys.map { it.url },
                    "rejected_by" to ack.filterValues { !it }.keys.map { it.url },
                ),
            )
            return 0
        }
    }

    /**
     * `amy graperank providers [USER] [--refresh] [--timeout SECS]`
     *
     * List the NIP-85 trusted providers a user declares in their kind:10040
     * (default: the active account). Cache-first; falls back to a relay drain on
     * a miss or with `--refresh`. For the active account, private (NIP-44)
     * provider entries are decrypted and included too.
     */
    suspend fun providers(
        dataDir: DataDir,
        rest: Array<String>,
    ): Int {
        val args = Args(rest)
        val userArg = args.positionalOrNull(0)
        val refresh = args.bool("refresh")
        val timeoutMs = args.longFlag("timeout", 8L) * 1000
        args.rejectUnknown()

        Context.open(dataDir).use { ctx ->
            ctx.prepare()
            val user = userArg?.let { ctx.requireUserHex(it) } ?: ctx.identity.pubKeyHex
            val isSelf = user == ctx.identity.pubKeyHex

            var event = if (refresh) null else providerListOf(ctx, user)
            if (event == null) {
                ctx.drain(
                    (ctx.bootstrapRelays() + Constants.eventFinderRelays).associateWith {
                        listOf(Filter(kinds = listOf(TrustProviderListEvent.KIND), authors = listOf(user), limit = 1))
                    },
                    timeoutMs,
                )
                event = providerListOf(ctx, user)
            }

            if (event == null) {
                Output.emit(mapOf("user" to user, "found" to false, "providers" to emptyList<Any>()))
                return 0
            }

            val public = event.serviceProviders()
            val private = if (isSelf) event.privateTags(ctx.signer)?.serviceProviders().orEmpty() else emptyList()

            fun render(
                tag: ServiceProviderTag,
                scope: String,
            ) = mapOf(
                "service" to tag.service.toValue(),
                "provider" to tag.pubkey,
                "relay" to tag.relayUrl.url,
                "scope" to scope,
            )

            Output.emit(
                mapOf(
                    "user" to user,
                    "found" to true,
                    "event_id" to event.id,
                    "created_at" to event.createdAt,
                    "providers" to public.map { render(it, "public") } + private.map { render(it, "private") },
                ),
            )
            return 0
        }
    }
}
