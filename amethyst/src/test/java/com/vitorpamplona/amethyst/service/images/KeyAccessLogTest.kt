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

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files

class KeyAccessLogTest {
    private lateinit var tmpDir: File
    private lateinit var logFile: File
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    @Before
    fun setUp() {
        tmpDir = Files.createTempDirectory("coil-keylog-test").toFile()
        logFile = File(tmpDir, "log.tsv")
    }

    @After
    fun tearDown() {
        scope.cancel("tearDown")
        tmpDir.deleteRecursively()
    }

    @Test
    fun recordAccess_persistsAndReplaysOnLoad() =
        runTest {
            val log = KeyAccessLog(logFile, scope, flushIntervalMs = 1L)
            log.load()

            log.recordAccess("a", timeMs = 100)
            log.recordAccess("b", timeMs = 200)
            log.recordAccess("c", timeMs = 300)
            log.flushNow()

            val reloaded = KeyAccessLog(logFile, scope, flushIntervalMs = 1L)
            reloaded.load()
            val snapshot = reloaded.snapshotOldestFirst()

            assertEquals(3, snapshot.size)
            assertEquals(listOf("a", "b", "c"), snapshot.map { it.key })
            assertEquals(listOf(100L, 200L, 300L), snapshot.map { it.lastAccessMs })
        }

    @Test
    fun recordAccess_laterTimestampWins() =
        runTest {
            val log = KeyAccessLog(logFile, scope, flushIntervalMs = 1L)
            log.load()

            log.recordAccess("a", timeMs = 100)
            log.recordAccess("a", timeMs = 500)
            log.recordAccess("a", timeMs = 300)
            log.flushNow()

            val reloaded = KeyAccessLog(logFile, scope, flushIntervalMs = 1L)
            reloaded.load()
            val snapshot = reloaded.snapshotOldestFirst()

            assertEquals(1, snapshot.size)
            assertEquals("a", snapshot[0].key)
            assertEquals(500L, snapshot[0].lastAccessMs)
        }

    @Test
    fun forget_removesKey() =
        runTest {
            val log = KeyAccessLog(logFile, scope, flushIntervalMs = 1L)
            log.load()

            log.recordAccess("a", timeMs = 100)
            log.recordAccess("b", timeMs = 200)
            log.forget("a")
            log.flushNow()

            assertEquals(listOf("b"), log.snapshotOldestFirst().map { it.key })
        }

    @Test
    fun forgetAll_clearsLogFile() =
        runTest {
            val log = KeyAccessLog(logFile, scope, flushIntervalMs = 1L)
            log.load()

            log.recordAccess("a", timeMs = 100)
            log.flushNow()
            assertTrue(logFile.exists())

            log.forgetAll()
            // Give the IO-dispatched delete a moment.
            kotlinx.coroutines.delay(100)

            assertEquals(0, log.snapshotOldestFirst().size)
        }

    @Test
    fun snapshotOldestFirst_sortsByTimestamp() =
        runTest {
            val log = KeyAccessLog(logFile, scope, flushIntervalMs = 1L)
            log.load()

            log.recordAccess("newest", timeMs = 500)
            log.recordAccess("oldest", timeMs = 100)
            log.recordAccess("middle", timeMs = 300)
            log.flushNow()

            val snapshot = log.snapshotOldestFirst()
            assertEquals(listOf("oldest", "middle", "newest"), snapshot.map { it.key })
        }

    @Test
    fun load_isIdempotent() =
        runTest {
            val log = KeyAccessLog(logFile, scope, flushIntervalMs = 1L)
            log.recordAccess("a", timeMs = 100)
            log.flushNow()

            log.load()
            log.load() // second call should be a no-op
            assertEquals(1, log.snapshotOldestFirst().size)
        }

    @Test
    fun maxEntries_dropsOldestOnOverflow() =
        runTest {
            val log =
                KeyAccessLog(
                    logFile,
                    scope,
                    flushIntervalMs = 1L,
                    maxEntries = 3,
                    compactionRatio = 1,
                )
            log.load()

            for (i in 1..10) log.recordAccess("k$i", timeMs = i.toLong() * 100)
            log.flushNow()
            // Force compaction so overflow trimming runs.
            for (i in 11..20) log.recordAccess("k$i", timeMs = i.toLong() * 100)
            log.flushNow()

            // Reload and verify only the newest survived.
            val reloaded = KeyAccessLog(logFile, scope, flushIntervalMs = 1L, maxEntries = 3)
            reloaded.load()
            val keys = reloaded.snapshotOldestFirst().map { it.key }.toSet()
            assertTrue("only newest 3 should survive, got=$keys", keys.size <= 3)
            // The newest entries must include k20.
            assertTrue("k20 must survive", "k20" in keys)
        }

    @Test
    fun load_recoversFromCorruptedLines() =
        runTest {
            logFile.parentFile?.mkdirs()
            logFile.writeText(
                buildString {
                    appendLine("100\tgood-a")
                    appendLine("not a number\tbad")
                    appendLine("\tno-timestamp")
                    appendLine("200")
                    appendLine("300\tgood-b")
                },
            )

            val log = KeyAccessLog(logFile, scope, flushIntervalMs = 1L)
            log.load()
            val keys = log.snapshotOldestFirst().map { it.key }
            assertEquals(listOf("good-a", "good-b"), keys)
        }

    @Test
    fun keysWithTabOrNewline_areSkippedOnWrite() =
        runTest {
            val log = KeyAccessLog(logFile, scope, flushIntervalMs = 1L)
            log.load()

            log.recordAccess("ok", timeMs = 100)
            log.recordAccess("has\ttab", timeMs = 200)
            log.recordAccess("has\nnewline", timeMs = 300)
            log.flushNow()

            val reloaded = KeyAccessLog(logFile, scope, flushIntervalMs = 1L)
            reloaded.load()
            // Only the clean key should round-trip.
            assertEquals(listOf("ok"), reloaded.snapshotOldestFirst().map { it.key })
        }

    @Test
    fun size_reflectsInMemoryCount() =
        runTest {
            val log = KeyAccessLog(logFile, scope, flushIntervalMs = 1L)
            log.load()

            assertEquals(0, log.size())
            log.recordAccess("a", timeMs = 100)
            log.recordAccess("b", timeMs = 200)
            assertEquals(2, log.size())
            log.forget("a")
            assertEquals(1, log.size())

            assertNotNull(log.snapshotOldestFirst().firstOrNull { it.key == "b" })
            assertNull(log.snapshotOldestFirst().firstOrNull { it.key == "a" })
        }
}
