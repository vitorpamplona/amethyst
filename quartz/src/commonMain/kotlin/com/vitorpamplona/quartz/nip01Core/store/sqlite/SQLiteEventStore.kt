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
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.Kind
import com.vitorpamplona.quartz.nip01Core.core.OptimizedJsonMapper
import com.vitorpamplona.quartz.nip01Core.core.isEphemeral
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.store.IEventStore
import com.vitorpamplona.quartz.nip40Expiration.isExpired
import com.vitorpamplona.quartz.utils.EventFactory

class SQLiteEventStore(
    val driver: SQLiteDriver = BundledSQLiteDriver(),
    val dbName: String? = "events.db",
    val relay: NormalizedRelayUrl? = null,
    val indexStrategy: IndexingStrategy = DefaultIndexingStrategy(),
    val numReaders: Int = 4,
) {
    companion object {
        const val DATABASE_VERSION = 2
    }

    val seedModule = SeedModule()

    val fullTextSearchModule = FullTextSearchModule()
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
            }
        }
    }

    suspend fun clearDB() =
        pool.useWriter { db ->
            modules.reversed().forEach { it.deleteAll(db) }
        }

    suspend fun vacuum() =
        pool.useWriter { db ->
            // VACUUM: Rebuilds the database file, reclaiming unused space
            // and reducing fragmentation.
            db.execSQL("VACUUM")
        }

    suspend fun analyse() =
        pool.useWriter { db ->
            // ANALYZE: Collects statistics about tables and indices
            // to help the query planner optimize queries.
            db.execSQL("ANALYZE")
        }

    private fun innerInsertEvent(
        event: Event,
        db: SQLiteConnection,
    ) {
        val headerId = eventIndexModule.insert(event, db)
        deletionModule.insert(event, db)
        expirationModule.insert(event, headerId, db)
        fullTextSearchModule.insert(event, headerId, db)
        rightToVanishModule.insert(event, relay, headerId, db)
    }

    suspend fun insertEvent(event: Event) {
        if (event.isExpired()) throw SQLiteException("blocked: Cannot insert an expired event")
        if (event.kind.isEphemeral()) return

        pool.useWriter { db ->
            db.transaction {
                innerInsertEvent(event, this)
            }
        }
    }

    inner class Transaction(
        val db: SQLiteConnection,
    ) : IEventStore.ITransaction {
        override fun insert(event: Event) {
            if (event.isExpired()) throw SQLiteException("blocked: Cannot insert an expired event")
            if (event.kind.isEphemeral()) return

            innerInsertEvent(event, db)
        }
    }

    suspend fun transaction(body: Transaction.() -> Unit) {
        pool.useWriter { db ->
            db.transaction {
                with(Transaction(this)) {
                    body()
                }
            }
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

    suspend fun delete(filter: Filter) = pool.useWriter { queryBuilder.delete(filter, it) }

    suspend fun delete(filters: List<Filter>) = pool.useWriter { queryBuilder.delete(filters, it) }

    suspend fun delete(id: HexKey): Int =
        pool.useWriter { db ->
            db.execSQL("DELETE FROM event_headers WHERE id = ?", arrayOf(id))
            db.changes()
        }

    suspend fun deleteExpiredEvents() = pool.useWriter { expirationModule.deleteExpiredEvents(it) }

    fun close() = pool.close()
}

class RawEvent(
    val id: HexKey,
    val pubKey: HexKey,
    val createdAt: Long,
    val kind: Kind,
    val jsonTags: String,
    val content: String,
    val sig: HexKey,
) {
    fun <T : Event> toEvent() =
        EventFactory.create<T>(
            id,
            pubKey,
            createdAt,
            kind,
            OptimizedJsonMapper.fromJsonToTagArray(jsonTags),
            content,
            sig,
        )
}
