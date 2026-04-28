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
import androidx.sqlite.SQLiteStatement

inline fun <T> SQLiteStatement.use(block: (SQLiteStatement) -> T): T {
    try {
        return block(this)
    } finally {
        close()
    }
}

fun SQLiteConnection.execSQL(sql: String) {
    prepare(sql).use { it.step() }
}

fun SQLiteConnection.execSQL(
    sql: String,
    args: Array<out Any>,
) {
    prepare(sql).use { stmt ->
        args.forEachIndexed { index, arg ->
            stmt.bindAny(index + 1, arg)
        }
        stmt.step()
    }
}

fun SQLiteConnection.lastInsertRowId(): Long =
    prepare("SELECT last_insert_rowid()").use { stmt ->
        stmt.step()
        stmt.getLong(0)
    }

fun SQLiteConnection.deleteRows(
    table: String,
    whereClause: String,
    args: Array<out String>,
): Int {
    execSQL("DELETE FROM $table WHERE $whereClause", args)
    return changes()
}

fun SQLiteConnection.changes(): Int =
    prepare("SELECT changes()").use { stmt ->
        stmt.step()
        stmt.getInt(0)
    }

inline fun <T> SQLiteConnection.transaction(body: SQLiteConnection.() -> T): T {
    execSQL("BEGIN IMMEDIATE TRANSACTION")
    val result: T
    try {
        result = body()
    } catch (e: Throwable) {
        // Attempt rollback, but never let a rollback failure mask the
        // original cause — attach it as a suppressed exception instead.
        try {
            execSQL("ROLLBACK TRANSACTION")
        } catch (rollbackError: Throwable) {
            e.addSuppressed(rollbackError)
        }
        throw e
    }
    // Commit is intentionally outside the catch: if COMMIT fails SQLite
    // has already finalized the transaction state, so we must not also
    // try to ROLLBACK on top of it.
    execSQL("END TRANSACTION")
    return result
}

fun SQLiteStatement.bindAny(
    index: Int,
    value: Any,
) {
    when (value) {
        is String -> bindText(index, value)
        is Long -> bindLong(index, value)
        is Int -> bindInt(index, value)
        is Double -> bindDouble(index, value)
        is ByteArray -> bindBlob(index, value)
        else -> bindText(index, value.toString())
    }
}

inline fun <T> SQLiteConnection.rawQuery(
    sql: String,
    args: List<String>,
    block: (SQLiteStatement) -> T,
): T =
    prepare(sql).use { stmt ->
        args.forEachIndexed { index, arg ->
            stmt.bindText(index + 1, arg)
        }
        block(stmt)
    }
