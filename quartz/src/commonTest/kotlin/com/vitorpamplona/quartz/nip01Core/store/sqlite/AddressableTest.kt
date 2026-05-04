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

import androidx.sqlite.SQLiteException
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerSync
import com.vitorpamplona.quartz.nip23LongContent.LongTextNoteEvent
import com.vitorpamplona.quartz.utils.TimeUtils
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class AddressableTest : BaseDBTest() {
    val signer = NostrSignerSync()

    @Test
    fun testReplacingAddressables() =
        forEachDB { db ->
            val time = TimeUtils.now()
            val version1 = signer.sign(LongTextNoteEvent.build("my cool blog, version 1", "title", dTag = "my-cool-blog", createdAt = time))
            val version2 = signer.sign(LongTextNoteEvent.build("my cool blog, version 2", "title", dTag = "my-cool-blog", createdAt = time + 1))
            val version3 = signer.sign(LongTextNoteEvent.build("my cool blog, version 3", "title", dTag = "my-cool-blog", createdAt = time + 2))

            val addressableQuery = Filter(kinds = listOf(version1.kind), authors = listOf(version1.pubKey), tags = mapOf("d" to listOf(version1.dTag())))

            db.insert(version1)

            db.assertQuery(version1, Filter(ids = listOf(version1.id)))
            db.assertQuery(version1, addressableQuery)

            db.insert(version2)

            db.assertQuery(null, Filter(ids = listOf(version1.id)))
            db.assertQuery(version2, Filter(ids = listOf(version2.id)))
            db.assertQuery(version2, addressableQuery)

            db.insert(version3)

            db.assertQuery(null, Filter(ids = listOf(version1.id)))
            db.assertQuery(null, Filter(ids = listOf(version2.id)))
            db.assertQuery(version3, Filter(ids = listOf(version3.id)))
            db.assertQuery(version3, addressableQuery)
        }

    @Test
    fun testBlockingOldAddressables() =
        forEachDB { db ->
            val time = TimeUtils.now()
            val version1 = signer.sign(LongTextNoteEvent.build("my cool blog, version 1", "title", dTag = "my-cool-blog", createdAt = time))
            val version2 = signer.sign(LongTextNoteEvent.build("my cool blog, version 2", "title", dTag = "my-cool-blog", createdAt = time + 1))
            val version3 = signer.sign(LongTextNoteEvent.build("my cool blog, version 3", "title", dTag = "my-cool-blog", createdAt = time + 2))

            val addressableQuery = Filter(kinds = listOf(version1.kind), authors = listOf(version1.pubKey), tags = mapOf("d" to listOf(version1.dTag())))

            db.insert(version3)

            db.assertQuery(version3, Filter(ids = listOf(version3.id)))

            assertFailsWith<SQLiteException> {
                db.insert(version2)
            }

            assertFailsWith<SQLiteException> {
                db.insert(version1)
            }

            db.assertQuery(version3, Filter(ids = listOf(version3.id)))
            db.assertQuery(version3, addressableQuery)
            db.assertQuery(null, Filter(ids = listOf(version2.id)))
            db.assertQuery(null, Filter(ids = listOf(version1.id)))
        }

    @Test
    fun testReplacingSameCreatedAtLowerIdWins() =
        forEachDB { db ->
            val time = TimeUtils.now()
            // Two events with the same (kind, pubkey, d_tag, created_at) but
            // different content -> different ids. NIP-01 says the lower-id wins.
            val a =
                signer.sign(
                    LongTextNoteEvent.build(
                        "version A",
                        "title",
                        dTag = "tie",
                        createdAt = time,
                    ),
                )
            val b =
                signer.sign(
                    LongTextNoteEvent.build(
                        "version B",
                        "title",
                        dTag = "tie",
                        createdAt = time,
                    ),
                )
            val (winner, loser) = if (a.id < b.id) a to b else b to a

            db.insert(loser)
            db.assertQuery(loser, Filter(ids = listOf(loser.id)))

            db.insert(winner)
            db.assertQuery(null, Filter(ids = listOf(loser.id)))
            db.assertQuery(winner, Filter(ids = listOf(winner.id)))
        }

    @Test
    fun testReplacingSameCreatedAtHigherIdRejected() =
        forEachDB { db ->
            val time = TimeUtils.now()
            val a =
                signer.sign(
                    LongTextNoteEvent.build(
                        "version A",
                        "title",
                        dTag = "tie",
                        createdAt = time,
                    ),
                )
            val b =
                signer.sign(
                    LongTextNoteEvent.build(
                        "version B",
                        "title",
                        dTag = "tie",
                        createdAt = time,
                    ),
                )
            val (winner, loser) = if (a.id < b.id) a to b else b to a

            db.insert(winner)
            assertFailsWith<SQLiteException> {
                db.insert(loser)
            }
            db.assertQuery(winner, Filter(ids = listOf(winner.id)))
            db.assertQuery(null, Filter(ids = listOf(loser.id)))
        }

    @Test
    fun testTriggersIndexUsage() =
        forEachDB { db ->
            val explainer =
                db.store.explainQuery(
                    """
                    SELECT * FROM event_headers
                    WHERE
                        event_headers.kind = 30000 AND
                        event_headers.pubkey = 'aa' AND
                        event_headers.d_tag = 'test-tag' AND
                        event_headers.created_at < 1766686500 AND
                        event_headers.kind >= 30000 AND event_headers.kind < 40000
                    """.trimIndent(),
                )

            assertEquals(
                """
                SELECT * FROM event_headers
                WHERE
                    event_headers.kind = 30000 AND
                    event_headers.pubkey = 'aa' AND
                    event_headers.d_tag = 'test-tag' AND
                    event_headers.created_at < 1766686500 AND
                    event_headers.kind >= 30000 AND event_headers.kind < 40000
                └── SEARCH event_headers USING INDEX addressable_idx (kind=? AND pubkey=? AND d_tag=?)
                """.trimIndent(),
                explainer,
            )
        }
}
