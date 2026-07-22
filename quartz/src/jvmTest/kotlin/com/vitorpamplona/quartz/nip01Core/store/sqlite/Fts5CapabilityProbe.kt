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
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Pins the FTS5 features the [FullTextSearchModule] contentless index depends
 * on to the bundled SQLite. All three shipped between SQLite 3.43 and 3.44
 * (2023); if the bundled driver is ever downgraded below that, this fails
 * loudly instead of the store silently breaking search on delete.
 *
 *  - `content=''` **contentless** table with `contentless_delete=1`: lets the
 *    index drop the duplicated content column yet still delete rows by rowid
 *    (the `fts_foreign_key` trigger needs it).
 *  - explicit `rowid` on insert (= `event_headers.row_id`): the join key and
 *    the O(log n) delete key.
 *  - bm25 `rank` on a contentless table, through a join: NIP-50 relevance order.
 *  - `'merge'` / `'optimize'` maintenance commands: segment compaction.
 */
class Fts5CapabilityProbe {
    @Test
    fun contentlessDeleteAndRowidOrderingAreSupported() {
        val db = BundledSQLiteDriver().open(":memory:")
        try {
            db.execSQL("CREATE VIRTUAL TABLE cl USING fts5(content, content='', contentless_delete=1)")
            db.execSQL("INSERT INTO cl(rowid, content) VALUES (100, 'hello world')")
            db.execSQL("INSERT INTO cl(rowid, content) VALUES (50, 'hello there')")
            db.execSQL("INSERT INTO cl(rowid, content) VALUES (200, 'hello again')")
            db.execSQL("DELETE FROM cl WHERE rowid = 50")

            // Deleting an absent rowid must be a harmless no-op: the store's
            // fts_foreign_key trigger fires on EVERY event_headers delete, but
            // only searchable events ever got an FTS row.
            db.execSQL("DELETE FROM cl WHERE rowid = 999999")

            val order = ArrayList<Long>()
            db.prepare("SELECT rowid FROM cl WHERE cl MATCH 'hello' ORDER BY rowid DESC LIMIT 5").use {
                while (it.step()) order.add(it.getLong(0))
            }
            // Deleted 50 is gone; the rest come back newest-rowid first.
            assertEquals(listOf(200L, 100L), order)
        } finally {
            db.close()
        }
    }

    @Test
    fun bm25RankWorksOnContentlessTableInAJoin() {
        // NIP-50 orders by relevance, not created_at. Verify FTS5 bm25 `rank`
        // works on a contentless table and is reachable through the same
        // join-back-to-base-table shape the store's search query uses.
        val db = BundledSQLiteDriver().open(":memory:")
        try {
            db.execSQL("CREATE TABLE headers (row_id INTEGER PRIMARY KEY, created_at INTEGER, tag TEXT)")
            db.execSQL("CREATE VIRTUAL TABLE fts USING fts5(content, content='', contentless_delete=1)")
            // row 10: term appears 3× in a short doc (most relevant).
            // row 20: term once in a long doc (least relevant) but NEWER.
            db.execSQL("INSERT INTO headers VALUES (10, 100, 'A')")
            db.execSQL("INSERT INTO fts(rowid, content) VALUES (10, 'needle needle needle')")
            db.execSQL("INSERT INTO headers VALUES (20, 999, 'B')")
            db.execSQL("INSERT INTO fts(rowid, content) VALUES (20, 'needle alpha beta gamma delta epsilon zeta eta')")

            // created_at DESC would return B (999) first; relevance returns A.
            val byRank = ArrayList<String>()
            db
                .prepare(
                    """
                    SELECT headers.tag FROM headers
                    INNER JOIN fts ON headers.row_id = fts.rowid
                    WHERE fts MATCH 'needle'
                    ORDER BY fts.rank
                    LIMIT 10
                    """.trimIndent(),
                ).use { while (it.step()) byRank.add(it.getText(0)) }
            assertEquals(listOf("A", "B"), byRank, "bm25 rank must put the more relevant (shorter, higher-tf) doc first")
        } finally {
            db.close()
        }
    }

    @Test
    fun segmentMergeAndOptimizeAreSupported() {
        val db = BundledSQLiteDriver().open(":memory:")
        try {
            db.execSQL("CREATE VIRTUAL TABLE m USING fts5(content)")
            db.execSQL("INSERT INTO m(rowid, content) VALUES (1, 'a b c')")
            db.execSQL("INSERT INTO m(rowid, content) VALUES (2, 'd e f')")
            // Bounded incremental merge, then a full optimize — both must parse
            // and run without error on the bundled build.
            db.execSQL("INSERT INTO m(m, rank) VALUES ('merge', -16)")
            db.execSQL("INSERT INTO m(m) VALUES ('optimize')")

            val hits = ArrayList<Long>()
            db.prepare("SELECT rowid FROM m WHERE m MATCH 'e' ORDER BY rowid").use {
                while (it.step()) hits.add(it.getLong(0))
            }
            assertEquals(listOf(2L), hits)
        } finally {
            db.close()
        }
    }
}
