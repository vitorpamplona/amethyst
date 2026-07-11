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
package com.vitorpamplona.amethyst.service.pow

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.vitorpamplona.amethyst.Amethyst
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.commons.service.pow.PoWEstimator
import com.vitorpamplona.amethyst.commons.service.pow.PoWJobState
import com.vitorpamplona.amethyst.ui.MainActivity
import com.vitorpamplona.amethyst.ui.pluralStringRes
import com.vitorpamplona.amethyst.ui.stringRes
import com.vitorpamplona.quartz.utils.Log
import com.vitorpamplona.quartz.utils.TimeUtils
import kotlinx.collections.immutable.ImmutableList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Short-lived foreground service that shields the PoW mining queue from the
 * cached-apps freezer: while it runs the process stays schedulable, so posts
 * finish mining even after the user backgrounds the app.
 *
 * Uses the Android 14+ `shortService` type — no special permission, but a
 * hard ~3 minute budget. On [onTimeout] the service exits cleanly; every
 * persistable job is already checkpointed by [PowJobStore], so anything still
 * unmined resumes on the next app launch. Started on every enqueue (the app
 * is necessarily in the foreground then), stops itself when the queue drains.
 *
 * The notification is a live progress card ([NotificationCompat.ProgressStyle]):
 * one track segment per post, filling as jobs complete, indeterminate while a
 * single post mines, with a cancel-all action. On Android 16+ it renders as a
 * Live Updates chip; older versions fall back to a standard progress bar.
 */
class PowMiningForegroundService : Service() {
    private val scope = CoroutineScope(Dispatchers.Main.immediate + SupervisorJob())
    private var watchJob: Job? = null

    // Session totals so the progress track can show "done / enqueued since the
    // service started" — the queue itself only knows what is still pending.
    private var sessionTotal = 0
    private var lastQueueSize = 0

    // Benchmarked once per service run (~250 ms, cached by the estimator);
    // read from the notification builder to compute expected durations.
    @Volatile
    private var hashRate: Double? = null

