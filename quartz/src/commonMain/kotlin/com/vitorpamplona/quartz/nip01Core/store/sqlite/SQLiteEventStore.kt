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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext

class SQLiteEventStore(
    val driver: SQLiteDriver = BundledSQLiteDriver(),
    val dbName: String? = "events.db",
    val relay: NormalizedRelayUrl? = null,
    val indexStrategy: IndexingStrategy = DefaultIndexingStrategy(),
) {
    companion object {
        const val DATABASE_VERSION = 2
    }

    val connection: SQLiteConnection by lazy {
        openAndConfigure()
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

    private fun openAndConfigure(): SQLiteConnection {
        val db = driver.open(dbName ?: ":memory:")

        // 32MB memory cache
        db.execSQL("PRAGMA cache_size=-32000;")

        // makes sure the FKs are sane
        db.execSQL("PRAGMA foreign_keys = ON;")

        // SQLite implements mutations by appending them to a log, which it occasionally
        // compacts into the database. This is called Write-Ahead Logging (WAL)
        db.execSQL("PRAGMA journal_mode = WAL;")

        // The DB can be corrupted if the OS is shutdown before sync, which generally
        // doesn't happen on Android
        db.execSQL("PRAGMA synchronous = OFF;")

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

        return db
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

    fun clearDB() {
        modules.reversed().forEach { it.deleteAll(connection) }
    }

    suspend fun vacuum() {
        // VACUUM: Rebuilds the database file, reclaiming unused space
        // and reducing fragmentation.
        withContext(Dispatchers.IO) {
            connection.execSQL("VACUUM")
        }
    }

    suspend fun analyse() {
        // ANALYZE: Collects statistics about tables and indices
        // to help the query planner optimize queries.
        withContext(Dispatchers.IO) {
            connection.execSQL("ANALYZE")
        }
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

    fun insertEvent(event: Event) {
        if (event.isExpired()) throw SQLiteException("blocked: Cannot insert an expired event")
        if (event.kind.isEphemeral()) return

        connection.transaction {
            innerInsertEvent(event, this)
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

    fun transaction(body: Transaction.() -> Unit) {
        connection.transaction {
            with(Transaction(this)) {
                body()
            }
        }
    }

    fun <T : Event> query(filter: Filter): List<T> = queryBuilder.query(filter, connection)

    fun <T : Event> query(filters: List<Filter>): List<T> = queryBuilder.query(filters, connection)

    fun <T : Event> query(
        filter: Filter,
        onEach: (T) -> Unit,
    ) = queryBuilder.query(filter, connection, onEach)

    fun <T : Event> query(
        filters: List<Filter>,
        onEach: (T) -> Unit,
    ) = queryBuilder.query(filters, connection, onEach)

    fun rawQuery(filter: Filter): List<RawEvent> = queryBuilder.rawQuery(filter, connection)

    fun rawQuery(filters: List<Filter>): List<RawEvent> = queryBuilder.rawQuery(filters, connection)

    fun rawQuery(
        filter: Filter,
        onEach: (RawEvent) -> Unit,
    ) = queryBuilder.rawQuery(filter, connection, onEach)

    fun rawQuery(
        filters: List<Filter>,
        onEach: (RawEvent) -> Unit,
    ) = queryBuilder.rawQuery(filters, connection, onEach)

    fun planQuery(filter: Filter) = queryBuilder.planQuery(filter, seedModule.hasher(connection), connection)

    fun planQuery(filters: List<Filter>) = queryBuilder.planQuery(filters, seedModule.hasher(connection), connection)

    fun count(filter: Filter): Int = queryBuilder.count(filter, connection)

    fun count(filters: List<Filter>): Int = queryBuilder.count(filters, connection)

    fun delete(filter: Filter) {
        queryBuilder.delete(filter, connection)
    }

    fun delete(filters: List<Filter>) {
        queryBuilder.delete(filters, connection)
    }

    fun delete(id: HexKey): Int {
        connection.execSQL("DELETE FROM event_headers WHERE id = ?", arrayOf(id))
        return connection.changes()
    }

    fun deleteExpiredEvents() = expirationModule.deleteExpiredEvents(connection)
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
