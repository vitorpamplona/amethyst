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
package com.vitorpamplona.geode

import com.vitorpamplona.geode.persistence.BannedEntry
import com.vitorpamplona.geode.persistence.RelayPersistedState
import com.vitorpamplona.geode.persistence.RelayStateStore
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.OptimizedJsonMapper
import com.vitorpamplona.quartz.nip01Core.relay.commands.toRelay.EventCmd
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.relay.server.IRelayPolicy
import com.vitorpamplona.quartz.nip01Core.relay.server.NostrServer
import com.vitorpamplona.quartz.nip01Core.relay.server.policies.EmptyPolicy
import com.vitorpamplona.quartz.nip01Core.store.IEventStore
import com.vitorpamplona.quartz.nip01Core.store.sqlite.EventStore
import com.vitorpamplona.quartz.nip11RelayInfo.Nip11RelayInformation
import com.vitorpamplona.quartz.nip86RelayManagement.server.BanListPolicy
import com.vitorpamplona.quartz.nip86RelayManagement.server.BanStore
import kotlinx.coroutines.SupervisorJob
import java.io.File
import kotlin.coroutines.CoroutineContext

/**
 * A self-contained Nostr relay scoped to a single URL. Wraps a [NostrServer]
 * over an [EventStore] (defaults to an in-memory SQLite database).
 *
 * Speaks NIP-01 (REQ/EVENT/EOSE/CLOSE), NIP-11 (relay info via [info]),
 * NIP-42 (AUTH — supply [policyBuilder] = `{ FullAuthPolicy(url) }` or
 * stack one with [com.vitorpamplona.quartz.nip01Core.relay.server.IRelayPolicy.plus]),
 * NIP-45 (COUNT) and NIP-50 (search via the SQLite FTS index).
 *
 * Two transports:
 *   - [com.vitorpamplona.quartz.nip01Core.relay.server.inprocess.InProcessWebSocket] /
 *     [RelayHub] — no socket, fastest path, ideal
 *     for unit tests inside one JVM.
 *   - [LocalRelayServer] — Ktor `embeddedServer` listening on a real port.
 *     Use when external clients need to connect (`cli`, instrumented tests,
 *     standalone deployment).
 */
