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
package com.vitorpamplona.quartz.relay

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
import com.vitorpamplona.quartz.relay.admin.BanStore
import com.vitorpamplona.quartz.relay.admin.DynamicBanPolicy
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
 *   - [InProcessWebSocket] / [RelayHub] — no socket, fastest path, ideal
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
) : AutoCloseable {
    /**
     * NIP-11 doc. Mutable so NIP-86 admin RPCs (`changerelayname`,
     * `changerelaydescription`, `changerelayicon`) can swap the doc
     * atomically. Readers (the NIP-11 GET endpoint) re-read on every
     * request so changes are visible immediately, no restart needed.
     */
    @Volatile
    var info: RelayInfo = info
        private set

    /** Mutates the live NIP-11 doc. Called by [admin.Nip86Server]. */
    fun updateInfo(transform: (Nip11RelayInformation) -> Nip11RelayInformation) {
        info = RelayInfo(transform(info.document))
    }

    /**
     * Runtime-mutable ban / allow lists. NIP-86 RPC handlers in
     * [admin.Nip86Server] mutate this; the policy stack consults it on
     * every accept call via [DynamicBanPolicy].
     */
    val banStore: BanStore = BanStore()

    val server =
        NostrServer(
            store,
            // Always prepend a DynamicBanPolicy so NIP-86 admin actions
            // bite. When the operator-supplied builder returns
            // [EmptyPolicy] we use the dynamic policy alone; otherwise
            // we stack them so both layers must accept.
            policyBuilder = {
                val user = policyBuilder()
                if (user === EmptyPolicy) DynamicBanPolicy(banStore) else user + DynamicBanPolicy(banStore)
            },
            parentContext,
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
