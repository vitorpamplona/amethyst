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
package com.vitorpamplona.amethyst.desktop.service.bench

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.withFrameNanos
import coil3.PlatformContext
import coil3.SingletonImageLoader
import com.vitorpamplona.quartz.utils.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.awt.AWTEvent
import java.awt.Component
import java.awt.EventQueue
import java.awt.GraphicsEnvironment
import java.awt.Point
import java.awt.Rectangle
import java.awt.Robot
import java.awt.Toolkit
import java.awt.event.MouseWheelEvent
import java.io.File
import java.lang.management.ManagementFactory
import java.util.Locale
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.locks.LockSupport
import javax.imageio.ImageIO
import javax.swing.JFrame
import javax.swing.SwingUtilities
import kotlin.concurrent.thread
import kotlin.system.exitProcess

/**
 * A scroll benchmark of the running app: `-Damethyst.scrollBench=<dir>` waits for the home feed
 * to load, scrolls it with the mouse wheel at a fixed pace (down, back up, down again), records
 * every frame, writes a report to `<dir>` and exits. Off unless the property is set; only the dev
 * `run` forwards it (desktopApp/build.gradle.kts), and `tools/desktop-run/scroll-bench.sh` drives it.
 *
 * The wheel events are posted to the window as AWT would deliver them, so they go through Compose's
 * own wheel handling and smooth scrolling, as a user's do; nothing reaches into the feed's state.
 * Per frame it records when the frame started ([withFrameNanos]) and how long the UI thread ran
 * since the previous one, timed by an [EventQueue] that wraps every event it dispatches: AWT's
 * event dispatch thread is where Compose composes, lays out and draws. Per pass it also records the
 * garbage collections and JIT compilation that ran, the two costs a JVM adds over native code.
 *
 * After each pass it saves the window as `after-<pass>.png`, to check that the feed moved and did
 * not run out.
 *
 * Passes: `down` scrolls into posts the app has not drawn yet (composing, decoding images),
 * `up` returns over them (what the caches kept), `down-again` repeats the first with everything
 * warm. See desktopApp/plans/2026-11-20-desktop-scroll-benchmark.md.
 */
object ScrollBenchmark {
    private const val TAG = "ScrollBenchmark"

    // Read with its `.waitSeconds`, `.notches`, `.intervalMs` and `.at` settings. The dev `run`
    // forwards only the properties desktopApp/build.gradle.kts lists: add a new one there too.
    const val PROPERTY = "amethyst.scrollBench"

    class Config(
        val outDir: File,
        // How long after the window opens to start: the feed must have loaded its first pages.
        val waitSeconds: Long,
        // Wheel notches per pass, one every [intervalMs].
        val notches: Int,
        val intervalMs: Long,
        // Where in the window to scroll, as fractions of its width and height: the feed's column.
        val atX: Double,
        val atY: Double,
    )

    fun config(): Config? {
        val dir = System.getProperty(PROPERTY)?.takeIf { it.isNotBlank() } ?: return null
        val at =
            System
                .getProperty("$PROPERTY.at")
                ?.split(',')
                ?.mapNotNull { it.trim().toDoubleOrNull() }
                ?.takeIf { it.size == 2 } ?: listOf(0.5, 0.6)
        return Config(
            outDir = File(dir),
            waitSeconds = System.getProperty("$PROPERTY.waitSeconds")?.toLongOrNull() ?: 30,
            notches = System.getProperty("$PROPERTY.notches")?.toIntOrNull() ?: 250,
            intervalMs = System.getProperty("$PROPERTY.intervalMs")?.toLongOrNull() ?: 16,
            atX = at[0],
            atY = at[1],
        )
    }

    private val configured: Config? by lazy { config() }

    /** Whether this run is a benchmark: the image loader then reports to [ImageLoadStats]. */
    val enabled: Boolean get() = configured != null

