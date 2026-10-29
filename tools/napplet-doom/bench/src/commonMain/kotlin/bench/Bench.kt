package bench

import kotlin.math.roundToLong
import kotlin.time.TimeSource

interface DoomEngine {
    /** Writes the IWAD's file name where dg_start() reads it; doomgeneric identifies the game partly by it. */
    fun setIwadName(name: String)

    fun start()

    fun tick()
}

class WasmRuntime(val name: String, val create: (ByteArray, DoomHost) -> DoomEngine)

class BenchResult(
    val runtime: String,
    val loadMs: Double,
    val startMs: Double,
    val ticNanos: LongArray,
    val hashes: List<Pair<Int, String>>,
    val error: String?,
)

/** A 35 Hz game has this long to run each tic. */
const val TIC_BUDGET_NANOS = 1_000_000_000L / 35

/** The first 10 s of game time is reported separately: it holds the JIT warm-up. */
const val WARMUP_TICS = 350

private val CHECKPOINTS = listOf(175, 350, 700, 1050, 1400, 2100, 3500)

/**
 * Loads doom.wasm, starts the engine, and runs [tics] game tics of the title and demo loop,
 * timing each dg_tick(). The clock is virtual (see [DoomHost]), so the work is identical on every
 * runtime and the frame hashes at the checkpoints must agree.
 */
fun runBench(runtime: WasmRuntime, wasm: ByteArray, wad: ByteArray, wadName: String, tics: Int): BenchResult {
    val host = DoomHost(wad, wadName)
    val clock = TimeSource.Monotonic
    val ticNanos = LongArray(tics)
    val hashes = ArrayList<Pair<Int, String>>()
    var loadMs = 0.0
    var startMs = 0.0
    var error: String? = null
    try {
        val loadMark = clock.markNow()
        val engine = runtime.create(wasm, host)
        engine.setIwadName(wadName)
        loadMs = loadMark.elapsedNow().inWholeMicroseconds / 1000.0

        val startMark = clock.markNow()
        engine.start()
        startMs = startMark.elapsedNow().inWholeMicroseconds / 1000.0

        val firstTic = host.currentTic()
        for (i in 0 until tics) {
            host.setTic(firstTic + i + 1)
            val mark = clock.markNow()
            engine.tick()
            ticNanos[i] = mark.elapsedNow().inWholeNanoseconds
            if (i + 1 in CHECKPOINTS) hashes += (i + 1) to host.frameHash()
        }
    } catch (e: Throwable) {
        error = "${e::class.simpleName}: ${e.message}\n" + host.log.takeLast(8).joinToString("\n")
    }
    return BenchResult(runtime.name, loadMs, startMs, ticNanos, hashes, error)
}

private fun ms(nanos: Double) = ((nanos / 1e6) * 100).roundToLong() / 100.0

private fun percentile(sorted: LongArray, p: Double): Long = sorted[((sorted.size - 1) * p).toInt()]

private fun row(label: String, sample: LongArray): String {
    if (sample.isEmpty()) return "| $label | - | - | - | - | - | - |"
    val sorted = sample.sortedArray()
    val over = sample.count { it > TIC_BUDGET_NANOS }
    return "| $label | ${ms(sample.average())} | ${ms(percentile(sorted, 0.5).toDouble())} | " +
        "${ms(percentile(sorted, 0.95).toDouble())} | ${ms(percentile(sorted, 0.99).toDouble())} | " +
        "${ms(sorted.last().toDouble())} | ${(over * 1000 / sample.size) / 10.0}% |"
}

fun report(results: List<BenchResult>): String = buildString {
    appendLine("Times are milliseconds per dg_tick() (one 35 Hz game tic plus its frame); the budget is ${ms(TIC_BUDGET_NANOS.toDouble())} ms.")
    appendLine()
    appendLine("| Runtime | load | start | tics run |")
    appendLine("|---|---|---|---|")
    for (r in results) {
        val ran = r.ticNanos.count { it > 0 }
        appendLine("| ${r.runtime} | ${r.loadMs} | ${r.startMs} | $ran |")
    }
    appendLine()
    appendLine("| Runtime, tics | mean | p50 | p95 | p99 | max | over budget |")
    appendLine("|---|---|---|---|---|---|---|")
    for (r in results) {
        val ran = r.ticNanos.copyOf(r.ticNanos.count { it > 0 })
        appendLine(row("${r.runtime}, first $WARMUP_TICS", ran.copyOf(minOf(WARMUP_TICS, ran.size))))
        appendLine(row("${r.runtime}, after", if (ran.size > WARMUP_TICS) ran.copyOfRange(WARMUP_TICS, ran.size) else LongArray(0)))
    }
    appendLine()
    appendLine("Frame hashes at tics (must match across runtimes):")
    for (r in results) {
        appendLine("  ${r.runtime}: " + r.hashes.joinToString(" ") { (tic, hash) -> "$tic=${hash.take(8)}" })
        r.error?.let { appendLine("  ${r.runtime} FAILED: $it") }
    }
}
