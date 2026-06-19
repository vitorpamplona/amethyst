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
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

/**
 * Pins the invariant required by the Phase 5.2 launch-optimization fix:
 * [NostrClient.subscribe] called BEFORE [NostrClient.connect] still
 * delivers events once the connection comes up. This is the basis for
 * dropping the `connectedRelays.first { isNotEmpty() }` gate from the
 * desktop bootstrap subscription (Main.kt:1242-1284).
 *
 * See desktopApp/plans/2026-06-17-feat-app-launch-optimization-plan.md
 * § Phase 5.2.
 */
class SubscribeBeforeConnectTest {
    @Test
    fun subscribeIssuedBeforeConnectStillReceivesEventsAndEose() =
        runBlocking {
            val fixture = LaunchFixture.build(noteCount = 5)
            val relay = LaunchFixtureRelay.open(fixture.events)
            val clientScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

            try {
                val client = NostrClient(relay.builder, parentScope = clientScope)

                val received = mutableListOf<Event>()
                val eose = CompletableDeferred<Unit>()

                // Subscribe BEFORE connect — the production fix that drops the
                // `connectedRelays.first { isNotEmpty() }` gate relies on REQs
                // queued at this point being flushed when the pool comes up.
                client.subscribe(
                    subId = "pre-connect-sub",
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

                client.connect()

                withTimeout(5.seconds) { eose.await() }

                val expected = fixture.events.count { it.kind == TextNoteEvent.KIND }
                assertTrue(
                    received.size == expected,
                    "Subscription registered pre-connect should still deliver all $expected events (got ${received.size})",
                )
                client.close()
            } finally {
                relay.close()
                clientScope.cancel()
            }
        }
}