    // Built once per service instance: the intents never change, and
    // buildNotification runs on every queue update.
    private val tapIntent: PendingIntent by lazy {
        PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    private val cancelIntent: PendingIntent by lazy {
        PendingIntent.getService(
            this,
            1,
            Intent(this, PowMiningForegroundService::class.java).setAction(ACTION_CANCEL_ALL),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    override fun onCreate() {
        super.onCreate()
        running = true
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int,
    ): Int {
        // Android's contract: every onStartCommand after startForegroundService
        // must call startForeground promptly, even on the stop path.
        runCatching { startForegroundCompat(currentJobs()) }
            .onFailure {
                Log.w(TAG, "startForeground failed; mining continues without the service", it)
                stopSelf()
                return START_NOT_STICKY
            }

        if (intent?.action == ACTION_CANCEL_ALL) {
            Amethyst.instance.powPublishQueue.cancelAll()
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }

        watchQueue()
        return START_NOT_STICKY
    }

    /**
     * The shortService budget (~3 min) is exhausted. Exit before the system
     * ANRs us: persisted jobs are checkpointed and resume on next launch;
     * in-memory jobs keep mining opportunistically until the process freezes.
     */
    override fun onTimeout(startId: Int) {
        Log.d(TAG) { "shortService budget exhausted; ${currentJobs().size} job(s) left to resume later" }
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        running = false
        scope.cancel()
        super.onDestroy()
    }

    private fun currentJobs(): ImmutableList<PoWJobState> = Amethyst.instance.powPublishQueue.jobs.value

    private fun watchQueue() {
        if (watchJob != null) return
        watchJob =
            scope.launch {
                Amethyst.instance.powPublishQueue.jobs.collect { jobs ->
                    if (jobs.size > lastQueueSize) sessionTotal += jobs.size - lastQueueSize
                    lastQueueSize = jobs.size

                    if (jobs.isEmpty()) {
                        stopForeground(STOP_FOREGROUND_REMOVE)
                        stopSelf()
                    } else {
                        updateNotification(jobs)
                    }
                }
            }

        // the estimated-time-left figure and progress fraction only move with
        // the clock, not with queue events: benchmark the hash rate once,
        // then refresh the card periodically while something is mining.
        scope.launch {
            hashRate = PoWEstimator.hashesPerSecond()
            while (true) {
                val jobs = currentJobs()
                if (jobs.any { it.isMining }) updateNotification(jobs)
                delay(PROGRESS_REFRESH_MS)
            }
        }
    }

    private fun startForegroundCompat(jobs: ImmutableList<PoWJobState>) {
        ensureChannel(this)
        val notification = buildNotification(jobs)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SHORT_SERVICE)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun updateNotification(jobs: ImmutableList<PoWJobState>) {
        val manager = NotificationManagerCompat.from(this)
        if (!manager.areNotificationsEnabled()) return
        try {
            manager.notify(NOTIFICATION_ID, buildNotification(jobs))
        } catch (_: SecurityException) {
            // POST_NOTIFICATIONS revoked mid-flight; the FGS keeps running.
        }
    }

    private fun buildNotification(jobs: ImmutableList<PoWJobState>): Notification {
        val done = (sessionTotal - jobs.size).coerceAtLeast(0)
        val total = (done + jobs.size).coerceAtLeast(1)

        val current = jobs.firstOrNull { it.isMining } ?: jobs.firstOrNull()

        // expected duration for the job being mined right now, so the card can
        // say "≈ 10 minutes left" and fill its bar toward a predictable end.
        val rate = hashRate
        val startedAt = current?.miningStartedAt
        val expectedSec = if (current != null && rate != null) PoWEstimator.estimateSeconds(current.difficulty, rate) else null
        val elapsedSec = startedAt?.let { (TimeUtils.now() - it).coerceAtLeast(0) }

        val base =
            current?.let {
                pluralStringRes(this, R.plurals.pow_mining_job, it.difficulty, stringRes(this, powKindLabelRes(it.kind)), it.difficulty)
            } ?: stringRes(this, R.string.pow_mining_title)
        val text =
            if (expectedSec != null && elapsedSec != null) {
                "$base • ${formatTimeLeft(this, expectedSec, elapsedSec)}"
            } else {
                base
            }

        val fraction = if (expectedSec != null && elapsedSec != null) elapsedSec / expectedSec else null

        val progressStyle: NotificationCompat.ProgressStyle =
            if (total <= 1) {
                // single post: fill toward the estimated duration; past the
                // mean the search is memoryless, so sweep instead of lying.
                if (fraction != null && fraction < 1.0) {
                    NotificationCompat
                        .ProgressStyle()
                        .setProgressSegments(listOf(NotificationCompat.ProgressStyle.Segment(100)))
                        .setProgress((fraction * 100).toInt())
                } else {
                    NotificationCompat.ProgressStyle().setProgressIndeterminate(true)
                }
            } else {
                NotificationCompat
                    .ProgressStyle()
                    .setProgressSegments(List(total) { NotificationCompat.ProgressStyle.Segment(1) })
                    .setProgress(done)
            }

        return NotificationCompat
            .Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.amethyst)
            .setContentTitle(
                if (jobs.size > 1) {
                    pluralStringRes(this, R.plurals.pow_mining_progress, jobs.size, jobs.size)
                } else {
                    stringRes(this, R.string.pow_mining_title)
                },
            ).setContentText(text)
            .setStyle(progressStyle)
            .setContentIntent(tapIntent)
            .addAction(0, stringRes(this, R.string.pow_notification_cancel_all), cancelIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    companion object {
        private const val TAG = "PowMiningFgs"
        private const val CHANNEL_ID = "pow_mining"
        private const val NOTIFICATION_ID = 0x504F57 // "POW"
        private const val ACTION_CANCEL_ALL = "com.vitorpamplona.amethyst.pow.CANCEL_ALL"

        // clock-driven refresh cadence for the time-left text and bar; the
        // shortService budget (~3 min) caps this at a handful of updates.
        private const val PROGRESS_REFRESH_MS = 30_000L

        // Best-effort de-dup for start(): the queue calls it on EVERY enqueue,
        // and each call otherwise round-trips through system_server. A stale
        // false only costs one redundant startForegroundService (which Android
        // routes to the existing instance's onStartCommand anyway).
        @Volatile
        private var running = false

        /**
         * Best-effort start: enqueue happens while the user is interacting
         * with the app, so the foreground-start allowance normally holds. A
         * restore during a cold background launch may be denied — mining then
         * proceeds unprotected and the service starts on the next enqueue.
         */
        fun start(context: Context) {
            if (running) return
            try {
                context.startForegroundService(Intent(context, PowMiningForegroundService::class.java))
            } catch (e: Exception) {
                Log.w(TAG, "Could not start mining foreground service (backgrounded?); mining continues unprotected", e)
            }
        }

        private fun ensureChannel(context: Context) {
            val manager = context.getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            if (manager.getNotificationChannel(CHANNEL_ID) != null) return
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    stringRes(context, R.string.pow_notification_channel_name),
                    NotificationManager.IMPORTANCE_LOW,
                ).apply {
                    description = stringRes(context, R.string.pow_notification_channel_description)
                    setShowBadge(false)
                },
            )
        }
    }
}
