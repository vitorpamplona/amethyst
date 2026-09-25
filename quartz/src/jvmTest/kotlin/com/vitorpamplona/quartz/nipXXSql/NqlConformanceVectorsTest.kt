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

import androidx.sqlite.SQLITE_DATA_FLOAT
import androidx.sqlite.SQLITE_DATA_INTEGER
import androidx.sqlite.SQLITE_DATA_NULL
import androidx.sqlite.SQLITE_DATA_TEXT
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.SQLiteStatement
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerSync
import com.vitorpamplona.quartz.utils.Hex
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import java.io.File
import kotlin.math.abs
import kotlin.math.max
import kotlin.test.Test
import kotlin.test.fail

/**
 * Builds and checks NIP-FF's (Nostr Query Language) conformance vectors from
 * `nql/nip-ff-cases.json`: signs the corpus with fixed keys, lays it out as the
 * NIP's `events` / `tags` tables in SQLite (the reference implementation), and
 * for every case checks the declared column names and types against SQLite,
 * that the rows don't depend on insertion order, and that every hand-stated
 * expectation matches SQLite, except where the case records a documented SQLite
 * divergence, which must then really diverge.
 *
 * Set `NQL_VECTORS_OUT` to a path to write the finished vectors there.
 */
class NqlConformanceVectorsTest {
    private val json = Json { prettyPrint = true }

    private class Col(
        val name: String,
        val type: String,
    )

    private class Result(
        val names: List<String>,
        /** SQLite storage class per value, and the value. */
        val rows: List<List<Pair<Int, Any?>>>,
    )

    @Test
    fun vectors() {
        val source = Json.parseToJsonElement(javaClass.getResource("/nql/nip-ff-cases.json")!!.readText()).jsonObject

        // Fixed keys: alice = 1, bob = 2, ... so ids and pubkeys are stable across runs.
        val signers =
            source["authors"]!!.jsonArray.withIndex().associate { (i, a) ->
                a.jsonPrimitive.content to NostrSignerSync(KeyPair(Hex.decode("%064x".format(i + 1))))
            }
        val ids = HashMap<String, String>()

        fun resolve(s: String): String =
            Regex("""\{(pubkey|id):([A-Za-z0-9-]+)\}""").replace(s) { m ->
                val (what, ref) = m.destructured
                if (what == "pubkey") signers.getValue(ref).pubKey else ids[ref] ?: error("unknown ref $ref")
            }

        val events =
            source["corpus"]!!.jsonArray.map { e ->
                val o = e.jsonObject
                val tags = o["tags"]!!.jsonArray.map { t -> t.jsonArray.map { resolve(it.jsonPrimitive.content) }.toTypedArray() }.toTypedArray()
                val event =
                    signers.getValue(o["author"]!!.jsonPrimitive.content).sign<Event>(
                        o["created_at"]!!.jsonPrimitive.content.toLong(),
                        o["kind"]!!.jsonPrimitive.int,
                        tags,
                        o["content"]!!.jsonPrimitive.content,
                    )
                ids[o["ref"]!!.jsonPrimitive.content] = event.id
                event
            }

        val forward = database(events)
        val reverse = database(events.reversed())
        val problems = ArrayList<String>()
        val cases = ArrayList<JsonElement>()

        for (c in source["cases"]!!.jsonArray.map { it.jsonObject }) {
            val name = c["name"]!!.jsonPrimitive.content
            val query = c["query"]!!.jsonPrimitive.content
            val params = c["params"]?.let { resolveParams(it, ::resolve) }
            val out = LinkedHashMap<String, JsonElement>(c)
            params?.let { out["params"] = it }

            val error = c["error"]?.jsonPrimitive?.content
            if (error != null) {
                // `error:` cases are runtime failures SQLite must also report.
                if (error == "error" && runCatching { run(forward, query, params) }.isSuccess) {
                    problems += "$name: expected an evaluation error, SQLite succeeded"
                }
                cases += JsonObject(out)
                continue
            }

            val cols = c["columns"]!!.jsonArray.map { Col(it.jsonArray[0].jsonPrimitive.content, it.jsonArray[1].jsonPrimitive.content) }
            val ordered = c["ordered"]?.jsonPrimitive?.booleanOrNull ?: false
            val tolerance = c["tolerance"]?.jsonPrimitive?.doubleOrNull
            val differs = c["sqlite_differs"] != null

            val f =
                try {
                    run(forward, query, params)
                } catch (e: Exception) {
                    if (!differs) problems += "$name: SQLite failed: ${e.message}"
                    cases += JsonObject(out)
                    continue
                }
            val r = run(reverse, query, params)

            if (f.names != cols.map { it.name }) problems += "$name: SQLite names ${f.names}, declared ${cols.map { it.name }}"

            val converted = convert(f, cols)
            val reversed = convert(r, cols)
            if (converted.error != null && !differs) problems += "$name: ${converted.error}"
            if (!differs && !sameRows(converted.rows, reversed.rows, ordered, tolerance)) {
                problems += "$name: rows depend on insertion order (ordered=$ordered): ${converted.rows} vs ${reversed.rows}"
            }

            val stated = c["rows"]?.jsonArray?.map { it.jsonArray.toList() }
            when {
                stated != null && differs -> {
                    if (converted.error == null && sameRows(converted.rows, stated, ordered, tolerance)) {
                        problems += "$name: marked sqlite_differs, but SQLite gives the stated rows"
                    }
                }

                stated != null -> {
                    if (!sameRows(converted.rows, stated, ordered, tolerance)) problems += "$name: stated ${JsonArray(stated.map(::JsonArray))}, SQLite ${JsonArray(converted.rows.map(::JsonArray))}"
                }

                differs -> {
                    problems += "$name: sqlite_differs needs stated rows"
                }

                else -> {
                    out["rows"] = JsonArray(converted.rows.map(::JsonArray))
                }
            }
            cases += JsonObject(out)
        }

        forward.close()
        reverse.close()
        if (problems.isNotEmpty()) fail("${problems.size} problem(s):\n" + problems.joinToString("\n"))

        System.getenv("NQL_VECTORS_OUT")?.let { path ->
            val doc =
                buildJsonObject {
                    put("nip", JsonPrimitive("FF"))
                    put("events", JsonArray(events.map { Json.parseToJsonElement(it.toJson()) }))
                    put("cases", JsonArray(cases))
                }
            File(path).writeText(json.encodeToString(JsonElement.serializer(), doc) + "\n")
        }
    }

