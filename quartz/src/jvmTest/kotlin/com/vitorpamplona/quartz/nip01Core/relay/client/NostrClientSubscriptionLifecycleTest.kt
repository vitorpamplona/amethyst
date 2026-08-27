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
package com.vitorpamplona.quartz.nip01Core.relay.client

import com.vitorpamplona.quartz.nip01Core.relay.client.reqs.SubscriptionListener
import com.vitorpamplona.quartz.nip01Core.relay.client.subscriptions.SubscriptionController
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.relay.sockets.WebSocket
import com.vitorpamplona.quartz.nip01Core.relay.sockets.WebSocketListener
import com.vitorpamplona.quartz.nip01Core.relay.sockets.WebsocketBuilder
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * End-to-end audit of the subscription lifecycle through a real [NostrClient] and a
 * recording socket, added alongside the registry-release fix in [PoolRequests].
 *
 * Two things must hold together, and it is easy to fix one by breaking the other:
 *  - **nothing leaks**: the pool's state registries must return to zero once the app
 *    stops wanting a subscription, on every path that can drop one;
 *  - **nothing is dropped**: every frame the relay needs to stay in sync — the REQ, the
 *    CLOSE, the replay after a reconnect — must still reach the wire.
 *
 * So every test here asserts BOTH the wire traffic and the registry size.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class NostrClientSubscriptionLifecycleTest {
    private val url = NormalizedRelayUrl("wss://lifecycle.example.com")
    private val url2 = NormalizedRelayUrl("wss://lifecycle2.example.com")

    private class RecordingSocket(
        val sent: MutableList<String>,
    ) : WebSocket {
        override fun needsReconnect() = false

        override fun connect() {}

        override fun disconnect() {}

        override fun send(msg: String): Boolean {
            sent.add(msg)
            return true
        }
    }

    /**
     * Tracks every relay separately: the pool drops a relay as soon as nothing wants it
     * and redials it when something does, so a single `lastListener` would only ever
     * describe the most recent socket and quietly hide frames sent to the others.
     */
    private class RecordingBuilder : WebsocketBuilder {
        val sent = mutableListOf<String>()
        val perRelay = mutableMapOf<NormalizedRelayUrl, MutableList<String>>()
        val listeners = mutableMapOf<NormalizedRelayUrl, WebSocketListener>()

        /** Sockets dialed but not yet driven to onOpen — drained by `openAll`. */
        val unopened = mutableListOf<WebSocketListener>()
        var connectAttempts = 0

        override fun build(
            url: NormalizedRelayUrl,
            out: WebSocketListener,
        ): WebSocket {
            connectAttempts++
            listeners[url] = out
            unopened.add(out)
            val perRelaySink = perRelay.getOrPut(url) { mutableListOf() }
            return RecordingSocket(
                object : MutableList<String> by sent {
                    override fun add(element: String): Boolean {
                        perRelaySink.add(element)
                        return sent.add(element)
                    }
                },
            )
        }
    }

    private fun List<String>.reqs() = count { it.startsWith("[\"REQ\"") }

    private fun List<String>.closes() = count { it.startsWith("[\"CLOSE\"") }

    private fun List<String>.counts() = count { it.startsWith("[\"COUNT\"") }

    /**
     * Brings every socket the pool has dialed but not yet opened to a ready state.
     * Only newly dialed sockets are opened: re-opening a live one would make the client
     * treat it as a fresh connection and replay every filter, inflating REQ counts.
     */
    private fun TestScope.openAll(builder: RecordingBuilder) {
        // Loop: opening one relay can make the pool dial another.
        repeat(3) {
            val batch = builder.unopened.toList()
            builder.unopened.clear()
            if (batch.isEmpty() && it > 0) return
            batch.forEach { listener -> listener.onOpen(50, false) }
            advanceTimeBy(500)
            runCurrent()
        }
    }

    private fun TestScope.settle() {
        advanceTimeBy(1_000)
        runCurrent()
    }

    private fun filters() = listOf(Filter(kinds = listOf(1)))

    @Test
    fun subscribeThenUnsubscribeSendsBothFramesAndReleasesTheRegistry() =
        runTest {
            val builder = RecordingBuilder()
            val client = NostrClient(builder, this)
            try {
                advanceTimeBy(500)
                runCurrent()

                client.subscribe("sub", mapOf(url to filters()))
                openAll(builder)
                assertEquals(1, builder.sent.reqs(), "the REQ must reach the relay")
                assertEquals(1, client.registrySizes().liveRequests, "a live sub is tracked")

                client.unsubscribe("sub")
                advanceTimeBy(500)
                runCurrent()

                assertEquals(1, builder.sent.closes(), "the relay must be told to CLOSE")
                assertEquals(0, client.registrySizes().liveRequests, "the row must be released")
                assertEquals(0, client.registrySizes().liveCounts)
            } finally {
                client.close()
            }
        }

    /**
     * The app backgrounds (host calls [NostrClient.disconnect]) and a ViewModel or a
     * composable then tears its subscription down. No CLOSE can be sent — the socket is
     * gone and the relay already dropped the sub — but the row must still be released,
     * or it survives the whole background stretch and inflates every later connect scan.
     */
    @Test
    fun unsubscribeWhileInactiveStillReleasesTheRegistry() =
        runTest {
            val builder = RecordingBuilder()
            val client = NostrClient(builder, this)
            try {
                advanceTimeBy(500)
                runCurrent()

                client.subscribe("sub", mapOf(url to filters()))
                openAll(builder)
                assertEquals(1, client.registrySizes().liveRequests)

                client.disconnect()
                advanceTimeBy(500)
                runCurrent()

                client.unsubscribe("sub")
                advanceTimeBy(500)
                runCurrent()

                assertEquals(0, client.registrySizes().liveRequests, "an inactive client must still release the row")
            } finally {
                client.close()
            }
        }

    /** Many subs torn down while backgrounded must not accumulate. */
    @Test
    fun churnWhileInactiveDoesNotAccumulate() =
        runTest {
            val builder = RecordingBuilder()
            val client = NostrClient(builder, this)
            try {
                advanceTimeBy(500)
                runCurrent()
                client.disconnect()
                advanceTimeBy(500)
                runCurrent()

                repeat(200) { i ->
                    client.subscribe("bg-$i", mapOf(url to filters()))
                    client.unsubscribe("bg-$i")
                }
                advanceTimeBy(500)
                runCurrent()

                assertEquals(0, client.registrySizes().liveRequests, "background churn must not accumulate rows")
            } finally {
                client.close()
            }
        }

    /**
     * The core app pattern: [SubscriptionController.updateRelaysIfNeeded] unsubscribes a
     * feed whose relay set went empty and re-subscribes THE SAME id when it comes back.
     * The second subscribe must produce a fresh REQ — a stale row that made the client
     * think a REQ was already in flight would silently leave the feed empty.
     */
    @Test
    fun resubscribingTheSameIdAfterUnsubscribeSendsAFreshReq() =
        runTest {
            val builder = RecordingBuilder()
            val client = NostrClient(builder, this)
            try {
                advanceTimeBy(500)
                runCurrent()

                repeat(5) {
                    client.subscribe("feed", mapOf(url to filters()))
                    // The pool drops the relay when nothing wants it and redials on the
                    // next subscribe, so each cycle needs its socket brought up.
                    openAll(builder)
                    client.unsubscribe("feed")
                    settle()
                }

                assertEquals(5, builder.sent.reqs(), "every re-subscribe must re-REQ")
                assertEquals(5, builder.sent.closes(), "every unsubscribe must CLOSE")
                assertEquals(0, client.registrySizes().liveRequests)
            } finally {
                client.close()
            }
        }

    /** Same cycle driven through SubscriptionController, which is what the app uses. */
    @Test
    fun subscriptionControllerDismissAndRecreateStaysInSync() =
        runTest {
            val builder = RecordingBuilder()
            val client = NostrClient(builder, this)
            try {
                val controller = SubscriptionController(client)
                advanceTimeBy(500)
                runCurrent()

                repeat(4) {
                    val sub = controller.requestNewSubscription("feed", object : SubscriptionListener {})
                    client.subscribe("feed", mapOf(url to filters()), sub.listener)
                    openAll(builder)
                    controller.dismissSubscription(sub)
                    settle()
                }

                assertEquals(4, builder.sent.reqs())
                assertEquals(4, builder.sent.closes())
                assertEquals(0, client.registrySizes().liveRequests, "dismissed subs must not linger")
            } finally {
                client.close()
            }
        }

    /** A live sub whose filters change keeps exactly one row and re-REQs. */
    @Test
    fun filterChangesOnALiveSubKeepOneRowAndResend() =
        runTest {
            val builder = RecordingBuilder()
            val client = NostrClient(builder, this)
            try {
                advanceTimeBy(500)
                runCurrent()

                client.subscribe("feed", mapOf(url to listOf(Filter(kinds = listOf(1)))))
                openAll(builder)
                builder.listeners.getValue(url).onMessage("[\"EOSE\",\"feed\"]")
                advanceTimeBy(500)
                runCurrent()

                client.subscribe("feed", mapOf(url to listOf(Filter(kinds = listOf(7)))))
                advanceTimeBy(500)
                runCurrent()

                assertEquals(1, client.registrySizes().liveRequests, "a filter change is one sub, not two")
                assertTrue(builder.sent.reqs() >= 2, "the changed filters must be re-REQed")
                assertEquals(0, builder.sent.closes(), "a filter change must not CLOSE the sub")
            } finally {
                client.close()
            }
        }

    /** A reconnect must replay every live sub — and only the live ones. */
    @Test
    fun reconnectReplaysLiveSubsOnly() =
        runTest {
            val builder = RecordingBuilder()
            val client = NostrClient(builder, this)
            try {
                advanceTimeBy(500)
                runCurrent()

                client.subscribe("keep", mapOf(url to filters()))
                client.subscribe("drop", mapOf(url to filters()))
                openAll(builder)
                advanceTimeBy(500)
                runCurrent()

                client.unsubscribe("drop")
                advanceTimeBy(500)
                runCurrent()
                assertEquals(1, client.registrySizes().liveRequests)

                builder.sent.clear()
                builder.listeners.getValue(url).onClosed(1000, "server closed")
                advanceTimeBy(500)
                runCurrent()
                client.connect()
                advanceTimeBy(1_000)
                runCurrent()
                openAll(builder)

                assertEquals(1, builder.sent.reqs(), "exactly the live sub is replayed")
                assertTrue(builder.sent.any { it.contains("\"keep\"") }, "the live sub must be replayed")
                assertTrue(builder.sent.none { it.contains("\"drop\"") }, "the dropped sub must not be replayed")
            } finally {
                client.close()
            }
        }

    /** A COUNT query must release its row too, and not touch the REQ registry. */
    @Test
    fun countQueriesReleaseTheirOwnRegistryOnly() =
        runTest {
            val builder = RecordingBuilder()
            val client = NostrClient(builder, this)
            try {
                advanceTimeBy(500)
                runCurrent()

                client.subscribe("req", mapOf(url to filters()))
                client.count("cnt", mapOf(url to filters()))
                openAll(builder)
                advanceTimeBy(500)
                runCurrent()

                assertEquals(1, client.registrySizes().liveRequests)
                assertEquals(1, client.registrySizes().liveCounts)
                assertEquals(1, builder.sent.counts(), "the COUNT must reach the relay")

                client.unsubscribe("cnt")
                advanceTimeBy(500)
                runCurrent()

                assertEquals(1, client.registrySizes().liveRequests, "closing a COUNT must not disturb the REQ")
                assertEquals(0, client.registrySizes().liveCounts)

                client.unsubscribe("req")
                advanceTimeBy(500)
                runCurrent()
                assertEquals(0, client.registrySizes().liveRequests)
                assertEquals(0, client.registrySizes().liveCounts)
            } finally {
                client.close()
            }
        }

    /**
     * Releasing a sub's row also drops that sub's per-filter refusal memory, so this pins
     * what must NOT be dropped: the relay-WIDE capability block lives in
     * [com.vitorpamplona.quartz.nip01Core.relay.client.pool.RelayReqRefusals], keyed by
     * relay and never by sub, so a relay that refuses to serve reads at all stays blocked
     * across any number of unsubscribe/re-subscribe cycles. That is the guard that stops
     * a relay being hammered; the per-sub counter only sharpens it.
     */
    @Test
    fun relayWideRefusalSurvivesUnsubscribeAndResubscribe() =
        runTest {
            val builder = RecordingBuilder()
            val client = NostrClient(builder, this)
            try {
                settle()

                // Two refusals of the "no reads" class block the relay pool-wide.
                repeat(2) { round ->
                    client.subscribe("feed-$round", mapOf(url to filters()))
                    openAll(builder)
                    builder.listeners
                        .getValue(url)
                        .onMessage("[\"CLOSED\",\"feed-$round\",\"unsupported: this relay does not accept REQ\"]")
                    settle()
                    client.unsubscribe("feed-$round")
                    settle()
                }

                assertEquals(0, client.registrySizes().liveRequests, "cycled subs must not linger")

                // A brand-new sub on the same relay must now be suppressed, even though
                // every earlier sub's own row is long gone.
                builder.sent.clear()
                client.subscribe("feed-new", mapOf(url to filters()))
                openAll(builder)
                settle()

                assertEquals(
                    0,
                    builder.sent.reqs(),
                    "a relay blocked pool-wide must not be re-offered a REQ after the sub rows were released",
                )
            } finally {
                client.close()
            }
        }

    /** A multi-relay sub must CLOSE on every relay that got a REQ before releasing. */
    @Test
    fun multiRelaySubClosesOnEveryRelayBeforeRelease() =
        runTest {
            val builder = RecordingBuilder()
            val client = NostrClient(builder, this)
            try {
                settle()

                client.subscribe("multi", mapOf(url to filters(), url2 to filters()))
                openAll(builder)
                settle()

                val reqRelays = builder.perRelay.filterValues { it.reqs() > 0 }.keys
                assertEquals(setOf(url, url2), reqRelays, "both relays must get the REQ")

                client.unsubscribe("multi")
                settle()

                val closeRelays = builder.perRelay.filterValues { it.closes() > 0 }.keys
                assertEquals(reqRelays, closeRelays, "every relay that got a REQ must get a CLOSE")
                assertEquals(0, client.registrySizes().liveRequests)
            } finally {
                client.close()
            }
        }
}
