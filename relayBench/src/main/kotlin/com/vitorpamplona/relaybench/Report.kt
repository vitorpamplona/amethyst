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
package com.vitorpamplona.relaybench

import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.vitorpamplona.relaybench.bench.RelayResult
import com.vitorpamplona.relaybench.bench.SyncBenchmark
import com.vitorpamplona.relaybench.corpus.Corpus
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

/** Everything one benchmark run produced, ready to render. */
class BenchRun(
    val startedAt: ZonedDateTime,
    val corpus: Corpus,
    val options: Options,
    val relays: List<RelayResult>,
    val syncs: List<SyncBenchmark.PairResult>,
)

/**
 * Renders the run three ways: a rich ANSI terminal report (bars, colors,
 * winners), a Markdown report for sharing, and raw JSON for tooling.
 */
object Report {
    private const val BAR_WIDTH = 30

    private var color = System.getenv("NO_COLOR") == null

    fun disableColor() {
        color = false
    }

    private fun paint(
        code: String,
        text: String,
    ) = if (color) "\u001B[${code}m$text\u001B[0m" else text

    private fun bold(t: String) = paint("1", t)

    private fun dim(t: String) = paint("2", t)

    private fun green(t: String) = paint("32;1", t)

    private fun cyan(t: String) = paint("36", t)

    private fun yellow(t: String) = paint("33", t)

    private fun red(t: String) = paint("31;1", t)

    private fun bar(
        value: Double,
        max: Double,
        best: Boolean,
    ): String {
        if (max <= 0 || value.isNaN()) return ""
        val cells = ((value / max) * BAR_WIDTH).toInt().coerceIn(if (value > 0) 1 else 0, BAR_WIDTH)
        val b = "█".repeat(cells) + "░".repeat(BAR_WIDTH - cells)
        return if (best) green(b) else cyan(b)
    }

    private fun fmt(v: Double): String =
        when {
            v >= 1000 -> "%,.0f".format(v)
            v >= 10 -> "%.1f".format(v)
            else -> "%.2f".format(v)
        }

    private fun bytesHuman(b: Long): String =
        when {
            b >= 1 shl 30 -> "%.2f GiB".format(b / 1073741824.0)
            b >= 1 shl 20 -> "%.1f MiB".format(b / 1048576.0)
            b >= 1 shl 10 -> "%.1f KiB".format(b / 1024.0)
            else -> "$b B"
        }

    /**
     * One metric across all relays: name column, bar, value, star on the
     * winner. [higherIsBetter] flips both the star and the bar scaling
     * (for latencies the *shortest* bar wins, so bars stay proportional
     * to the raw value and the star marks the minimum).
     */
    private fun StringBuilder.metricRows(
        rows: List<Pair<String, Double?>>,
        unit: String,
        higherIsBetter: Boolean,
        detail: Map<String, String> = emptyMap(),
    ) {
        val present = rows.mapNotNull { (n, v) -> v?.let { n to it } }
        if (present.isEmpty()) return
        val max = present.maxOf { it.second }
        val bestValue = if (higherIsBetter) present.maxOf { it.second } else present.minOf { it.second }
        val nameWidth = rows.maxOf { it.first.length }
        for ((name, value) in rows) {
            if (value == null) {
                appendLine("    ${name.padEnd(nameWidth)}  ${dim("(failed)")}")
                continue
            }
            val best = value == bestValue && present.size > 1
            val star = if (best) green(" ★") else ""
            val valueText = (fmt(value) + " " + unit).let { if (best) bold(it) else it }
            val extra = detail[name]?.let { "  ${dim(it)}" } ?: ""
            appendLine("    ${name.padEnd(nameWidth)}  ${bar(value, max, best)}  $valueText$star$extra")
        }
    }

    private fun StringBuilder.sectionTitle(title: String) {
        appendLine()
        appendLine(bold("▌ $title"))
    }

