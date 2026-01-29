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

import com.vitorpamplona.quartz.experimental.relationshipStatus.ContactCardEvent
import com.vitorpamplona.quartz.nip01Core.metadata.MetadataEvent
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip02FollowList.ContactListEvent
import com.vitorpamplona.quartz.nip10Notes.TextNoteEvent
import com.vitorpamplona.quartz.nip37Drafts.DraftWrapEvent
import com.vitorpamplona.quartz.nip50Search.SearchRelayListEvent
import com.vitorpamplona.quartz.nip65RelayList.AdvertisedRelayListEvent
import junit.framework.TestCase
import junit.framework.TestCase.assertEquals
import org.junit.Assert
import org.junit.Test

class QueryAssemblerTest : BaseDBTest() {
    val hasher = TagNameValueHasher(0)
    val key1 = "7c5eb72a4584fdaaeaa145b25c92ea9917704224951219dbd43acef9e91fb88d"
    val key2 = "f3ac434d61bc0f491a814782ccfdf9c439dae1f0bde9097ad4a245f4c495cd14"
    val key3 = "12ae0fd81c85e1e7d9ed096397dc3129849425fe6f8afce7213ebf38ddfc6ca9"

    fun EventStore.explain(f: Filter) = store.queryBuilder.planQuery(f, hasher, store.readableDatabase)

    fun EventStore.explain(f: List<Filter>) = store.queryBuilder.planQuery(f, hasher, store.readableDatabase)

    @Test
    fun testEmpty() =
        forEachDB { db ->
            val orderBy = if (db.indexStrategy.useAndIndexIdOnOrderBy) "created_at DESC, id ASC" else "created_at DESC"
            if (db.indexStrategy.indexEventsByCreatedAtAlone) {
                Assert.assertEquals(
                    """
                    SELECT id, pubkey, created_at, kind, tags, content, sig FROM event_headers
                    ORDER BY $orderBy
                    └── SCAN event_headers USING INDEX query_by_created_at_id
                    """.trimIndent(),
                    db.explain(Filter()),
                )
            } else {
                Assert.assertEquals(
                    """
                    SELECT id, pubkey, created_at, kind, tags, content, sig FROM event_headers
                    ORDER BY $orderBy
                    ├── SCAN event_headers
                    └── USE TEMP B-TREE FOR ORDER BY
                    """.trimIndent(),
                    db.explain(Filter()),
                )
            }
        }

    @Test
    fun testCheckDeletionEventExists() =
        forEachDB { db ->
            val filter =
                Filter(
                    kinds = listOf(5),
                    authors = listOf(key1),
                    tags = mapOf("e" to listOf(key2)),
                    since = 1750889190,
                )
            val orderBy = if (db.indexStrategy.useAndIndexIdOnOrderBy) "created_at DESC, id ASC" else "created_at DESC"
            if (db.indexStrategy.indexTagsWithKindAndPubkey) {
                TestCase.assertEquals(
                    """
                    SELECT id, pubkey, created_at, kind, tags, content, sig FROM event_headers
                    INNER JOIN (
                        SELECT DISTINCT(event_tags.event_header_row_id) as row_id FROM event_tags WHERE (event_tags.tag_hash = "2657743813502222172") AND (event_tags.kind = "5") AND (event_tags.pubkey_hash = "1730514094536529999") AND (event_tags.created_at >= "1750889190")
                    ) AS filtered
                    ON event_headers.row_id = filtered.row_id
                    ORDER BY $orderBy
                    ├── CO-ROUTINE filtered
                    │   ├── SEARCH event_tags USING INDEX query_by_tags_hash_kind_pubkey (tag_hash=? AND kind=? AND pubkey_hash=? AND created_at>?)
                    │   └── USE TEMP B-TREE FOR DISTINCT
                    ├── SCAN filtered
                    ├── SEARCH event_headers USING INTEGER PRIMARY KEY (rowid=?)
                    └── USE TEMP B-TREE FOR ORDER BY
                    """.trimIndent(),
                    db.explain(filter),
                )
            } else {
                TestCase.assertEquals(
                    """
                    SELECT id, pubkey, created_at, kind, tags, content, sig FROM event_headers
                    INNER JOIN (
                        SELECT DISTINCT(event_tags.event_header_row_id) as row_id FROM event_tags WHERE (event_tags.tag_hash = "2657743813502222172") AND (event_tags.kind = "5") AND (event_tags.pubkey_hash = "1730514094536529999") AND (event_tags.created_at >= "1750889190")
                    ) AS filtered
                    ON event_headers.row_id = filtered.row_id
                    ORDER BY $orderBy
                    ├── CO-ROUTINE filtered
                    │   ├── SEARCH event_tags USING INDEX query_by_tags_hash_kind (tag_hash=? AND kind=? AND created_at>?)
                    │   └── USE TEMP B-TREE FOR DISTINCT
                    ├── SCAN filtered
                    ├── SEARCH event_headers USING INTEGER PRIMARY KEY (rowid=?)
                    └── USE TEMP B-TREE FOR ORDER BY
                    """.trimIndent(),
                    db.explain(filter),
                )
            }
        }

    @Test
    fun testLimit() =
        forEachDB { db ->
            val filter = Filter(limit = 10)
            val orderBy = if (db.indexStrategy.useAndIndexIdOnOrderBy) "created_at DESC, id ASC" else "created_at DESC"

            if (db.indexStrategy.indexEventsByCreatedAtAlone) {
                TestCase.assertEquals(
                    """
                    SELECT id, pubkey, created_at, kind, tags, content, sig FROM event_headers
                    ORDER BY $orderBy
                    LIMIT 10
                    └── SCAN event_headers USING INDEX query_by_created_at_id
                    """.trimIndent(),
                    db.explain(filter),
                )
            } else {
                TestCase.assertEquals(
                    """
                    SELECT id, pubkey, created_at, kind, tags, content, sig FROM event_headers
                    ORDER BY $orderBy
                    LIMIT 10
                    ├── SCAN event_headers
                    └── USE TEMP B-TREE FOR ORDER BY
                    """.trimIndent(),
                    db.explain(filter),
                )
            }
        }

