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
package com.vitorpamplona.quartz.nip01Core.store.sqlite

import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerSync
import kotlinx.coroutines.runBlocking
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.deleteIfExists
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The v5 → v6 upgrade: `d_tag` follows the kind (NIP-01), not the parsed class.
 * A v5 store kept `d_tag` NULL for addressable kinds Quartz had no class for, so
 * their versions never replaced each other. On open they get their `d`, and of
 * each address only the newest version stays.
 *
 * The v5 state is fabricated: events inserted by current code, then their
 * `d_tag` nulled and `user_version` stamped 5.
 */
class AddressableByKindMigrationTest {
    private val signer = NostrSignerSync()
    private lateinit var dbFile: Path

    private fun path() = dbFile.toAbsolutePath().toString()

    @BeforeTest
    fun setup() {
        dbFile = Files.createTempFile("addressable-by-kind-", ".db")
        Files.deleteIfExists(dbFile)
    }

    @AfterTest
    fun tearDown() {
        listOf("", "-wal", "-shm", "-journal").forEach { Path.of(dbFile.toString() + it).deleteIfExists() }
    }

    private fun card(
        createdAt: Long,
        vararg tags: Array<String>,
    ) = signer.sign<Event>(createdAt, UNKNOWN_ADDRESSABLE, arrayOf(*tags), "")

    @Test
    fun givesUnknownAddressableKindsTheirAddress() =
        runBlocking {
            val xOld = card(100, arrayOf("d", "x"))
            val xNew = card(200, arrayOf("d", "x"))
            val yOld = card(100, arrayOf("d"), arrayOf("d", "y"))
            val yNew = card(300, arrayOf("d", "y"))
            val noD = card(100, arrayOf("t", "no identifier"))
            val relays = signer.sign<Event>(100, 10002, arrayOf(arrayOf("r", "wss://a.example")), "")

            // 1. What a v5 store held: x's old version and both y versions with d_tag NULL
            //    (never replaced), x's new version stored after them.
            EventStore(dbName = path(), relay = null).also {
                it.insert(xOld)
                it.insert(yOld)
                it.insert(noD)
                it.insert(relays)
                it.close()
            }
            BundledSQLiteDriver().open(path()).use { db ->
                db.execSQL("DROP TRIGGER event_headers_prevent_update")
                db.execSQL("UPDATE event_headers SET d_tag = NULL, atag_hash = NULL WHERE kind = $UNKNOWN_ADDRESSABLE")
                db.execSQL(EventIndexesModule.PREVENT_HEADER_UPDATES)
            }
            EventStore(dbName = path(), relay = null).also {
                it.insert(xNew)
                it.close()
            }
            BundledSQLiteDriver().open(path()).use { db ->
                db.execSQL("DROP TRIGGER event_headers_prevent_update")
                db.execSQL("UPDATE event_headers SET d_tag = NULL, atag_hash = NULL WHERE id = '${noD.id}'")
                db.execSQL(EventIndexesModule.PREVENT_HEADER_UPDATES)
                db.execSQL(
                    "INSERT INTO event_headers (id, pubkey, created_at, kind, tags, content, sig, d_tag, pubkey_owner_hash, etag_hash, atag_hash) " +
                        "SELECT '${yNew.id}', pubkey, ${yNew.createdAt}, kind, '[[\"d\",\"y\"]]', '', '${yNew.sig}', NULL, pubkey_owner_hash, 0, NULL " +
                        "FROM event_headers WHERE id = '${yOld.id}'",
                )
                db.execSQL("PRAGMA user_version = 5")
            }

            // 2. Reopen: onUpgrade(5 → 6).
            val store = EventStore(dbName = path(), relay = null)
            try {
                val kept = store.query<Event>(Filter(kinds = listOf(UNKNOWN_ADDRESSABLE))).map { it.id }.toSet()
                assertEquals(setOf(xNew.id, yNew.id, noD.id), kept, "one version per address, the newest")

                val d = store.nql("SELECT id, d FROM events WHERE kind = ? ORDER BY d", listOf(UNKNOWN_ADDRESSABLE.toLong())).rows
                assertEquals(listOf(listOf<Any?>(noD.id, ""), listOf<Any?>(xNew.id, "x"), listOf<Any?>(yNew.id, "y")), d)

                // `#d` reads d_tag, so the migrated rows answer it.
                val byD = store.query<Event>(Filter(kinds = listOf(UNKNOWN_ADDRESSABLE), tags = mapOf("d" to listOf("y"))))
                assertEquals(listOf(yNew.id), byD.map { it.id })

                // A later version of the address now replaces the migrated one.
                val yNewest = card(400, arrayOf("d", "y"))
                store.insert(yNewest)
                assertEquals(listOf(yNewest.id), store.query<Event>(Filter(kinds = listOf(UNKNOWN_ADDRESSABLE), tags = mapOf("d" to listOf("y")))).map { it.id })

                // Replaceable kinds keep d_tag '' for a-tag deletions, but NQL's `d` is NULL for them.
                assertEquals(listOf(listOf<Any?>(null)), store.nql("SELECT d FROM events WHERE kind = 10002").rows)
            } finally {
                store.close()
            }
        }

    companion object {
        /** An addressable kind no Quartz class parses. */
        const val UNKNOWN_ADDRESSABLE = 31_999
    }
}
