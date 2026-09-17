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
package com.vitorpamplona.amethyst.service.resourceusage

import android.os.Process
import com.vitorpamplona.amethyst.isDebug
import com.vitorpamplona.quartz.utils.Log

/**
 * Attributes the process's CPU time to a fixed set of subsystem buckets
 * (`cpu.net.bg.ms`, `cpu.dispatch.fg.ms`, …) by diffing per-thread counters out
 * of `/proc/self/task`.
 *
 * **Why this exists.** [ProcessCpuSampler] answers *whether* CPU matters;
 * `plans/2026-09-14-cpu-battery-diagnosis.md` needs *where*. A whole-process
 * `cpu.ms` cannot distinguish "the relay firehose is being parsed with the
 * screen off" from "the user scrolled a feed", and those have opposite fixes.
 * Thread names separate them, and they are readable from inside a release
 * build with no profiler attached — which matters because battery bugs are
 * production-only phenomena (see the 2026-07-12 ping study).
 *
 * **Cost.** One pass over `/proc/self/task` per ledger flush (30s while traffic
 * flows, nothing while idle), piggybacked on the accountant's pre-flush hook
 * exactly like [ProcessCpuSampler] and [BatteryDrainSampler]. Each thread costs
 * one `stat` read per sample plus one `comm` read for its whole life — the
 * classification is cached per tid, so the ~650-thread cold-start storm is paid
 * for once. Buffers are reused ([ProcThreadCpuReader]); the sweep allocates a
 * bounded map, not per-thread garbage.
 *
 * Measured on a 675-thread JVM process: **~4.1ms median per sweep**, plus ~7.6ms
 * once for the names. Expect several times that on a slow ARM device, so call it
 * ~20ms every 30s — under 0.1% of a core, on `Dispatchers.IO`, on threads
 * `WorkerThreadPriorityGovernor` has already demoted to nice 10 so the sweep
 * yields to the UI. This is the most expensive thing the ledger does; it is
 * still the cheapest way to answer where the CPU goes from inside a release
 * build.
 *
 * **Threads that exit are not lost.** A thread that dies between two samples
 * takes its last interval's CPU with it, which would quietly under-report
 * exactly the short-lived-thread churn we are hunting. Instead the sampler also
 * diffs the whole-process counter and books the unattributed remainder to
 * [ThreadCpuBuckets.GONE], so the buckets sum to the process total by
 * construction and the residual is itself a signal.
 *
 * **Privacy.** Thread names are read, matched to a compile-time bucket, and
 * dropped — see [ThreadCpuBuckets], which exists because OkHttp names its
 * threads after the relay host they are serving.
 *
 * Note that pre-flush hooks also run on [ResourceUsageAccountant
 * .allDaysIncludingLive], so a sweep happens on every ledger READ too. That is
 * fine at today's three call sites (opening the usage screen, the alert
 * evaluator, assembling a report) but this is not a method to put behind a
 * poll.
 */
