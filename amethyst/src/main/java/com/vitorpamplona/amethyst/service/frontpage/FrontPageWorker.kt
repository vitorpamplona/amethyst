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
package com.vitorpamplona.amethyst.service.frontpage

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.vitorpamplona.amethyst.Amethyst
import com.vitorpamplona.amethyst.LocalPreferences
import com.vitorpamplona.amethyst.commons.frontpage.FrontPageClock
import com.vitorpamplona.amethyst.commons.frontpage.FrontPageSchedule
import com.vitorpamplona.amethyst.commons.frontpage.FrontPageScheduler
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.utils.Log
import com.vitorpamplona.quartz.utils.TimeUtils
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.TimeUnit

/**
 * The morning edition on Android: one unique WorkManager job per account, due at the reader's
 * chosen local time, network-constrained. WorkManager keeps it across reboots. Each run
 * schedules the next, so a time zone change takes effect the next morning.
 */
class AndroidFrontPageScheduler(
    private val context: Context,
) : FrontPageScheduler {
    override fun reschedule(
        account: HexKey,
        schedule: FrontPageSchedule,
    ) {
        val manager = WorkManager.getInstance(context)
        val name = workName(account)
        if (!schedule.enabled) {
            manager.cancelUniqueWork(name)
            return
        }
        val now = TimeUtils.now()
        val delaySec = FrontPageClock.nextRun(now, schedule.hour, schedule.minute) - now
        val request =
            OneTimeWorkRequestBuilder<FrontPageWorker>()
                .setInitialDelay(delaySec, TimeUnit.SECONDS)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setInputData(workDataOf(FrontPageWorker.KEY_ACCOUNT to account))
                .addTag(TAG)
                .build()
        manager.enqueueUniqueWork(name, ExistingWorkPolicy.REPLACE, request)
        Log.d(TAG) { "Next front page for ${account.take(8)} in ${delaySec / 60} min" }
    }

    companion object {
        const val TAG = "FrontPageScheduler"

        fun workName(account: HexKey) = "front_page_${account.lowercase()}"
    }
}

/**
 * Prints one account's morning edition, then schedules tomorrow's. It is a plain worker, not a
 * foreground one: Android 12+ refuses to start a foreground service from the background, and a
 * print (desks, then the model) fits inside WorkManager's ten-minute window. A run that would
 * outlast it stops itself first.
 */
class FrontPageWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val account = inputData.getString(KEY_ACCOUNT) ?: return Result.success()
        val app = Amethyst.instance
        val printers = app.frontPagePrinters
        try {
            val schedule = printers.schedules.get(account)
            if (!schedule.enabled) return Result.success()

            // A cold process has no account loaded: load the ones that can sign, as the
            // scheduled-post worker does. One that cannot load is the reader's to fix.
            val cache = app.accountsCache
            if (account !in cache.accounts.value) cache.loadAllWritableAccounts(LocalPreferences)
            val loaded = cache.accounts.value[account]
            if (loaded == null) {
                app.frontPageNotifier.needsYou(account)
                return Result.success()
            }

            val outcome = withTimeoutOrNull(RUN_LIMIT_MS) { printers.of(loaded).printScheduled() }
            Log.d(TAG) { "Morning edition for ${account.take(8)}: ${outcome ?: "timed out"}" }
            return Result.success()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Morning edition failed", e)
            return Result.success()
        } finally {
            // Tomorrow's, whatever happened today.
            runCatching { app.frontPageScheduler.reschedule(account, printers.schedules.get(account)) }
        }
    }

    companion object {
        const val KEY_ACCOUNT = "account"
        private const val TAG = "FrontPageWorker"

        /** Under WorkManager's ten minutes, so the run ends itself before it is killed. */
        private const val RUN_LIMIT_MS = 9 * 60 * 1000L
    }
}
