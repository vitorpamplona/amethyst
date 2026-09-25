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
package com.vitorpamplona.quartz.nipXXSql

import com.vitorpamplona.quartz.nip01Core.relay.commands.toClient.Message
import com.vitorpamplona.quartz.nip01Core.relay.commands.toRelay.Command

/**
 * Opens a cursor: `["SQL", <queryId>, <sql>, <options>?]`.
 *
 * `options` is an optional object:
 *  - `params`: a JSON array (bound to `?` / `?NNN`) or object (bound to `:name`);
 *    values are strings, numbers, booleans or null.
 *  - `page`: rows in the first `SQL-ROWS` frame (the relay clamps it).
 *
 * The relay answers `SQL-COLS`, then the first page as `SQL-ROWS`, or a
 * NIP-01 `CLOSED` with an `invalid:` / `unsupported:` / `blocked:` /
 * `error:` reason. A second SQL with the same id replaces the first.
 */
class SqlCmd(
    val queryId: String,
    val sql: String,
    val params: List<Any?> = emptyList(),
    val named: Map<String, Any?> = emptyMap(),
    val pageSize: Int? = null,
) : Command {
    override fun label() = LABEL

    override fun isValid() = queryId.isNotEmpty() && sql.isNotBlank() && (pageSize == null || pageSize > 0)

    companion object {
        const val LABEL = "SQL"
    }
}

/** Pulls the next page of an open cursor: `["FETCH", <queryId>, <maxRows>]`. */
class FetchCmd(
    val queryId: String,
    val maxRows: Int,
) : Command {
    override fun label() = LABEL

    override fun isValid() = queryId.isNotEmpty() && maxRows > 0

    companion object {
        const val LABEL = "FETCH"
    }
}

/** Releases a cursor before it is exhausted: `["SQL-CLOSE", <queryId>]`. Silent if unknown. */
class SqlCloseCmd(
    val queryId: String,
) : Command {
    override fun label() = LABEL

    override fun isValid() = queryId.isNotEmpty()

    companion object {
        const val LABEL = "SQL-CLOSE"
    }
}

/** Result column names, sent once per cursor before any rows: `["SQL-COLS", <queryId>, [<name>, …]]`. */
class SqlColsMessage(
    val queryId: String,
    val columns: List<String>,
) : Message {
    override fun label() = LABEL

    companion object {
        const val LABEL = "SQL-COLS"
    }
}

/**
 * A page of rows: `["SQL-ROWS", <queryId>, [[…], …], "more" | "done"]`.
 * Values are JSON strings, numbers or null. After `done` the relay has
 * released the cursor; after `more` the client sends `FETCH` or `SQL-CLOSE`.
 */
class SqlRowsMessage(
    val queryId: String,
    val rows: List<List<Any?>>,
    val done: Boolean,
) : Message {
    override fun label() = LABEL

    companion object {
        const val LABEL = "SQL-ROWS"
        const val MORE = "more"
        const val DONE = "done"
    }
}
