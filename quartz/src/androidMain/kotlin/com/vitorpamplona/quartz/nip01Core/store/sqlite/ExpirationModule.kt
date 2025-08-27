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

import android.database.sqlite.SQLiteDatabase
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip40Expiration.expiration

class ExpirationModule {
    fun create(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE event_expirations (
                event_header_row_id INTEGER,
                expiration INTEGER NOT NULL,
                FOREIGN KEY (event_header_row_id) REFERENCES event_headers(row_id) ON DELETE CASCADE
            )
            """.trimIndent(),
        )

        // Rejects old addressables
        db.execSQL(
            """
            CREATE TRIGGER reject_expired_events
            BEFORE INSERT ON event_expirations
            FOR EACH ROW
            BEGIN
                -- Check for existing newer record
                SELECT RAISE(ABORT, 'blocked: this event is expired')
                WHERE NEW.expiration <= unixepoch();
            END;
            """.trimIndent(),
        )

        db.execSQL("CREATE UNIQUE INDEX events_exp_id       ON event_expirations (event_header_row_id)")
    }

    val insertExpiration =
        """
        INSERT OR ROLLBACK INTO event_expirations (event_header_row_id, expiration)
        VALUES (?, ?)
        """.trimIndent()

    fun insert(
        event: Event,
        headerId: Long,
        db: SQLiteDatabase,
    ) {
        val exp = event.expiration()
        if (exp != null && exp > 0) {
            val stmt = StatementCache.get(insertExpiration, db)
            stmt.bindLong(1, headerId)
            stmt.bindLong(2, exp)
            stmt.executeInsert()
        }
    }

    val deleteExpiredEvents =
        """
        DELETE FROM event_headers
        WHERE row_id IN (
            SELECT event_expirations.event_header_row_id FROM event_expirations
            WHERE event_expirations.expiration < unixepoch()
        );
        """.trimIndent()

    fun deleteExpiredEvents(db: SQLiteDatabase) {
        StatementCache.get(deleteExpiredEvents, db).execute()
    }

    fun deleteAll(db: SQLiteDatabase) {
        db.execSQL("DELETE FROM event_expirations")
    }
}
