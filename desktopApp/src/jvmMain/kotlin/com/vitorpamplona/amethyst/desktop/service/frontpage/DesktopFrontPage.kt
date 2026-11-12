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
package com.vitorpamplona.amethyst.desktop.service.frontpage

import com.vitorpamplona.amethyst.commons.frontpage.FrontPageNotifier
import com.vitorpamplona.amethyst.commons.frontpage.FrontPagePrinters
import com.vitorpamplona.amethyst.commons.frontpage.FrontPageSchedule
import com.vitorpamplona.amethyst.commons.frontpage.FrontPageScheduleWatcher
import com.vitorpamplona.amethyst.commons.frontpage.FrontPageScheduler
import com.vitorpamplona.amethyst.commons.frontpage.PrintJobEnd
import com.vitorpamplona.amethyst.commons.frontpage.SavedEdition
import com.vitorpamplona.amethyst.commons.model.Account
import com.vitorpamplona.amethyst.commons.moderation.notifications.NotifKind
import com.vitorpamplona.amethyst.commons.moderation.notifications.NotificationSpec
import com.vitorpamplona.amethyst.commons.moderation.notifications.NotificationTarget
import com.vitorpamplona.amethyst.commons.moderation.notifications.SendResult
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.front_page_failed_title
import com.vitorpamplona.amethyst.commons.resources.front_page_needs_you_body
import com.vitorpamplona.amethyst.commons.resources.front_page_needs_you_title
import com.vitorpamplona.amethyst.commons.resources.front_page_ready_title
import com.vitorpamplona.amethyst.commons.ui.loadStringRes
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.frontpage.loadPrintEndSentence
import com.vitorpamplona.amethyst.desktop.app.DesktopNotifications
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.utils.Log
import com.vitorpamplona.quartz.utils.TimeUtils
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

/** How this computer names itself in the synced schedule: "Desktop (Linux)", "Desktop (macOS)". */
fun desktopInstallName(): String {
    val os = System.getProperty("os.name").orEmpty()
    val name =
        when {
            os.startsWith("Mac", ignoreCase = true) -> "macOS"
            os.startsWith("Windows", ignoreCase = true) -> "Windows"
            os.startsWith("Linux", ignoreCase = true) -> "Linux"
            else -> os.ifBlank { null }
        }
    return if (name != null) "Desktop ($name)" else "Desktop"
}

/** "Your paper is ready" through the desktop's own notifier, when the reader has notifications on. */
class DesktopFrontPageNotifier(
    private val notifications: DesktopNotifications,
) : FrontPageNotifier {
    override suspend fun paperReady(
        account: HexKey,
        edition: SavedEdition,
    ) = send(account, loadStringRes(Res.string.front_page_ready_title), edition.headline.orEmpty())

    override suspend fun needsYou(account: HexKey) = send(account, loadStringRes(Res.string.front_page_needs_you_title), loadStringRes(Res.string.front_page_needs_you_body))

    override suspend fun printFailed(
        account: HexKey,
        end: PrintJobEnd,
    ) = send(account, loadStringRes(Res.string.front_page_failed_title), loadPrintEndSentence(end).orEmpty())

    private suspend fun send(
        account: HexKey,
        title: String,
        body: String,
    ) {
        if (!notifications.settings.enabled.value) return
        // One thread per account: one account's paper never replaces another's.
        val result = notifications.dispatcher.send(frontPageSpec(account, title, body))
        if (result is SendResult.Failed) Log.w(TAG, "Could not post the front page notification", result.error)
    }

    companion object {
        private const val TAG = "DesktopFrontPage"
        private const val THREAD_ID = "front-page"

        /** A click opens The Observer, as [account]: the paper is that reader's. */
        internal fun frontPageSpec(
            account: HexKey,
            title: String,
            body: String,
        ) = NotificationSpec(
            title = title,
            body = body,
            kind = NotifKind.FRONT_PAGE,
            threadId = "$THREAD_ID-${account.take(16)}",
            target = NotificationTarget.FrontPage,
            account = account,
        )
    }
}

/**
 * The morning edition on desktop: a timer per account while the app runs, since nothing here
 * wakes a closed app, and only where the reader turned on "Print from this device"
 * ([FrontPageScheduleWatcher] drives [reschedule] and [runNow]). Which device prints an
 * occurrence is its claim's call. An occurrence missed while the app was closed may run when the
 * account loads, until the next one comes.
 *
 * The timer that wakes and the run it starts are apart: a reschedule moves or stops the timer,
 * never a print that is running (that costs the reader money), and a reschedule to the same
 * schedule leaves its timer alone.
 */
class DesktopFrontPageScheduler(
    private val printers: FrontPagePrinters,
    private val accounts: StateFlow<Map<HexKey, Account>>,
    private val scope: CoroutineScope,
) : FrontPageScheduler {
    private class Timer(
        val schedule: FrontPageSchedule,
        val job: Job,
    )

    private val timers = ConcurrentHashMap<HexKey, Timer>()
    private val runs = ConcurrentHashMap<HexKey, Job>()

    /** Watches accounts load, keeps each one's timer in step with the synced schedule. */
    fun start() {
        FrontPageScheduleWatcher(printers.morning, accounts, this, scope).start()
        scope.launch {
            accounts.collect { loaded ->
                // A signed-out account's timer, and its run, go with it.
                timers.keys.filter { it !in loaded }.forEach { forget(it) }
            }
        }
    }

    override fun reschedule(
        account: HexKey,
        schedule: FrontPageSchedule,
    ) {
        val current = timers[account]
        if (current != null && current.schedule == schedule && current.job.isActive) return
        timers.remove(account)?.job?.cancel()
        if (!schedule.enabled) return
        val job =
            scope.launch {
                var from = TimeUtils.now()
                while (true) {
                    val next = schedule.nextRun(from) ?: return@launch
                    untilWallClock(next)
                    startRun(account)
                    from = maxOf(next, TimeUtils.now())
                }
            }
        timers[account] = Timer(schedule, job)
    }

    override fun runNow(account: HexKey) = startRun(account)

    override fun forget(account: HexKey) {
        timers.remove(account)?.job?.cancel()
        runs.remove(account)?.cancel()
    }

    /** One run at a time per account, on its own job: no timer change reaches it. */
    private fun startRun(account: HexKey) {
        if (runs[account]?.isActive == true) return
        runs[account] = scope.launch { run(account) }
    }

    private suspend fun run(account: HexKey) {
        val loaded = accounts.value[account] ?: return
        try {
            Log.d(TAG) { "Morning edition for ${account.take(8)}: ${printers.of(loaded).printScheduled()}" }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Morning edition failed", e)
        }
    }

    companion object {
        private const val TAG = "DesktopFrontPage"

        /** How often a waiting timer looks at the wall clock. */
        private const val SLICE_MS = 60_000L

        /**
         * Waits until the wall clock reaches [atSeconds]. In slices, not one long delay: the
         * coroutine clock is monotonic and stops while a laptop sleeps, so a single delay set at
         * night fires late by however long the lid was closed.
         */
        suspend fun untilWallClock(
            atSeconds: Long,
            now: () -> Long = { TimeUtils.now() },
            pause: suspend (Long) -> Unit = { delay(it) },
        ) {
            while (true) {
                val left = atSeconds - now()
                if (left <= 0) return
                pause(minOf(left * 1000, SLICE_MS))
            }
        }
    }
}
