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
import com.vitorpamplona.quartz.utils.RandomInstance

class SeedModule : IModule {
    override fun create(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE seeds (seed_value INTEGER)")

        val insertSeed = "INSERT INTO seeds (seed_value) VALUES (?)"

        val stmt = db.compileStatement(insertSeed)
        stmt.bindLong(1, RandomInstance.long())
        stmt.executeInsert()

        // Prevent updates to maintain immutability
        db.execSQL(
            """
            CREATE TRIGGER block_insert_seeds
            BEFORE INSERT ON seeds
            BEGIN
                SELECT RAISE(ABORT, 'Inserts are not allowed on this table');
            END;
            """.trimIndent(),
        )

        db.execSQL(
            """
            CREATE TRIGGER block_update_seeds
            BEFORE UPDATE ON seeds
            BEGIN
                SELECT RAISE(ABORT, 'Inserts are not allowed on this table');
            END;
            """.trimIndent(),
        )

        db.execSQL(
            """
            CREATE TRIGGER block_delete_seeds
            BEFORE DELETE ON seeds
            BEGIN
                SELECT RAISE(ABORT, 'Inserts are not allowed on this table');
            END;
            """.trimIndent(),
        )
    }

    fun getSeed(db: SQLiteDatabase): Long =
        db.rawQuery("SELECT seed_value FROM seeds LIMIT 1", null).use {
            it.moveToFirst()
            it.getLong(0)
        }

    override fun drop(db: SQLiteDatabase) {
        db.execSQL("DROP TABLE IF EXISTS seeds")
    }

    override fun deleteAll(db: SQLiteDatabase) {}

    private var hasherCache: TagNameValueHasher? = null

    fun hasher(db: SQLiteDatabase): TagNameValueHasher = hasherCache ?: TagNameValueHasher(getSeed(db)).also { hasherCache = it }
}
