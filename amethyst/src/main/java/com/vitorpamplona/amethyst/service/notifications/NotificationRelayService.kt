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

import android.app.AlarmManager
import android.app.ForegroundServiceStartNotAllowedException
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
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.vitorpamplona.amethyst.Amethyst
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.ui.MainActivity
import com.vitorpamplona.quartz.utils.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * A foreground service that maintains persistent WebSocket connections to the user's
 * inbox relays for real-time notification delivery.
 *
 * This service keeps the shared NostrClient alive by collecting the relayServices flow.
 * It does NOT create its own relay subscriptions — instead, it relies on the
 * AccountFilterAssembler subscription from the Compose UI tree, which stays active
 * as long as the Activity is alive (even when stopped/backgrounded). That subscription
 * covers notifications, gift wraps, metadata, follows, relay lists, and drafts.
 *
 * Heavy feed subscriptions (Home, Video, Discovery, ChatroomList) are managed
 * separately with lifecycle awareness — they pause when the app backgrounds
 * and resume when it foregrounds. This prevents bandwidth waste on feeds
 * nobody is viewing.
 *
 * Auto-restart mechanisms (inspired by ntfy):
 * - START_STICKY: Android restarts killed services
 * - onTaskRemoved(): 1-second alarm restart when swiped from recents
 * - onDestroy(): broadcast to AutoRestartReceiver for WorkManager restart
 * - AlarmManager watchdog (external, ServiceWatchdogManager)
 * - WorkManager catch-up (external, NotificationCatchUpWorker)
 * - BOOT_COMPLETED + MY_PACKAGE_REPLACED receivers (external, BootCompletedReceiver)
 */