class ThreadCpuSampler(
    private val accountant: ResourceUsageAccountant,
    private val isForeground: () -> Boolean,
    private val reader: ThreadCpuReader = ProcThreadCpuReader(),
    private val processCpuMs: () -> Long = { Process.getElapsedCpuTime() },
    /**
     * The main thread's tid, which on Linux equals the pid.
     *
     * Identified by tid rather than by name because the main thread's `comm` is
     * the truncated process name — it carries no marker a prefix table could
     * match, so without this the UI thread's CPU would silently land in
     * [ThreadCpuBuckets.MISC]. `WorkerThreadPriorityGovernor` picks it out the
     * same way.
     */
    private val mainTid: Int = Process.myPid(),
) {
    /** tid -> bucket. Classified once per tid; pruned when the tid disappears. */
    private val bucketByTid = HashMap<Int, String>()

    /** tid -> last observed cumulative CPU, in clock ticks. */
    private val ticksByTid = HashMap<Int, Long>()

    private var lastProcessCpuMs = 0L
    private var primed = false

    /**
     * Registers the pre-flush hook and takes the baseline immediately, so the
     * first real flush already reports a true delta. Priming here rather than
     * 30s later is deliberate: it is wired up during `AppModules` construction,
     * when few threads exist, and it keeps the cold-start storm — the most
     * expensive window the app has — inside the first measured interval.
     */
    fun register() {
        sample()
        accountant.addPreFlushHook(::sample)
    }

    @Synchronized
    fun sample() {
        val processNowMs = processCpuMs()
        val tids = reader.listTids()

        val perBucketTicks = HashMap<String, Long>(ThreadCpuBuckets.ALL.size)
        var attributedTicks = 0L
        val live = HashSet<Int>(tids.size * 2)

        for (tid in tids) {
            live.add(tid)
            val ticks = reader.readCpuTicks(tid)
            // Exited between the listing and the read — the normal case in a
            // process that spawns hundreds of short-lived socket threads. Its
            // tail is recovered by the GONE residual below.
            if (ticks < 0) continue

            val previous = ticksByTid.put(tid, ticks)

            // Per-thread CPU only ever increases, so a counter that went DOWN
            // means the kernel recycled this tid onto a new thread between two
            // sweeps — the one case pruneDeadThreads cannot catch, because the
            // tid was never absent from a sweep. Treated as a birth: without
            // this the new thread's first delta is negative (and dropped), and
            // it inherits the dead thread's bucket for the rest of its life.
            val recycled = previous != null && ticks < previous
            if (recycled) bucketByTid.remove(tid)

            val bucket =
                bucketByTid.getOrPut(tid) {
                    when {
                        tid == mainTid -> ThreadCpuBuckets.MAIN
                        // A tid with no cached name may have exited too; an unknown
                        // name is still real CPU, so bucket it as MISC rather than drop it.
                        else -> reader.readName(tid)?.let(ThreadCpuBuckets::classify) ?: ThreadCpuBuckets.MISC
                    }
                }

            // A thread we have not seen before was born after the last sample, so
            // its whole lifetime falls inside this interval and counts in full.
            // (On the priming pass nothing is emitted, so process start is not
            // double-counted.)
            val delta = if (previous == null || recycled) ticks else ticks - previous
            if (delta > 0) {
                perBucketTicks[bucket] = (perBucketTicks[bucket] ?: 0L) + delta
                attributedTicks += delta
            }
        }

        pruneDeadThreads(live)

        val processDeltaMs = processNowMs - lastProcessCpuMs
        lastProcessCpuMs = processNowMs

        if (!primed) {
            primed = true
            return
        }

        val visibility = if (isForeground()) UsageKeys.FG else UsageKeys.BG
        var attributedMs = 0L
        for ((bucket, ticks) in perBucketTicks) {
            val ms = ticksToMs(ticks)
            if (ms > 0) {
                accountant.add(UsageKeys.cpuBucket(bucket, visibility), ms)
                attributedMs += ms
            }
        }

        // Whatever the live threads did not account for belongs to threads that
        // have since exited. Negative means the two sources disagree by less
        // than a tick (they are sampled a few microseconds apart); ignore it.
        val goneMs = processDeltaMs - attributedMs
        if (goneMs > 0) {
            accountant.add(UsageKeys.cpuBucket(ThreadCpuBuckets.GONE, visibility), goneMs)
        }

        logUnclassified(tids)
    }

    private fun pruneDeadThreads(live: Set<Int>) {
        // Tids are recycled, so a stale entry would make a brand-new thread
        // inherit a dead one's bucket and counter (producing a large negative
        // delta, which is then silently dropped — i.e. lost CPU). Unconditional
        // rather than guarded on a size comparison: a sample where some `stat`
        // reads raced with thread exits can leave the map smaller than the live
        // set while still holding stale keys.
        ticksByTid.keys.retainAll(live)
        bucketByTid.keys.retainAll(live)
    }

    private fun ticksToMs(ticks: Long): Long = ticks * 1000L / reader.ticksPerSecond

    /**
     * Debug builds only: names the threads that fell into
     * [ThreadCpuBuckets.MISC], which is how the prefix table gets extended when
     * a large `misc` bucket shows up. Never runs in release — these strings can
     * be relay hostnames, and nothing that leaves the device may contain one.
     */
    private fun logUnclassified(tids: IntArray) {
        if (!isDebug) return
        val unknown = tids.filter { bucketByTid[it] == ThreadCpuBuckets.MISC }
        if (unknown.isEmpty()) return
        val names = unknown.mapNotNull { reader.readName(it) }.distinct().sorted()
        if (names.isNotEmpty()) {
            Log.d("ThreadCpu") { "Unclassified threads (${names.size}): ${names.joinToString()}" }
        }
    }
}
