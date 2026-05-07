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
package com.vitorpamplona.geode.persistence

import com.vitorpamplona.geode.Relay
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer
import com.vitorpamplona.quartz.nip11RelayInfo.Nip11RelayInformation
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PersistenceTest {
    private lateinit var dir: File
    private lateinit var stateFile: File
    private val url = RelayUrlNormalizer.normalize("ws://127.0.0.1:7770/")

    @BeforeTest
    fun setup() {
        dir = Files.createTempDirectory("quartz-relay-persist-").toFile()
        stateFile = File(dir, "admin.json")
    }

    @AfterTest
    fun teardown() {
        dir.deleteRecursively()
    }

    @Test
    fun firstBootWritesNothingUntilFirstMutation() {
        val relay = Relay(url = url, stateFile = stateFile)
        try {
            // No mutation yet → file does not exist.
            assertTrue(!stateFile.exists(), "fresh relay must not eagerly write a snapshot")
        } finally {
            relay.close()
        }
    }

    @Test
    fun banPubkeyTriggersSnapshotAndSurvivesRestart() {
        val pk = "a".repeat(64)
        val r1 = Relay(url = url, stateFile = stateFile)
        try {
            r1.banStore.banPubkey(pk, "spam")
        } finally {
            r1.close()
        }
        assertTrue(stateFile.exists(), "snapshot must be written after a mutation")

        // Fresh relay reads the snapshot and sees the ban.
        val r2 = Relay(url = url, stateFile = stateFile)
        try {
            assertTrue(r2.banStore.isBanned(pk))
            assertEquals("spam", r2.banStore.listBannedPubkeys()[0].second)
        } finally {
            r2.close()
        }
    }

    @Test
    fun updateInfoSurvivesRestart() {
        val r1 = Relay(url = url, stateFile = stateFile)
        try {
            r1.updateInfo { it.copy(name = "renamed") }
        } finally {
            r1.close()
        }

        val r2 = Relay(url = url, stateFile = stateFile)
        try {
            assertEquals("renamed", r2.info.document.name)
        } finally {
            r2.close()
        }
    }

    @Test
    fun allowKindRoundTripsAcrossRestart() {
        val r1 = Relay(url = url, stateFile = stateFile)
        try {
            r1.banStore.allowKind(1)
            r1.banStore.allowKind(7)
            r1.banStore.disallowKind(4)
        } finally {
            r1.close()
        }

        val r2 = Relay(url = url, stateFile = stateFile)
        try {
            assertEquals(listOf(1, 7), r2.banStore.listAllowedKinds())
            assertEquals(listOf(4), r2.banStore.listDisallowedKinds())
        } finally {
            r2.close()
        }
    }

    @Test
    fun corruptStateFileIsTolerated() {
        stateFile.writeText("not valid json {")
        // Should not throw — just log and start fresh.
        val r = Relay(url = url, stateFile = stateFile)
        try {
            assertTrue(r.banStore.listBannedPubkeys().isEmpty())
            assertTrue(r.banStore.listAllowedKinds().isEmpty())
        } finally {
            r.close()
        }
    }

    @Test
    fun snapshotWriteIsAtomicViaTempFile() {
        // After a mutation completes, no `.tmp` file should remain.
        val r = Relay(url = url, stateFile = stateFile)
        try {
            r.banStore.banPubkey("b".repeat(64))
            val tmp = File(dir, "admin.json.tmp")
            assertTrue(!tmp.exists(), "tmp file must be moved into place, not left behind")
        } finally {
            r.close()
        }
    }

    @Test
    fun missingStateFileMeansInMemoryOnly() {
        val r = Relay(url = url) // no stateFile
        try {
            r.banStore.banPubkey("c".repeat(64))
            // No snapshot path → nothing on disk in our temp dir.
            assertNull(dir.list()?.firstOrNull { it.startsWith("admin") })
        } finally {
            r.close()
        }
    }

    @Test
    fun stateStoreRoundTripsAllSections() {
        val ss = RelayStateStore(stateFile)
        val state =
            RelayPersistedState(
                info = Nip11RelayInformation(name = "x", description = "y"),
                bannedPubkeys = listOf(BannedEntry("aa", "spam"), BannedEntry("bb", null)),
                allowedPubkeys = listOf(BannedEntry("cc", "trusted")),
                bannedEvents = listOf(BannedEntry("dd", "off-topic")),
                allowedKinds = listOf(1, 7),
                disallowedKinds = listOf(4, 1059),
            )
        ss.save(state)
        val loaded = ss.load()!!
        assertEquals("x", loaded.info!!.name)
        assertEquals(2, loaded.bannedPubkeys.size)
        assertEquals(listOf(1, 7), loaded.allowedKinds)
        assertEquals(listOf(4, 1059), loaded.disallowedKinds)
    }

    /**
     * Manual `Nip11RelayInformation.copy` — the class isn't a data
     * class so Kotlin doesn't generate one. We only need `name` here.
     */
    private fun Nip11RelayInformation.copy(name: String? = this.name) =
        Nip11RelayInformation(
            id = id,
            name = name,
            description = description,
            icon = icon,
            pubkey = pubkey,
            self = self,
            contact = contact,
            supported_nips = supported_nips,
            supported_nip_extensions = supported_nip_extensions,
            software = software,
            version = version,
            limitation = limitation,
            relay_countries = relay_countries,
            language_tags = language_tags,
            tags = tags,
            posting_policy = posting_policy,
            privacy_policy = privacy_policy,
            terms_of_service = terms_of_service,
            payments_url = payments_url,
            retention = retention,
            fees = fees,
            nip50 = nip50,
            supported_grasps = supported_grasps,
        )
}
