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
package com.vitorpamplona.jsonbench

import java.io.File
import java.lang.management.ManagementFactory
import kotlin.system.exitProcess

/**
 * jsonBench JVM harness. Follows jsonBench/HARNESS.md.
 *
 * The process started by the user is the *parent*: it never parses an event. For each
 * parser it forks a fresh *child* JVM (same java binary, classpath and JVM flags) that
 * verifies and times that one parser on every selected workload and writes its result
 * rows to a temp file; the parent merges them into one result file. One JVM per parser
 * keeps one library's JIT profile, loaded classes and heap from taxing the next.
 */
class Args(
    var corpus: String = "jsonBench/.corpus",
    var out: String = "jsonBench/results/jvm.json",
    var workloads: List<String>? = null,
    var parsers: List<String>? = null,
    var warmupMs: Long = 1000,
    var minTimeMs: Long = 2000,
    var minRounds: Int = 5,
    var list: Boolean = false,
    var childParser: String? = null,
    var childOut: String? = null,
) {
    fun settings() = Settings(warmupMs, minTimeMs, minRounds)

    companion object {
        fun parse(argv: Array<String>): Args {
            val a = Args()
            var i = 0

            fun next(): String {
                if (i + 1 >= argv.size) usage("missing value for ${argv[i]}")
                return argv[++i]
            }
            while (i < argv.size) {
                when (val arg = argv[i]) {
                    "--corpus" -> a.corpus = next()
                    "--out" -> a.out = next()
                    "--workloads" -> a.workloads = next().split(',').map { it.trim() }.filter { it.isNotEmpty() }
                    "--parsers" -> a.parsers = next().split(',').map { it.trim() }.filter { it.isNotEmpty() }
                    "--warmup-ms" -> a.warmupMs = next().toLong()
                    "--min-time-ms" -> a.minTimeMs = next().toLong()
                    "--min-rounds" -> a.minRounds = next().toInt()
                    "--quick" -> {
                        a.warmupMs = 200
                        a.minTimeMs = 300
                        a.minRounds = 3
                    }
                    "--list" -> a.list = true
                    "--child-parser" -> a.childParser = next()
                    "--child-out" -> a.childOut = next()
                    "-h", "--help" -> usage(null)
                    else -> usage("unknown argument: $arg")
                }
                i++
            }
            return a
        }

        fun usage(error: String?): Nothing {
            if (error != null) System.err.println("jsonbench: $error")
            System.err.println(
                """
                usage: jsonbench [--corpus DIR] [--out FILE] [--workloads a,b] [--parsers x,y]
                                 [--warmup-ms N] [--min-time-ms N] [--min-rounds N] [--quick] [--list]
                """.trimIndent(),
            )
            exitProcess(if (error == null) 0 else 2)
        }
    }
}

class Workload(
    val name: String,
    val file: String,
    val events: Int,
    val bytes: Long,
    val digest: String,
)

class Manifest(
    val fingerprint: String,
    val workloads: List<Workload>,
) {
    companion object {
        fun load(corpusDir: File): Manifest {
            val f = File(corpusDir, "manifest.json")
            if (!f.exists()) {
                System.err.println("jsonbench: $f not found; build it with: python3 jsonBench/corpus/prepare.py")
                exitProcess(2)
            }

            @Suppress("UNCHECKED_CAST")
            val m = MiniJson.parse(f.readText()) as Map<String, Any?>

            @Suppress("UNCHECKED_CAST")
            val ws =
                (m["workloads"] as List<Map<String, Any?>>).map {
                    Workload(
                        it["name"] as String,
                        it["file"] as String,
                        (it["events"] as Number).toInt(),
                        (it["bytes"] as Number).toLong(),
                        it["digest"] as String,
                    )
                }
            return Manifest(m["fingerprint"] as? String ?: "", ws)
        }
    }
}

