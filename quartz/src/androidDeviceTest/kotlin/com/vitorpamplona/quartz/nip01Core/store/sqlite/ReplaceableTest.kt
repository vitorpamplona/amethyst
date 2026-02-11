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

import android.database.sqlite.SQLiteConstraintException
import com.vitorpamplona.quartz.nip01Core.metadata.MetadataEvent
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerSync
import com.vitorpamplona.quartz.utils.TimeUtils
import junit.framework.TestCase
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.fail
import org.junit.Test

class ReplaceableTest : BaseDBTest() {
    val signer = NostrSignerSync()

    @Test
    fun testReplacing() =
        forEachDB { db ->
            val time = TimeUtils.now()
            val version1 = signer.sign(MetadataEvent.createNew("Vitor 1", createdAt = time))
            val version2 = signer.sign(MetadataEvent.createNew("Vitor 2", createdAt = time + 1))
            val version3 = signer.sign(MetadataEvent.createNew("Vitor 3", createdAt = time + 2))

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
    fun testBlockingOldVersions() =
        forEachDB { db ->
            val time = TimeUtils.now()
            val version1 = signer.sign(MetadataEvent.createNew("Vitor 1", createdAt = time))
            val version2 = signer.sign(MetadataEvent.createNew("Vitor 2", createdAt = time + 1))
            val version3 = signer.sign(MetadataEvent.createNew("Vitor 3", createdAt = time + 2))

            val addressableQuery = Filter(kinds = listOf(version1.kind), authors = listOf(version1.pubKey), tags = mapOf("d" to listOf(version1.dTag())))

            db.insert(version3)

            db.assertQuery(version3, Filter(ids = listOf(version3.id)))

            try {
                db.insert(version2)
                fail("It should not allow inserting an older version")
            } catch (e: Exception) {
                TestCase.assertTrue(e is SQLiteConstraintException)
            }

            try {
                db.insert(version1)
                fail("It should not allow inserting an older version")
            } catch (e: Exception) {
                TestCase.assertTrue(e is SQLiteConstraintException)
            }

            db.assertQuery(version3, Filter(ids = listOf(version3.id)))
            db.assertQuery(version3, addressableQuery)
            db.assertQuery(null, Filter(ids = listOf(version2.id)))
            db.assertQuery(null, Filter(ids = listOf(version1.id)))
        }

    @Test
    fun testTriggersIndexUsageKind0() =
        forEachDB { db ->
            val sql =
                """
                SELECT * FROM event_headers
                WHERE
                    event_headers.kind = 0 AND
                    event_headers.pubkey = 'aa' AND
                    event_headers.created_at < 1766686500
                """.trimIndent()

            val explainer = db.store.explainQuery(sql)

            assertEquals(
                """
                SELECT * FROM event_headers
                WHERE
                    event_headers.kind = 0 AND
                    event_headers.pubkey = 'aa' AND
                    event_headers.created_at < 1766686500
                └── SEARCH event_headers USING INDEX query_by_kind_pubkey_created (kind=? AND pubkey=? AND created_at<?)
                """.trimIndent(),
                explainer,
            )
        }

    @Test
    fun testTriggersIndexUsageKind3() =
        forEachDB { db ->
            val sql =
                """
                SELECT * FROM event_headers
                WHERE
                    event_headers.kind = 3 AND
                    event_headers.pubkey = 'aa' AND
                    event_headers.created_at < 1766686500
                """.trimIndent()

            val explainer = db.store.explainQuery(sql)

            assertEquals(
                """
                SELECT * FROM event_headers
                WHERE
                    event_headers.kind = 3 AND
                    event_headers.pubkey = 'aa' AND
                    event_headers.created_at < 1766686500
                └── SEARCH event_headers USING INDEX query_by_kind_pubkey_created (kind=? AND pubkey=? AND created_at<?)
                """.trimIndent(),
                explainer,
            )
        }
}
