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
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.utils.Log
import kotlinx.coroutines.CancellationException
import java.util.concurrent.TimeUnit

/**
 * Scans the scheduled-post store and publishes posts whose publish time has arrived.
 *
 *  - schedule(context):       periodic, every 15 min (WorkManager minimum).
 *  - scheduleCatchUp(context): one-time, on app start, to flush posts that came
 *                              due while the device was off or while WorkManager
 *                              was deferred by Doze.
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

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME_CATCH_UP)
            Log.d(TAG) { "cancel(): cancelled both periodic and catch-up workers" }
        }
    }

    override suspend fun doWork(): Result {
        val nowSec = System.currentTimeMillis() / 1000
        Log.d(TAG) { "doWork() ENTER nowSec=$nowSec runAttempt=$runAttemptCount tags=$tags" }

        return try {
            val appModules = Amethyst.instance
            val store = appModules.scheduledPostStore

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
                    Log.w(TAG, "Account ${post.accountPubkey} not loaded; releasing ${post.id} for retry")
                    store.releaseClaim(post.id)
                    continue
                }

                try {
                    val event = Event.fromJson(post.signedEventJson)
                    val relays = post.relayUrls.map { NormalizedRelayUrl(it) }.toSet()
                    val extras = post.extraEventsJson.map { Event.fromJson(it) }

                    Log.d(TAG) { "client.publish(${post.id}) starting on ${relays.size} relay(s)" }
                    account.client.publish(event, relays)
                    account.consumePostEvent(event, relays, extras)

                    store.markSent(post.id)
                    Log.d(TAG) { "client.publish(${post.id}) done; marked SENT" }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to publish scheduled post ${post.id}", e)
                    store.markFailed(post.id, e.message)
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