    /** Runs the benchmark in [window] when the property asks for it; nothing otherwise. */
    @Composable
    fun Run(window: JFrame) {
        val config = configured ?: return
        LaunchedEffect(Unit) {
            try {
                run(window, config)
            } catch (e: CancellationException) {
                // The window's composition went away (a crash rebuilt the window): not a result, and
                // not a reason to quit. The new window's composition runs the benchmark again.
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "The scroll benchmark failed", e)
                writeFile(File(config.outDir, "error.txt"), e.stackTraceToString())
            }
            // A normal exit, on another thread: exitProcess from the UI thread would wait on itself.
            thread(name = "scroll-bench-exit") { exitProcess(0) }
        }
    }

    private enum class Direction(
        val wheel: Int,
    ) {
        DOWN(1),
        UP(-1),
    }

    private suspend fun run(
        window: JFrame,
        config: Config,
    ) {
        Log.i(TAG) { "Scroll benchmark: starting in ${config.waitSeconds} s, ${config.notches} notches every ${config.intervalMs} ms" }
        delay(config.waitSeconds * 1000)

        val queue = TimingEventQueue()
        Toolkit.getDefaultToolkit().systemEventQueue.push(queue)

        val passes = listOf("down" to Direction.DOWN, "up" to Direction.UP, "down-again" to Direction.DOWN)
        val stats = mutableListOf<PassStats>()
        val frames = StringBuilder("pass\tframe\tstart_ms\tinterval_ms\tbusy_ms\n")
        // The image memory cache after each pass: how full it is, and what it holds.
        val cacheLog = StringBuilder("pass\tsize_bytes\tmax_bytes\tentries\n")
        cacheLog.append("start\t").append(memoryCacheState()).append('\n')
        for ((name, direction) in passes) {
            // Lets what the last pass started (image loads, a feed page) settle before the next.
            delay(2_000)
            val pass = measurePass(window, config, name, direction, queue)
            stats += pass
            cacheLog
                .append(name)
                .append('\t')
                .append(memoryCacheState())
                .append('\n')
            // After the pass, not in it: what the window shows, to check that it moved.
            val bounds = Rectangle(window.locationOnScreen, window.size)
            withContext(Dispatchers.IO) { capture(bounds, File(config.outDir, "after-$name.png")) }
            val start = pass.intervalsMs.runningFold(0.0) { at, gap -> at + gap }
            pass.intervalsMs.forEachIndexed { i, gap ->
                frames.append("$name\t${i + 1}\t${ms(start[i + 1])}\t${ms(gap)}\t${ms(pass.busyMs[i])}\n")
            }
            Log.i(TAG) { "Pass $name: ${pass.row().zip(PassStats.HEADER).joinToString(" ") { (v, k) -> "$k=$v" }}" }
        }

        val summary = StringBuilder()
        environment(window, config).forEach { (k, v) -> summary.append("# $k=$v\n") }
        summary.append(PassStats.HEADER.joinToString("\t")).append('\n')
        stats.forEach { summary.append(it.row().joinToString("\t")).append('\n') }
        writeFile(File(config.outDir, "frames.tsv"), frames.toString())
        writeFile(File(config.outDir, "images.tsv"), ImageLoadStats.log())
        writeFile(File(config.outDir, "memory-cache.tsv"), cacheLog.toString())
        // Written last: the script takes its presence as the run being done.
        writeFile(File(config.outDir, "summary.tsv"), summary.toString())
        Log.i(TAG) { "Scroll benchmark written to ${config.outDir}" }
    }

    private suspend fun measurePass(
        window: JFrame,
        config: Config,
        name: String,
        direction: Direction,
        queue: TimingEventQueue,
    ): PassStats =
        coroutineScope {
            val starts = LongArrayList()
            val busy = LongArrayList()
            val gcBefore = gcTotals()
            val jitBefore = jitMillis()
            ImageLoadStats.reset(name)

            var recording = true
            val recorder =
                launch {
                    queue.busyNanos.set(0)
                    // Awaiting every frame keeps the clock ticking while the pass runs, so a frame
                    // that is late shows as a long gap rather than as no frame at all.
                    while (isActive && recording) {
                        withFrameNanos {
                            starts.add(System.nanoTime())
                            busy.add(queue.busyNanos.getAndSet(0))
                        }
                    }
                }

            // Found here, on the UI thread, where Swing's component tree may be read.
            val content = window.contentPane
            val x = (content.width * config.atX).toInt()
            val y = (content.height * config.atY).toInt()
            val target = SwingUtilities.getDeepestComponentAt(content, x, y) ?: content
            val at = SwingUtilities.convertPoint(content, x, y, target)
            withContext(Dispatchers.IO) { scroll(target, at, config, direction) }
            // The smooth scroll's last animation frames.
            delay(500)
            recording = false
            recorder.join()

            val gcAfter = gcTotals()
            PassStats.of(
                name = name,
                startNanos = starts.toArray(),
                busyNanos = busy.toArray(),
                gcCount = gcAfter.first - gcBefore.first,
                gcMs = gcAfter.second - gcBefore.second,
                jitMs = jitMillis() - jitBefore,
                images = ImageLoadStats.snapshot(),
            )
        }

    /** Posts [Config.notches] wheel events at [at] in [target], one every [Config.intervalMs], on schedule. */
    private fun scroll(
        target: Component,
        at: Point,
        config: Config,
        direction: Direction,
    ) {
        val queue = Toolkit.getDefaultToolkit().systemEventQueue
        val interval = config.intervalMs * 1_000_000
        val start = System.nanoTime()
        repeat(config.notches) { i ->
            // Against the start, not the last event: a late wake doesn't slow the whole pass.
            val due = start + i * interval
            while (true) {
                val wait = due - System.nanoTime()
                if (wait <= 0) break
                LockSupport.parkNanos(wait)
            }
            queue.postEvent(
                MouseWheelEvent(
                    target,
                    MouseWheelEvent.MOUSE_WHEEL,
                    System.currentTimeMillis(),
                    0,
                    at.x,
                    at.y,
                    0,
                    false,
                    MouseWheelEvent.WHEEL_UNIT_SCROLL,
                    3,
                    direction.wheel,
                ),
            )
        }
    }

    private fun environment(
        window: JFrame,
        config: Config,
    ): List<Pair<String, String>> {
        val refresh =
            runCatching {
                window.graphicsConfiguration
                    ?.device
                    ?.displayMode
                    ?.refreshRate
            }.getOrNull()
        return listOf(
            "version" to (System.getProperty("amethyst.version") ?: "dev"),
            "java" to "${System.getProperty("java.vm.name")} ${System.getProperty("java.version")}",
            "os" to "${System.getProperty("os.name")} ${System.getProperty("os.arch")}",
            "cores" to Runtime.getRuntime().availableProcessors().toString(),
            "max_heap_mb" to (Runtime.getRuntime().maxMemory() / 1024 / 1024).toString(),
            "render_api" to (System.getProperty("skiko.renderApi") ?: System.getenv("SKIKO_RENDER_API") ?: "default"),
            "headless" to GraphicsEnvironment.isHeadless().toString(),
            "refresh_hz" to (refresh?.toString() ?: "unknown"),
            "window" to "${window.width}x${window.height}",
            "notches" to config.notches.toString(),
            "interval_ms" to config.intervalMs.toString(),
            "wait_s" to config.waitSeconds.toString(),
        )
    }

    private fun memoryCacheState(): String {
        val cache = SingletonImageLoader.get(PlatformContext.INSTANCE).memoryCache ?: return "none"
        return "${cache.size}\t${cache.maxSize}\t${cache.keys.size}"
    }

    private fun gcTotals(): Pair<Long, Long> {
        var count = 0L
        var millis = 0L
        ManagementFactory.getGarbageCollectorMXBeans().forEach { gc ->
            count += gc.collectionCount.coerceAtLeast(0)
            millis += gc.collectionTime.coerceAtLeast(0)
        }
        return count to millis
    }

    private fun jitMillis(): Long =
        ManagementFactory
            .getCompilationMXBean()
            ?.takeIf { it.isCompilationTimeMonitoringSupported }
            ?.totalCompilationTime ?: 0L

    /** A screenshot of [bounds]; skipped where the OS refuses one (macOS without screen recording). */
    private fun capture(
        bounds: Rectangle,
        file: File,
    ) {
        try {
            val dir = file.parentFile
            if (dir != null && !dir.isDirectory && !dir.mkdirs()) return
            ImageIO.write(Robot().createScreenCapture(bounds), "png", file)
        } catch (e: Exception) {
            Log.w(TAG, "Could not save $file", e)
        }
    }

    private fun ms(value: Double): String = String.format(Locale.ROOT, "%.3f", value)

    private fun writeFile(
        file: File,
        text: String,
    ) {
        val dir = file.parentFile
        if (dir != null && !dir.isDirectory && !dir.mkdirs()) {
            Log.w(TAG) { "Could not create $dir" }
            return
        }
        file.writeText(text)
    }

    /**
     * The system event queue, timing what the event dispatch thread runs: [busyNanos] adds up the
     * time spent in each event it dispatches. Nested dispatches (a modal loop) count once, in the
     * outer one.
     */
    private class TimingEventQueue : EventQueue() {
        val busyNanos = AtomicLong()

        // Read and written only on the event dispatch thread.
        private var depth = 0

        override fun dispatchEvent(event: AWTEvent) {
            if (depth > 0) {
                depth++
                try {
                    super.dispatchEvent(event)
                } finally {
                    depth--
                }
                return
            }
            depth = 1
            val start = System.nanoTime()
            try {
                super.dispatchEvent(event)
            } finally {
                depth = 0
                busyNanos.addAndGet(System.nanoTime() - start)
            }
        }
    }

    // Frame starts and busy times without boxing a Long per frame.
    private class LongArrayList {
        private var values = LongArray(1024)
        private var size = 0

        fun add(value: Long) {
            if (size == values.size) values = values.copyOf(size * 2)
            values[size++] = value
        }

        fun toArray(): LongArray = values.copyOf(size)
    }
}
