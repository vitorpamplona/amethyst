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
package com.vitorpamplona.geode

import com.vitorpamplona.geode.fixtures.SyntheticEvents
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.OptimizedJsonMapper
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerSync
import com.vitorpamplona.quartz.nip01Core.store.sqlite.DefaultIndexingStrategy
import com.vitorpamplona.quartz.nip01Core.store.sqlite.EventStore
import com.vitorpamplona.quartz.nip10Notes.TextNoteEvent
import kotlinx.coroutines.runBlocking
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Guards the `geode import` / `geode export` NDJSON round-trip: import counts,
 * duplicate handling, malformed-line skipping, and — the security-relevant part —
 * that verification actually gates a bad signature while still admitting a good one.
 */
class ImportExportTest {
    private val store =
        EventStore(
            dbName = null,
            indexStrategy =
                DefaultIndexingStrategy(
                    indexEventsByCreatedAtAlone = true,
                    indexEventsByPubkeyAlone = true,
                    indexFullTextSearch = false,
                ),
        )

    @AfterTest
    fun tearDown() = store.close()

    private fun ndjson(events: List<Event>): Sequence<String> = events.asSequence().map { it.toJson() }

    @Test
    fun importThenExport_roundTrips() =
        runBlocking {
            val events = SyntheticEvents.batch(count = 25)
            val stats = ImportExport.import(store, ndjson(events), verify = false)

            assertEquals(25L, stats.read)
            assertEquals(25L, stats.imported)
            assertEquals(0L, stats.rejected)
            assertEquals(0L, stats.invalid)
            assertEquals(0L, stats.malformed)

            val out = StringBuilder()
            val exported = ImportExport.export(store, out)
            assertEquals(25L, exported)

            val backIds =
                out
                    .trim()
                    .lineSequence()
                    .map { OptimizedJsonMapper.fromJson(it).id }
                    .toSet()
            assertEquals(events.map { it.id }.toSet(), backIds, "every imported event must round-trip through export")
        }

    @Test
    fun reimport_countsDuplicatesAsRejected() =
        runBlocking {
            val events = SyntheticEvents.batch(count = 10)
            ImportExport.import(store, ndjson(events), verify = false)

            val second = ImportExport.import(store, ndjson(events), verify = false)
            assertEquals(10L, second.read)
            assertEquals(0L, second.imported)
            assertEquals(10L, second.rejected, "the store's unique-id constraint drops the re-import")
        }

    @Test
    fun malformedAndBlankLines_areSkipped() =
        runBlocking {
            val good = SyntheticEvents.batch(count = 3)
            val lines =
                sequenceOf(
                    good[0].toJson(),
                    "",
                    "{not a valid event}",
                    good[1].toJson(),
                    "   ",
                    "[\"NOTANEVENT\"]",
                    good[2].toJson(),
                )
            val stats = ImportExport.import(store, lines, verify = false)

            assertEquals(5L, stats.read, "blank lines are not counted as read")
            assertEquals(3L, stats.imported)
            assertEquals(2L, stats.malformed)
            assertEquals(0L, stats.invalid)
        }

    @Test
    fun verify_rejectsBadSignaturesButKeepsValidOnes() =
        runBlocking {
            // Fake events carry a syntactically-valid but cryptographically-wrong
            // signature — verification must drop them all.
            val fakes = SyntheticEvents.batch(count = 8)
            val fakeStats = ImportExport.import(store, ndjson(fakes), verify = true)
            assertEquals(8L, fakeStats.read)
            assertEquals(0L, fakeStats.imported)
            assertEquals(8L, fakeStats.invalid, "bad signatures must be rejected under verify")

            // Genuinely-signed events (a fresh key, real Schnorr signatures) must
            // pass verification and land in the store.
            val signer = NostrSignerSync(KeyPair())
            val real = (1..5).map { signer.sign(TextNoteEvent.build("import verify $it", createdAt = it.toLong())) }
            val realStats = ImportExport.import(store, ndjson(real), verify = true)
            assertEquals(5L, realStats.read)
            assertEquals(0L, realStats.invalid, "correctly-signed events must not be flagged invalid")
            assertEquals(5L, realStats.imported, "valid events must be admitted under verify")
        }
}
