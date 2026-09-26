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
import com.vitorpamplona.quartz.nip01Core.core.isAddressable
import com.vitorpamplona.quartz.nip01Core.store.owner
import com.vitorpamplona.quartz.nip01Core.tags.dTag.dTag

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
        db.execSQL(PREVENT_HEADER_UPDATES)

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
        db.execSQL("DROP TABLE IF EXISTS $TAG_VALUES")
        db.execSQL("DROP TABLE IF EXISTS event_tags")
        db.execSQL("DROP TABLE IF EXISTS event_headers")
    }

    /**
     * v5 → v6: `d_tag` follows the kind (NIP-01), not the parsed class. Rows of an
     * addressable kind Quartz had no class for were stored with `d_tag` NULL, so
     * they never replaced each other. Each gets its `d` now, and of each address
     * only the newest version stays (ties to the lowest id), as on insert.
     */
    fun migrateV5AddressableByKind(db: SQLiteConnection) {
        class Version(
            val rowId: Long,
            val createdAt: Long,
            val id: String,
            val missing: Boolean,
        )
        val byAddress = LinkedHashMap<Triple<Int, String, String>, MutableList<Version>>()
        db
            .prepare(
                "SELECT row_id, kind, pubkey, created_at, id, coalesce((SELECT json_extract(j.value, '$[1]') FROM json_each(h.tags) AS j " +
                    "WHERE json_extract(j.value, '$[0]') = 'd' AND json_array_length(j.value) > 1 ORDER BY j.key LIMIT 1), '') " +
                    "FROM event_headers AS h WHERE kind >= 30000 AND kind < 40000 AND d_tag IS NULL",
            ).use { st ->
                while (st.step()) {
                    val address = Triple(st.getLong(1).toInt(), st.getText(2), st.getText(5))
                    byAddress.getOrPut(address) { ArrayList() }.add(Version(st.getLong(0), st.getLong(3), st.getText(4), missing = true))
                }
            }
        if (byAddress.isEmpty()) return
        val hasher = hasher(db)
        // Rows are immutable but for this one correction of what they were indexed under.
        db.execSQL("DROP TRIGGER IF EXISTS event_headers_prevent_update")
        for ((address, versions) in byAddress) {
            val (kind, pubkey, d) = address
            db.prepare("SELECT row_id, created_at, id FROM event_headers WHERE kind = ? AND pubkey = ? AND d_tag = ?").use { st ->
                st.bindLong(1, kind.toLong())
                st.bindText(2, pubkey)
                st.bindText(3, d)
                while (st.step()) versions.add(Version(st.getLong(0), st.getLong(1), st.getText(2), missing = false))
            }
            val winner = versions.minWith(compareByDescending<Version> { it.createdAt }.thenBy { it.id })
            versions.forEach { v ->
                if (v !== winner) {
                    db.prepare("DELETE FROM event_headers WHERE row_id = ?").use {
                        it.bindLong(1, v.rowId)
                        it.step()
                    }
                }
            }
            if (winner.missing) {
                db.prepare("UPDATE event_headers SET d_tag = ?, atag_hash = ? WHERE row_id = ?").use {
                    it.bindText(1, d)
                    it.bindLong(2, hasher.hashATag(AddressSerializer.assemble(kind, pubkey, d)))
                    it.bindLong(3, winner.rowId)
                    it.step()
                }
            }
        }
        db.execSQL(PREVENT_HEADER_UPDATES)
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

    /**
     * Creates and backfills `event_tag_values` when [IndexingStrategy.indexTagValues]
     * is on and the table isn't there yet, and drops it when the flag is off. Runs
     * on every open, like [ensureOptionalIndexes].
     */
    fun ensureTagValues(db: SQLiteConnection) {
        val exists = db.prepare("SELECT 1 FROM sqlite_master WHERE type = 'table' AND name = '$TAG_VALUES'").use { it.step() }
        if (!indexStrategy.indexTagValues) {
            if (exists) db.execSQL("DROP TABLE $TAG_VALUES")
            return
        }
        if (exists) {
            ensureTagValuesByEvent(db)
            return
        }
        db.execSQL(
            """
            CREATE TABLE $TAG_VALUES (
                event_header_row_id INTEGER NOT NULL,
                idx INTEGER NOT NULL,
                t0 TEXT NOT NULL,
                t1 TEXT,
                t2 TEXT,
                t3 TEXT,
                t4 TEXT,
                kind INTEGER NOT NULL,
                created_at INTEGER NOT NULL,
                FOREIGN KEY (event_header_row_id) REFERENCES event_headers(row_id) ON DELETE CASCADE
            )
            """.trimIndent(),
        )
        db.execSQL(
            "INSERT INTO $TAG_VALUES (event_header_row_id, idx, t0, t1, t2, t3, t4, kind, created_at) " +
                "SELECT h.row_id, j.key, json_extract(j.value, '$[0]'), json_extract(j.value, '$[1]'), json_extract(j.value, '$[2]'), " +
                "json_extract(j.value, '$[3]'), json_extract(j.value, '$[4]'), h.kind, h.created_at " +
                "FROM event_headers AS h, json_each(h.tags) AS j WHERE json_type(j.value, '$[0]') IS NOT NULL",
        )
        // Indexes after the backfill: one sort instead of a rebalance per row.
        db.execSQL("CREATE INDEX ${TAG_VALUES}_by_name_value ON $TAG_VALUES (t0, t1, kind)")
        db.execSQL("CREATE INDEX $TAG_VALUES_BY_EVENT ON $TAG_VALUES (event_header_row_id, t0, t1)")
    }

    /**
     * The per-event index covers `t0` and `t1`, so one tag of an event (a NIP-85
     * card's `rank`) is read from the index alone: 1.0 s to 0.57 s over 300k cards.
     * Tables made before it carry `(event_header_row_id)` alone, replaced here.
     */
    private fun ensureTagValuesByEvent(db: SQLiteConnection) {
        val has = db.prepare("SELECT 1 FROM sqlite_master WHERE type = 'index' AND name = '$TAG_VALUES_BY_EVENT'").use { it.step() }
        if (has) return
        db.execSQL("DROP INDEX IF EXISTS ${TAG_VALUES}_by_event")
        db.execSQL("CREATE INDEX $TAG_VALUES_BY_EVENT ON $TAG_VALUES (event_header_row_id, t0, t1)")
    }

    val sqlInsertTagValues =
        """
        INSERT INTO $TAG_VALUES
            (event_header_row_id, idx, t0, t1, t2, t3, t4, kind, created_at)
        VALUES
            (?, ?, ?, ?, ?, ?, ?, ?, ?)
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

        val ownerKey = event.owner()
        val eventOwnerHash = if (ownerKey == event.pubKey) pubkeyHash else hasher.hash(ownerKey)

        val eTagHash = hasher.hashETag(event.id)

        db.prepare(sqlInsertHeader).use { stmt ->
            stmt.bindText(1, event.id)
            stmt.bindText(2, event.pubKey)
            stmt.bindLong(3, event.createdAt)
            stmt.bindLong(4, kindLong)
            stmt.bindText(5, OptimizedJsonMapper.toJson(event.tags))
            stmt.bindText(6, event.content)
            stmt.bindText(7, event.sig)
            // Addressable kinds by kind (NIP-01), whether or not Quartz has a class for
            // them; replaceable classes keep their fixed '' (a-tag deletions match it).
            val dTag =
                when {
                    event.kind.isAddressable() -> event.tags.dTag()
                    event is AddressableEvent -> event.dTag()
                    else -> null
                }
            if (dTag != null) {
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

        if (indexStrategy.indexTagValues) {
            db.prepare(sqlInsertTagValues).use { stmt ->
                event.tags.forEachIndexed { idx, tag ->
                    if (tag.isEmpty()) return@forEachIndexed
                    stmt.bindLong(1, headerId)
                    stmt.bindLong(2, idx.toLong())
                    for (i in 0..4) if (i < tag.size) stmt.bindText(3 + i, tag[i]) else stmt.bindNull(3 + i)
                    stmt.bindLong(8, kindLong)
                    stmt.bindLong(9, event.createdAt)
                    stmt.step()
                    stmt.reset()
                }
            }
        }

        return headerId
    }

    override fun deleteAll(db: SQLiteConnection) {
        if (indexStrategy.indexTagValues) db.execSQL("DELETE FROM $TAG_VALUES")
        db.execSQL("DELETE FROM event_tags")
        db.execSQL("DELETE FROM event_headers")
    }

    companion object {
        /** The NQL tag-values table ([IndexingStrategy.indexTagValues]). */
        const val TAG_VALUES = "event_tag_values"

        val PREVENT_HEADER_UPDATES =
            """
            CREATE TRIGGER event_headers_prevent_update
            BEFORE UPDATE ON event_headers
            FOR EACH ROW
            BEGIN
                SELECT RAISE(ABORT, 'Error: Updates are not allowed.');
            END;
            """.trimIndent()
        const val TAG_VALUES_BY_EVENT = "${TAG_VALUES}_by_event_value"
    }
}
