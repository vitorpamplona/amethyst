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

import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteException
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip50Search.SearchableEvent

class FullTextSearchModule : IModule {
    val tableName = "event_fts"
    val eventHeaderRowIdName = "event_header_row_id"
    val contentName = "content"

    override fun create(db: SQLiteDatabase) {
        val ftsVersion = FullTextSearchModule().versionFinder(db)
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

    override fun drop(db: SQLiteDatabase) {
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
        db: SQLiteDatabase,
    ) {
        if (event is SearchableEvent) {
            val stmt = db.compileStatement(insertFTS)
            stmt.bindLong(1, headerId)
            stmt.bindString(2, event.indexableContent())
            stmt.executeInsert()
        }
    }

    fun versionFinder(db: SQLiteDatabase): Int =
        try {
            try {
                db.execSQL("CREATE VIRTUAL TABLE dummy_fts5 USING fts5(dummy)")
                5
            } catch (e: SQLiteException) {
                db.execSQL("CREATE VIRTUAL TABLE dummy_fts4 USING fts4(dummy)")
                4
            }
        } catch (e: SQLiteException) {
            db.execSQL("CREATE VIRTUAL TABLE dummy_fts3 USING fts3(dummy)")
            3
        }

    override fun deleteAll(db: SQLiteDatabase) {
        db.execSQL("DELETE FROM event_fts")
    }
}
