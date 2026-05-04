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
import com.vitorpamplona.quartz.utils.RandomInstance
import kotlin.concurrent.atomics.AtomicReference
import kotlin.concurrent.atomics.ExperimentalAtomicApi

@OptIn(ExperimentalAtomicApi::class)
class SeedModule : IModule {
    override fun create(db: SQLiteConnection) {
        db.execSQL("CREATE TABLE seeds (seed_value INTEGER)")

        val insertSeed = "INSERT INTO seeds (seed_value) VALUES (?)"

        db.prepare(insertSeed).use { stmt ->
            stmt.bindLong(1, RandomInstance.long())
            stmt.step()
        }

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

    fun getSeed(db: SQLiteConnection): Long =
        db.prepare("SELECT seed_value FROM seeds LIMIT 1").use {
            it.step()
            it.getLong(0)
        }

    override fun drop(db: SQLiteConnection) {
        db.execSQL("DROP TABLE IF EXISTS seeds")
    }

    override fun deleteAll(db: SQLiteConnection) {}

    // The hasher is keyed off a stable per-DB seed, so two concurrent
    // computations would produce identical hashers — the race is benign.
    // We still publish via AtomicReference so the field write is visible
    // across threads (commonMain has no @Volatile equivalent).
    private val hasherCache = AtomicReference<TagNameValueHasher?>(null)

    fun hasher(db: SQLiteConnection): TagNameValueHasher {
        hasherCache.load()?.let { return it }
        val fresh = TagNameValueHasher(getSeed(db))
        hasherCache.compareAndSet(null, fresh)
        return hasherCache.load() ?: fresh
    }
}
