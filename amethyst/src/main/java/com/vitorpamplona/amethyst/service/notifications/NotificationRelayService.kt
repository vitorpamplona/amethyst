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
 * This service:
 * - Keeps the shared NostrClient alive by collecting the relayServices flow
 * - Adds notification-specific subscriptions on inbox relays
 * - Routes incoming events through EventNotificationConsumer
 * - Survives app backgrounding (connections stay open)
 * - Uses specialUse foreground service type (no Android 15 time limit)
 *
 * The key insight is that this service shares the same NostrClient as the UI.
 * When the app is in the foreground, both the UI and this service are subscribers.
 * When the app backgrounds, UI subscriptions drop but this service's subscriptions
 * remain, keeping inbox relay connections alive. No reconnection needed.
 */
class NotificationRelayService : Service() {
    companion object {
        private const val TAG = "NotificationRelayService"
        private const val CHANNEL_ID = "notification_relay_service"
        private const val NOTIFICATION_ID = 9832

        private const val ACTION_START = "com.vitorpamplona.amethyst.START_NOTIFICATION_SERVICE"
        private const val ACTION_STOP = "com.vitorpamplona.amethyst.STOP_NOTIFICATION_SERVICE"

        fun start(context: Context) {
            val intent =
                Intent(context, NotificationRelayService::class.java).apply {
                    action = ACTION_START
                }
            ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            val intent =
                Intent(context, NotificationRelayService::class.java).apply {
                    action = ACTION_STOP
                }
            context.startService(intent)
        }

        fun isEnabled(context: Context): Boolean {
            // Check if service should be enabled based on account settings
            return try {
                Amethyst.instance.sessionManager
                    .loggedInAccount()
                    ?.settings
                    ?.alwaysOnNotificationService
                    ?.value == true
            } catch (e: Exception) {
                false
            }
        }
    }

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var relayServiceCollectorJob: Job? = null
    private var connectedRelayCount = 0

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Service created")
        createNotificationChannel()
    }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int,
    ): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                Log.d(TAG, "Stopping service")
                stopSelf()
                return START_NOT_STICKY
            }

            else -> {
                Log.d(TAG, "Starting service")
                startForegroundWithNotification()
                startRelayConnection()
            }
        }
        return START_STICKY
    }

    private fun startForegroundWithNotification() {
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
    }

    private fun startRelayConnection() {
        relayServiceCollectorJob?.cancel()
        relayServiceCollectorJob =
            scope.launch {
                // Collecting the relayServices flow keeps the RelayProxyClientConnector alive.
                // This is the same flow that ManageRelayServices() composable collects in the UI.
                // By collecting it here, relay connections survive app backgrounding.
                launch {
                    Amethyst.instance.relayProxyClientConnector.relayServices.collectLatest {
                        Log.d(TAG, "Relay services state updated: $it")
                    }
                }

                // Monitor connected relay count to update the notification
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

        val stopIntent =
            Intent(this, NotificationRelayService::class.java).apply {
                action = ACTION_STOP
            }
        val stopPendingIntent =
            PendingIntent.getService(
                this,
                1,
                stopIntent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )

        return NotificationCompat
            .Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.always_on_notif_title))
            .setContentText(contentText)
            .setSmallIcon(R.drawable.amethyst)
            .setContentIntent(pendingIntent)
            .addAction(0, getString(R.string.always_on_notif_stop), stopPendingIntent)
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

    override fun onDestroy() {
        Log.d(TAG, "Service destroyed")
        relayServiceCollectorJob?.cancel()
        scope.cancel()
        super.onDestroy()
    }
}
