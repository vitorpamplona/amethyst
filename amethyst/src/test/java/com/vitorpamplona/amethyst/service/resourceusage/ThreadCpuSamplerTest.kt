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

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class ThreadCpuSamplerTest {
    @get:Rule
    val temp = TemporaryFolder()

    /** A tid no fixture uses, so tests that are not about the main thread never hit that branch. */
    private val noMainTid = -1

    /** Representative kernel `comm` values, capped at 15 chars as the kernel caps them. */
    private val sampleThreadNames =
        listOf(
            "OkHttp TaskRunner",
            "Okio Watchdog",
            "DefaultDispatch",
            "RenderThread",
            "hwuiTask",
            "HeapTaskDaemon",
            "FinalizerDaemon",
            "Jit thread pool",
            "Runtime worker",
            "tokio-runtime-w",
            "ExoPlayer:Playb",
            "AudioTrack",
            "binder:123_4",
            "pool-3-thread-1",
            "Thread-9",
            "queued-work-loo",
        )

    // ------------------------------------------------------------------
    // /proc/<tid>/stat parsing
    // ------------------------------------------------------------------

    private fun parse(line: String): Long {
        val bytes = line.toByteArray()
        return ProcThreadCpuReader.parseUtimePlusStime(bytes, bytes.size)
    }

    /** fields 1..15, where 14 = utime and 15 = stime. */
    private fun statLine(
        comm: String,
        utime: Long,
        stime: Long,
    ) = "42 ($comm) S 1 1 1 0 -1 4210752 903 0 0 0 $utime $stime 0 0 20 0 51 0 12345"

    @Test
    fun parsesUtimePlusStime() {
        assertEquals(30L, parse(statLine("worker", utime = 12, stime = 18)))
    }

    @Test
    fun parsesZero() {
        assertEquals(0L, parse(statLine("idle", utime = 0, stime = 0)))
    }

    /**
     * The whole reason the parser scans back from the last `)` instead of
     * splitting on spaces. ART and OkHttp both produce thread names with
     * spaces, and OkHttp's carry hostnames.
     */
    @Test
    fun parsesThreadNamesContainingSpaces() {
        assertEquals(7L, parse(statLine("Jit thread pool", utime = 3, stime = 4)))
        assertEquals(7L, parse(statLine("OkHttp relay.example.com", utime = 3, stime = 4)))
    }

    /** A name may itself contain a `)`; only the LAST one closes the comm field. */
    @Test
    fun parsesThreadNamesContainingParens() {
        assertEquals(11L, parse(statLine("weird)name", utime = 5, stime = 6)))
    }

    /** Several fields before utime are legitimately negative (e.g. tpgid = -1). */
    @Test
    fun skipsNegativeFieldsBeforeUtime() {
        assertEquals(9L, parse("7 (t) S 1 1 1 0 -1 4210752 1 2 3 4 4 5 0 0 20 0 2 0 9"))
    }

    @Test
    fun returnsMinusOneOnMalformedLine() {
        assertEquals(-1L, parse("no parens here at all"))
        assertEquals(-1L, parse("42 (short) S 1 2 3"))
    }

    @Test
    fun parsesWhenBufferHoldsGarbageBeyondTheLine() {
        // The reader reuses one buffer, so a shorter line leaves the previous
        // read's tail behind. Only the first `length` bytes may be read.
        val line = statLine("t", utime = 1, stime = 2)
        val buffer = ByteArray(512) { 'X'.code.toByte() }
        val bytes = line.toByteArray()
        bytes.copyInto(buffer)
        assertEquals(3L, ProcThreadCpuReader.parseUtimePlusStime(buffer, bytes.size))
    }

    /**
     * The synthetic lines above prove the parser is self-consistent; only a real
     * kernel proves the field OFFSETS are right, and an off-by-one there would
     * report some unrelated field as CPU with no symptom but wrong numbers.
     *
     * JVM unit tests run on Linux, which has the same `/proc/<tid>/stat` layout
     * as Android, so the reader can be pointed at this very process. Skipped
     * (rather than failed) anywhere `/proc` is absent, so the suite stays green
     * on a non-Linux dev machine.
     */
    @Test
    fun tracksRealCpuGrowthOnALiveProcess() {
        val taskDir = File("/proc/self/task")
        if (!taskDir.isDirectory) return

        val reader = ProcThreadCpuReader(taskDir)

        // Compared over the threads alive at BOTH ends, not over a tid list
        // captured once. A JVM retires GC and JIT threads at unpredictable
        // moments, and a tid that has gone reads as -1 — so a fixed list can
        // total LESS after the burn than before it and fail a test about a
        // monotonic counter. Over the intersection the sum cannot regress,
        // because per-thread CPU only ever increases.
        fun sample(): Map<Int, Long> =
            reader
                .listTids()
                .associateWith { reader.readCpuTicks(it) }
                .filterValues { it >= 0 }

        val before = sample()
        assertTrue("/proc/self/task listed no readable threads", before.isNotEmpty())

        // Well past one clock tick (10ms at 100Hz) of real user time on THIS
        // thread, which cannot be the one that exits, so the counter has to
        // move if the offsets point at utime/stime.
        val deadline = System.nanoTime() + 400_000_000L
        var sink = 0L
        while (System.nanoTime() < deadline) sink += System.nanoTime() % 7
        assertTrue(sink >= 0)

        val after = sample()
        val survivors = before.keys.intersect(after.keys)
        assertTrue("no thread survived the burn", survivors.isNotEmpty())

        val beforeTicks = survivors.sumOf { before.getValue(it) }
        val afterTicks = survivors.sumOf { after.getValue(it) }
        assertTrue(
            "CPU ticks did not grow after burning CPU: $beforeTicks -> $afterTicks over ${survivors.size} threads",
            afterTicks > beforeTicks,
        )
        assertTrue("Implausible tick rate: ${reader.ticksPerSecond}", reader.ticksPerSecond in 1..10_000)
    }

    /** Every live thread must classify, and none may be attributed by raw name. */
    @Test
    fun classifiesEveryThreadOfALiveProcess() {
        val taskDir = File("/proc/self/task")
        if (!taskDir.isDirectory) return

        val reader = ProcThreadCpuReader(taskDir)
        reader.listTids().forEach { tid ->
            val name = reader.readName(tid) ?: return@forEach
            assertTrue("comm '$name' should not be blank", name.isNotBlank())
            assertTrue(ThreadCpuBuckets.classify(name) in ThreadCpuBuckets.ALL)
        }
    }

    // ------------------------------------------------------------------
    // Bucket classification
    // ------------------------------------------------------------------

    @Test
    fun classifiesKnownThreadNames() {
        assertEquals(ThreadCpuBuckets.NET, ThreadCpuBuckets.classify("OkHttp TaskRunner"))
        assertEquals(ThreadCpuBuckets.NET, ThreadCpuBuckets.classify("OkHttp relay.damus.io"))
        assertEquals(ThreadCpuBuckets.NET, ThreadCpuBuckets.classify("Okio Watchdog"))
        assertEquals(ThreadCpuBuckets.DISPATCH, ThreadCpuBuckets.classify("DefaultDispatch"))
        assertEquals(ThreadCpuBuckets.RENDER, ThreadCpuBuckets.classify("RenderThread"))
        assertEquals(ThreadCpuBuckets.GC, ThreadCpuBuckets.classify("HeapTaskDaemon"))
        assertEquals(ThreadCpuBuckets.TOR, ThreadCpuBuckets.classify("tokio-runtime-w"))
        assertEquals(ThreadCpuBuckets.MEDIA, ThreadCpuBuckets.classify("ExoPlayer:Playb"))
        assertEquals(ThreadCpuBuckets.BINDER, ThreadCpuBuckets.classify("binder:1234_2"))
        assertEquals(ThreadCpuBuckets.POOL, ThreadCpuBuckets.classify("pool-3-thread-1"))
        assertEquals(ThreadCpuBuckets.MISC, ThreadCpuBuckets.classify("something-else"))
    }

    /** `Jit thread pool` starts with neither `pool-` nor `Thread-`, but the ordering still matters. */
    @Test
    fun artInternalsWinOverGenericPrefixes() {
        assertEquals(ThreadCpuBuckets.JIT, ThreadCpuBuckets.classify("Jit thread pool"))
        assertEquals(ThreadCpuBuckets.JIT, ThreadCpuBuckets.classify("Runtime worker"))
    }

    /**
     * The ledger's key grammar reserves segments that [UsageKeys.sumMatching]
     * folds into headline figures. A bucket named `image` or `other` would
     * silently join the HTTP-bytes-per-subsystem sums.
     */
    @Test
    fun bucketNamesAvoidReservedSegments() {
        val reserved =
            setOf(
                UsageKeys.RX,
                UsageKeys.TX,
                "msg",
                "connms",
                "connects",
                "connfails",
                "reqs",
                "bursts",
                "activems",
                "worker",
                "runs",
                "ms",
                UsageKeys.FG,
                UsageKeys.BG,
            ) + UsageKeys.HTTP_ROLES

        ThreadCpuBuckets.ALL.forEach { bucket ->
            assertTrue("Bucket '$bucket' collides with a reserved key segment", bucket !in reserved)
        }
    }

    /** A new bucket must not disturb any existing headline figure. */
    @Test
    fun bucketKeysDoNotDisturbTheSummary() {
        val base =
            mapOf(
                UsageKeys.CPU_MS to 1_000L,
                UsageKeys.net(UsageKeys.ROLE_IMAGE, mobile = true, foreground = false, received = true) to 500L,
                UsageKeys.relayConnMs(mobile = true, foreground = false) to 900L,
            )
        val withBuckets =
            base +
                ThreadCpuBuckets.ALL.flatMap { bucket ->
                    listOf(
                        UsageKeys.cpuBucket(bucket, UsageKeys.FG) to 7L,
                        UsageKeys.cpuBucket(bucket, UsageKeys.BG) to 11L,
                    )
                }

        val before = UsageSummary.from(base)
        val after = UsageSummary.from(withBuckets)

        assertEquals(before.mobileBytesBg, after.mobileBytesBg)
        assertEquals(before.relayConnMsMobileBg, after.relayConnMsMobileBg)
        assertEquals(before.cpuMs, after.cpuMs)
        assertEquals(before.httpRequests, after.httpRequests)
        assertEquals(before.workerRuns, after.workerRuns)
        assertEquals(before.bytesPerSubsystem, after.bytesPerSubsystem)
    }

    /** `cpu.fg.ms`/`cpu.bg.ms` share the `cpu.` prefix but are totals — never buckets. */
    @Test
    fun visibilityTotalsAreNotReadAsBuckets() {
        val summary =
            UsageSummary.from(
                mapOf(
                    UsageKeys.CPU_MS to 300L,
                    UsageKeys.CPU_FG_MS to 200L,
                    UsageKeys.CPU_BG_MS to 100L,
                ),
            )

        assertEquals(300L, summary.cpuMs)
        assertEquals(200L, summary.cpuFgMs)
        assertEquals(100L, summary.cpuBgMs)
        assertTrue(summary.cpuMsPerBucket.isEmpty())
        assertNull(summary.cpuMsPerBucket[UsageKeys.FG])
    }

    // ------------------------------------------------------------------
    // Sampling
    // ------------------------------------------------------------------

    private class FakeReader(
        override val ticksPerSecond: Long = 100L,
    ) : ThreadCpuReader {
        val names = mutableMapOf<Int, String>()
        val ticks = mutableMapOf<Int, Long>()

        fun set(
            tid: Int,
            name: String,
            cpuTicks: Long,
        ) {
            names[tid] = name
            ticks[tid] = cpuTicks
        }

        override fun listTids() = ticks.keys.toIntArray()

        override fun readName(tid: Int) = names[tid]

        override fun readCpuTicks(tid: Int) = ticks[tid] ?: -1
    }

    private fun accountant(scope: CoroutineScope) =
        ResourceUsageAccountant(
            ResourceUsageStore(File(temp.root, "u.json")),
            scope,
            epochDay = { 0L },
        )

    @Test
    fun attributesDeltasPerBucketAndVisibility() =
        runTest {
            val accountant = accountant(backgroundScope)
            val reader = FakeReader()
            var foreground = true
            var processMs = 0L

            reader.set(10, "OkHttp TaskRunner", 0)
            reader.set(11, "DefaultDispatch", 0)

            val sampler = ThreadCpuSampler(accountant, { foreground }, reader, { processMs }, mainTid = noMainTid)
            sampler.register()

            // 50 ticks = 500ms of network work in the foreground.
            reader.ticks[10] = 50
            processMs = 500
            sampler.sample()

            var counters = accountant.allDaysIncludingLive()[0L].orEmpty()
            assertEquals(500L, counters[UsageKeys.cpuBucket(ThreadCpuBuckets.NET, UsageKeys.FG)])
            assertNull(counters[UsageKeys.cpuBucket(ThreadCpuBuckets.NET, UsageKeys.BG)])

            // Backgrounded: 20 ticks of coroutine work lands in the bg column.
            foreground = false
            reader.ticks[11] = 20
            processMs = 700
            sampler.sample()

            counters = accountant.allDaysIncludingLive()[0L].orEmpty()
            assertEquals(200L, counters[UsageKeys.cpuBucket(ThreadCpuBuckets.DISPATCH, UsageKeys.BG)])
            // The foreground figure from the first interval is untouched.
            assertEquals(500L, counters[UsageKeys.cpuBucket(ThreadCpuBuckets.NET, UsageKeys.FG)])
        }

    /** Priming must not book the CPU every thread accrued before the sampler existed. */
    @Test
    fun theFirstSampleOnlyTakesABaseline() =
        runTest {
            val accountant = accountant(backgroundScope)
            val reader = FakeReader()
            reader.set(10, "OkHttp TaskRunner", 9_999)

            ThreadCpuSampler(accountant, { true }, reader, { 99_990L }, mainTid = noMainTid).register()

            assertTrue(accountant.allDaysIncludingLive()[0L].orEmpty().isEmpty())
        }

    /** A thread born mid-interval did all of its work inside that interval. */
    @Test
    fun countsNewThreadsInFull() =
        runTest {
            val accountant = accountant(backgroundScope)
            val reader = FakeReader()
            var processMs = 0L
            reader.set(10, "OkHttp TaskRunner", 0)

            val sampler = ThreadCpuSampler(accountant, { true }, reader, { processMs }, mainTid = noMainTid)
            sampler.register()

            reader.set(11, "DefaultDispatch", 30)
            processMs = 300
            sampler.sample()

            val counters = accountant.allDaysIncludingLive()[0L].orEmpty()
            assertEquals(300L, counters[UsageKeys.cpuBucket(ThreadCpuBuckets.DISPATCH, UsageKeys.FG)])
        }

    /**
     * The point of the residual: a thread that exits mid-interval takes its CPU
     * with it, and that is exactly the short-lived-thread churn the diagnosis is
     * hunting. The process total recovers it.
     */
    @Test
    fun booksCpuFromExitedThreadsToTheGoneBucket() =
        runTest {
            val accountant = accountant(backgroundScope)
            val reader = FakeReader()
            var processMs = 0L
            reader.set(10, "OkHttp TaskRunner", 0)

            val sampler = ThreadCpuSampler(accountant, { true }, reader, { processMs }, mainTid = noMainTid)
            sampler.register()

            // The live thread accounts for 100ms; the process burned 450ms. The
            // missing 350ms belongs to threads that have since exited.
            reader.ticks[10] = 10
            processMs = 450
            sampler.sample()

            val counters = accountant.allDaysIncludingLive()[0L].orEmpty()
            assertEquals(100L, counters[UsageKeys.cpuBucket(ThreadCpuBuckets.NET, UsageKeys.FG)])
            assertEquals(350L, counters[UsageKeys.cpuBucket(ThreadCpuBuckets.GONE, UsageKeys.FG)])
        }

    /** Buckets must sum to the process delta, which is what makes the table readable as shares. */
    @Test
    fun bucketsSumToTheProcessTotal() =
        runTest {
            val accountant = accountant(backgroundScope)
            val reader = FakeReader()
            var processMs = 0L
            reader.set(10, "OkHttp TaskRunner", 0)
            reader.set(11, "DefaultDispatch", 0)
            reader.set(12, "RenderThread", 0)

            val sampler = ThreadCpuSampler(accountant, { true }, reader, { processMs }, mainTid = noMainTid)
            sampler.register()

            reader.ticks[10] = 11
            reader.ticks[11] = 22
            reader.ticks[12] = 33
            processMs = 1_000
            sampler.sample()

            val counters = accountant.allDaysIncludingLive()[0L].orEmpty()
            val total = ThreadCpuBuckets.ALL.sumOf { counters[UsageKeys.cpuBucket(it, UsageKeys.FG)] ?: 0L }
            assertEquals(1_000L, total)
        }

    /**
     * Tids are recycled. Without pruning, a new thread inheriting a dead tid
     * would be diffed against the dead thread's counter and its CPU dropped as
     * a negative delta.
     */
    @Test
    fun recycledTidsDoNotInheritADeadThreadsCounter() =
        runTest {
            val accountant = accountant(backgroundScope)
            val reader = FakeReader()
            var processMs = 0L
            reader.set(10, "OkHttp TaskRunner", 500)

            val sampler = ThreadCpuSampler(accountant, { true }, reader, { processMs }, mainTid = noMainTid)
            sampler.register()

            // tid 10 exits.
            reader.ticks.remove(10)
            reader.names.remove(10)
            processMs = 100
            sampler.sample()

            // tid 10 is handed to a brand-new render thread with a small counter.
            reader.set(10, "RenderThread", 4)
            processMs = 140
            sampler.sample()

            val counters = accountant.allDaysIncludingLive()[0L].orEmpty()
            assertEquals(40L, counters[UsageKeys.cpuBucket(ThreadCpuBuckets.RENDER, UsageKeys.FG)])
            // ...and it is a render thread now, not still the old socket thread.
            assertNull(counters[UsageKeys.cpuBucket(ThreadCpuBuckets.NET, UsageKeys.FG)])
        }

    /**
     * The UI thread has to be separable — it is the one whose CPU a user
     * actually feels. Its `comm` is the truncated process name, which carries
     * no marker any prefix table could match, so it is identified by tid; when
     * it was not, main-thread CPU silently landed in `misc`.
     */
    @Test
    fun theMainThreadIsItsOwnBucket() =
        runTest {
            val accountant = accountant(backgroundScope)
            val reader = FakeReader()
            var processMs = 0L
            // The name a real Android main thread carries: the truncated app id.
            reader.set(7, "torpamplona.ame", 0)

            val sampler = ThreadCpuSampler(accountant, { true }, reader, { processMs }, mainTid = 7)
            sampler.register()

            reader.ticks[7] = 25
            processMs = 250
            sampler.sample()

            val counters = accountant.allDaysIncludingLive()[0L].orEmpty()
            assertEquals(250L, counters[UsageKeys.cpuBucket(ThreadCpuBuckets.MAIN, UsageKeys.FG)])
            assertNull(counters[UsageKeys.cpuBucket(ThreadCpuBuckets.MISC, UsageKeys.FG)])
        }

    /** Every bucket the table can emit must be reachable, or its row never renders. */
    @Test
    fun everyBucketIsReachable() {
        val unreachable =
            ThreadCpuBuckets.ALL.filter { bucket ->
                when (bucket) {
                    // Not thread names: one is assigned by tid, the other is a residual.
                    ThreadCpuBuckets.MAIN, ThreadCpuBuckets.GONE -> false
                    ThreadCpuBuckets.MISC -> false
                    else -> sampleThreadNames.none { ThreadCpuBuckets.classify(it) == bucket }
                }
            }
        assertTrue("Buckets no thread name can produce: $unreachable", unreachable.isEmpty())
    }

    /**
     * The gap [pruneDeadThreads] cannot see: a tid that exits and is handed to a
     * new thread BETWEEN two sweeps is never absent from a listing, so the stale
     * entry survives. Left alone, the new thread's first delta goes negative and
     * is dropped, and it wears the dead thread's bucket for the rest of its life.
     */
    @Test
    fun aTidRecycledBetweenSweepsIsTreatedAsANewThread() =
        runTest {
            val accountant = accountant(backgroundScope)
            val reader = FakeReader()
            var processMs = 0L
            reader.set(10, "OkHttp TaskRunner", 500)

            val sampler = ThreadCpuSampler(accountant, { true }, reader, { processMs }, mainTid = noMainTid)
            sampler.register()

            // Same tid, now a render thread with a counter that restarted from 0.
            // No sweep ever observed tid 10 missing.
            reader.set(10, "RenderThread", 6)
            processMs = 60
            sampler.sample()

            val counters = accountant.allDaysIncludingLive()[0L].orEmpty()
            assertEquals(60L, counters[UsageKeys.cpuBucket(ThreadCpuBuckets.RENDER, UsageKeys.FG)])
            assertNull(counters[UsageKeys.cpuBucket(ThreadCpuBuckets.NET, UsageKeys.FG)])
            // Nothing leaked into the residual either.
            assertNull(counters[UsageKeys.cpuBucket(ThreadCpuBuckets.GONE, UsageKeys.FG)])
        }

    /** An unreadable /proc must cost the breakdown, never a crash. */
    @Test
    fun survivesAnUnreadableProc() =
        runTest {
            val accountant = accountant(backgroundScope)
            val empty =
                object : ThreadCpuReader {
                    override val ticksPerSecond = 100L

                    override fun listTids() = IntArray(0)

                    override fun readName(tid: Int): String? = null

                    override fun readCpuTicks(tid: Int) = -1L
                }
            var processMs = 0L
            val sampler = ThreadCpuSampler(accountant, { false }, empty, { processMs }, mainTid = noMainTid)
            sampler.register()

            processMs = 250
            sampler.sample()

            // Everything lands in the residual — honest, since nothing could be attributed.
            val counters = accountant.allDaysIncludingLive()[0L].orEmpty()
            assertEquals(250L, counters[UsageKeys.cpuBucket(ThreadCpuBuckets.GONE, UsageKeys.BG)])
        }
}
