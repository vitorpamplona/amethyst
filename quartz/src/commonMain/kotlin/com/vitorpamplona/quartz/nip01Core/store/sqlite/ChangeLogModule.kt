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
import com.vitorpamplona.quartz.nip01Core.core.HexKey

/**
 * Per-connection scratch space that records the id of every row that
 * leaves `event_headers`, regardless of why it left.
 *
 * Why TEMP: the table and trigger are connection-scoped, so they only
 * exist on the writer connection where they're installed. Readers never
 * see them and never accumulate rows. The data also lives only for the
 * lifetime of the connection — exactly the right scope for "ids removed
 * since the last drain."
 *
 * The trigger fires after every delete on `event_headers`, including:
 *  - the supersession triggers in [ReplaceableModule] / [AddressableModule],
 *  - the cascade from `event_vanish` in [RightToVanishModule],
 *  - the explicit deletes in [DeletionRequestModule],
 *  - [ExpirationModule.deleteExpiredEvents],
 *  - manual `delete(filter)` / `delete(id)`,
 *  - `clearDB()`.
 *
 * The [SQLiteEventStore] drains this log around each unit of work and
 * publishes the resulting ids in a [StoreChange].
 */
class ChangeLogModule {
    fun installOnWriter(db: SQLiteConnection) {
        db.execSQL(
            """
            CREATE TEMP TABLE IF NOT EXISTS event_change_log (
                seq INTEGER PRIMARY KEY AUTOINCREMENT,
                id TEXT NOT NULL
            )
            """.trimIndent(),
        )

        db.execSQL(
            """
            CREATE TEMP TRIGGER IF NOT EXISTS event_change_log_on_delete
            AFTER DELETE ON event_headers
            FOR EACH ROW
            BEGIN
                INSERT INTO event_change_log (id) VALUES (OLD.id);
            END
            """.trimIndent(),
        )
    }

    /**
     * Reads every id currently logged and clears the log.
     *
     * Must be called on the writer connection that owns the temp table.
     * Callers are expected to be inside the writer mutex (drain happens
     * inside or right after the `useWriter { ... }` block); a single
     * read-and-clear pair is therefore atomic from the writer's view.
     */
    fun drain(db: SQLiteConnection): List<HexKey> {
        val ids = ArrayList<HexKey>()
        db.prepare("SELECT id FROM event_change_log ORDER BY seq").use { stmt ->
            while (stmt.step()) {
                ids.add(stmt.getText(0))
            }
        }
        if (ids.isNotEmpty()) {
            db.execSQL("DELETE FROM event_change_log")
        }
        return ids
    }

    /**
     * Drops everything from the log without returning it. Used after a
     * rollback so the next successful unit of work starts clean.
     */
    fun reset(db: SQLiteConnection) {
        db.execSQL("DELETE FROM event_change_log")
    }
}
