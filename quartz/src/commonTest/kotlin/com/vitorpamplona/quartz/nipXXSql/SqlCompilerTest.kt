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

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SqlCompilerTest {
    private val sources =
        SqlTableSources(
            events = TableSource("SELECT * FROM ev_phys WHERE k <> ?", listOf(1059L)),
            tags = TableSource("SELECT * FROM tag_phys"),
        )

    private fun compile(
        sql: String,
        vararg params: Any?,
    ) = SqlCompiler.compile(sql, sources, params.toList())

    private fun rejects(
        sql: String,
        prefix: String,
    ) {
        val e = assertFailsWith<SqlException>(sql) { compile(sql) }
        assertEquals(prefix, e.prefix, "$sql -> ${e.message}")
    }

    // ---- what must never get through ---------------------------------------

    @Test
    fun rejectsEverythingButSelect() {
        rejects("DELETE FROM events", SqlException.UNSUPPORTED)
        rejects("INSERT INTO events VALUES (1)", SqlException.UNSUPPORTED)
        rejects("UPDATE events SET kind = 1", SqlException.UNSUPPORTED)
        rejects("PRAGMA query_only = OFF", SqlException.UNSUPPORTED)
        rejects("ATTACH DATABASE '/etc/passwd' AS x", SqlException.UNSUPPORTED)
        rejects("CREATE TEMP TABLE t(x)", SqlException.UNSUPPORTED)
        rejects("VACUUM", SqlException.UNSUPPORTED)
    }

    @Test
    fun rejectsStackedStatements() {
        rejects("SELECT 1; DELETE FROM events", SqlException.UNSUPPORTED)
        rejects("SELECT 1; SELECT 2", SqlException.UNSUPPORTED)
        // A trailing semicolon alone is fine.
        compile("SELECT 1;")
    }

    @Test
    fun rejectsTablesOutsideTheProfile() {
        rejects("SELECT * FROM event_headers", SqlException.INVALID)
        rejects("SELECT * FROM sqlite_master", SqlException.INVALID)
        rejects("SELECT * FROM sqlite_schema", SqlException.INVALID)
        rejects("SELECT * FROM main.events", SqlException.UNSUPPORTED)
        rejects("SELECT * FROM pragma_table_info('events')", SqlException.UNSUPPORTED)
        rejects("SELECT * FROM json_each('[1]')", SqlException.UNSUPPORTED)
        rejects("SELECT (SELECT count(*) FROM event_tags)", SqlException.INVALID)
        rejects("SELECT * FROM events WHERE id IN (SELECT id FROM ev_phys)", SqlException.INVALID)
    }

    @Test
    fun rejectsFunctionsOutsideTheProfile() {
        rejects("SELECT load_extension('x')", SqlException.UNSUPPORTED)
        rejects("SELECT sqlite_version()", SqlException.UNSUPPORTED)
        rejects("SELECT randomblob(1000000000)", SqlException.UNSUPPORTED)
        rejects("SELECT printf('%.*c', 1000000000, 'x')", SqlException.UNSUPPORTED)
        rejects("SELECT json_extract(content, '$') FROM events", SqlException.UNSUPPORTED)
        rejects("SELECT \"count\"(*) FROM events", SqlException.INVALID)
    }

    @Test
    fun cteCannotReachLaterOrPhysicalNames() {
        // A forward reference must not fall through to a physical table.
        rejects("WITH a AS (SELECT * FROM ev_phys), ev_phys AS (SELECT 1) SELECT * FROM a", SqlException.INVALID)
        rejects("WITH a AS (SELECT * FROM b), b AS (SELECT 1) SELECT * FROM a", SqlException.INVALID)
        rejects("WITH a AS (SELECT 1), a AS (SELECT 2) SELECT * FROM a", SqlException.INVALID)
        // A CTE is out of scope outside its WITH.
        rejects("SELECT * FROM (WITH a AS (SELECT 1) SELECT * FROM a), a", SqlException.INVALID)
    }

    @Test
    fun rejectsMalformedText() {
        rejects("SELECT 'unterminated", SqlException.INVALID)
        rejects("SELECT /* open", SqlException.INVALID)
        rejects("SELECT 1 +", SqlException.INVALID)
        rejects("SELECT x'00'", SqlException.UNSUPPORTED)
        rejects("SELECT 1 & 2", SqlException.UNSUPPORTED)
        rejects("SELECT ?", SqlException.INVALID) // no value bound
    }

    @Test
    fun rejectsPathologicalNesting() {
        rejects("SELECT " + "(".repeat(5000) + "1" + ")".repeat(5000), SqlException.UNSUPPORTED)
    }

    // ---- what the emitted SQL looks like -----------------------------------

    @Test
    fun substitutesTablesWithSources() {
        val c = compile("SELECT id FROM events WHERE kind = ?", 1L)
        assertEquals(
            "SELECT `id` AS `id` FROM (SELECT * FROM ev_phys WHERE k <> ?) AS `events` WHERE (`kind` = ?)",
            c.sql,
        )
        // Source args come first, in text order.
        assertEquals(listOf(1059L, 1L), c.args)
    }

    @Test
    fun aliasesKeepQualifiersWorking() {
        val c = compile("SELECT e.id FROM events e JOIN tags t ON t.event_id = e.id")
        assertTrue(c.sql.contains(") AS `e` JOIN (SELECT * FROM tag_phys) AS `t` ON (`t`.`event_id` = `e`.`id`)"), c.sql)
    }

    @Test
    fun ctesAreRenamed() {
        val c = compile("WITH ev_phys AS (SELECT 1 AS x) SELECT x FROM ev_phys")
        assertEquals("WITH `nsq_cte_1` AS (SELECT 1 AS `x`) SELECT `x` AS `x` FROM `nsq_cte_1` AS `ev_phys`", c.sql)
    }

    @Test
    fun cteMayShadowAProfileTable() {
        val c = compile("WITH events AS (SELECT 1 AS id) SELECT id FROM events")
        assertFalse(c.sql.contains("ev_phys"), c.sql)
    }

    @Test
    fun recursiveCteSeesItself() {
        val c = compile("WITH RECURSIVE n(x) AS (SELECT 1 UNION ALL SELECT x + 1 FROM n WHERE x < 5) SELECT x FROM n")
        assertEquals(
            "WITH RECURSIVE `nsq_cte_1`(`x`) AS (SELECT 1 AS `1` UNION ALL SELECT (`x` + 1) AS `x + 1` " +
                "FROM `nsq_cte_1` AS `n` WHERE (`x` < 5)) SELECT `x` AS `x` FROM `nsq_cte_1` AS `n`",
            c.sql,
        )
    }

    @Test
    fun stringsAndIdentifiersAreRequoted() {
        val c = compile("SELECT 'a''b' AS \"we\"\"ird\" FROM events")
        assertTrue(c.sql.startsWith("SELECT 'a''b' AS `we\"ird` FROM"), c.sql)
    }

    @Test
    fun doubleNegationCannotOpenAComment() {
        val c = compile("SELECT - -1, -(-1)")
        assertFalse(c.sql.contains("--"), c.sql)
    }

    @Test
    fun unaliasedColumnsKeepTheirSourceText() {
        val c = compile("SELECT count(*), kind , max( created_at ) FROM events GROUP BY kind")
        assertTrue(c.sql.startsWith("SELECT count(*) AS `count(*)`, `kind` AS `kind`, max(`created_at`) AS `max( created_at )`"), c.sql)
    }

    @Test
    fun parameters() {
        assertEquals(listOf(1059L, 7L, 1L), compile("SELECT * FROM events WHERE kind = ?2 OR kind = ?1", 1L, 7L).args)
        assertEquals(listOf(1059L, 1L, 7L, 7L), compile("SELECT * FROM events WHERE kind IN (?, ?, ?2)", 1L, 7L).args)
        val named = SqlCompiler.compile("SELECT * FROM events WHERE pubkey = :pk", sources, named = mapOf("pk" to "ab"))
        assertEquals(listOf(1059L, "ab"), named.args)
        assertEquals(listOf(1059L, 1L), compile("SELECT * FROM events WHERE kind = ?", true).args)
    }

    @Test
    fun operatorPrecedenceFollowsSqlite() {
        // `||` binds tighter than `*`, `<` tighter than `=`, NOT looser than `=`.
        assertEquals("SELECT (2 * ('a' || 'b')) AS `2 * 'a' || 'b'`", compile("SELECT 2 * 'a' || 'b'").sql)
        assertEquals("SELECT (1 = (2 < 3)) AS `1 = 2 < 3`", compile("SELECT 1 = 2 < 3").sql)
        assertEquals("SELECT (NOT (1 = 2)) AS `NOT 1 = 2`", compile("SELECT NOT 1 = 2").sql)
        assertEquals(
            "SELECT ((1 BETWEEN 0 AND 2) AND (3 > 2)) AS `1 BETWEEN 0 AND 2 AND 3 > 2`",
            compile("SELECT 1 BETWEEN 0 AND 2 AND 3 > 2").sql,
        )
    }

    @Test
    fun windowsAndFilters() {
        val c =
            compile(
                "SELECT pubkey, count(*) FILTER (WHERE kind = 1) AS notes, " +
                    "row_number() OVER (PARTITION BY kind ORDER BY created_at DESC NULLS LAST ROWS BETWEEN UNBOUNDED PRECEDING AND CURRENT ROW) " +
                    "FROM events GROUP BY pubkey",
            )
        assertTrue(c.sql.contains("count(*) FILTER (WHERE (`kind` = 1)) AS `notes`"), c.sql)
        assertTrue(
            c.sql.contains(
                "row_number() OVER (PARTITION BY `kind` ORDER BY `created_at` DESC NULLS LAST ROWS BETWEEN UNBOUNDED PRECEDING AND CURRENT ROW)",
            ),
            c.sql,
        )
    }

    @Test
    fun sqliteLimitCommaIsOffsetThenCount() {
        assertTrue(compile("SELECT id FROM events LIMIT 10, 5").sql.endsWith(" LIMIT 5 OFFSET 10"))
    }

    // ---- tags pushdown -------------------------------------------------------

    private val pushSources =
        SqlTableSources(
            events = TableSource("SELECT * FROM ev_phys"),
            tags = TableSource("SELECT * FROM tag_phys"),
            tagsMatching = { c -> TableSource("SELECT * FROM tag_pushed WHERE n = ? AND v IN (${c.values.joinToString(", ") { "?" }})", listOf(c.name) + c.values) },
        )

    private fun pushed(
        sql: String,
        vararg params: Any?,
    ) = SqlCompiler.compile(sql, pushSources, params.toList())

    @Test
    fun pushesNameAndValueIntoTags() {
        val c = pushed("SELECT value FROM tags WHERE name = 't' AND value = 'nostr'")
        assertTrue(c.sql.contains("FROM (SELECT * FROM tag_pushed WHERE n = ? AND v IN (?)) AS `tags`"), c.sql)
        assertEquals(listOf("t", "nostr"), c.args)
    }

    @Test
    fun pushesInListsParamsAndEitherOperandOrder() {
        val c = pushed("SELECT count(*) FROM tags t WHERE ? = t.name AND t.value IN (?, 'b', ?1)", "t", "a")
        assertTrue(c.sql.contains("tag_pushed"), c.sql)
        // `?` = ?1 = "t", the bare `?` in the list is ?2 = "a". Source args
        // (name, then distinct values) come first, then the query's own in text order.
        assertEquals(listOf("t", "a", "b", "t", "t", "a", "t"), c.args)
    }

    @Test
    fun pushesIntoJoinsWhereItIsSafe() {
        assertTrue(pushed("SELECT 1 FROM events e JOIN tags t ON t.event_id = e.id AND t.name = 'e' AND t.value = 'x'").sql.contains("tag_pushed"))
        // Right side of a LEFT JOIN: ON only restricts the null-extended side.
        assertTrue(pushed("SELECT 1 FROM events e LEFT JOIN tags t ON t.event_id = e.id AND t.name = 'e' AND t.value = 'x'").sql.contains("tag_pushed"))
        // A strict WHERE on the null-extended side.
        assertTrue(pushed("SELECT 1 FROM events e LEFT JOIN tags t ON t.event_id = e.id WHERE t.name = 'e' AND t.value = 'x'").sql.contains("tag_pushed"))
    }

    @Test
    fun doesNotPushWhereItWouldChangeResults() {
        fun notPushed(
            sql: String,
            vararg params: Any?,
        ) {
            val c = pushed(sql, *params)
            assertFalse(c.sql.contains("tag_pushed"), sql)
        }
        // ON of a LEFT JOIN doesn't filter its preserved side.
        notPushed("SELECT 1 FROM tags t LEFT JOIN events e ON e.id = t.event_id AND t.name = 'e' AND t.value = 'x'")
        // Not a top-level conjunct.
        notPushed("SELECT 1 FROM tags WHERE name = 'e' AND value = 'x' OR kind = 1")
        notPushed("SELECT 1 FROM tags WHERE NOT (name = 'e' AND value = 'x')")
        notPushed("SELECT 1 FROM tags WHERE name = 'e' AND value NOT IN ('x')")
        // Only one of the two, or not a string.
        notPushed("SELECT 1 FROM tags WHERE name = 'e'")
        notPushed("SELECT 1 FROM tags WHERE value = 'x'")
        notPushed("SELECT 1 FROM tags WHERE name = 'e' AND value = ?", 5L)
        notPushed("SELECT 1 FROM tags WHERE name = 'e' AND value = content")
        // Unqualified columns with more than one source are ambiguous here.
        notPushed("SELECT 1 FROM tags, events WHERE name = 'e' AND value = 'x'")
        // Another alias's columns: only `b` is narrowed.
        val two = pushed("SELECT 1 FROM tags a, tags b WHERE b.name = 'e' AND b.value = 'x' AND a.idx = 0")
        assertEquals(1, Regex("tag_pushed").findAll(two.sql).count(), two.sql)
        assertTrue(two.sql.contains("(SELECT * FROM tag_phys) AS `a`"), two.sql)
        // A CTE named tags is not the base table.
        notPushed("WITH tags AS (SELECT 'e' AS name, 'x' AS value) SELECT 1 FROM tags WHERE name = 'e' AND value = 'x'")
    }

    @Test
    fun positionalParametersAreNumberedLikeSqlite() {
        // Bare `?` is one more than the largest number seen so far, in text order.
        assertEquals(listOf("a", "a", "b"), compile("SELECT ?, ?1, ?", "a", "b").args)
        assertEquals(listOf("b", "c"), compile("SELECT ?2, ?", "a", "b", "c").args)
    }
}
