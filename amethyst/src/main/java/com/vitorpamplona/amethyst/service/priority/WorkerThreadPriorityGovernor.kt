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
package com.vitorpamplona.amethyst.service.priority

import android.content.Context
import android.os.Process
import android.provider.Settings
import com.vitorpamplona.quartz.utils.Log
import java.io.File

/**
 * Demotes the app's network/ingest worker threads below the UI thread so a cold-start relay storm
 * cannot starve the main thread out of its frames.
 *
 * **Why this exists.** On a cold start the outbox model dials ~190 relays at once and the process
 * grows to ~500 threads (OkHttp's TaskRunner pool, OkHttp dispatchers, the kotlinx scheduler and
 * Arti's tokio workers). Every one of them is born at nice 0. The main thread is nice -10, but a
 * single -10 thread against ~30 simultaneously-runnable nice-0 threads on 4 cores still loses about
 * half of its schedulable time to the runqueue — long enough that the first feed frame takes
 * multiple seconds and the "Loading account" screen stays on-screen well after the account itself
 * has loaded.
 *
 * **Why a /proc sweep instead of thread factories.** The largest pool by far is OkHttp's
 * `TaskRunner` backend, a process-wide singleton whose thread factory OkHttp does not expose per
 * client, so there is no injection point to set a priority at creation time. Sweeping
 * `/proc/self/task` catches every pool uniformly — including threads OkHttp renames after the host
 * they are currently serving. A nice value is per-OS-thread and survives renaming, so seeing a
 * thread once is enough; the sweep only has to be frequent enough to catch newly-spawned ones.
 *
 * Note that [Thread.setPriority] does NOT map to a Linux nice level on Android — only
 * [Process.setThreadPriority] does. (`AudioTrackPlayer` documents the same trap for audio.)
 *
 * **Scope.** [DENYLIST] holds the threads that must keep their scheduling: the main thread,
 * RenderThread (it draws the frames we are trying to protect), the ART daemons (demoting
 * HeapTaskDaemon would make the GC pressure *worse*, not better) and binder threads (IPC replies
 * the system waits on). Everything else is app work that should yield to the UI.
 *
 * **Runtime control.** The target nice level is read from a `Settings.Global` key so the effect can
 * be A/B-measured on one build:
 * ```
 * adb shell settings put global amethyst_worker_nice 9    # demote workers (diagnostic only)
 * adb shell settings delete global amethyst_worker_nice   # control (no-op)
 * ```
 *
 * **Measured 2026-08-17 (4-round round-robin sweeps on two rigs) — this does NOT fix the stall on
 * real hardware, which is why it ships disabled.** The mechanism works everywhere: starvation tracks
 * the CFS weight monotonically and roughly halves (emulator 50.2% -> 24.8% at nice 9; SM-T220 15.2%
 * -> 8.1%). But the user-visible time-to-first-paint does not reliably improve on device — the
 * paired deltas were non-monotonic across nice levels, i.e. noise.
 *
 * The two rigs disagree because the bottleneck is not the same on both. On a 4-core emulator the
 * main thread is only 27% busy and genuinely starved; on the SM-T220 it is **70% busy** and
 * saturated with its own work (~42s of it), so scheduling was never the constraint. Raising priority
 * there did hand main more CPU (41.9s -> 47.6s on-cpu) and the stall did not move. Treat an emulator
 * as unable to answer scheduling questions: its shared cores manufacture contention real devices do
 * not have.
 *
 * Kept as a diagnostic knob for the scheduling half of the problem. The larger, still-untested lever
 * is bounding the ~190-relay connect fan-out.
 *
 * Despite AOSP's `androidSetThreadPriority` calling `set_sched_policy(SP_BACKGROUND)` at nice >= 10,
 * no cpuset/schedtune move was observed on real hardware (SM-T220 / Android 14): at nice 5, 9 and 10
 * every worker kept main's exact membership (`schedtune:/top-app`, `cpuset:/top-app`, `cpu:/`) and
 * only the nice value changed. So there is no threshold at 10 to design around — pick the level off
 * the weight/throughput curve above.
 */