    /** The NIP's two sources as plain tables, loaded in [events] order. */
    private fun database(events: List<Event>): SQLiteConnection {
        val conn = BundledSQLiteDriver().open(":memory:")
        conn.execSQL("CREATE TABLE events (id TEXT, pubkey TEXT, created_at INTEGER, kind INTEGER, content TEXT, sig TEXT)")
        conn.execSQL(
            "CREATE TABLE tags (event_id TEXT, idx INTEGER, t0 TEXT, t1 TEXT, t2 TEXT, t3 TEXT, t4 TEXT, created_at INTEGER, kind INTEGER, pubkey TEXT)",
        )
        conn.prepare("INSERT INTO events VALUES (?, ?, ?, ?, ?, ?)").use { ins ->
            for (e in events) {
                ins.bindText(1, e.id)
                ins.bindText(2, e.pubKey)
                ins.bindLong(3, e.createdAt)
                ins.bindLong(4, e.kind.toLong())
                ins.bindText(5, e.content)
                ins.bindText(6, e.sig)
                ins.step()
                ins.reset()
            }
        }
        conn.prepare("INSERT INTO tags VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)").use { ins ->
            for (e in events) {
                e.tags.forEachIndexed { idx, tag ->
                    if (tag.isEmpty()) return@forEachIndexed
                    ins.bindText(1, e.id)
                    ins.bindLong(2, idx.toLong())
                    for (i in 0..4) if (i < tag.size) ins.bindText(3 + i, tag[i]) else ins.bindNull(3 + i)
                    ins.bindLong(8, e.createdAt)
                    ins.bindLong(9, e.kind.toLong())
                    ins.bindText(10, e.pubKey)
                    ins.step()
                    ins.reset()
                }
            }
        }
        return conn
    }

    private fun resolveParams(
        p: JsonElement,
        resolve: (String) -> String,
    ): JsonElement {
        fun one(v: JsonElement): JsonElement = if (v is JsonPrimitive && v.isString) JsonPrimitive(resolve(v.content)) else v
        return when (p) {
            is JsonArray -> JsonArray(p.map(::one))
            is JsonObject -> JsonObject(p.mapValues { one(it.value) })
            else -> p
        }
    }

    /** Runs [query] as written, binding `?` in order and each `:name` at its SQLite index. */
    private fun run(
        conn: SQLiteConnection,
        query: String,
        params: JsonElement?,
    ): Result =
        conn.prepare(query).use { st ->
            val tokens = parameterTokens(query)
            when (params) {
                is JsonArray -> {
                    params.forEachIndexed { i, v -> bind(st, i + 1, v) }
                }

                is JsonObject -> {
                    tokens.distinct().forEachIndexed { i, t -> bind(st, i + 1, params[t.removePrefix(":")] ?: JsonNull) }
                }

                else -> {}
            }
            val names = (0 until st.getColumnCount()).map { st.getColumnName(it) }
            val rows = ArrayList<List<Pair<Int, Any?>>>()
            while (st.step()) {
                rows +=
                    names.indices.map { i ->
                        when (val t = st.getColumnType(i)) {
                            SQLITE_DATA_INTEGER -> t to st.getLong(i)
                            SQLITE_DATA_FLOAT -> t to st.getDouble(i)
                            SQLITE_DATA_TEXT -> t to st.getText(i)
                            SQLITE_DATA_NULL -> t to null
                            else -> error("unexpected storage class $t")
                        }
                    }
            }
            Result(names, rows)
        }

