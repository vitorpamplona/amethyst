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
import androidx.test.core.app.ApplicationProvider
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip02FollowList.ContactListEvent
import junit.framework.TestCase
import org.junit.After
import org.junit.Assert
import org.junit.Before
import org.junit.Test

class QueryAssemblerTest {
    val hasher = TagNameValueHasher(0)
    val builder = QueryBuilder(FullTextSearchModule(), { hasher })

    val key1 = "7c5eb72a4584fdaaeaa145b25c92ea9917704224951219dbd43acef9e91fb88d"
    val key2 = "f3ac434d61bc0f491a814782ccfdf9c439dae1f0bde9097ad4a245f4c495cd14"
    val key3 = "12ae0fd81c85e1e7d9ed096397dc3129849425fe6f8afce7213ebf38ddfc6ca9"

    private lateinit var db: EventStore

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = EventStore(context, null)
    }

    @After
    fun tearDown() {
        db.close()
    }

    fun explain(f: Filter) = builder.planQuery(f, hasher, db.store.readableDatabase)

    fun explain(f: List<Filter>) = builder.planQuery(f, hasher, db.store.readableDatabase)

    @Test
    fun testEmpty() {
        Assert.assertEquals(
            """
            SELECT id, pubkey, created_at, kind, tags, content, sig FROM event_headers ORDER BY created_at DESC, id
            └── SCAN event_headers USING INDEX query_by_created_at_id
            """.trimIndent(),
            explain(Filter()),
        )
    }

    @Test
    fun testCheckDeletionEventExists() {
        val query =
            explain(
                Filter(
                    kinds = listOf(5),
                    authors = listOf(key1),
                    tags = mapOf("e" to listOf(key2)),
                    since = 1750889190,
                ),
            )
        TestCase.assertEquals(
            """
            SELECT id, pubkey, created_at, kind, tags, content, sig FROM event_headers
            INNER JOIN (
                SELECT DISTINCT(event_tags.event_header_row_id) as row_id FROM event_tags INNER JOIN event_headers ON event_headers.row_id = event_tags.event_header_row_id  WHERE (event_tags.tag_hash = "2657743813502222172") AND (event_tags.created_at >= "1750889190") AND (event_headers.kind = "5") AND (event_headers.pubkey = "7c5eb72a4584fdaaeaa145b25c92ea9917704224951219dbd43acef9e91fb88d")
            ) AS filtered
            ON event_headers.row_id = filtered.row_id
            ORDER BY created_at DESC, id
            ├── CO-ROUTINE filtered
            │   ├── SEARCH event_tags USING INDEX query_by_tags_hash (tag_hash=? AND created_at>?)
            │   ├── SEARCH event_headers USING INTEGER PRIMARY KEY (rowid=?)
            │   └── USE TEMP B-TREE FOR DISTINCT
            ├── SCAN filtered
            ├── SEARCH event_headers USING INTEGER PRIMARY KEY (rowid=?)
            └── USE TEMP B-TREE FOR ORDER BY
            """.trimIndent(),
            query,
        )
    }

    @Test
    fun testLimit() {
        val explainer = explain(Filter(limit = 10))
        TestCase.assertEquals(
            """
            SELECT id, pubkey, created_at, kind, tags, content, sig FROM event_headers
            INNER JOIN (
                SELECT event_headers.row_id as row_id FROM event_headers  WHERE 1 = 1 ORDER BY event_headers.created_at DESC, event_headers.id ASC LIMIT 10
            ) AS filtered
            ON event_headers.row_id = filtered.row_id
            ORDER BY created_at DESC, id
            ├── CO-ROUTINE filtered
            │   └── SCAN event_headers USING COVERING INDEX query_by_created_at_id
            ├── SCAN filtered
            ├── SEARCH event_headers USING INTEGER PRIMARY KEY (rowid=?)
            └── USE TEMP B-TREE FOR ORDER BY
            """.trimIndent(),
            explainer,
        )
    }

    @Test
    fun testLimits() {
        val explainer = explain(listOf(Filter(limit = 10), Filter(limit = 30)))
        TestCase.assertEquals(
            """
            SELECT id, pubkey, created_at, kind, tags, content, sig FROM event_headers
            INNER JOIN (
                SELECT row_id FROM (SELECT event_headers.row_id as row_id FROM event_headers  WHERE 1 = 1 ORDER BY event_headers.created_at DESC, event_headers.id ASC LIMIT 10)
                UNION
                SELECT row_id FROM (SELECT event_headers.row_id as row_id FROM event_headers  WHERE 1 = 1 ORDER BY event_headers.created_at DESC, event_headers.id ASC LIMIT 30)
            ) AS filtered
            ON event_headers.row_id = filtered.row_id
            ORDER BY created_at DESC, id
            ├── CO-ROUTINE filtered
            │   └── COMPOUND QUERY
            │       ├── LEFT-MOST SUBQUERY
            │       │   ├── CO-ROUTINE (subquery-1)
            │       │   │   └── SCAN event_headers USING COVERING INDEX query_by_created_at_id
            │       │   └── SCAN (subquery-1)
            │       └── UNION USING TEMP B-TREE
            │           ├── CO-ROUTINE (subquery-3)
            │           │   └── SCAN event_headers USING COVERING INDEX query_by_created_at_id
            │           └── SCAN (subquery-3)
            ├── SCAN filtered
            ├── SEARCH event_headers USING INTEGER PRIMARY KEY (rowid=?)
            └── USE TEMP B-TREE FOR ORDER BY
            """.trimIndent(),
            explainer,
        )
    }

    @Test
    fun testAllFeatures() {
        val sql =
            explain(
                listOf(
                    Filter(limit = 10),
                    Filter(
                        authors = listOf(key1),
                        kinds = listOf(1, 1111),
                        search = "keywords",
                        limit = 100,
                    ),
                    Filter(kinds = listOf(20), search = "cats", limit = 30),
                ),
            )
        TestCase.assertEquals(
            """
            SELECT id, pubkey, created_at, kind, tags, content, sig FROM event_headers
            INNER JOIN (
                SELECT row_id FROM (SELECT event_headers.row_id as row_id FROM event_headers  WHERE 1 = 1 ORDER BY event_headers.created_at DESC, event_headers.id ASC LIMIT 10)
                UNION
                SELECT row_id FROM (SELECT event_fts.event_header_row_id as row_id FROM event_fts INNER JOIN event_headers ON event_headers.row_id = event_fts.event_header_row_id WHERE (event_headers.kind IN ("1", "1111")) AND (event_headers.pubkey = "7c5eb72a4584fdaaeaa145b25c92ea9917704224951219dbd43acef9e91fb88d") AND (event_fts MATCH "keywords") ORDER BY event_headers.created_at DESC, event_headers.id ASC LIMIT 100)
                UNION
                SELECT row_id FROM (SELECT event_fts.event_header_row_id as row_id FROM event_fts INNER JOIN event_headers ON event_headers.row_id = event_fts.event_header_row_id WHERE (event_headers.kind = "20") AND (event_fts MATCH "cats") ORDER BY event_headers.created_at DESC, event_headers.id ASC LIMIT 30)
            ) AS filtered
            ON event_headers.row_id = filtered.row_id
            ORDER BY created_at DESC, id
            ├── CO-ROUTINE filtered
            │   └── COMPOUND QUERY
            │       ├── LEFT-MOST SUBQUERY
            │       │   ├── CO-ROUTINE (subquery-1)
            │       │   │   └── SCAN event_headers USING COVERING INDEX query_by_created_at_id
            │       │   └── SCAN (subquery-1)
            │       ├── UNION USING TEMP B-TREE
            │       │   ├── CO-ROUTINE (subquery-3)
            │       │   │   ├── SCAN event_fts VIRTUAL TABLE INDEX 4:
            │       │   │   ├── SEARCH event_headers USING INTEGER PRIMARY KEY (rowid=?)
            │       │   │   └── USE TEMP B-TREE FOR ORDER BY
            │       │   └── SCAN (subquery-3)
            │       └── UNION USING TEMP B-TREE
            │           ├── CO-ROUTINE (subquery-5)
            │           │   ├── SCAN event_fts VIRTUAL TABLE INDEX 4:
            │           │   ├── SEARCH event_headers USING INTEGER PRIMARY KEY (rowid=?)
            │           │   └── USE TEMP B-TREE FOR ORDER BY
            │           └── SCAN (subquery-5)
            ├── SCAN filtered
            ├── SEARCH event_headers USING INTEGER PRIMARY KEY (rowid=?)
            └── USE TEMP B-TREE FOR ORDER BY
            """.trimIndent(),
            sql,
        )
    }

    @Test
    fun testKind() {
        val sql =
            explain(
                listOf(
                    Filter(
                        kinds = listOf(ContactListEvent.KIND),
                        limit = 30,
                    ),
                ),
            )
        TestCase.assertEquals(
            """
            SELECT id, pubkey, created_at, kind, tags, content, sig FROM event_headers
            INNER JOIN (
                SELECT event_headers.row_id as row_id FROM event_headers  WHERE event_headers.kind = "3" ORDER BY event_headers.created_at DESC, event_headers.id ASC LIMIT 30
            ) AS filtered
            ON event_headers.row_id = filtered.row_id
            ORDER BY created_at DESC, id
            ├── CO-ROUTINE filtered
            │   ├── SEARCH event_headers USING INDEX query_by_kind_pubkey_dtag_idx (kind=?)
            │   └── USE TEMP B-TREE FOR ORDER BY
            ├── SCAN filtered
            ├── SEARCH event_headers USING INTEGER PRIMARY KEY (rowid=?)
            └── USE TEMP B-TREE FOR ORDER BY
            """.trimIndent(),
            sql,
        )
    }

    @Test
    fun testKindAndDTag() {
        val sql =
            explain(
                listOf(
                    Filter(
                        kinds = listOf(ContactListEvent.KIND),
                        tags = mapOf("d" to listOf("")),
                        limit = 30,
                    ),
                ),
            )
        TestCase.assertEquals(
            """
            SELECT id, pubkey, created_at, kind, tags, content, sig FROM event_headers
            INNER JOIN (
                SELECT event_headers.row_id as row_id FROM event_headers  WHERE (event_headers.kind = "3") AND (event_headers.d_tag = "") ORDER BY event_headers.created_at DESC, event_headers.id ASC LIMIT 30
            ) AS filtered
            ON event_headers.row_id = filtered.row_id
            ORDER BY created_at DESC, id
            ├── CO-ROUTINE filtered
            │   ├── SEARCH event_headers USING INDEX query_by_kind_pubkey_dtag_idx (kind=?)
            │   └── USE TEMP B-TREE FOR ORDER BY
            ├── SCAN filtered
            ├── SEARCH event_headers USING INTEGER PRIMARY KEY (rowid=?)
            └── USE TEMP B-TREE FOR ORDER BY
            """.trimIndent(),
            sql,
        )
    }

    @Test
    fun testFollowersOf() {
        val sql =
            explain(
                listOf(
                    Filter(
                        kinds = listOf(ContactListEvent.KIND),
                        tags = mapOf("p" to listOf("460c25e682fda7832b52d1f22d3d22b3176d972f60dcdc3212ed8c92ef85065c")),
                        limit = 30,
                    ),
                ),
            )
        TestCase.assertEquals(
            """
            SELECT id, pubkey, created_at, kind, tags, content, sig FROM event_headers
            INNER JOIN (
                SELECT DISTINCT(event_tags.event_header_row_id) as row_id FROM event_tags INNER JOIN event_headers ON event_headers.row_id = event_tags.event_header_row_id  WHERE (event_tags.tag_hash = "-4551135004136952885") AND (event_headers.kind = "3") ORDER BY event_tags.created_at DESC LIMIT 30
            ) AS filtered
            ON event_headers.row_id = filtered.row_id
            ORDER BY created_at DESC, id
            ├── CO-ROUTINE filtered
            │   ├── SEARCH event_tags USING INDEX query_by_tags_hash (tag_hash=?)
            │   ├── SEARCH event_headers USING INTEGER PRIMARY KEY (rowid=?)
            │   └── USE TEMP B-TREE FOR DISTINCT
            ├── SCAN filtered
            ├── SEARCH event_headers USING INTEGER PRIMARY KEY (rowid=?)
            └── USE TEMP B-TREE FOR ORDER BY
            """.trimIndent(),
            sql,
        )
    }

    @Test
    fun testNotificationsOf() {
        val sql =
            explain(
                listOf(
                    Filter(
                        tags = mapOf("p" to listOf("460c25e682fda7832b52d1f22d3d22b3176d972f60dcdc3212ed8c92ef85065c")),
                        limit = 30,
                    ),
                ),
            )
        TestCase.assertEquals(
            """
            SELECT id, pubkey, created_at, kind, tags, content, sig FROM event_headers
            INNER JOIN (
                SELECT DISTINCT(event_tags.event_header_row_id) as row_id FROM event_tags  WHERE event_tags.tag_hash = "-4551135004136952885" ORDER BY event_tags.created_at DESC LIMIT 30
            ) AS filtered
            ON event_headers.row_id = filtered.row_id
            ORDER BY created_at DESC, id
            ├── CO-ROUTINE filtered
            │   ├── SEARCH event_tags USING INDEX query_by_tags_hash (tag_hash=?)
            │   └── USE TEMP B-TREE FOR DISTINCT
            ├── SCAN filtered
            ├── SEARCH event_headers USING INTEGER PRIMARY KEY (rowid=?)
            └── USE TEMP B-TREE FOR ORDER BY
            """.trimIndent(),
            sql,
        )
    }

    @Test
    fun testTagsAndKinds() {
        val sql =
            explain(
                listOf(
                    Filter(
                        kinds = listOf(ContactListEvent.KIND),
                        tags = mapOf("p" to listOf("460c25e682fda7832b52d1f22d3d22b3176d972f60dcdc3212ed8c92ef85065c")),
                        limit = 30,
                    ),
                ),
            )

        println(sql)

        TestCase.assertEquals(
            """
            SELECT id, pubkey, created_at, kind, tags, content, sig FROM event_headers
            INNER JOIN (
                SELECT DISTINCT(event_tags.event_header_row_id) as row_id FROM event_tags INNER JOIN event_headers ON event_headers.row_id = event_tags.event_header_row_id  WHERE (event_tags.tag_hash = "-4551135004136952885") AND (event_headers.kind = "3") ORDER BY event_tags.created_at DESC LIMIT 30
            ) AS filtered
            ON event_headers.row_id = filtered.row_id
            ORDER BY created_at DESC, id
            ├── CO-ROUTINE filtered
            │   ├── SEARCH event_tags USING INDEX query_by_tags_hash (tag_hash=?)
            │   ├── SEARCH event_headers USING INTEGER PRIMARY KEY (rowid=?)
            │   └── USE TEMP B-TREE FOR DISTINCT
            ├── SCAN filtered
            ├── SEARCH event_headers USING INTEGER PRIMARY KEY (rowid=?)
            └── USE TEMP B-TREE FOR ORDER BY
            """.trimIndent(),
            sql,
        )
    }

    @Test
    fun testTagsAndAuthors() {
        val sql =
            explain(
                listOf(
                    Filter(
                        authors = listOf(key1),
                        tags = mapOf("p" to listOf("460c25e682fda7832b52d1f22d3d22b3176d972f60dcdc3212ed8c92ef85065c")),
                        limit = 30,
                    ),
                ),
            )
        TestCase.assertEquals(
            """
            SELECT id, pubkey, created_at, kind, tags, content, sig FROM event_headers
            INNER JOIN (
                SELECT DISTINCT(event_tags.event_header_row_id) as row_id FROM event_tags INNER JOIN event_headers ON event_headers.row_id = event_tags.event_header_row_id  WHERE (event_tags.tag_hash = "-4551135004136952885") AND (event_headers.pubkey = "7c5eb72a4584fdaaeaa145b25c92ea9917704224951219dbd43acef9e91fb88d") ORDER BY event_tags.created_at DESC LIMIT 30
            ) AS filtered
            ON event_headers.row_id = filtered.row_id
            ORDER BY created_at DESC, id
            ├── CO-ROUTINE filtered
            │   ├── SEARCH event_tags USING INDEX query_by_tags_hash (tag_hash=?)
            │   ├── SEARCH event_headers USING INTEGER PRIMARY KEY (rowid=?)
            │   └── USE TEMP B-TREE FOR DISTINCT
            ├── SCAN filtered
            ├── SEARCH event_headers USING INTEGER PRIMARY KEY (rowid=?)
            └── USE TEMP B-TREE FOR ORDER BY
            """.trimIndent(),
            sql,
        )
    }

    @Test
    fun testTwoTags() {
        val sql =
            explain(
                listOf(
                    Filter(
                        kinds = listOf(1),
                        tags =
                            mapOf(
                                "p" to listOf("460c25e682fda7832b52d1f22d3d22b3176d972f60dcdc3212ed8c92ef85065c"),
                                "t" to listOf("hashtag"),
                            ),
                        limit = 30,
                    ),
                ),
            )
        TestCase.assertEquals(
            """
            SELECT id, pubkey, created_at, kind, tags, content, sig FROM event_headers
            INNER JOIN (
                SELECT DISTINCT(event_tags.event_header_row_id) as row_id FROM event_tags INNER JOIN event_tags as event_tagsIn1 ON event_tagsIn1.event_header_row_id = event_tags.event_header_row_id AND event_tagsIn1.created_at = event_tags.created_at INNER JOIN event_headers ON event_headers.row_id = event_tags.event_header_row_id  WHERE (event_tags.tag_hash = "-4551135004136952885") AND (event_tagsIn1.tag_hash = "-6379614208644810021") AND (event_headers.kind = "1") ORDER BY event_tags.created_at DESC LIMIT 30
            ) AS filtered
            ON event_headers.row_id = filtered.row_id
            ORDER BY created_at DESC, id
            ├── CO-ROUTINE filtered
            │   ├── SEARCH event_tags USING INDEX query_by_tags_hash (tag_hash=?)
            │   ├── SEARCH event_headers USING INTEGER PRIMARY KEY (rowid=?)
            │   ├── SEARCH event_tagsIn1 USING INDEX query_by_tags_hash (tag_hash=? AND event_header_row_id=?)
            │   └── USE TEMP B-TREE FOR DISTINCT
            ├── SCAN filtered
            ├── SEARCH event_headers USING INTEGER PRIMARY KEY (rowid=?)
            └── USE TEMP B-TREE FOR ORDER BY
            """.trimIndent(),
            sql,
        )
    }

    @Test
    fun testIdQuery() {
        val sql = explain(Filter(ids = listOf(key1)))
        TestCase.assertEquals(
            """
            SELECT id, pubkey, created_at, kind, tags, content, sig FROM event_headers
            INNER JOIN (
                SELECT event_headers.row_id as row_id FROM event_headers  WHERE event_headers.id = "7c5eb72a4584fdaaeaa145b25c92ea9917704224951219dbd43acef9e91fb88d"
            ) AS filtered
            ON event_headers.row_id = filtered.row_id
            ORDER BY created_at DESC, id
            ├── SEARCH event_headers USING COVERING INDEX event_headers_id (id=?)
            └── SEARCH event_headers USING INTEGER PRIMARY KEY (rowid=?)
            """.trimIndent(),
            sql,
        )
    }

    @Test
    fun testAuthors() {
        val sql = explain(Filter(authors = listOf(key1, key2), kinds = listOf(1, 30023), limit = 300))
        TestCase.assertEquals(
            """
            SELECT id, pubkey, created_at, kind, tags, content, sig FROM event_headers
            INNER JOIN (
                SELECT event_headers.row_id as row_id FROM event_headers  WHERE (event_headers.kind IN ("1", "30023")) AND (event_headers.pubkey IN ("7c5eb72a4584fdaaeaa145b25c92ea9917704224951219dbd43acef9e91fb88d", "f3ac434d61bc0f491a814782ccfdf9c439dae1f0bde9097ad4a245f4c495cd14")) ORDER BY event_headers.created_at DESC, event_headers.id ASC LIMIT 300
            ) AS filtered
            ON event_headers.row_id = filtered.row_id
            ORDER BY created_at DESC, id
            ├── CO-ROUTINE filtered
            │   ├── SEARCH event_headers USING INDEX query_by_kind_pubkey_dtag_idx (kind=? AND pubkey=?)
            │   └── USE TEMP B-TREE FOR ORDER BY
            ├── SCAN filtered
            ├── SEARCH event_headers USING INTEGER PRIMARY KEY (rowid=?)
            └── USE TEMP B-TREE FOR ORDER BY
            """.trimIndent(),
            sql,
        )
    }

    @Test
    fun testAuthorsAndSearch() {
        val sql = explain(Filter(authors = listOf(key1, key2, key3), search = "keywords"))
        TestCase.assertEquals(
            """
            SELECT id, pubkey, created_at, kind, tags, content, sig FROM event_headers
            INNER JOIN (
                SELECT event_fts.event_header_row_id as row_id FROM event_fts INNER JOIN event_headers ON event_headers.row_id = event_fts.event_header_row_id WHERE (event_headers.pubkey IN ("7c5eb72a4584fdaaeaa145b25c92ea9917704224951219dbd43acef9e91fb88d", "f3ac434d61bc0f491a814782ccfdf9c439dae1f0bde9097ad4a245f4c495cd14", "12ae0fd81c85e1e7d9ed096397dc3129849425fe6f8afce7213ebf38ddfc6ca9")) AND (event_fts MATCH "keywords")
            ) AS filtered
            ON event_headers.row_id = filtered.row_id
            ORDER BY created_at DESC, id
            ├── SCAN event_fts VIRTUAL TABLE INDEX 4:
            ├── SEARCH event_headers USING INTEGER PRIMARY KEY (rowid=?)
            ├── SEARCH event_headers USING INTEGER PRIMARY KEY (rowid=?)
            └── USE TEMP B-TREE FOR ORDER BY
            """.trimIndent(),
            sql,
        )
    }

    @Test
    fun testKindAndSearch() {
        val sql = explain(Filter(kinds = listOf(1, 1111, 10000), search = "keywords"))
        TestCase.assertEquals(
            """
            SELECT id, pubkey, created_at, kind, tags, content, sig FROM event_headers
            INNER JOIN (
                SELECT event_fts.event_header_row_id as row_id FROM event_fts INNER JOIN event_headers ON event_headers.row_id = event_fts.event_header_row_id WHERE (event_headers.kind IN ("1", "1111", "10000")) AND (event_fts MATCH "keywords")
            ) AS filtered
            ON event_headers.row_id = filtered.row_id
            ORDER BY created_at DESC, id
            ├── SCAN event_fts VIRTUAL TABLE INDEX 4:
            ├── SEARCH event_headers USING INTEGER PRIMARY KEY (rowid=?)
            ├── SEARCH event_headers USING INTEGER PRIMARY KEY (rowid=?)
            └── USE TEMP B-TREE FOR ORDER BY
            """.trimIndent(),
            sql,
        )
    }

    @Test
    fun testAllTag() {
        val sql = explain(Filter(tagsAll = mapOf("p" to listOf(key1, key2))))
        println(sql)
        TestCase.assertEquals(
            """
            SELECT id, pubkey, created_at, kind, tags, content, sig FROM event_headers
            INNER JOIN (
                SELECT DISTINCT(event_tags.event_header_row_id) as row_id FROM event_tags INNER JOIN event_tags as event_tagsAll0_1 ON event_tagsAll0_1.event_header_row_id = event_tags.event_header_row_id AND event_tagsAll0_1.created_at = event_tags.created_at  WHERE (event_tags.tag_hash = "884286737453847614") AND (event_tagsAll0_1.tag_hash = "-4988851810256311323")
            ) AS filtered
            ON event_headers.row_id = filtered.row_id
            ORDER BY created_at DESC, id
            ├── CO-ROUTINE filtered
            │   ├── SEARCH event_tags USING INDEX query_by_tags_hash (tag_hash=?)
            │   ├── SEARCH event_tagsAll0_1 USING INDEX query_by_tags_hash (tag_hash=? AND created_at=?)
            │   └── USE TEMP B-TREE FOR DISTINCT
            ├── SCAN filtered
            ├── SEARCH event_headers USING INTEGER PRIMARY KEY (rowid=?)
            └── USE TEMP B-TREE FOR ORDER BY
            """.trimIndent(),
            sql,
        )
    }

    @Test
    fun testReportLikeFilter() {
        val sql =
            explain(
                Filter(
                    kinds = listOf(ContactListEvent.KIND),
                    authors = listOf("460c25e682fda7832b52d1f22d3d22b3176d972f60dcdc3212ed8c92ef85065c"),
                    tags =
                        mapOf(
                            "p" to listOf("460c25e682fda7832b52d1f22d3d22b3176d972f60dcdc3212ed8c92ef85065c"),
                        ),
                    limit = 500,
                ),
            )

        println(sql)

        TestCase.assertEquals(
            """
            SELECT id, pubkey, created_at, kind, tags, content, sig FROM event_headers
            INNER JOIN (
                SELECT DISTINCT(event_tags.event_header_row_id) as row_id FROM event_tags INNER JOIN event_headers ON event_headers.row_id = event_tags.event_header_row_id  WHERE (event_tags.tag_hash = "-4551135004136952885") AND (event_headers.kind = "3") AND (event_headers.pubkey = "460c25e682fda7832b52d1f22d3d22b3176d972f60dcdc3212ed8c92ef85065c") ORDER BY event_tags.created_at DESC LIMIT 500
            ) AS filtered
            ON event_headers.row_id = filtered.row_id
            ORDER BY created_at DESC, id
            ├── CO-ROUTINE filtered
            │   ├── SEARCH event_tags USING INDEX query_by_tags_hash (tag_hash=?)
            │   ├── SEARCH event_headers USING INTEGER PRIMARY KEY (rowid=?)
            │   └── USE TEMP B-TREE FOR DISTINCT
            ├── SCAN filtered
            ├── SEARCH event_headers USING INTEGER PRIMARY KEY (rowid=?)
            └── USE TEMP B-TREE FOR ORDER BY
            """.trimIndent(),
            sql,
        )
    }
}
