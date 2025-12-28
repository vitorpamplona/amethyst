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

import android.content.Context
import android.database.sqlite.SQLiteConstraintException
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import androidx.core.database.sqlite.transaction
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.isEphemeral
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.store.IEventStore
import com.vitorpamplona.quartz.nip01Core.store.sqlite.EventIndexesModule.IndexingStrategy
import com.vitorpamplona.quartz.nip40Expiration.isExpired
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class SQLiteEventStore(
    val context: Context,
    val dbName: String? = "events.db",
    val relayUrl: String? = null,
    val tagIndexStrategy: IndexingStrategy = IndexingStrategy(),
) : SQLiteOpenHelper(context, dbName, null, DATABASE_VERSION) {
    companion object {
        const val DATABASE_VERSION = 2
    }

    val seedModule = SeedModule()

    val fullTextSearchModule = FullTextSearchModule()
    val eventIndexModule = EventIndexesModule(fullTextSearchModule, seedModule::hasher, tagIndexStrategy)

    val replaceableModule = ReplaceableModule()
    val addressableModule = AddressableModule()
    val ephemeralModule = EphemeralModule()

    val deletionModule = DeletionRequestModule(seedModule::hasher)
    val expirationModule = ExpirationModule()
    val rightToVanishModule = RightToVanishModule(seedModule::hasher)

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

    override fun onConfigure(db: SQLiteDatabase) {
        super.onConfigure(db)

        // makes sure the FKs are sane
        db.setForeignKeyConstraintsEnabled(true)

        // SQLite implements mutations by appending them to a log, which it occasionally
        // compacts into the database. This is called Write-Ahead Logging (WAL)
        db.enableWriteAheadLogging()

        // The DB can be corrupted if the OS is shutdown before sync, which generally
        // doesn't happen on Android
        db.execSQL("PRAGMA synchronous = OFF")
    }

    override fun onCreate(db: SQLiteDatabase) {
        modules.forEach {
            it.create(db)
        }
    }

    override fun onUpgrade(
        db: SQLiteDatabase,
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
        val db = writableDatabase
        modules.reversed().forEach { it.deleteAll(db) }
    }

    suspend fun vacuum() {
        // 1. ANALYZE: Collects statistics about tables and indices
        // to help the query planner optimize queries.
        withContext(Dispatchers.IO) {
            writableDatabase.execSQL("VACUUM")
        }
    }

    suspend fun analyse() {
        // 2. VACUUM: Rebuilds the database file, reclaiming unused space
        // and reducing fragmentation.
        withContext(Dispatchers.IO) {
            writableDatabase.execSQL("ANALYZE")
        }
    }

    private fun innerInsertEvent(
        event: Event,
        db: SQLiteDatabase,
    ) {
        val headerId = eventIndexModule.insert(event, db)
        deletionModule.insert(event, db)
        expirationModule.insert(event, headerId, db)
        fullTextSearchModule.insert(event, headerId, db)
        rightToVanishModule.insert(event, relayUrl, headerId, db)
    }

    fun insertEvent(event: Event): Boolean {
        if (event.isExpired()) throw SQLiteConstraintException("blocked: Cannot insert an expired event")
        if (event.kind.isEphemeral()) return false

        writableDatabase.transaction {
            innerInsertEvent(event, this)
        }
        return true
    }

    inner class Transaction(
        val db: SQLiteDatabase,
    ) : IEventStore.ITransaction {
        override fun insert(event: Event): Boolean {
            if (event.isExpired()) throw SQLiteConstraintException("blocked: Cannot insert an expired event")
            if (event.kind.isEphemeral()) return false

            innerInsertEvent(event, db)
            return true
        }
    }

    fun transaction(body: Transaction.() -> Unit) {
        writableDatabase.transaction {
            with(Transaction(this)) {
                body()
            }
        }
    }

    fun <T : Event> query(filter: Filter): List<T> = eventIndexModule.query(filter, readableDatabase)

    fun <T : Event> query(filters: List<Filter>): List<T> = eventIndexModule.query(filters, readableDatabase)

    fun <T : Event> query(
        filter: Filter,
        onEach: (T) -> Unit,
    ) = eventIndexModule.query(filter, readableDatabase, onEach)

    fun <T : Event> query(
        filters: List<Filter>,
        onEach: (T) -> Unit,
    ) = eventIndexModule.query(filters, readableDatabase, onEach)

    fun count(filter: Filter): Int = eventIndexModule.count(filter, readableDatabase)

    fun count(filters: List<Filter>): Int = eventIndexModule.count(filters, readableDatabase)

    fun delete(filter: Filter) {
        eventIndexModule.delete(filter, writableDatabase)
    }

    fun delete(filters: List<Filter>) {
        eventIndexModule.delete(filters, writableDatabase)
    }

    fun delete(id: HexKey): Int = writableDatabase.delete("event_headers", "id = ?", arrayOf(id))

    fun deleteExpiredEvents() = expirationModule.deleteExpiredEvents(writableDatabase)
}
