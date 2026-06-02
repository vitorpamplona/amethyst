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
package com.vitorpamplona.amethyst.service.relayClient.eoseManagers

import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.atomic.AtomicReference
import kotlin.time.Duration.Companion.milliseconds

/**
 * Real-time (not virtual-time) tests: the tracker's watchdog reads the wall clock, so a short
 * [silenceTimeout] with real delays is the honest way to exercise the silence backstop.
 */
class WindowLoadTrackerSilenceTest {
    private val good = NormalizedRelayUrl("wss://vitor.nostr1.com/")
    private val silent = NormalizedRelayUrl("wss://relay.ditto.pub/")

    @Test
    fun silentRelayDoesNotBlockTheLoadAndIsReportedAsAbandoned() =
        runBlocking {
            val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
            val abandoned = AtomicReference<Set<NormalizedRelayUrl>>(emptySet())
            val tracker =
                WindowLoadTracker(
                    name = "test",
                    silenceTimeout = 50.milliseconds,
                    onAbandoned = { abandoned.set(it) },
                )

            tracker.startLoading(scope)
            tracker.setExpectedRelays(setOf(good, silent))
            // Both received the REQ; only the good relay answers (an EOSE settles it).
            tracker.onReqSent(good.url)
            tracker.onReqSent(silent.url)
            tracker.onRelaySettled(good)

            // The good relay is settled and the silent one trips the silence backstop, so the load
            // completes without ever hearing from the silent relay.
            withTimeout(3000) { tracker.loading.first { !it } }

            assertEquals(setOf(silent), abandoned.get())
            scope.cancel()
        }

    @Test
    fun aRelayStuckBeforeItsReqStopsBlockingButIsNotGivenUp() =
        runBlocking {
            val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
            val abandoned = AtomicReference<Set<NormalizedRelayUrl>>(emptySet())
            val tracker =
                WindowLoadTracker(
                    name = "test",
                    connectGrace = 50.milliseconds,
                    onAbandoned = { abandoned.set(it) },
                )

            tracker.startLoading(scope)
            tracker.setExpectedRelays(setOf(good, silent))
            // `good` answers; `silent` is stuck connecting — it never even receives its REQ.
            tracker.onReqSent(good.url)
            tracker.onRelaySettled(good)

            // The connect-grace backstop completes the load without the stuck relay...
            withTimeout(3000) { tracker.loading.first { !it } }
            // ...but it must NOT be given up: a slow connect deserves a retry next round.
            assertEquals(emptySet<NormalizedRelayUrl>(), abandoned.get())
            scope.cancel()
        }

    @Test
    fun aSilentRelayThatNeverGotAReqStillBlocksUntilItSettles() =
        runBlocking {
            val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
            val tracker = WindowLoadTracker(name = "test", silenceTimeout = 50.milliseconds)

            tracker.startLoading(scope)
            tracker.setExpectedRelays(setOf(good, silent))
            tracker.onReqSent(good.url)
            tracker.onRelaySettled(good)
            // `silent` is still connecting: no onReqSent, so the silence clock never starts and the
            // load must stay open (a connection gap must not be mistaken for a dead relay).

            Thread.sleep(400) // well past silenceTimeout
            assertTrue("still loading while a relay has not even been sent its REQ", tracker.loading.value)

            // Once it connects, gets its REQ, and stays silent, the backstop then completes the load.
            tracker.onReqSent(silent.url)
            withTimeout(3000) { tracker.loading.first { !it } }

            scope.cancel()
        }
}
