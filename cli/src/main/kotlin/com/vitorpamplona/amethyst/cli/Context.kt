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
package com.vitorpamplona.amethyst.cli

import com.vitorpamplona.amethyst.cli.stores.FileCashuKeysetCounterStore
import com.vitorpamplona.amethyst.cli.stores.FileKeyPackageBundleStore
import com.vitorpamplona.amethyst.cli.stores.FileMarmotMessageStore
import com.vitorpamplona.amethyst.cli.stores.FileMlsGroupStateStore
import com.vitorpamplona.amethyst.commons.cashu.CashuWalletReader
import com.vitorpamplona.amethyst.commons.cashu.ops.CashuWalletOps
import com.vitorpamplona.amethyst.commons.cashu.ops.RestoreOutcome
import com.vitorpamplona.amethyst.commons.defaults.DefaultDMRelayList
import com.vitorpamplona.amethyst.commons.defaults.DefaultNIP65RelaySet
import com.vitorpamplona.amethyst.commons.marmot.MarmotManager
import com.vitorpamplona.amethyst.commons.marmot.ingest
import com.vitorpamplona.quartz.marmot.MarmotFilters
import com.vitorpamplona.quartz.marmot.RecipientRelayFetcher
import com.vitorpamplona.quartz.marmot.mip00KeyPackages.KeyPackageRelayListEvent
import com.vitorpamplona.quartz.marmot.mip03GroupMessages.GroupEvent
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.hexToByteArray
import com.vitorpamplona.quartz.nip01Core.crypto.verify
import com.vitorpamplona.quartz.nip01Core.jackson.JacksonMapper
import com.vitorpamplona.quartz.nip01Core.metadata.MetadataEvent
import com.vitorpamplona.quartz.nip01Core.relay.client.NostrClient
import com.vitorpamplona.quartz.nip01Core.relay.client.accessories.publishAndConfirmDetailed
import com.vitorpamplona.quartz.nip01Core.relay.client.reqs.SubscriptionListener
import com.vitorpamplona.quartz.nip01Core.relay.client.single.newSubId
import com.vitorpamplona.quartz.nip01Core.relay.commands.toClient.CachingEventDecoder
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer
import com.vitorpamplona.quartz.nip01Core.relay.sockets.okhttp.BasicOkHttpWebSocket
import com.vitorpamplona.quartz.nip01Core.relay.sockets.okhttp.TcpNoDelaySocketFactory
import com.vitorpamplona.quartz.nip01Core.signers.NostrSigner
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerInternal
import com.vitorpamplona.quartz.nip01Core.store.IEventStore
import com.vitorpamplona.quartz.nip01Core.store.fs.FsEventStore
import com.vitorpamplona.quartz.nip02FollowList.ContactListEvent
import com.vitorpamplona.quartz.nip17Dm.settings.ChatMessageRelayListEvent
import com.vitorpamplona.quartz.nip46RemoteSigner.signer.NostrSignerRemote
import com.vitorpamplona.quartz.nip59Giftwrap.wraps.GiftWrapEvent
import com.vitorpamplona.quartz.nip60Cashu.history.CashuSpendingHistoryEvent
import com.vitorpamplona.quartz.nip60Cashu.mintApi.DeterministicSecretFactory
import com.vitorpamplona.quartz.nip60Cashu.quote.CashuMintQuoteEvent
import com.vitorpamplona.quartz.nip60Cashu.seed.CashuDeterministic
import com.vitorpamplona.quartz.nip60Cashu.token.CashuTokenEvent
import com.vitorpamplona.quartz.nip60Cashu.wallet.CashuWalletEvent
import com.vitorpamplona.quartz.nip61Nutzaps.info.NutzapInfoEvent
import com.vitorpamplona.quartz.nip61Nutzaps.nutzap.NutzapEvent
import com.vitorpamplona.quartz.nip65RelayList.AdvertisedRelayListEvent
import com.vitorpamplona.quartz.nip87Ecash.recommendation.MintRecommendationEvent
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.Channel.Factory.UNLIMITED
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient

/**
 * Per-invocation wiring. Each CLI run constructs a Context, does its work,
 * and then closes it — no daemon.
 *
 * Responsibilities:
 *  - load identity + relay + run-state from the data-dir,
 *  - wire up a [NostrClient] pointing at those relays,
 *  - wire up the [MarmotManager] pipeline with file-backed stores,
 *  - expose helpers that every command needs (sync, publish-and-confirm,
 *    process-incoming, etc).
 *
 * Closing flushes run-state to disk and disconnects the client.
 *
 * # Source of truth — [store]
 *
 * Every Nostr event Amy observes — whether received from a relay
 * subscription, unwrapped from a NIP-59 gift wrap, or generated locally
 * before publish — is verified (NIP-01 signature + id check via
 * [Event.verify]) and persisted to the file-backed [IEventStore] at
 * `<data-dir>/events-store/`. Malformed events are dropped before
 * reaching command code.
 *
 * This makes [store] the authoritative cache of everything Amy has ever
 * seen: profile metadata, relay lists, contact lists, gift wraps,
 * group events, etc. Persistence is best-effort — an I/O failure on
 * the store does not break the relay subscription.
 *
 * Reads should prefer the local store via the helpers below
 * ([profileOf], [relaysOf], [contactsOf]) and only fall back to a
 * relay [drain] on cache miss.
 */