fun main(argv: Array<String>) {
    val args = Args.parse(argv)
    if (args.list) {
        PARSERS.forEach { println(it.id) }
        return
    }
    if (args.childParser != null) {
        Child.run(args)
    } else {
        Parent.run(args)
    }
}

fun selectWorkloads(
    manifest: Manifest,
    args: Args,
): List<Workload> {
    val wanted = args.workloads ?: return manifest.workloads
    val unknown = wanted.filter { w -> manifest.workloads.none { it.name == w } }
    if (unknown.isNotEmpty()) Args.usage("unknown workload(s): ${unknown.joinToString()}")
    return manifest.workloads.filter { it.name in wanted }
}

/** Splits an NDJSON file into its lines (without the '\n'). */
fun readLines(file: File): List<ByteArray> {
    val all = file.readBytes()
    val out = ArrayList<ByteArray>()
    var start = 0
    for (i in all.indices) {
        if (all[i] == '\n'.code.toByte()) {
            if (i > start) out.add(all.copyOfRange(start, i))
            start = i + 1
        }
    }
    if (start < all.size) out.add(all.copyOfRange(start, all.size))
    return out
}

object Child {
    fun run(args: Args) {
        val spec = PARSERS.firstOrNull { it.id == args.childParser } ?: Args.usage("unknown parser ${args.childParser}")
        val corpusDir = File(args.corpus)
        val manifest = Manifest.load(corpusDir)
        val workloads = selectWorkloads(manifest, args)
        val settings = args.settings()

        val parser = spec.create()
        val rows = workloads.map { w -> runWorkload(spec, parser, w, corpusDir, settings) }

        File(args.childOut!!).writeText(MiniJson.write(rows).toString())
        // Printing the sink keeps every round's result observable.
        System.err.println("    (sink ${Runner.blackhole})")
    }

    private fun <I : Any> runWorkload(
        spec: ParserSpec,
        parser: BenchParser<I>,
        w: Workload,
        corpusDir: File,
        settings: Settings,
    ): Map<String, Any?> {
        val lines = readLines(File(corpusDir, w.file))
        check(lines.size == w.events) { "${w.file}: ${lines.size} lines, manifest says ${w.events}" }
        val bytes = lines.sumOf { it.size.toLong() }
        check(bytes == w.bytes) { "${w.file}: $bytes bytes, manifest says ${w.bytes}" }

        val inputs = parser.prepareAll(lines)

        val v = Runner.verify(parser, inputs)
        val digestOk: Boolean? = if (v.digest == null && v.errors == 0) null else v.errors == 0 && v.digest == w.digest
        var error = v.firstError
        if (error == null && digestOk == false) error = "digest mismatch: got ${v.digest}, expected ${w.digest}"

        var m: Measurement? = null
        if (v.errors == 0) {
            try {
                m = Runner.measure(parser, inputs, settings)
            } catch (t: Throwable) {
                error = "while timing: ${t.javaClass.name}: ${t.message?.take(300)}"
            }
        }

        val row = LinkedHashMap<String, Any?>()
        row["parser"] = spec.id
        row["library"] = spec.library
        row["version"] = spec.version
        row["license"] = spec.license
        row["mode"] = spec.mode
        row["input"] = spec.input
        row["notes"] = spec.notes
        row["workload"] = w.name
        row["events"] = w.events
        row["bytes"] = w.bytes
        row["digest_ok"] = digestOk
        row["errors"] = v.errors
        row["error"] = error
        row["rounds"] = m?.rounds ?: 0
        row["median_ns"] = m?.medianNs
        row["min_ns"] = m?.minNs
        row["p90_ns"] = m?.p90Ns
        if (m != null) {
            val events = w.events.toDouble()
            row["ns_per_event"] = m.medianNs / events
            row["mb_per_s"] = w.bytes / (m.medianNs / 1e9) / 1e6
            row["events_per_s"] = events / (m.medianNs / 1e9)
            row["alloc_bytes_per_event"] = m.allocBytes?.let { it / (events * m.rounds) }
        } else {
            row["ns_per_event"] = null
            row["mb_per_s"] = null
            row["events_per_s"] = null
            row["alloc_bytes_per_event"] = null
        }
        row["allocs_per_event"] = null
        row["gc_count"] = m?.gcCount
        row["gc_ms"] = m?.gcMs

        val status =
            when (digestOk) {
                true -> "digest ok"
                null -> "baseline"
                false -> "FAILED: $error"
            }
        if (m != null) {
            System.err.println(
                String.format(
                    java.util.Locale.ROOT,
                    "  %-26s %-10s %9.1f MB/s %10.0f ns/ev %9.0f B/ev  %4d rounds  gc %d/%dms  %s",
                    spec.id,
                    w.name,
                    row["mb_per_s"] as Double,
                    row["ns_per_event"] as Double,
                    (row["alloc_bytes_per_event"] as Double?) ?: Double.NaN,
                    m.rounds,
                    m.gcCount ?: -1,
                    m.gcMs ?: -1,
                    status,
                ),
            )
        } else {
            System.err.println("  ${spec.id.padEnd(26)} ${w.name.padEnd(10)} not timed  $status")
        }
        return row
    }
}

