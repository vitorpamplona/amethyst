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
import com.vitorpamplona.amethyst.model.Account
import com.vitorpamplona.quartz.utils.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Coordinates all 5 layers of the always-on notification system:
 *
 * L1 - NotificationRelayService (foreground service with persistent WebSocket)
 * L2 - FCM/UnifiedPush (existing push system, wakeup trigger)
 * L3 - NotificationCatchUpWorker (WorkManager, 15-min periodic catch-up)
 * L4 - BootCompletedReceiver (restart on boot)
 * L5 - ServiceWatchdogManager (AlarmManager, 5-min health check)
 *
 * When enabled, all layers activate. When disabled, all layers deactivate.
 * The manager watches the account's alwaysOnNotificationService setting
 * and reacts to changes in real time.
 */
class AlwaysOnNotificationServiceManager(
    private val context: Context,
    private val scope: CoroutineScope,
) {
    companion object {
        private const val TAG = "AlwaysOnNotifManager"
    }

    private var watchJob: Job? = null

    /**
     * Starts watching the given account's always-on setting.
     * When the setting changes, all layers are started or stopped accordingly.
     */
    fun watchAccount(account: Account) {
        watchJob?.cancel()
        watchJob =
            scope.launch {
                account.settings.alwaysOnNotificationService.collectLatest { enabled ->
                    if (enabled) {
                        enableAllLayers()
                    } else {
                        disableAllLayers()
                    }
                }
            }
    }

    fun stop() {
        watchJob?.cancel()
        watchJob = null
    }

    private fun enableAllLayers() {
        Log.d(TAG, "Enabling all notification service layers")

        // L1: Start foreground service
        NotificationRelayService.start(context)

        // L3: Schedule periodic catch-up worker
        NotificationCatchUpWorker.schedule(context)

        // L5: Start watchdog alarm
        ServiceWatchdogManager.schedule(context)

        // L2 (FCM) and L4 (BOOT_COMPLETED) are always active via manifest
    }

    private fun disableAllLayers() {
        Log.d(TAG, "Disabling all notification service layers")

        // L1: Stop foreground service
        NotificationRelayService.stop(context)

        // L3: Cancel periodic catch-up worker
        NotificationCatchUpWorker.cancel(context)

        // L5: Cancel watchdog alarm
        ServiceWatchdogManager.cancel(context)
    }
}
