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
import com.vitorpamplona.quartz.nip01Core.core.Address
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.isAddressable
import com.vitorpamplona.quartz.nip01Core.core.isReplaceable
import com.vitorpamplona.quartz.nip09Deletions.DeletionEvent

class DeletionRequestModule(
    val hasher: (db: SQLiteDatabase) -> TagNameValueHasher,
) : IModule {
    /**
     * Creates a trigger to reject events that have been
     * deleted by ID or ATag including GiftWraps that
     * must be checked against the p-tag (pubkey_owner_hash)
     */
    override fun create(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TRIGGER reject_deleted_events
            BEFORE INSERT ON event_headers
            FOR EACH ROW
            BEGIN
                -- Check for ID-based deletion record
                SELECT RAISE(ABORT, 'blocked: a deletion event exists')
                WHERE EXISTS (
                    SELECT 1 FROM event_tags
                    INNER JOIN event_headers
                    ON event_headers.row_id = event_tags.event_header_row_id
                    WHERE
                        event_tags.tag_hash IN (NEW.etag_hash, NEW.atag_hash) AND
                        event_headers.kind = 5 AND
                        event_headers.pubkey_owner_hash = NEW.pubkey_owner_hash AND
                        event_headers.created_at >= NEW.created_at
                );
            END;
            """.trimIndent(),
        )
    }

    override fun drop(db: SQLiteDatabase) {}

    override fun deleteAll(db: SQLiteDatabase) {}

    fun insert(
        event: Event,
        db: SQLiteDatabase,
    ) {
        if (event is DeletionEvent) {
            val idValues = event.deleteEventIds()
            val addresses = event.deleteAddresses()

            deleteSQL(event.pubKey, idValues, addresses, hasher(db)).forEach { delete ->
                db.execSQL(delete.sql, delete.args)
            }
        }
    }

    /**
     * Creates a Delete statement that correctly deletes by id,
     * by address and by replaceable (no d-tag) using each index
     * appropriately, including GiftWraps where the owner is the
     * p-tag (via event_header.pubkey_owner_hash)
     */
    fun deleteSQL(
        pubkey: HexKey,
        idValues: List<String>,
        addresses: List<Address>,
        hasher: TagNameValueHasher,
    ): List<SqlArgs> {
        val owner = hasher.hash(pubkey)
        val idParams = idValues.joinToString(",") { "?" }

        // aligns each type of param with the need to filter d-tag
        // and thus each index type
        val addressablesByKind = addresses.filter { it.kind.isAddressable() && it.pubKeyHex == pubkey }.groupBy { it.kind }
        val replaceablesByKind = addresses.filter { it.kind.isReplaceable() && it.pubKeyHex == pubkey }.groupBy { it.kind }

        val addressableParams =
            addressablesByKind.keys.joinToString("\n                    OR\n                        ") {
                val tagList = addressablesByKind[it]
                if (tagList == null) {
                    ""
                } else if (tagList.size == 1) {
                    "(kind = ? AND pubkey = ? AND d_tag = ?)"
                } else {
                    "(kind = ? AND pubkey = ? AND d_tag IN (${tagList.joinToString(",") { "?" }}))"
                }
            }

        val addressableValues =
            addressablesByKind.flatMap {
                listOf<Any>(it.key.toLong(), pubkey) + it.value.map { it.dTag }
            }

        val replaceableKindsParam = replaceablesByKind.keys.joinToString(",") { "?" }
        val replaceableKindsValues = replaceablesByKind.keys.map { it.toLong() }

        val deleteById =
            if (idValues.isNotEmpty()) {
                SqlArgs(
                    """
                    DELETE FROM event_headers
                    WHERE
                        id IN ($idParams) AND
                        pubkey_owner_hash = ?
                    """.trimIndent(),
                    idValues.plus(owner).toTypedArray(),
                )
            } else {
                null
            }

        val deleteByAddress =
            if (addressableValues.isNotEmpty()) {
                SqlArgs(
                    """
                    DELETE FROM event_headers
                    WHERE (
                        $addressableParams
                    ) AND
                        kind >= 30000 AND kind < 40000
                    """.trimIndent(),
                    addressableValues.toTypedArray(),
                )
            } else {
                null
            }

        val deleteByReplaceable =
            if (replaceableKindsParam.isNotEmpty()) {
                SqlArgs(
                    """
                    DELETE FROM event_headers
                    WHERE
                        kind IN ($replaceableKindsParam) AND
                        pubkey = ? AND
                        ((kind in (0,3)) OR (kind >= 10000 AND kind < 20000))
                    """.trimIndent(),
                    replaceableKindsValues.plus(pubkey).toTypedArray(),
                )
            } else {
                null
            }

        return listOfNotNull(deleteById, deleteByAddress, deleteByReplaceable)
    }

    class SqlArgs(
        val sql: String,
        val args: Array<Any>,
    )
}
