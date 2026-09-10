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
package com.vitorpamplona.amethyst.model

import com.vitorpamplona.quartz.nip01Core.core.Address
import com.vitorpamplona.quartz.nip89AppHandlers.definition.AppDefinitionEvent
import com.vitorpamplona.quartz.nip90Dvms.dvmHeartbeat.DvmHeartbeatEvent
import com.vitorpamplona.quartz.utils.TimeUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * `LocalCache` is a process-wide object and JUnit 4 runs methods in hash order, so every test
 * uses its own pubkeys/dTags/ids (same discipline as ReportNamingIndexIngestionTest).
 */
class DvmHeartbeatTest {
    private val appDefPubKey = "aa".repeat(32)

    private fun appDef(
        dTag: String,
        pubKey: String = appDefPubKey,
    ) = AppDefinitionEvent(
        id = "b0".repeat(32),
        pubKey = pubKey,
        createdAt = 1_760_000_000L,
        tags = arrayOf(arrayOf("d", dTag), arrayOf("k", "5300")),
        content = """{"name":"Test DVM"}""",
        sig = "cc".repeat(64),
    )

    private fun beat(
        dTag: String,
        pubKey: String = appDefPubKey,
        createdAt: Long,
        id: String,
    ) = DvmHeartbeatEvent(
        id = id,
        pubKey = pubKey,
        createdAt = createdAt,
        tags =
            arrayOf(
                arrayOf("d", dTag),
                arrayOf("status", "My heart keeps beating like a hammer"),
                arrayOf("expiration", (createdAt + 300).toString()),
            ),
        content = "Alive and kicking",
        sig = "dd".repeat(64),
    )

    @Test
    fun aConsumedHeartbeatLandsAtTheAnnouncementMirrorAddress() {
        val app = appDef("dvm-one")
        LocalCache.justConsume(beat("dvm-one", createdAt = 1_760_000_100L, id = "e0".repeat(32)), null, true)

        val found = LocalCache.dvmHeartbeatOf(app)
        assertTrue("heartbeat should be found via the announcement's address", found != null)
        assertEquals(1_760_000_100L, found?.createdAt)
        assertEquals(Address(11998, appDefPubKey, "dvm-one"), found?.address())
    }

    @Test
    fun aFreshHeartbeatPassesTheGateAndAStaleOneDoesNot() {
        val now = 1_760_000_000L
        // Separate dTags: consumeBaseReplaceable only accepts NEWER beats per address, so a
        // 421s-old beat could never supersede the 420s one within a single address slot.
        val freshApp = appDef("dvm-two-fresh")
        val staleApp = appDef("dvm-two-stale")
        assertNull("no beat yet", LocalCache.dvmHeartbeatOf(freshApp))

        LocalCache.justConsume(beat("dvm-two-fresh", createdAt = now - 420, id = "e1".repeat(32)), null, true)
        LocalCache.justConsume(beat("dvm-two-stale", createdAt = now - 421, id = "e2".repeat(32)), null, true)

        assertTrue("exactly 420s old counts as fresh", LocalCache.hasFreshDvmHeartbeat(freshApp, now))
        assertFalse("421s old is stale", LocalCache.hasFreshDvmHeartbeat(staleApp, now))
    }

    @Test
    fun theNewestBeatPerAddressWins() {
        val now = 1_760_000_000L
        val app = appDef("dvm-three")
        LocalCache.justConsume(beat("dvm-three", createdAt = now - 600, id = "e3".repeat(32)), null, true)
        LocalCache.justConsume(beat("dvm-three", createdAt = now - 60, id = "e4".repeat(32)), null, true)

        assertEquals(now - 60, LocalCache.dvmHeartbeatOf(app)?.createdAt)
    }

    @Test
    fun beatsAreKeyedByDTagSoDifferentDvmsDoNotCollide() {
        val now = 1_760_000_000L
        val appA = appDef("dvm-a")
        val appB = appDef("dvm-b")
        LocalCache.justConsume(beat("dvm-a", createdAt = now - 60, id = "e5".repeat(32)), null, true)

        assertTrue(LocalCache.hasFreshDvmHeartbeat(appA, now))
        assertFalse("no beat for dvm-b", LocalCache.hasFreshDvmHeartbeat(appB, now))
    }

    @Test
    fun noHeartbeatMeansNoLiveness() {
        assertFalse(LocalCache.hasFreshDvmHeartbeat(appDef("dvm-never"), TimeUtils.now()))
    }
}
