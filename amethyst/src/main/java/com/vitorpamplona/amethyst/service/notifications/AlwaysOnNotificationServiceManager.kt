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
import com.vitorpamplona.amethyst.LocalPreferences
import com.vitorpamplona.amethyst.model.accountsCache.AccountCacheState
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.utils.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
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
 * Two switches gate the system:
 *
 * - The **global master** ([LocalPreferences.notificationServiceEnabledFlow], the
 *   "Background notification service" toggle / Quick Settings tile). When off, every
 *   layer is torn down and nothing restarts, regardless of any account's setting —
 *   this is the battery-saver "airplane mode". Persisted, so an explicit off survives
 *   restarts and crashes.
 * - The **per-account participation** flag ([com.vitorpamplona.amethyst.model.AccountSettings.alwaysOnNotificationService],
 *   "Keep this account active in the background"). While the master is on, the service
 *   runs as long as **at least one** writable account participates.
 *
 * While the master is on, every saved writable account is kept loaded in
 * [AccountCacheState] so (a) its participation flag is observable and (b) GiftWraps
 * addressed to any of them (delivered via open relay subscriptions) get unwrapped by
 * the owning account's `newNotesPreProcessor`. Without this, wraps for non-active
 * accounts would sit in [com.vitorpamplona.amethyst.model.LocalCache] with no
 * subscriber able to decrypt them.
 */
class AlwaysOnNotificationServiceManager(
    private val context: Context,
    private val scope: CoroutineScope,
    private val accountsCache: AccountCacheState,
    private val localPreferences: LocalPreferences,
    private val activePubKeyProvider: () -> HexKey?,
) {
    companion object {
        private const val TAG = "AlwaysOnNotifManager"
    }

    private var watchJob: Job? = null
    private var preloadJob: Job? = null
    private var wasEnabled = false

    /**
     * Starts watching the global master switch and the participation flags of every
     * loaded writable account. The service layers run while the master is on AND at
     * least one account participates; the master overrides everything when off.
     *
     * Idempotent: safe to call again on account switch/login — it restarts the watch.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun start() {
        watchJob?.cancel()
        wasEnabled = false
        watchJob =
            scope.launch {
                localPreferences.notificationServiceEnabledFlow().collectLatest { masterEnabled ->
                    if (!masterEnabled) {
                        // Global airplane mode: suppress every layer regardless of
                        // per-account participation, and stop keeping accounts loaded.
                        if (wasEnabled) {
                            disableServiceLayers()
                            wasEnabled = false
                        }
                        stopMultiAccountPreload()
                        return@collectLatest
                    }

                    // Master on: keep every writable account loaded so its participation
                    // flag is observable and its gift wraps can decrypt, then run the
                    // service only while at least one account is participating.
                    startMultiAccountPreload()
                    accountsCache.accounts
                        .flatMapLatest { accounts ->
                            val flags = accounts.values.map { it.settings.alwaysOnNotificationService }
                            if (flags.isEmpty()) {
                                flowOf(false)
                            } else {
                                combine(flags) { values -> values.any { it } }
                            }
                        }.distinctUntilChanged()
                        .collectLatest { anyParticipating ->
                            if (anyParticipating) {
                                wasEnabled = true
                                enableServiceLayers()
                            } else if (wasEnabled) {
                                disableServiceLayers()
                                wasEnabled = false
                            }
                        }
                }
            }
    }

    fun stop() {
        watchJob?.cancel()
        watchJob = null
        preloadJob?.cancel()
        preloadJob = null
        // Logout/terminate: tear the layers down explicitly. Otherwise the watchdog alarm
        // and periodic worker stay scheduled and would resurrect the service for a
        // logged-out user (nobody participating).
        disableServiceLayers()
        wasEnabled = false
    }

    private fun enableServiceLayers() {
        Log.d(TAG, "Enabling notification service layers")

        // L1: Start foreground service
        NotificationRelayService.start(context)

        // L3: Schedule periodic catch-up worker
        NotificationCatchUpWorker.schedule(context)

        // L5: Start watchdog alarm
        ServiceWatchdogManager.schedule(context)

        // L2 (FCM) and L4 (BOOT_COMPLETED) are always active via manifest
    }

    private fun disableServiceLayers() {
        Log.d(TAG, "Disabling notification service layers")

        // L1: Stop foreground service
        NotificationRelayService.stop(context)

        // L3: Cancel periodic catch-up worker
        NotificationCatchUpWorker.cancel(context)

        // L5: Cancel watchdog alarm
        ServiceWatchdogManager.cancel(context)
    }

    /**
     * Preloads every saved writable account into [AccountCacheState] and keeps the set
     * in sync by observing [LocalPreferences.accountsFlow]. New accounts added while
     * the service is enabled (login flow) are picked up automatically.
     *
     * Note: the first [LocalPreferences.accountsFlow] emission is `null` (lazily
     * populated). We still call [AccountCacheState.loadAllWritableAccounts] on every
     * emission — its suspend call to `allSavedAccounts()` triggers flow population,
     * and subsequent [loadAccount] calls are idempotent on already-loaded accounts.
     */
    private fun startMultiAccountPreload() {
        preloadJob?.cancel()
        preloadJob =
            scope.launch {
                localPreferences.accountsFlow().collect {
                    try {
                        accountsCache.loadAllWritableAccounts(localPreferences)
                    } catch (e: Exception) {
                        if (e is CancellationException) throw e
                        Log.w(TAG, "Multi-account preload failed: ${e.message}", e)
                    }
                }
            }
    }

    /**
     * Cancels the preload collector and releases every cached account except the
     * currently active one, so users with the master off return to single-account
     * memory/battery footprint.
     */
    private fun stopMultiAccountPreload() {
        preloadJob?.cancel()
        preloadJob = null
        // remove this because we don't know which other accounts might be getting used.
        // val active = activePubKeyProvider()
        // if (active != null) {
        //    accountsCache.retainOnly(setOf(active))
        // }
    }
}
