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
import androidx.sqlite.SQLiteDriver
import androidx.sqlite.SQLiteException
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.vitorpamplona.negentropy.storage.IStorage
import com.vitorpamplona.quartz.nip01Core.core.AddressableEvent
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.isAddressable
import com.vitorpamplona.quartz.nip01Core.core.isEphemeral
import com.vitorpamplona.quartz.nip01Core.core.isReplaceable
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.store.FtsReindexProgress
import com.vitorpamplona.quartz.nip01Core.store.IEventStore
import com.vitorpamplona.quartz.nip01Core.store.IdAndTime
import com.vitorpamplona.quartz.nip01Core.store.RawEvent
import com.vitorpamplona.quartz.nip09Deletions.DeletionEvent
import com.vitorpamplona.quartz.nip40Expiration.isExpired
import com.vitorpamplona.quartz.nip62RequestToVanish.RequestToVanishEvent
import com.vitorpamplona.quartz.nip77Negentropy.LiveNegentropyIndex

class SQLiteEventStore(
    val driver: SQLiteDriver = BundledSQLiteDriver(),
    val dbName: String? = "events.db",
    val relay: NormalizedRelayUrl? = null,
    val indexStrategy: IndexingStrategy = DefaultIndexingStrategy(),
    val numReaders: Int = 4,
) {
    companion object {
        const val DATABASE_VERSION = 4
    }

    val seedModule = SeedModule()

    val fullTextSearchModule =
        FullTextSearchModule(
            indexStrategy.indexFullTextSearch,
            indexStrategy.deferFullTextSearchIndexing,
        )
    val eventIndexModule =
        EventIndexesModule(
            seedModule::hasher,
            indexStrategy,
        )

    val replaceableModule = ReplaceableModule()
    val addressableModule = AddressableModule()
    val ephemeralModule = EphemeralModule()

    val deletionModule = DeletionRequestModule(seedModule::hasher)
    val expirationModule = ExpirationModule()
    val rightToVanishModule = RightToVanishModule(seedModule::hasher)

    val queryBuilder =
        QueryBuilder(
            fullTextSearchModule,
            seedModule::hasher,
            indexStrategy,
        )

    /**
     * Always-current `(created_at, id)` set for NIP-77 (see
     * [IndexingStrategy.maintainLiveNegentropyIndex]). Populated lazily
     * by the first [liveNegentropySnapshot]; kept current by the write
     * paths through a [LiveIndexDelta] applied only after each
     * transaction commits, so a rolled-back row never leaks into it.
     */
    val liveNegentropyIndex: LiveNegentropyIndex? =
        if (indexStrategy.maintainLiveNegentropyIndex) LiveNegentropyIndex() else null

    val modules =
        listOf(
            seedModule,
            eventIndexModule,
            replaceableModule,
            addressableModule,
            ephemeralModule,
            deletionModule,
            expirationModule,
            rightToVanishModule,
            fullTextSearchModule,
        )

    val pool: SQLiteConnectionPool by lazy {
        SQLiteConnectionPool(
            driver = driver,
            dbName = dbName,
            numReaders = numReaders,
            onConfigure = { db ->
                // 32MB memory cache (per-connection).
                db.execSQL("PRAGMA cache_size=-32000;")

                // Make sure the FKs are sane (per-connection).
                db.execSQL("PRAGMA foreign_keys = ON;")

                // SQLite implements mutations by appending them to a log,
                // which it occasionally compacts into the database. This
                // is called Write-Ahead Logging (WAL). Setting it on the
                // first connection is enough — `journal_mode` is
                // database-wide; subsequent connections inherit it.
                db.execSQL("PRAGMA journal_mode = WAL;")

                // The DB can be corrupted if the OS shuts down before
                // sync, which generally doesn't happen on Android.
                db.execSQL("PRAGMA synchronous = OFF;")

                // Without busy_timeout, BEGIN IMMEDIATE returns SQLITE_BUSY
                // the instant another connection holds a conflicting lock —
                // e.g. a WAL auto-checkpoint or a reader transiently
                // upgrading its snapshot. With it, SQLite retries internally
                // for up to N ms before giving up. Matches Room's default.
                db.execSQL("PRAGMA busy_timeout = 5000;")
            },
            onMigrate = { db ->
                val currentVersion = getUserVersion(db)
                if (currentVersion == 0) {
                    db.transaction {
                        onCreate(this)
                        setUserVersion(this, DATABASE_VERSION)
                    }
                } else if (currentVersion < DATABASE_VERSION) {
                    db.transaction {
                        onUpgrade(this, currentVersion, DATABASE_VERSION)
                        setUserVersion(this, DATABASE_VERSION)
                    }
                }
            },
        )
    }

    private fun getUserVersion(db: SQLiteConnection): Int =
        db.prepare("PRAGMA user_version").use { stmt ->
            stmt.step()
            stmt.getInt(0)
        }

    private fun setUserVersion(
        db: SQLiteConnection,
        version: Int,
    ) {
        db.execSQL("PRAGMA user_version = $version")
    }

    fun onCreate(db: SQLiteConnection) {
        modules.forEach {
            it.create(db)
        }
    }

    fun onUpgrade(
        db: SQLiteConnection,
        oldVersion: Int,
        newVersion: Int,
    ) {
        // Handle all intermediate versions
        for (version in oldVersion..<newVersion) {
            when (version) {
                1 -> {
                    // Upgrade from version 1 to 2
                    // We changed event_tags to use a probabilistic hash as tag
                    modules.reversed().forEach { it.drop(db) }
                    modules.forEach { it.create(db) }
                }
                2 -> {
                    // Upgrade from version 2 to 3: authors-only query index
                    // (created only for strategies that opt in).
                    eventIndexModule.migrateV2AddPubkeyIndex(db)
                }
                3 -> {
                    // Upgrade from version 3 to 4: deferred-FTS watermark.
                    // Pre-v4 rows were indexed synchronously, so the
                    // watermark seeds at the current MAX(row_id).
                    fullTextSearchModule.createStateTable(db)
                }
            }
        }
    }

    suspend fun clearDB() {
        pool.useWriter { db ->
            modules.reversed().forEach { it.deleteAll(db) }
        }
        liveNegentropyIndex?.invalidate()
    }

    suspend fun vacuum() =
        pool.useWriter { db ->
            // VACUUM: Rebuilds the database file, reclaiming unused space
            // and reducing fragmentation.
            db.execSQL("VACUUM")
        }

    /**
     * True when something must drive [ftsCatchUp] for NIP-50 to work —
     * i.e. the strategy defers tokenization off the insert path. The
     * relay server wires a background worker (and a pre-search drain)
     * when this is set.
     */
    val needsFtsCatchUp: Boolean =
        indexStrategy.indexFullTextSearch && indexStrategy.deferFullTextSearchIndexing

    /**
     * One deferred-FTS catch-up batch; returns `true` once the index has
     * caught up with the table. Each batch is its own write transaction,
     * so publishes interleave between batches instead of stalling behind
     * a long rebuild.
     */
    suspend fun ftsCatchUp(batchSize: Int = 1000): Boolean =
        pool.useWriter { db ->
            var done = false
            db.transaction {
                done = fullTextSearchModule.catchUpBatch(this, batchSize)
            }
            done
        }

    suspend fun analyse() =
        pool.useWriter { db ->
            // ANALYZE: Collects statistics about tables and indices
            // to help the query planner optimize queries.
            db.execSQL("ANALYZE")
        }

    /**
     * Live-index mutations gathered during one write transaction and
     * applied only after its COMMIT — a rolled-back row (savepoint or
     * whole-transaction failure) must never reach the index, and the
     * index must never advertise an id before it is durable. `null`
     * when [liveNegentropyIndex] is off: zero cost on the write path.
     */
    internal class LiveIndexDelta {
        val added = ArrayList<IdAndTime>()
        val removed = ArrayList<IdAndTime>()

        /**
         * Set when a row's side effects delete OTHER rows in ways this
         * delta can't itemize (kind-5 targets, vanish-by-pubkey). The
         * whole index is dropped and lazily rebuilt from one scan.
         */
        var invalidateAll = false
    }

    /**
     * Starts delta tracking for a write transaction, or `null` when there
     * is nothing to track — index off, or not (yet) populated. An
     * unpopulated index is covered by its next [LiveNegentropyIndex.rebuild]
     * scan instead, so ingest pays zero bookkeeping until the first
     * NEG-OPEN actually builds the index.
     *
     * MUST be called while already holding the writer connection: the
     * populated check races [liveNegentropySnapshot]'s rebuild otherwise
     * (rebuild flips `populated` under the writer mutex — deciding out
     * here could skip tracking for a transaction that commits after the
     * rebuild's scan, silently losing its rows).
     */
    private fun newDeltaOrNull(): LiveIndexDelta? = if (liveNegentropyIndex?.isPopulated() == true) LiveIndexDelta() else null

    /** Records one successfully inserted row into the pending delta. */
    private fun LiveIndexDelta.recordAccepted(
        event: Event,
        displaced: IdAndTime?,
    ) {
        if (event is DeletionEvent || (event is RequestToVanishEvent && event.shouldVanishFrom(relay))) {
            // Their own row lands too, but the rebuild scan picks it up.
            invalidateAll = true
            return
        }
        displaced?.let { removed += it }
        added += IdAndTime(event.createdAt, event.id)
    }

    private fun applyAfterCommit(delta: LiveIndexDelta?) {
        val index = liveNegentropyIndex ?: return
        if (delta == null) return
        if (delta.invalidateAll) {
            index.invalidate()
            return
        }
        delta.removed.forEach(index::remove)
        delta.added.forEach(index::insert)
    }

    /**
     * The row the replaceable/addressable BEFORE-INSERT trigger is about
     * to delete for [event], or `null` when nothing will be displaced.
     * Must run *before* the insert (the trigger fires during it) and
     * mirrors the trigger predicates exactly — including the NIP-01
     * lowest-id-wins tie-break. At most one row can match thanks to the
     * partial unique indexes, and the same indexes make this lookup
     * O(log n). Only called when the live index is on.
     */
    private fun displacedBy(
        event: Event,
        db: SQLiteConnection,
    ): IdAndTime? {
        // The addressable branch requires the parsed class to carry a
        // d-tag, mirroring the header insert: events without one store
        // d_tag NULL, and the trigger's `d_tag = NEW.d_tag` never
        // matches NULL — so nothing gets displaced.
        val addressable = event.kind.isAddressable() && event is AddressableEvent
        val sql =
            when {
                event.kind.isReplaceable() ->
                    """
                    SELECT created_at, id FROM event_headers
                    WHERE kind = ? AND pubkey = ?
                      AND (created_at < ? OR (created_at = ? AND id > ?))
                    """.trimIndent()

                addressable ->
                    """
                    SELECT created_at, id FROM event_headers
                    WHERE kind = ? AND pubkey = ? AND d_tag = ?
                      AND kind >= 30000 AND kind < 40000
                      AND (created_at < ? OR (created_at = ? AND id > ?))
                    """.trimIndent()

                else -> return null
            }
        db.prepare(sql).use { stmt ->
            var i = 1
            stmt.bindLong(i++, event.kind.toLong())
            stmt.bindText(i++, event.pubKey)
            if (addressable) stmt.bindText(i++, (event as AddressableEvent).dTag())
            stmt.bindLong(i++, event.createdAt)
            stmt.bindLong(i++, event.createdAt)
            stmt.bindText(i, event.id)
            if (!stmt.step()) return null
            return IdAndTime(stmt.getLong(0), stmt.getText(1))
        }
    }

    private fun innerInsertEvent(
        event: Event,
        db: SQLiteConnection,
        delta: LiveIndexDelta? = null,
    ) {
        val displaced = if (delta != null) displacedBy(event, db) else null
        val headerId = eventIndexModule.insert(event, db)
        deletionModule.insert(event, db)
        expirationModule.insert(event, headerId, db)
        fullTextSearchModule.insert(event, headerId, db)
        rightToVanishModule.insert(event, relay, headerId, db)
        delta?.recordAccepted(event, displaced)
    }

    suspend fun insertEvent(event: Event) {
        if (event.isExpired()) throw SQLiteException("blocked: Cannot insert an expired event")
        if (event.kind.isEphemeral()) return

        pool.useWriter { db ->
            val delta = newDeltaOrNull()
            db.transaction {
                innerInsertEvent(event, this, delta)
            }
            // Still holding the writer mutex: the transaction above has
            // committed, and applying here keeps index updates in exact
            // commit order across every writer (and the rebuild, which
            // also runs under this mutex).
            applyAfterCommit(delta)
        }
    }

    /**
     * Group-commit batch insert with per-row error isolation via
     * SAVEPOINTs. Acquires the writer mutex once and wraps every
     * inserts in a single outer transaction so the WAL append + sync
     * cost is paid once for the whole batch.
     *
     * Per-row contract:
     *  - Validation errors (expired) and per-row INSERT failures
     *    (UNIQUE constraint, etc.) ROLLBACK only that row's savepoint;
     *    other rows commit.
     *  - Ephemeral kinds are accepted without writing — the live
     *    stream still surfaces them; persistence is intentionally a
     *    no-op per NIP-01.
     *
     * Outer-commit failure throws; the caller treats every entry as
     * `Rejected` (this is what the IEventStore contract documents).
     */
    suspend fun batchInsertEvents(events: List<Event>): List<IEventStore.InsertOutcome> {
        if (events.isEmpty()) return emptyList()
        val outcomes = ArrayList<IEventStore.InsertOutcome>(events.size)
        pool.useWriter { db ->
            val delta = newDeltaOrNull()
            db.transaction {
                events.forEachIndexed { i, event ->
                    outcomes.add(insertWithSavepoint(event, i, this, delta))
                }
            }
            // Post-commit, pre-mutex-release: see insertEvent.
            applyAfterCommit(delta)
        }
        return outcomes
    }

    private fun insertWithSavepoint(
        event: Event,
        index: Int,
        db: SQLiteConnection,
        delta: LiveIndexDelta?,
    ): IEventStore.InsertOutcome {
        if (event.isExpired()) {
            return IEventStore.InsertOutcome.Rejected("blocked: Cannot insert an expired event")
        }
        if (event.kind.isEphemeral()) return IEventStore.InsertOutcome.Accepted

        val sp = "ev$index"
        db.execSQL("SAVEPOINT $sp")
        return try {
            // The delta records this row only after every module insert
            // succeeded (last step of innerInsertEvent), so a savepoint
            // rollback below leaves the pending delta untouched.
            innerInsertEvent(event, db, delta)
            db.execSQL("RELEASE SAVEPOINT $sp")
            IEventStore.InsertOutcome.Accepted
        } catch (e: Throwable) {
            // Roll back just this row, then release the (now empty)
            // savepoint frame so the next iteration's BEGIN works.
            // Both calls are individually try/catch'd because a failed
            // ROLLBACK shouldn't mask the original cause.
            runCatching { db.execSQL("ROLLBACK TRANSACTION TO SAVEPOINT $sp") }
            runCatching { db.execSQL("RELEASE SAVEPOINT $sp") }
            IEventStore.InsertOutcome.Rejected(e.message ?: e::class.simpleName ?: "insert failed")
        }
    }

    inner class Transaction internal constructor(
        val db: SQLiteConnection,
        private val delta: LiveIndexDelta?,
    ) : IEventStore.ITransaction {
        override fun insert(event: Event) {
            if (event.isExpired()) throw SQLiteException("blocked: Cannot insert an expired event")
            if (event.kind.isEphemeral()) return

            innerInsertEvent(event, db, delta)
        }
    }

    suspend fun transaction(body: Transaction.() -> Unit) {
        pool.useWriter { db ->
            val delta = newDeltaOrNull()
            db.transaction {
                with(Transaction(this, delta)) {
                    body()
                }
            }
            // Post-commit, pre-mutex-release: see insertEvent.
            applyAfterCommit(delta)
        }
    }

    suspend fun <T : Event> query(filter: Filter): List<T> = pool.useReader { queryBuilder.query(filter, it) }

    suspend fun <T : Event> query(filters: List<Filter>): List<T> = pool.useReader { queryBuilder.query(filters, it) }

    suspend fun <T : Event> query(
        filter: Filter,
        onEach: (T) -> Unit,
    ) = pool.useReader { queryBuilder.query(filter, it, onEach) }

    suspend fun <T : Event> query(
        filters: List<Filter>,
        onEach: (T) -> Unit,
    ) = pool.useReader { queryBuilder.query(filters, it, onEach) }

    suspend fun rawQuery(filter: Filter): List<RawEvent> = pool.useReader { queryBuilder.rawQuery(filter, it) }

    suspend fun rawQuery(filters: List<Filter>): List<RawEvent> = pool.useReader { queryBuilder.rawQuery(filters, it) }

    suspend fun rawQuery(
        filter: Filter,
        onEach: (RawEvent) -> Unit,
    ) = pool.useReader { queryBuilder.rawQuery(filter, it, onEach) }

    suspend fun rawQuery(
        filters: List<Filter>,
        onEach: (RawEvent) -> Unit,
    ) = pool.useReader { queryBuilder.rawQuery(filters, it, onEach) }

    suspend fun planQuery(filter: Filter) = pool.useReader { queryBuilder.planQuery(filter, seedModule.hasher(it), it) }

    suspend fun planQuery(filters: List<Filter>) = pool.useReader { queryBuilder.planQuery(filters, seedModule.hasher(it), it) }

    suspend fun count(filter: Filter): Int = pool.useReader { queryBuilder.count(filter, it) }

    suspend fun count(filters: List<Filter>): Int = pool.useReader { queryBuilder.count(filters, it) }

    suspend fun snapshotIdsForNegentropy(
        filters: List<Filter>,
        maxEntries: Int? = null,
    ): List<IdAndTime> = pool.useReader { queryBuilder.snapshotIdsForNegentropy(filters, it, maxEntries) }

    suspend fun delete(filter: Filter) {
        pool.useWriter { queryBuilder.delete(filter, it) }
        // Delete-by-filter can't itemize what it removed; drop the live
        // index and let the next NEG-OPEN rebuild from one scan.
        liveNegentropyIndex?.invalidate()
    }

    suspend fun delete(filters: List<Filter>) {
        pool.useWriter { queryBuilder.delete(filters, it) }
        liveNegentropyIndex?.invalidate()
    }

    suspend fun delete(id: HexKey): Int {
        val changes =
            pool.useWriter { db ->
                db.execSQL("DELETE FROM event_headers WHERE id = ?", arrayOf(id))
                db.changes()
            }
        if (changes > 0) liveNegentropyIndex?.invalidate()
        return changes
    }

    suspend fun deleteExpiredEvents() {
        val swept =
            pool.useWriter { db ->
                expirationModule.deleteExpiredEvents(db)
                db.changes()
            }
        if (swept > 0) liveNegentropyIndex?.invalidate()
    }

    /**
     * Sealed live-index snapshot of the FULL stored set for NIP-77, or
     * `null` when the live index is off (strategy default) or the set
     * exceeds [maxEntries] — callers fall back to the scan path either
     * way. The first call after boot or an invalidation rebuilds the
     * index from one scan **on the writer connection**, so no insert can
     * commit between the scan and the rebuild — after that, the write
     * paths keep it current and this is a cached-snapshot lookup.
     */
    suspend fun liveNegentropySnapshot(maxEntries: Int): IStorage? {
        val index = liveNegentropyIndex ?: return null
        if (!index.isPopulated()) {
            pool.useWriter { db ->
                if (!index.isPopulated()) {
                    index.rebuild(queryBuilder.snapshotIdsForNegentropy(listOf(Filter()), db, null))
                }
            }
        }
        return index.sealedSnapshot(maxEntries)
    }

    /**
     * Wipe and rebuild the NIP-50 full-text search index for every
     * stored event. See [IEventStore.reindexFullTextSearch] for when to
     * call this. The whole rebuild runs in a single write transaction so
     * the WAL append + sync cost is paid once.
     */
    suspend fun reindexFullTextSearch() =
        pool.useWriter { db ->
            db.transaction {
                fullTextSearchModule.reindexAll(db)
            }
        }

    /**
     * One batch of a resumable FTS rebuild. See
     * [IEventStore.reindexFullTextSearch]. The opaque cursor is the last
     * `row_id` processed; `null` (or an unparseable value) starts from
     * the beginning. Each batch is its own write transaction.
     */
    suspend fun reindexFullTextSearch(
        resumeFrom: String?,
        batchSize: Int,
    ): FtsReindexProgress =
        pool.useWriter { db ->
            db.transaction {
                fullTextSearchModule.reindexBatch(db, resumeFrom?.toLongOrNull() ?: 0L, batchSize)
            }
        }

    fun close() = pool.close()
}
