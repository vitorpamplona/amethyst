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
import com.vitorpamplona.amethyst.model.Account
import com.vitorpamplona.amethyst.service.relayClient.reqCommand.account.nip01Notifications.filterNotificationsToPubkey
import com.vitorpamplona.amethyst.service.relayClient.reqCommand.account.nip01Notifications.filterSummaryNotificationsToPubkey
import com.vitorpamplona.amethyst.service.relayClient.reqCommand.account.nip59GiftWraps.filterGiftWrapsToPubkey
import com.vitorpamplona.amethyst.ui.MainActivity
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.utils.Log
import com.vitorpamplona.quartz.utils.TimeUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/**
 * A foreground service that maintains persistent WebSocket connections to the user's
 * inbox relays for real-time notification delivery.
 *
 * This service:
 * - Keeps the shared NostrClient alive by collecting the relayServices flow
 * - Routes incoming events through EventNotificationConsumer
 * - Survives app backgrounding (connections stay open)
 * - Uses specialUse foreground service type (no Android 15 time limit)
 *
 * The key insight is that this service shares the same NostrClient as the UI.
 * When the app is in the foreground, both the UI and this service are subscribers.
 * When the app backgrounds, UI subscriptions drop but this service's subscriptions
 * remain, keeping inbox relay connections alive. No reconnection needed.
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
        private const val ACTION_STOP = "com.vitorpamplona.amethyst.STOP_NOTIFICATION_SERVICE"

        const val ACTION_AUTO_RESTART = "com.vitorpamplona.amethyst.AUTO_RESTART_NOTIFICATION_SERVICE"

        fun start(context: Context) {
            val intent =
                Intent(context, NotificationRelayService::class.java).apply {
                    action = ACTION_START
                }
            try {
                ContextCompat.startForegroundService(context, intent)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start foreground service", e)
            }
        }

        fun stop(context: Context) {
            val intent =
                Intent(context, NotificationRelayService::class.java).apply {
                    action = ACTION_STOP
                }
            context.startService(intent)
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

    private val subIdNotifications = "svc:notif"
    private val subIdGiftWraps = "svc:giftwrap"

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
        when (intent?.action) {
            ACTION_STOP -> {
                Log.d(TAG, "Stopping service")
                stopSelf()
                return START_NOT_STICKY
            }

            else -> {
                Log.d(TAG, "Starting service")
                // Safety: also call startForeground from onStartCommand in case
                // onCreate didn't complete before onStartCommand fired (ntfy #1520)
                initializeForeground()
                startRelayConnection()
            }
        }
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

        // Clean up our subscriptions so the relay pool can disconnect
        // relays that are no longer needed by anyone
        try {
            Amethyst.instance.client.unsubscribe(subIdNotifications)
            Amethyst.instance.client.unsubscribe(subIdGiftWraps)
        } catch (e: Exception) {
            // App might already be torn down
        }

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

    private fun startRelayConnection() {
        relayServiceCollectorJob?.cancel()
        relayServiceCollectorJob =
            scope.launch {
                // Collecting the relayServices flow keeps the RelayProxyClientConnector alive.
                // This is the same flow that ManageRelayServices() composable collects in the UI.
                // By collecting it here, relay connections survive app backgrounding.
                launch {
                    Amethyst.instance.relayProxyClientConnector.relayServices.collectLatest {
                        Log.d(TAG) { "Relay services state updated: $it" }
                    }
                }

                // Maintain notification + DM subscriptions on inbox relays.
                // When the UI backgrounds, its subscriptions drop and relays that are
                // only needed for those subscriptions disconnect. By opening our own
                // subscriptions here, we ensure inbox + DM relay connections persist.
                launch {
                    maintainInboxSubscriptions()
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

    /**
     * Watches the logged-in account's notification inbox relays and DM inbox relays,
     * maintaining lightweight subscriptions on them. This ensures these relay connections
     * persist even when the UI's subscriptions drop.
     *
     * Notification relays: NIP-65 inbox + local relays → subscribe to mentions, zaps, etc.
     * DM relays: NIP-17 DM relays + NIP-65 inbox + private outbox + local relays → gift wraps
     */
    private suspend fun maintainInboxSubscriptions() {
        // Wait for account to be available
        Amethyst.instance.sessionManager.accountContent.collectLatest { state ->
            val account =
                (state as? com.vitorpamplona.amethyst.ui.screen.AccountState.LoggedIn)?.account

            if (account == null || !account.isWriteable()) {
                // No account or read-only — close any existing subscriptions
                Amethyst.instance.client.unsubscribe(subIdNotifications)
                Amethyst.instance.client.unsubscribe(subIdGiftWraps)
                return@collectLatest
            }

            // Reactively update subscriptions when relay lists change
            combine(
                account.notificationRelays.flow,
                account.dmRelays.flow,
            ) { notifRelays, dmRelays ->
                Pair(notifRelays, dmRelays)
            }.collectLatest { (notifRelays, dmRelays) ->
                updateNotificationSubscription(account, notifRelays)
                updateGiftWrapSubscription(account, dmRelays)
            }
        }
    }

    private fun updateNotificationSubscription(
        account: Account,
        relays: Set<NormalizedRelayUrl>,
    ) {
        if (relays.isEmpty()) {
            Amethyst.instance.client.unsubscribe(subIdNotifications)
            return
        }

        val pubkey = account.signer.pubKey
        val since = TimeUtils.oneWeekAgo()
        val filters = mutableMapOf<NormalizedRelayUrl, List<Filter>>()

        relays.forEach { relay ->
            val relayFilters =
                (
                    filterSummaryNotificationsToPubkey(relay, pubkey, since) +
                        filterNotificationsToPubkey(relay, pubkey, since)
                ).map { it.filter }

            if (relayFilters.isNotEmpty()) {
                filters[relay] = relayFilters
            }
        }

        if (filters.isNotEmpty()) {
            Amethyst.instance.client.subscribe(subIdNotifications, filters)
            Log.d(TAG) { "Subscribed to notifications on ${filters.size} relays" }
        }
    }

    private fun updateGiftWrapSubscription(
        account: Account,
        relays: Set<NormalizedRelayUrl>,
    ) {
        if (relays.isEmpty()) {
            Amethyst.instance.client.unsubscribe(subIdGiftWraps)
            return
        }

        val pubkey = account.signer.pubKey
        val since = TimeUtils.oneWeekAgo()
        val filters = mutableMapOf<NormalizedRelayUrl, List<Filter>>()

        relays.forEach { relay ->
            val relayFilters =
                filterGiftWrapsToPubkey(relay, pubkey, since).map { it.filter }

            if (relayFilters.isNotEmpty()) {
                filters[relay] = relayFilters
            }
        }

        if (filters.isNotEmpty()) {
            Amethyst.instance.client.subscribe(subIdGiftWraps, filters)
            Log.d(TAG) { "Subscribed to gift wraps on ${filters.size} DM relays" }
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
}
