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

class AddressableModule : IModule {
    override fun create(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE UNIQUE INDEX addressable_idx
            ON event_headers (kind, pubkey, d_tag)
            WHERE kind >= 30000 AND kind < 40000
            """.trimIndent(),
        )

        // deletes older addressables when inserting new ones
        // if a newer addressable is inserted the unique index
        // above will be triggered. Delete cascade will take
        // care of the event_tags table
        db.execSQL(
            """
            CREATE TRIGGER delete_older_addressable_event
            BEFORE INSERT ON event_headers
            FOR EACH ROW
            WHEN (NEW.kind >= 30000 AND NEW.kind < 40000)
            BEGIN
                DELETE FROM event_headers
                WHERE
                    event_headers.kind = NEW.kind AND
                    event_headers.pubkey = NEW.pubkey AND
                    event_headers.d_tag = NEW.d_tag AND
                    event_headers.created_at < NEW.created_at;
            END;
            """.trimIndent(),
        )
    }

    override fun drop(db: SQLiteDatabase) {}

    override fun deleteAll(db: SQLiteDatabase) {}
}