    fun terminal(run: BenchRun): String =
        buildString {
            val line = "═".repeat(74)
            appendLine(bold(line))
            appendLine(bold("  RELAY BENCH") + dim("  ·  ${run.startedAt.format(DateTimeFormatter.RFC_1123_DATE_TIME)}"))
            appendLine("  corpus: ${run.corpus.source} — ${"%,d".format(run.corpus.events.size)} events, fingerprint ${run.corpus.fingerprint}")
            val kinds =
                run.corpus
                    .kindHistogram()
                    .entries
                    .sortedByDescending { it.value }
                    .take(8)
                    .joinToString("  ") { "k${it.key}:${"%,d".format(it.value)}" }
            appendLine("  kinds: $kinds")
            appendLine(
                "  relays: " +
                    run.relays.joinToString("  ·  ") { r ->
                        val v = r.info?.let { "${it.software ?: r.name} ${it.version ?: ""}".trim() } ?: r.name
                        if (r.error != null) red("$v (FAILED)") else bold(v)
                    },
            )
            appendLine(bold(line))

            run.relays.filter { it.error != null }.forEach {
                appendLine(red("  ✗ ${it.name} failed: ${it.error?.lineSequence()?.first()}"))
            }

            val ok = run.relays.filter { it.error == null }
            if (ok.isNotEmpty()) {
                sectionTitle("INGEST — receipt ➜ queryable by REQ (idle relay, ${ok.firstNotNullOfOrNull { it.visibility?.samples } ?: 0} samples)")
                metricRows(ok.map { it.name to it.visibility?.visibleLatency?.p50 }, "ms p50", higherIsBetter = false, detail = ok.associate { it.name to "p99 ${fmt(it.visibility?.visibleLatency?.p99 ?: 0.0)} ms" })
                appendLine(dim("    OK-ack latency:"))
                metricRows(ok.map { it.name to it.visibility?.okLatency?.p50 }, "ms p50", higherIsBetter = false, detail = ok.associate { it.name to "p99 ${fmt(it.visibility?.okLatency?.p99 ?: 0.0)} ms" })
                ok.forEach { r ->
                    val pct = (r.visibility?.visibleByOkTime ?: 0.0) * 100
                    appendLine(dim("    ${r.name}: ${"%.0f".format(pct)}% of events were already queryable when their OK arrived"))
                }

                sectionTitle("INGEST — corpus replay throughput (${run.options.publishers} connections, window ${run.options.window})")
                metricRows(
                    ok.map { it.name to it.throughput?.eventsPerSec },
                    "events/s",
                    higherIsBetter = true,
                    detail =
                        ok.associate {
                            it.name to
                                "${"%,d".format(it.throughput?.accepted ?: 0)} accepted, ${"%,d".format(it.throughput?.rejected ?: 0)} rejected, ${fmt((it.throughput?.wallMs ?: 0.0) / 1000)} s"
                        },
                )

                sectionTitle("STORAGE — on-disk footprint after full ingest")
                metricRows(ok.map { it.name to it.storageBytes.toDouble() / 1048576.0 }, "MiB", higherIsBetter = false)

                // ---- queries ----
                val scenarioKeys = ok.flatMap { r -> r.queries.map { it.scenario.key } }.distinct()
                if (scenarioKeys.isNotEmpty()) {
                    sectionTitle("QUERIES — time to EOSE, single client (p50, ${run.options.queryRounds} rounds)")
                    for (key in scenarioKeys) {
                        val results = ok.associateWith { r -> r.queries.find { it.scenario.key == key } }
                        val any = results.values.filterNotNull().firstOrNull() ?: continue
                        val counts =
                            results.values
                                .filterNotNull()
                                .map { it.eventsPerRound }
                                .distinct()
                        val mismatch = if (counts.size > 1) red("  ⚠ result sets differ: $counts") else dim("  ${counts.first()} events")
                        appendLine("  ${bold(key)} ${dim("— " + any.scenario.description)}$mismatch")
                        metricRows(
                            ok.map { it.name to results[it]?.timeToEose?.p50 },
                            "ms",
                            higherIsBetter = false,
                            detail = ok.associate { it.name to "p99 ${fmt(results[it]?.timeToEose?.p99 ?: 0.0)} ms, first event ${fmt(results[it]?.timeToFirst?.p50 ?: 0.0)} ms" },
                        )
                    }

                    sectionTitle("QUERIES — aggregate throughput (${run.options.queryConnections} concurrent connections)")
                    for (key in scenarioKeys) {
                        val results = ok.associateWith { r -> r.queries.find { it.scenario.key == key } }
                        appendLine("  ${bold(key)}")
                        metricRows(ok.map { it.name to results[it]?.concurrentEventsPerSec }, "events/s", higherIsBetter = true)
                    }
                }
            }

            // ---- sync ----
            if (run.syncs.isNotEmpty()) {
                sectionTitle("NIP-77 NEGENTROPY SYNC — pairwise (80%/80% slices, 60% overlap)")
                for (pair in run.syncs) {
                    val status = if (pair.converged) green("converged ✓") else red("DID NOT CONVERGE ✗")
                    appendLine("  ${bold("${pair.serverA} ⇄ ${pair.serverB}")}  ${dim("${"%,d".format(pair.syncableEvents)} syncable events")}  $status")
                    pair.error?.let { appendLine(red("    error: $it")) }
                    if (pair.initialReconcile.isNotEmpty()) {
                        appendLine(dim("    initial reconcile (server side doing the range work):"))
                        metricRows(
                            pair.initialReconcile.map { (name, s) -> name to s.ms },
                            "ms",
                            higherIsBetter = false,
                            detail =
                                pair.initialReconcile.mapValues { (_, s) ->
                                    "${s.rounds} rounds, ${bytesHuman(s.wireBytes)} on the wire, need ${s.needCount} / have ${s.haveCount}" +
                                        (s.error?.let { "  ! $it" } ?: "")
                                },
                        )
                        appendLine(dim("    delta transfer: ${pair.transferredToA} → ${pair.serverA}, ${pair.transferredToB} → ${pair.serverB} in ${fmt(pair.transferMs)} ms"))
                        appendLine(dim("    steady-state reconcile of identical sets:"))
                        metricRows(
                            pair.identicalReconcile.map { (name, s) -> name to s.ms },
                            "ms",
                            higherIsBetter = false,
                            detail = pair.identicalReconcile.mapValues { (_, s) -> "${s.rounds} rounds, ${bytesHuman(s.wireBytes)}" },
                        )
                        if (pair.repeatReconcile.isNotEmpty()) {
                            appendLine(dim("    heartbeat: same reconcile again, no writes in between:"))
                            metricRows(
                                pair.repeatReconcile.map { (name, s) -> name to s.ms },
                                "ms",
                                higherIsBetter = false,
                                detail = pair.repeatReconcile.mapValues { (_, s) -> "${s.rounds} rounds, ${bytesHuman(s.wireBytes)}" },
                            )
                        }
                    }
                }
            }

            // ---- verdict ----
            val okRelays = run.relays.filter { it.error == null }
            if (okRelays.size > 1) {
                sectionTitle("HEAD-TO-HEAD")
                appendLine(headToHead(okRelays))
            }
            appendLine()
            appendLine(bold(line))
        }

