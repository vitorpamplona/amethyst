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
package com.vitorpamplona.geode.mirror

import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The catch-up's resume memory across restarts. Without it every boot
 * re-syncs each upstream's whole backfill window — a full re-download for an
 * upstream without NIP-77. These pin the restart round-trip and the window
 * clamping that keys bands on the stable filter rather than the sliding
 * boot window.
 */
class SyncCoverageFileTest {
    private val relay = RelayUrlNormalizer.normalize("wss://relay.example")
    private val profiles = Filter(kinds = listOf(0))

    private fun tempFile(): File {
        val f = File.createTempFile("sync-coverage", ".json")
        f.delete()
        return f
    }

    @Test
    fun `bands survive a restart`() {
        val f = tempFile()
        SyncCoverageFile(f).use {
            it.coverage.record(relay, profiles, 1_700_001_000L, 1_700_002_000L, paged = true)
        }

        // A fresh instance, as a restart would build.
        SyncCoverageFile(f).use { reopened ->
            val band = reopened.coverage.band(relay, profiles)!!
            assertEquals(1_700_001_000L, band.minCreatedAt)
            assertEquals(1_700_002_000L, band.maxCreatedAt)
            assertFalse(band.complete)
        }
    }

    @Test
    fun `a complete band survives with its completeness`() {
        val f = tempFile()
        SyncCoverageFile(f).use {
            it.coverage.record(relay, profiles, null, null, paged = false, reconciledThrough = 1_700_005_000L)
        }
        SyncCoverageFile(f).use { reopened ->
            assertTrue(reopened.coverage.band(relay, profiles)!!.complete)
        }
    }

    @Test
    fun `a corrupt file starts fresh instead of refusing to start`() {
        val f = tempFile()
        f.writeText("{ not json")
        SyncCoverageFile(f).use {
            assertNull(it.coverage.band(relay, profiles))
        }
    }

    @Test
    fun `recording does not write but closing does`() {
        val f = tempFile()
        val store = SyncCoverageFile(f)
        store.coverage.record(relay, profiles, 1_700_001_000L, 1_700_002_000L, paged = true)
        assertFalse(f.isFile, "a record marks dirty; only a flush writes")
        store.close()
        assertTrue(f.isFile, "close flushes")
    }

    @Test
    fun `reopening without new records does not rewrite the file`() {
        val f = tempFile()
        SyncCoverageFile(f).use {
            it.coverage.record(relay, profiles, 1_700_001_000L, 1_700_002_000L, paged = true)
        }
        val written = f.lastModified()
        SyncCoverageFile(f).close()
        assertEquals(written, f.lastModified(), "restoring bands must not mark the store dirty")
    }

    // ---- the window clamp --------------------------------------------------

    @Test
    fun `an unbanded filter clamps to exactly the boot window`() {
        val leg = clampToWindow(profiles, since = 1_000L, until = 2_000L)!!
        assertEquals(1_000L, leg.since)
        assertEquals(2_000L, leg.until)
    }

    @Test
    fun `a leg outside the window is dropped rather than inverted`() {
        // The band covers past the window's floor: the older leg would ask
        // [since..band.min] with since above until — a range nothing can be in.
        val olderLeg = profiles.copy(until = 500L)
        assertNull(clampToWindow(olderLeg, since = 1_000L, until = 2_000L))
    }

    @Test
    fun `a leg inside the window keeps its own tighter bound`() {
        val newerLeg = profiles.copy(since = 1_500L)
        val clamped = clampToWindow(newerLeg, since = 1_000L, until = 2_000L)!!
        assertEquals(1_500L, clamped.since, "the band's ceiling wins over the window floor")
        assertEquals(2_000L, clamped.until)
    }
}
