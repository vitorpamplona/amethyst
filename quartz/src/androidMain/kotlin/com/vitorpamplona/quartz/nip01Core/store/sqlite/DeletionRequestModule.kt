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
import com.vitorpamplona.quartz.nip09Deletions.DeletionEvent

class DeletionRequestModule : IModule {
    override fun create(db: SQLiteDatabase) {
        // rejects deleted events.
        db.execSQL(
            """
            CREATE TRIGGER reject_deleted_events
            BEFORE INSERT ON event_headers
            FOR EACH ROW
            BEGIN
                -- Check for ID-based deletion record
                SELECT RAISE(ABORT, 'blocked: a deletion event for this event id exists')
                WHERE EXISTS (
                    SELECT 1 FROM event_headers INNER JOIN event_tags ON event_headers.row_id = event_tags.event_header_row_id
                    WHERE
                        event_headers.created_at >= NEW.created_at AND
                        event_headers.kind = 5 AND
                        event_headers.pubkey = NEW.pubkey AND
                        event_tags.tag_name = 'e' AND
                        event_tags.tag_value = NEW.id
                );

                -- Check for address-based deletion record
                SELECT RAISE(ABORT, 'blocked: a deletion event for this address exists')
                WHERE EXISTS (
                    SELECT 1 FROM event_headers INNER JOIN event_tags ON event_headers.row_id = event_tags.event_header_row_id
                    WHERE
                        event_headers.created_at >= NEW.created_at AND
                        event_headers.kind = 5 AND
                        event_headers.pubkey = NEW.pubkey AND
                        event_tags.tag_name = 'a' AND
                        event_tags.tag_value = NEW.kind || ':' || NEW.pubkey || ':' || NEW.d_tag
                );
            END;
            """.trimIndent(),
        )
    }

    override fun drop(db: SQLiteDatabase) {}

    override fun deleteAll(db: SQLiteDatabase) {}

    fun insert(
        event: Event,
        headerId: Long,
        db: SQLiteDatabase,
    ) {
        if (event is DeletionEvent) {
            val idValues = event.deleteEventIds()
            val idParams = idValues.joinToString(",") { "?" }

            val addresses = event.deleteAddresses()
            val addressParams = addresses.joinToString(",") { "(?, ?)" }
            val addressValues = addresses.flatMap { listOf<Any>(it.kind, it.dTag) }

            val whereClause =
                if (idValues.isNotEmpty() && addresses.isNotEmpty()) {
                    "(id IN ($idParams) OR (kind, d_tag) IN ($addressParams)) AND pubkey = ?"
                } else if (idValues.isNotEmpty()) {
                    "id IN ($idParams) AND pubkey = ?"
                } else if (addresses.isNotEmpty()) {
                    "(kind, d_tag) IN ($addressParams) AND pubkey = ?"
                } else {
                    return
                }
            val whereParams = idValues.plus(addressValues).plus(event.pubKey).toTypedArray()

            db.execSQL(
                """
                DELETE FROM event_headers
                WHERE $whereClause;
                """.trimIndent(),
                whereParams,
            )
        }
    }
}
