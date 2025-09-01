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

import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import junit.framework.TestCase
import org.junit.Test

class EventDbQueryAssemblerTest {
    val builder = EventIndexesModule(FullTextSearchModule())

    val key1 = "7c5eb72a4584fdaaeaa145b25c92ea9917704224951219dbd43acef9e91fb88d"
    val key2 = "f3ac434d61bc0f491a814782ccfdf9c439dae1f0bde9097ad4a245f4c495cd14"
    val key3 = "12ae0fd81c85e1e7d9ed096397dc3129849425fe6f8afce7213ebf38ddfc6ca9"

    @Test
    fun testEmpty() {
        val sql = builder.planQuery(Filter())
        TestCase.assertEquals(
            "SELECT id, pubkey, created_at, kind, tags, content, sig FROM event_headers ORDER BY created_at DESC, id",
            sql,
        )
    }

    @Test
    fun testLimit() {
        val sql = builder.planQuery(Filter(limit = 10))
        TestCase.assertEquals(
            """
            SELECT id, pubkey, created_at, kind, tags, content, sig FROM event_headers
            INNER JOIN (SELECT event_headers.row_id as row_id FROM event_headers WHERE 1 = 1 ORDER BY created_at DESC, id ASC LIMIT 10) AS filtered
            ON event_headers.row_id = filtered.row_id
            ORDER BY created_at DESC, id
            """.trimIndent(),
            sql,
        )
    }

    @Test
    fun testAllFearures() {
        val sql =
            builder.planQuery(
                listOf(
                    Filter(limit = 10),
                    Filter(authors = listOf(key1), kinds = listOf(1, 1111), search = "keywords", limit = 100),
                    Filter(kinds = listOf(20), search = "cats", limit = 30),
                ),
            )
        TestCase.assertEquals(
            """
            SELECT id, pubkey, created_at, kind, tags, content, sig FROM event_headers
            INNER JOIN (SELECT event_headers.row_id as row_id FROM event_headers WHERE 1 = 1 ORDER BY created_at DESC, id ASC LIMIT 10 UNION SELECT event_headers.row_id as row_id FROM event_headers INNER JOIN event_fts ON event_fts.event_header_row_id = event_headers.row_id WHERE (event_headers.kind IN (?, ?)) AND (event_headers.pubkey = ?) AND (event_fts MATCH ?) ORDER BY created_at DESC, id ASC LIMIT 100 UNION SELECT event_headers.row_id as row_id FROM event_headers INNER JOIN event_fts ON event_fts.event_header_row_id = event_headers.row_id WHERE (event_headers.kind = ?) AND (event_fts MATCH ?) ORDER BY created_at DESC, id ASC LIMIT 30) AS filtered
            ON event_headers.row_id = filtered.row_id
            ORDER BY created_at DESC, id
            """.trimIndent(),
            sql,
        )
    }

    @Test
    fun testIdQuery() {
        val sql = builder.planQuery(Filter(ids = listOf(key1)))
        TestCase.assertEquals(
            """
            SELECT id, pubkey, created_at, kind, tags, content, sig FROM event_headers
            INNER JOIN (SELECT event_headers.row_id as row_id FROM event_headers WHERE event_headers.id = ?) AS filtered
            ON event_headers.row_id = filtered.row_id
            ORDER BY created_at DESC, id
            """.trimIndent(),
            sql,
        )
    }

    @Test
    fun testAuthors() {
        val sql = builder.planQuery(Filter(authors = listOf(key1, key2)))
        TestCase.assertEquals(
            """
            SELECT id, pubkey, created_at, kind, tags, content, sig FROM event_headers
            INNER JOIN (SELECT event_headers.row_id as row_id FROM event_headers WHERE event_headers.pubkey IN (?, ?)) AS filtered
            ON event_headers.row_id = filtered.row_id
            ORDER BY created_at DESC, id
            """.trimIndent(),
            sql,
        )
    }

    @Test
    fun testAuthorsAndSearch() {
        val sql = builder.planQuery(Filter(authors = listOf(key1, key2, key3), search = "keywords"))
        TestCase.assertEquals(
            """
            SELECT id, pubkey, created_at, kind, tags, content, sig FROM event_headers
            INNER JOIN (SELECT event_headers.row_id as row_id FROM event_headers INNER JOIN event_fts ON event_fts.event_header_row_id = event_headers.row_id WHERE (event_headers.pubkey IN (?, ?, ?)) AND (event_fts MATCH ?)) AS filtered
            ON event_headers.row_id = filtered.row_id
            ORDER BY created_at DESC, id
            """.trimIndent(),
            sql,
        )
    }

    @Test
    fun testKindAndSearch() {
        val sql = builder.planQuery(Filter(kinds = listOf(1, 1111, 10000), search = "keywords"))
        TestCase.assertEquals(
            """
            SELECT id, pubkey, created_at, kind, tags, content, sig FROM event_headers
            INNER JOIN (SELECT event_headers.row_id as row_id FROM event_headers INNER JOIN event_fts ON event_fts.event_header_row_id = event_headers.row_id WHERE (event_headers.kind IN (?, ?, ?)) AND (event_fts MATCH ?)) AS filtered
            ON event_headers.row_id = filtered.row_id
            ORDER BY created_at DESC, id
            """.trimIndent(),
            sql,
        )
    }
}
