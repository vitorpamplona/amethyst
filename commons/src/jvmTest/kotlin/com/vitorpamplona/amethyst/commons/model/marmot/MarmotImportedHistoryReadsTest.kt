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
import com.vitorpamplona.marmot.features.deviceGroup.MarmotDeviceGroupManager
import com.vitorpamplona.quartz.nip01Core.core.Event
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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

    private fun imported(
        id: String,
        vararg createdAts: Long,
    ) = MarmotDeviceGroupManager.ImportedHistory(id, createdAts.map { event(it) })

    @Test
    fun anImportIsReadUpToItsNewestMessageButNeverPastNow() {
        assertEquals(now - 5, importedHistoryReadUpTo(listOf(now - 50, now - 5), now))
        assertEquals("one message dated years ahead must not silence the chat until then", now, importedHistoryReadUpTo(listOf(now - 50, future), now))
        assertNull(importedHistoryReadUpTo(emptyList(), now))
    }

    /**
     * The import is written down when the device group hands it over, so a process that dies before
     * the app shows it still restores those messages as read.
     */
    @Test
    fun theReadTimesSurviveARestartAndOnlyMoveForward() =
        runBlocking {
            val blobs = MemoryBlobs()
            val first = MarmotImportedHistoryReads(blobs)
            first.record(listOf(imported(chat, now - 100, now - 10), imported(other, future)), now)

            val restarted = MarmotImportedHistoryReads(blobs)
            assertEquals(mapOf(chat to now - 10, other to now), restarted.load())

            restarted.record(listOf(imported(chat, now - 500)), now)
            assertEquals("an older import does not move it back", now - 10, MarmotImportedHistoryReads(blobs).load()[chat])
        }
}
