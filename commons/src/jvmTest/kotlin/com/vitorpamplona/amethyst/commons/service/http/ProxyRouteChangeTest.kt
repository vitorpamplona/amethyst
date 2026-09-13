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
package com.vitorpamplona.amethyst.commons.service.http

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class ProxyRouteChangeTest {
    @Test
    fun subscribingIsNotAChange() =
        runTest {
            val port = MutableStateFlow<Int?>(9050)
            val evictions = AtomicInteger()

            val job = evictOnProxyRouteChange(port) { evictions.incrementAndGet() }
            runCurrent()

            // The port in force when we wired up is the status quo, not a route change.
            assertEquals(0, evictions.get())
            job.cancel()
        }

    @Test
    fun torComingUpEvictsOnce() =
        runTest {
            val port = MutableStateFlow<Int?>(null)
            val evictions = AtomicInteger()

            val job = evictOnProxyRouteChange(port) { evictions.incrementAndGet() }
            runCurrent()

            port.value = 9050
            runCurrent()

            assertEquals(1, evictions.get())
            job.cancel()
        }

    @Test
    fun torGoingAwayEvictsOnce() =
        runTest {
            val port = MutableStateFlow<Int?>(9050)
            val evictions = AtomicInteger()

            val job = evictOnProxyRouteChange(port) { evictions.incrementAndGet() }
            runCurrent()

            port.value = null
            runCurrent()

            assertEquals(1, evictions.get())
            job.cancel()
        }

    @Test
    fun movingToAnotherPortEvictsOnce() =
        runTest {
            val port = MutableStateFlow<Int?>(9050)
            val evictions = AtomicInteger()

            val job = evictOnProxyRouteChange(port) { evictions.incrementAndGet() }
            runCurrent()

            port.value = 9150 // Tor Browser's port
            runCurrent()

            assertEquals(1, evictions.get())
            job.cancel()
        }

    /**
     * The regression this whole seam exists for. The old per-factory check fired on every client
     * rebuild — which happens on each network-state emission and each resubscribe — and wiped the
     * pool both clients share. Re-emitting the same port must be free.
     */
    @Test
    fun reEmittingTheSamePortNeverEvicts() =
        runTest {
            val port = MutableStateFlow<Int?>(9050)
            val evictions = AtomicInteger()

            val job = evictOnProxyRouteChange(port) { evictions.incrementAndGet() }
            runCurrent()

            repeat(20) {
                port.value = 9050
                runCurrent()
            }

            assertEquals(0, evictions.get())
            job.cancel()
        }

    @Test
    fun aRoundTripEvictsOncePerLeg() =
        runTest {
            val port = MutableStateFlow<Int?>(null)
            val evictions = AtomicInteger()

            val job = evictOnProxyRouteChange(port) { evictions.incrementAndGet() }
            runCurrent()

            listOf<Int?>(9050, null, 9050).forEach {
                port.value = it
                runCurrent()
            }

            assertEquals(3, evictions.get())
            job.cancel()
        }
}
