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

import com.sun.management.UnixOperatingSystemMXBean
import com.vitorpamplona.amethyst.cli.stores.FileKeyPackageBundleStore
import com.vitorpamplona.amethyst.cli.stores.FileMarmotMessageStore
import com.vitorpamplona.amethyst.cli.stores.FileMlsGroupStateStore
import com.vitorpamplona.amethyst.commons.cashu.CashuWalletReader
import com.vitorpamplona.amethyst.commons.cashu.ops.CashuWalletOps
import com.vitorpamplona.amethyst.commons.cashu.ops.RestoreOutcome
import com.vitorpamplona.amethyst.commons.defaults.DefaultDMRelayList
import com.vitorpamplona.amethyst.commons.defaults.DefaultNIP65RelaySet
import com.vitorpamplona.amethyst.commons.marmot.MarmotManager
import com.vitorpamplona.amethyst.commons.marmot.MarmotSyncPolicy
import com.vitorpamplona.quartz.marmot.RecipientRelayFetcher
import com.vitorpamplona.quartz.marmot.mip00KeyPackages.KeyPackageRelayListEvent
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.metadata.MetadataEvent
import com.vitorpamplona.quartz.nip01Core.relay.client.NostrClient
import com.vitorpamplona.quartz.nip01Core.relay.client.accessories.AdaptiveRelayLimiter
import com.vitorpamplona.quartz.nip01Core.relay.client.accessories.DrainFailure
import com.vitorpamplona.quartz.nip01Core.relay.client.accessories.PublishResult
import com.vitorpamplona.quartz.nip01Core.relay.client.accessories.fetchAllPagesFromPoolWithHooks
import com.vitorpamplona.quartz.nip01Core.relay.client.accessories.fetchAllWithHooks
import com.vitorpamplona.quartz.nip01Core.relay.client.accessories.publishAndCollectResults
import com.vitorpamplona.quartz.nip01Core.relay.client.auth.RelayAuthenticator
import com.vitorpamplona.quartz.nip01Core.relay.client.reqs.SubscriptionListener
import com.vitorpamplona.quartz.nip01Core.relay.client.single.newSubId
import com.vitorpamplona.quartz.nip01Core.relay.commands.toClient.CachingEventDecoder
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.toHttp
import com.vitorpamplona.quartz.nip01Core.relay.sockets.okhttp.BasicOkHttpWebSocket
import com.vitorpamplona.quartz.nip01Core.relay.sockets.okhttp.SurgeDns
import com.vitorpamplona.quartz.nip01Core.relay.sockets.okhttp.SurgeDnsStore
import com.vitorpamplona.quartz.nip01Core.relay.sockets.okhttp.TcpNoDelaySocketFactory
import com.vitorpamplona.quartz.nip01Core.signers.NostrSigner
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerInternal
import com.vitorpamplona.quartz.nip01Core.store.IEventStore
import com.vitorpamplona.quartz.nip01Core.store.verifyAndInsert
import com.vitorpamplona.quartz.nip02FollowList.ContactListEvent
import com.vitorpamplona.quartz.nip05DnsIdentifiers.resolveUserHexOrNull
import com.vitorpamplona.quartz.nip11RelayInfo.Nip11RelayInformation
import com.vitorpamplona.quartz.nip17Dm.settings.ChatMessageRelayListEvent
import com.vitorpamplona.quartz.nip46RemoteSigner.signer.NostrSignerRemote
import com.vitorpamplona.quartz.nip65RelayList.AdvertisedRelayListEvent
import com.vitorpamplona.quartz.nip66RelayMonitor.reachability.RelayReachabilityStore
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.Dispatcher
import okhttp3.OkHttpClient
import okhttp3.Request
import java.lang.management.ManagementFactory
import java.util.concurrent.TimeUnit

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
 * [Event.verify]) and persisted to the shared [IEventStore] under
 * `<data-dir>/shared/` (a SQLite DB by default, or the FS tree when
 * `AMY_STORE=fs` — see [StoreFactory]). Malformed events are dropped
 * before reaching command code.
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
    /**
     * Anonymous read-only run: no account on disk, [identity] is an ephemeral
     * key-less identity (see [Identity.anonymous]). Marmot state is not
     * restored and run-state is not persisted — the run only reads relays and
     * the shared event store. Signing verbs never take this path; they go
     * through [Companion.open], which requires a real account.
     */
    val anonymous: Boolean = false,
) : AutoCloseable {
    // Shared resolver — the SAME SurgeDns the Android app runs (stale-while-revalidate,
    // single-flight, jittered 24-48h positive TTL, 10-min negative TTL), persisted under
    // `<data-dir>/shared/` so repeat crawls start with the whole relay universe pre-resolved:
    // every previously-seen host serves its cached answer instantly and re-verifies in the
    // background instead of paying a blocking getaddrinfo per host.
    private val surgeDns = SurgeDns()
    private val dnsStore =
        SurgeDnsStore(dataDir.dnsCacheFile, surgeDns).also {
            // Best-effort warm start (~25KB read); a corrupt/missing blob just means a cold cache.
            runCatching { it.load() }
        }

    // Internal (not private) so [CashuContext] can reuse the shared instance
    // for mint HTTP.
    internal val okhttp =
        OkHttpClient
            .Builder()
            .socketFactory(TcpNoDelaySocketFactory)
            // The crawl opens WebSockets to thousands of relays. Each WS-upgrade
            // handshake is an async call through OkHttp's shared Dispatcher, whose
            // default cap (maxRequests=64) throttles the connection ramp — worse,
            // a dead relay holds a slot for the whole connectTimeout, starving live
            // relays queued behind it. Widen the dispatcher so handshakes fan out,
            // and keep connectTimeout tight-ish so an unreachable relay frees its
            // slot fast. This is orthogonal to REQ concurrency (that runs on
            // already-open sockets, bounded by AdaptiveRelayLimiter), so it can't
            // trip a relay's REQ rate-limit — it only speeds connection setup. The
            // executor thread pool is unbounded on demand, so raising maxRequests
            // just lets more of those short-lived handshakes proceed at once
            // (1 platform thread per in-flight handshake — ~1k is fine on a JVM,
            // 10k is not). Sized against the process FD budget: every pending
            // handshake and every open socket is one file descriptor. 7s
            // (not 5s): a 5s cap struck too many merely-busy relays as connect
            // failures — the crawl treats a connect *timeout* as retryable anyway,
            // but the extra headroom lets slow-but-alive relays finish the handshake.
            .connectTimeout(7, TimeUnit.SECONDS)
            // DNS dominates a mass connection ramp without help: Dns.SYSTEM blocks a
            // dispatcher thread per lookup, the JVM's own cache lasts ~30s (useless
            // over a 30-minute crawl), a dead domain burns 10-30s of resolver
            // timeouts on EVERY re-dial, and the outbox model mints hundreds of
            // per-user URLs on one host — each a fresh lookup. SurgeDns (shared with
            // the Android app) collapses all of it: single-flight per host,
            // stale-while-revalidate positives, 10-min negative TTL, persisted
            // across runs via [dnsStore].
            .dns(surgeDns)
            .dispatcher(
                Dispatcher().apply {
                    maxRequests = maxParallelHandshakes
                    maxRequestsPerHost = 16
                },
            ).build()

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
            ).also {
                // signer.pubKey must be the USER identity, not the ephemeral transport key, so
                // self-encryption/decryption (Concord list, private NIP-51 lists) uses the right peer.
                it.bindUserPubkey(identity.pubKeyHex)
            }
        } ?: NostrSignerInternal(identity.keyPair())

    /**
     * Client-wide tally of relay feedback — NOTICE frames, CLOSED reasons
     * (auth-required / rate-limited / restricted / …), and NIP-42 AUTH
     * challenges — so a failed REQ can be explained instead of guessed at.
     * Registered on [client] for the life of this run.
     */
    val relayDiagnostics: RelayDiagnostics = RelayDiagnostics().also { client.addConnectionListener(it) }

    /**
     * Adaptive per-relay concurrent-subscription cap. Starts every relay
     * generous (100) and demotes only the ones that complain about concurrency
     * (100 → 20 → 10), driven straight off the NOTICE/CLOSED frames it observes
     * as a connection listener. [drain]'s `gatePerRelay` path holds a relay's
     * permit for the life of that relay's subscription, so we never exceed the
     * cap the relay itself asked for. Idle for commands that don't opt in.
     */
    val relayLimiter: AdaptiveRelayLimiter =
        AdaptiveRelayLimiter(
            // The starting per-relay concurrent-sub cap dominates whether the crawl
            // floods a popular relay into timing out. Benchmarked: 16 is ~30% faster
            // on a from-scratch GrapeRank crawl than the old 100 (which drowned
            // damus/nos.lol in 100 concurrent giant REQs) at equal completeness, and
            // is still generous for the single-user fetches other amy commands do.
            // AMY_RELAY_SUB_CAP overrides for experiments — the crawl's Phase-B
            // throughput plateaus on hot-relay permit queues, and the 16-vs-100
            // benchmark predates the multithreaded-crawl fix, so the sweet spot may
            // sit higher; relays that complain still get demoted down the ladder.
            startCap = System.getenv("AMY_RELAY_SUB_CAP")?.toIntOrNull()?.coerceIn(1, 100) ?: 16,
        ).also { client.addConnectionListener(it) }

    /**
     * Concord plane stream-key AUTH registry (CORD-01 §4b) — see [ConcordAuth].
     * `amy concord` verbs register their stream secrets via
     * [registerConcordStreamKeys] before draining, and [relayAuth] answers a
     * challenge from one of those relays with one kind-22242 per stream key.
     */
    private val concordAuth = ConcordAuth()

    /** Registers raw 32-byte Concord stream [secrets] to answer NIP-42 challenges from [relays]. */
    fun registerConcordStreamKeys(
        relays: Set<NormalizedRelayUrl>,
        secrets: List<ByteArray>,
    ) = concordAuth.register(relays, secrets)

    /**
     * NIP-42 responder: answers a relay's AUTH challenge by signing with the
     * account key, so auth-gated relays serve our reads instead of CLOSing the
     * subscription. Constructing it registers its own listener on [client].
     * Only a local key auto-signs — a remote bunker signer is skipped, since a
     * per-relay remote round-trip during a crawl would stall it (and signing an
     * auth event with any key still unlocks relays that just want *some* auth).
     * Any Concord stream keys registered via [registerConcordStreamKeys] for the
     * challenging relay are signed alongside the account AUTH.
     */
    private val relayAuth: RelayAuthenticator =
        RelayAuthenticator(
            client = client,
            signWithAllLoggedInUsers = { relay, template, _ ->
                val accountAuth =
                    if (signer is NostrSignerInternal) {
                        runCatching { listOf(signer.sign(template)) }.getOrElse { emptyList() }
                    } else {
                        emptyList()
                    }
                accountAuth + concordAuth.signAuths(relay, template)
            },
        )

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

    /**
     * Fetches [relay]'s NIP-11 relay-information document (over the same OkHttp instance), or null if
     * it serves none / the request fails. Used to read the relay's `self` pubkey — NIP-29's authority
     * for group metadata — so `relaygroup` reads can verify a 39000 was actually signed by the relay.
     */
    suspend fun relayInfo(relay: NormalizedRelayUrl): Nip11RelayInformation? =
        withContext(Dispatchers.IO) {
            runCatching {
                val request =
                    Request
                        .Builder()
                        .url(relay.toHttp())
                        .header("Accept", "application/nostr+json")
                        .build()
                okhttp.newCall(request).execute().use { resp ->
                    resp.body.string().let { Nip11RelayInformation.fromJson(it) }
                }
            }.getOrNull()
        }

    // Lazy so an anonymous read (no account dir) never materialises the
    // per-account marmot stores — constructing them would `mkdir` group dirs
    // under the shared root. Real accounts build them on first marmot use.
    private val mlsStore by lazy { FileMlsGroupStateStore(dataDir.groupsDir) }
    private val keyPackageStore by lazy { FileKeyPackageBundleStore(dataDir.keyPackageBundleFile) }
    private val messageStore by lazy { FileMarmotMessageStore(dataDir.groupsDir) }

    /**
     * Shared Nostr event store for this run, opened via [StoreFactory]
     * (SQLite by default, or the FS tree when `AMY_STORE=fs`). Lazy so
     * commands that don't touch persistent event state pay zero open cost
     * (no DB file / `.lock`, no seed allocation). Closed by [close] when
     * this Context shuts down.
     */
    private val storeDelegate: Lazy<IEventStore> = lazy { StoreFactory.open(dataDir) }
    val store: IEventStore by storeDelegate

    /**
     * Shared relay-reachability cache (NIP-66 kind:30166 records in [store]), signed by
     * the machine's dedicated monitor key — derived from the operator master, NOT the
     * account (see [OperatorKeys.monitorKey]). The crawler and the WoT updater read its
     * dead set to skip proven-dead relays and write their findings back, so liveness
     * knowledge is shared across procedures and runs instead of rediscovered each time.
     * Lazy so a run that never touches relays doesn't materialize the operator master.
     */
    val reachability: RelayReachabilityStore by lazy {
        RelayReachabilityStore(
            store = store,
            signer = NostrSignerInternal(dataDir.operatorKeys().monitorKey()),
        )
    }

    /** Fully-wired manager. Call [prepare] once before use to load persisted state. */
    val marmot: MarmotManager by lazy { MarmotManager(signer, mlsStore, messageStore, keyPackageStore) }

    // ------------------------------------------------------------------
    // Cashu (NIP-60 / NIP-61) — shared wallet code from commons
    // ------------------------------------------------------------------

    /**
     * Cashu wiring (seed warming, snapshot projection, NUT-09 restore) —
     * see [CashuContext]. Lazy so a run that never touches the wallet pays
     * nothing. The `cashuOps` / `cashuSnapshot` / `cashuSeed` / `cashuRestore`
     * members below forward to it, keeping the command surface unchanged.
     */
    val cashu: CashuContext by lazy { CashuContext(this) }

    /** See [CashuContext.ops]. */
    fun cashuOps(): CashuWalletOps = cashu.ops()

    /** See [CashuContext.snapshot]. */
    suspend fun cashuSnapshot(): CashuWalletReader.WalletSnapshot = cashu.snapshot()

    /** See [CashuContext.seed]. */
    suspend fun cashuSeed(): ByteArray? = cashu.seed()

    /** See [CashuContext.restore]. */
    suspend fun cashuRestore(mintUrl: String): RestoreOutcome? = cashu.restore(mintUrl)

    private var prepared = false

    /**
     * Hydrate MarmotManager from disk (groups + KeyPackage bundles) and
     * connect to relays. Safe to call multiple times — subsequent calls are
     * no-ops.
     */
    suspend fun prepare() {
        if (prepared) return
        // Anonymous runs have no account and therefore no marmot state to
        // restore (and touching `marmot` would allocate the per-account stores).
        if (!anonymous) marmot.restoreAll()
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
     * Per-account alias map, read once per invocation. Alias lookups only
     * apply to short name-shaped inputs: an npub/nprofile/NIP-05/64-hex input
     * always resolves as itself, so an alias can never silently shadow a real
     * identifier (`amy dm send bob@damus.io …` reaches the actual NIP-05 owner
     * even if a local account happens to carry that name).
     */
    private val aliases by lazy { Aliases.load(dataDir) }

    private fun aliasFor(input: String): String? {
        val nameShaped = input.length in 1..64 && input.all { it.isLetterOrDigit() || it == '_' || it == '-' }
        val hexShaped = input.length == 64 && input.all { it.isDigit() || it in 'a'..'f' || it in 'A'..'F' }
        if (!nameShaped || hexShaped) return null
        return aliases[input]
    }

    /**
     * Resolve an alias (per-account `aliases.json`) / `npub…` / `nprofile…` /
     * 64-hex / `name@domain.tld` to a pubkey hex. Aliases are checked first —
     * they are local, unambiguous names the user chose (`amy dm send bob "hi"`)
     * — then the input delegates to the shared [resolveUserHexOrNull] in quartz
     * so the UI and CLI accept the exact same identifier formats. Throws on
     * unrecognised input — command handlers catch [IllegalArgumentException] at
     * the top level and translate to `{"error": "bad_args"}`.
     *
     * A pubkey is always 32 bytes, so we require exactly 64 hex chars: the shared
     * resolver's fallback runs a lenient `Hex.decode` that turns a short
     * bech32/hex-ish word (e.g. a mistyped verb like `sync`) into a bogus few-byte
     * "pubkey" instead of failing — this rejects that so a bad OBSERVER/USER errors
     * cleanly rather than silently scoring/fetching garbage.
     */
    suspend fun requireUserHex(input: String): HexKey {
        val notResolved = "Could not resolve user: '$input' (accepts an alias, npub, nprofile, 64-hex, or name@domain.tld)"
        val resolvable = aliasFor(input) ?: input
        val hex =
            resolveUserHexOrNull(resolvable, nip05Client)
                ?: throw IllegalArgumentException(notResolved)
        require(hex.length == 64) { notResolved }
        return hex
    }

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
     * Index relays — the shared, app-global set used to fetch profile
     * metadata (kind 0) and follow lists (kind 3). Mirrors the Desktop
     * app's `LocalRelayCategories.indexRelays` by reading from the same
     * `java.util.prefs` node
     * (`com/vitorpamplona/amethyst/relays/index`). Falls back to the
     * shipping defaults when the user hasn't configured anything.
     *
     * This is what `amy wot sync` uses; `outboxRelays()` /
     * `inboxRelays()` remain for callers that want relay lists derived
     * from NIP-65 identity semantics.
     */
    fun indexRelays(): Set<NormalizedRelayUrl> =
        com.vitorpamplona.amethyst.commons.relays.index
            .PreferencesIndexRelays()
            .effective()

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
    ): Map<NormalizedRelayUrl, PublishResult> {
        // Persist locally before broadcasting. The store is the source of
        // truth — even if every relay rejects, we want our own outbound
        // event in the local cache.
        verifyAndStore(event)
        if (relayList.isEmpty()) return emptyMap()

        var results = client.publishAndCollectResults(event, relayList, timeoutSecs)

        // NIP-42 write path: an auth-required relay rejects the FIRST publish with
        // `auth-required`. The publish opens a connection, races the async AUTH reply, and
        // tears down before it lands — and [relayAuth] only re-auths on a REQ `CLOSED`, never
        // on a rejected EVENT `OK`. The READ path, though, holds the connection open and its
        // `auth-required:` CLOSED reliably drives a re-auth (`reauthenticateIfAuthRequired`),
        // leaving the pooled connection authenticated. So on `auth-required` we warm auth with
        // a tiny `pendingOnAuthRequired` REQ, then retry the publish on the now-authed socket.
        var attempt = 0
        while (attempt < 4) {
            val needAuth =
                results
                    .filterValues { !it.accepted && it.message.contains("auth-required", ignoreCase = true) }
                    .keys
            if (needAuth.isEmpty()) break
            // A cheap REQ whose only purpose is to force the AUTH handshake to completion.
            val warmFilter = listOf(Filter(kinds = listOf(event.kind), limit = 1))
            drain(needAuth.associateWith { warmFilter }, timeoutMs = 8_000, pendingOnAuthRequired = true)
            results = results + client.publishAndCollectResults(event, needAuth, timeoutSecs)
            attempt++
        }
        return results
    }

    /**
     * Subscribe to the given filters across the given relays, drain all events
     * until either every relay has sent EOSE or the line has been silent for
     * the timeout (an idle window — progress resets it), and
     * return them. Used for one-shot catch-up queries — not live subscriptions.
     * Thin adapter over the shared [fetchAllWithHooks] accessory: every arriving
     * event is verified + persisted via [verifyAndStore] before it is surfaced.
     *
     * When [deadOut] is provided, every relay that reported it could not be
     * connected to (`onCannotConnect`) is added to it, so callers can prune
     * proven-dead relays from future routing instead of paying the full
     * [timeoutMs] on them again. Slow-but-connected relays are NOT reported —
     * only hard connect failures, so a temporarily-busy relay isn't discarded.
     *
     * With [pendingOnAuthRequired], a relay that refuses the REQ with an
     * `auth-required` CLOSED is kept pending rather than treated as terminal: the
     * NIP-42 responder answers the challenge and the client re-fires this same
     * subscription (`syncFilters`), so the post-auth events are collected instead of
     * returning empty. If auth never satisfies it, the relay simply falls through to
     * the [timeoutMs]. Needed for Concord planes, whose kind-1059 wraps are served
     * only to a connection authenticated as the derived stream key.
     */
    suspend fun drain(
        filters: Map<NormalizedRelayUrl, List<Filter>>,
        timeoutMs: Long = 8_000,
        diagnoseSlow: Boolean = false,
        deadOut: MutableMap<NormalizedRelayUrl, DrainFailure>? = null,
        pendingOnAuthRequired: Boolean = false,
    ): List<Pair<NormalizedRelayUrl, Event>> =
        client.fetchAllWithHooks(
            filters = filters,
            timeoutMs = timeoutMs,
            pendingOnAuthRequired = pendingOnAuthRequired,
            deadOut = deadOut,
            onTimeout =
                if (diagnoseSlow) {
                    { stalled, doneReasons, collected -> logSlowDrain(timeoutMs, stalled, doneReasons, collected) }
                } else {
                    null
                },
        ) { _, event -> verifyAndStore(event) }

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
     * Like [drain], but paginates every relay to completion via
     * [fetchAllPagesFromPoolWithHooks] instead of stopping at the first EOSE — so a
     * query larger than a relay's per-`REQ` cap (strfry's `limit`, ~500) is fully
     * retrieved instead of silently truncated. Each relay is walked on its own
     * `until` cursor, up to [maxConcurrentRelays] at once, and every event funnels
     * through [verifyAndStore]; the result is tagged by the relay that first
     * delivered it. Unlike [drain], it IS deduped across relays: the same
     * widely-mirrored event arrives once per relay, and the repeats are dropped by
     * the accessory's `SeenIds` filter BEFORE the expensive verify+store — an id is
     * marked seen only after it verifies, so a forged copy (valid id, bad signature)
     * delivered first can't suppress the genuine one from another relay.
     *
     * Bound the work with the filters' `limit`: each relay pages until it reaches
     * the limit, so an unbounded filter pages that relay's entire matching history.
     * A `search` filter is fetched as a single relevance-ranked page (see
     * [com.vitorpamplona.quartz.nip01Core.relay.client.accessories.fetchAllPages]).
     */
    suspend fun drainAllPages(
        filters: Map<NormalizedRelayUrl, List<Filter>>,
        timeoutMs: Long = 30_000,
        maxConcurrentRelays: Int = 8,
    ): List<Pair<NormalizedRelayUrl, Event>> =
        client.fetchAllPagesFromPoolWithHooks(
            filters = filters,
            timeoutMs = timeoutMs,
            maxConcurrentRelays = maxConcurrentRelays,
        ) { _, event -> verifyAndStore(event) }

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
     * Verify [event]'s NIP-01 id+signature and, if valid, persist it to [store].
     * Returns `true` when the event was accepted (and therefore should be surfaced
     * to callers). Persistence failures (I/O errors, full disk) are logged but do
     * not propagate; a UNIQUE-constraint rejection is normal and swallowed quietly.
     *
     * Every event-arrival path in the CLI funnels through this so that [store] is
     * the authoritative cache of what Amy has seen. Delegates to the shared quartz
     * [verifyAndInsert] sink so the CLI and the GrapeRank crawler apply the exact
     * same verify-then-store policy.
     */
    suspend fun verifyAndStore(event: Event): Boolean = store.verifyAndInsert(event)

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
     * The shared Marmot catch-up policy (gift-wrap 2-day lookback, per-group
     * `since` cursors, MIP-00 consumed-KeyPackage rotation), wired to the
     * CLI's pieces: [drain] for the one-shot subscription, [RunState] for the
     * persisted cursors, and [publish] for the rotation replacements. The
     * policy itself lives in `commons` ([MarmotSyncPolicy]) so the CLI and the
     * Android app apply identical rules. Lazy so an anonymous run never
     * materialises [marmot] (which would allocate the per-account stores).
     */
    private val marmotSyncPolicy: MarmotSyncPolicy by lazy {
        MarmotSyncPolicy(
            marmot = marmot,
            userPubKey = identity.pubKeyHex,
            cursors =
                object : MarmotSyncPolicy.Cursors {
                    override var giftWrapSince: Long?
                        get() = state.giftWrapSince
                        set(value) {
                            state.giftWrapSince = value
                        }

                    override fun groupSince(groupId: HexKey): Long? = state.groupSince[groupId]

                    override fun setGroupSince(
                        groupId: HexKey,
                        value: Long,
                    ) {
                        state.groupSince[groupId] = value
                    }
                },
            relays =
                object : MarmotSyncPolicy.Relays {
                    override suspend fun inboxRelays() = this@Context.inboxRelays()

                    override suspend fun outboxRelays() = this@Context.outboxRelays()

                    override suspend fun keyPackageRelays() = this@Context.keyPackageRelays()

                    override suspend fun anyRelays() = this@Context.anyRelays()

                    override fun groupRelays(nostrGroupId: HexKey) = marmotGroupRelays(nostrGroupId)
                },
            drain = { filters, timeoutMs -> drain(filters, timeoutMs) },
            publish = { event, relayList -> publish(event, relayList) },
            log = { System.err.println("[cli] $it") },
        )
    }

    /**
     * Pull down everything needed to bring local Marmot state current —
     * kind:1059 gift wraps and kind:445 group events — advancing the `since`
     * cursors in [state] and rotating consumed KeyPackages. All policy
     * (lookback windows, cursor advancement rules, MIP-00 rotation) lives in
     * the shared [MarmotSyncPolicy]; see its docs for the reasoning.
     */
    suspend fun syncIncoming(timeoutMs: Long = 8_000) = marmotSyncPolicy.syncIncoming(timeoutMs)

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
        // Nothing to persist for an anonymous run (no account dir to write into).
        if (!anonymous) dataDir.saveRunState(state)
        // Persist the DNS cache (shared dir, account-independent) so the next run's
        // connection storm starts with every known host pre-resolved. Best-effort:
        // a failed save must never break the run it is summarizing.
        runCatching { dnsStore.save() }
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
        /** FDs reserved for everything that isn't a relay socket (store, jars, pipes, DNS). */
        private const val FD_RESERVE = 256L

        /**
         * The process's max-open-files limit (`ulimit -n`), the hard ceiling on
         * concurrent sockets: every open WebSocket AND every in-flight handshake is
         * one file descriptor, and the JVM cannot raise its own rlimit. Falls back
         * to the conservative 1024 (the common soft default) when the platform bean
         * doesn't expose it.
         */
        val maxFileDescriptors: Long =
            (ManagementFactory.getOperatingSystemMXBean() as? UnixOperatingSystemMXBean)
                ?.maxFileDescriptorCount ?: 1024L

        /**
         * Concurrent WS-upgrade handshakes (OkHttp Dispatcher.maxRequests): a quarter
         * of the FD budget, so pending dials can never crowd out the sockets already
         * held open (warm pool, drains, parked subs). Also bounds the transient
         * platform threads OkHttp spawns — one per in-flight handshake.
         */
        val maxParallelHandshakes: Int =
            ((maxFileDescriptors - FD_RESERVE) / 4).coerceIn(64, 1024).toInt()

        /**
         * Default cap for the crawl's mass pre-connect (warm pool): half the FD
         * budget goes to held-open relay sockets, leaving the other half for
         * in-flight handshakes, drain/parked subscriptions and headroom. At the
         * common 1024-FD soft limit this is ~384; `ulimit -n 16384` unlocks the
         * full 4000. Overridable per-run with --preconnect-cap.
         */
        val defaultPreconnectCap: Int =
            ((maxFileDescriptors - FD_RESERVE) / 2).coerceIn(100, 4000).toInt()

        /**
         * Build a Context but require an account with a usable identity —
         * signing verbs can't run without one. Throws [IllegalArgumentException]
         * (→ exit 2) when no account was resolvable, carrying the "which
         * account?" hint from [DataDir.resolveOptional]; throws
         * [IllegalStateException] when the account exists but has no identity.
         */
        fun open(dataDir: DataDir): Context {
            require(dataDir.hasAccount) {
                dataDir.noAccountDetail ?: "no account selected; pass --account <name> or run `amy use <name>`"
            }
            val identity =
                dataDir.loadIdentityOrNull()
                    ?: run {
                        System.err.println("No identity found at ${dataDir.identityFile}. Run `amy --account ${dataDir.accountName} init` first.")
                        throw IllegalStateException("no identity")
                    }
            return Context(
                dataDir = dataDir,
                identity = identity,
                state = dataDir.loadRunState(),
            )
        }

        /**
         * Context for read-only verbs: use the resolved account when one is
         * present, otherwise run anonymously (ephemeral key-less identity, no
         * persisted state). Lets `fetch`/`subscribe`/`count`/`publish`/`outbox`/
         * … query relays and the shared store with no account on disk — they
         * read fine, they just can't sign.
         */
        fun openOrAnonymous(dataDir: DataDir): Context =
            if (dataDir.hasAccount && dataDir.identityExists()) {
                open(dataDir)
            } else {
                Context(
                    dataDir = dataDir,
                    identity = Identity.anonymous(),
                    state = RunState(),
                    anonymous = true,
                )
            }
    }
}
