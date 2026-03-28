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
package com.vitorpamplona.amethyst.service.notifications

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.vitorpamplona.amethyst.Amethyst
import com.vitorpamplona.quartz.utils.Log
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.TimeUnit

/**
 * WorkManager periodic worker that catches up on missed notifications.
 *
 * This runs every 15 minutes (WorkManager's minimum interval) and ensures
 * relay connections are active. It acts as a safety net:
 *
 * - If the foreground service was killed by an OEM battery optimizer, this
 *   worker will briefly restore connections and pull missed events.
 * - If the foreground service is running fine, this worker is essentially a no-op
 *   since the connections are already live and filters are active.
 *
 * The worker collects relayServices to ensure connections are active,
 * waits briefly for data to flow, then exits. The foreground service
 * (if alive) handles the persistent connection.
 */
class NotificationCatchUpWorker(
    appContext: Context,
    workerParams: WorkerParameters,
) : CoroutineWorker(appContext, workerParams) {
    companion object {
        private const val TAG = "NotificationCatchUpWorker"
        private const val WORK_NAME = "notification_catch_up"
        private const val CATCH_UP_DURATION_MS = 30_000L

        fun schedule(context: Context) {
            val constraints =
                Constraints
                    .Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()

            val request =
                PeriodicWorkRequestBuilder<NotificationCatchUpWorker>(
                    15,
                    TimeUnit.MINUTES,
                ).setConstraints(constraints)
                    .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request,
            )
            Log.d(TAG, "Scheduled periodic catch-up work")
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
            Log.d(TAG, "Cancelled periodic catch-up work")
        }
    }

    override suspend fun doWork(): Result {
        Log.d(TAG, "Starting notification catch-up")

        return try {
            val appModules = Amethyst.instance

            // Collecting relayServices ensures connections are active.
            // If the foreground service is alive, connections are already up
            // and this is essentially free. If it was killed, this briefly
            // restores them.
            withTimeoutOrNull(CATCH_UP_DURATION_MS) {
                // Trigger connection by collecting the first emission
                appModules.relayProxyClientConnector.relayServices.first()

                // Give the relay subscriptions time to receive pending events
                delay(CATCH_UP_DURATION_MS - 5_000)
            }

            Log.d(TAG, "Notification catch-up completed")
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Notification catch-up failed", e)
            Result.retry()
        }
    }
}
