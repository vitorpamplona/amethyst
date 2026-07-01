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
package com.vitorpamplona.quartz.nip01Core.relay

import com.vitorpamplona.quartz.nip01Core.relay.client.pool.PoolRequests
import com.vitorpamplona.quartz.nip01Core.relay.client.reqs.SubscriptionListener
import com.vitorpamplona.quartz.nip01Core.relay.client.single.IRelayClient
import com.vitorpamplona.quartz.nip01Core.relay.commands.toClient.EoseMessage
import com.vitorpamplona.quartz.nip01Core.relay.commands.toRelay.Command
import com.vitorpamplona.quartz.nip01Core.relay.commands.toRelay.ReqCmd
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Regression guard for the shared-sub-id double-REQ race in [PoolRequests].
 *
 * A single subscription id is driven from two threads at once: the app thread
 * (the subscribe path, [PoolRequests.sendToRelayIfChanged]) and the relay reader
 * thread (an EOSE that triggers an auto-resend, [PoolRequests.onIncomingMessage]).
 * The subscription is already LIVE and its desired filters have just changed, so
 * both threads independently conclude "the filters changed, send a REQ".
 *
 * The bug: the decision (read state) and the send (mark state SENT via onSent)
 * were not atomic, so the reader could read the pre-send state (filters still on
 * the previous value) while the app had already moved the desired filters
 * forward — and both would send a REQ for the same sub id. Two REQs on one id
 * race on the wire: the relay answers with two EOSEs and duplicate events, or —
 * if a CLOSE interleaves — an empty result that silently truncates a paged
 * download (this is what broke `fetchAllPages` on large sets).
 *
 * The fix makes the "should I send a REQ?" decision pre-mark the state
 * atomically, so exactly one REQ is ever produced. This test pins the exact
 * interleaving the bug needs (app has produced its REQ but not yet run onSent)
 * open and asserts only one REQ comes out.
 */
class PoolRequestsConcurrencyTest {
    private class FakeRelay(
        override val url: NormalizedRelayUrl,
        val onCmd: (Command) -> Unit,
    ) : IRelayClient {
        override fun connect() {}

        override fun needsToReconnect() = false

        override fun connectAndSyncFiltersIfDisconnected(ignoreRetryDelays: Boolean) {}

        override fun isConnected() = true

        override fun sendOrConnectAndSync(cmd: Command) = onCmd(cmd)

        override fun sendIfConnected(cmd: Command) = onCmd(cmd)

        override fun disconnect() {}
    }

    @Test
    fun concurrentEoseResendAndSubscribeSendExactlyOneReq() {
        val url = RelayUrlNormalizer.normalize("ws://race/")
        val subId = "shared-sub"
        val filtersA = listOf(Filter(kinds = listOf(1)))
        val filtersB = listOf(Filter(kinds = listOf(2)))
        val listener = object : SubscriptionListener {}

        // Many episodes so a regression that only sometimes doubles still trips.
        repeat(300) { episode ->
            val pool = PoolRequests()
            val reqBCount = AtomicInteger(0)

            fun countReqB(cmd: Command) {
                if (cmd is ReqCmd && cmd.filters == filtersB) reqBCount.incrementAndGet()
            }

            val fakeRelay =
                FakeRelay(url) { cmd ->
                    // relay-reader auto-resend send path
                    countReqB(cmd)
                    pool.onSent(url, cmd)
                }

            // Bring the sub to LIVE with filters A.
            val setupRelays = pool.addOrUpdate(subId, mapOf(url to filtersA), listener)
            pool.sendToRelayIfChanged(subId, setupRelays) { _, cmd -> pool.onSent(url, cmd) }
            pool.onIncomingMessage(fakeRelay, EoseMessage(subId))

            // The desired filters change to B (e.g. the next page of a paged download).
            pool.addOrUpdate(subId, mapOf(url to filtersB), listener)

            val appProducedReq = CountDownLatch(1)
            val readerDone = CountDownLatch(1)

            val appThread =
                thread {
                    pool.sendToRelayIfChanged(subId, setOf(url)) { _, cmd ->
                        countReqB(cmd)
                        // App has produced its REQ(B); park before onSent so the
                        // subscription state is not yet advanced — the exact window
                        // the race needs.
                        appProducedReq.countDown()
                        readerDone.await()
                        pool.onSent(url, cmd)
                    }
                }

            val readerThread =
                thread {
                    appProducedReq.await()
                    pool.onIncomingMessage(fakeRelay, EoseMessage(subId))
                    readerDone.countDown()
                }

            appThread.join()
            readerThread.join()

            assertEquals(
                1,
                reqBCount.get(),
                "episode $episode: exactly one REQ must be sent for the changed filters, " +
                    "never a duplicate from the app + reader race",
            )
        }
    }
}