object Parent {
    fun run(args: Args) {
        val corpusDir = File(args.corpus)
        val manifest = Manifest.load(corpusDir)
        val workloads = selectWorkloads(manifest, args)

        val specs =
            args.parsers?.let { wanted ->
                val unknown = wanted.filter { id -> PARSERS.none { it.id == id } }
                if (unknown.isNotEmpty()) Args.usage("unknown parser(s): ${unknown.joinToString()} (see --list)")
                PARSERS.filter { it.id in wanted }
            } ?: PARSERS

        val runtime = ManagementFactory.getRuntimeMXBean()
        // inputArguments also lists what the JVM picked up from JAVA_TOOL_OPTIONS; the child
        // inherits the environment and picks those up itself, so they are not repeated.
        val toolOptions = System.getenv("JAVA_TOOL_OPTIONS")?.split(Regex("\\s+"))?.toSet() ?: emptySet()
        val jvmArgs =
            runtime.inputArguments.filterNot {
                it in toolOptions || it.startsWith("-agentlib:") || it.startsWith("-javaagent:")
            }
        val java =
            ProcessHandle
                .current()
                .info()
                .command()
                .orElse(File(System.getProperty("java.home"), "bin/java").path)
        val classpath = System.getProperty("java.class.path")

        val gcNames = ManagementFactory.getGarbageCollectorMXBeans().map { it.name }
        val gc = gcName(gcNames)
        val shownArgs = jvmArgs.filter { it.startsWith("-Xm") || it.startsWith("-XX:") || it.startsWith("--add-modules") }
        val vmName = System.getProperty("java.vm.name")
        val runtimeString =
            "${System.getProperty("java.vm.vendor")} $vmName ${System.getProperty("java.runtime.version")}, " +
                "GC $gc, ${shownArgs.joinToString(" ")}, one JVM per parser"

        System.err.println("jsonbench (jvm): ${specs.size} parsers x ${workloads.size} workloads; $runtimeString")

        val results = ArrayList<Any?>()
        for (spec in specs) {
            System.err.println("== ${spec.id}")
            val tmp = File.createTempFile("jsonbench-${spec.id}-", ".json")
            try {
                val cmd = ArrayList<String>()
                cmd += java
                cmd += jvmArgs
                cmd += listOf("-cp", classpath, "com.vitorpamplona.jsonbench.MainKt")
                cmd += listOf("--child-parser", spec.id, "--child-out", tmp.path, "--corpus", corpusDir.path)
                cmd += listOf("--workloads", workloads.joinToString(",") { it.name })
                cmd += listOf("--warmup-ms", args.warmupMs.toString(), "--min-time-ms", args.minTimeMs.toString())
                cmd += listOf("--min-rounds", args.minRounds.toString())
                val code =
                    ProcessBuilder(cmd)
                        .redirectOutput(ProcessBuilder.Redirect.INHERIT)
                        .redirectError(ProcessBuilder.Redirect.INHERIT)
                        .start()
                        .waitFor()
                val rows = if (code == 0 && tmp.length() > 0) MiniJson.parse(tmp.readText()) as? List<*> else null
                if (rows != null) {
                    results.addAll(rows)
                } else {
                    System.err.println("  child JVM for ${spec.id} exited with code $code")
                    workloads.forEach { results.add(failedRow(spec, it, "child JVM exited with code $code")) }
                }
            } finally {
                if (!tmp.delete() && tmp.exists()) System.err.println("  could not delete $tmp")
            }
        }

        val out = LinkedHashMap<String, Any?>()
        out["harness"] = "jvm"
        out["language"] = "Kotlin/Java (JVM)"
        out["runtime"] = runtimeString
        out["jvm"] =
            linkedMapOf(
                "vm" to vmName,
                "vendor" to System.getProperty("java.vm.vendor"),
                "version" to System.getProperty("java.runtime.version"),
                "gc" to gc,
                "gc_beans" to gcNames,
                "args" to jvmArgs,
            )
        out["host"] =
            linkedMapOf(
                "cpu" to cpuModel(),
                "cores" to Runtime.getRuntime().availableProcessors(),
                "os" to "${System.getProperty("os.name")} ${System.getProperty("os.version")}",
            )
        out["settings"] =
            linkedMapOf("warmup_ms" to args.warmupMs, "min_time_ms" to args.minTimeMs, "min_rounds" to args.minRounds)
        out["corpus_fingerprint"] = manifest.fingerprint
        out["results"] = results

        val outFile = File(args.out)
        outFile.absoluteFile.parentFile?.let { if (!it.isDirectory && !it.mkdirs()) error("could not create $it") }
        outFile.writeText(MiniJson.write(out).append('\n').toString())
        System.err.println("wrote ${outFile.path}")
    }