class Relay(
    val url: NormalizedRelayUrl,
    val store: IEventStore = EventStore(dbName = null, relay = url),
    info: RelayInfo = RelayInfo.default(url),
    policyBuilder: () -> IRelayPolicy = { EmptyPolicy },
    parentContext: CoroutineContext = SupervisorJob(),
    /**
     * Optional path for the operator-state JSON snapshot. When set,
     * the file is loaded at boot to seed [info] and [banStore], and
     * rewritten atomically on every NIP-86 mutation and
     * [updateInfo] call so admin actions survive restarts.
     *
     * Convention: place next to the SQLite event-store file
     * (e.g. `events.db` → `events.db.admin.json`). `null` keeps
     * everything in memory only — fine for tests.
     */
    stateFile: File? = null,
    /**
     * Run Schnorr signature verification in parallel inside the
     * [com.vitorpamplona.quartz.nip01Core.relay.server.IngestQueue]
     * instead of serially in the policy chain. Enables the Tier-3
     * win in `geode/plans/2026-05-07-event-ingestion-batching.md`.
     *
     * When set, callers MUST omit `VerifyPolicy` from [policyBuilder]
     * — having both verifies the same event twice for no benefit.
     * `Main.kt` skips `VerifyPolicy` when this flag is on.
     */
    parallelVerify: Boolean = false,
) : AutoCloseable {
    private val stateStore: RelayStateStore? = stateFile?.let { RelayStateStore(it) }

    /**
     * NIP-11 doc. Mutable so NIP-86 admin RPCs (`changerelayname`,
     * `changerelaydescription`, `changerelayicon`) can swap the doc
     * atomically. Readers (the NIP-11 GET endpoint) re-read on every
     * request so changes are visible immediately, no restart needed.
     *
     * If a [RelayStateStore] is configured and the snapshot exists,
     * the persisted info doc takes precedence over the constructor
     * default — operators expect their last `changerelayname` to
     * survive a restart.
     */
    @Volatile
    var info: RelayInfo =
        stateStore?.load()?.info?.let { RelayInfo(it) } ?: info
        private set

    /** Mutates the live NIP-11 doc. Called by [Nip86Server]. */
    fun updateInfo(transform: (Nip11RelayInformation) -> Nip11RelayInformation) {
        info = RelayInfo(transform(info.document))
        snapshot()
    }

    /**
     * Runtime-mutable ban / allow lists. NIP-86 RPC handlers in
     * [Nip86Server] mutate this; the policy stack consults it on
     * every accept call via [BanListPolicy].
     */
    val banStore: BanStore = BanStore(onMutation = { snapshot() })

    init {
        // Seed the in-memory ban state from disk *without* triggering
        // [snapshot] on every entry — the snapshot is exactly what we
        // just loaded.
        stateStore?.load()?.let { snap ->
            banStore.seedFromSnapshot(
                bannedPubkeys = snap.bannedPubkeys.map { it.key to it.reason },
                allowedPubkeys = snap.allowedPubkeys.map { it.key to it.reason },
                bannedEvents = snap.bannedEvents.map { it.key to it.reason },
                allowedKinds = snap.allowedKinds,
                disallowedKinds = snap.disallowedKinds,
            )
        }
    }

    /**
     * Writes the current state (NIP-11 doc + ban lists) to disk.
     * No-op when no `stateFile` was configured.
     *
     * Best-effort: any I/O failure is logged to stderr and swallowed
     * so an unwritable disk doesn't take the relay down. Operators
     * monitor for missing snapshots out-of-band.
     */
    fun snapshot() {
        val s = stateStore ?: return
        runCatching {
            s.save(
                RelayPersistedState(
                    info = info.document,
                    bannedPubkeys = banStore.listBannedPubkeys().map { (k, r) -> BannedEntry(k, r) },
                    allowedPubkeys = banStore.listAllowedPubkeys().map { (k, r) -> BannedEntry(k, r) },
                    bannedEvents = banStore.listBannedEvents().map { (k, r) -> BannedEntry(k, r) },
                    allowedKinds = banStore.listAllowedKinds(),
                    disallowedKinds = banStore.listDisallowedKinds(),
                ),
            )
        }.onFailure {
            System.err.println("warning: failed to write relay state file: ${it.message}")
        }
    }

    val server =
        NostrServer(
            store,
            // Always prepend a BanListPolicy so NIP-86 admin actions
            // bite. When the operator-supplied builder returns
            // [EmptyPolicy] we use the dynamic policy alone; otherwise
            // we stack them so both layers must accept.
            policyBuilder = {
                val user = policyBuilder()
                if (user === EmptyPolicy) BanListPolicy(banStore) else user + BanListPolicy(banStore)
            },
            parentContext,
            parallelVerify = parallelVerify,
        )

    /**
     * Inserts events directly into the underlying store, bypassing the wire protocol.
     *
     * Use this for **pre-test setup** — events that exist before any client connects.
     * It does NOT broadcast to active subscriptions. For sending events that should
     * fan out to live subscribers (post-EOSE), use [publish] instead.
     */
    suspend fun preload(events: Iterable<Event>) {
        events.forEach { store.insert(it) }
    }

    /** @see preload(Iterable) */
    suspend fun preload(vararg events: Event) = preload(events.toList())

    /**
     * Publishes an event through the relay's session machinery so it both lands
     * in the store and fans out to active subscriptions matching its filters
     * (mirrors what a real client would do via an `EVENT` command).
     */
    suspend fun publish(event: Event) {
        val session = server.connect { /* ignore OK echo */ }
        try {
            session.receive(OptimizedJsonMapper.toJson(EventCmd(event)))
        } finally {
            session.close()
        }
    }

    override fun close() = server.close()
}
