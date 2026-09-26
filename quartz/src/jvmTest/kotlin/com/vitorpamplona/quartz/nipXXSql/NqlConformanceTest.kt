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

import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.store.sqlite.DefaultIndexingStrategy
import com.vitorpamplona.quartz.nip01Core.store.sqlite.EventStore
import com.vitorpamplona.quartz.utils.EventFactory
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlin.math.abs
import kotlin.math.max
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * NIP-FF's conformance vectors (`nql/FF-conformance.json`, a copy of the NIP's
 * `FF-conformance.json`) against the SQLite store's native path (the query
 * compiled to SQLite SQL) and the interpreter over three backends: the SQLite
 * store's own, one that answers through id walks, and one that refuses
 * unselective scans (whose `unsupported:` refusals the NIP allows).
 */
class NqlConformanceTest {
    private val vectors = Json.parseToJsonElement(javaClass.getResource("/nql/FF-conformance.json")!!.readText()).jsonObject
    private val store = EventStore(dbName = null, relay = null)

    /** The same events in a store that keeps `event_tag_values`, which the compiled path reads `tags` from. */
    private val tagValuesStore = EventStore(dbName = null, relay = null, indexStrategy = DefaultIndexingStrategy(indexTagValues = true))

    init {
        runBlocking {
            vectors["events"]!!.jsonArray.forEach { e ->
                val o = e.jsonObject
                val event =
                    EventFactory.create<Event>(
                        o["id"]!!.jsonPrimitive.content,
                        o["pubkey"]!!.jsonPrimitive.content,
                        o["created_at"]!!.jsonPrimitive.long,
                        o["kind"]!!.jsonPrimitive.int,
                        o["tags"]!!.jsonArray.map { t -> t.jsonArray.map { it.jsonPrimitive.content }.toTypedArray() }.toTypedArray(),
                        o["content"]!!.jsonPrimitive.content,
                        o["sig"]!!.jsonPrimitive.content,
                    )
                store.insert(event)
                tagValuesStore.insert(event)
            }
        }
    }

    @AfterTest
    fun close() {
        store.close()
        tagValuesStore.close()
    }

    private val cases = vectors["cases"]!!.jsonArray.map { it.jsonObject }

    @Test
    fun compiledToSqliteByTheStore() {
        check(unsupportedAllowed = false) { q, p -> runBlocking { store.nql(q, p) } }
    }

    @Test
    fun compiledOverTagValues() {
        check(unsupportedAllowed = false) { q, p -> runBlocking { tagValuesStore.nql(q, p) } }
    }

    @Test
    fun overTheSqliteStore() {
        check(store.sqlBackend(), unsupportedAllowed = false)
    }

    @Test
    fun overIdWalks() {
        check(IdWalkBackend(store), unsupportedAllowed = false)
    }

    @Test
    fun overASelectiveStore() {
        val answered = check(SelectiveBackend(store), unsupportedAllowed = true)
        assertTrue(answered > 60, "a selective store should still answer most row cases: $answered")
    }

    /** Runs every case; returns how many row cases were answered. */
    private fun check(
        backend: SqlStoreBackend,
        unsupportedAllowed: Boolean,
    ): Int = check(unsupportedAllowed) { q, p -> runBlocking { Nql.run(q, p, backend) } }

    private fun check(
        unsupportedAllowed: Boolean,
        run: (String, List<Any?>) -> NqlResult,
    ): Int {
        val problems = ArrayList<String>()
        var answered = 0
        for (c in cases) {
            val name = c["name"]!!.jsonPrimitive.content
            val query = c["query"]!!.jsonPrimitive.content
            val params = (c["params"] as? JsonArray)?.map { NqlJsonValues.fromElement(it) } ?: emptyList()
            val error = c["error"]?.jsonPrimitive?.content
            val result =
                try {
                    run(query, params)
                } catch (e: SqlException) {
                    when {
                        e.prefix == SqlException.UNSUPPORTED && unsupportedAllowed && error != "invalid" -> {}
                        error == null -> problems += "$name: refused ${e.message}"
                        e.prefix != error -> problems += "$name: expected $error, got ${e.message}"
                    }
                    continue
                }
            if (error != null) {
                problems += "$name: expected $error, got an answer"
                continue
            }
            answered++
            val columns = c["columns"]!!.jsonArray.map { it.jsonArray[0].jsonPrimitive.content to NqlType.valueOf(it.jsonArray[1].jsonPrimitive.content) }
            if (result.columns.map { it.name to it.type } != columns) {
                problems += "$name: columns ${result.columns.map { it.name to it.type }}, expected $columns"
                continue
            }
            val expected = c["rows"]!!.jsonArray.map { row -> row.jsonArray.mapIndexed { i, v -> NqlJsonValues.fromElement(v, columns[i].second) } }
            val ordered = c["ordered"]?.jsonPrimitive?.booleanOrNull ?: false
            val tolerance = c["tolerance"]?.jsonPrimitive?.doubleOrNull
            if (!sameRows(result.rows, expected, ordered, tolerance)) problems += "$name: rows ${result.rows}, expected $expected"
        }
        if (problems.isNotEmpty()) fail("${problems.size} of ${cases.size} cases failed:\n" + problems.joinToString("\n"))
        return answered
    }

    private fun sameRows(
        a: List<List<Any?>>,
        b: List<List<Any?>>,
        ordered: Boolean,
        tolerance: Double?,
    ): Boolean {
        if (a.size != b.size) return false
        val same = { x: List<Any?>, y: List<Any?> -> x.size == y.size && x.indices.all { sameValue(x[it], y[it], tolerance) } }
        if (ordered) return a.indices.all { same(a[it], b[it]) }
        val left = b.toMutableList()
        for (row in a) {
            val i = left.indexOfFirst { same(row, it) }
            if (i < 0) return false
            left.removeAt(i)
        }
        return true
    }

    private fun sameValue(
        a: Any?,
        b: Any?,
        tolerance: Double?,
    ): Boolean {
        if (a == null || b == null) return a == null && b == null
        if (a is Double && b is Double) {
            if (tolerance == null) return a == b
            return abs(a - b) <= tolerance * max(1.0, max(abs(a), abs(b)))
        }
        return a == b && a::class == b::class
    }
}
