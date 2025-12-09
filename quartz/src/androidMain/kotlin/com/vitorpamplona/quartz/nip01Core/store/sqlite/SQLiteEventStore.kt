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
import com.vitorpamplona.quartz.nip01Core.store.sqlite.EventIndexesModule.IndexingStrategy
import com.vitorpamplona.quartz.nip40Expiration.isExpired

class SQLiteEventStore(
    val context: Context,
    val dbName: String? = "events.db",
    val relayUrl: String? = null,
    val tagIndexStrategy: IndexingStrategy = IndexingStrategy(),
) : SQLiteOpenHelper(context, dbName, null, DATABASE_VERSION) {
    companion object {
        const val DATABASE_VERSION = 1
    }

    val fullTextSearchModule = FullTextSearchModule()
    val eventIndexModule = EventIndexesModule(fullTextSearchModule, tagIndexStrategy)

    val replaceableModule = ReplaceableModule()
    val addressableModule = AddressableModule()
    val ephemeralModule = EphemeralModule()

    val deletionModule = DeletionRequestModule()
    val expirationModule = ExpirationModule()
    val rightToVanishModule = RightToVanishModule()

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
        eventIndexModule.create(db)
        replaceableModule.create(db)
        addressableModule.create(db)
        ephemeralModule.create(db)
        deletionModule.create(db)
        expirationModule.create(db)
        rightToVanishModule.create(db)
        fullTextSearchModule.create(db)
    }

    override fun onUpgrade(
        db: SQLiteDatabase,
        oldVersion: Int,
        newVersion: Int,
    ) {}

    fun clearDB() {
        context.deleteDatabase(dbName)
    }

    private fun innerInsertEvent(
        event: Event,
        db: SQLiteDatabase,
    ) {
        val headerId = eventIndexModule.insert(event, db)
        deletionModule.insert(event, headerId, db)
        expirationModule.insert(event, headerId, db)
        fullTextSearchModule.insert(event, headerId, db)
        rightToVanishModule.insert(event, relayUrl, headerId, db)
    }

    fun insertEvent(event: Event): Boolean {
        if (event.isExpired()) throw SQLiteConstraintException("blocked: Cannot insert an expired event")
        if (event.kind.isEphemeral()) return false

        val db = writableDatabase
        db.transaction {
            innerInsertEvent(event, this)
        }
        return true
    }

    inner class Transaction(
        val db: SQLiteDatabase,
    ) : EventStore.ITransaction {
        override fun insert(event: Event): Boolean {
            if (event.isExpired()) throw SQLiteConstraintException("blocked: Cannot insert an expired event")
            if (event.kind.isEphemeral()) return false

            innerInsertEvent(event, db)
            return true
        }
    }

    fun transaction(body: Transaction.() -> Unit) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            with(Transaction(db)) {
                body()
            }
        } finally {
            db.endTransaction()
        }
    }

    fun query(filter: Filter): List<Event> = eventIndexModule.query(filter, readableDatabase)

    fun query(filters: List<Filter>): List<Event> = eventIndexModule.query(filters, readableDatabase)

    fun query(
        filter: Filter,
        onEach: (Event) -> Unit,
    ) = eventIndexModule.query(filter, readableDatabase, onEach)

    fun query(
        filters: List<Filter>,
        onEach: (Event) -> Unit,
    ) = eventIndexModule.query(filters, readableDatabase, onEach)

    fun count(filter: Filter): Int = eventIndexModule.count(filter, readableDatabase)

    fun count(filters: List<Filter>): Int = eventIndexModule.count(filters, readableDatabase)

    fun delete(filter: Filter): Int? = eventIndexModule.delete(filter, writableDatabase)

    fun delete(filters: List<Filter>): Int? = eventIndexModule.delete(filters, writableDatabase)

    fun delete(id: HexKey): Int = writableDatabase.delete("event_headers", "id = ?", arrayOf(id))

    fun deleteExpiredEvents() = expirationModule.deleteExpiredEvents(writableDatabase)
}