    @Test
    fun testLimits() =
        forEachDB { db ->
            val filter = listOf(Filter(limit = 10), Filter(limit = 30))
            val orderBy =
                if (db.indexStrategy.useAndIndexIdOnOrderBy) {
                    "created_at DESC, id ASC"
                } else {
                    "created_at DESC"
                }
            if (db.indexStrategy.indexEventsByCreatedAtAlone) {
                TestCase.assertEquals(
                    """
                    SELECT id, pubkey, created_at, kind, tags, content, sig FROM event_headers
                    INNER JOIN (
                        SELECT row_id FROM (SELECT event_headers.row_id as row_id FROM event_headers ORDER BY event_headers.created_at DESC LIMIT 10)
                        UNION
                        SELECT row_id FROM (SELECT event_headers.row_id as row_id FROM event_headers ORDER BY event_headers.created_at DESC LIMIT 30)
                    ) AS filtered
                    ON event_headers.row_id = filtered.row_id
                    ORDER BY $orderBy
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
                    db.explain(filter),
                )
            } else {
                TestCase.assertEquals(
                    """
                    SELECT id, pubkey, created_at, kind, tags, content, sig FROM event_headers
                    INNER JOIN (
                        SELECT row_id FROM (SELECT event_headers.row_id as row_id FROM event_headers ORDER BY event_headers.created_at DESC LIMIT 10)
                        UNION
                        SELECT row_id FROM (SELECT event_headers.row_id as row_id FROM event_headers ORDER BY event_headers.created_at DESC LIMIT 30)
                    ) AS filtered
                    ON event_headers.row_id = filtered.row_id
                    ORDER BY $orderBy
                    ├── CO-ROUTINE filtered
                    │   └── COMPOUND QUERY
                    │       ├── LEFT-MOST SUBQUERY
                    │       │   ├── CO-ROUTINE (subquery-1)
                    │       │   │   ├── SCAN event_headers USING COVERING INDEX query_by_kind_created
                    │       │   │   └── USE TEMP B-TREE FOR ORDER BY
                    │       │   └── SCAN (subquery-1)
                    │       └── UNION USING TEMP B-TREE
                    │           ├── CO-ROUTINE (subquery-3)
                    │           │   ├── SCAN event_headers USING COVERING INDEX query_by_kind_created
                    │           │   └── USE TEMP B-TREE FOR ORDER BY
                    │           └── SCAN (subquery-3)
                    ├── SCAN filtered
                    ├── SEARCH event_headers USING INTEGER PRIMARY KEY (rowid=?)
                    └── USE TEMP B-TREE FOR ORDER BY
                    """.trimIndent(),
                    db.explain(filter),
                )
            }
        }

    @Test
    fun testAllFeatures() =
        forEachDB { db ->
            val filter =
                listOf(
                    Filter(limit = 10),
                    Filter(
                        authors = listOf(key1),
                        kinds = listOf(1, 1111),
                        search = "keywords",
                        limit = 100,
                    ),
                    Filter(kinds = listOf(20), search = "cats", limit = 30),
                )
            val orderBy = if (db.indexStrategy.useAndIndexIdOnOrderBy) "created_at DESC, id ASC" else "created_at DESC"
            if (db.indexStrategy.indexEventsByCreatedAtAlone) {
                TestCase.assertEquals(
                    """
                    SELECT id, pubkey, created_at, kind, tags, content, sig FROM event_headers
                    INNER JOIN (
                        SELECT row_id FROM (SELECT event_headers.row_id as row_id FROM event_headers ORDER BY event_headers.created_at DESC LIMIT 10)
                        UNION
                        SELECT row_id FROM (SELECT event_fts.event_header_row_id as row_id FROM event_fts INNER JOIN event_headers ON event_headers.row_id = event_fts.event_header_row_id WHERE (event_headers.kind IN ("1", "1111")) AND (event_headers.pubkey = "7c5eb72a4584fdaaeaa145b25c92ea9917704224951219dbd43acef9e91fb88d") AND (event_fts MATCH "keywords") ORDER BY event_headers.created_at DESC LIMIT 100)
                        UNION
                        SELECT row_id FROM (SELECT event_fts.event_header_row_id as row_id FROM event_fts INNER JOIN event_headers ON event_headers.row_id = event_fts.event_header_row_id WHERE (event_headers.kind = "20") AND (event_fts MATCH "cats") ORDER BY event_headers.created_at DESC LIMIT 30)
                    ) AS filtered
                    ON event_headers.row_id = filtered.row_id
                    ORDER BY $orderBy
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
                    │           │   ├── SEARCH event_headers USING COVERING INDEX query_by_kind_created (kind=?)
                    │           │   └── SCAN event_fts VIRTUAL TABLE INDEX 4:
                    │           └── SCAN (subquery-5)
                    ├── SCAN filtered
                    ├── SEARCH event_headers USING INTEGER PRIMARY KEY (rowid=?)
                    └── USE TEMP B-TREE FOR ORDER BY
                    """.trimIndent(),
                    db.explain(filter),
                )
            } else {
                TestCase.assertEquals(
                    """
                    SELECT id, pubkey, created_at, kind, tags, content, sig FROM event_headers
                    INNER JOIN (
                        SELECT row_id FROM (SELECT event_headers.row_id as row_id FROM event_headers ORDER BY event_headers.created_at DESC LIMIT 10)
                        UNION
                        SELECT row_id FROM (SELECT event_fts.event_header_row_id as row_id FROM event_fts INNER JOIN event_headers ON event_headers.row_id = event_fts.event_header_row_id WHERE (event_headers.kind IN ("1", "1111")) AND (event_headers.pubkey = "7c5eb72a4584fdaaeaa145b25c92ea9917704224951219dbd43acef9e91fb88d") AND (event_fts MATCH "keywords") ORDER BY event_headers.created_at DESC LIMIT 100)
                        UNION
                        SELECT row_id FROM (SELECT event_fts.event_header_row_id as row_id FROM event_fts INNER JOIN event_headers ON event_headers.row_id = event_fts.event_header_row_id WHERE (event_headers.kind = "20") AND (event_fts MATCH "cats") ORDER BY event_headers.created_at DESC LIMIT 30)
                    ) AS filtered
                    ON event_headers.row_id = filtered.row_id
                    ORDER BY $orderBy
                    ├── CO-ROUTINE filtered
                    │   └── COMPOUND QUERY
                    │       ├── LEFT-MOST SUBQUERY
                    │       │   ├── CO-ROUTINE (subquery-1)
                    │       │   │   ├── SCAN event_headers USING COVERING INDEX query_by_kind_created
                    │       │   │   └── USE TEMP B-TREE FOR ORDER BY
                    │       │   └── SCAN (subquery-1)
                    │       ├── UNION USING TEMP B-TREE
                    │       │   ├── CO-ROUTINE (subquery-3)
                    │       │   │   ├── SCAN event_fts VIRTUAL TABLE INDEX 4:
                    │       │   │   ├── SEARCH event_headers USING INTEGER PRIMARY KEY (rowid=?)
                    │       │   │   └── USE TEMP B-TREE FOR ORDER BY
                    │       │   └── SCAN (subquery-3)
                    │       └── UNION USING TEMP B-TREE
                    │           ├── CO-ROUTINE (subquery-5)
                    │           │   ├── SEARCH event_headers USING COVERING INDEX query_by_kind_created (kind=?)
                    │           │   └── SCAN event_fts VIRTUAL TABLE INDEX 4:
                    │           └── SCAN (subquery-5)
                    ├── SCAN filtered
                    ├── SEARCH event_headers USING INTEGER PRIMARY KEY (rowid=?)
                    └── USE TEMP B-TREE FOR ORDER BY
                    """.trimIndent(),
                    db.explain(filter),
                )
            }
        }

    @Test
    fun testSingleFilterConversionToSimpleQuery() =
        forEachDB { db ->
            val filter =
                listOf(
                    Filter(
                        kinds = listOf(ContactListEvent.KIND),
                        limit = 30,
                    ),
                )
            if (db.indexStrategy.useAndIndexIdOnOrderBy) {
                TestCase.assertEquals(
                    """
                    SELECT id, pubkey, created_at, kind, tags, content, sig FROM event_headers
                    WHERE kind = "3"
                    ORDER BY created_at DESC, id ASC
                    LIMIT 30
                    └── SEARCH event_headers USING INDEX query_by_kind_created (kind=?)
                    """.trimIndent(),
                    db.explain(filter),
                )
            } else {
                TestCase.assertEquals(
                    """
                    SELECT id, pubkey, created_at, kind, tags, content, sig FROM event_headers
                    WHERE kind = "3"
                    ORDER BY created_at DESC
                    LIMIT 30
                    └── SEARCH event_headers USING INDEX query_by_kind_created (kind=?)
                    """.trimIndent(),
                    db.explain(filter),
                )
            }
        }

    @Test
    fun testKinds() =
        forEachDB { db ->
            val filter =
                listOf(
                    Filter(
                        kinds = listOf(ContactListEvent.KIND),
                        limit = 30,
                    ),
                    Filter(
                        kinds = listOf(TextNoteEvent.KIND),
                        limit = 30,
                    ),
                )
            val orderBy = if (db.indexStrategy.useAndIndexIdOnOrderBy) "created_at DESC, id ASC" else "created_at DESC"

            TestCase.assertEquals(
                """
                SELECT id, pubkey, created_at, kind, tags, content, sig FROM event_headers
                INNER JOIN (
                    SELECT row_id FROM (SELECT event_headers.row_id as row_id FROM event_headers WHERE event_headers.kind = "3" ORDER BY event_headers.created_at DESC LIMIT 30)
                    UNION
                    SELECT row_id FROM (SELECT event_headers.row_id as row_id FROM event_headers WHERE event_headers.kind = "1" ORDER BY event_headers.created_at DESC LIMIT 30)
                ) AS filtered
                ON event_headers.row_id = filtered.row_id
                ORDER BY $orderBy
                ├── CO-ROUTINE filtered
                │   └── COMPOUND QUERY
                │       ├── LEFT-MOST SUBQUERY
                │       │   ├── CO-ROUTINE (subquery-1)
                │       │   │   └── SEARCH event_headers USING COVERING INDEX query_by_kind_created (kind=?)
                │       │   └── SCAN (subquery-1)
                │       └── UNION USING TEMP B-TREE
                │           ├── CO-ROUTINE (subquery-3)
                │           │   └── SEARCH event_headers USING COVERING INDEX query_by_kind_created (kind=?)
                │           └── SCAN (subquery-3)
                ├── SCAN filtered
                ├── SEARCH event_headers USING INTEGER PRIMARY KEY (rowid=?)
                └── USE TEMP B-TREE FOR ORDER BY
                """.trimIndent(),
                db.explain(filter),
            )
        }

    @Test
    fun testKindAndDTag() =
        forEachDB { db ->
            val filter =
                listOf(
                    Filter(
                        kinds = listOf(ContactListEvent.KIND),
                        tags = mapOf("d" to listOf("")),
                        limit = 30,
                    ),
                )
            if (db.indexStrategy.useAndIndexIdOnOrderBy) {
                TestCase.assertEquals(
                    """
                    SELECT id, pubkey, created_at, kind, tags, content, sig FROM event_headers
                    WHERE (kind = "3") AND (d_tag = "")
                    ORDER BY created_at DESC, id ASC
                    LIMIT 30
                    └── SEARCH event_headers USING INDEX query_by_kind_created (kind=?)
                    """.trimIndent(),
                    db.explain(filter),
                )
            } else {
                TestCase.assertEquals(
                    """
                    SELECT id, pubkey, created_at, kind, tags, content, sig FROM event_headers
                    WHERE (kind = "3") AND (d_tag = "")
                    ORDER BY created_at DESC
                    LIMIT 30
                    └── SEARCH event_headers USING INDEX query_by_kind_created (kind=?)
                    """.trimIndent(),
                    db.explain(filter),
                )
            }
        }

    @Test
    fun testFollowersOf() =
        forEachDB { db ->
            val filter =
                listOf(
                    Filter(
                        kinds = listOf(ContactListEvent.KIND),
                        tags = mapOf("p" to listOf("460c25e682fda7832b52d1f22d3d22b3176d972f60dcdc3212ed8c92ef85065c")),
                        limit = 30,
                    ),
                )
            val orderBy = if (db.indexStrategy.useAndIndexIdOnOrderBy) "created_at DESC, id ASC" else "created_at DESC"
            TestCase.assertEquals(
                """
                SELECT id, pubkey, created_at, kind, tags, content, sig FROM event_headers
                INNER JOIN (
                    SELECT DISTINCT(event_tags.event_header_row_id) as row_id FROM event_tags WHERE (event_tags.tag_hash = "-4551135004136952885") AND (event_tags.kind = "3") ORDER BY event_tags.created_at DESC LIMIT 30
                ) AS filtered
                ON event_headers.row_id = filtered.row_id
                ORDER BY $orderBy
                ├── CO-ROUTINE filtered
                │   ├── SEARCH event_tags USING INDEX query_by_tags_hash_kind (tag_hash=? AND kind=?)
                │   └── USE TEMP B-TREE FOR DISTINCT
                ├── SCAN filtered
                ├── SEARCH event_headers USING INTEGER PRIMARY KEY (rowid=?)
                └── USE TEMP B-TREE FOR ORDER BY
                """.trimIndent(),
                db.explain(filter),
            )
        }

    @Test
    fun testNotificationsOf() =
        forEachDB { db ->
            val filter =
                listOf(
                    Filter(
                        tags = mapOf("p" to listOf("460c25e682fda7832b52d1f22d3d22b3176d972f60dcdc3212ed8c92ef85065c")),
                        limit = 30,
                    ),
                )
            val orderBy = if (db.indexStrategy.useAndIndexIdOnOrderBy) "created_at DESC, id ASC" else "created_at DESC"
            if (db.indexStrategy.indexTagsByCreatedAtAlone) {
                TestCase.assertEquals(
                    """
                    SELECT id, pubkey, created_at, kind, tags, content, sig FROM event_headers
                    INNER JOIN (
                        SELECT DISTINCT(event_tags.event_header_row_id) as row_id FROM event_tags WHERE event_tags.tag_hash = "-4551135004136952885" ORDER BY event_tags.created_at DESC LIMIT 30
                    ) AS filtered
                    ON event_headers.row_id = filtered.row_id
                    ORDER BY $orderBy
                    ├── CO-ROUTINE filtered
                    │   ├── SEARCH event_tags USING INDEX query_by_tags_hash (tag_hash=?)
                    │   └── USE TEMP B-TREE FOR DISTINCT
                    ├── SCAN filtered
                    ├── SEARCH event_headers USING INTEGER PRIMARY KEY (rowid=?)
                    └── USE TEMP B-TREE FOR ORDER BY
                    """.trimIndent(),
                    db.explain(filter),
                )
            } else {
                TestCase.assertEquals(
                    """
                    SELECT id, pubkey, created_at, kind, tags, content, sig FROM event_headers
                    INNER JOIN (
                        SELECT DISTINCT(event_tags.event_header_row_id) as row_id FROM event_tags WHERE event_tags.tag_hash = "-4551135004136952885" ORDER BY event_tags.created_at DESC LIMIT 30
                    ) AS filtered
                    ON event_headers.row_id = filtered.row_id
                    ORDER BY $orderBy
                    ├── CO-ROUTINE filtered
                    │   ├── SEARCH event_tags USING INDEX query_by_tags_hash_kind (tag_hash=?)
                    │   ├── USE TEMP B-TREE FOR DISTINCT
                    │   └── USE TEMP B-TREE FOR ORDER BY
                    ├── SCAN filtered
                    ├── SEARCH event_headers USING INTEGER PRIMARY KEY (rowid=?)
                    └── USE TEMP B-TREE FOR ORDER BY
                    """.trimIndent(),
                    db.explain(filter),
                )
            }
        }

    @Test
    fun testTagsAndKinds() =
        forEachDB { db ->
            val filter =
                listOf(
                    Filter(
                        kinds = listOf(ContactListEvent.KIND),
                        tags = mapOf("p" to listOf("460c25e682fda7832b52d1f22d3d22b3176d972f60dcdc3212ed8c92ef85065c")),
                        limit = 30,
                    ),
                )
            val orderBy = if (db.indexStrategy.useAndIndexIdOnOrderBy) "created_at DESC, id ASC" else "created_at DESC"
            TestCase.assertEquals(
                """
                SELECT id, pubkey, created_at, kind, tags, content, sig FROM event_headers
                INNER JOIN (
                    SELECT DISTINCT(event_tags.event_header_row_id) as row_id FROM event_tags WHERE (event_tags.tag_hash = "-4551135004136952885") AND (event_tags.kind = "3") ORDER BY event_tags.created_at DESC LIMIT 30
                ) AS filtered
                ON event_headers.row_id = filtered.row_id
                ORDER BY $orderBy
                ├── CO-ROUTINE filtered
                │   ├── SEARCH event_tags USING INDEX query_by_tags_hash_kind (tag_hash=? AND kind=?)
                │   └── USE TEMP B-TREE FOR DISTINCT
                ├── SCAN filtered
                ├── SEARCH event_headers USING INTEGER PRIMARY KEY (rowid=?)
                └── USE TEMP B-TREE FOR ORDER BY
                """.trimIndent(),
                db.explain(filter),
            )
        }

    @Test
    fun testTagsAndAuthors() =
        forEachDB { db ->
            val filter =
                listOf(
                    Filter(
                        authors = listOf(key1),
                        tags = mapOf("p" to listOf("460c25e682fda7832b52d1f22d3d22b3176d972f60dcdc3212ed8c92ef85065c")),
                        limit = 30,
                    ),
                )
            val orderBy = if (db.indexStrategy.useAndIndexIdOnOrderBy) "created_at DESC, id ASC" else "created_at DESC"
            if (db.indexStrategy.indexTagsByCreatedAtAlone) {
                TestCase.assertEquals(
                    """
                    SELECT id, pubkey, created_at, kind, tags, content, sig FROM event_headers
                    INNER JOIN (
                        SELECT DISTINCT(event_tags.event_header_row_id) as row_id FROM event_tags WHERE (event_tags.tag_hash = "-4551135004136952885") AND (event_tags.pubkey_hash = "1730514094536529999") ORDER BY event_tags.created_at DESC LIMIT 30
                    ) AS filtered
                    ON event_headers.row_id = filtered.row_id
                    ORDER BY $orderBy
                    ├── CO-ROUTINE filtered
                    │   ├── SEARCH event_tags USING INDEX query_by_tags_hash (tag_hash=?)
                    │   └── USE TEMP B-TREE FOR DISTINCT
                    ├── SCAN filtered
                    ├── SEARCH event_headers USING INTEGER PRIMARY KEY (rowid=?)
                    └── USE TEMP B-TREE FOR ORDER BY
                    """.trimIndent(),
                    db.explain(filter),
                )
            } else {
                TestCase.assertEquals(
                    """
                    SELECT id, pubkey, created_at, kind, tags, content, sig FROM event_headers
                    INNER JOIN (
                        SELECT DISTINCT(event_tags.event_header_row_id) as row_id FROM event_tags WHERE (event_tags.tag_hash = "-4551135004136952885") AND (event_tags.pubkey_hash = "1730514094536529999") ORDER BY event_tags.created_at DESC LIMIT 30
                    ) AS filtered
                    ON event_headers.row_id = filtered.row_id
                    ORDER BY $orderBy
                    ├── CO-ROUTINE filtered
                    │   ├── SEARCH event_tags USING INDEX query_by_tags_hash_kind (tag_hash=?)
                    │   ├── USE TEMP B-TREE FOR DISTINCT
                    │   └── USE TEMP B-TREE FOR ORDER BY
                    ├── SCAN filtered
                    ├── SEARCH event_headers USING INTEGER PRIMARY KEY (rowid=?)
                    └── USE TEMP B-TREE FOR ORDER BY
                    """.trimIndent(),
                    db.explain(filter),
                )
            }
        }

    @Test
    fun testTwoTags() =
        forEachDB { db ->
            val filter =
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
                )
            val orderBy = if (db.indexStrategy.useAndIndexIdOnOrderBy) "created_at DESC, id ASC" else "created_at DESC"
            if (db.indexStrategy.indexTagsByCreatedAtAlone) {
                TestCase.assertEquals(
                    """
                    SELECT id, pubkey, created_at, kind, tags, content, sig FROM event_headers
                    INNER JOIN (
                        SELECT DISTINCT(event_tags.event_header_row_id) as row_id FROM event_tags INNER JOIN event_tags as event_tagsIn1 ON event_tagsIn1.event_header_row_id = event_tags.event_header_row_id AND event_tagsIn1.created_at = event_tags.created_at WHERE (event_tags.tag_hash = "-4551135004136952885") AND (event_tagsIn1.tag_hash = "-6379614208644810021") AND (event_tags.kind = "1") ORDER BY event_tags.created_at DESC LIMIT 30
                    ) AS filtered
                    ON event_headers.row_id = filtered.row_id
                    ORDER BY $orderBy
                    ├── CO-ROUTINE filtered
                    │   ├── SEARCH event_tags USING INDEX query_by_tags_hash_kind (tag_hash=? AND kind=?)
                    │   ├── SEARCH event_tagsIn1 USING INDEX query_by_tags_hash (tag_hash=? AND created_at=?)
                    │   └── USE TEMP B-TREE FOR DISTINCT
                    ├── SCAN filtered
                    ├── SEARCH event_headers USING INTEGER PRIMARY KEY (rowid=?)
                    └── USE TEMP B-TREE FOR ORDER BY
                    """.trimIndent(),
                    db.explain(filter),
                )
            } else {
                TestCase.assertEquals(
                    """
                    SELECT id, pubkey, created_at, kind, tags, content, sig FROM event_headers
                    INNER JOIN (
                        SELECT DISTINCT(event_tags.event_header_row_id) as row_id FROM event_tags INNER JOIN event_tags as event_tagsIn1 ON event_tagsIn1.event_header_row_id = event_tags.event_header_row_id AND event_tagsIn1.created_at = event_tags.created_at WHERE (event_tags.tag_hash = "-4551135004136952885") AND (event_tagsIn1.tag_hash = "-6379614208644810021") AND (event_tags.kind = "1") ORDER BY event_tags.created_at DESC LIMIT 30
                    ) AS filtered
                    ON event_headers.row_id = filtered.row_id
                    ORDER BY $orderBy
                    ├── CO-ROUTINE filtered
                    │   ├── SEARCH event_tags USING INDEX query_by_tags_hash_kind (tag_hash=? AND kind=?)
                    │   ├── SEARCH event_tagsIn1 USING INDEX fk_event_tags_header_id (event_header_row_id=?)
                    │   └── USE TEMP B-TREE FOR DISTINCT
                    ├── SCAN filtered
                    ├── SEARCH event_headers USING INTEGER PRIMARY KEY (rowid=?)
                    └── USE TEMP B-TREE FOR ORDER BY
                    """.trimIndent(),
                    db.explain(filter),
                )
            }
        }

    @Test
    fun testIdQuery() =
        forEachDB { db ->
            val filter = Filter(ids = listOf(key1))
            if (db.indexStrategy.useAndIndexIdOnOrderBy) {
                TestCase.assertEquals(
                    """
                    SELECT id, pubkey, created_at, kind, tags, content, sig FROM event_headers
                    WHERE id = "7c5eb72a4584fdaaeaa145b25c92ea9917704224951219dbd43acef9e91fb88d"
                    ORDER BY created_at DESC, id ASC
                    └── SEARCH event_headers USING INDEX event_headers_id (id=?)
                    """.trimIndent(),
                    db.explain(filter),
                )
            } else {
                TestCase.assertEquals(
                    """
                    SELECT id, pubkey, created_at, kind, tags, content, sig FROM event_headers
                    WHERE id = "7c5eb72a4584fdaaeaa145b25c92ea9917704224951219dbd43acef9e91fb88d"
                    ORDER BY created_at DESC
                    └── SEARCH event_headers USING INDEX event_headers_id (id=?)
                    """.trimIndent(),
                    db.explain(filter),
                )
            }
        }

    @Test
    fun testAuthors() =
        forEachDB { db ->
            val filter = Filter(authors = listOf(key1, key2), kinds = listOf(1, 30023), limit = 300)
            if (db.indexStrategy.useAndIndexIdOnOrderBy) {
                TestCase.assertEquals(
                    """
                    SELECT id, pubkey, created_at, kind, tags, content, sig FROM event_headers
                    WHERE (kind IN ("1", "30023")) AND (pubkey IN ("7c5eb72a4584fdaaeaa145b25c92ea9917704224951219dbd43acef9e91fb88d", "f3ac434d61bc0f491a814782ccfdf9c439dae1f0bde9097ad4a245f4c495cd14"))
                    ORDER BY created_at DESC, id ASC
                    LIMIT 300
                    ├── SEARCH event_headers USING INDEX query_by_kind_pubkey_created (kind=? AND pubkey=?)
                    └── USE TEMP B-TREE FOR ORDER BY
                    """.trimIndent(),
                    db.explain(filter),
                )
            } else {
                TestCase.assertEquals(
                    """
                    SELECT id, pubkey, created_at, kind, tags, content, sig FROM event_headers
                    WHERE (kind IN ("1", "30023")) AND (pubkey IN ("7c5eb72a4584fdaaeaa145b25c92ea9917704224951219dbd43acef9e91fb88d", "f3ac434d61bc0f491a814782ccfdf9c439dae1f0bde9097ad4a245f4c495cd14"))
                    ORDER BY created_at DESC
                    LIMIT 300
                    ├── SEARCH event_headers USING INDEX query_by_kind_pubkey_created (kind=? AND pubkey=?)
                    └── USE TEMP B-TREE FOR ORDER BY
                    """.trimIndent(),
                    db.explain(filter),
                )
            }
        }

    @Test
    fun testAuthorsAndSearch() =
        forEachDB { db ->
            val filter = Filter(authors = listOf(key1, key2, key3), search = "keywords")
            if (db.indexStrategy.useAndIndexIdOnOrderBy) {
                TestCase.assertEquals(
                    """
                    SELECT event_headers.id, event_headers.pubkey, event_headers.created_at, event_headers.kind, event_headers.tags, event_headers.content, event_headers.sig FROM event_headers
                    INNER JOIN event_fts ON event_headers.row_id = event_fts.event_header_row_id
                    WHERE (event_fts MATCH "keywords") AND (event_headers.pubkey IN ("7c5eb72a4584fdaaeaa145b25c92ea9917704224951219dbd43acef9e91fb88d", "f3ac434d61bc0f491a814782ccfdf9c439dae1f0bde9097ad4a245f4c495cd14", "12ae0fd81c85e1e7d9ed096397dc3129849425fe6f8afce7213ebf38ddfc6ca9"))
                    ORDER BY event_headers.created_at DESC, event_headers.id ASC
                    ├── SCAN event_fts VIRTUAL TABLE INDEX 4:
                    ├── SEARCH event_headers USING INTEGER PRIMARY KEY (rowid=?)
                    └── USE TEMP B-TREE FOR ORDER BY
                    """.trimIndent(),
                    db.explain(filter),
                )
            } else {
                TestCase.assertEquals(
                    """
                    SELECT event_headers.id, event_headers.pubkey, event_headers.created_at, event_headers.kind, event_headers.tags, event_headers.content, event_headers.sig FROM event_headers
                    INNER JOIN event_fts ON event_headers.row_id = event_fts.event_header_row_id
                    WHERE (event_fts MATCH "keywords") AND (event_headers.pubkey IN ("7c5eb72a4584fdaaeaa145b25c92ea9917704224951219dbd43acef9e91fb88d", "f3ac434d61bc0f491a814782ccfdf9c439dae1f0bde9097ad4a245f4c495cd14", "12ae0fd81c85e1e7d9ed096397dc3129849425fe6f8afce7213ebf38ddfc6ca9"))
                    ORDER BY event_headers.created_at DESC
                    ├── SCAN event_fts VIRTUAL TABLE INDEX 4:
                    ├── SEARCH event_headers USING INTEGER PRIMARY KEY (rowid=?)
                    └── USE TEMP B-TREE FOR ORDER BY
                    """.trimIndent(),
                    db.explain(filter),
                )
            }
        }

    @Test
    fun testKindAndSearch() =
        forEachDB { db ->
            val filter = Filter(kinds = listOf(1, 1111, 10000), search = "keywords")
            val orderBy = if (db.indexStrategy.useAndIndexIdOnOrderBy) "event_headers.created_at DESC, event_headers.id ASC" else "event_headers.created_at DESC"
            TestCase.assertEquals(
                """
                SELECT event_headers.id, event_headers.pubkey, event_headers.created_at, event_headers.kind, event_headers.tags, event_headers.content, event_headers.sig FROM event_headers
                INNER JOIN event_fts ON event_headers.row_id = event_fts.event_header_row_id
                WHERE (event_fts MATCH "keywords") AND (event_headers.kind IN ("1", "1111", "10000"))
                ORDER BY $orderBy
                ├── SCAN event_fts VIRTUAL TABLE INDEX 4:
                ├── SEARCH event_headers USING INTEGER PRIMARY KEY (rowid=?)
                └── USE TEMP B-TREE FOR ORDER BY
                """.trimIndent(),
                db.explain(filter),
            )
        }

    @Test
    fun testAllTag() =
        forEachDB { db ->
            val filter = Filter(tagsAll = mapOf("p" to listOf(key1, key2)))
            val orderBy = if (db.indexStrategy.useAndIndexIdOnOrderBy) "created_at DESC, id ASC" else "created_at DESC"
            if (db.indexStrategy.indexTagsByCreatedAtAlone) {
                TestCase.assertEquals(
                    """
                    SELECT id, pubkey, created_at, kind, tags, content, sig FROM event_headers
                    INNER JOIN (
                        SELECT DISTINCT(event_tags.event_header_row_id) as row_id FROM event_tags INNER JOIN event_tags as event_tagsAll0_1 ON event_tagsAll0_1.event_header_row_id = event_tags.event_header_row_id AND event_tagsAll0_1.created_at = event_tags.created_at WHERE (event_tags.tag_hash = "884286737453847614") AND (event_tagsAll0_1.tag_hash = "-4988851810256311323")
                    ) AS filtered
                    ON event_headers.row_id = filtered.row_id
                    ORDER BY $orderBy
                    ├── CO-ROUTINE filtered
                    │   ├── SEARCH event_tags USING INDEX query_by_tags_hash_kind (tag_hash=?)
                    │   ├── SEARCH event_tagsAll0_1 USING INDEX query_by_tags_hash (tag_hash=? AND created_at=?)
                    │   └── USE TEMP B-TREE FOR DISTINCT
                    ├── SCAN filtered
                    ├── SEARCH event_headers USING INTEGER PRIMARY KEY (rowid=?)
                    └── USE TEMP B-TREE FOR ORDER BY
                    """.trimIndent(),
                    db.explain(filter),
                )
            } else {
                TestCase.assertEquals(
                    """
                    SELECT id, pubkey, created_at, kind, tags, content, sig FROM event_headers
                    INNER JOIN (
                        SELECT DISTINCT(event_tags.event_header_row_id) as row_id FROM event_tags INNER JOIN event_tags as event_tagsAll0_1 ON event_tagsAll0_1.event_header_row_id = event_tags.event_header_row_id AND event_tagsAll0_1.created_at = event_tags.created_at WHERE (event_tags.tag_hash = "884286737453847614") AND (event_tagsAll0_1.tag_hash = "-4988851810256311323")
                    ) AS filtered
                    ON event_headers.row_id = filtered.row_id
                    ORDER BY $orderBy
                    ├── CO-ROUTINE filtered
                    │   ├── SEARCH event_tags USING INDEX query_by_tags_hash_kind (tag_hash=?)
                    │   ├── SEARCH event_tagsAll0_1 USING INDEX fk_event_tags_header_id (event_header_row_id=?)
                    │   └── USE TEMP B-TREE FOR DISTINCT
                    ├── SCAN filtered
                    ├── SEARCH event_headers USING INTEGER PRIMARY KEY (rowid=?)
                    └── USE TEMP B-TREE FOR ORDER BY
                    """.trimIndent(),
                    db.explain(filter),
                )
            }
        }

    @Test
    fun testReportLikeFilter() =
        forEachDB { db ->
            val filter =
                Filter(
                    kinds = listOf(ContactListEvent.KIND),
                    authors = listOf("460c25e682fda7832b52d1f22d3d22b3176d972f60dcdc3212ed8c92ef85065c"),
                    tags =
                        mapOf(
                            "p" to listOf("460c25e682fda7832b52d1f22d3d22b3176d972f60dcdc3212ed8c92ef85065c"),
                        ),
                    limit = 500,
                )
            val orderBy = if (db.indexStrategy.useAndIndexIdOnOrderBy) "created_at DESC, id ASC" else "created_at DESC"
            if (db.indexStrategy.indexTagsWithKindAndPubkey) {
                assertEquals(
                    """
                    SELECT id, pubkey, created_at, kind, tags, content, sig FROM event_headers
                    INNER JOIN (
                        SELECT DISTINCT(event_tags.event_header_row_id) as row_id FROM event_tags WHERE (event_tags.tag_hash = "-4551135004136952885") AND (event_tags.kind = "3") AND (event_tags.pubkey_hash = "5446767199141196776") ORDER BY event_tags.created_at DESC LIMIT 500
                    ) AS filtered
                    ON event_headers.row_id = filtered.row_id
                    ORDER BY $orderBy
                    ├── CO-ROUTINE filtered
                    │   ├── SEARCH event_tags USING INDEX query_by_tags_hash_kind_pubkey (tag_hash=? AND kind=? AND pubkey_hash=?)
                    │   └── USE TEMP B-TREE FOR DISTINCT
                    ├── SCAN filtered
                    ├── SEARCH event_headers USING INTEGER PRIMARY KEY (rowid=?)
                    └── USE TEMP B-TREE FOR ORDER BY
                    """.trimIndent(),
                    db.explain(filter),
                )
            } else {
                assertEquals(
                    """
                    SELECT id, pubkey, created_at, kind, tags, content, sig FROM event_headers
                    INNER JOIN (
                        SELECT DISTINCT(event_tags.event_header_row_id) as row_id FROM event_tags WHERE (event_tags.tag_hash = "-4551135004136952885") AND (event_tags.kind = "3") AND (event_tags.pubkey_hash = "5446767199141196776") ORDER BY event_tags.created_at DESC LIMIT 500
                    ) AS filtered
                    ON event_headers.row_id = filtered.row_id
                    ORDER BY $orderBy
                    ├── CO-ROUTINE filtered
                    │   ├── SEARCH event_tags USING INDEX query_by_tags_hash_kind (tag_hash=? AND kind=?)
                    │   └── USE TEMP B-TREE FOR DISTINCT
                    ├── SCAN filtered
                    ├── SEARCH event_headers USING INTEGER PRIMARY KEY (rowid=?)
                    └── USE TEMP B-TREE FOR ORDER BY
                    """.trimIndent(),
                    db.explain(filter),
                )
            }
        }

    @Test
    fun testFollowersSinceNov2025() =
        forEachDB { db ->
            val filter =
                Filter(
                    kinds = listOf(ContactListEvent.KIND),
                    tags =
                        mapOf(
                            "p" to listOf("460c25e682fda7832b52d1f22d3d22b3176d972f60dcdc3212ed8c92ef85065c"),
                        ),
                    since = 1764553447, // Nov 2025
                )
            val orderBy = if (db.indexStrategy.useAndIndexIdOnOrderBy) "created_at DESC, id ASC" else "created_at DESC"
            assertEquals(
                """
                SELECT id, pubkey, created_at, kind, tags, content, sig FROM event_headers
                INNER JOIN (
                    SELECT DISTINCT(event_tags.event_header_row_id) as row_id FROM event_tags WHERE (event_tags.tag_hash = "-4551135004136952885") AND (event_tags.kind = "3") AND (event_tags.created_at >= "1764553447")
                ) AS filtered
                ON event_headers.row_id = filtered.row_id
                ORDER BY $orderBy
                ├── CO-ROUTINE filtered
                │   ├── SEARCH event_tags USING INDEX query_by_tags_hash_kind (tag_hash=? AND kind=? AND created_at>?)
                │   └── USE TEMP B-TREE FOR DISTINCT
                ├── SCAN filtered
                ├── SEARCH event_headers USING INTEGER PRIMARY KEY (rowid=?)
                └── USE TEMP B-TREE FOR ORDER BY
                """.trimIndent(),
                db.explain(filter),
            )
        }

    @Test
    fun testAllAddressablesOfAKindDownload() =
        forEachDB { db ->
            val filter =
                Filter(
                    kinds = listOf(DraftWrapEvent.KIND),
                    authors = listOf("460c25e682fda7832b52d1f22d3d22b3176d972f60dcdc3212ed8c92ef85065c"),
                    since = 1764553447, // Nov 2025
                )
            val orderBy = if (db.indexStrategy.useAndIndexIdOnOrderBy) "created_at DESC, id ASC" else "created_at DESC"
            assertEquals(
                """
                SELECT id, pubkey, created_at, kind, tags, content, sig FROM event_headers
                WHERE (kind = "31234") AND (pubkey = "460c25e682fda7832b52d1f22d3d22b3176d972f60dcdc3212ed8c92ef85065c") AND (created_at >= "1764553447")
                ORDER BY $orderBy
                └── SEARCH event_headers USING INDEX query_by_kind_pubkey_created (kind=? AND pubkey=? AND created_at>?)
                """.trimIndent(),
                db.explain(filter),
            )
        }

    @Test
    fun testReplaceablesOfMultipleKindsDownloadByDateLimit() =
        forEachDB { db ->
            val filter =
                Filter(
                    kinds = listOf(MetadataEvent.KIND, SearchRelayListEvent.KIND, AdvertisedRelayListEvent.KIND),
                    authors = listOf("460c25e682fda7832b52d1f22d3d22b3176d972f60dcdc3212ed8c92ef85065c"),
                    limit = 20,
                    since = 1764553447, // Nov 2025
                )
            if (db.indexStrategy.useAndIndexIdOnOrderBy) {
                assertEquals(
                    """
                    SELECT id, pubkey, created_at, kind, tags, content, sig FROM event_headers
                    WHERE (kind IN ("0", "10007", "10002")) AND (pubkey = "460c25e682fda7832b52d1f22d3d22b3176d972f60dcdc3212ed8c92ef85065c") AND (created_at >= "1764553447")
                    ORDER BY created_at DESC, id ASC
                    LIMIT 20
                    ├── SEARCH event_headers USING INDEX query_by_kind_pubkey_created (kind=? AND pubkey=? AND created_at>?)
                    └── USE TEMP B-TREE FOR ORDER BY
                    """.trimIndent(),
                    db.explain(filter),
                )
            } else {
                assertEquals(
                    """
                    SELECT id, pubkey, created_at, kind, tags, content, sig FROM event_headers
                    WHERE (kind IN ("0", "10007", "10002")) AND (pubkey = "460c25e682fda7832b52d1f22d3d22b3176d972f60dcdc3212ed8c92ef85065c") AND (created_at >= "1764553447")
                    ORDER BY created_at DESC
                    LIMIT 20
                    ├── SEARCH event_headers USING INDEX query_by_kind_pubkey_created (kind=? AND pubkey=? AND created_at>?)
                    └── USE TEMP B-TREE FOR ORDER BY
                    """.trimIndent(),
                    db.explain(filter),
                )
            }
        }

    @Test
    fun testReplaceablesOfMultipleKindsDownload() =
        forEachDB { db ->
            val filter =
                Filter(
                    kinds = listOf(MetadataEvent.KIND, SearchRelayListEvent.KIND, AdvertisedRelayListEvent.KIND),
                    authors = listOf("460c25e682fda7832b52d1f22d3d22b3176d972f60dcdc3212ed8c92ef85065c"),
                )

            if (db.indexStrategy.useAndIndexIdOnOrderBy) {
                assertEquals(
                    """
                    SELECT id, pubkey, created_at, kind, tags, content, sig FROM event_headers
                    WHERE (kind IN ("0", "10007", "10002")) AND (pubkey = "460c25e682fda7832b52d1f22d3d22b3176d972f60dcdc3212ed8c92ef85065c")
                    ORDER BY created_at DESC, id ASC
                    ├── SEARCH event_headers USING INDEX query_by_kind_pubkey_created (kind=? AND pubkey=?)
                    └── USE TEMP B-TREE FOR ORDER BY
                    """.trimIndent(),
                    db.explain(filter),
                )
            } else {
                assertEquals(
                    """
                    SELECT id, pubkey, created_at, kind, tags, content, sig FROM event_headers
                    WHERE (kind IN ("0", "10007", "10002")) AND (pubkey = "460c25e682fda7832b52d1f22d3d22b3176d972f60dcdc3212ed8c92ef85065c")
                    ORDER BY created_at DESC
                    ├── SEARCH event_headers USING INDEX query_by_kind_pubkey_created (kind=? AND pubkey=?)
                    └── USE TEMP B-TREE FOR ORDER BY
                    """.trimIndent(),
                    db.explain(filter),
                )
            }
        }

    @Test
    fun testContactCardDownloadFromTrustedKeys() =
        forEachDB { db ->
            val filter =
                Filter(
                    kinds = listOf(ContactCardEvent.KIND),
                    authors = listOf("460c25e682fda7832b52d1f22d3d22b3176d972f60dcdc3212ed8c92ef85065c"),
                    tags =
                        mapOf(
                            "d" to listOf("460c25e682fda7832b52d1f22d3d22b3176d972f60dcdc3212ed8c92ef85065c"),
                        ),
                    since = 1764553447, // Nov 2025
                )

            if (db.indexStrategy.useAndIndexIdOnOrderBy) {
                assertEquals(
                    """
                    SELECT id, pubkey, created_at, kind, tags, content, sig FROM event_headers
                    WHERE (kind = "30382") AND (pubkey = "460c25e682fda7832b52d1f22d3d22b3176d972f60dcdc3212ed8c92ef85065c") AND (d_tag = "460c25e682fda7832b52d1f22d3d22b3176d972f60dcdc3212ed8c92ef85065c") AND (created_at >= "1764553447") AND ((kind >= 30000 AND kind < 40000))
                    ORDER BY created_at DESC, id ASC
                    ├── SEARCH event_headers USING INDEX addressable_idx (kind=? AND pubkey=? AND d_tag=?)
                    └── USE TEMP B-TREE FOR ORDER BY
                    """.trimIndent(),
                    db.explain(filter),
                )
            } else {
                assertEquals(
                    """
                    SELECT id, pubkey, created_at, kind, tags, content, sig FROM event_headers
                    WHERE (kind = "30382") AND (pubkey = "460c25e682fda7832b52d1f22d3d22b3176d972f60dcdc3212ed8c92ef85065c") AND (d_tag = "460c25e682fda7832b52d1f22d3d22b3176d972f60dcdc3212ed8c92ef85065c") AND (created_at >= "1764553447") AND ((kind >= 30000 AND kind < 40000))
                    ORDER BY created_at DESC
                    ├── SEARCH event_headers USING INDEX addressable_idx (kind=? AND pubkey=? AND d_tag=?)
                    └── USE TEMP B-TREE FOR ORDER BY
                    """.trimIndent(),
                    db.explain(filter),
                )
            }
        }
}
