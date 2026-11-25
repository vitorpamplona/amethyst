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
package com.vitorpamplona.amethyst.commons.model.marmot

import com.vitorpamplona.amethyst.commons.model.marmot.devices.DeviceGroupBlobs
import com.vitorpamplona.amethyst.commons.model.marmot.devices.MarmotImportedHistoryReads
import com.vitorpamplona.amethyst.commons.model.marmot.devices.importedHistoryReadUpTo
import com.vitorpamplona.quartz.nip01Core.core.Event
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MarmotImportedHistoryReadsTest {
    private class MemoryBlobs : DeviceGroupBlobs {
        val saved = HashMap<String, ByteArray>()

        override suspend fun load(name: String) = saved[name]

        override suspend fun save(
            name: String,
            bytes: ByteArray,
        ) {
            saved[name] = bytes
        }
    }

    private val chat = "cc".repeat(32)
    private val other = "dd".repeat(32)
    private val now = 1_800_000_000L

    /** 2030-ish: a sender's clock, or a sender's choice. */
    private val future = 1_900_000_000L

    private fun event(createdAt: Long) = Event("ee".repeat(32), "ff".repeat(32), createdAt, 9, emptyArray(), "m", "")

    private fun events(vararg createdAts: Long) = createdAts.map { event(it) }

    private class FailingBlobs : DeviceGroupBlobs {
        override suspend fun load(name: String): ByteArray? = null

        override suspend fun save(
            name: String,
            bytes: ByteArray,
        ): Unit = throw IllegalStateException("disk full")
    }

    @Test
    fun anImportIsReadUpToItsNewestMessageButNeverPastNow() {
        assertEquals(now - 5, importedHistoryReadUpTo(listOf(now - 50, now - 5), now))
        assertEquals("one message dated years ahead must not silence the chat until then", now, importedHistoryReadUpTo(listOf(now - 50, future), now))
        assertNull(importedHistoryReadUpTo(emptyList(), now))
    }

    /**
     * The import is written down before marmot-kmp stores it, so a process that dies before the app
     * shows it still restores those messages as read.
     */
    @Test
    fun theReadTimesSurviveARestartAndOnlyMoveForward() =
        runBlocking {
            val blobs = MemoryBlobs()
            val first = MarmotImportedHistoryReads(blobs)
            first.record(chat, events(now - 100, now - 10), now)
            first.record(other, events(future), now)

            val restarted = MarmotImportedHistoryReads(blobs)
            assertEquals(mapOf(chat to now - 10, other to now), restarted.load())

            restarted.record(chat, events(now - 500), now)
            assertEquals("an older import does not move it back", now - 10, MarmotImportedHistoryReads(blobs).load()[chat])
        }

    /** A save that fails throws, so the import waits, and keeps nothing in memory that would skip the next save. */
    @Test
    fun aSaveThatFailsThrowsAndIsTriedAgain() =
        runBlocking {
            val failing = MarmotImportedHistoryReads(FailingBlobs())
            assertTrue(runCatching { failing.record(chat, events(now - 10), now) }.isFailure)
            assertTrue(failing.load().isEmpty())
            assertTrue("and again on the next try", runCatching { failing.record(chat, events(now - 10), now) }.isFailure)
        }
}
