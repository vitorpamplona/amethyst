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
package com.vitorpamplona.amethyst.commons.service.pow

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.signers.EventTemplate
import com.vitorpamplona.quartz.nip13Pow.miner.PoWMiner
import com.vitorpamplona.quartz.utils.Log
import com.vitorpamplona.quartz.utils.RandomInstance
import com.vitorpamplona.quartz.utils.TimeUtils
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.Channel.Factory.UNLIMITED
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlin.concurrent.Volatile
import kotlin.coroutines.cancellation.CancellationException

/**
 * Snapshot of one queued/mining publish job, for display in the broadcast banner.
 * [miningStartedAt] (epoch seconds) is set when a worker picks the job up, so
 * the UI can show how long the current nonce search has been running.
 */
@Immutable
data class PoWJobState(
    val id: String,
    val kind: Int,
    val difficulty: Int,
    val isMining: Boolean,
    val miningStartedAt: Long? = null,
)

/**
 * Fire-and-forget NIP-13 mining queue: the Post button enqueues the finished,
 * unsigned template here and returns immediately; mining runs on a small
 * worker pool and, once the nonce is found, the job hands the mined template
 * to the normal sign+broadcast continuation captured at enqueue time.
 *
 * FIFO with at most [maxConcurrent] concurrent miners so a burst of posts
 * queues up instead of spawning unbounded CPU work. Each job is cancellable
 * while queued or mining.
 *
 * Template jobs enqueued with a [PersistedPoWJob] record are checkpointed to
 * [persistence] and removed when they finish or are cancelled, so the platform
 * layer can re-enqueue them after process death. Opaque [enqueueWork] jobs
 * (reactions, reposts, gift wraps — and anonymous posts, deliberately, so a
 * throwaway key and its content never touch disk) stay in-memory only.
 *
 * [onQueueActive] fires on every enqueue; the Android layer uses it to start
 * the mining foreground service so backgrounding the app doesn't freeze the
 * workers mid-nonce.
 */
class PoWPublishQueue(
    private val scope: CoroutineScope,
    maxConcurrent: Int = 1,
    miningDispatcher: CoroutineDispatcher = Dispatchers.Default,
    private val persistence: PoWJobPersistence? = null,
    private val onQueueActive: () -> Unit = {},
) {
    private class MiningJob(
        val id: String,
        val kind: Int,
        val difficulty: Int,
        val work: suspend (isActive: () -> Boolean) -> Unit,
    ) {
        @Volatile
        var cancelled = false
    }

    private val queue = Channel<MiningJob>(UNLIMITED)

    private val _jobs = MutableStateFlow<ImmutableList<PoWJobState>>(persistentListOf())

    /** Queued + currently-mining jobs, in enqueue order. */
    val jobs: StateFlow<ImmutableList<PoWJobState>> = _jobs.asStateFlow()

    init {
        repeat(maxConcurrent.coerceAtLeast(1)) {
            scope.launch(miningDispatcher) {
                for (job in queue) {
                    process(job)
                }
            }
        }
    }

    /**
     * Mines [template] at [difficulty] and hands the mined template to
     * [onMined] on the queue's scope. [onMined] should run the exact
     * sign+broadcast path the caller would have used without PoW.
     *
     * When [persistAs] is given, the job is checkpointed (under the record's
     * id) until it finishes or is cancelled, so it can be restored after
     * process death. Re-enqueueing an id already in the queue is a no-op —
     * that makes restore-on-login idempotent.
     */
    fun <T : Event> enqueue(
        template: EventTemplate<T>,
        pubKey: HexKey,
        difficulty: Int,
        persistAs: PersistedPoWJob? = null,
        onMined: suspend (EventTemplate<T>) -> Unit,
    ) = addJob(
        id = persistAs?.id ?: RandomInstance.randomChars(16),
        kind = template.kind,
        difficulty = difficulty,
        persistAs = persistAs,
    ) { isActive ->
        val mined = PoWMiner.run(template, pubKey, difficulty, isActive)
        // frees the mining worker: signing may wait on an external signer
        // (Amber/bunker) and broadcasting is IO, neither belongs on the pool.
        scope.launch { onMined(mined) }
    }

    /**
     * Enqueues arbitrary in-memory mining work — used by flows where the
     * mining happens inside a larger build step (e.g. gift wraps, where each
     * recipient's ephemeral-key wrap is mined right before its local
     * signature) and by anonymous posts, whose throwaway key must not be
     * written to disk. Lost on process death.
     */
    fun enqueueWork(
        kind: Int,
        difficulty: Int,
        work: suspend (isActive: () -> Boolean) -> Unit,
    ) = addJob(RandomInstance.randomChars(16), kind, difficulty, persistAs = null, work = work)

    private fun addJob(
        id: String,
        kind: Int,
        difficulty: Int,
        persistAs: PersistedPoWJob?,
        work: suspend (isActive: () -> Boolean) -> Unit,
    ) {
        if (_jobs.value.any { it.id == id }) {
            Log.d(TAG) { "PoW job $id already queued; skipping duplicate enqueue" }
            return
        }

        val job = MiningJob(id, kind, difficulty, work)
        persistAs?.let { persistence?.save(it) }
        pending.update { it.put(job.id, job) }
        _jobs.update { (it + PoWJobState(job.id, job.kind, job.difficulty, isMining = false)).toImmutableList() }
        Log.d(TAG) {
            val durability = if (persistAs != null) "persisted" else "in-memory only, lost on process death"
            "Enqueued PoW job ${job.id} kind=${job.kind} difficulty=${job.difficulty} ($durability)"
        }
        queue.trySend(job)
        onQueueActive()
    }

    /** Cancels a queued or mining job. No-op if the job already finished. */
    fun cancel(jobId: String) {
        val job = pending.value[jobId] ?: return
        job.cancelled = true
        remove(jobId)
        Log.d(TAG) { "Cancelled PoW job $jobId" }
    }

    /** Cancels everything still queued or mining. */
    fun cancelAll() {
        pending.value.keys.forEach { cancel(it) }
    }

    // Jobs the workers haven't finished yet, so cancel() can reach the flag of
    // a job that is still sitting in the channel. StateFlow.update gives us
    // atomic CAS updates across the UI thread and the mining workers.
    private val pending = MutableStateFlow<PersistentMap<String, MiningJob>>(persistentMapOf())

    private suspend fun process(job: MiningJob) {
        if (job.cancelled) {
            remove(job.id)
            return
        }

        markMining(job.id)

        val workerJob = currentCoroutineContext().job

        try {
            job.work { !job.cancelled && workerJob.isActive }
            Log.d(TAG) { "Finished PoW job ${job.id} kind=${job.kind} difficulty=${job.difficulty}" }
        } catch (e: CancellationException) {
            // the worker itself was cancelled (scope teardown): propagate.
            if (!currentCoroutineContext().isActive) throw e
            Log.d(TAG) { "PoW job ${job.id} cancelled while mining" }
        } catch (e: Exception) {
            Log.w(TAG, "PoW job ${job.id} kind=${job.kind} failed", e)
        } finally {
            remove(job.id)
        }
    }

    private fun markMining(jobId: String) {
        _jobs.update { list ->
            list.map { if (it.id == jobId) it.copy(isMining = true, miningStartedAt = TimeUtils.now()) else it }.toImmutableList()
        }
    }

    private fun remove(jobId: String) {
        pending.update { it.remove(jobId) }
        _jobs.update { list -> list.filter { it.id != jobId }.toImmutableList() }
        persistence?.remove(jobId)
    }

    companion object {
        private const val TAG = "PoWPublishQueue"
    }
}
