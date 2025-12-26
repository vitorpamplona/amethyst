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
import com.vitorpamplona.quartz.nip62RequestToVanish.RequestToVanishEvent

class RightToVanishModule : IModule {
    override fun create(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE event_vanish (
                pubkey TEXT NOT NULL,
                event_header_row_id INTEGER PRIMARY KEY NOT NULL,
                created_at INTEGER NOT NULL,
                FOREIGN KEY (event_header_row_id) REFERENCES event_headers(row_id) ON DELETE CASCADE
            )
            """.trimIndent(),
        )

        db.execSQL("CREATE UNIQUE INDEX event_vanish_key ON event_vanish (pubkey)")

        db.execSQL(
            """
            CREATE TRIGGER delete_older_event_vanish
            BEFORE INSERT ON event_vanish
            FOR EACH ROW
            BEGIN
                -- Delete older records if this is the newest
                DELETE FROM event_vanish
                WHERE
                    event_vanish.created_at < NEW.created_at AND
                    event_vanish.pubkey = NEW.pubkey;
            END;
            """.trimIndent(),
        )

        db.execSQL(
            """
            CREATE TRIGGER delete_events_on_event_vanish
            AFTER INSERT ON event_vanish
            FOR EACH ROW
            BEGIN
                DELETE FROM event_headers
                WHERE created_at < NEW.created_at AND
                      pubkey = NEW.pubkey;
            END;
            """.trimIndent(),
        )

        // reject new events inside a right to vanish request
        db.execSQL(
            """
            CREATE TRIGGER reject_events_on_event_vanish
            BEFORE INSERT ON event_headers
            FOR EACH ROW
            BEGIN
                SELECT RAISE(ABORT, 'blocked: a request to vanish event exists')
                WHERE EXISTS (
                    SELECT 1 FROM event_vanish
                    WHERE
                        event_vanish.created_at >= NEW.created_at AND
                        event_vanish.pubkey = NEW.pubkey
                );
            END;
            """.trimIndent(),
        )
    }

    override fun drop(db: SQLiteDatabase) {
        db.execSQL("DROP TABLE IF EXISTS event_vanish")
    }

    val insertRTV =
        """
        INSERT OR ROLLBACK INTO event_vanish (event_header_row_id, pubkey, created_at)
        VALUES (?, ?, ?)
        """.trimIndent()

    fun insert(
        event: Event,
        relayUrl: String?,
        headerId: Long,
        db: SQLiteDatabase,
    ) {
        if (event is RequestToVanishEvent && event.shouldVanishFrom(relayUrl)) {
            val stmt = db.compileStatement(insertRTV)
            stmt.bindLong(1, headerId)
            stmt.bindString(2, event.pubKey)
            stmt.bindLong(3, event.createdAt)
            stmt.executeInsert()
        }
    }

    override fun deleteAll(db: SQLiteDatabase) {
        db.execSQL("DELETE FROM event_vanish")
    }
}
