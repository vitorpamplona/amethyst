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
package com.vitorpamplona.amethyst.desktop.testrelay

import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.relay.server.NostrServer
import com.vitorpamplona.quartz.nip01Core.relay.server.policies.EmptyPolicy
import com.vitorpamplona.quartz.nip01Core.relay.sockets.WebsocketBuilder
import com.vitorpamplona.quartz.nip01Core.store.sqlite.EventStore
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking

/**
 * In-process relay primed with a [LaunchFixture] (or any list of pre-signed
 * events). Wraps an [EventStore] backed [NostrServer], applies
 * [EmptyPolicy] so REQs are answered without auth gating, and exposes the
 * matching [InProcessWebsocketBuilder] so a [RelayConnectionManager] / test
 * harness can connect to it.
 *
 * Lifecycle: callers should construct via [open], use, then [close].
 * The constructor seeds the store synchronously to keep test setup terse.
 *
 * Combines plan Phases 2.1 (builder) + 2.2 (server) + 2.4 (wire) into a
 * single small entry point used by every consumer.
 *
 * The relay URL [LAUNCH_TEST_RELAY_URL] is what tests should add to the
 * account's relay list — the in-process socket ignores the URL value, but
 * RelayPool keys connections by it.
 */
class LaunchFixtureRelay private constructor(
    val server: NostrServer,
    private val store: EventStore,
) : AutoCloseable {
    val builder: WebsocketBuilder = InProcessWebsocketBuilder(server)

    override fun close() {
        // NostrServer.close() also closes its store.
        server.close()
    }

    companion object {
        val LAUNCH_TEST_RELAY_URL: NormalizedRelayUrl = NormalizedRelayUrl("wss://launch.test.invalid")

        /**
         * Construct a relay pre-seeded with [events]. The seeding is done
         * synchronously via [runBlocking] — fine in a JVM test context but
         * never call from production code.
         */
        fun open(
            events: List<Event>,
            dispatcher: CoroutineDispatcher = Dispatchers.Default,
        ): LaunchFixtureRelay {
            val store = EventStore(dbName = null, relay = LAUNCH_TEST_RELAY_URL)
            runBlocking { store.batchInsert(events) }
            val server =
                NostrServer(
                    store = store,
                    policyBuilder = { EmptyPolicy },
                    parentContext = dispatcher + SupervisorJob(),
                )
            return LaunchFixtureRelay(server, store)
        }

        /** Convenience: open a relay primed with [LaunchFixture.build]. */
        fun openLaunchFixture(): LaunchFixtureRelay = open(LaunchFixture.build().events)
    }
}
