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
import android.os.PowerManager
import com.vitorpamplona.amethyst.Amethyst
import com.vitorpamplona.amethyst.model.Account
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.model.User
import com.vitorpamplona.amethyst.service.relayClient.authCommand.model.ScreenAuthAccount
import com.vitorpamplona.amethyst.service.relayClient.reqCommand.event.EventFinderQueryState
import com.vitorpamplona.amethyst.service.relayClient.reqCommand.user.UserFinderQueryState
import com.vitorpamplona.quartz.utils.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Makes a tray notification *observable*: it renders immediately from whatever
 * is already in [com.vitorpamplona.amethyst.model.LocalCache], then — if the
 * involved users' names/pictures or the involved notes' content/media haven't
 * loaded yet — opens a bounded relay window, subscribes to those users and
 * notes, and re-renders the same notification (replacing it in place) as the
 * missing data arrives.
 *
 * This is the generalized form of the ad-hoc subscribe-and-wait that
 * `notifyIncomingCall` and `wakeUpFor` do: on a cold push the process has no
 * cached metadata, so without this a notification would show a raw pubkey and
 * no avatar. With it, the notification fills in the moment the kind:0 (and the
 * post itself, for media) lands.
 *
 * The re-render path relies on notifications being keyed by a stable id and on
 * `setOnlyAlertOnce(true)` (see [PushNotifier]) so replacements update silently
 * instead of re-buzzing.
 */
object NotificationEnricher {
    private const val TAG = "NotificationEnricher"
    private const val WINDOW_MS = 25_000L

    /**
     * Posts [build] now, then observes [users] and [notes] for the enrichment
     * window, re-running [build] whenever their metadata/content changes, until
     * [isComplete] reports everything needed is present or the window elapses.
     *
     * Non-blocking: the observation runs detached on the app IO scope under its
     * own wakelock, so the caller (the notification dispatcher) is never held up.
     * When [isComplete] is already satisfied, no relay window is opened.
     */
    fun enrichAndPost(
        context: Context,
        account: Account,
        users: Collection<User>,
        notes: Collection<Note>,
        isComplete: () -> Boolean,
        build: suspend () -> Unit,
    ) {
        Amethyst.instance.applicationIOScope.launch {
            // 1. Immediate render from whatever is already cached.
            runBuild(build)

            // 2. If we already have everything, we're done — no relay window.
            if (isComplete()) return@launch

            withEnrichmentWakeLock(context) {
                observeUntilComplete(account, users, notes, isComplete, build)
            }
        }
    }

    private suspend fun observeUntilComplete(
        account: Account,
        users: Collection<User>,
        notes: Collection<Note>,
        isComplete: () -> Boolean,
        build: suspend () -> Unit,
    ) {
        val userSubs = users.map { UserFinderQueryState(it, account) }
        val noteSubs = notes.map { EventFinderQueryState(it, account) }
        val authSub = ScreenAuthAccount(account)

        try {
            Amethyst.instance.authCoordinator.subscribe(authSub)
            userSubs.forEach {
                Amethyst.instance.sources.userFinder
                    .subscribe(it)
            }
            noteSubs.forEach {
                Amethyst.instance.sources.eventFinder
                    .subscribe(it)
            }

            coroutineScope {
                // Keep the relay pool connected for the duration of the window.
                val relayJob =
                    launch {
                        try {
                            withTimeout(WINDOW_MS) {
                                Amethyst.instance.relayProxyClientConnector.relayServices
                                    .collect()
                            }
                        } catch (_: CancellationException) {
                            // window elapsed or observation finished first
                        }
                    }

                // Re-render whenever any involved user's metadata or note's
                // content changes; stop as soon as everything needed is present.
                val changes =
                    (
                        users.map { it.metadata().flow.map { } } +
                            notes.map {
                                it
                                    .flow()
                                    .metadata.stateFlow
                                    .map { }
                            }
                    )

                if (changes.isNotEmpty()) {
                    withTimeoutOrNull(WINDOW_MS) {
                        merge(*changes.toTypedArray())
                            .onEach { runBuild(build) }
                            .first { isComplete() }
                    }
                }

                relayJob.cancel()
            }
        } finally {
            noteSubs.forEach {
                Amethyst.instance.sources.eventFinder
                    .unsubscribe(it)
            }
            userSubs.forEach {
                Amethyst.instance.sources.userFinder
                    .unsubscribe(it)
            }
            Amethyst.instance.authCoordinator.unsubscribe(authSub)
        }
    }

    private suspend fun runBuild(build: suspend () -> Unit) {
        try {
            build()
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Log.w(TAG, "Notification build failed", e)
        }
    }

    private inline fun <T> withEnrichmentWakeLock(
        context: Context,
        block: () -> T,
    ): T {
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        val wakeLock =
            powerManager.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "amethyst:notification_enrichment",
            )
        wakeLock.acquire(WINDOW_MS + 5_000L)
        try {
            return block()
        } finally {
            if (wakeLock.isHeld) wakeLock.release()
        }
    }
}
