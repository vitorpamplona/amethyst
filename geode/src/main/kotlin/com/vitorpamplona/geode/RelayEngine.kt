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

import com.vitorpamplona.geode.config.BannedEntry
import com.vitorpamplona.geode.config.RuntimeConfig
import com.vitorpamplona.geode.config.RuntimeConfigData
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.relay.server.IRelayPolicy
import com.vitorpamplona.quartz.nip01Core.relay.server.NostrServer
import com.vitorpamplona.quartz.nip01Core.relay.server.policies.EmptyPolicy
import com.vitorpamplona.quartz.nip01Core.store.IEventStore
import com.vitorpamplona.quartz.nip01Core.store.sqlite.EventStore
import com.vitorpamplona.quartz.nip11RelayInfo.Nip11RelayInformation
import com.vitorpamplona.quartz.nip77Negentropy.NegentropySettings
import com.vitorpamplona.quartz.nip86RelayManagement.server.BanListPolicy
import com.vitorpamplona.quartz.nip86RelayManagement.server.BanStore
import com.vitorpamplona.quartz.nip86RelayManagement.server.Nip86Server
import kotlinx.coroutines.SupervisorJob
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
 *     [InProcessRelays] — no socket, fastest path, ideal
 *     for unit tests inside one JVM.
 *   - [KtorRelay] — Ktor `embeddedServer` listening on a real port.
 *     Use when external clients need to connect (`cli`, instrumented tests,
 *     standalone deployment).
 */
class RelayEngine(
    val url: NormalizedRelayUrl,
    val store: IEventStore = EventStore(dbName = null, relay = url),
    /**
     * Runtime configuration handle — owns the persistence path (when
     * any), the NIP-11 doc seed, and the seed for the NIP-86 ban /
     * allow / kind lists. Mirrors how [store] is built externally and
     * passed in: tests use the in-memory default
     * (`RuntimeConfig(file=null, seed=...)`); `Main.kt` builds one
     * from `StaticConfig` so first-boot defaults flow from TOML and
     * subsequent admin mutations are persisted next to the SQLite
     * event store.
     *
     * The default constructs an in-memory-only handle whose seed
     * advertises the supported NIPs via [RelayInfo.default].
     */
    private val runtimeConfig: RuntimeConfig =
        RuntimeConfig(
            file = null,
            seed = RuntimeConfigData(info = RelayInfo.default(url).document),
        ),
    policyBuilder: () -> IRelayPolicy = { EmptyPolicy },
    parentContext: CoroutineContext = SupervisorJob(),
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
    /**
     * NIP-77 server-side tuning (frame cap, snapshot cap,
     * per-connection session cap). Defaults to strfry-parity values.
     */
    negentropySettings: NegentropySettings = NegentropySettings.Default,
    /**
     * Pubkeys allowed to invoke NIP-86 admin RPCs. Empty (the default)
     * effectively disables the admin API: every dispatch returns
     * `not authorized`. Transports (e.g. [KtorRelay] over HTTP) read
     * [nip86Server] and bind it to their wire protocol — the engine
     * owns *who* is admin; the transport owns *how* admins authenticate.
     */
    adminPubkeys: Set<HexKey> = emptySet(),
) : AutoCloseable {
    /**
     * The runtime state to install at boot: the persisted snapshot if
     * the file exists, otherwise the seed baked into [runtimeConfig].
     * Read once here so the `info` property initializer and the
     * [banStore] seeding in [init] agree on the same source.
     */
    private val effectiveAtBoot: RuntimeConfigData = runtimeConfig.effective()

    /**
     * NIP-11 doc. Mutable so NIP-86 admin RPCs (`changerelayname`,
     * `changerelaydescription`, `changerelayicon`) can swap the doc
     * atomically. Readers (the NIP-11 GET endpoint) re-read on every
     * request so changes are visible immediately, no restart needed.
     *
     * Initialized from [effectiveAtBoot] — a persisted admin override
     * wins, otherwise we fall back to the seed baked into
     * [runtimeConfig]. The `!!` is load-bearing: the seed is always
     * built with a non-null info, so the only way we reach a null
     * here is a manually corrupted state file, which we'd rather
     * crash on than serve empty NIP-11 from.
     */
    @Volatile
    var info: RelayInfo = RelayInfo(effectiveAtBoot.info!!)
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
        // Seed the in-memory ban state from [effectiveAtBoot] *without*
        // firing [snapshot] on every entry — the snapshot is exactly
        // what we just loaded (or, on first boot, the static seed we
        // just synthesized).
        banStore.seedFromSnapshot(
            bannedPubkeys = effectiveAtBoot.bannedPubkeys.map { it.key to it.reason },
            allowedPubkeys = effectiveAtBoot.allowedPubkeys.map { it.key to it.reason },
            bannedEvents = effectiveAtBoot.bannedEvents.map { it.key to it.reason },
            allowedKinds = effectiveAtBoot.allowedKinds,
            disallowedKinds = effectiveAtBoot.disallowedKinds,
        )
    }

    /**
     * NIP-86 admin RPC dispatcher. Owns the ban / allow / kind list
     * mutations, the NIP-11 `changerelay*` mutations (via an
     * [Nip86Server.InfoHolder] adapter that wraps [info] / [updateInfo]),
     * the event-store purge on each ban method (via `store.delete`),
     * and the admin allow-list ([adminPubkeys]). Transport-agnostic:
     * `KtorRelay` wraps it with `Nip86HttpHandler` for HTTP/NIP-98;
     * an in-process tool could call `dispatch(adminPubkey, req)`
     * directly.
     */
    val nip86Server: Nip86Server =
        Nip86Server(
            banStore = banStore,
            infoHolder =
                object : Nip86Server.InfoHolder {
                    override fun get(): Nip11RelayInformation = info.document

                    override fun set(info: Nip11RelayInformation) = updateInfo { info }
                },
            onBan = { filter -> store.delete(filter) },
            allowList = adminPubkeys,
        )

    /**
     * Writes the current state (NIP-11 doc + ban lists) to disk.
     * No-op when no `stateFile` was configured (see [RuntimeConfig.save]).
     *
     * Best-effort: any I/O failure is logged to stderr and swallowed
     * so an unwritable disk doesn't take the relay down. Operators
     * monitor for missing snapshots out-of-band.
     */
    fun snapshot() {
        runCatching {
            runtimeConfig.save(
                RuntimeConfigData(
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
            parentContext = parentContext,
            parallelVerify = parallelVerify,
            negentropySettings = negentropySettings,
        )

    override fun close() = server.close()
}