class NotificationRelayService : Service() {
    companion object {
        private const val TAG = "NotificationRelayService"
        private const val CHANNEL_ID = "notification_relay_service"
        private const val NOTIFICATION_ID = 9832

        private const val ACTION_START = "com.vitorpamplona.amethyst.START_NOTIFICATION_SERVICE"

        const val ACTION_AUTO_RESTART = "com.vitorpamplona.amethyst.AUTO_RESTART_NOTIFICATION_SERVICE"

        fun start(context: Context) {
            val intent =
                Intent(context, NotificationRelayService::class.java).apply {
                    action = ACTION_START
                }
            try {
                ContextCompat.startForegroundService(context, intent)
            } catch (e: Exception) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                    e is ForegroundServiceStartNotAllowedException
                ) {
                    // Android 12+ blocks startForegroundService() from the background unless
                    // the caller has a temporary FGS exemption (e.g. broadcast receiver,
                    // exact alarm, high-priority FCM). Cold-start triggered by a flow emission
                    // from AlwaysOnNotificationServiceManager hits this path. The other
                    // notification layers (BootCompletedReceiver, ServiceWatchdogManager,
                    // NotificationCatchUpWorker) plus MainActivity.onResume retry from
                    // contexts that are allowed.
                    Log.w(TAG) { "Foreground service start not allowed from background; will retry from another layer" }
                } else {
                    Log.e(TAG, "Failed to start foreground service", e)
                }
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, NotificationRelayService::class.java))
        }

        fun isEnabled(context: Context): Boolean =
            try {
                Amethyst.instance.sessionManager
                    .loggedInAccount()
                    ?.settings
                    ?.alwaysOnNotificationService
                    ?.value == true
            } catch (e: Exception) {
                false
            }
    }

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var relayServiceCollectorJob: Job? = null
    private var connectedRelayCount = 0
    private var foregroundStarted = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Service created")
        createNotificationChannel()
        initializeForeground()
    }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int,
    ): Int {
        Log.d(TAG, "Starting service")
        // Safety: also call startForeground from onStartCommand in case
        // onCreate didn't complete before onStartCommand fired (ntfy #1520)
        initializeForeground()
        startRelayConnection()
        return START_STICKY
    }

    /**
     * Called when the user swipes the app from recents. Some OEMs kill the
     * foreground service at this point. Schedule an alarm to restart in 1 second.
     */
    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        if (!isEnabled(this)) return

        Log.d(TAG, "Task removed, scheduling restart alarm")
        val restartIntent =
            Intent(this, NotificationRelayService::class.java).apply {
                action = ACTION_START
            }
        val pendingIntent =
            PendingIntent.getService(
                this,
                2,
                restartIntent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_ONE_SHOT,
            )
        val alarmManager = getSystemService(ALARM_SERVICE) as AlarmManager
        alarmManager.set(
            AlarmManager.ELAPSED_REALTIME,
            SystemClock.elapsedRealtime() + 1000,
            pendingIntent,
        )
    }

    /**
     * Called when the service is being destroyed. Send a broadcast to
     * AutoRestartReceiver which will enqueue a WorkManager task to restart.
     * This catches kills that START_STICKY might miss.
     */
    override fun onDestroy() {
        Log.d(TAG, "Service destroyed")
        relayServiceCollectorJob?.cancel()
        scope.cancel()

        if (isEnabled(this)) {
            Log.d(TAG, "Broadcasting auto-restart request")
            val intent =
                Intent(this, AutoRestartReceiver::class.java).apply {
                    action = ACTION_AUTO_RESTART
                }
            sendBroadcast(intent)
        }

        super.onDestroy()
    }

    private fun initializeForeground() {
        if (foregroundStarted) return
        try {
            val notification = buildNotification(0)
            ServiceCompat.startForeground(
                this,
                NOTIFICATION_ID,
                notification,
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                } else {
                    0
                },
            )
            foregroundStarted = true
        } catch (e: Exception) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                e is ForegroundServiceStartNotAllowedException
            ) {
                Log.w(TAG, "Foreground service start not allowed, stopping self")
                stopSelf()
            } else {
                Log.e(TAG, "Failed to start foreground", e)
            }
        }
    }

    /**
     * Keeps the relay infrastructure alive. The service collects two flows:
     *
     * 1. relayServices: Keeps the RelayProxyClientConnector active (connectivity,
     *    Tor, network changes). Without this, the client disconnects 30s after
     *    the UI stops collecting.
     *
     * 2. connectedRelaysFlow: Updates the persistent notification with relay count.
     *
     * The service does NOT create its own relay subscriptions. Instead, it relies on
     * the AccountFilterAssembler subscription that lives in the Compose tree (LoggedInPage).
     * That subscription stays active as long as the Activity exists (even when stopped)
     * and covers: metadata, follows, notifications (inbox relays), gift wraps (DM relays),
     * drafts, and relay list changes. Since the service keeps the client connected,
     * those subscriptions remain active on the relays.
     */
    private fun startRelayConnection() {
        relayServiceCollectorJob?.cancel()
        relayServiceCollectorJob =
            scope.launch {
                launch {
                    Amethyst.instance.relayProxyClientConnector.relayServices.collectLatest {
                        Log.d(TAG) { "Relay services state updated: $it" }
                    }
                }

                launch {
                    Amethyst.instance.client.connectedRelaysFlow().collectLatest { relays ->
                        val count = relays.size
                        if (count != connectedRelayCount) {
                            connectedRelayCount = count
                            updateNotification(count)
                        }
                    }
                }
            }
    }

    private fun updateNotification(connectedRelays: Int) {
        val notification = buildNotification(connectedRelays)
        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    private fun buildNotification(connectedRelays: Int): Notification {
        val contentText =
            if (connectedRelays > 0) {
                getString(R.string.always_on_notif_connected, connectedRelays)
            } else {
                getString(R.string.always_on_notif_connecting)
            }

        val openAppIntent =
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
        val pendingIntent =
            PendingIntent.getActivity(
                this,
                0,
                openAppIntent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )

        return NotificationCompat
            .Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.always_on_notif_title))
            .setContentText(contentText)
            .setSmallIcon(R.drawable.amethyst)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }

    private fun createNotificationChannel() {
        val channel =
            NotificationChannel(
                CHANNEL_ID,
                getString(R.string.always_on_notif_channel_name),
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = getString(R.string.always_on_notif_channel_description)
                setShowBadge(false)
            }
        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(channel)
    }
}