    private fun failedRow(
        spec: ParserSpec,
        w: Workload,
        error: String,
    ): Map<String, Any?> =
        linkedMapOf(
            "parser" to spec.id,
            "library" to spec.library,
            "version" to spec.version,
            "license" to spec.license,
            "mode" to spec.mode,
            "input" to spec.input,
            "notes" to spec.notes,
            "workload" to w.name,
            "events" to w.events,
            "bytes" to w.bytes,
            "digest_ok" to if (spec.mode == "baseline") null else false,
            "errors" to 0,
            "error" to error,
            "rounds" to 0,
            "median_ns" to null,
            "min_ns" to null,
            "p90_ns" to null,
            "ns_per_event" to null,
            "mb_per_s" to null,
            "events_per_s" to null,
            "alloc_bytes_per_event" to null,
            "allocs_per_event" to null,
            "gc_count" to null,
            "gc_ms" to null,
        )

    private fun gcName(beans: List<String>): String =
        when {
            beans.any { it.startsWith("G1") } -> "G1"
            beans.any { it.startsWith("ZGC") } -> "ZGC"
            beans.any { it.startsWith("Shenandoah") } -> "Shenandoah"
            beans.any { it.startsWith("PS ") } -> "Parallel"
            beans.any { it == "Copy" || it == "MarkSweepCompact" } -> "Serial"
            beans.any { it.startsWith("Epsilon") } || beans.isEmpty() -> "Epsilon/none"
            else -> beans.joinToString("+")
        }

    private fun cpuModel(): String =
        try {
            File("/proc/cpuinfo")
                .readLines()
                .firstOrNull { it.startsWith("model name") }
                ?.substringAfter(':')
                ?.trim()
        } catch (_: Exception) {
            null
        } ?: System.getProperty("os.arch")
}