object WorkerThreadPriorityGovernor {
    /** `Settings.Global` key holding the target nice level. Absent/invalid = feature off. */
    const val SETTING_KEY = "amethyst_worker_nice"

    /** Sentinel for "not configured" — the governor stays off and costs nothing. */
    private const val DISABLED = Int.MIN_VALUE

    /** Sweep cadence while the cold-start storm is spawning threads. */
    private const val BURST_INTERVAL_MS = 250L

    /** How long to sweep aggressively before backing off to [IDLE_INTERVAL_MS]. */
    private const val BURST_DURATION_MS = 120_000L

    private const val IDLE_INTERVAL_MS = 2_000L

    /**
     * Threads whose scheduling must not be touched. Matched as prefixes against the kernel `comm`
     * (which the kernel caps at 15 characters, so these are deliberately short).
     */
    private val DENYLIST =
        listOf(
            // Draws the frames this whole exercise is meant to protect.
            "RenderThread",
            "hwuiTask",
            "GPU completion",
            // ART daemons — demoting the GC would deepen the very stalls we are fixing.
            "HeapTaskDaemon",
            "ReferenceQueueD",
            "FinalizerDaemon",
            "FinalizerWatchd",
            "Signal Catcher",
            "Jit thread pool",
            "Runtime worker",
            "perfetto_hprof",
            // Debugger/profiler plumbing.
            "ADB-JDWP",
            "JDWP",
            // Synchronous IPC the system framework blocks on.
            "binder:",
        )

    @Volatile private var started = false

    fun startIfConfigured(context: Context) {
        if (started) return
        val targetNice = readTargetNice(context)
        if (targetNice == DISABLED) {
            Log.i("ThreadPriority") { "Worker thread governor off (no $SETTING_KEY setting)" }
            return
        }
        started = true
        Log.i("ThreadPriority") { "Worker thread governor ON, target nice=$targetNice" }

        Thread({ sweepLoop(targetNice) }, "worker-nice-governor")
            .apply {
                isDaemon = true
                start()
            }
    }

    private fun readTargetNice(context: Context): Int =
        runCatching {
            Settings.Global.getInt(context.contentResolver, SETTING_KEY, DISABLED)
        }.getOrDefault(DISABLED)

    private fun sweepLoop(targetNice: Int) {
        // The governor must keep running while the pools it polices saturate the CPU, so it runs
        // slightly above default rather than as background work.
        runCatching { Process.setThreadPriority(Process.THREAD_PRIORITY_FOREGROUND) }

        val startedAt = System.currentTimeMillis()
        val mainTid = Process.myPid()
        // A thread's nice survives renaming, so once demoted it never needs revisiting.
        // Seeding with our own tid keeps the sweep from demoting the governor itself.
        val alreadyDemoted = HashSet<Int>().apply { add(Process.myTid()) }

        while (true) {
            val demoted = sweepOnce(mainTid, targetNice, alreadyDemoted)
            val elapsed = System.currentTimeMillis() - startedAt
            if (demoted > 0) {
                Log.d("ThreadPriority") { "Demoted $demoted thread(s) to nice $targetNice" }
            }
            runCatching {
                Thread.sleep(if (elapsed < BURST_DURATION_MS) BURST_INTERVAL_MS else IDLE_INTERVAL_MS)
            }.onFailure { return }
        }
    }

    private fun sweepOnce(
        mainTid: Int,
        targetNice: Int,
        alreadyDemoted: MutableSet<Int>,
    ): Int {
        val tasks = File("/proc/self/task").listFiles() ?: return 0
        var demoted = 0
        for (task in tasks) {
            val tid = task.name.toIntOrNull() ?: continue
            if (tid == mainTid || tid in alreadyDemoted) continue

            // A thread can exit between listing and reading; treat any failure as "skip".
            val name = runCatching { File(task, "comm").readText().trim() }.getOrNull() ?: continue
            if (DENYLIST.any { name.startsWith(it) }) continue

            val ok = runCatching { Process.setThreadPriority(tid, targetNice) }.isSuccess
            if (ok) {
                alreadyDemoted.add(tid)
                demoted++
            }
        }
        return demoted
    }
}
