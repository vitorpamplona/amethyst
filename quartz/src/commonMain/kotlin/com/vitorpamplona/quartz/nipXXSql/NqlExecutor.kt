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

/**
 * Runs a checked NQL query ([NqlChecker]) over a [SqlStoreBackend]:
 *
 * 1. A single-source aggregate or DISTINCT whose conditions the store can
 *    express exactly goes to [SqlStoreBackend.aggregate].
 * 2. Otherwise each `events` / `tags` source is scanned with the conditions
 *    the query puts on it alone ([ScanAnalyzer]); a source the store refuses
 *    to scan on its own is fetched by the join keys of one already loaded.
 * 3. Joins (hashed on an equality when the ON has one), WHERE, grouping,
 *    HAVING, the result columns, DISTINCT, ORDER BY and LIMIT run here, with
 *    NQL's semantics ([NqlValues]).
 *
 * Subqueries run once per distinct value of the outer columns they read.
 */
internal class NqlExecutor(
    private val backend: SqlStoreBackend,
    private val params: List<Any?>,
) {
    /** One query's row: a row per source (null where a LEFT JOIN found nothing), in the enclosing row's scope. */
    class Env(
        val query: NqlQuery?,
        val rows: Array<Array<Any?>?>,
        val parent: Env?,
    ) {
        /** A grouped query's aggregate values for the group [rows] stands for. */
        var aggregates: Array<Any?>? = null
    }

    private val subqueryResults = HashMap<NqlQuery, HashMap<List<Any?>, List<Array<Any?>>>>()
    private val outerColumns = HashMap<NqlQuery, List<NqlColumnRef>>()

    suspend fun run(q: NqlQuery): List<Array<Any?>> = execute(q, null)

    private suspend fun execute(
        q: NqlQuery,
        outer: Env?,
    ): List<Array<Any?>> {
        native(q, outer)?.let { return it }

        val sources = load(q, outer)
        var rows = join(q, sources, outer)
        q.where?.let { where -> rows = rows.filter { eval(where, Env(q, it, outer)) == true } }

        // Each output row with its ORDER BY keys.
        val out = ArrayList<Pair<Array<Any?>, List<Any?>>>()
        if (q.grouped) {
            val terms = q.groupBy.map { if (it is NqlColumnRef && it.output >= 0) q.outputs[it.output].expr else it }
            val groups = LinkedHashMap<List<Any?>, Pair<Array<Array<Any?>?>, List<NqlAggregateState>>>()
            for (row in rows) {
                val env = Env(q, row, outer)
                val key = terms.map { NqlValues.key(eval(it, env)) }
                val group = groups.getOrPut(key) { row to q.aggregates.map { NqlAggregateState(it) } }
                q.aggregates.forEachIndexed { i, a -> group.second[i].add(if (a.star) null else eval(a.args[0], env)) }
            }
            if (terms.isEmpty() && groups.isEmpty()) groups[emptyList()] = arrayOfNulls<Array<Any?>>(q.from.size) to q.aggregates.map { NqlAggregateState(it) }
            for ((row, states) in groups.values) {
                val env = Env(q, row, outer)
                env.aggregates = Array(states.size) { states[it].result() }
                if (q.having != null && eval(q.having, env) != true) continue
                out.add(project(q, env))
            }
        } else {
            for (row in rows) out.add(project(q, Env(q, row, outer)))
        }

        val distinct = if (q.distinct) out.distinctBy { (values, _) -> values.map { NqlValues.key(it) } } else out
        val sorted = if (q.orderBy.isEmpty()) distinct else distinct.sortedWith { a, b -> compareKeys(q, a.second, b.second) }
        return slice(q, sorted.map { it.first })
    }

    private suspend fun project(
        q: NqlQuery,
        env: Env,
    ): Pair<Array<Any?>, List<Any?>> {
        val values = arrayOfNulls<Any?>(q.outputs.size)
        for (i in q.outputs.indices) values[i] = eval(q.outputs[i].expr, env)
        val keys = q.orderBy.map { if (it.output >= 0) values[it.output] else eval(it.expr, env) }
        return values to keys
    }

    /** NULLs sort after every value: last ascending, first descending. */
    private fun compareKeys(
        q: NqlQuery,
        a: List<Any?>,
        b: List<Any?>,
    ): Int {
        for (i in a.indices) {
            val x = a[i]
            val y = b[i]
            val c =
                when {
                    x == null && y == null -> 0
                    x == null -> 1
                    y == null -> -1
                    else -> NqlValues.compare(x, y)
                }
            if (c != 0) return if (q.orderBy[i].descending) -c else c
        }
        return 0
    }

    private fun count(c: NqlCount?): Long? = c?.let { it.literal ?: (params[it.param!!] as Long) }

    private fun <T> slice(
        q: NqlQuery,
        rows: List<T>,
    ): List<T> {
        val offset = count(q.offset) ?: 0
        val limit = count(q.limit)
        if (offset == 0L && limit == null) return rows
        val from = minOf(offset, rows.size.toLong()).toInt()
        val to = if (limit == null) rows.size else minOf(rows.size.toLong(), from + limit).toInt()
        return rows.subList(from, to)
    }

    // ---- Scans --------------------------------------------------------------

    /** A value fixed for this run of [q]: a literal, a parameter or a column of an enclosing query; else null. */
    private fun constant(
        q: NqlQuery,
        e: NqlExpr,
        outer: Env?,
    ): Any? =
        when {
            e is NqlLiteral -> e.value
            e is NqlParam -> params[e.index]
            e is NqlColumnRef && e.owner !== q && e.output < 0 -> lookup(e, outer)
            else -> null
        }

    private fun columnOf(
        q: NqlQuery,
        i: Int,
        e: NqlExpr,
    ): String? = if (e is NqlColumnRef && e.owner === q && e.output < 0 && e.source == i) q.from[i].columns[e.index].first else null

    private suspend fun load(
        q: NqlQuery,
        outer: Env?,
    ): List<List<Array<Any?>>> {
        val n = q.from.size
        if (n == 0) return emptyList()
        val nullable = BooleanArray(n) { q.from[it].join == NqlJoin.LEFT }
        val where = conjuncts(q.where)
        val onOf = Array(n) { conjuncts(q.from[it].on) }
        val local = q.localPredicates()

        val specs = arrayOfNulls<ScanSpec>(n)
        for (i in 0 until n) {
            val table = q.from[i].table ?: continue
            specs[i] = ScanAnalyzer({ columnOf(q, i, it) }, { constant(q, it, outer) }).analyze(table, local[i])
        }

        // Equalities between two table sources, for fetching one by the other's keys.
        fun link(
            e: NqlExpr,
            both: Boolean,
            later: Int,
        ) {
            if (e !is NqlBinary || e.op != "=") return
            val a = e.left as? NqlColumnRef ?: return
            val b = e.right as? NqlColumnRef ?: return
            if (a.owner !== q || b.owner !== q || a.output >= 0 || b.output >= 0 || a.source == b.source) return
            val sa = specs[a.source] ?: return
            val sb = specs[b.source] ?: return
            val ca = q.from[a.source].columns[a.index].first
            val cb = q.from[b.source].columns[b.index].first
            if (both || a.source == later) sa.links += ScanLink(ca, b.source, cb)
            if (both || b.source == later) sb.links += ScanLink(cb, a.source, ca)
        }
        for (c in where) {
            val s = q.sourcesOf(c)
            if (s.none { nullable[it] }) link(c, both = true, later = -1)
        }
        for (i in 1 until n) {
            // The later source's rows only matter where they match; the earlier ones', only if the join is inner.
            for (c in onOf[i]) link(c, both = !nullable[i] && q.sourcesOf(c).none { it != i && nullable[it] }, later = i)
        }

        // What each events source is read for, outside the predicates its spec already guarantees.
        val captured = HashSet<NqlExpr>()
        specs.forEach { s -> s?.captured?.let { captured.addAll(it) } }
        val usage = columnUsage(q, captured)
        specs.forEachIndexed { i, s -> s?.columns = usage[i] }

        val newest = newestFirstLimit(q, specs[0])
        val loaded = arrayOfNulls<List<Array<Any?>>>(n)
        for (i in 0 until n) {
            val sub = q.from[i].subquery
            if (sub != null) {
                loaded[i] = execute(sub, outer)
                continue
            }
            val spec = specs[i]!!
            if (spec.matchesNothing) {
                loaded[i] = emptyList()
            } else if (backend.acceptsScan(spec)) {
                loaded[i] =
                    rows(q, i, local[i], outer) { sink ->
                        if (newest != null && i == 0) loadNewest(spec.withLimit(newest).also { it.columns = spec.columns }, sink) else fetch(spec, sink)
                    }
            }
        }

        // Sources the store refused on their own: fetched by the join keys of a loaded one.
        var progress = true
        while (progress) {
            progress = false
            for (i in 0 until n) {
                if (loaded[i] != null) continue
                val spec = specs[i]!!
                for (link in spec.links) {
                    val target = loaded[link.target] ?: continue
                    val targetColumn = q.from[link.target].columns.indexOfFirst { it.first == link.targetColumn }
                    val keys = target.mapNotNullTo(HashSet()) { it[targetColumn] as? String }
                    val narrowed = spec.narrowedTo(link.column, keys) ?: continue
                    if (!narrowed.matchesNothing && !backend.acceptsScan(narrowed)) continue
                    loaded[i] =
                        rows(q, i, local[i], outer) { sink ->
                            if (!narrowed.matchesNothing) {
                                for (chunk in keys.chunked(JOIN_KEY_CHUNK)) {
                                    fetch(spec.narrowedTo(link.column, chunk.toSet())!!.also { it.columns = spec.columns }, sink)
                                }
                            }
                        }
                    progress = true
                    break
                }
            }
        }
        if (loaded.any { it == null }) {
            throw SqlException.unsupported(
                "this relay needs a condition on kind, pubkey, id or a single-letter tag for every source in the query, " +
                    "or a join to one that has it",
            )
        }
        return loaded.map { it!! }
    }

    /** The rows of table source [i] from the events [fetch] hands over, each event once, kept where [local] holds. */
    private suspend fun rows(
        q: NqlQuery,
        i: Int,
        local: List<NqlExpr>,
        outer: Env?,
        fetch: suspend ((Event) -> Unit) -> Unit,
    ): List<Array<Any?>> {
        val seen = HashSet<String>()
        val all = ArrayList<Array<Any?>>()
        val tags = q.from[i].table == SqlProfile.TAGS
        fetch { e ->
            if (!seen.add(e.id)) return@fetch
            if (tags) {
                e.tags.forEachIndexed { idx, tag ->
                    if (tag.isNotEmpty()) {
                        all.add(arrayOf(e.id, idx.toLong(), tag[0], tag.getOrNull(1), tag.getOrNull(2), tag.getOrNull(3), tag.getOrNull(4), e.createdAt, e.kind.toLong(), e.pubKey))
                    }
                }
            } else {
                all.add(arrayOf(e.id, e.pubKey, e.createdAt, e.kind.toLong(), e.content, e.sig))
            }
        }
        if (local.isEmpty()) return all
        val row = arrayOfNulls<Array<Any?>>(q.from.size)
        val env = Env(q, row, outer)
        return all.filter { r ->
            row[i] = r
            local.all { eval(it, env) == true }
        }
    }

    /**
     * How [spec] is fetched: an id walk when the query reads only its `id` /
     * `created_at` and the store has one, whole events otherwise. The walk's
     * rows carry a member of the spec's authors / kinds, which is all the
     * predicates it captured read of them.
     */
    private suspend fun fetch(
        spec: ScanSpec,
        sink: (Event) -> Unit,
    ) {
        if (spec.needsOnlyIdsAndTimes) {
            val pubkey = spec.authors?.firstOrNull() ?: ""
            val kind = spec.kinds?.firstOrNull() ?: 0
            if (backend.idsAndTimes(spec) { id, createdAt -> sink(Event(id, pubkey, createdAt, kind, emptyArray(), "", "")) }) return
        }
        backend.events(spec, sink)
    }

    /**
     * The newest [ScanSpec.limit] events, plus every event tied with the
     * oldest of them: the store may break `created_at` ties any way, so the
     * whole boundary group is fetched and the query's ORDER BY picks.
     */
    private suspend fun loadNewest(
        spec: ScanSpec,
        sink: (Event) -> Unit,
    ) {
        val limit = spec.limit ?: return fetch(spec, sink)
        var count = 0
        var oldest = Long.MAX_VALUE
        fetch(spec) {
            count++
            if (it.createdAt < oldest) oldest = it.createdAt
            sink(it)
        }
        if (count >= limit && limit > 0) {
            fetch(spec.withTimeRange(oldest, oldest).also { it.columns = spec.columns }, sink)
        }
    }

    /**
     * For `SELECT … FROM events WHERE <exact> ORDER BY created_at DESC[, …]
     * LIMIT n [OFFSET m]`, ungrouped, the n + m newest events are all the query
     * can read.
     */
    private fun newestFirstLimit(
        q: NqlQuery,
        spec: ScanSpec?,
    ): Int? {
        if (q.from.size != 1 || q.from[0].table != SqlProfile.EVENTS || spec == null || !spec.exact) return null
        if (q.grouped || q.distinct || q.limit == null) return null
        val first = q.orderBy.firstOrNull() ?: return null
        val firstExpr = if (first.output >= 0) q.outputs[first.output].expr else first.expr
        if (!first.descending || columnOf(q, 0, firstExpr) != "created_at") return null
        // A term that runs a subquery or a function could fail on rows the limit would skip.
        if (q.orderBy.any { t -> t.output < 0 && !isPlain(t.expr) }) return null
        val total = (count(q.limit) ?: return null) + (count(q.offset) ?: 0)
        return if (total <= Int.MAX_VALUE) total.toInt() else null
    }

    private fun isPlain(e: NqlExpr) = e is NqlColumnRef || e is NqlLiteral || e is NqlParam

    /**
     * The columns each source of [q] is read for, across the whole query and its
     * subqueries, leaving out [captured] predicates; null for every column.
     */
    private fun columnUsage(
        q: NqlQuery,
        captured: Set<NqlExpr>,
    ): Array<MutableSet<String>?> {
        val usage = Array<MutableSet<String>?>(q.from.size) { HashSet() }
        if (q.star) usage.fill(null)

        fun expr(e: NqlExpr) {
            if (e in captured) return
            if (e is NqlColumnRef && e.owner === q && e.output < 0) usage[e.source]?.add(q.from[e.source].columns[e.index].first)
            e.subqueries().forEach { sub -> sub.walkColumns { r -> if (r.owner === q && r.output < 0) usage[r.source]?.add(q.from[r.source].columns[r.index].first) } }
            e.children().forEach(::expr)
        }
        q.expressions().forEach(::expr)
        return usage
    }

    // ---- Native aggregates --------------------------------------------------

    /** The store's own answer for a single-source aggregate or DISTINCT it can compute exactly, or null. */
    private suspend fun native(
        q: NqlQuery,
        outer: Env?,
    ): List<Array<Any?>>? {
        if (q.from.size != 1 || q.from[0].table == null || q.having != null) return null
        if (!q.grouped && !q.distinct) return null
        if (q.grouped && q.distinct) return null
        val table = q.from[0].table!!
        val spec = ScanAnalyzer({ columnOf(q, 0, it) }, { constant(q, it, outer) }).analyze(table, conjuncts(q.where))
        if (!spec.exact || spec.matchesNothing) return null

        fun bare(e: NqlExpr) = columnOf(q, 0, e)

        val groupBy = ArrayList<String>()
        if (q.grouped) {
            for (t in q.groupBy) {
                val e = if (t is NqlColumnRef && t.output >= 0) q.outputs[t.output].expr else t
                val c = bare(e) ?: return null
                if (c !in groupBy) groupBy.add(c)
            }
        }
        val aggregates = ArrayList<Aggregate>()
        // Output i reads column [picks] i of the store's row.
        val picks = ArrayList<Int>()
        for (o in q.outputs) {
            val c = bare(o.expr)
            if (c != null) {
                if (q.distinct && c !in groupBy) groupBy.add(c)
                picks.add(groupBy.indexOf(c).takeIf { it >= 0 } ?: return null)
            } else {
                val f = o.expr as? NqlCall ?: return null
                if (f.aggregateSlot < 0 || f.distinct || f.name == "avg") return null
                aggregates.add(if (f.star) Aggregate("count", null) else Aggregate(f.name, bare(f.args[0]) ?: return null))
                picks.add(-aggregates.size)
            }
        }
        if (q.aggregates.size != aggregates.size) return null
        if (q.orderBy.any { it.output < 0 }) return null
        val resolved = picks.map { if (it < 0) groupBy.size - it - 1 else it }

        val rows = backend.aggregate(AggregatePlan(spec, groupBy, aggregates)) ?: return null
        val out =
            rows.map { row ->
                Array(resolved.size) { i ->
                    val v = row[resolved[i]]
                    when (q.outputs[i].type) {
                        NqlType.INTEGER -> (v as? Number)?.toLong()
                        NqlType.REAL -> (v as? Number)?.toDouble()
                        else -> v
                    }
                }
            }
        val sorted = if (q.orderBy.isEmpty()) out else out.sortedWith { a, b -> compareKeys(q, q.orderBy.map { a[it.output] }, q.orderBy.map { b[it.output] }) }
        return slice(q, sorted)
    }

    // ---- Joins --------------------------------------------------------------

    private suspend fun join(
        q: NqlQuery,
        sources: List<List<Array<Any?>>>,
        outer: Env?,
    ): List<Array<Array<Any?>?>> {
        val n = q.from.size
        if (n == 0) return listOf(emptyArray())
        var acc: List<Array<Array<Any?>?>> = sources[0].map { r -> arrayOfNulls<Array<Any?>>(n).also { it[0] = r } }
        for (i in 1 until n) {
            val on = q.from[i].on!!
            val left = q.from[i].join == NqlJoin.LEFT
            val right = sources[i]
            val next = ArrayList<Array<Array<Any?>?>>()

            // An equality between the joined-so-far and this source hashes the join.
            var probe: NqlExpr? = null
            var build: NqlExpr? = null
            for (c in conjuncts(on)) {
                if (c !is NqlBinary || c.op != "=" || c.left.subqueries().isNotEmpty() || c.right.subqueries().isNotEmpty()) continue
                val l = q.sourcesOf(c.left)
                val r = q.sourcesOf(c.right)
                if (l.isNotEmpty() && l.all { it < i } && r == setOf(i)) {
                    probe = c.left
                    build = c.right
                } else if (r.isNotEmpty() && r.all { it < i } && l == setOf(i)) {
                    probe = c.right
                    build = c.left
                }
                if (probe != null) break
            }

            // The build side, hashed on its join key; null for a nested loop.
            var hashed: HashMap<Any?, MutableList<Array<Any?>>>? = null
            val asReal = probe != null && build != null && probe.type != build.type
            if (probe != null && build != null) {
                val table = HashMap<Any?, MutableList<Array<Any?>>>()
                val row = arrayOfNulls<Array<Any?>>(n)
                val env = Env(q, row, outer)
                for (r in right) {
                    row[i] = r
                    val k = eval(build, env) ?: continue
                    table.getOrPut(NqlValues.key(k, asReal)) { ArrayList() }.add(r)
                }
                hashed = table
            }

            for (rows in acc) {
                var matched = false
                val candidates =
                    if (hashed == null) {
                        right
                    } else {
                        eval(probe!!, Env(q, rows, outer))?.let { hashed[NqlValues.key(it, asReal)] } ?: emptyList()
                    }
                for (r in candidates) {
                    val pair = rows.copyOf()
                    pair[i] = r
                    if (eval(on, Env(q, pair, outer)) == true) {
                        next.add(pair)
                        matched = true
                    }
                }
                if (left && !matched) next.add(rows.copyOf())
            }
            acc = next
        }
        return acc
    }

    // ---- Expressions --------------------------------------------------------

    private fun lookup(
        ref: NqlColumnRef,
        env: Env?,
    ): Any? {
        var e = env
        while (e != null && e.query !== ref.owner) e = e.parent
        return e?.rows?.get(ref.source)?.get(ref.index)
    }

    private suspend fun subquery(
        sub: NqlQuery,
        env: Env,
    ): List<Array<Any?>> {
        val refs =
            outerColumns.getOrPut(sub) {
                val inside = HashSet<NqlQuery>()
                sub.walkQueries { inside.add(it) }
                val out = ArrayList<NqlColumnRef>()
                sub.walkColumns { if (it.owner !in inside && it.output < 0) out.add(it) }
                out
            }
        val key = refs.map { lookup(it, env) }
        val cache = subqueryResults.getOrPut(sub) { HashMap() }
        return cache[key] ?: execute(sub, env).also { cache[key] = it }
    }

    private suspend fun eval(
        e: NqlExpr,
        env: Env,
    ): Any? =
        when (e) {
            is NqlLiteral -> {
                e.value
            }

            is NqlParam -> {
                params[e.index]
            }

            is NqlColumnRef -> {
                lookup(e, env)
            }

            is NqlNegate -> {
                eval(e.operand, env)?.let { NqlValues.negate(it) }
            }

            is NqlNot -> {
                (eval(e.operand, env) as Boolean?)?.not()
            }

            is NqlBinary -> {
                binary(e, env)
            }

            is NqlIsNull -> {
                (eval(e.expr, env) == null) != e.not
            }

            is NqlBetween -> {
                val x = eval(e.expr, env)
                val lo = eval(e.low, env)
                val hi = eval(e.high, env)
                val ge = if (x == null || lo == null) null else NqlValues.compare(x, lo) >= 0
                val le = if (x == null || hi == null) null else NqlValues.compare(x, hi) <= 0
                val r = and(ge, le)
                if (e.not) r?.not() else r
            }

            is NqlInList -> {
                val x = eval(e.expr, env)
                val r = inValues(x, e.items.map { eval(it, env) })
                if (e.not) r?.not() else r
            }

            is NqlInQuery -> {
                val x = eval(e.expr, env)
                val r = inValues(x, subquery(e.query, env).map { it[0] })
                if (e.not) r?.not() else r
            }

            is NqlLike -> {
                val x = eval(e.expr, env) as String?
                val p = eval(e.pattern, env) as String?
                if (x == null || p == null) null else NqlValues.like(x, p) != e.not
            }

            is NqlCase -> {
                var r: Any? = null
                var hit = false
                for ((c, v) in e.whens) {
                    if (eval(c, env) == true) {
                        r = eval(v, env)
                        hit = true
                        break
                    }
                }
                if (hit) r else e.elseExpr?.let { eval(it, env) }
            }

            is NqlCast -> {
                eval(e.expr, env)?.let { NqlValues.cast(it, e.target) }
            }

            is NqlCall -> {
                call(e, env)
            }

            is NqlExists -> {
                subquery(e.query, env).isNotEmpty()
            }

            is NqlScalarSubquery -> {
                val rows = subquery(e.query, env)
                if (rows.size > 1) NqlValues.fail("a subquery used as a value returned more than one row")
                rows.firstOrNull()?.get(0)
            }
        }

    private fun and(
        a: Boolean?,
        b: Boolean?,
    ): Boolean? =
        when {
            a == false || b == false -> false
            a == null || b == null -> null
            else -> true
        }

    private fun inValues(
        x: Any?,
        items: List<Any?>,
    ): Boolean? {
        if (x == null) return null
        var sawNull = false
        for (v in items) {
            if (v == null) {
                sawNull = true
            } else if (NqlValues.equal(x, v)) {
                return true
            }
        }
        return if (sawNull) null else false
    }

    private suspend fun binary(
        e: NqlBinary,
        env: Env,
    ): Any? {
        when (e.op) {
            "AND" -> {
                val a = eval(e.left, env) as Boolean?
                if (a == false) return false
                return and(a, eval(e.right, env) as Boolean?)
            }

            "OR" -> {
                val a = eval(e.left, env) as Boolean?
                if (a == true) return true
                val b = eval(e.right, env) as Boolean?
                return when {
                    b == true -> true
                    a == null || b == null -> null
                    else -> false
                }
            }
        }
        val a = eval(e.left, env) ?: return null
        val b = eval(e.right, env) ?: return null
        return when (e.op) {
            "+" -> NqlValues.add(a, b)
            "-" -> NqlValues.subtract(a, b)
            "*" -> NqlValues.multiply(a, b)
            "/" -> NqlValues.divide(a, b)
            "%" -> NqlValues.remainder(a as Long, b as Long)
            "||" -> (a as String) + (b as String)
            "=" -> NqlValues.equal(a, b)
            "<>" -> !NqlValues.equal(a, b)
            "<" -> NqlValues.compare(a, b) < 0
            "<=" -> NqlValues.compare(a, b) <= 0
            ">" -> NqlValues.compare(a, b) > 0
            ">=" -> NqlValues.compare(a, b) >= 0
            else -> throw IllegalStateException(e.op)
        }
    }

    private suspend fun call(
        e: NqlCall,
        env: Env,
    ): Any? {
        if (e.aggregateSlot >= 0) {
            var x: Env? = env
            while (x != null && x.query?.aggregates?.getOrNull(e.aggregateSlot) !== e) x = x.parent
            return x?.aggregates?.get(e.aggregateSlot)
        }
        when (e.name) {
            "coalesce" -> {
                for (a in e.args) eval(a, env)?.let { return it }
                return null
            }

            "nullif" -> {
                val x = eval(e.args[0], env) ?: return null
                val y = eval(e.args[1], env)
                return if (y != null && NqlValues.equal(x, y)) null else x
            }
        }
        val args = ArrayList<Any>(e.args.size)
        for (a in e.args) args.add(eval(a, env) ?: return null)
        return when (e.name) {
            "length" -> NqlValues.length(args[0] as String)
            "lower" -> NqlValues.lower(args[0] as String)
            "upper" -> NqlValues.upper(args[0] as String)
            "trim" -> NqlValues.trim(args[0] as String, args.getOrNull(1) as String? ?: " ", start = true, end = true)
            "ltrim" -> NqlValues.trim(args[0] as String, args.getOrNull(1) as String? ?: " ", start = true, end = false)
            "rtrim" -> NqlValues.trim(args[0] as String, args.getOrNull(1) as String? ?: " ", start = false, end = true)
            "replace" -> NqlValues.replace(args[0] as String, args[1] as String, args[2] as String)
            "instr" -> NqlValues.instr(args[0] as String, args[1] as String)
            "substr" -> NqlValues.substr(args[0] as String, args[1] as Long, args.getOrNull(2) as Long?)
            "abs" -> NqlValues.abs(args[0])
            "pow" -> NqlValues.pow(args[0], args[1])
            else -> NqlValues.math(e.name, args[0])
        }
    }

    companion object {
        /** Join keys per store call when a source is fetched by another's keys. */
        const val JOIN_KEY_CHUNK = 500
    }
}
