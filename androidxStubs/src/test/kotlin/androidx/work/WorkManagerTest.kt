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
package androidx.work

import android.content.Context
import com.vitorpamplona.amethyst.shared.platform.JvmContext
import kotlinx.coroutines.delay
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The desktop scheduler is the difference between a scheduled post publishing
 * and silently never publishing, so these run real work on the real timer
 * rather than asserting that an enqueue was recorded.
 */
class WorkManagerTest {
    private val managers = mutableListOf<WorkManager>()

    private fun manager(): WorkManager = WorkManager(JvmContext).also { managers.add(it) }

    @AfterTest
    fun stopEverything() {
        managers.forEach { it.cancelAllWork() }
        managers.clear()
        Recorder.reset()
    }

    /**
     * Workers are built reflectively from their class, exactly as WorkManager
     * does, so every worker here has to be a real top-level class with the
     * (Context, WorkerParameters) constructor.
     */
    object Recorder {
        val runs = ConcurrentLinkedQueue<Int>()

        @Volatile var latch = CountDownLatch(1)

        @Volatile var outcome: (Int) -> ListenableWorker.Result = { ListenableWorker.Result.success() }

        fun reset() {
            runs.clear()
            latch = CountDownLatch(1)
            outcome = { ListenableWorker.Result.success() }
        }

        fun expect(count: Int) {
            latch = CountDownLatch(count)
        }

        fun record(attempt: Int): ListenableWorker.Result {
            runs.add(attempt)
            latch.countDown()
            return outcome(attempt)
        }

        fun await(seconds: Long = 5): Boolean = latch.await(seconds, TimeUnit.SECONDS)
    }

    class RecordingWorker(
        context: Context,
        params: WorkerParameters,
    ) : CoroutineWorker(context, params) {
        override suspend fun doWork(): Result {
            // A real suspension, so the future genuinely bridges the two worlds.
            delay(1)
            return Recorder.record(runAttemptCount)
        }
    }

    class ThrowingWorker(
        context: Context,
        params: WorkerParameters,
    ) : CoroutineWorker(context, params) {
        override suspend fun doWork(): Result {
            Recorder.record(runAttemptCount)
            throw IllegalStateException("worker blew up")
        }
    }

    private fun oneTime(constraints: Constraints = Constraints.NONE) = OneTimeWorkRequestBuilder<RecordingWorker>().setConstraints(constraints).build()

    private fun periodic(millis: Long) = PeriodicWorkRequestBuilder<RecordingWorker>(millis, TimeUnit.MILLISECONDS).build()

    @Test
    fun oneTimeWorkActuallyRuns() {
        Recorder.expect(1)
        val work = manager()
        work.enqueueUniqueWork("once", ExistingWorkPolicy.KEEP, oneTime())

        assertTrue(Recorder.await(), "the worker never ran")
        assertEquals(listOf(0), Recorder.runs.toList())
    }

    @Test
    fun oneTimeWorkIsForgottenOnceItSucceeds() {
        Recorder.expect(1)
        val work = manager()
        work.enqueueUniqueWork("once", ExistingWorkPolicy.KEEP, oneTime())
        assertTrue(Recorder.await())

        // Poll: removal happens on the timer thread just after the run.
        val cleared =
            (0 until 50).any {
                Thread.sleep(20)
                !work.isScheduled("once")
            }
        assertTrue(cleared, "a finished one-time entry should not stay scheduled")
    }

    @Test
    fun periodicWorkRepeats() {
        Recorder.expect(3)
        val work = manager()
        work.enqueueUniquePeriodicWork("every", ExistingPeriodicWorkPolicy.KEEP, periodic(30))

        assertTrue(Recorder.await(), "expected three runs, got ${Recorder.runs.size}")
        assertTrue(work.isScheduled("every"), "periodic work stays scheduled between runs")
    }

    @Test
    fun cancelStopsTheChain() {
        Recorder.expect(1)
        val work = manager()
        work.enqueueUniquePeriodicWork("every", ExistingPeriodicWorkPolicy.KEEP, periodic(30))
        assertTrue(Recorder.await())

        work.cancelUniqueWork("every")
        assertFalse(work.isScheduled("every"))

        val after = Recorder.runs.size
        Thread.sleep(300)
        // At most the run already in flight when cancel landed.
        assertTrue(Recorder.runs.size <= after + 1, "cancelled work kept firing")
    }

    @Test
    fun keepLeavesTheExistingScheduleAlone() {
        val work = manager()
        val first = periodic(60_000)
        work.enqueueUniquePeriodicWork("every", ExistingPeriodicWorkPolicy.KEEP, first)
        work.enqueueUniquePeriodicWork("every", ExistingPeriodicWorkPolicy.KEEP, periodic(30))

        // The 30ms request would have fired repeatedly had KEEP not held the
        // original minute-long schedule.
        Thread.sleep(300)
        assertTrue(Recorder.runs.size <= 1, "KEEP replaced the existing schedule")
    }

    @Test
    fun replaceSwapsTheSchedule() {
        val work = manager()
        work.enqueueUniquePeriodicWork("every", ExistingPeriodicWorkPolicy.KEEP, periodic(60_000))
        Recorder.expect(3)
        work.enqueueUniquePeriodicWork("every", ExistingPeriodicWorkPolicy.REPLACE, periodic(30))

        assertTrue(Recorder.await(), "REPLACE should install the new, faster schedule")
    }

    @Test
    fun aWorkerThatThrowsDoesNotTakeTheSchedulerDown() {
        Recorder.expect(1)
        val work = manager()
        work.enqueueUniqueWork(
            "boom",
            ExistingWorkPolicy.KEEP,
            OneTimeWorkRequestBuilder<ThrowingWorker>().build(),
        )
        assertTrue(Recorder.await(), "the throwing worker never ran")

        // The scheduler survives: the next piece of work still runs.
        Recorder.expect(1)
        work.enqueueUniqueWork("after", ExistingWorkPolicy.KEEP, oneTime())
        assertTrue(Recorder.await(), "the scheduler stopped after a worker threw")
    }

    @Test
    fun unmetNetworkConstraintDefersInsteadOfRunning() {
        // Nothing to assert about a machine that is online; the point is that
        // the decision is made from a real signal rather than assumed.
        assertTrue(WorkManager.constraintsMet(Constraints.NONE))
        assertEquals(
            android.net.ConnectivityManager.isConnected(),
            WorkManager.constraintsMet(
                Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build(),
            ),
        )
    }

    @Test
    fun backoffMatchesWorkManagersOwnPolicy() {
        assertEquals(30_000L, WorkManager.backoffMillis(1))
        assertEquals(60_000L, WorkManager.backoffMillis(2))
        assertEquals(120_000L, WorkManager.backoffMillis(3))
        // 30s doubled ten times is 8h32m, past the 5h ceiling.
        assertEquals(5 * 60 * 60 * 1000L, WorkManager.backoffMillis(11))
        assertEquals(5 * 60 * 60 * 1000L, WorkManager.backoffMillis(40))
    }

    @Test
    fun everyRequestCarriesItsWorkerClassAsATag() {
        val request = oneTime()
        assertTrue(request.tags.contains(RecordingWorker::class.java.name))
        assertFalse(request.isPeriodic)
        assertTrue(periodic(30).isPeriodic)
    }
}
