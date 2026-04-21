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
import com.vitorpamplona.amethyst.commons.marmot.MarmotManager
import com.vitorpamplona.quartz.marmot.MarmotFilters
import com.vitorpamplona.quartz.marmot.mip02Welcome.WelcomeEvent
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

    fun outboxRelays(): Set<NormalizedRelayUrl> = relays.normalized("nip65")

    fun inboxRelays(): Set<NormalizedRelayUrl> = relays.normalized("inbox")

    fun keyPackageRelays(): Set<NormalizedRelayUrl> = relays.normalized("key_package")

    fun anyRelays(): Set<NormalizedRelayUrl> = relays.normalized("all")

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
     * only asks relays for newer events.
     */
    suspend fun syncIncoming(timeoutMs: Long = 8_000) {
        val inbox = inboxRelays().ifEmpty { anyRelays() }
        val gwSince = state.giftWrapSince
        val gwFilter =
            if (gwSince != null) {
                MarmotFilters.giftWrapsForUserSince(identity.pubKeyHex, gwSince)
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
        val now = System.currentTimeMillis() / 1000

        var maxGwSeen = gwSince ?: 0L
        val maxGroupSeen = perGroupFilters.keys.associateWith { state.groupSince[it] ?: 0L }.toMutableMap()

        for ((relay, event) in events) {
            when (event.kind) {
                GiftWrapEvent.KIND -> {
                    // kind:1059 — try to unwrap. If it's a Welcome, let the
                    // inbound processor apply it; anything else is ignored.
                    val gw = event as? GiftWrapEvent ?: continue
                    try {
                        val inner = gw.unwrapOrNull(signer) ?: continue
                        if (inner.kind == WelcomeEvent.KIND && inner is WelcomeEvent) {
                            val hint = inner.nostrGroupId()
                            val res = marmot.processWelcome(inner, hint)
                            System.err.println("[cli] Welcome via $relay → $res")
                        }
                    } catch (e: Exception) {
                        System.err.println("[cli] failed to unwrap giftwrap ${event.id.take(8)}: ${e.message}")
                    }
                    if (event.createdAt > maxGwSeen) maxGwSeen = event.createdAt
                }

                GroupEvent.KIND -> {
                    val ge = event as? GroupEvent ?: continue
                    val gid = ge.groupId() ?: continue
                    try {
                        val res = marmot.processGroupEvent(ge)
                        // Mirror DecryptAndIndexProcessor on Amethyst: application
                        // messages get persisted at decrypt-time, because MLS ratchet
                        // advancement makes the ciphertext un-re-decryptable later.
                        if (res is com.vitorpamplona.quartz.marmot.GroupEventResult.ApplicationMessage) {
                            marmot.persistDecryptedMessage(res.groupId, res.innerEventJson)
                        }
                        System.err.println("[cli] GroupEvent ${event.id.take(8)} → ${res::class.simpleName}")
                    } catch (e: Exception) {
                        System.err.println("[cli] processGroupEvent failed: ${e.message}")
                    }
                    val prev = maxGroupSeen[gid] ?: 0L
                    if (event.createdAt > prev) maxGroupSeen[gid] = event.createdAt
                }
            }
        }

        state.giftWrapSince = if (maxGwSeen > 0) maxGwSeen else now
        for ((gid, seen) in maxGroupSeen) {
            state.groupSince[gid] = if (seen > 0) seen else now
        }
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