    private fun headToHead(relays: List<RelayResult>): String =
        buildString {
            fun crown(
                label: String,
                winner: String?,
                note: String,
            ) {
                if (winner != null) appendLine("    $label: ${green(bold(winner))} ${dim(note)}")
            }

            val ingest = relays.mapNotNull { r -> r.throughput?.let { r.name to it.eventsPerSec } }
            ingest.maxByOrNull { it.second }?.let { (name, eps) ->
                val runnerUp = ingest.filter { it.first != name }.maxByOrNull { it.second }
                val ratio = runnerUp?.let { "%.1f× faster than ${it.first}".format(eps / it.second) } ?: ""
                crown("ingest throughput", name, ratio)
            }
            val visible = relays.mapNotNull { r -> r.visibility?.let { r.name to it.visibleLatency.p50 } }
            visible.minByOrNull { it.second }?.let { (name, ms) ->
                val runnerUp = visible.filter { it.first != name }.minByOrNull { it.second }
                val ratio = runnerUp?.let { "%.1f× lower p50 than ${it.first}".format(it.second / ms) } ?: ""
                crown("receipt➜queryable latency", name, ratio)
            }
            val queryWins = HashMap<String, Int>()
            val keys = relays.flatMap { r -> r.queries.map { it.scenario.key } }.distinct()
            for (key in keys) {
                relays
                    .mapNotNull { r -> r.queries.find { it.scenario.key == key }?.let { r.name to it.timeToEose.p50 } }
                    .minByOrNull { it.second }
                    ?.let { queryWins.merge(it.first, 1, Int::plus) }
            }
            queryWins.maxByOrNull { it.value }?.let { (name, wins) ->
                crown("query latency", name, "fastest EOSE in $wins of ${keys.size} scenarios")
            }
        }

