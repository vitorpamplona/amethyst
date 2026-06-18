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
import com.vitorpamplona.quartz.nip01Core.relay.client.NostrClient
import com.vitorpamplona.quartz.nip01Core.relay.client.reqs.SubscriptionListener
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip10Notes.TextNoteEvent
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

/**
 * Phase 2 roundtrip: NostrClient ↔ InProcessWebsocketBuilder ↔ NostrServer
 * with a seeded fixture round-trips a REQ → EVENTs → EOSE and delivers
 * the same events the fixture contains.
 *
 * Uses real coroutine dispatchers (not `runTest` virtual time) because the
 * in-process websocket pumps events on real channels — virtual time would
 * never advance the sample-debounce inside `NostrClient.allRelays`.
 *
 * See desktopApp/plans/2026-06-17-feat-app-launch-optimization-plan.md
 * § Phase 2.4.
 */
class LaunchFixtureRelayTest {
    @Test
    fun reqAgainstFixtureRelayReturnsAllAuthorNotesThenEose() =
        runBlocking {
            val fixture = LaunchFixture.build(noteCount = 10)
            val relay = LaunchFixtureRelay.open(fixture.events)
            val clientScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

            try {
                val client = NostrClient(relay.builder, parentScope = clientScope)
                client.connect()

                val received = mutableListOf<Event>()
                val eose = CompletableDeferred<Unit>()

                client.subscribe(
                    subId = "test-sub",
                    filters =
                        mapOf(
                            LaunchFixtureRelay.LAUNCH_TEST_RELAY_URL to listOf(Filter(kinds = listOf(TextNoteEvent.KIND))),
                        ),
                    listener =
                        object : SubscriptionListener {
                            override fun onEvent(
                                event: Event,
                                isLive: Boolean,
                                relay: NormalizedRelayUrl,
                                forFilters: List<Filter>?,
                            ) {
                                received += event
                            }

                            override fun onEose(
                                relay: NormalizedRelayUrl,
                                forFilters: List<Filter>?,
                            ) {
                                eose.complete(Unit)
                            }
                        },
                )

                withTimeout(5.seconds) { eose.await() }

                val expectedNotes = fixture.events.count { it.kind == TextNoteEvent.KIND }
                assertEquals(
                    expectedNotes,
                    received.size,
                    "Fixture relay must replay every text note before EOSE",
                )
                assertTrue(
                    received.all { it.kind == TextNoteEvent.KIND },
                    "REQ kind:[1] must only yield kind:1 events",
                )
                client.close()
            } finally {
                relay.close()
                clientScope.cancel()
            }
        }
}
