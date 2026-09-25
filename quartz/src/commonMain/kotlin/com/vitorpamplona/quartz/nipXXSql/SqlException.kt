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

/**
 * A query the Nostr SQL profile refuses. [prefix] is the NIP-01
 * machine-readable prefix a relay puts in front of the CLOSED reason:
 * `invalid` for syntax / name / type errors, `unsupported` for valid SQL
 * the profile deliberately leaves out. [position] is the 0-based offset
 * in the query text, or -1 when the error is not tied to one spot.
 */
class SqlException(
    val prefix: String,
    val detail: String,
    val position: Int = -1,
) : Exception(if (position >= 0) "$prefix: $detail at offset $position" else "$prefix: $detail") {
    companion object {
        const val INVALID = "invalid"
        const val UNSUPPORTED = "unsupported"

        fun invalid(
            detail: String,
            position: Int = -1,
        ) = SqlException(INVALID, detail, position)

        fun unsupported(
            detail: String,
            position: Int = -1,
        ) = SqlException(UNSUPPORTED, detail, position)
    }
}