    // ------------------------------------------------------------------
    fun markdown(run: BenchRun): String =
        buildString {
            appendLine("# Relay Bench — ${run.startedAt.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)}")
            appendLine()
            appendLine("- **Corpus**: ${run.corpus.source} — ${"%,d".format(run.corpus.events.size)} events, fingerprint `${run.corpus.fingerprint}`")
            appendLine("- **Relays**: " + run.relays.joinToString(", ") { r -> r.info?.let { "${it.software} ${it.version}" } ?: r.name })
            appendLine("- **Settings**: ${run.options.publishers} publisher conns, window ${run.options.window}, ${run.options.queryRounds} query rounds, ${run.options.queryConnections} concurrent conns")
            appendLine()
            val ok = run.relays.filter { it.error == null }
            run.relays.filter { it.error != null }.forEach { appendLine("> ⚠ **${it.name} failed**: ${it.error?.lineSequence()?.first()}") }

            appendLine("## Ingest")
            appendLine()
            appendLine("| metric | " + ok.joinToString(" | ") { it.name } + " |")
            appendLine("|---|" + ok.joinToString("|") { "---:" } + "|")

            fun row(
                label: String,
                value: (RelayResult) -> String,
            ) = appendLine("| $label | " + ok.joinToString(" | ") { value(it) } + " |")
            row("receipt➜queryable p50 (ms)") { fmt(it.visibility?.visibleLatency?.p50 ?: Double.NaN) }
            row("receipt➜queryable p99 (ms)") { fmt(it.visibility?.visibleLatency?.p99 ?: Double.NaN) }
            row("OK ack p50 (ms)") { fmt(it.visibility?.okLatency?.p50 ?: Double.NaN) }
            row("queryable by OK time") { "%.0f%%".format((it.visibility?.visibleByOkTime ?: 0.0) * 100) }
            row("replay throughput (events/s)") { fmt(it.throughput?.eventsPerSec ?: Double.NaN) }
            row("accepted / rejected") { "${it.throughput?.accepted} / ${it.throughput?.rejected}" }
            row("storage after ingest") { bytesHuman(it.storageBytes) }
            appendLine()

            appendLine("## Queries")
            appendLine()
            appendLine("| scenario | events | " + ok.joinToString(" | ") { "${it.name} EOSE p50 (ms)" } + " | " + ok.joinToString(" | ") { "${it.name} @${run.options.queryConnections}conn (ev/s)" } + " |")
            appendLine("|---|---:|" + ok.joinToString("|") { "---:" } + "|" + ok.joinToString("|") { "---:" } + "|")
            val keys = ok.flatMap { r -> r.queries.map { it.scenario.key } }.distinct()
            for (key in keys) {
                val cells = ok.map { r -> r.queries.find { it.scenario.key == key } }
                val counts = cells.filterNotNull().map { it.eventsPerRound }.distinct()
                val eventsCell = if (counts.size > 1) "⚠ $counts" else "${counts.firstOrNull() ?: "-"}"
                appendLine(
                    "| $key | $eventsCell | " +
                        cells.joinToString(" | ") { fmt(it?.timeToEose?.p50 ?: Double.NaN) } + " | " +
                        cells.joinToString(" | ") { fmt(it?.concurrentEventsPerSec ?: Double.NaN) } + " |",
                )
            }
            appendLine()

            if (run.syncs.isNotEmpty()) {
                appendLine("## NIP-77 negentropy sync")
                appendLine()
                for (pair in run.syncs) {
                    appendLine("### ${pair.serverA} ⇄ ${pair.serverB} — ${if (pair.converged) "converged ✓" else "did not converge ✗"}")
                    appendLine()
                    appendLine("| phase | server | ms | rounds | wire | need/have |")
                    appendLine("|---|---|---:|---:|---:|---|")
                    pair.initialReconcile.forEach { (name, s) ->
                        appendLine("| initial reconcile | $name | ${fmt(s.ms)} | ${s.rounds} | ${bytesHuman(s.wireBytes)} | ${s.needCount}/${s.haveCount} |")
                    }
                    pair.identicalReconcile.forEach { (name, s) ->
                        appendLine("| identical-set reconcile | $name | ${fmt(s.ms)} | ${s.rounds} | ${bytesHuman(s.wireBytes)} | 0/0 |")
                    }
                    appendLine()
                    appendLine("Delta transfer: ${pair.transferredToA} events → ${pair.serverA}, ${pair.transferredToB} → ${pair.serverB} in ${fmt(pair.transferMs)} ms.")
                    appendLine()
                }
            }
        }

    fun json(run: BenchRun): String {
        val mapper = jacksonObjectMapper().enable(SerializationFeature.INDENT_OUTPUT)
        val tree =
            mapOf(
                "startedAt" to run.startedAt.toString(),
                "corpus" to
                    mapOf(
                        "source" to run.corpus.source,
                        "events" to run.corpus.events.size,
                        "fingerprint" to run.corpus.fingerprint,
                        "kinds" to run.corpus.kindHistogram(),
                    ),
                "options" to
                    mapOf(
                        "publishers" to run.options.publishers,
                        "window" to run.options.window,
                        "visibilitySamples" to run.options.visibilitySamples,
                        "queryRounds" to run.options.queryRounds,
                        "queryConnections" to run.options.queryConnections,
                    ),
                "relays" to
                    run.relays.map { r ->
                        mapOf(
                            "name" to r.name,
                            "software" to r.info?.software,
                            "version" to r.info?.version,
                            "error" to r.error,
                            "visibility" to r.visibility,
                            "throughput" to r.throughput,
                            "storageBytes" to r.storageBytes,
                            "queries" to
                                r.queries.map { q ->
                                    mapOf(
                                        "scenario" to q.scenario.key,
                                        "description" to q.scenario.description,
                                        "filter" to q.scenario.filterJson,
                                        "eventsPerRound" to q.eventsPerRound,
                                        "timeToFirst" to q.timeToFirst,
                                        "timeToEose" to q.timeToEose,
                                        "sequentialEventsPerSec" to q.sequentialEventsPerSec,
                                        "concurrentEventsPerSec" to q.concurrentEventsPerSec,
                                    )
                                },
                        )
                    },
                "syncs" to run.syncs,
            )
        return mapper.writeValueAsString(tree)
    }
}