    private fun bind(
        st: SQLiteStatement,
        index: Int,
        v: JsonElement,
    ) {
        when {
            v is JsonNull -> st.bindNull(index)
            v is JsonPrimitive && v.isString -> st.bindText(index, v.content)
            v is JsonPrimitive && v.booleanOrNull != null -> st.bindLong(index, if (v.booleanOrNull!!) 1 else 0)
            v is JsonPrimitive && v.longOrNull != null -> st.bindLong(index, v.longOrNull!!)
            v is JsonPrimitive -> st.bindDouble(index, v.doubleOrNull!!)
            else -> error("bad parameter $v")
        }
    }

    /** `?` and `:name` parameters in order of appearance, skipping string literals. */
    private fun parameterTokens(q: String): List<String> {
        val out = ArrayList<String>()
        var i = 0
        while (i < q.length) {
            val c = q[i]
            when {
                c == '\'' -> {
                    i = q.indexOf('\'', i + 1).let { if (it < 0) q.length else it } + 1
                    continue
                }

                c == '?' -> {
                    out += "?"
                }

                c == ':' && i + 1 < q.length && (q[i + 1].isLetter() || q[i + 1] == '_') -> {
                    var j = i + 1
                    while (j < q.length && (q[j].isLetterOrDigit() || q[j] == '_')) j++
                    out += q.substring(i, j)
                    i = j
                    continue
                }
            }
            i++
        }
        return out
    }

    private class Converted(
        val rows: List<List<JsonElement>>,
        val error: String?,
    )

    /** SQLite's values in the NIP's JSON form for the declared types, noting any storage class the type doesn't allow. */
    private fun convert(
        r: Result,
        cols: List<Col>,
    ): Converted {
        var error: String? = null
        val rows =
            r.rows.map { row ->
                row.mapIndexed { i, (t, v) ->
                    val type = cols.getOrNull(i)?.type
                    if (t == SQLITE_DATA_NULL) return@mapIndexed JsonNull
                    val ok =
                        when (type) {
                            "INTEGER" -> t == SQLITE_DATA_INTEGER
                            "REAL" -> t == SQLITE_DATA_FLOAT
                            "TEXT" -> t == SQLITE_DATA_TEXT
                            "BOOLEAN" -> t == SQLITE_DATA_INTEGER && (v == 0L || v == 1L)
                            else -> false
                        }
                    if (!ok && error == null) error = "column ${cols.getOrNull(i)?.name} declared $type, SQLite storage class $t ($v)"
                    when {
                        type == "BOOLEAN" && v is Long -> JsonPrimitive(v != 0L)
                        v is Double && v.isInfinite() -> JsonPrimitive(if (v > 0) "Infinity" else "-Infinity")
                        v is Double -> JsonPrimitive(v)
                        v is Long -> if (type == "REAL") JsonPrimitive(v.toDouble()) else JsonPrimitive(v)
                        else -> JsonPrimitive(v as String)
                    }
                }
            }
        return Converted(rows, error)
    }

    private fun sameRows(
        a: List<List<JsonElement>>,
        b: List<List<JsonElement>>,
        ordered: Boolean,
        tolerance: Double?,
    ): Boolean {
        if (a.size != b.size) return false
        if (ordered) return a.indices.all { sameRow(a[it], b[it], tolerance) }
        val left = b.toMutableList()
        for (row in a) {
            val i = left.indexOfFirst { sameRow(row, it, tolerance) }
            if (i < 0) return false
            left.removeAt(i)
        }
        return true
    }

    private fun sameRow(
        a: List<JsonElement>,
        b: List<JsonElement>,
        tolerance: Double?,
    ): Boolean = a.size == b.size && a.indices.all { sameValue(a[it], b[it], tolerance) }

    private fun sameValue(
        a: JsonElement,
        b: JsonElement,
        tolerance: Double?,
    ): Boolean {
        if (a is JsonNull || b is JsonNull) return a is JsonNull && b is JsonNull
        val pa = a.jsonPrimitive
        val pb = b.jsonPrimitive
        if (pa.isString || pb.isString) return pa.isString && pb.isString && pa.content == pb.content
        pa.booleanOrNull?.let { return it == pb.booleanOrNull }
        val x = pa.doubleOrNull ?: return false
        val y = pb.doubleOrNull ?: return false
        if (tolerance == null) return x == y
        return abs(x - y) <= tolerance * max(1.0, max(abs(x), abs(y)))
    }
}