class Context(
    val dataDir: DataDir,
    val identity: Identity,
    val state: RunState,
) : AutoCloseable {
    private val okhttp = OkHttpClient.Builder().socketFactory(TcpNoDelaySocketFactory).build()

    val client: NostrClient =
        NostrClient(
            websocketBuilder = BasicOkHttpWebSocket.Builder { okhttp },
            // Skips re-parsing EVENT frames that arrive again via another
            // subscription or relay.
            decoder = CachingEventDecoder(),
        )

    /**
     * The account's signer. For a local account this is a [NostrSignerInternal]
     * over the stored key; for a NIP-46 bunker account it is a
     * [NostrSignerRemote] that delegates signing/encryption to the remote
     * signer over [client]. The remote signer's subscription + connect
     * handshake are driven from [prepare].
     */
    val signer: NostrSigner =
        identity.bunker?.let { b ->
            NostrSignerRemote(
                signer = NostrSignerInternal(identity.clientKeyPair()),
                remotePubkey = b.remotePubkey,
                relays = b.relays.mapNotNull { RelayUrlNormalizer.normalizeOrNull(it) }.toSet(),
                client = client,
                secret = b.connectSecret,
                // Bunker requires web authorization: surface the URL; the request keeps waiting.
                onAuthUrl = { url -> System.err.println("[nip46] authorize this request in a browser, then it will continue:\n  $url") },
            )
        } ?: NostrSignerInternal(identity.keyPair())

    /**
     * NIP-05 resolver for turning `alice@damus.io`-style identifiers into pubkeys.
     * Uses the same OkHttp instance as the WebSocket client so we share connection
     * pools and TLS sessions.
     */
    val nip05Client: com.vitorpamplona.quartz.nip05DnsIdentifiers.Nip05Client =
        com.vitorpamplona.quartz.nip05DnsIdentifiers.Nip05Client(
            fetcher =
                com.vitorpamplona.quartz.nip05DnsIdentifiers
                    .OkHttpNip05Fetcher { _ -> okhttp },
        )

    private val mlsStore = FileMlsGroupStateStore(dataDir.groupsDir)
    private val keyPackageStore = FileKeyPackageBundleStore(dataDir.keyPackageBundleFile)
    private val messageStore = FileMarmotMessageStore(dataDir.groupsDir)

    /**
     * Filesystem-backed Nostr event store, rooted at [DataDir.eventsDir].
     * Lazy so commands that don't touch persistent event state pay zero
     * open cost (no `.lock` file, no seed allocation). Closed by
     * [close] when this Context shuts down.
     *
     * Files are written pretty-printed (not the compact NIP-01 canonical
     * form) so `cat`, `jq`, `git diff` are useful out of the box —
     * humans inspect these files. Verification always re-canonicalises,
     * so the stored bytes never feed back into a signature check.
     */
    private val storeDelegate: Lazy<IEventStore> =
        lazy {
            FsEventStore(
                root = dataDir.eventsDir.toPath(),
                eventToJson = JacksonMapper::toJsonPretty,
            )
        }
    val store: IEventStore by storeDelegate

    /** Fully-wired manager. Call [prepare] once before use to load persisted state. */
    val marmot: MarmotManager = MarmotManager(signer, mlsStore, messageStore, keyPackageStore)

    // ------------------------------------------------------------------
    // Cashu (NIP-60 / NIP-61) — shared wallet code from commons
    // ------------------------------------------------------------------

    /** Durable NUT-13 counter store at `<data-dir>/cashu.json`. */
    private val cashuCounters by lazy { FileCashuKeysetCounterStore(dataDir.cashuFile) }

    @Volatile private var cachedCashuSeed: ByteArray? = null

    /**
     * Decrypt the wallet's NUT-13 seed once per run and cache it. The
     * [DeterministicSecretFactory] thunk reads this synchronously, so any
     * mint/swap op must warm it first (CashuWalletOps' seedWarmer does).
     */
    private suspend fun warmCashuSeed() {
        if (cachedCashuSeed != null) return
        val priv = cashuSnapshot().walletEvent?.let { runCatching { it.privkey(signer) }.getOrNull() } ?: return
        cachedCashuSeed = CashuDeterministic.deriveWalletSeed(priv.hexToByteArray())
    }

    /**
     * Wallet operations driven by the exact same `commons` [CashuWalletOps]
     * the Android app uses. Wired to publish on the account's outbox relays,
     * the shared OkHttp instance for mint HTTP, and the file-backed NUT-13
     * counter store.
     */
    fun cashuOps(): CashuWalletOps =
        CashuWalletOps(
            signer = signer,
            // Amethyst publishes cashu events via sendLiterallyEverywhere
            // (all the user's relays). anyRelays() — outbox + inbox +
            // keypackage — is the CLI's closest analog, so the wallet lands
            // on the same broad relay set the app would use, not just outbox.
            publish = { event -> publish(event, anyRelays()) },
            okHttpClient = { okhttp },
            secretFactory =
                DeterministicSecretFactory(
                    seedProvider = { cachedCashuSeed },
                    reserveCounters = { keysetId, count -> cashuCounters.reserve(keysetId, count) },
                ),
            seedWarmer = { warmCashuSeed() },
            seedForRestore = {
                warmCashuSeed()
                cachedCashuSeed
            },
            peekCashuCounter = { keysetId -> cashuCounters.peek(keysetId) },
            reserveCashuCounters = { keysetId, count -> cashuCounters.reserve(keysetId, count) },
        )

    /**
     * Project this account's locally-stored NIP-60/61/87 events into a wallet
     * snapshot via the shared [CashuWalletReader] — the same decrypt +
     * del-rollover + pending-quote logic the Android holder runs. Reads the
     * cache only; commands that need fresh state should [drain] first.
     */
    suspend fun cashuSnapshot(): CashuWalletReader.WalletSnapshot {
        val pk = identity.pubKeyHex
        // Mirror commons' CashuWalletFilterAssembler exactly: authored wallet
        // kinds by authors=[pk], inbound nutzaps by #p — so amy projects the
        // same event set the Android app subscribes to.
        val authored =
            store.query<Event>(
                Filter(
                    authors = listOf(pk),
                    kinds =
                        listOf(
                            CashuWalletEvent.KIND,
                            CashuTokenEvent.KIND,
                            CashuSpendingHistoryEvent.KIND,
                            CashuMintQuoteEvent.KIND,
                            NutzapInfoEvent.KIND,
                            MintRecommendationEvent.KIND,
                        ),
                ),
            )
        val inboundNutzaps =
            store.query<Event>(
                Filter(kinds = listOf(NutzapEvent.KIND), tags = mapOf("p" to listOf(pk))),
            )
        return CashuWalletReader(signer).project(authored + inboundNutzaps)
    }

    /** Warm and return the wallet's NUT-13 seed, or null if no wallet. */
    suspend fun cashuSeed(): ByteArray? {
        warmCashuSeed()
        return cachedCashuSeed
    }

    /**
     * NUT-09 restore for one mint, mirroring Android's
     * `CashuWalletState.restoreFromMint`: re-derive proofs from the seed
     * (skipping secrets we already hold), then bump the persisted NUT-13
     * counter past every slot the scan confirmed in use so later mints can't
     * reuse one. Returns null when the wallet has no seed yet.
     */
    suspend fun cashuRestore(mintUrl: String): RestoreOutcome? {
        val seed = cashuSeed() ?: return null
        val existingSecrets =
            cashuSnapshot()
                .tokenEntries
                .flatMap { it.content.proofs }
                .mapTo(HashSet()) { it.secret }
        val outcome = cashuOps().restoreFromMint(mintUrl = mintUrl, seed = seed, startCounter = 0L, existingSecrets = existingSecrets)
        val delta = (outcome.nextCounterAfterScan - cashuCounters.peek(outcome.keysetId)).coerceAtLeast(0L)
        if (delta > 0) cashuCounters.reserve(outcome.keysetId, delta.toInt())
        return outcome
    }

    private var prepared = false

    /**
     * Hydrate MarmotManager from disk (groups + KeyPackage bundles) and
     * connect to relays. Safe to call multiple times — subsequent calls are
     * no-ops.
     */
    suspend fun prepare() {
        if (prepared) return
        marmot.restoreAll()
        client.connect()
        // A bunker account must open its NIP-46 response subscription and run
        // the connect handshake before any signing/encryption call.
        (signer as? NostrSignerRemote)?.let {
            it.openSubscription()
            it.connect()
        }
        prepared = true
    }

    /**
     * Resolve `npub…` / `nprofile…` / 64-hex / `name@domain.tld` to a pubkey hex.
     * Delegates to the shared [resolveUserHexOrNull] in quartz so the UI and CLI
     * accept the exact same identifier formats. Throws on unrecognised input —
     * command handlers catch [IllegalArgumentException] at the top level and
     * translate to `{"error": "bad_args"}`.
     */
    suspend fun requireUserHex(input: String): com.vitorpamplona.quartz.nip01Core.core.HexKey =
        com.vitorpamplona.quartz.nip05DnsIdentifiers
            .resolveUserHexOrNull(input, nip05Client)
            ?: throw IllegalArgumentException("Could not resolve user: '$input' (accepts npub, nprofile, 64-hex, or name@domain.tld)")

    /**
     * Outbox / NIP-65 write relays for this account. Read from the
     * local kind:10002 (after `amy create` or `amy relay publish-lists`
     * has written one); falls back to [DefaultNIP65RelaySet] when no
     * advertised list exists yet.
     *
     * Returns *write*-marked URLs only — the semantic is "where I
     * publish from", which mirrors `User.outboxRelays()` in the
     * Android app.
     */
    suspend fun outboxRelays(): Set<NormalizedRelayUrl> =
        relaysOf(identity.pubKeyHex)?.writeRelaysNorm()?.takeIf { it.isNotEmpty() }?.toSet()
            ?: DefaultNIP65RelaySet

    /**
     * NIP-65 *read* (inbox) relays for this account — "where others reach me".
     * Mirrors the Android app's NIP-65 inbox set (the `notificationRelays`
     * flow). Used as the default `relay` tags advertised in a kind:10019
     * nutzap-info event. Falls back to [outboxRelays] when no read relays are
     * marked.
     */
    suspend fun nip65ReadRelays(): Set<NormalizedRelayUrl> =
        relaysOf(identity.pubKeyHex)?.readRelaysNorm()?.takeIf { it.isNotEmpty() }?.toSet()
            ?: outboxRelays()

    /**
     * DM inbox relays (NIP-17 kind:10050) for this account. Falls back
     * to [DefaultDMRelayList] when no kind:10050 has been seen.
     */
    suspend fun inboxRelays(): Set<NormalizedRelayUrl> =
        dmInboxOf(identity.pubKeyHex)?.relays()?.takeIf { it.isNotEmpty() }?.toSet()
            ?: DefaultDMRelayList.toSet()

    /**
     * KeyPackage relays (MIP-00 kind:10051) for this account. Falls
     * back to [outboxRelays] when no kind:10051 has been seen — same
     * fallback the Android app uses for KeyPackage discovery.
     */
    suspend fun keyPackageRelays(): Set<NormalizedRelayUrl> =
        keyPackageRelaysOf(identity.pubKeyHex)?.relays()?.takeIf { it.isNotEmpty() }?.toSet()
            ?: outboxRelays()

    /** Union of all three buckets. */
    suspend fun anyRelays(): Set<NormalizedRelayUrl> = outboxRelays() + inboxRelays() + keyPackageRelays()

    /**
     * Seed relays for "look up someone we know nothing about" queries —
     * fetching another user's kind:10002 / 10050 / 10051 / 30443 before we
     * can deliver something to them.
     *
     * Strategy: union our own configured relays with Amethyst's hard-coded
     * defaults (DefaultNIP65RelaySet + DefaultDMRelayList). The defaults are
     * what every fresh Amethyst account publishes to first, so they're the
     * most reliable place to find a stranger's replaceable events even when
     * we and they have completely disjoint relay configurations.
     */
    suspend fun bootstrapRelays(): Set<NormalizedRelayUrl> =
        buildSet {
            addAll(anyRelays())
            addAll(DefaultNIP65RelaySet)
            addAll(DefaultDMRelayList)
        }

    /**
     * Publish an event to the given relays and wait for OK confirmations.
     *
     * Returns the set of relays that ACK'd `true`. Does not throw on rejection —
     * callers inspect the map and decide.
     */
    suspend fun publish(
        event: Event,
        relayList: Set<NormalizedRelayUrl>,
        timeoutSecs: Long = 15,
    ): Map<NormalizedRelayUrl, Boolean> {
        // Persist locally before broadcasting. The store is the source of
        // truth — even if every relay rejects, we want our own outbound
        // event in the local cache.
        verifyAndStore(event)
        if (relayList.isEmpty()) return emptyMap()
        return client.publishAndConfirmDetailed(event, relayList, timeoutSecs)
    }

    /**
     * Subscribe to the given filters across the given relays, drain all events
     * until either every relay has sent EOSE or the timeout elapses, and
     * return them. Used for one-shot catch-up queries — not live subscriptions.
     */
    suspend fun drain(
        filters: Map<NormalizedRelayUrl, List<Filter>>,
        timeoutMs: Long = 8_000,
        diagnoseSlow: Boolean = false,
    ): List<Pair<NormalizedRelayUrl, Event>> {
        if (filters.isEmpty()) return emptyList()
        val eventChannel = Channel<Pair<NormalizedRelayUrl, Event>>(UNLIMITED)
        // Carries the terminal reason per relay so a timeout can distinguish a slow
        // relay (never terminal) from a connect failure / CLOSED.
        val doneChannel = Channel<Pair<NormalizedRelayUrl, String>>(UNLIMITED)
        val remaining = filters.keys.toMutableSet()
        val doneReasons = HashMap<NormalizedRelayUrl, String>()
        val subId = newSubId()
        val listener =
            object : SubscriptionListener {
                override fun onEvent(
                    event: Event,
                    isLive: Boolean,
                    relay: NormalizedRelayUrl,
                    forFilters: List<Filter>?,
                ) {
                    eventChannel.trySend(relay to event)
                }

                override fun onEose(
                    relay: NormalizedRelayUrl,
                    forFilters: List<Filter>?,
                ) {
                    doneChannel.trySend(relay to "eose")
                }

                override fun onClosed(
                    message: String,
                    relay: NormalizedRelayUrl,
                    forFilters: List<Filter>?,
                ) {
                    doneChannel.trySend(relay to "closed:$message")
                }

                override fun onCannotConnect(
                    relay: NormalizedRelayUrl,
                    message: String,
                    forFilters: List<Filter>?,
                ) {
                    doneChannel.trySend(relay to "cannot:$message")
                }
            }
        val collected = mutableListOf<Pair<NormalizedRelayUrl, Event>>()
        try {
            client.subscribe(subId, filters, listener)
            val completed =
                withTimeoutOrNull(timeoutMs) {
                    while (remaining.isNotEmpty()) {
                        select {
                            eventChannel.onReceive { pair ->
                                if (verifyAndStore(pair.second)) collected.add(pair)
                            }
                            doneChannel.onReceive { (relay, reason) ->
                                remaining.remove(relay)
                                doneReasons[relay] = reason
                            }
                        }
                    }
                    // Drain any events that landed after EOSE but before cancel
                    while (true) {
                        val r = eventChannel.tryReceive()
                        if (!r.isSuccess) break
                        val pair = r.getOrThrow()
                        if (verifyAndStore(pair.second)) collected.add(pair)
                    }
                    true
                }
            if (diagnoseSlow && completed == null && remaining.isNotEmpty()) {
                logSlowDrain(timeoutMs, remaining, doneReasons, collected)
            }
        } finally {
            client.unsubscribe(subId)
            eventChannel.close()
            doneChannel.close()
        }
        return collected
    }

    /**
     * On a [drain] timeout, report which relays stalled and why — a relay that
     * never sent EOSE (slow, possibly still streaming) vs one that couldn't be
     * reached (CANNOT-CONNECT, which points at our side / the network) vs one
     * that CLOSED the sub. Includes how many events each slow relay did send, so
     * "relay is slow" and "we never connected" are easy to tell apart.
     */
    private fun logSlowDrain(
        timeoutMs: Long,
        stalled: Set<NormalizedRelayUrl>,
        doneReasons: Map<NormalizedRelayUrl, String>,
        collected: List<Pair<NormalizedRelayUrl, Event>>,
    ) {
        val eventsPer = collected.groupingBy { it.first }.eachCount()
        val cannot = doneReasons.filterValues { it.startsWith("cannot") }
        val closed = doneReasons.filterValues { it.startsWith("closed") }
        val slowDetail = stalled.take(12).joinToString(", ") { "${it.url}(${eventsPer[it] ?: 0}ev)" }
        val cannotDetail = cannot.entries.take(8).joinToString(", ") { "${it.key.url}=${it.value.removePrefix("cannot:").take(40)}" }
        System.err.println(
            "[drain] timeout ${timeoutMs}ms: ${stalled.size} slow(no EOSE), ${cannot.size} cannot-connect, ${closed.size} closed" +
                (if (slowDetail.isNotEmpty()) " | slow: $slowDetail" else "") +
                (if (cannotDetail.isNotEmpty()) " | cannot: $cannotDetail" else ""),
        )
    }

    /**
     * Publish [request] to [relays], then wait for the FIRST event matching [responseFilter]
     * — a live reply that arrives after our own EOSE, which [drain] would miss (it returns at
     * EOSE). Verifies and stores the reply. Returns it, or null on timeout; always tears the
     * subscription down. Used for request/response round-trips (e.g. a CLINK offer invoice).
     */
    suspend fun requestResponse(
        request: Event,
        relays: Set<NormalizedRelayUrl>,
        responseFilter: Filter,
        timeoutMs: Long = 15_000,
    ): Event? {
        if (relays.isEmpty()) return null
        val reply = CompletableDeferred<Event>()
        val subId = newSubId()
        val filters = relays.associateWith { listOf(responseFilter) }
        val listener =
            object : SubscriptionListener {
                override fun onEvent(
                    event: Event,
                    isLive: Boolean,
                    relay: NormalizedRelayUrl,
                    forFilters: List<Filter>?,
                ) {
                    if (!reply.isCompleted) reply.complete(event)
                }
            }
        client.subscribe(subId, filters, listener)
        return try {
            publish(request, relays)
            val event = withTimeoutOrNull(timeoutMs) { reply.await() } ?: return null
            if (verifyAndStore(event)) event else null
        } finally {
            client.unsubscribe(subId)
        }
    }

    /**
     * Verify [event]'s NIP-01 id+signature and, if valid, persist it
     * to [store]. Returns `true` when the event was accepted (and
     * therefore should be surfaced to callers). Persistence failures
     * (I/O errors, full disk) are logged but do not propagate.
     *
     * Every event-arrival path in the CLI funnels through this method
     * so that [store] is the authoritative cache of what Amy has seen.
     */
    suspend fun verifyAndStore(event: Event): Boolean {
        if (!event.verify()) {
            System.err.println("[cli] dropped event ${event.id.take(8)} kind=${event.kind} — bad signature")
            return false
        }
        try {
            store.insert(event)
        } catch (t: Throwable) {
            System.err.println("[cli] store insert failed for ${event.id.take(8)}: ${t.message}")
        }
        return true
    }

    // ------------------------------------------------------------------
    // Cache-first reads from [store]
    // ------------------------------------------------------------------

    /**
     * Latest known kind:0 metadata for [pubKey], read from the local
     * store. Returns null if Amy has never observed a profile for
     * this user. Callers that need a network fetch on miss should fall
     * back to [drain] explicitly — this helper never hits the network.
     */
    suspend fun profileOf(pubKey: HexKey): MetadataEvent? =
        store
            .query<Event>(
                Filter(authors = listOf(pubKey), kinds = listOf(MetadataEvent.KIND), limit = 1),
            ).firstOrNull() as? MetadataEvent

    /**
     * Latest known kind:10002 advertised relay list (NIP-65) for
     * [pubKey]. `null` when Amy has never seen one.
     */
    suspend fun relaysOf(pubKey: HexKey): AdvertisedRelayListEvent? =
        store
            .query<Event>(
                Filter(authors = listOf(pubKey), kinds = listOf(AdvertisedRelayListEvent.KIND), limit = 1),
            ).firstOrNull() as? AdvertisedRelayListEvent

    /**
     * Latest known kind:3 contact list (NIP-02) for [pubKey], or
     * `null` if Amy has never observed one. Useful for follow-graph
     * lookups without re-hitting relays.
     */
    suspend fun contactsOf(pubKey: HexKey): ContactListEvent? =
        store
            .query<Event>(
                Filter(authors = listOf(pubKey), kinds = listOf(ContactListEvent.KIND), limit = 1),
            ).firstOrNull() as? ContactListEvent

    /**
     * Latest known kind:10050 chat-message (NIP-17 DM) inbox relay list
     * for [pubKey], or `null` if Amy has never observed one. Used by
     * `dm send` to resolve where to deliver a wrap.
     */
    suspend fun dmInboxOf(pubKey: HexKey): ChatMessageRelayListEvent? =
        store
            .query<Event>(
                Filter(authors = listOf(pubKey), kinds = listOf(ChatMessageRelayListEvent.KIND), limit = 1),
            ).firstOrNull() as? ChatMessageRelayListEvent

    /**
     * Latest known kind:10051 KeyPackage relay list (MIP-00) for
     * [pubKey], or `null` if Amy has never observed one. Used by
     * `marmot key-package check` and `marmot await key-package` to
     * locate where the recipient publishes their KeyPackages.
     */
    suspend fun keyPackageRelaysOf(pubKey: HexKey): KeyPackageRelayListEvent? =
        store
            .query<Event>(
                Filter(authors = listOf(pubKey), kinds = listOf(KeyPackageRelayListEvent.KIND), limit = 1),
            ).firstOrNull() as? KeyPackageRelayListEvent

    /**
     * Latest known replaceable event of [kind] authored by [pubKey] in the
     * local store, or `null`. Generic sibling of [relaysOf] / [dmInboxOf] /
     * [keyPackageRelaysOf] used by the relay-settings commands to load any of
     * the NIP-51 relay lists (blocked / search / proxy / indexer / …) without
     * a dedicated typed helper for each kind.
     */
    suspend fun latestReplaceable(
        pubKey: HexKey,
        kind: Int,
    ): Event? =
        store
            .query<Event>(Filter(authors = listOf(pubKey), kinds = listOf(kind), limit = 1))
            .firstOrNull()

    /**
     * Assemble a [RecipientRelayFetcher.Lists] from the local store —
     * the same shape callers get from [RecipientRelayFetcher.fetchRelayLists]
     * after a network drain, but no network round-trip. Returns `null`
     * only when the cache has *no* relay-list events at all for
     * [pubKey] (none of kind 10050 / 10051 / 10002), so callers can
     * trivially fall back to the network fetcher with `?:`.
     *
     * Stale-data caveat: replaceable events are immutable per snapshot
     * — if the recipient has rotated their inbox since we last saw them,
     * we'll still hand back the old list. Commands that care can drain
     * (which re-populates the cache) or expose a `--refresh` flag.
     */
    suspend fun cachedRelayListsOf(pubKey: HexKey): RecipientRelayFetcher.Lists? {
        val dm = dmInboxOf(pubKey)
        val kp = keyPackageRelaysOf(pubKey)
        val nip65 = relaysOf(pubKey)
        if (dm == null && kp == null && nip65 == null) return null
        return RecipientRelayFetcher.Lists(
            dmInbox = dm?.relays().orEmpty(),
            keyPackage = kp?.relays().orEmpty(),
            nip65 = nip65,
        )
    }

    /**
     * Pull down everything needed to bring local Marmot state current:
     *  - kind:1059 gift wraps on inbox relays → try to unwrap Welcomes
     *  - kind:445 group events per active group → feed into inbound processor
     *
     * Incrementally advances the `since` cursors in [state] so the next run
     * only asks relays for newer events. Two wrinkles:
     *
     *  1. NIP-59 gift wraps are published with a random-past `created_at`
     *     (see [com.vitorpamplona.quartz.utils.TimeUtils.randomWithTwoDays])
     *     so a newly-published wrap can trivially have `created_at` earlier
     *     than the last cursor we saw. To avoid silently dropping such wraps
     *     we always subtract a 2-day lookback window from the gift-wrap
     *     `since`, and dedup is handled inside [MarmotInboundProcessor].
     *  2. We only advance the on-disk cursor when events actually arrive.
     *     Snapping an empty sync up to "now" on the first invocation would
     *     make every later `since` query skip any past-dated wrap or 445.
     */
    suspend fun syncIncoming(timeoutMs: Long = 8_000) {
        val inbox = inboxRelays().ifEmpty { anyRelays() }
        val gwSince = state.giftWrapSince
        val gwFilterSince =
            gwSince?.let { (it - GIFT_WRAP_LOOKBACK_SECS).coerceAtLeast(0L) }
        val gwFilter =
            if (gwFilterSince != null) {
                MarmotFilters.giftWrapsForUserSince(identity.pubKeyHex, gwFilterSince)
            } else {
                MarmotFilters.giftWrapsForUser(identity.pubKeyHex)
            }

        val activeGroupIds = marmot.subscriptionManager.activeGroupIdsSnapshot().toList()
        val perGroupFilters: Map<HexKey, Filter> =
            activeGroupIds.associateWith { gid ->
                val since = state.groupSince[gid]
                if (since != null) {
                    MarmotFilters.groupEventsByGroupIdSince(gid, since)
                } else {
                    MarmotFilters.groupEventsByGroupId(gid)
                }
            }

        // Group filters go to each group's configured relays, not the user's
        // inbox — kind:445 is delivered to the group's relay set advertised in
        // its MIP-01 metadata (falls back to our outbox if the group never
        // stamped any).
        val filterMap = mutableMapOf<NormalizedRelayUrl, MutableList<Filter>>()
        for (r in inbox) filterMap.getOrPut(r) { mutableListOf() }.add(gwFilter)
        for ((gid, filter) in perGroupFilters) {
            val groupRelays = marmotGroupRelays(gid).ifEmpty { outboxRelays() }
            for (r in groupRelays) filterMap.getOrPut(r) { mutableListOf() }.add(filter)
        }
        if (filterMap.isEmpty()) return

        val events = drain(filterMap, timeoutMs)

        var maxGwSeen = gwSince ?: 0L
        val maxGroupSeen = perGroupFilters.keys.associateWith { state.groupSince[it] ?: 0L }.toMutableMap()
        var sawGiftWrap = false
        val sawGroupEvent = mutableSetOf<HexKey>()

        for ((relay, event) in events) {
            // All the MLS/NIP-59 decryption + persistence lives in MarmotIngest —
            // we only care about bookkeeping (since-cursors, logging) here.
            val result = marmot.ingest(event)
            val detail =
                when (result) {
                    is com.vitorpamplona.amethyst.commons.marmot.MarmotIngestResult.Failure -> " ${result.message}"
                    else -> ""
                }
            System.err.println("[cli] ingest ${event.kind}/${event.id.take(8)} via $relay → ${result::class.simpleName}$detail")

            when (event.kind) {
                GiftWrapEvent.KIND -> {
                    sawGiftWrap = true
                    if (event.createdAt > maxGwSeen) maxGwSeen = event.createdAt
                }

                GroupEvent.KIND -> {
                    val gid = (event as? GroupEvent)?.groupId() ?: continue
                    sawGroupEvent.add(gid)
                    val prev = maxGroupSeen[gid] ?: 0L
                    if (event.createdAt > prev) maxGroupSeen[gid] = event.createdAt
                }
            }
        }

        if (sawGiftWrap && maxGwSeen > 0) {
            state.giftWrapSince = maxGwSeen
        }
        for (gid in sawGroupEvent) {
            val seen = maxGroupSeen[gid] ?: continue
            if (seen > 0) state.groupSince[gid] = seen
        }

        // If any welcome we processed consumed a KeyPackage, MIP-00 requires
        // us to immediately publish a replacement (a KP can only be used for
        // ONE welcome; leaving the old one on relays lets a second sender
        // invite us with a bundle we no longer have private keys for). The
        // Amethyst UI handles this via its own rotation scheduler; the CLI
        // has no scheduler, so we rotate inline right after sync.
        if (marmot.needsKeyPackageRotation()) {
            try {
                val kpRelays = keyPackageRelays().ifEmpty { outboxRelays() }.ifEmpty { anyRelays() }
                if (kpRelays.isNotEmpty()) {
                    val rotated = marmot.rotateConsumedKeyPackages(kpRelays.toList())
                    for (event in rotated) {
                        publish(event, kpRelays)
                        System.err.println("[cli] rotated KeyPackage → ${event.id.take(8)} on ${kpRelays.size} relay(s)")
                    }
                }
            } catch (e: Exception) {
                System.err.println("[cli] key-package rotation failed: ${e.message}")
            }
        }
    }

    /**
     * Resolve a group identifier given on the CLI to the nostr_group_id that
     * amy's [MarmotManager] indexes on.
     *
     * amy internally keys everything off MIP-01's `nostr_group_id`. whitenoise
     * (and every other mdk consumer) keys off the MLS `GroupContext.groupId` —
     * a separate 32-byte random value stamped at group creation. Cross-client
     * scripts therefore wind up juggling both ids, and it's very easy to pass
     * the wrong one to amy. Rather than make every caller translate, we accept
     * either format and resolve here:
     *  1. If the input is an active nostr_group_id, use it unchanged.
     *  2. Otherwise scan active groups for one whose MLS groupId matches.
     *  3. Otherwise return the input unchanged (so the caller still gets a
     *     sensible `not_member` response rather than a silent mismatch).
     */
    fun resolveGroupId(input: HexKey): HexKey {
        if (marmot.isMember(input)) return input
        val normalized = input.lowercase()
        return marmot.activeGroupIds().firstOrNull { nostrId ->
            marmot.mlsGroupIdHex(nostrId)?.lowercase() == normalized
        } ?: input
    }

    fun marmotGroupRelays(nostrGroupId: HexKey): Set<NormalizedRelayUrl> {
        val m = marmot.groupMetadata(nostrGroupId) ?: return emptySet()
        return m.relays
            .mapNotNull {
                com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer
                    .normalizeOrNull(it)
            }.toSet()
    }

    override fun close() {
        dataDir.saveRunState(state)
        (signer as? NostrSignerRemote)?.let {
            try {
                it.closeSubscription()
            } catch (_: Exception) {
            }
        }
        try {
            client.close()
        } catch (_: Exception) {
        }
        // Only close the store if it was actually opened — by-lazy
        // otherwise allocates the lock channel just to release it.
        if (storeDelegate.isInitialized()) {
            try {
                storeDelegate.value.close()
            } catch (_: Exception) {
            }
        }
    }

    companion object {
        /**
         * Lookback applied to the gift-wrap `since` filter to compensate for
         * NIP-59's randomised-past `created_at`. 2 days matches
         * [com.vitorpamplona.quartz.utils.TimeUtils.randomWithTwoDays].
         */
        private const val GIFT_WRAP_LOOKBACK_SECS: Long = 2L * 24 * 60 * 60

        /** Build a Context but require an identity to already exist — most commands can't run without one. */
        fun open(dataDir: DataDir): Context {
            val identity =
                dataDir.loadIdentityOrNull()
                    ?: run {
                        System.err.println("No identity found at ${dataDir.identityFile}. Run `amethyst-cli init` first.")
                        throw IllegalStateException("no identity")
                    }
            return Context(
                dataDir = dataDir,
                identity = identity,
                state = dataDir.loadRunState(),
            )
        }
    }
}
