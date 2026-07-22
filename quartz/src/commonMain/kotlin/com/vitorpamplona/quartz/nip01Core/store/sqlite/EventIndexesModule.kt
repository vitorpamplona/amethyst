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
import com.vitorpamplona.quartz.nip01Core.core.AddressSerializer
import com.vitorpamplona.quartz.nip01Core.core.AddressableEvent
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.OptimizedJsonMapper
import com.vitorpamplona.quartz.nip59Giftwrap.wraps.GiftWrapEvent

class EventIndexesModule(
    val hasher: (db: SQLiteConnection) -> TagNameValueHasher,
    val indexStrategy: IndexingStrategy = DefaultIndexingStrategy(),
) : IModule {
    override fun create(db: SQLiteConnection) {
        db.execSQL(
            """
            CREATE TABLE event_headers (
                row_id INTEGER PRIMARY KEY AUTOINCREMENT,
                id TEXT NOT NULL,
                pubkey TEXT NOT NULL,
                created_at INTEGER NOT NULL,
                kind INTEGER NOT NULL,
                d_tag TEXT,
                tags TEXT NOT NULL,
                content TEXT NOT NULL,
                sig TEXT NOT NULL,
                pubkey_owner_hash INTEGER NOT NULL,
                etag_hash INTEGER,
                atag_hash INTEGER
            )
            """.trimIndent(),
        )

        db.execSQL(
            """
            CREATE TABLE event_tags (
                event_header_row_id INTEGER NOT NULL,
                tag_hash INTEGER NOT NULL,
                created_at INTEGER NOT NULL,
                kind INTEGER NOT NULL,
                pubkey_hash INTEGER NOT NULL,
                FOREIGN KEY (event_header_row_id) REFERENCES event_headers(row_id) ON DELETE CASCADE
            )
            """.trimIndent(),
        )

        // queries by ID (load events)
        db.execSQL("CREATE UNIQUE INDEX event_headers_id       ON event_headers (id)")

        val orderBy =
            if (indexStrategy.useAndIndexIdOnOrderBy) {
                "created_at DESC, id ASC"
            } else {
                "created_at DESC"
            }

        // queries by limit (latest records), since, until (sync all) alone without any filter by kind.. rare
        if (indexStrategy.indexEventsByCreatedAtAlone) {
            db.execSQL("CREATE INDEX query_by_created_at_id        ON event_headers ($orderBy)")
        }

        // queries by author only, no kind — "everything by these pubkeys"
        // (profile archives, migration/backup tools). Relays need it;
        // clients query their supported kinds and can skip it.
        if (indexStrategy.indexEventsByPubkeyAlone) {
            db.execSQL("CREATE INDEX query_by_pubkey_created       ON event_headers (pubkey, $orderBy)")
        }

        // queries by kind only, mostly used in Global Feeds when author is not important.
        db.execSQL("CREATE INDEX query_by_kind_created         ON event_headers (kind, $orderBy)")

        // queries by kind + pubkey, but not d-tag, even if they are replaceables and addressables, by date.
        db.execSQL("CREATE INDEX query_by_kind_pubkey_created  ON event_headers (kind, pubkey, $orderBy)")

        // makes deletions on the event_header fast
        db.execSQL("CREATE INDEX fk_event_tags_header_id       ON event_tags (event_header_row_id)")

        // ---------------------------------------------------------------------------
        // These next 3 are a very slow indexes (80% of the insert time goes here)
        // ---------------------------------------------------------------------------
        if (indexStrategy.indexTagsByCreatedAtAlone) {
            // First one is only needed if the user is searching by tags without a kind.
            db.execSQL("CREATE INDEX query_by_tags_hash          ON event_tags (tag_hash, created_at DESC)")
        }

        // This is the default index for most clients: tags by specific kinds that are supported by the client.
        db.execSQL("CREATE INDEX query_by_tags_hash_kind         ON event_tags (tag_hash, kind, created_at DESC)")

        // this one is to allow search of tags by kind and author at the same time: NIP-04 DMs, reports,
        if (indexStrategy.indexTagsWithKindAndPubkey) {
            db.execSQL("CREATE INDEX query_by_tags_hash_kind_pubkey  ON event_tags (tag_hash, kind, pubkey_hash, created_at DESC)")
        }

        // Prevent updates to maintain immutability
        db.execSQL(
            """
            CREATE TRIGGER event_headers_prevent_update
            BEFORE UPDATE ON event_headers
            FOR EACH ROW
            BEGIN
                SELECT RAISE(ABORT, 'Error: Updates are not allowed.');
            END;
            """.trimIndent(),
        )

        db.execSQL(
            """
            CREATE TRIGGER event_tags_prevent_update
            BEFORE UPDATE ON event_tags
            FOR EACH ROW
            BEGIN
                SELECT RAISE(ABORT, 'Error: Updates are not allowed.');
            END;
            """.trimIndent(),
        )
    }

    override fun drop(db: SQLiteConnection) {
        db.execSQL("DROP TABLE IF EXISTS event_tags")
        db.execSQL("DROP TABLE IF EXISTS event_headers")
    }

    /**
     * v2 → v3 migration: the authors-only index arrived after v2 schemas
     * shipped. Backfills it (idempotently) for strategies that want it;
     * everyone else just gets the version bump.
     */
    fun migrateV2AddPubkeyIndex(db: SQLiteConnection) {
        if (!indexStrategy.indexEventsByPubkeyAlone) return
        db.execSQL("CREATE INDEX IF NOT EXISTS query_by_pubkey_created ON event_headers (pubkey, ${orderByColumns()})")
    }

    private fun orderByColumns() =
        if (indexStrategy.useAndIndexIdOnOrderBy) {
            "created_at DESC, id ASC"
        } else {
            "created_at DESC"
        }

    /**
     * Materializes any flag-gated index the current [indexStrategy] wants
     * but the on-disk schema predates. Flags are runtime configuration, not
     * schema — a deployment can flip one without a `user_version` bump — so
     * this runs idempotently on every open. The first open after enabling a
     * flag pays a one-time index build over the existing rows; subsequent
     * opens are no-ops. A disabled flag never drops an existing index (that
     * stays an operator decision).
     */
    fun ensureOptionalIndexes(db: SQLiteConnection) {
        if (indexStrategy.indexEventsByCreatedAtAlone) {
            db.execSQL("CREATE INDEX IF NOT EXISTS query_by_created_at_id ON event_headers (${orderByColumns()})")
        }
        if (indexStrategy.indexEventsByPubkeyAlone) {
            db.execSQL("CREATE INDEX IF NOT EXISTS query_by_pubkey_created ON event_headers (pubkey, ${orderByColumns()})")
        }
        if (indexStrategy.indexTagsByCreatedAtAlone) {
            db.execSQL("CREATE INDEX IF NOT EXISTS query_by_tags_hash ON event_tags (tag_hash, created_at DESC)")
        }
        if (indexStrategy.indexTagsWithKindAndPubkey) {
            db.execSQL("CREATE INDEX IF NOT EXISTS query_by_tags_hash_kind_pubkey ON event_tags (tag_hash, kind, pubkey_hash, created_at DESC)")
        }
    }

    val sqlInsertHeader =
        """
        INSERT INTO event_headers
            (id, pubkey, created_at, kind, tags, content, sig, d_tag, pubkey_owner_hash, etag_hash, atag_hash)
        VALUES
            (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """.trimIndent()

    val sqlInsertTags =
        """
        INSERT OR ROLLBACK INTO event_tags
            (event_header_row_id, tag_hash, created_at, kind, pubkey_hash)
        VALUES
            (?,?,?,?,?)
        """.trimIndent()

    fun insert(
        event: Event,
        db: SQLiteConnection,
    ): Long {
        val hasher = hasher(db)

        val kindLong = event.kind.toLong()
        val pubkeyHash = hasher.hash(event.pubKey)

        val eventOwnerHash =
            if (event is GiftWrapEvent) {
                event.recipientPubKey()?.let { hasher.hash(it) } ?: pubkeyHash
            } else {
                pubkeyHash
            }

        val eTagHash = hasher.hashETag(event.id)

        db.prepare(sqlInsertHeader).use { stmt ->
            stmt.bindText(1, event.id)
            stmt.bindText(2, event.pubKey)
            stmt.bindLong(3, event.createdAt)
            stmt.bindLong(4, kindLong)
            stmt.bindText(5, OptimizedJsonMapper.toJson(event.tags))
            stmt.bindText(6, event.content)
            stmt.bindText(7, event.sig)
            if (event is AddressableEvent) {
                val dTag = event.dTag()
                stmt.bindText(8, dTag)
                stmt.bindLong(9, eventOwnerHash)
                stmt.bindLong(10, eTagHash)
                stmt.bindLong(11, hasher.hashATag(AddressSerializer.assemble(event.kind, event.pubKey, dTag)))
            } else {
                stmt.bindNull(8)
                stmt.bindLong(9, eventOwnerHash)
                stmt.bindLong(10, eTagHash)
                stmt.bindNull(11)
            }
            stmt.step()
        }

        val headerId = db.lastInsertRowId()

        db.prepare(sqlInsertTags).use { stmtTags ->
            // sorting helps SQLLite by avoiding
            // rebalancing the tree every new insert
            val indexableTags = ArrayList<Long>()
            for (idx in event.tags.indices) {
                if (indexStrategy.shouldIndex(event.kind, event.tags[idx])) {
                    indexableTags.add(hasher.hash(event.tags[idx][0], event.tags[idx][1]))
                }
            }
            indexableTags.sort()
            indexableTags.forEach {
                stmtTags.bindLong(1, headerId)
                stmtTags.bindLong(2, it)
                stmtTags.bindLong(3, event.createdAt)
                stmtTags.bindLong(4, kindLong)
                stmtTags.bindLong(5, pubkeyHash)
                stmtTags.step()
                stmtTags.reset()
            }
        }

        return headerId
    }

    override fun deleteAll(db: SQLiteConnection) {
        db.execSQL("DELETE FROM event_tags")
        db.execSQL("DELETE FROM event_headers")
    }
}
