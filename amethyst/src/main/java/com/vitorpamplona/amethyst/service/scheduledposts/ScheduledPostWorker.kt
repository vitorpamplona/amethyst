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
package com.vitorpamplona.amethyst.service.scheduledposts

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.vitorpamplona.amethyst.Amethyst
import com.vitorpamplona.amethyst.commons.scheduledposts.ScheduledPostPublisher
import com.vitorpamplona.amethyst.commons.scheduledposts.ScheduledPostStatus
import com.vitorpamplona.amethyst.service.resourceusage.UsageKeys
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.relay.client.accessories.publishAndConfirmDetailed
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.utils.Log
import kotlinx.coroutines.CancellationException
import java.util.concurrent.TimeUnit

/**
 * Scans the scheduled-post store and publishes posts whose publish time has arrived.
 *
 *  - schedule(context):       periodic, every 15 min (WorkManager minimum). Only
 *                             enqueued while the store holds a PENDING post — the
 *                             store observer in AppModules schedules it when a
 *                             pending post appears and cancels it when the last
 *                             one drains, so the worker never wakes the process
 *                             with nothing to publish.
 *  - scheduleCatchUp(context): one-time, to flush posts that came due while the
 *                              device was off or while WorkManager was deferred
 *                              by Doze.
 */
class ScheduledPostWorker(
    appContext: Context,
    workerParams: WorkerParameters,
) : CoroutineWorker(appContext, workerParams) {
    init {
        // Logs every worker instantiation. Without this, "doWork never ran" looks
        // identical to "constructor never invoked" — and the latter means the OS
        // (Doze, battery-opt, JobScheduler quotas) never woke us at all.
        Log.d(TAG) { "Worker instantiated (runAttempt=${workerParams.runAttemptCount}, tags=${workerParams.tags})" }
    }

    companion object {
        private const val TAG = "ScheduledPostWorker"
        private const val WORK_NAME = "scheduled_post_worker"
        private const val WORK_NAME_CATCH_UP = "scheduled_post_worker_catch_up"

        fun schedule(context: Context) {
            val constraints =
                Constraints
                    .Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()

            val request =
                PeriodicWorkRequestBuilder<ScheduledPostWorker>(15, TimeUnit.MINUTES)
                    .setConstraints(constraints)
                    .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request,
            )
            Log.d(TAG) {
                "schedule(): enqueueUniquePeriodicWork($WORK_NAME, 15 MIN, KEEP) — KEEP policy preserves any existing schedule"
            }
        }

        fun scheduleCatchUp(context: Context) {
            val constraints =
                Constraints
                    .Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()

            val request =
                OneTimeWorkRequestBuilder<ScheduledPostWorker>()
                    .setConstraints(constraints)
                    .build()

            WorkManager.getInstance(context).enqueueUniqueWork(
                WORK_NAME_CATCH_UP,
                ExistingWorkPolicy.KEEP,
                request,
            )
            Log.d(TAG) { "scheduleCatchUp(): enqueueUniqueWork($WORK_NAME_CATCH_UP, KEEP)" }
        }

        /**
         * Ends the 15-minute periodic chain (keeps any catch-up run). Called by the
         * store observer in AppModules when the last PENDING post drains, so the
         * worker doesn't keep waking the process with nothing to publish.
         */
        fun cancelPeriodic(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
            Log.d(TAG) { "cancelPeriodic(): cancelled periodic worker" }
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME_CATCH_UP)
            Log.d(TAG) { "cancel(): cancelled both periodic and catch-up workers" }
        }
    }

    override suspend fun doWork(): Result {
        val nowSec = System.currentTimeMillis() / 1000
        Log.d(TAG) { "doWork() ENTER nowSec=$nowSec runAttempt=$runAttemptCount tags=$tags" }
        runCatching { Amethyst.instance.resourceUsage.add(UsageKeys.workerRuns("scheduledPost"), 1) }

        return try {
            val appModules = Amethyst.instance
            val store = appModules.scheduledPostStore
            val notifier = AndroidScheduledPostNotifier(applicationContext)

            val all = store.list()
            val pending = all.count { it.status == ScheduledPostStatus.PENDING }
            Log.d(TAG) { "doWork() store has ${all.size} total, $pending PENDING" }

            val claimed = store.claimDuePosts(nowSec)
            if (claimed.isEmpty()) {
                Log.d(TAG) { "doWork() EXIT no posts due" }
                return Result.success()
            }

            Log.d(TAG) { "doWork() claimed ${claimed.size} due post(s)" }

            for (post in claimed) {
                val ageSec = nowSec - post.publishAtSec
                Log.d(TAG) {
                    "Publishing post id=${post.id} publishAtSec=${post.publishAtSec} ageSec=$ageSec relays=${post.relayUrls.size}"
                }
                val account = appModules.accountsCache.accounts.value[post.accountPubkey]
                if (account == null) {
                    Log.w(TAG) { "Account ${post.accountPubkey} not loaded; releasing ${post.id} for retry" }
                    store.releaseClaim(post.id)
                    continue
                }

                try {
                    val event = Event.fromJson(post.signedEventJson)
                    val relays = post.relayUrls.map { NormalizedRelayUrl(it) }.toSet()
                    val extras = post.extraEventsJson.map { Event.fromJson(it) }

                    Log.d(TAG) { "client.publish(${post.id}) starting on ${relays.size} relay(s)" }

                    val results = account.client.publishAndConfirmDetailed(event, relays)
                    val acks = results.count { it.value }
                    if (acks > 0) {
                        // Only feed the event into the local cache once at least one relay
                        // acked — a failed publish must not appear in the user's timeline.
                        account.consumePostEvent(event, relays, extras)
                        store.markSent(post.id)
                        notifier.notifySent(post)
                        Log.d(TAG) { "client.publish(${post.id}) acked by $acks/${relays.size}; marked SENT" }
                    } else {
                        // Transient no-ack: release the claim for a later retry rather than
                        // stranding the post as FAILED, until MAX_PUBLISH_ATTEMPTS is hit.
                        val msg = "no relay acknowledged"
                        if (post.attemptCount < ScheduledPostPublisher.MAX_PUBLISH_ATTEMPTS) {
                            store.releaseClaim(post.id)
                            Log.w(TAG) { "client.publish(${post.id}) got no ack (attempt ${post.attemptCount}); released for retry" }
                        } else {
                            store.markFailed(post.id, msg)
                            notifier.notifyFailed(post, msg)
                            Log.w(TAG) { "client.publish(${post.id}) failed after max attempts: $msg" }
                        }
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to publish scheduled post ${post.id}", e)
                    store.markFailed(post.id, e.message)
                    notifier.notifyFailed(post, e.message)
                }
            }

            Log.d(TAG) { "doWork() EXIT success" }
            Result.success()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "doWork() unexpected failure", e)
            Result.retry()
        }
    }
}
