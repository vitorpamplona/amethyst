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

import com.vitorpamplona.amethyst.cli.stores.FileKeyPackageBundleStore
import com.vitorpamplona.amethyst.cli.stores.FileMarmotMessageStore
import com.vitorpamplona.amethyst.cli.stores.FileMlsGroupStateStore
import com.vitorpamplona.amethyst.commons.defaults.DefaultDMRelayList
import com.vitorpamplona.amethyst.commons.defaults.DefaultNIP65RelaySet
import com.vitorpamplona.amethyst.commons.marmot.MarmotManager
import com.vitorpamplona.amethyst.commons.marmot.ingest
import com.vitorpamplona.quartz.marmot.MarmotFilters
import com.vitorpamplona.quartz.marmot.mip03GroupMessages.GroupEvent
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.relay.client.NostrClient
import com.vitorpamplona.quartz.nip01Core.relay.client.accessories.publishAndConfirmDetailed
import com.vitorpamplona.quartz.nip01Core.relay.client.reqs.SubscriptionListener
import com.vitorpamplona.quartz.nip01Core.relay.client.single.newSubId
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.relay.sockets.okhttp.BasicOkHttpWebSocket
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerInternal
import com.vitorpamplona.quartz.nip59Giftwrap.wraps.GiftWrapEvent
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
 */
class Context(
    val dataDir: DataDir,
    val identity: Identity,
    val relays: RelayConfig,
    val state: RunState,
) : AutoCloseable {
    val signer = NostrSignerInternal(identity.keyPair())

    private val okhttp = OkHttpClient.Builder().build()

    val client: NostrClient =
        NostrClient(
            websocketBuilder = BasicOkHttpWebSocket.Builder { okhttp },
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

    private val mlsStore = FileMlsGroupStateStore(dataDir.groupsDir)
    private val keyPackageStore = FileKeyPackageBundleStore(dataDir.keyPackageBundleFile)
    private val messageStore = FileMarmotMessageStore(dataDir.groupsDir)

    /** Fully-wired manager. Call [prepare] once before use to load persisted state. */
    val marmot: MarmotManager = MarmotManager(signer, mlsStore, messageStore, keyPackageStore)

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

    fun outboxRelays(): Set<NormalizedRelayUrl> = relays.normalized("nip65")

    fun inboxRelays(): Set<NormalizedRelayUrl> = relays.normalized("inbox")

    fun keyPackageRelays(): Set<NormalizedRelayUrl> = relays.normalized("key_package")

    fun anyRelays(): Set<NormalizedRelayUrl> = relays.normalized("all")

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
    fun bootstrapRelays(): Set<NormalizedRelayUrl> =
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
    ): List<Pair<NormalizedRelayUrl, Event>> {
        if (filters.isEmpty()) return emptyList()
        val eventChannel = Channel<Pair<NormalizedRelayUrl, Event>>(UNLIMITED)
        val doneChannel = Channel<NormalizedRelayUrl>(UNLIMITED)
        val remaining = filters.keys.toMutableSet()
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
                    doneChannel.trySend(relay)
                }

                override fun onClosed(
                    message: String,
                    relay: NormalizedRelayUrl,
                    forFilters: List<Filter>?,
                ) {
                    doneChannel.trySend(relay)
                }

                override fun onCannotConnect(
                    relay: NormalizedRelayUrl,
                    message: String,
                    forFilters: List<Filter>?,
                ) {
                    doneChannel.trySend(relay)
                }
            }
        val collected = mutableListOf<Pair<NormalizedRelayUrl, Event>>()
        try {
            client.subscribe(subId, filters, listener)
            withTimeoutOrNull(timeoutMs) {
                while (remaining.isNotEmpty()) {
                    select {
                        eventChannel.onReceive { collected.add(it) }
                        doneChannel.onReceive { r -> remaining.remove(r) }
                    }
                }
                // Drain any events that landed after EOSE but before cancel
                while (true) {
                    val r = eventChannel.tryReceive()
                    if (!r.isSuccess) break
                    collected.add(r.getOrThrow())
                }
            }
        } finally {
            client.unsubscribe(subId)
            eventChannel.close()
            doneChannel.close()
        }
        return collected
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
        try {
            client.close()
        } catch (_: Exception) {
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
                relays = dataDir.loadRelays(),
                state = dataDir.loadRunState(),
            )
        }
    }
}
