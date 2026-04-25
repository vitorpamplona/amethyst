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

import androidx.sqlite.SQLiteConnection
import androidx.sqlite.SQLiteException
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip50Search.SearchableEvent

class FullTextSearchModule : IModule {
    val tableName = "event_fts"
    val eventHeaderRowIdName = "event_header_row_id"
    val contentName = "content"

    override fun create(db: SQLiteConnection) {
        val ftsVersion = versionFinder(db)
        db.execSQL(
            """
                    CREATE VIRTUAL TABLE $tableName
                    USING fts$ftsVersion($eventHeaderRowIdName, $contentName)
                """,
        )

        // Foreign key cleanup for full text search
        db.execSQL(
            """
                CREATE TRIGGER fts_foreign_key
                AFTER DELETE ON event_headers
                FOR EACH ROW
                BEGIN
                    DELETE FROM $tableName
                    WHERE old.row_id = $tableName.$eventHeaderRowIdName;
                END;
            """,
        )
    }

    override fun drop(db: SQLiteConnection) {
        db.execSQL("DROP TABLE IF EXISTS $tableName")
    }

    val insertFTS =
        """
        INSERT OR ROLLBACK INTO $tableName ($eventHeaderRowIdName, $contentName)
        VALUES (?, ?)
        """.trimIndent()

    fun insert(
        event: Event,
        headerId: Long,
        db: SQLiteConnection,
    ) {
        if (event is SearchableEvent) {
            db.prepare(insertFTS).use { stmt ->
                stmt.bindLong(1, headerId)
                stmt.bindText(2, event.indexableContent())
                stmt.step()
            }
        }
    }

    fun versionFinder(db: SQLiteConnection): Int {
        // Defensive cleanup in case a previous probe left these behind
        // (e.g. a partial create() during an upgrade) — without this,
        // the next CREATE VIRTUAL TABLE would fail with "already exists".
        db.execSQL("DROP TABLE IF EXISTS dummy_fts5")
        db.execSQL("DROP TABLE IF EXISTS dummy_fts4")
        db.execSQL("DROP TABLE IF EXISTS dummy_fts3")

        val (table, version) =
            try {
                try {
                    db.execSQL("CREATE VIRTUAL TABLE dummy_fts5 USING fts5(dummy)")
                    "dummy_fts5" to 5
                } catch (e: SQLiteException) {
                    db.execSQL("CREATE VIRTUAL TABLE dummy_fts4 USING fts4(dummy)")
                    "dummy_fts4" to 4
                }
            } catch (e: SQLiteException) {
                db.execSQL("CREATE VIRTUAL TABLE dummy_fts3 USING fts3(dummy)")
                "dummy_fts3" to 3
            }

        // We only needed the table to probe FTS support — drop it now so
        // re-running create() on an already-probed DB stays idempotent.
        db.execSQL("DROP TABLE $table")

        return version
    }

    override fun deleteAll(db: SQLiteConnection) {
        db.execSQL("DELETE FROM event_fts")
    }
}
