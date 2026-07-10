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
 */
@Immutable
data class PoWJobState(
    val id: String,
    val kind: Int,
    val difficulty: Int,
    val isMining: Boolean,
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
 * The queue is in-memory only: if the process dies, still-unmined posts are
 * lost (v1 trade-off; every enqueue/finish is logged for post-mortems).
 */
class PoWPublishQueue(
    private val scope: CoroutineScope,
    maxConcurrent: Int = 1,
    miningDispatcher: CoroutineDispatcher = Dispatchers.Default,
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
     */
    fun <T : Event> enqueue(
        template: EventTemplate<T>,
        pubKey: HexKey,
        difficulty: Int,
        onMined: suspend (EventTemplate<T>) -> Unit,
    ) = enqueueWork(template.kind, difficulty) { isActive ->
        val mined = PoWMiner.run(template, pubKey, difficulty, isActive)
        // frees the mining worker: signing may wait on an external signer
        // (Amber/bunker) and broadcasting is IO, neither belongs on the pool.
        scope.launch { onMined(mined) }
    }

    /**
     * Enqueues arbitrary mining work — used by flows where the mining happens
     * inside a larger build step (e.g. gift wraps, where each recipient's
     * ephemeral-key wrap is mined right before its local signature).
     */
    fun enqueueWork(
        kind: Int,
        difficulty: Int,
        work: suspend (isActive: () -> Boolean) -> Unit,
    ) {
        val job = MiningJob(RandomInstance.randomChars(16), kind, difficulty, work)
        pending.update { it.put(job.id, job) }
        _jobs.update { (it + PoWJobState(job.id, job.kind, job.difficulty, isMining = false)).toImmutableList() }
        Log.d(TAG) { "Enqueued PoW job ${job.id} kind=${job.kind} difficulty=${job.difficulty} (in-memory queue, lost on process death)" }
        queue.trySend(job)
    }

    /** Cancels a queued or mining job. No-op if the job already finished. */
    fun cancel(jobId: String) {
        val job = pending.value[jobId] ?: return
        job.cancelled = true
        remove(jobId)
        Log.d(TAG) { "Cancelled PoW job $jobId" }
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
            list.map { if (it.id == jobId) it.copy(isMining = true) else it }.toImmutableList()
        }
    }

    private fun remove(jobId: String) {
        pending.update { it.remove(jobId) }
        _jobs.update { list -> list.filter { it.id != jobId }.toImmutableList() }
    }

    companion object {
        private const val TAG = "PoWPublishQueue"
    }
}
