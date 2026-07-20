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
package com.vitorpamplona.amethyst.service.foreground

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.ui.MainActivity
import com.vitorpamplona.amethyst.ui.stringRes
import com.vitorpamplona.quartz.utils.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * Shared scaffolding for a foreground service that shields a background job from the
 * cached-apps freezer while it runs, rendering a live [NotificationCompat.ProgressStyle]
 * progress card driven by a [StateFlow].
 *
 * This is deliberately NOT a single "do everything" service: Android 14+ binds
 * `foregroundServiceType` to the service's actual behavior (and each type carries its
 * own permission, budget and start rules), so each workload keeps its own concrete
 * subclass with the correct [fgsType]. What's shared here is only the boilerplate —
 * channel setup, start/stop-when-idle, the notification skeleton, tap + cancel intents,
 * and `onTimeout` — parameterized by a handful of hooks.
 *
 * Subclasses supply the [fgsType], the [state] flow to watch, [isActive] to decide when
 * to stop, [render] to build the card, and [cancelAll] for the cancel action. See
 * `PowMiningForegroundService` (shortService) and `BlossomSyncForegroundService`
 * (dataSync) for the two consumers.
 */
abstract class FlowProgressForegroundService<T> : Service() {
    protected val scope = CoroutineScope(Dispatchers.Main.immediate + SupervisorJob())
    private var watchJob: Job? = null

    /** The Android 14+ `ServiceInfo.FOREGROUND_SERVICE_TYPE_*` this service runs as. */
    protected abstract val fgsType: Int
    protected abstract val channelId: String
    protected abstract val channelNameRes: Int
    protected abstract val channelDescRes: Int
    protected abstract val notificationId: Int

    /** The intent action that routes back here to cancel everything. */
    protected abstract val cancelAction: String
    protected abstract val cancelLabelRes: Int
    protected open val smallIcon: Int = R.drawable.amethyst

    /** When non-null, re-render the card on this cadence (for clock-driven text like "time left"). */
    protected open val refreshMs: Long? = null

    protected abstract fun state(): StateFlow<T>

    /** Keep the service (and notification) alive while this is true; stop once it goes false. */
    protected abstract fun isActive(value: T): Boolean

    protected abstract fun render(value: T): Content

    /** Invoked by the cancel action. */
    protected abstract fun cancelAll()

    /** Called for every emission before [render]; use to update derived subclass state. */
    protected open fun onEmission(value: T) {
        // No-op by default: only subclasses that keep derived state need this hook.
    }

    /** Only consulted for the [refreshMs] clock loop; skip re-renders when nothing is moving. */
    protected open fun needsClockRefresh(value: T): Boolean = true

    /** One-time setup once the watch loop starts (e.g. a benchmark). */
    protected open fun onStarted() {
        // No-op by default: only subclasses with one-time setup (e.g. a benchmark) override this.
    }

    /** How to draw the progress bar of the card. */
    sealed interface Bar {
        data object Indeterminate : Bar

        /** A single bar filled to [fraction] in `0f..1f`. */
        data class Determinate(
            val fraction: Double,
        ) : Bar

        /** [total] equal segments, [done] of them filled — good for "N of M". */
        data class Segmented(
            val total: Int,
            val done: Int,
        ) : Bar
    }

    data class Content(
        val title: String,
        val text: String?,
        val bar: Bar,
    )

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
            Intent(this, this.javaClass).setAction(cancelAction),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int,
    ): Int {
        // Android's contract: every onStartCommand after startForegroundService must call
        // startForeground promptly, even on the stop path.
        runCatching { startForegroundCompat(state().value) }
            .onFailure {
                Log.w(logTag(), "startForeground failed; work continues without the service", it)
                stopSelf()
                return START_NOT_STICKY
            }

        if (intent?.action == cancelAction) {
            cancelAll()
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }

        watch()
        return START_NOT_STICKY
    }

    /** Foreground-service budget exhausted (shortService ~3 min; dataSync on newer OS). Exit cleanly. */
    override fun onTimeout(startId: Int) {
        Log.d(logTag()) { "foreground-service budget exhausted; stopping" }
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun watch() {
        if (watchJob != null) return
        onStarted()
        watchJob =
            scope.launch {
                state().collect { value ->
                    onEmission(value)
                    if (!isActive(value)) {
                        stopForeground(STOP_FOREGROUND_REMOVE)
                        stopSelf()
                    } else {
                        updateNotification(value)
                    }
                }
            }

        refreshMs?.let { ms ->
            scope.launch {
                while (true) {
                    val v = state().value
                    if (isActive(v) && needsClockRefresh(v)) updateNotification(v)
                    delay(ms)
                }
            }
        }
    }

    private fun startForegroundCompat(value: T) {
        ensureChannel()
        val notification = buildNotification(value)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(notificationId, notification, fgsType)
        } else {
            startForeground(notificationId, notification)
        }
    }

    private fun updateNotification(value: T) {
        val manager = NotificationManagerCompat.from(this)
        if (!manager.areNotificationsEnabled()) return
        try {
            manager.notify(notificationId, buildNotification(value))
        } catch (_: SecurityException) {
            // POST_NOTIFICATIONS revoked mid-flight; the FGS keeps running.
        }
    }

    private fun buildNotification(value: T): Notification {
        val content = render(value)
        val style =
            when (val bar = content.bar) {
                is Bar.Indeterminate -> NotificationCompat.ProgressStyle().setProgressIndeterminate(true)
                is Bar.Determinate ->
                    if (bar.fraction.isFinite() && bar.fraction in 0.0..1.0) {
                        NotificationCompat
                            .ProgressStyle()
                            .setProgressSegments(listOf(NotificationCompat.ProgressStyle.Segment(100)))
                            .setProgress((bar.fraction * 100).toInt())
                    } else {
                        NotificationCompat.ProgressStyle().setProgressIndeterminate(true)
                    }
                is Bar.Segmented ->
                    NotificationCompat
                        .ProgressStyle()
                        .setProgressSegments(List(bar.total.coerceAtLeast(1)) { NotificationCompat.ProgressStyle.Segment(1) })
                        .setProgress(bar.done)
            }

        return NotificationCompat
            .Builder(this, channelId)
            .setSmallIcon(smallIcon)
            .setContentTitle(content.title)
            .setContentText(content.text)
            .setStyle(style)
            .setContentIntent(tapIntent)
            .addAction(0, stringRes(this, cancelLabelRes), cancelIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    private fun ensureChannel() {
        val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        if (manager.getNotificationChannel(channelId) != null) return
        manager.createNotificationChannel(
            NotificationChannel(channelId, stringRes(this, channelNameRes), NotificationManager.IMPORTANCE_LOW).apply {
                description = stringRes(this@FlowProgressForegroundService, channelDescRes)
                setShowBadge(false)
            },
        )
    }

    protected open fun logTag(): String = this.javaClass.simpleName

    companion object {
        /**
         * Best-effort start of a [FlowProgressForegroundService] subclass. A start from the
         * background (e.g. a restore) may be denied — the work then proceeds unprotected and
         * the service starts on the next foreground trigger.
         */
        fun start(
            context: Context,
            clazz: Class<out FlowProgressForegroundService<*>>,
            tag: String,
        ) {
            try {
                context.startForegroundService(Intent(context, clazz))
            } catch (e: Exception) {
                Log.w(tag, "Could not start foreground service (backgrounded?); work continues unprotected", e)
            }
        }
    }
}
