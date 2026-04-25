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
package com.vitorpamplona.quartz.nip01Core.store.fs

import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerSync
import com.vitorpamplona.quartz.utils.Secp256k1Instance
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.listDirectoryEntries
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FsExpirationTest {
    private val signer = NostrSignerSync()
    private lateinit var root: Path
    private var clockNow: Long = 1_000_000

    /**
     * Test subclass overriding the `now()` seam so we can drive NIP-40
     * expiration at exact timestamps — no wall-clock sleeps, no flakes.
     */
    private class ClockedStore(
        root: Path,
        private val source: () -> Long,
    ) : FsEventStore(root) {
        override fun now(): Long = source()
    }

    private lateinit var store: ClockedStore

    @BeforeTest
    fun setup() {
        Secp256k1Instance
        root = Files.createTempDirectory("fs-exp-")
        store = ClockedStore(root) { clockNow }
    }

    @AfterTest
    fun tearDown() {
        store.close()
        if (root.exists()) {
            Files.walk(root).use { it.sorted(Comparator.reverseOrder()).forEach { p -> Files.deleteIfExists(p) } }
        }
    }

    private fun expiringNote(
        body: String,
        createdAt: Long,
        expiresAt: Long,
    ): Event =
        signer.sign(
            createdAt = createdAt,
            kind = 1,
            tags = arrayOf(arrayOf("expiration", expiresAt.toString())),
            content = body,
        )

    @Test
    fun `event with future expiration is accepted and indexed`() {
        clockNow = 1_000
        val e = expiringNote("future", createdAt = 500, expiresAt = 2_000)
        store.insert(e)

        assertEquals(listOf(e.id), store.query<Event>(Filter(ids = listOf(e.id))).map { it.id })

        val expIdx = root.resolve("idx/expires_at")
        val entries = expIdx.listDirectoryEntries().map { it.fileName.toString() }
        assertEquals(1, entries.size, "expires_at index should hold exactly one entry")
        assertTrue(entries.single().endsWith("-${e.id}"))
        assertTrue(entries.single().startsWith("0000002000"), "filename should be padded expiration ts")
    }

    @Test
    fun `event already expired at insert time is rejected`() {
        clockNow = 5_000
        val e = expiringNote("dead-on-arrival", createdAt = 1_000, expiresAt = 4_000)
        store.insert(e)

        assertEquals(emptyList(), store.query<Event>(Filter(ids = listOf(e.id))).map { it.id })
        assertFalse(store.hasCanonical(e.id))
    }

    @Test
    fun `event with expiration equal to now is rejected (parity with SQLite trigger)`() {
        clockNow = 5_000
        val e = expiringNote("just-now", createdAt = 1_000, expiresAt = 5_000)
        store.insert(e)

        assertFalse(store.hasCanonical(e.id), "exp == now should be rejected (SQLite uses <=)")
    }

    @Test
    fun `non-positive expiration is ignored`() {
        clockNow = 5_000
        val zero = expiringNote("zero", createdAt = 1, expiresAt = 0)
        val neg = expiringNote("neg", createdAt = 2, expiresAt = -1)
        store.insert(zero)
        store.insert(neg)

        assertTrue(store.hasCanonical(zero.id))
        assertTrue(store.hasCanonical(neg.id))
        // And nothing in idx/expires_at.
        val expIdx = root.resolve("idx/expires_at")
        assertEquals(0, expIdx.listDirectoryEntries().size, "non-positive exp should not be indexed")
    }

    @Test
    fun `deleteExpiredEvents sweeps everything past now`() {
        clockNow = 1_000
        val a = expiringNote("a", createdAt = 100, expiresAt = 500) // already expired
        val b = expiringNote("b", createdAt = 200, expiresAt = 999) // expired in past
        val c = expiringNote("c", createdAt = 300, expiresAt = 2_000) // still alive

        // Insert at a fake earlier "now" so all three pass the insert guard.
        clockNow = 99
        store.insert(a)
        store.insert(b)
        store.insert(c)

        // Advance the clock and sweep.
        clockNow = 1_000
        store.deleteExpiredEvents()

        assertFalse(store.hasCanonical(a.id), "a should be swept")
        assertFalse(store.hasCanonical(b.id), "b should be swept")
        assertTrue(store.hasCanonical(c.id), "c should survive")
    }

    @Test
    fun `sweep uses strict less-than parity with SQLite`() {
        // SQLite trigger: WHERE NEW.expiration <= unixepoch()      (insert)
        // SQLite sweep:   WHERE expiration < unixepoch()           (delete)
        // Insert-time uses inclusive <=, sweep uses strict <.
        clockNow = 50
        val onTheTick = expiringNote("equal", createdAt = 10, expiresAt = 100)
        store.insert(onTheTick)

        clockNow = 100 // exp == now → sweep keeps it
        store.deleteExpiredEvents()
        assertTrue(store.hasCanonical(onTheTick.id), "exp == now should NOT be swept")

        clockNow = 101
        store.deleteExpiredEvents()
        assertFalse(store.hasCanonical(onTheTick.id), "exp < now should be swept")
    }

    @Test
    fun `sweep removes index entries too`() {
        clockNow = 50
        val e = expiringNote("x", createdAt = 1, expiresAt = 100)
        store.insert(e)

        val expIdx = root.resolve("idx/expires_at")
        assertEquals(1, expIdx.listDirectoryEntries().size)

        clockNow = 1_000
        store.deleteExpiredEvents()
        assertEquals(0, expIdx.listDirectoryEntries().size, "expires_at entry should be unlinked")

        // Author + kind index entries also gone.
        val authorDir = root.resolve("idx/author/${signer.pubKey}")
        if (authorDir.exists()) assertEquals(0, authorDir.listDirectoryEntries().size)
    }

    @Test
    fun `events without expiration are unaffected by sweep`() {
        clockNow = 100
        val plain =
            signer.sign<Event>(
                createdAt = 50,
                kind = 1,
                tags = emptyArray(),
                content = "plain",
            )
        store.insert(plain)

        clockNow = 1_000_000
        store.deleteExpiredEvents()
        assertTrue(store.hasCanonical(plain.id))
    }

    private fun FsEventStore.hasCanonical(id: String): Boolean {
        val p =
            root
                .resolve("events")
                .resolve(id.substring(0, 2))
                .resolve(id.substring(2, 4))
                .resolve("$id.json")
        return p.exists()
    }
}
