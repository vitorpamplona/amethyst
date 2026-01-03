/**
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
import com.vitorpamplona.quartz.nip01Core.core.Address
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerSync
import com.vitorpamplona.quartz.nip09Deletions.DeletionEvent
import com.vitorpamplona.quartz.nip10Notes.TextNoteEvent
import com.vitorpamplona.quartz.nip23LongContent.LongTextNoteEvent
import com.vitorpamplona.quartz.nip59Giftwrap.wraps.GiftWrapEvent
import com.vitorpamplona.quartz.utils.TimeUtils
import junit.framework.TestCase
import junit.framework.TestCase.fail
import org.junit.Assert.assertEquals
import org.junit.Test

class DeletionTest : BaseDBTest() {
    val signer = NostrSignerSync()

    @Test
    fun testInsertDeleteEvent() =
        forEachDB { db ->
            val note1 = signer.sign(TextNoteEvent.build("test1"))
            val note2 = signer.sign(TextNoteEvent.build("test2"))
            val note3 = signer.sign(TextNoteEvent.build("test3"))

            db.insert(note1)
            db.insert(note2)
            db.insert(note3)

            db.assertQuery(note1, Filter(ids = listOf(note1.id)))
            db.assertQuery(note2, Filter(ids = listOf(note2.id)))
            db.assertQuery(note3, Filter(ids = listOf(note3.id)))

            val deletion = signer.sign(DeletionEvent.build(listOf(note1)))

            db.insert(deletion)

            db.assertQuery(deletion, Filter(ids = listOf(deletion.id)))
            db.assertQuery(null, Filter(ids = listOf(note1.id)))
            db.assertQuery(note2, Filter(ids = listOf(note2.id)))
            db.assertQuery(note3, Filter(ids = listOf(note3.id)))

            // trying to insert again should fail.
            try {
                db.insert(note1)
                fail("Should not be able to insert a deleted event")
            } catch (e: SQLiteConstraintException) {
                assertEquals("blocked: a deletion event exists (code 1811 SQLITE_CONSTRAINT_TRIGGER)", e.message)
            }

            db.assertQuery(deletion, Filter(ids = listOf(deletion.id)))
            db.assertQuery(null, Filter(ids = listOf(note1.id)))
            db.assertQuery(note2, Filter(ids = listOf(note2.id)))
            db.assertQuery(note3, Filter(ids = listOf(note3.id)))
        }

    @Test
    fun testInsertDeleteEventOfAddressable() =
        forEachDB { db ->
            val time = TimeUtils.now()
            val note1 = signer.sign(LongTextNoteEvent.build("my cool blog, version 1", "title", dTag = "my-cool-blog", createdAt = time))
            val note2 = signer.sign(LongTextNoteEvent.build("my cool blog, version 2", "title", dTag = "my-cool-blog", createdAt = time + 1))
            val note3 = signer.sign(LongTextNoteEvent.build("my cool blog, version 3", "title", dTag = "my-cool-blog", createdAt = time + 2))

            db.insert(note1)

            db.assertQuery(note1, Filter(ids = listOf(note1.id)))

            db.insert(note2)

            db.assertQuery(null, Filter(ids = listOf(note1.id)))
            db.assertQuery(note2, Filter(ids = listOf(note2.id)))

            db.insert(note3)

            db.assertQuery(null, Filter(ids = listOf(note1.id)))
            db.assertQuery(null, Filter(ids = listOf(note2.id)))
            db.assertQuery(note3, Filter(ids = listOf(note3.id)))

            val deletion = signer.sign(DeletionEvent.build(listOf(note1)))

            db.insert(deletion)

            db.assertQuery(deletion, Filter(ids = listOf(deletion.id)))
            db.assertQuery(null, Filter(ids = listOf(note1.id)))
            db.assertQuery(null, Filter(ids = listOf(note2.id)))
            db.assertQuery(null, Filter(ids = listOf(note3.id)))

            // trying to insert again should fail.
            try {
                db.insert(note1)
                fail("Should not be able to insert a deleted event")
            } catch (e: SQLiteConstraintException) {
                assertEquals("blocked: a deletion event exists (code 1811 SQLITE_CONSTRAINT_TRIGGER)", e.message)
            }

            db.assertQuery(deletion, Filter(ids = listOf(deletion.id)))
            db.assertQuery(null, Filter(ids = listOf(note1.id)))
            db.assertQuery(null, Filter(ids = listOf(note2.id)))
            db.assertQuery(null, Filter(ids = listOf(note3.id)))
        }

    @Test
    fun testInsertDeleteEventOfAddressable2() =
        forEachDB { db ->
            val time = TimeUtils.now()
            val note1 = signer.sign(LongTextNoteEvent.build("my cool blog, version 1", "title", dTag = "my-cool-blog", createdAt = time))
            val note2 = signer.sign(LongTextNoteEvent.build("my cool blog, version 2", "title", dTag = "my-cool-blog", createdAt = time + 1))
            val note3 = signer.sign(LongTextNoteEvent.build("my cool blog, version 3", "title", dTag = "my-cool-blog", createdAt = time + 2))

            db.insert(note1)
            db.insert(note2)
            db.insert(note3)

            db.assertQuery(null, Filter(ids = listOf(note1.id)))
            db.assertQuery(null, Filter(ids = listOf(note2.id)))
            db.assertQuery(note3, Filter(ids = listOf(note3.id)))

            val deletion = signer.sign(DeletionEvent.buildAddressOnly(listOf(note1)))

            db.insert(deletion)

            db.assertQuery(deletion, Filter(ids = listOf(deletion.id)))
            db.assertQuery(null, Filter(ids = listOf(note1.id)))
            db.assertQuery(null, Filter(ids = listOf(note2.id)))
            db.assertQuery(null, Filter(ids = listOf(note3.id)))

            // trying to insert again should fail.
            try {
                db.insert(note1)
                fail("Should not be able to insert a deleted event")
            } catch (e: SQLiteConstraintException) {
                assertEquals("blocked: a deletion event exists (code 1811 SQLITE_CONSTRAINT_TRIGGER)", e.message)
            }

            db.assertQuery(deletion, Filter(ids = listOf(deletion.id)))
            db.assertQuery(null, Filter(ids = listOf(note1.id)))
            db.assertQuery(null, Filter(ids = listOf(note2.id)))
            db.assertQuery(null, Filter(ids = listOf(note3.id)))
        }

    @Test
    fun testInsertDeleteWrap() =
        forEachDB { db ->
            val me = NostrSignerSync()
            val myFriend = NostrSignerSync()

            val note1 = me.sign(TextNoteEvent.build("test1"))
            val wrap1 = GiftWrapEvent.create(note1, me.pubKey)
            val wrap2 = GiftWrapEvent.create(note1, myFriend.pubKey)

            db.insert(wrap1)
            db.insert(wrap2)

            db.assertQuery(wrap1, Filter(ids = listOf(wrap1.id)))
            db.assertQuery(wrap2, Filter(ids = listOf(wrap2.id)))

            val randomDeletionToWrap = signer.sign(DeletionEvent.build(listOf(wrap1)))

            db.insert(randomDeletionToWrap)

            db.assertQuery(randomDeletionToWrap, Filter(ids = listOf(randomDeletionToWrap.id)))
            db.assertQuery(wrap1, Filter(ids = listOf(wrap1.id)))
            db.assertQuery(wrap2, Filter(ids = listOf(wrap2.id)))

            val deletion = me.sign(DeletionEvent.build(listOf(wrap1)))

            db.insert(deletion)

            db.assertQuery(deletion, Filter(ids = listOf(deletion.id)))
            db.assertQuery(null, Filter(ids = listOf(wrap1.id)))
            db.assertQuery(wrap2, Filter(ids = listOf(wrap2.id)))

            // trying to insert again should fail.
            try {
                db.insert(wrap1)
                fail("Should not be able to insert a deleted event")
            } catch (e: SQLiteConstraintException) {
                assertEquals("blocked: a deletion event exists (code 1811 SQLITE_CONSTRAINT_TRIGGER)", e.message)
            }

            db.assertQuery(deletion, Filter(ids = listOf(deletion.id)))
            db.assertQuery(null, Filter(ids = listOf(wrap1.id)))
            db.assertQuery(wrap2, Filter(ids = listOf(wrap2.id)))
        }

    @Test
    fun testTriggersIndexUsage() =
        forEachDB { db ->
            var sql = db.store.deletionModule.rejectDeletedEventsSQLTemplate()

            sql = sql.replace("NEW.etag_hash", "3221122")
            sql = sql.replace("NEW.atag_hash", "223322")
            sql = sql.replace("NEW.pubkey_owner_hash", "22332323")
            sql = sql.replace("NEW.created_at", "1766686500")

            val explainer = db.store.explainQuery(sql)

            if (db.indexStrategy.indexTagsWithKindAndPubkey) {
                TestCase.assertEquals(
                    """
                    |$sql
                    |└── SEARCH event_tags USING COVERING INDEX query_by_tags_hash_kind_pubkey (tag_hash=? AND kind=? AND pubkey_hash=? AND created_at>?)
                    """.trimMargin(),
                    explainer,
                )
            } else {
                TestCase.assertEquals(
                    """
                    |$sql
                    |└── SEARCH event_tags USING INDEX query_by_tags_hash_kind (tag_hash=? AND kind=? AND created_at>?)
                    """.trimMargin(),
                    explainer,
                )
            }
        }

    @Test
    fun testDeleteById() =
        forEachDB { db ->
            val sql =
                db.store.deletionModule
                    .deleteSQL(
                        pubkey = "key1",
                        idValues = listOf("ca29c211f", "ca29c211d"),
                        addresses = emptyList(),
                        hasher = TagNameValueHasher(0),
                    ).first()

            TestCase.assertEquals(
                """
                DELETE FROM event_headers
                WHERE
                    id IN ("ca29c211f","ca29c211d") AND
                    pubkey_owner_hash = "1573573083296714675"
                ├── SEARCH event_headers USING INDEX event_headers_id (id=?)
                ├── SEARCH event_vanish USING INTEGER PRIMARY KEY (rowid=?)
                ├── SEARCH event_expirations USING INTEGER PRIMARY KEY (rowid=?)
                └── SEARCH event_tags USING COVERING INDEX fk_event_tags_header_id (event_header_row_id=?)
                """.trimIndent(),
                db.store.explainQuery(sql.sql, sql.args),
            )
        }

    @Test
    fun testDeleteAddressable() =
        forEachDB { db ->
            val sql =
                db.store.deletionModule
                    .deleteSQL(
                        pubkey = "key1",
                        idValues = emptyList(),
                        addresses =
                            listOf(
                                Address(30000, "key1", "a"),
                            ),
                        hasher = TagNameValueHasher(0),
                    ).first()

            TestCase.assertEquals(
                """
                DELETE FROM event_headers
                WHERE (
                    (kind = "30000" AND pubkey = "key1" AND d_tag = "a")
                ) AND
                    kind >= 30000 AND kind < 40000
                ├── SEARCH event_headers USING COVERING INDEX addressable_idx (kind=? AND pubkey=? AND d_tag=?)
                ├── SEARCH event_vanish USING INTEGER PRIMARY KEY (rowid=?)
                ├── SEARCH event_expirations USING INTEGER PRIMARY KEY (rowid=?)
                └── SEARCH event_tags USING COVERING INDEX fk_event_tags_header_id (event_header_row_id=?)
                """.trimIndent(),
                db.store.explainQuery(sql.sql, sql.args),
            )
        }

    @Test
    fun testDeleteAddressablesSingleKind() =
        forEachDB { db ->
            val sql =
                db.store.deletionModule
                    .deleteSQL(
                        pubkey = "key1",
                        idValues = emptyList(),
                        addresses =
                            listOf(
                                Address(30000, "key1", "a"),
                                Address(30000, "key1", "b"),
                                Address(30000, "key1", "c"),
                                Address(30000, "key1", "d"),
                            ),
                        hasher = TagNameValueHasher(0),
                    ).first()

            TestCase.assertEquals(
                """
                DELETE FROM event_headers
                WHERE (
                    (kind = "30000" AND pubkey = "key1" AND d_tag IN ("a","b","c","d"))
                ) AND
                    kind >= 30000 AND kind < 40000
                ├── SEARCH event_headers USING COVERING INDEX addressable_idx (kind=? AND pubkey=? AND d_tag=?)
                ├── SEARCH event_vanish USING INTEGER PRIMARY KEY (rowid=?)
                ├── SEARCH event_expirations USING INTEGER PRIMARY KEY (rowid=?)
                └── SEARCH event_tags USING COVERING INDEX fk_event_tags_header_id (event_header_row_id=?)
                """.trimIndent(),
                db.store.explainQuery(sql.sql, sql.args),
            )
        }

    @Test
    fun testDeleteAddressablesMultipleKinds() =
        forEachDB { db ->
            val sql =
                db.store.deletionModule
                    .deleteSQL(
                        pubkey = "key1",
                        idValues = emptyList(),
                        addresses =
                            listOf(
                                Address(30000, "key1", "a"),
                                Address(30000, "key1", "b"),
                                Address(30101, "key1", "c"),
                                Address(30101, "key1", "d"),
                                Address(30001, "key2", "e"),
                                Address(30001, "key2", "f"),
                            ),
                        hasher = TagNameValueHasher(0),
                    ).first()

            TestCase.assertEquals(
                """
                DELETE FROM event_headers
                WHERE (
                    (kind = "30000" AND pubkey = "key1" AND d_tag IN ("a","b"))
                OR
                    (kind = "30101" AND pubkey = "key1" AND d_tag IN ("c","d"))
                ) AND
                    kind >= 30000 AND kind < 40000
                ├── MULTI-INDEX OR
                │   ├── INDEX 1
                │   │   └── SEARCH event_headers USING COVERING INDEX addressable_idx (kind=? AND pubkey=? AND d_tag=?)
                │   └── INDEX 2
                │       └── SEARCH event_headers USING COVERING INDEX addressable_idx (kind=? AND pubkey=? AND d_tag=?)
                ├── SEARCH event_vanish USING INTEGER PRIMARY KEY (rowid=?)
                ├── SEARCH event_expirations USING INTEGER PRIMARY KEY (rowid=?)
                └── SEARCH event_tags USING COVERING INDEX fk_event_tags_header_id (event_header_row_id=?)
                """.trimIndent(),
                db.store.explainQuery(sql.sql, sql.args),
            )
        }

    @Test
    fun testDeleteReplaceables() =
        forEachDB { db ->
            val sql =
                db.store.deletionModule
                    .deleteSQL(
                        pubkey = "key1",
                        idValues = emptyList(),
                        addresses =
                            listOf(
                                Address(10000, "key1", ""),
                                Address(10000, "key1", ""),
                                Address(10001, "key1", ""),
                                Address(10001, "key1", ""),
                                Address(10001, "key2", ""),
                                Address(10001, "key2", ""),
                            ),
                        hasher = TagNameValueHasher(0),
                    ).first()

            TestCase.assertEquals(
                """
                DELETE FROM event_headers
                WHERE
                    kind IN ("10000","10001") AND
                    pubkey = "key1" AND
                    ((kind in (0,3)) OR (kind >= 10000 AND kind < 20000))
                ├── SEARCH event_headers USING COVERING INDEX replaceable_idx (kind=? AND pubkey=?)
                ├── SEARCH event_vanish USING INTEGER PRIMARY KEY (rowid=?)
                ├── SEARCH event_expirations USING INTEGER PRIMARY KEY (rowid=?)
                └── SEARCH event_tags USING COVERING INDEX fk_event_tags_header_id (event_header_row_id=?)
                """.trimIndent(),
                db.store.explainQuery(sql.sql, sql.args),
            )
        }
}
