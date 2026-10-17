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
package com.vitorpamplona.quartz.nip01Core.relay.client.stats

import java.util.concurrent.CountDownLatch
import kotlin.concurrent.thread
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Every relay's receive coroutine and every sending thread count into the same [RelayStat] at
 * once. With plain `+=` they lost updates; none may be lost.
 */
class RelayStatConcurrencyTest {
    @Test
    fun concurrentCountsAreNotLost() {
        val stat = RelayStat()
        val threads = 8
        val perThread = 20_000
        val start = CountDownLatch(1)

        val workers =
            List(threads) {
                thread {
                    start.await()
                    repeat(perThread) {
                        stat.addBytesReceived(1)
                        stat.addBytesSent(2)
                        stat.newConnection()
                        stat.connectionCompleted()
                    }
                }
            }
        start.countDown()
        workers.forEach { it.join() }

        assertEquals(threads * perThread, stat.receivedBytes)
        assertEquals(threads * perThread * 2, stat.sentBytes)
        assertEquals(threads * perThread, stat.connectionTentatives)
        assertEquals(threads * perThread, stat.connectionCompleted)
    }

    @Test
    fun constructorValuesAndOverwritesAreKept() {
        val stat = RelayStat(receivedBytes = 10, pingInMs = 300, compression = true, connectionTentatives = 2)
        assertEquals(10, stat.receivedBytes)
        assertEquals(300, stat.pingInMs)
        assertEquals(true, stat.compression)
        assertEquals(true, stat.hasNeverConnected)

        stat.pingInMs = 42
        stat.compression = false
        stat.connectionCompleted()
        assertEquals(42, stat.pingInMs)
        assertEquals(false, stat.compression)
        assertEquals(false, stat.hasNeverConnected)
    }
}
