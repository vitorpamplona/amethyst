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
package com.vitorpamplona.quartz.nip01Core.store

import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.OptimizedJsonMapper
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerSync
import com.vitorpamplona.quartz.nip01Core.store.sqlite.DefaultIndexingStrategy
import com.vitorpamplona.quartz.nip01Core.store.sqlite.EventStore
import com.vitorpamplona.quartz.nip10Notes.TextNoteEvent
import com.vitorpamplona.quartz.utils.EventFactory
import kotlinx.coroutines.runBlocking
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Guards [NdjsonImportExport]: the NDJSON import/export round-trip, its counts,
 * duplicate handling, malformed-line skipping, and — the security-relevant part —
 * that verification gates a bad signature while still admitting a good one.
 */
class NdjsonImportExportTest {
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

    private val fakeSig = "0".repeat(128)

    private fun hex64(n: Int): String = n.toString(16).padStart(64, '0')

    /** A structurally-valid kind-1 event with a fake (cryptographically-invalid) sig. */
    private fun fake(i: Int): Event = EventFactory.create(hex64(i), hex64(1_000_000 + i), i.toLong(), 1, emptyArray(), "note-$i", fakeSig)

    private fun ndjson(events: List<Event>): Sequence<String> = events.asSequence().map { it.toJson() }

    @Test
    fun importThenExport_roundTrips() =
        runBlocking {
            val events = (1..25).map { fake(it) }
            val stats = NdjsonImportExport.import(store, ndjson(events), verify = false)

            assertEquals(25L, stats.read)
            assertEquals(25L, stats.imported)
            assertEquals(0L, stats.rejected)
            assertEquals(0L, stats.invalid)
            assertEquals(0L, stats.malformed)

            val out = StringBuilder()
            val exported = NdjsonImportExport.export(store, out)
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
            val events = (1..10).map { fake(it) }
            NdjsonImportExport.import(store, ndjson(events), verify = false)

            val second = NdjsonImportExport.import(store, ndjson(events), verify = false)
            assertEquals(10L, second.read)
            assertEquals(0L, second.imported)
            assertEquals(10L, second.rejected, "the store's unique-id constraint drops the re-import")
        }

    @Test
    fun malformedAndBlankLines_areSkipped() =
        runBlocking {
            val good = (1..3).map { fake(it) }
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
            val stats = NdjsonImportExport.import(store, lines, verify = false)

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
            val fakes = (1..8).map { fake(it) }
            val fakeStats = NdjsonImportExport.import(store, ndjson(fakes), verify = true)
            assertEquals(8L, fakeStats.read)
            assertEquals(0L, fakeStats.imported)
            assertEquals(8L, fakeStats.invalid, "bad signatures must be rejected under verify")

            // Genuinely-signed events (fresh key, real Schnorr signatures) must pass.
            val signer = NostrSignerSync(KeyPair())
            val real = (1..5).map { signer.sign(TextNoteEvent.build("import verify $it", createdAt = it.toLong())) }
            val realStats = NdjsonImportExport.import(store, ndjson(real), verify = true)
            assertEquals(5L, realStats.read)
            assertEquals(0L, realStats.invalid, "correctly-signed events must not be flagged invalid")
            assertEquals(5L, realStats.imported, "valid events must be admitted under verify")
        }
}
