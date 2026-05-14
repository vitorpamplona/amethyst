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
package com.vitorpamplona.amethyst.service.images

import coil3.annotation.ExperimentalCoilApi
import coil3.disk.DiskCache
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.util.concurrent.atomic.AtomicLong

@OptIn(ExperimentalCoilApi::class)
class CoilDiskTrimmerTest {
    private lateinit var tmpDir: File
    private lateinit var logFile: File
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    @Before
    fun setUp() {
        tmpDir = Files.createTempDirectory("coil-trimmer-test").toFile()
        logFile = File(tmpDir, "log.tsv")
    }

    @After
    fun tearDown() {
        scope.cancel("tearDown")
        tmpDir.deleteRecursively()
    }

    /**
     * Tiny fake of [DiskCache] that only models `size`, `maxSize`, and
     * `remove`. Each call to `remove(key)` decrements `size` by the per-entry
     * byte budget we hand in. Other methods of `DiskCache` are not relevant
     * to the trimmer and are stubbed via mockk so any accidental call would
     * blow up loud in tests.
     */
    private class FakeDiskCache(
        override val maxSize: Long,
        private val bytesPerEntry: Long,
    ) : DiskCache by mockk(relaxed = false) {
        private val current = AtomicLong(0L)

        override val size: Long get() = current.get()

        fun setSize(bytes: Long) {
            current.set(bytes)
        }

        override fun remove(key: String): Boolean {
            current.addAndGet(-bytesPerEntry)
            return true
        }
    }

    @Test
    fun trim_belowTarget_isNoop() =
        runTest {
            val cache = FakeDiskCache(maxSize = 1000L, bytesPerEntry = 10L)
            cache.setSize(500L) // 50% — below 70% target

            val log = KeyAccessLog(logFile, scope, flushIntervalMs = 1L)
            log.load()
            for (i in 1..50) log.recordAccess("k$i", timeMs = i.toLong())
            log.flushNow()

            val trimmer = CoilDiskTrimmer({ cache }, log)
            trimmer.trim()

            assertEquals(500L, cache.size)
        }

    @Test
    fun trim_aboveTarget_drainsToFloor() =
        runTest {
            val cache = FakeDiskCache(maxSize = 1000L, bytesPerEntry = 10L)
            cache.setSize(900L) // 90%, well above 70% target

            val log = KeyAccessLog(logFile, scope, flushIntervalMs = 1L)
            log.load()
            // 90 known entries so we definitely have enough to evict.
            for (i in 1..90) log.recordAccess("k$i", timeMs = i.toLong())
            log.flushNow()

            val trimmer = CoilDiskTrimmer({ cache }, log)
            trimmer.trim()

            // Floor is 55% = 550 bytes. Trimmer stops at the first call where
            // size has dipped at or below the floor — with 10-byte entries we
            // stop at exactly 550 (35 evictions: 900 - 35*10 = 550).
            assertTrue("size=${cache.size} should be at/below floor 550", cache.size <= 550L)
            assertTrue("size=${cache.size} should not over-evict past floor", cache.size >= 540L)
        }

    @Test
    fun trim_evictsOldestFirst() =
        runTest {
            val cache = FakeDiskCache(maxSize = 1000L, bytesPerEntry = 10L)
            cache.setSize(800L) // 80%, above target

            val log = KeyAccessLog(logFile, scope, flushIntervalMs = 1L)
            log.load()
            log.recordAccess("oldest", timeMs = 100)
            log.recordAccess("middle", timeMs = 200)
            log.recordAccess("newest", timeMs = 300)
            // Pad the log so we have plenty of keys to choose from.
            for (i in 1..30) log.recordAccess("pad$i", timeMs = 1000L + i)
            log.flushNow()

            val evicted = mutableListOf<String>()
            val tracked =
                object : DiskCache by cache {
                    override fun remove(key: String): Boolean {
                        evicted += key
                        return cache.remove(key)
                    }
                }

            val trimmer = CoilDiskTrimmer({ tracked }, log)
            trimmer.trim()

            // First three evictions must be the three oldest in that order.
            assertTrue("evicted=$evicted; first 3 must be oldest-first", evicted.size >= 3)
            assertEquals("oldest", evicted[0])
            assertEquals("middle", evicted[1])
            assertEquals("newest", evicted[2])
        }

    @Test
    fun trim_emptyKeyLog_isNoopEvenWhenOverTarget() =
        runTest {
            val cache = FakeDiskCache(maxSize = 1000L, bytesPerEntry = 10L)
            cache.setSize(950L) // 95%, well above target

            val log = KeyAccessLog(logFile, scope, flushIntervalMs = 1L)
            log.load() // no entries

            val trimmer = CoilDiskTrimmer({ cache }, log)
            trimmer.trim()

            // No known keys to evict; relies on Coil's own internal trim.
            assertEquals(950L, cache.size)
        }

    @Test
    fun trim_zeroMaxSize_isNoop() =
        runTest {
            val cache = mockk<DiskCache>(relaxed = true)
            every { cache.maxSize } returns 0L
            every { cache.size } returns 100L

            val log = KeyAccessLog(logFile, scope, flushIntervalMs = 1L)
            log.load()
            log.recordAccess("a", timeMs = 100)
            log.flushNow()

            val trimmer = CoilDiskTrimmer({ cache }, log)
            trimmer.trim()

            verify(exactly = 0) { cache.remove(any()) }
        }

    @Test
    fun trim_concurrentCalls_serialise() =
        runTest {
            val cache = FakeDiskCache(maxSize = 1000L, bytesPerEntry = 10L)
            cache.setSize(900L)

            val log = KeyAccessLog(logFile, scope, flushIntervalMs = 1L)
            log.load()
            for (i in 1..100) log.recordAccess("k$i", timeMs = i.toLong())
            log.flushNow()

            val trimmer = CoilDiskTrimmer({ cache }, log)

            // Two concurrent trim() calls: the second should tryLock-fail and
            // bail out; the first should still drain to floor.
            coroutineScope {
                listOf(async { trimmer.trim() }, async { trimmer.trim() }).awaitAll()
            }

            assertTrue(cache.size <= 550L)
        }
}
