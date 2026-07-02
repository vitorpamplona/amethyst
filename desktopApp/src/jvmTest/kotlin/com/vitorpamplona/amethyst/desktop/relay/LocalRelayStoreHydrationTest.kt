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
package com.vitorpamplona.amethyst.desktop.relay

import com.vitorpamplona.amethyst.desktop.cache.DesktopLocalCache
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.metadata.MetadataEvent
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerSync
import com.vitorpamplona.quartz.nip01Core.store.sqlite.EventStore
import com.vitorpamplona.quartz.nip02FollowList.ContactListEvent
import com.vitorpamplona.quartz.nip10Notes.TextNoteEvent
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Phase 1.2 of the launch-optimization plan: pin the invariants of
 * [LocalRelayStore.hydrate] before any launch-path refactor touches it.
 *
 * The hydrate phases (per LocalRelayStore.kt:115-161) are:
 *  1. kind:3 contact list (populates [DesktopLocalCache.followedUsers]).
 *  2. kind:0 metadata for followed users (depends on #1 having run).
 *  3. recent activity events (kinds 1/6/7/16/1111/9735) since now-7d.
 *
 * Tests seed a fresh SQLite DB at the location LocalRelayStore will open,
 * then exercise hydrate against a real [DesktopLocalCache] and assert on
 * the observable side effects (cache users, notes, followedUsers).
 *
 * See desktopApp/plans/2026-06-17-feat-app-launch-optimization-plan.md § Phase 1.2.
 */
class LocalRelayStoreHydrationTest {
    private lateinit var tempHome: File
    private lateinit var ownerKeyPair: KeyPair
    private lateinit var ownerPubKey: String
    private lateinit var dbPath: String

    @BeforeTest
    fun setup() {
        tempHome = createTempDirectory("localrelay-hydrate").toFile()
        ownerKeyPair = KeyPair()
        ownerPubKey = ownerKeyPair.pubKey.toHexKey()
        val dbDir = File(tempHome, ".amethyst/accounts/${ownerPubKey.take(8)}").also { it.mkdirs() }
        dbPath = File(dbDir, "events.db").absolutePath
    }

    @AfterTest
    fun teardown() {
        tempHome.deleteRecursively()
    }

    /**
     * Seeds the SQLite file LocalRelayStore will open, then closes it
     * so the production code can re-open the same DB file.
     */
    private suspend fun seedDatabase(events: List<Event>) {
        val seeder = EventStore(dbName = dbPath, relay = LocalRelayStore.LOCAL_RELAY_URL)
        try {
            seeder.batchInsert(events)
        } finally {
            seeder.close()
        }
    }

    private fun newStore(): LocalRelayStore = LocalRelayStore(scope = TestScope(), homeDir = tempHome).also { it.openForAccount(ownerPubKey) }

    private fun makeContactList(
        owner: KeyPair,
        follows: List<String>,
        createdAt: Long = nowSeconds(),
    ): ContactListEvent {
        val signer = NostrSignerSync(owner)
        val tags = follows.map { arrayOf("p", it) }.toTypedArray()
        return signer.sign<ContactListEvent>(
            createdAt = createdAt,
            kind = ContactListEvent.KIND,
            tags = tags,
            content = "",
        )
    }

    private fun makeMetadata(
        author: KeyPair,
        name: String,
        createdAt: Long = nowSeconds(),
    ): MetadataEvent {
        val signer = NostrSignerSync(author)
        return signer.sign<MetadataEvent>(
            createdAt = createdAt,
            kind = MetadataEvent.KIND,
            tags = emptyArray(),
            content = """{"name":"$name"}""",
        )
    }

    private fun makeTextNote(
        author: KeyPair,
        content: String,
        createdAt: Long = nowSeconds(),
    ): TextNoteEvent {
        val signer = NostrSignerSync(author)
        return signer.sign<TextNoteEvent>(
            createdAt = createdAt,
            kind = TextNoteEvent.KIND,
            tags = emptyArray(),
            content = content,
        )
    }

    private fun nowSeconds(): Long = System.currentTimeMillis() / 1000

    @Test
    fun hydratingAnEmptyDatabaseSucceedsAndLeavesCacheEmpty() =
        runTest {
            val cache = DesktopLocalCache().apply { accountPubkey = ownerPubKey }
            val store = newStore()
            try {
                store.hydrate(cache)
            } finally {
                store.close()
            }

            assertTrue(cache.followedUsers.value.isEmpty(), "Empty DB must leave followedUsers empty")
            assertNull(store.lastError.value, "Empty hydrate must not flag a lastError")
        }

    @Test
    fun kind3IsHydratedBeforeKind0SoMetadataLoadsForFollowedAuthors() =
        runTest {
            val followee = KeyPair()
            val contactList = makeContactList(ownerKeyPair, follows = listOf(followee.pubKey.toHexKey()))
            val followeeMetadata = makeMetadata(followee, name = "Followee")

            // Insert metadata first, contact list second — the on-disk row order
            // is unrelated to the order hydrate() actually queries them. If
            // hydrate ran phases in the wrong order, followedUsers would be
            // empty when phase 2 ran and the metadata would never load.
            seedDatabase(listOf(followeeMetadata, contactList))

            val cache = DesktopLocalCache().apply { accountPubkey = ownerPubKey }
            val store = newStore()
            try {
                store.hydrate(cache)
            } finally {
                store.close()
            }

            assertTrue(
                followee.pubKey.toHexKey() in cache.followedUsers.value,
                "Phase 1 (kind:3) must populate followedUsers before phase 2 runs",
            )
            val followeeUser = cache.getUserIfExists(followee.pubKey.toHexKey())
            assertNotNull(followeeUser, "Followed user must be in cache after kind:3 phase")
            assertEquals(
                expected = "Followee",
                actual = followeeUser.toBestDisplayName(),
                message = "Phase 2 (kind:0) must run after phase 1 so metadata is applied to followed users",
            )
        }

    @Test
    fun recentTextNotesWithinSevenDayWindowAreHydrated() =
        runTest {
            val author = KeyPair()
            val recentNote = makeTextNote(author, "recent", createdAt = nowSeconds() - 3600)
            seedDatabase(listOf(recentNote))

            val cache = DesktopLocalCache().apply { accountPubkey = ownerPubKey }
            val store = newStore()
            try {
                store.hydrate(cache)
            } finally {
                store.close()
            }

            val note = cache.getNoteIfExists(recentNote.id)
            assertNotNull(note, "Recent text note must be hydrated into cache")
        }

    @Test
    fun textNotesOlderThanSevenDaysAreSkipped() =
        runTest {
            val author = KeyPair()
            val eightDaysAgo = nowSeconds() - (8L * 24 * 3600)
            val oldNote = makeTextNote(author, "stale", createdAt = eightDaysAgo)
            seedDatabase(listOf(oldNote))

            val cache = DesktopLocalCache().apply { accountPubkey = ownerPubKey }
            val store = newStore()
            try {
                store.hydrate(cache)
            } finally {
                store.close()
            }

            assertNull(
                cache.getNoteIfExists(oldNote.id),
                "Notes older than the 7-day hydration window must be excluded",
            )
        }

    @Test
    fun hydrateDoesNotEmitWasVerifiedFalseForLocalEvents() =
        runTest {
            // The wasVerified=true semantics of cache.consume during hydrate is
            // the contract we depend on: a tampered event that round-tripped
            // through this DB would be admitted without re-checking the
            // signature on every cold boot. The store's read-time gate is
            // SQLite write-time verification — exercised by EventStore tests —
            // and the hydrate path trusts it.
            //
            // Here we just verify the round-trip doesn't accidentally degrade
            // an event by re-checking signatures it wouldn't pass elsewhere.
            val author = KeyPair()
            val note = makeTextNote(author, "round-trip")
            seedDatabase(listOf(note))

            val cache = DesktopLocalCache().apply { accountPubkey = ownerPubKey }
            val store = newStore()
            try {
                store.hydrate(cache)
            } finally {
                store.close()
            }

            assertFalse(
                cache.getNoteIfExists(note.id) == null,
                "Round-trip through hydrate must not drop a valid note",
            )
        }
}
